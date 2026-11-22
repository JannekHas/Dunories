package de.kalorienapp.ui.feedback

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.FeedbackType
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.capture.SpeechButton
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.theme.Neon

/** Feedback geben: Art, Text (tippen oder einsprechen), optional Screenshot und Name – ohne Konto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attach(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (!state.sent) {
                        IconButton(onClick = viewModel::send, enabled = state.canSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.feedback_send))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.sent) {
            FeedbackSent(state, onDone = onBack, onAgain = viewModel::again, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.feedback_intro), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
            ChoiceChips(
                options = FeedbackType.entries,
                selected = state.type,
                onSelect = viewModel::setType,
                label = { stringResource(it.labelRes) },
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::setText,
                label = { Text(stringResource(R.string.feedback_text)) },
                placeholder = { Text(stringResource(state.type.hintRes)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            SpeechButton(text = state.text, onChange = viewModel::setText, separator = " ")

            val image = state.screenshot
            when {
                state.loadingImage -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan)
                    Text(stringResource(R.string.feedback_image_loading), style = MaterialTheme.typography.bodySmall)
                }
                image != null -> Box {
                    Image(
                        image.asImageBitmap(),
                        contentDescription = stringResource(R.string.feedback_screenshot),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    IconButton(onClick = viewModel::removeScreenshot, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.feedback_screenshot_remove), tint = Neon.TextPrimary)
                    }
                }
                else -> OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.feedback_screenshot_add)) }
            }

            val saved = viewModel.savedProfile
            if (saved != null) {
                // Name schon einmal angegeben → nicht jedes Mal fragen (Wunsch des Nutzers), änderbar in den Einstellungen.
                Text(
                    stringResource(
                        if (saved.credits) R.string.feedback_sent_as_credits else R.string.feedback_sent_as,
                        saved.name,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Neon.TextSecondary,
                )
            } else {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.feedback_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.name.isNotBlank()) {
                    de.kalorienapp.ui.components.SettingSwitchRow(
                        title = stringResource(R.string.feedback_credits),
                        text = stringResource(R.string.feedback_credits_text),
                        checked = state.credits,
                        onChange = viewModel::setCredits,
                    )
                }
            }
            Text(stringResource(R.string.feedback_reward_hint), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
            Text(stringResource(R.string.feedback_privacy), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            if (state.failed) {
                Text(stringResource(R.string.feedback_failed), style = MaterialTheme.typography.bodySmall, color = Neon.Yellow)
            }
            Button(
                onClick = viewModel::send,
                enabled = state.canSend,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.OnNeon)
                } else {
                    Text(stringResource(R.string.feedback_send))
                }
            }
        }
    }
}

private val FeedbackType.labelRes: Int
    get() = when (this) {
        FeedbackType.BUG -> R.string.feedback_type_bug
        FeedbackType.IDEA -> R.string.feedback_type_idea
        FeedbackType.PRAISE -> R.string.feedback_type_praise
    }

private val FeedbackType.hintRes: Int
    get() = when (this) {
        FeedbackType.BUG -> R.string.feedback_hint_bug
        FeedbackType.IDEA -> R.string.feedback_hint_idea
        FeedbackType.PRAISE -> R.string.feedback_hint_praise
    }

/** Nach dem Senden: mittig, großer Haken, was geschickt wurde, „Fertig“ oder „Noch etwas melden“. */
@Composable
private fun FeedbackSent(state: FeedbackUiState, onDone: () -> Unit, onAgain: () -> Unit, modifier: Modifier = Modifier) {
    // Mittig, aber mit großer Schrift trotzdem scrollbar (weight geht in scrollbaren Spalten nicht).
    BoxWithConstraints(modifier.fillMaxSize()) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Neon.Green.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Neon.Green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Neon.OnNeon, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.feedback_thanks), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.feedback_thanks_text),
            style = MaterialTheme.typography.bodyLarge,
            color = Neon.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.feedback_sent_label, stringResource(state.type.labelRes)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Neon.Cyan,
                )
                if (state.text.isNotBlank()) {
                    Text(state.text.trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                state.screenshot?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = stringResource(R.string.feedback_screenshot),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.feedback_done)) }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.feedback_again))
        }
    }
    }
}
