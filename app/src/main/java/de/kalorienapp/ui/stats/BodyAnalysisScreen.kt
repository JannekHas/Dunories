package de.kalorienapp.ui.stats

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.capture.BodyAnalysisReader
import de.kalorienapp.data.repository.BodyMeasurementRepository
import de.kalorienapp.domain.model.BodyMeasurement
import de.kalorienapp.domain.model.MeasureType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.capture.CaptureError
import de.kalorienapp.ui.capture.SpeechButton
import de.kalorienapp.ui.capture.messageRes
import de.kalorienapp.ui.capture.toCaptureError
import de.kalorienapp.ui.components.AiWaitProgress
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

data class BodyAnalysisState(
    /** Eingabe je Wert (Text wie im Feld); KI-Auslesen füllt sie vor – immer ein bearbeitbarer Vorschlag. */
    val fields: Map<MeasureType, String> = emptyMap(),
    val images: List<Uri> = emptyList(),
    val spoken: String = "",
    val running: Boolean = false,
    val readDone: Boolean = false,
    val error: CaptureError? = null,
) {
    fun value(type: MeasureType): Double? = fields[type]?.parseDecimal()?.takeIf(type::isValid)
    fun isError(type: MeasureType) = !fields[type].isNullOrBlank() && value(type) == null
    val values: Map<MeasureType, Double> get() = MeasureType.ANALYSIS.mapNotNull { t -> value(t)?.let { t to it } }.toMap()
    val canSave: Boolean get() = values.isNotEmpty() && MeasureType.ANALYSIS.none(::isError)
}

class BodyAnalysisViewModel(
    private val reader: BodyAnalysisReader,
    private val repository: BodyMeasurementRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BodyAnalysisState())
    val state: StateFlow<BodyAnalysisState> = _state.asStateFlow()
    private var saving = false

    /** Letzte Werte als Platzhalter. */
    val latest: StateFlow<Map<MeasureType, Double>> = repository.all
        .map { list -> list.groupBy { it.type }.mapValues { it.value.maxBy { m -> m.measuredAt }.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setField(type: MeasureType, text: String) = _state.update { it.copy(fields = it.fields + (type to text)) }

    fun addImages(uris: List<Uri>) = _state.update {
        it.copy(images = (it.images + uris).distinct().take(BodyAnalysisReader.MAX_IMAGES), error = null, readDone = false)
    }

    fun setSpoken(text: String) = _state.update { it.copy(spoken = text, error = null, readDone = false) }

    /** Fotos/Gesprochenes von der KI auslesen lassen → Felder vorbefüllen (vorhandene Eingaben werden überschrieben). */
    fun read() {
        val current = _state.value
        if (current.running || (current.images.isEmpty() && current.spoken.isBlank())) return
        _state.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            try {
                val values = reader.read(current.images, current.spoken)
                _state.update { s ->
                    s.copy(
                        running = false,
                        readDone = true,
                        fields = s.fields + values.mapValues { it.value.toInputString() },
                        images = emptyList(),
                        spoken = "",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.toCaptureError()) }
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val values = _state.value.takeIf { it.canSave }?.values ?: return
        if (saving) return
        saving = true
        val now = Instant.now()
        viewModelScope.launch {
            repository.add(values.map { (type, value) -> BodyMeasurement(type = type, value = value, measuredAt = now) })
            onDone()
        }
    }
}

/** Körperanalyse eintragen: eintippen, Foto vom Display/Ausdruck (Kamera oder Galerie) oder einsprechen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyAnalysisScreen(
    onBack: () -> Unit,
    viewModel: BodyAnalysisViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BodyAnalysisReader.MAX_IMAGES),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.addImages(listOf(it)) }
    }
    fun launchCamera() {
        val uri = analysisPhotoUri(context)
        cameraUri = uri
        takePhoto.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.body_analysis_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    // Speichern von jeder Scrollposition (wie bei den anderen langen Formularen)
                    IconButton(onClick = { viewModel.save(onBack) }, enabled = state.canSave) {
                        Icon(Icons.Filled.Check, stringResource(R.string.save))
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.body_analysis_add_text), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val full = state.images.size >= BodyAnalysisReader.MAX_IMAGES
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !state.running && !full,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.body_analysis_camera)) }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.running && !full,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.body_analysis_photos)) }
            }
            if (state.images.isNotEmpty()) {
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.plan_import_photos_count, state.images.size, state.images.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.Cyan,
                )
            }
            // Einsprechen wie beim Beschreiben („Körperfett 22, Muskelmasse 35 Kilo …“)
            if (!state.running) SpeechButton(text = state.spoken, onChange = viewModel::setSpoken, separator = ", ")
            if (state.spoken.isNotBlank()) {
                Text(
                    stringResource(R.string.body_analysis_spoken) + ": " + state.spoken,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.Cyan,
                )
            }
            if (state.running) {
                AiWaitProgress(expectedSeconds = 8)
            } else if (state.images.isNotEmpty() || state.spoken.isNotBlank()) {
                Button(
                    onClick = viewModel::read,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Violet, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null)
                    Text(stringResource(R.string.body_analysis_read), modifier = Modifier.padding(start = 8.dp))
                }
            }
            state.error?.let { error ->
                Text(
                    stringResource(if (error == CaptureError.NO_FOOD) R.string.body_analysis_nothing else error.messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.readDone) {
                Text(stringResource(R.string.body_analysis_read_done), style = MaterialTheme.typography.bodyMedium, color = Neon.Green)
            }
            MeasureType.ANALYSIS.forEach { type ->
                NumberField(
                    value = state.fields[type].orEmpty(),
                    onValueChange = { viewModel.setField(type, it) },
                    label = stringResource(type.labelRes),
                    suffix = stringResource(type.unitRes).takeIf { it.isNotEmpty() },
                    placeholder = latest[type]?.toInputString(),
                    isError = state.isError(type),
                    errorText = stringResource(R.string.error_number),
                    decimal = type != MeasureType.BMR,
                    imeAction = if (type == MeasureType.ANALYSIS.last()) ImeAction.Done else ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(R.string.body_analysis_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = state.canSave && !state.running,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
        }
    }
}

/** Ziel für Kamerafotos der Waage (Cache, über FileProvider freigegeben). */
private fun analysisPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "analysis_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
