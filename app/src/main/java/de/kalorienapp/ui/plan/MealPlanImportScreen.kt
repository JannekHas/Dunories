package de.kalorienapp.ui.plan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
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
    val pdfs: List<Uri> = emptyList(),
    /** PDF zu groß für die KI. */
    val pdfTooLarge: Boolean = false,
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

    /** Fotos dazunehmen (Galerie oder Kamera), höchstens [MealPlanImporter.MAX_IMAGES]. */
    fun addImages(uris: List<Uri>) = _state.update {
        it.copy(images = (it.images + uris).distinct().take(MealPlanImporter.MAX_IMAGES), error = null, pdfTooLarge = false)
    }

    fun addPdf(uri: Uri) = _state.update {
        it.copy(pdfs = (it.pdfs + uri).distinct().take(MealPlanImporter.MAX_PDFS), error = null, pdfTooLarge = false)
    }

    fun clearFiles() = _state.update { it.copy(images = emptyList(), pdfs = emptyList(), error = null, pdfTooLarge = false) }

    val PlanImportState.hasInput get() = images.isNotEmpty() || pdfs.isNotEmpty() || text.isNotBlank()

    fun setText(text: String) = _state.update { it.copy(text = text, error = null) }

    fun run() {
        val current = _state.value
        if (current.running || !current.hasInput) return
        _state.update { it.copy(running = true, error = null, pdfTooLarge = false) }
        job = viewModelScope.launch {
            try {
                val items = importer.import(current.images, current.text, current.pdfs)
                _state.update { it.copy(running = false, result = items) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: MealPlanImporter.PdfTooLargeException) {
                _state.update { it.copy(running = false, pdfTooLarge = true) }
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
            repository.updateConfig { it.copy(days = items.maxOf { item -> item.dayIndex } + 1) }
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
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MealPlanImporter.MAX_IMAGES),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addPdf)
    }
    // Kamera: Foto direkt vom Plan (z. B. Ausdruck vom Ernährungsberater)
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.addImages(listOf(it)) }
    }
    fun launchCamera() {
        val uri = planPhotoUri(context)
        cameraUri = uri
        takePhoto.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val maxImages = state.images.size >= MealPlanImporter.MAX_IMAGES

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
            // Drei Wege: Foto machen, Fotos aus der Galerie, PDF
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SourceButton(stringResource(R.string.plan_import_camera), enabled = !state.running && !maxImages, Modifier.weight(1f)) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                }
                SourceButton(stringResource(R.string.plan_import_photos), enabled = !state.running && !maxImages, Modifier.weight(1f)) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SourceButton(
                    stringResource(R.string.plan_import_pdf),
                    enabled = !state.running && state.pdfs.size < MealPlanImporter.MAX_PDFS,
                    Modifier.weight(1f),
                ) { pdfPicker.launch(arrayOf("application/pdf")) }
            }
            if (state.images.isNotEmpty() || state.pdfs.isNotEmpty()) {
                val parts = buildList {
                    if (state.images.isNotEmpty()) add(pluralStringResource(R.plurals.plan_import_photos_count, state.images.size, state.images.size))
                    if (state.pdfs.isNotEmpty()) add(pluralStringResource(R.plurals.plan_import_pdfs_count, state.pdfs.size, state.pdfs.size))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.plan_import_selected, parts.joinToString(" · ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    TextButtonRow(stringResource(R.string.plan_import_photos_clear)) { viewModel.clearFiles() }
                }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::setText,
                label = { Text(stringResource(R.string.plan_import_paste)) },
                minLines = 4,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.pdfTooLarge) {
                Text(
                    stringResource(R.string.plan_import_pdf_too_large, MealPlanImporter.MAX_PDF_MB),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.error?.let { error ->
                Text(stringResource(error.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.running) {
                AiWaitProgress(expectedSeconds = 25)
            } else {
                Button(
                    onClick = viewModel::run,
                    enabled = state.images.isNotEmpty() || state.pdfs.isNotEmpty() || state.text.isNotBlank(),
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
private fun SourceButton(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 10.dp),
        modifier = modifier,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

/** Ziel für Kamerafotos vom Plan (Cache, über FileProvider freigegeben); jedes Foto eine eigene Datei. */
private fun planPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "plan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
