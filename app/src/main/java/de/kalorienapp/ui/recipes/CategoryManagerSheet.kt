package de.kalorienapp.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.RecipeCategory
import de.kalorienapp.ui.theme.Neon

/** Kategorien anlegen, umbenennen, löschen (Rezepte bleiben erhalten). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerSheet(
    categories: List<RecipeCategory>,
    onAdd: (String) -> Unit,
    onRename: (RecipeCategory, String) -> Unit,
    onDelete: (RecipeCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CategoryEdit?>(null) }
    var deleting by remember { mutableStateOf<RecipeCategory?>(null) }

    editing?.let { (category) ->
        CategoryNameDialog(
            initial = category?.name.orEmpty(),
            title = stringResource(if (category == null) R.string.category_new else R.string.category_rename),
            onSave = { name ->
                if (category == null) onAdd(name) else onRename(category, name)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.category_delete_title, category.name)) },
            text = { Text(stringResource(R.string.category_delete_text)) },
            confirmButton = {
                TextButton(onClick = { onDelete(category); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Neon.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.categories_manage), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = CategoryEdit(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.category_new), modifier = Modifier.padding(start = 4.dp))
                }
            }
            categories.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { editing = CategoryEdit(category) }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.category_rename), tint = Neon.TextSecondary)
                    }
                    IconButton(onClick = { deleting = category }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary)
                    }
                }
            }
        }
    }
}

/** Offener Namensdialog; [category] = null → neue Kategorie. */
private data class CategoryEdit(val category: RecipeCategory?)

@Composable
fun CategoryNameDialog(initial: String, title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
