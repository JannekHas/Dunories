package de.kalorienapp.ui.social

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.capture.ImageLoader
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.remote.gemini.GeminiStreakReader
import de.kalorienapp.data.remote.gemini.ReadStreak
import de.kalorienapp.domain.Gamification.ImportedStreak
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.capture.CaptureError
import de.kalorienapp.ui.capture.messageRes
import de.kalorienapp.ui.capture.toCaptureError
import de.kalorienapp.ui.components.AiWaitProgress
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Screenshot → abgelesene Serie (Bild verkleinern wie bei der Körperanalyse, kleine Ziffern lesbar halten). */
class StreakScreenshotReader(private val context: Context, private val reader: GeminiStreakReader) {
    suspend fun read(uri: Uri): ReadStreak =
        reader.read(listOf(ImageLoader.toAiImage(ImageLoader.load(context, uri, maxSide = 1600), quality = 90)))
}

data class YazioStreakState(
    /** Eingaben als Text – KI füllt sie vor, der Nutzer kann alles ändern (immer nur ein Vorschlag). */
    val current: String = "",
    val longest: String = "",
    val running: Boolean = false,
    val readDone: Boolean = false,
    val error: CaptureError? = null,
    /** Schon übernommen (zum Anzeigen und Entfernen). */
    val saved: ImportedStreak? = null,
) {
    private fun parse(text: String) = text.trim().toIntOrNull()?.takeIf { it in 0..ImportedStreak.MAX_IMPORT }
    fun currentValue() = if (current.isBlank()) 0 else parse(current)
    fun longestValue() = if (longest.isBlank()) 0 else parse(longest)
    val currentError get() = currentValue() == null
    val longestError get() = longestValue() == null
    val canSave: Boolean
        get() = !running && !currentError && !longestError && ((currentValue() ?: 0) > 0 || (longestValue() ?: 0) > 0)
}

class YazioStreakViewModel(
    private val reader: StreakScreenshotReader,
    private val settings: AppSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(
        YazioStreakState(saved = settings.importedStreak.value).let { s ->
            s.saved?.let { s.copy(current = it.current.toString(), longest = it.longest.toString()) } ?: s
        },
    )
    val state: StateFlow<YazioStreakState> = _state.asStateFlow()

    fun setCurrent(text: String) = _state.update { it.copy(current = text.filter(Char::isDigit).take(5)) }
    fun setLongest(text: String) = _state.update { it.copy(longest = text.filter(Char::isDigit).take(5)) }

    /** Screenshot gewählt → gleich ablesen (ein Tipp weniger). */
    fun read(uri: Uri) {
        if (_state.value.running) return
        _state.update { it.copy(running = true, error = null, readDone = false) }
        viewModelScope.launch {
            try {
                val r = reader.read(uri)
                _state.update { s ->
                    s.copy(
                        running = false,
                        readDone = true,
                        current = r.current?.toString() ?: s.current,
                        longest = r.longest?.toString() ?: s.longest,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.toCaptureError()) }
            }
        }
    }

    /** Übernehmen: die laufende Serie zählt ab heute weiter, der Bestwert nie kleiner als die laufende. */
    fun save(onDone: () -> Unit) {
        val s = _state.value.takeIf { it.canSave } ?: return
        val current = s.currentValue() ?: return
        val longest = maxOf(s.longestValue() ?: return, current)
        settings.setImportedStreak(ImportedStreak(current, longest, LocalDate.now().toEpochDay()))
        onDone()
    }

    fun remove() {
        settings.setImportedStreak(null)
        _state.value = YazioStreakState()
    }
}

/**
 * Serie aus YAZIO übernehmen (Wunsch des Nutzers, Nov. 2026): YAZIO hat keinen Datenexport – deshalb Screenshot der
 * Serien-Ansicht, die KI liest „Tage in Folge“ und „Längster Streak“ ab. Eintippen geht genauso.
 * Erreichbar aus der Einrichtung und unter Profil → Einstellungen → Datensicherung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YazioStreakScreen(
    onBack: () -> Unit,
    viewModel: YazioStreakViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.read(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.yazio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
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
            Text(stringResource(R.string.yazio_intro), style = MaterialTheme.typography.bodyLarge, color = Neon.TextSecondary)
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.yazio_how_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.yazio_how), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
                }
            }
            if (state.running) {
                AiWaitProgress(expectedSeconds = 6)
            } else {
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Violet, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null)
                    Text(stringResource(R.string.yazio_pick), modifier = Modifier.padding(start = 8.dp))
                }
            }
            state.error?.let { error ->
                Text(
                    stringResource(if (error == CaptureError.NO_FOOD) R.string.yazio_nothing else error.messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.readDone) {
                Text(stringResource(R.string.yazio_read_done), style = MaterialTheme.typography.bodyMedium, color = Neon.Green)
            }
            NumberField(
                value = state.current,
                onValueChange = viewModel::setCurrent,
                label = stringResource(R.string.yazio_current),
                suffix = stringResource(R.string.yazio_days),
                isError = state.currentError,
                errorText = stringResource(R.string.error_number),
                decimal = false,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(
                value = state.longest,
                onValueChange = viewModel::setLongest,
                label = stringResource(R.string.yazio_longest),
                suffix = stringResource(R.string.yazio_days),
                isError = state.longestError,
                errorText = stringResource(R.string.error_number),
                decimal = false,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.yazio_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.yazio_save), fontWeight = FontWeight.Bold) }
            state.saved?.let { saved ->
                Text(
                    stringResource(R.string.yazio_saved_on, LocalDate.ofEpochDay(saved.epochDay).format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
                OutlinedButton(onClick = viewModel::remove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.yazio_remove))
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.yazio_later)) }
        }
    }
}
