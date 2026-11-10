package de.kalorienapp.ui.capture

import android.Manifest
import android.speech.RecognizerIntent
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import de.kalorienapp.ui.theme.Neon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import de.kalorienapp.ui.components.neonChipColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import de.kalorienapp.ui.components.pressable
import de.kalorienapp.R
import de.kalorienapp.ui.components.Tip
import de.kalorienapp.ui.components.TipCaret
import de.kalorienapp.ui.components.TipSlot
import de.kalorienapp.ui.components.AiWaitProgress
import de.kalorienapp.capture.AnalysisStage
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onClose: () -> Unit,
    onManualEntry: (MealType) -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val step = state.step
    val atCamera = step is CaptureStep.Camera

    BackHandler(enabled = !atCamera) { viewModel.backToCamera() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(step.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = { if (atCamera) onClose() else viewModel.backToCamera() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (step !is CaptureStep.Proposal && !atCamera) {
                        TextButton(onClick = { onManualEntry(state.mealType) }) {
                            Text(stringResource(R.string.capture_manual))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (step) {
            CaptureStep.Camera -> CameraContent(
                viewModel,
                { onManualEntry(state.mealType) },
                contentModifier,
                labelMode = state.labelMode,
                onLabelMode = viewModel::setLabelMode,
            )
            CaptureStep.TextInput -> TextInputContent(
                description = state.description,
                onChange = viewModel::onDescriptionChange,
                onSubmit = viewModel::submitDescription,
                modifier = contentModifier,
            )
            is CaptureStep.QuickPick -> {
                val combos by viewModel.combos.collectAsStateWithLifecycle()
                val favorites by viewModel.favorites.collectAsStateWithLifecycle()
                val frequent by viewModel.frequent.collectAsStateWithLifecycle()
                QuickPickContent(
                    combos = combos,
                    onRemoveCombo = viewModel::removeCombo,
                    favorites = favorites,
                    frequent = frequent,
                    selected = step.selected,
                    onToggle = viewModel::toggleQuickPick,
                    onRemoveFavorite = viewModel::removeFavorite,
                    onConfirm = viewModel::confirmQuickPick,
                    modifier = contentModifier,
                )
            }
            is CaptureStep.Analyzing -> AnalyzingContent(step.stage, contentModifier)
            is CaptureStep.UnknownBarcode -> MessageContent(
                title = stringResource(R.string.capture_unknown_barcode_title),
                message = stringResource(R.string.capture_unknown_barcode_text, step.barcode),
                primaryLabel = stringResource(R.string.capture_photograph_label),
                onPrimary = viewModel::photographLabel,
                secondaryLabel = stringResource(R.string.capture_analyze_with_ai),
                onSecondary = viewModel::analyzeLastPhotoWithAi,
                onManual = { onManualEntry(state.mealType) },
                onNewPhoto = viewModel::backToCamera,
                modifier = contentModifier,
            )
            is CaptureStep.Failed -> MessageContent(
                title = stringResource(R.string.capture_failed_title),
                message = stringResource(step.error.messageRes),
                primaryLabel = stringResource(R.string.capture_retry).takeIf { step.error.retryable },
                onPrimary = viewModel::retry,
                onManual = { onManualEntry(state.mealType) },
                onNewPhoto = viewModel::backToCamera,
                modifier = contentModifier,
            )
            is CaptureStep.Proposal -> ProposalContent(
                proposal = step,
                mealType = state.mealType,
                viewModel = viewModel,
                onSaved = onClose,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun CameraContent(
    viewModel: CaptureViewModel,
    onManualEntry: () -> Unit,
    modifier: Modifier,
    labelMode: Boolean,
    onLabelMode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { CameraController() }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var capturing by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPhoto)
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier.background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(controller, Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.capture_no_camera_permission),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.capture_grant_permission))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Umschalter: Essen schätzen oder Nährwerttabelle exakt ablesen.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !labelMode,
                    onClick = { onLabelMode(false) },
                    label = { Text(stringResource(R.string.capture_mode_food)) },
                    colors = neonChipColors(),
                )
                FilterChip(
                    selected = labelMode,
                    onClick = { onLabelMode(true) },
                    label = { Text(stringResource(R.string.capture_mode_label)) },
                    colors = neonChipColors(),
                )
            }
            Text(
                stringResource(if (labelMode) R.string.capture_hint_label else R.string.capture_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TipSlot(Tip.CAPTURE, caret = TipCaret.UP_CENTER)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraAction(
                label = stringResource(R.string.capture_gallery_short),
                icon = R.drawable.ic_capture_gallery,
                color = Neon.Magenta,
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            CameraAction(
                label = stringResource(R.string.quick_short),
                icon = R.drawable.ic_capture_favorites,
                color = Neon.Yellow,
                onClick = viewModel::showQuickPick,
            )
            LargeFloatingActionButton(
                shape = CircleShape,
                onClick = {
                    if (hasPermission && !capturing) {
                        capturing = true
                        controller.takePhoto(context) { uri ->
                            capturing = false
                            if (uri != null) viewModel.onPhoto(uri) else viewModel.onCaptureFailed()
                        }
                    }
                },
            ) {
                if (capturing) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    Icon(
                        painterResource(R.drawable.ic_capture_camera),
                        stringResource(R.string.capture_take_photo),
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            CameraAction(
                label = stringResource(R.string.capture_describe),
                icon = R.drawable.ic_capture_describe,
                color = Neon.Violet,
                onClick = viewModel::showTextInput,
            )
            CameraAction(
                label = stringResource(R.string.capture_manual),
                icon = R.drawable.ic_capture_manual,
                color = Neon.Green,
                onClick = onManualEntry,
            )
        }
    }
}

/** Kleiner Knopf mit Beschriftung in der Kameraleiste: farbig getönter Glaskreis mit Neon-Icon. */
@Composable
private fun CameraAction(label: String, @DrawableRes icon: Int, color: Color, onClick: () -> Unit) {
    // Ganze Spalte antippbar, damit auch die Beschriftung reagiert.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pressable(onClick, MaterialTheme.shapes.medium)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TextInputContent(
    description: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.capture_describe_intro), style = MaterialTheme.typography.bodyLarge)
        SpeechButton(onText = { spoken -> onChange(listOf(description.trim(), spoken).filter(String::isNotBlank).joinToString(", ")) })
        OutlinedTextField(
            value = description,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.capture_describe_label)) },
            placeholder = { Text(stringResource(R.string.capture_describe_placeholder)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSubmit,
            enabled = description.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.capture_estimate))
        }
    }
}

