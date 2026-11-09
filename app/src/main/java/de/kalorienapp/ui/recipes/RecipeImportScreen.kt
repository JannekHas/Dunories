package de.kalorienapp.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.AiWaitProgress
import de.kalorienapp.data.remote.social.SocialPageParser
import de.kalorienapp.data.remote.social.SocialPlatform
import de.kalorienapp.data.repository.RecipeRepository
import de.kalorienapp.recipe.ImportFailure
import de.kalorienapp.recipe.ImportResult
import de.kalorienapp.recipe.ImportSource
import de.kalorienapp.recipe.ImportStage
import de.kalorienapp.recipe.RecipeDraftStore
import de.kalorienapp.recipe.RecipeImporter
import de.kalorienapp.recipe.ShareInbox
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeImportUiState(
    val source: ImportSource? = null,
    val stage: ImportStage? = null,
    val failure: ImportFailure? = null,
    val platform: SocialPlatform? = null,
    /** Vorschlag liegt im [RecipeDraftStore] → Editor öffnen. */
    val done: Boolean = false,
)

class RecipeImportViewModel(
    private val inbox: ShareInbox,
    private val importer: RecipeImporter,
    private val recipes: RecipeRepository,
    private val drafts: RecipeDraftStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeImportUiState())
    val uiState: StateFlow<RecipeImportUiState> = _uiState.asStateFlow()

    init {
        inbox.take()?.let(::start)
    }

    fun retry() {
        _uiState.value.source?.let(::start)
    }

    private fun start(source: ImportSource) {
        _uiState.value = RecipeImportUiState(source = source, stage = ImportStage.READING_LINK)
        viewModelScope.launch {
            val categories = recipes.categories.first().map { it.name }
            val result = importer.import(source, categories) { stage -> _uiState.update { it.copy(stage = stage) } }
            when (result) {
                is ImportResult.Success -> {
                    drafts.put(result.draft)
                    _uiState.update { it.copy(done = true) }
                }
                is ImportResult.Failure -> _uiState.update {
                    it.copy(stage = null, failure = result.reason, platform = result.platform ?: it.platform)
                }
            }
        }
    }
}

/** Fortschritt des Imports; bei Erfolg weiter zum Editor, bei Fehlern Hinweise und manuelle Eingabe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeImportScreen(
    onReview: () -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
    viewModel: RecipeImportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onReview() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_import_title)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val source = state.source
            if (source == null) {
                Text(stringResource(R.string.recipe_import_nothing), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text(sourceText(source), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary, maxLines = 3)
            val failure = state.failure
            if (failure == null) {
                Progress(source, state.stage)
            } else {
                FailureCard(failure, state.platform)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (failure in setOf(ImportFailure.NETWORK, ImportFailure.QUOTA, ImportFailure.OVERLOADED, ImportFailure.AI_ERROR, ImportFailure.LINK_UNREADABLE)) {
                        OutlinedButton(onClick = viewModel::retry, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.retry)) }
                    }
                    Button(
                        onClick = onManual,
                        colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.recipe_import_manual)) }
                }
            }
        }
    }
}

@Composable
private fun sourceText(source: ImportSource): String = when (source) {
    is ImportSource.Link -> source.url
    is ImportSource.Text -> source.text
    is ImportSource.Images -> stringResource(R.string.recipe_import_source_images, source.uris.size)
    is ImportSource.Video -> stringResource(R.string.recipe_import_source_video)
}

@Composable
private fun Progress(source: ImportSource, current: ImportStage?) {
    val stages = buildList {
        val hasLink = source is ImportSource.Link ||
            (source is ImportSource.Text && SocialPageParser.extractUrl(source.text) != null)
        if (hasLink) add(ImportStage.READING_LINK)
        if (source is ImportSource.Video) add(ImportStage.UPLOADING_VIDEO)
        add(ImportStage.ANALYZING)
        add(ImportStage.PREPARING_IMAGE)
    }
    val currentIndex = stages.indexOf(current).coerceAtLeast(0)
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            stages.forEachIndexed { index, stage ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        index < currentIndex -> Icon(Icons.Filled.Check, contentDescription = null, tint = Neon.Green, modifier = Modifier.size(24.dp))
                        index == currentIndex -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Neon.Cyan, strokeWidth = 3.dp)
                        else -> Icon(Icons.Filled.Check, contentDescription = null, tint = Neon.Track, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        stringResource(stage.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index <= currentIndex) Neon.TextPrimary else Neon.TextSecondary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            // Ein Balken je Schritt; die KI-Analyse dauert am längsten.
            current?.let { stage ->
                AiWaitProgress(
                    expectedSeconds = when (stage) {
                        ImportStage.ANALYZING -> 20
                        ImportStage.UPLOADING_VIDEO -> 30
                        else -> 5
                    },
                    key = stage,
                )
            }
        }
    }
}

private val ImportStage.labelRes: Int
    get() = when (this) {
        ImportStage.READING_LINK -> R.string.import_stage_link
        ImportStage.UPLOADING_VIDEO -> R.string.import_stage_video
        ImportStage.ANALYZING -> R.string.import_stage_ai
        ImportStage.PREPARING_IMAGE -> R.string.import_stage_image
    }

@Composable
private fun FailureCard(failure: ImportFailure, platform: SocialPlatform?) {
    val (title, text) = when (failure) {
        ImportFailure.LINK_UNREADABLE -> R.string.import_fail_link_title to R.string.import_fail_link_text
        ImportFailure.NO_RECIPE -> R.string.import_fail_recipe_title to R.string.import_fail_recipe_text
        ImportFailure.NO_API_KEY -> R.string.import_fail_ai_title to R.string.error_ai_no_key
        ImportFailure.QUOTA -> R.string.import_fail_ai_title to R.string.error_ai_quota
        ImportFailure.OVERLOADED -> R.string.import_fail_ai_title to R.string.error_ai_overloaded
        ImportFailure.NETWORK -> R.string.import_fail_ai_title to R.string.error_network
        ImportFailure.AI_ERROR -> R.string.import_fail_ai_title to R.string.error_ai_invalid
        ImportFailure.FILE_UNREADABLE -> R.string.import_fail_file_title to R.string.import_fail_file_text
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = Neon.Yellow)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            if (failure == ImportFailure.LINK_UNREADABLE || failure == ImportFailure.NO_RECIPE) {
                Text(
                    stringResource(if (platform == SocialPlatform.INSTAGRAM) R.string.import_tip_instagram else R.string.import_tip_tiktok),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.Cyan,
                )
            }
        }
    }
}
