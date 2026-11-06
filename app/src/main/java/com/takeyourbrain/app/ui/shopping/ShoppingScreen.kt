package com.takeyourbrain.app.ui.shopping

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takeyourbrain.app.R
import com.takeyourbrain.app.data.db.ShoppingItem
import com.takeyourbrain.app.ui.common.AppAlertDialog
import com.takeyourbrain.app.ui.common.ArrowLoader
import com.takeyourbrain.app.ui.common.ChoicePill
import com.takeyourbrain.app.ui.common.EmptyState
import com.takeyourbrain.app.ui.common.LocalGlass
import com.takeyourbrain.app.ui.common.clearTopBarColors
import kotlinx.coroutines.launch

/** Какое окно открыто: новый список, переименование, удаление. */
private enum class ListDialog { NEW, RENAME, DELETE }

/** Список покупок: списки-таблетки сверху, пункты карточками, поле ввода снизу. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(onBack: () -> Unit, viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ListDialog?>(null) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }

    // Первый раз списков нет — сразу создаём "Продукты", чтобы можно было писать
    val defaultName = stringResource(R.string.shopping_default_list)
    LaunchedEffect(state.isLoading, state.lists.isEmpty()) {
        if (!state.isLoading && state.lists.isEmpty()) viewModel.ensureList(defaultName)
    }

    // Снекбар "Удалено · Отменить"
    val undoLabel = stringResource(R.string.shopping_undo)
    fun offerUndo(message: String, items: List<ShoppingItem>) {
        if (items.isEmpty()) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(items)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = clearTopBarColors(),
                title = { Text(stringResource(R.string.todo_shopping)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.shareText()?.let { text ->
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        },
                        enabled = state.toBuy.isNotEmpty()
                    ) {
                        Icon(painterResource(R.drawable.ic_share), stringResource(R.string.shopping_share))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.shopping_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_list_new)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_add), null) },
                                onClick = { menuOpen = false; dialog = ListDialog.NEW }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_list_rename)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
                                onClick = { menuOpen = false; dialog = ListDialog.RENAME }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_list_delete)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                                onClick = { menuOpen = false; dialog = ListDialog.DELETE }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            AddBar(
                value = viewModel.input,
                onValueChange = viewModel::onInputChange,
                suggestions = remember(state, viewModel.input) { viewModel.suggestions() },
                onAdd = viewModel::add
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.isLoading || state.selected == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ArrowLoader() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "lists") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    items(state.lists, key = { it.id }) { list ->
                        ChoicePill(
                            text = list.name,
                            selected = list.id == state.selected?.id,
                            onClick = { viewModel.selectList(list.id) },
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            horizontalPadding = 20.dp
                        )
                    }
                    item(key = "new") {
                        ChoicePill(
                            text = stringResource(R.string.shopping_list_new_short),
                            icon = R.drawable.ic_add,
                            selected = false,
                            onClick = { dialog = ListDialog.NEW },
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            horizontalPadding = 16.dp
                        )
                    }
                }
            }
            if (state.toBuy.isEmpty() && state.bought.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.shopping_empty)) }
            }
            itemsIndexed(state.toBuy, key = { _, item -> item.id }) { index, item ->
                SwipeToDelete(
                    onDelete = {
                        viewModel.delete(item)
                        offerUndo(resources.getString(R.string.shopping_deleted, item.text), listOf(item))
                    },
                    shape = groupShape(index, state.toBuy.size),
                    modifier = Modifier.animateItem()
                ) {
                    ItemRow(
                        item = item,
                        shape = groupShape(index, state.toBuy.size),
                        onClick = { viewModel.toggle(item) },
                        onLongClick = { editing = item }
                    )
                }
            }
            if (state.bought.isNotEmpty()) {
                item(key = "bought_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp)
                            .animateItem(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.shopping_bought_section, state.bought.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            viewModel.clearBought { removed ->
                                offerUndo(resources.getString(R.string.shopping_cleared, removed.size), removed)
                            }
                        }) {
                            Text(stringResource(R.string.shopping_clear_bought))
                        }
                    }
                }
                itemsIndexed(state.bought, key = { _, item -> item.id }) { index, item ->
                    SwipeToDelete(
                        onDelete = {
                            viewModel.delete(item)
                            offerUndo(resources.getString(R.string.shopping_deleted, item.text), listOf(item))
                        },
                        shape = groupShape(index, state.bought.size),
                        modifier = Modifier.animateItem()
                    ) {
                        ItemRow(
                            item = item,
                            shape = groupShape(index, state.bought.size),
                            onClick = { viewModel.toggle(item) },
                            onLongClick = { editing = item }
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        ListDialog.NEW -> NameDialog(
            title = R.string.shopping_list_new,
            initial = "",
            onConfirm = { name -> viewModel.createList(name); dialog = null },
            onDismiss = { dialog = null }
        )
        ListDialog.RENAME -> NameDialog(
            title = R.string.shopping_list_rename,
            initial = state.selected?.name.orEmpty(),
            onConfirm = { name -> viewModel.renameSelected(name); dialog = null },
            onDismiss = { dialog = null }
        )
        ListDialog.DELETE -> AppAlertDialog(
            onDismissRequest = { dialog = null },
            text = { Text(stringResource(R.string.shopping_list_delete_confirm, state.selected?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(); dialog = null }) {
                    Text(stringResource(R.string.shopping_list_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
        null -> Unit
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            onSave = { text, quantity -> viewModel.update(item, text, quantity); editing = null },
            onDelete = {
                viewModel.delete(item)
                offerUndo(resources.getString(R.string.shopping_deleted, item.text), listOf(item))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/**
 * Пункты одного раздела выглядят как одна карточка: у первого скруглён верх, у последнего низ,
 * между ними узкая щель (как в новых настройках Android).
 */
