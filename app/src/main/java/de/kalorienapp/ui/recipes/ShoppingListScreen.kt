package de.kalorienapp.ui.recipes

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.repository.ShoppingRepository
import de.kalorienapp.domain.ShoppingList
import de.kalorienapp.domain.model.ShoppingItem
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(private val repository: ShoppingRepository) : ViewModel() {
    val items: StateFlow<List<ShoppingItem>> =
        repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.add(listOf(ShoppingItem(name = name.trim()))) }
    }

    fun toggle(item: ShoppingItem) = viewModelScope.launch { repository.setChecked(item, !item.checked) }
    fun delete(item: ShoppingItem) = viewModelScope.launch { repository.delete(item) }
    fun restore(item: ShoppingItem) = viewModelScope.launch { repository.restore(item) }
    fun deleteChecked() = viewModelScope.launch { repository.deleteChecked() }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }
}

/** „200 g Feta“ – Menge im deutschen Format, ganze Zahlen ohne Komma. */
fun ShoppingItem.label(): String =
    listOfNotNull(quantity?.let(::formatQuantity), unit.takeIf { it.isNotBlank() }, name).joinToString(" ")

/** Einkaufsliste: abhaken, eigene Artikel, teilen (z. B. per WhatsApp). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val title = stringResource(R.string.shopping_title)
    val removed = stringResource(R.string.shopping_removed)
    val undo = stringResource(R.string.undo)

    fun addText() {
        viewModel.add(text)
        text = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ShoppingList.shareText(items, "$title:") { it.label() })
                            }
                            context.startActivity(Intent.createChooser(share, title))
                        },
                        enabled = items.any { !it.checked },
                    ) { Icon(Icons.Filled.Share, stringResource(R.string.shopping_share)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_remove_checked)) },
                            onClick = { menu = false; viewModel.deleteChecked() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_clear)) },
                            onClick = { menu = false; viewModel.deleteAll() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "add") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addText() }),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = ::addText,
                        enabled = text.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Neon.Yellow, contentColor = Neon.OnNeon),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Icon(Icons.Filled.Add, stringResource(R.string.shopping_add)) }
                }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.shopping_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
                }
            }
            val (open, done) = items.partition { !it.checked }
            items(open, key = { it.id }) { item ->
                ShoppingRow(item, onToggle = { viewModel.toggle(item) }, onDelete = {
                    viewModel.delete(item)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(removed.format(item.name), undo, duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(item)
                    }
                })
            }
            if (done.isNotEmpty()) {
                item(key = "done_divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                items(done, key = { it.id }) { item ->
                    ShoppingRow(item, onToggle = { viewModel.toggle(item) }, onDelete = { viewModel.delete(item) })
                }
            }
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().alpha(if (item.checked) 0.5f else 1f),
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Neon.Green, checkmarkColor = Neon.OnNeon),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.label(),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
            if (item.sources.isNotEmpty()) {
                Text(item.sources.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Neon.Violet)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Neon.TextSecondary) }
    }
}
