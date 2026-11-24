package de.kalorienapp.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.capture.QueuedJob
import de.kalorienapp.capture.QueuedKind
import de.kalorienapp.capture.QueuedStatus
import de.kalorienapp.ui.theme.Neon

/**
 * KI-Warteschlange in der Mahlzeit: „Wird noch erkannt …“ bzw. „Erkannt: Spaghetti“ mit „Prüfen“. Nur sichtbar, solange
 * etwas wartet – sonst nirgends (App soll sich nicht überfüllt anfühlen).
 */
@Composable
internal fun QueuedRows(
    jobs: List<QueuedJob>,
    color: Color,
    onOpen: (QueuedJob) -> Unit,
    onDiscard: (QueuedJob) -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        jobs.forEach { job ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val what = stringResource(
                            when (job.kind) {
                                QueuedKind.PHOTO -> R.string.ai_queue_kind_photo
                                QueuedKind.LABEL -> R.string.ai_queue_kind_label
                                QueuedKind.TEXT -> R.string.ai_queue_kind_text
                            },
                        )
                        Text(
                            when (job.status) {
                                QueuedStatus.WAITING -> stringResource(R.string.ai_queue_row_waiting, what)
                                QueuedStatus.DONE -> stringResource(R.string.ai_queue_row_done, job.drafts.joinToString { it.name })
                                QueuedStatus.FAILED -> stringResource(R.string.ai_queue_row_failed, what)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                        )
                        if (job.status == QueuedStatus.WAITING) {
                            Text(stringResource(R.string.ai_queue_row_waiting_info), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                        }
                    }
                    IconButton(onClick = { onDiscard(job) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.ai_queue_discard), tint = Neon.TextSecondary)
                    }
                }
                // Fertig oder aufgegeben → öffnen (Vorschlag prüfen bzw. nochmal/von Hand). Wartend: nur bei Bedarf jetzt versuchen.
                Button(
                    onClick = { onOpen(job) },
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Neon.OnNeon),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        stringResource(
                            when (job.status) {
                                QueuedStatus.DONE -> R.string.ai_queue_check
                                QueuedStatus.WAITING -> R.string.ai_queue_try_now
                                QueuedStatus.FAILED -> R.string.ai_queue_open_failed
                            },
                        ),
                    )
                }
            }
        }
    }
}