private fun groupShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner
    )
}

/** Смахнуть пункт влево — удалить. Под ним видна красная полоса с корзиной. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, shape: Shape, modifier: Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        modifier = modifier
    ) {
        content()
    }
}

/** Пункт: кружок-отметка, название, количество. Нажатие — куплено, долгое нажатие — изменить. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(item: ShoppingItem, shape: Shape, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundCheck(checked = item.checked)
            Spacer(Modifier.width(16.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.quantity.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.quantity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Круглая отметка: пустой кружок или зелёный с галочкой. */
@Composable
private fun RoundCheck(checked: Boolean) {
    if (checked) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    } else {
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}

/**
 * Поле "Добавить покупку" внизу, над клавиатурой. Enter добавляет и оставляет клавиатуру открытой.
 * Пока поле в фокусе — над ним подсказки из прошлых покупок.
 */
@Composable
private fun AddBar(value: String, onValueChange: (String) -> Unit, suggestions: List<String>, onAdd: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        // В стеклянном стиле у полосы нет своего фона, иначе она закрывает пятна
        color = if (LocalGlass.current) Color.Transparent else MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (focused && suggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(suggestions) { suggestion ->
                        ChoicePill(
                            text = suggestion,
                            icon = R.drawable.ic_add,
                            selected = false,
                            onClick = { onAdd(suggestion) },
                            unselectedColor = MaterialTheme.colorScheme.secondaryContainer,
                            horizontalPadding = 14.dp
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    // Свой обработчик Enter: добавляем, клавиатура не закрывается
                    keyboardActions = KeyboardActions(onDone = { onAdd(value) }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused }
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { onAdd(value) },
                    enabled = value.isNotBlank(),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.shopping_add))
                }
            }
        }
    }
}

/** Окно с одним полем: название списка. */
@Composable
private fun NameDialog(@StringRes title: Int, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shopping_list_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) })
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

/** Изменить пункт: название и количество ("2 л", "3 шт"), или удалить. */
@Composable
private fun ItemDialog(item: ShoppingItem, onSave: (String, String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(item.text) }
    var quantity by remember { mutableStateOf(item.quantity) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_item_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.shopping_item_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.take(20) },
                    label = { Text(stringResource(R.string.shopping_item_quantity)) },
                    placeholder = { Text(stringResource(R.string.shopping_item_quantity_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text, quantity) })
                )
                TextButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.shopping_item_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, quantity) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
