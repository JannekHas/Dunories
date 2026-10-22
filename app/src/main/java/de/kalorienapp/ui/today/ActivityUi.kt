package de.kalorienapp.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.ActivityEntry
import de.kalorienapp.ui.components.NumberField
import de.kalorienapp.ui.components.format

/** Anzeigename einer Aktivität – leer = „Aktivität“. */
@Composable
fun ActivityEntry.displayName(): String = name.ifBlank { stringResource(R.string.activity_default_name) }

/** Aktivität eintragen oder bearbeiten: optionale Bezeichnung + verbrannte kcal. */
@Composable
fun ActivityEditDialog(
    existing: ActivityEntry?,
    onSave: (name: String, kcal: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kcalText by remember { mutableStateOf(existing?.kcal?.toString().orEmpty()) }
    val kcal = kcalText.toIntOrNull()?.takeIf { it in 1..10_000 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.activity_add_title else R.string.activity_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = kcalText,
                    onValueChange = { kcalText = it },
                    label = stringResource(R.string.activity_kcal_label),
                    suffix = stringResource(R.string.unit_kcal),
                    isError = kcalText.isNotBlank() && kcal == null,
                    errorText = stringResource(R.string.error_number),
                    decimal = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.activity_name_label)) },
                    placeholder = { Text(stringResource(R.string.activity_name_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { kcal?.let { onSave(name, it) } }, enabled = kcal != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Übersicht der eingetragenen Aktivitäten eines Tages: bearbeiten, löschen, hinzufügen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityListSheet(
    activities: List<ActivityEntry>,
    onAdd: () -> Unit,
    onEdit: (ActivityEntry) -> Unit,
    onDelete: (ActivityEntry) -> Unit,
    onDismiss: () -> Unit,
    /** Eigene Snackbar im Sheet – die des Screens läge verdeckt dahinter. */
    snackbarHostState: SnackbarHostState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.activity_list_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.kcal_value, activities.sumOf { it.kcal }.format()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (activities.isEmpty()) {
                Text(
                    stringResource(R.string.activity_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            activities.forEach { activity ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(activity) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(activity.displayName()) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.kcal_value, activity.kcal.format()),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            IconButton(onClick = { onDelete(activity) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        }
                    },
                )
            }
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.activity_add_title), modifier = Modifier.padding(start = 8.dp))
            }
            SnackbarHost(snackbarHostState)
        }
    }
}