@Composable
private fun AnalyzingContent(stage: AnalysisStage, modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (stage == AnalysisStage.AI) {
            Text(
                stringResource(R.string.capture_stage_ai),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            AiWaitProgress(expectedSeconds = 8)
            return@Column
        }
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(
                when (stage) {
                    AnalysisStage.BARCODE -> R.string.capture_stage_barcode
                    AnalysisStage.PRODUCT_LOOKUP -> R.string.capture_stage_product
                    AnalysisStage.AI -> R.string.capture_stage_ai
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    onManual: () -> Unit,
    onNewPhoto: () -> Unit,
    modifier: Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        if (primaryLabel != null) {
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
        }
        if (secondaryLabel != null) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryLabel) }
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.capture_enter_manually))
        }
        TextButton(onClick = onNewPhoto) { Text(stringResource(R.string.capture_back_to_camera)) }
    }
}

private val CaptureStep.titleRes: Int
    get() = when (this) {
        CaptureStep.Camera -> R.string.capture_title
        CaptureStep.TextInput -> R.string.capture_describe
        is CaptureStep.QuickPick -> R.string.quick_title
        is CaptureStep.Analyzing -> R.string.capture_analyzing_title
        is CaptureStep.UnknownBarcode, is CaptureStep.Failed -> R.string.capture_title
        is CaptureStep.Proposal -> R.string.proposal_title
    }

private val CaptureError.retryable: Boolean
    get() = this != CaptureError.NO_API_KEY && this != CaptureError.REJECTED

private val CaptureError.messageRes: Int
    get() = when (this) {
        CaptureError.NO_API_KEY -> R.string.error_ai_no_key
        CaptureError.REJECTED -> R.string.error_ai_rejected
        CaptureError.NETWORK -> R.string.error_network
        CaptureError.QUOTA -> R.string.error_ai_quota
        CaptureError.OVERLOADED -> R.string.error_ai_overloaded
        CaptureError.INVALID_RESPONSE -> R.string.error_ai_invalid
        CaptureError.NO_FOOD -> R.string.error_ai_no_food
        CaptureError.IMAGE -> R.string.error_image
        CaptureError.UNKNOWN -> R.string.error_unknown
    }

/**
 * Spracheingabe über die Android-Spracherkennung (wie das Mikrofon der Tastatur; bevorzugt auf dem Gerät).
 * Der erkannte Text landet im Beschreiben-Feld und kann vor dem Schätzen korrigiert werden.
 */
@Composable
internal fun SpeechButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.takeIf { it.isNotBlank() }?.let(onText)
    }
    val prompt = stringResource(R.string.speech_prompt)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                }
                try {
                    launcher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    unavailable = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Neon.Violet, contentColor = Neon.OnNeon),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
            Text(stringResource(R.string.speech_button), modifier = Modifier.padding(start = 8.dp))
        }
        if (unavailable) {
            Text(stringResource(R.string.speech_unavailable), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
        }
    }
}
