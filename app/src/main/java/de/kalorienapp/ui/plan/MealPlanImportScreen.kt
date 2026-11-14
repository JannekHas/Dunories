package de.kalorienapp.ui.plan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.capture.MealPlanImporter
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.domain.model.MealPlanItem
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.sum
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.capture.CaptureError
import de.kalorienapp.ui.capture.messageRes
import de.kalorienapp.ui.capture.toCaptureError
import de.kalorienapp.ui.components.AiWaitProgress
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.labelRes
import de.kalorienapp.ui.food.trimDecimalZero
import de.kalorienapp.ui.today.neonColor
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanImportState(
    val images: List<Uri> = emptyList(),
    val text: String = "",
    val running: Boolean = false,
    val error: CaptureError? = null,
    /** Erkannter Plan – erst nach „Übernehmen“ gespeichert. */
    val result: List<MealPlanItem>? = null,
)

class MealPlanImportViewModel(
    private val importer: MealPlanImporter,
    private val repository: MealPlanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanImportState())
    val state: StateFlow<PlanImportState> = _state.asStateFlow()
    private var job: Job? = null
    private var saving = false

    fun setImages(uris: List<Uri>) = _state.update { it.copy(images = uris.take(MealPlanImporter.MAX_IMAGES), error = null) }

    fun setText(text: String) = _state.update { it.copy(text = text, error = null) }

    fun run() {
        val current = _state.value
        if (current.running || (current.images.isEmpty() && current.text.isBlank())) return
        _state.update { it.copy(running = true, error = null) }
        job = viewModelScope.launch {
            try {
                val items = importer.import(current.images, current.text)
                _state.update { it.copy(running = false, result = items) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.toCaptureError()) }
            }
        }
    }

    fun remove(item: MealPlanItem) = _state.update { s -> s.copy(result = s.result?.filterNot { it === item }) }

    fun discardResult() = _state.update { it.copy(result = null) }

    /** Ersetzt den bisherigen Plan; Länge = letzter erkannter Tag. */
    fun apply(onDone: () -> Unit) {
        val items = _state.value.result?.takeIf { it.isNotEmpty() } ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            repository.replaceAll(items)
            repository.updateConfig { it.copy(days = items.maxOf { item -> item.dayIndex } + 1, endEpochDay = null) }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: MealPlanImportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MealPlanImporter.MAX_IMAGES),
    ) { uris -> if (uris.isNotEmpty()) viewModel.setImages(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_import)) },
                navigationIcon = {
                    IconButton(onClick = { if (state.result != null) viewModel.discardResult() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val result = state.result
            if (result != null) {
                ImportPreview(result, onRemove = viewModel::remove, onApply = { viewModel.apply(onDone) })
                return@Column
            }
            Text(stringResource(R.string.plan_import_text), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    if (state.images.isEmpty()) {
                        stringResource(R.string.plan_import_photos)
                    } else {
                        pluralStringResource(R.plurals.plan_import_photos_count, state.images.size, state.images.size)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.images.isNotEmpty()) {
                TextButtonRow(stringResource(R.string.plan_import_photos_clear)) { viewModel.setImages(emptyList()) }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::setText,
                label = { Text(stringResource(R.string.plan_import_paste)) },
                minLines = 4,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { error ->
                Text(stringResource(error.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.running) {
                AiWaitProgress(expectedSeconds = 25)
            } else {
                Button(
                    onClick = viewModel::run,
                    enabled = state.images.isNotEmpty() || state.text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null)
                    Text(stringResource(R.string.plan_import_run), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TextButtonRow(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}

@Composable
private fun ImportPreview(items: List<MealPlanItem>, onRemove: (MealPlanItem) -> Unit, onApply: () -> Unit) {
    val days = items.map { it.dayIndex }.distinct().sorted()
    Text(
        pluralStringResource(R.plurals.plan_import_preview, days.size, days.size),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(R.string.plan_import_replace_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
    days.forEach { day ->
        val dayItems = items.filter { it.dayIndex == day }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plan_day_short, day + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.kcal_value, dayItems.map { it.nutrients }.sum().kcal.format()),
                        style = MaterialTheme.typography.labelLarge,
                        color = Neon.Cyan,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                MealType.entries.forEach { meal ->
                    dayItems.filter { it.mealType == meal }.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(meal.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = meal.neonColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(84.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(
                                        R.string.proposal_item_summary,
                                        item.amount.format(1).trimDecimalZero(),
                                        stringResource(item.unit.labelRes),
                                        item.nutrients.kcal.format(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Neon.TextSecondary,
                                )
                            }
                            IconButton(onClick = { onRemove(item) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
    Button(
        onClick = onApply,
        enabled = items.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.plan_import_apply)) }
}
