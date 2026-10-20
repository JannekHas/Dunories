package de.kalorienapp.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import de.kalorienapp.R
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
            CaptureStep.Camera -> CameraContent(viewModel, { onManualEntry(state.mealType) }, contentModifier)
            CaptureStep.TextInput -> TextInputContent(
                description = state.description,
                onChange = viewModel::onDescriptionChange,
                onSubmit = viewModel::submitDescription,
                modifier = contentModifier,
            )
            is CaptureStep.QuickPick -> {
                val favorites by viewModel.favorites.collectAsStateWithLifecycle()
                val frequent by viewModel.frequent.collectAsStateWithLifecycle()
                QuickPickContent(
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
                primaryLabel = stringResource(R.string.capture_analyze_with_ai),
                onPrimary = viewModel::analyzeLastPhotoWithAi,
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
private fun CameraContent(viewModel: CaptureViewModel, onManualEntry: () -> Unit, modifier: Modifier) {
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

        Text(
            stringResource(R.string.capture_hint),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(12.dp),
        )

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
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Icon(painterResource(R.drawable.ic_photo_library), contentDescription = null) }
            CameraAction(label = stringResource(R.string.quick_short), onClick = viewModel::showQuickPick) {
                Icon(Icons.Filled.Star, contentDescription = null)
            }
            LargeFloatingActionButton(
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
                        painterResource(R.drawable.ic_camera),
                        stringResource(R.string.capture_take_photo),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            CameraAction(label = stringResource(R.string.capture_describe), onClick = viewModel::showTextInput) {
                Icon(Icons.Filled.Edit, contentDescription = null)
            }
            CameraAction(label = stringResource(R.string.capture_manual), onClick = onManualEntry) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
            }
        }
    }
}

/** Kleiner Knopf mit Beschriftung in der Kameraleiste. */
@Composable
private fun CameraAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) { icon() }
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
        CaptureError.INVALID_RESPONSE -> R.string.error_ai_invalid
        CaptureError.NO_FOOD -> R.string.error_ai_no_food
        CaptureError.IMAGE -> R.string.error_image
        CaptureError.UNKNOWN -> R.string.error_unknown
    }
