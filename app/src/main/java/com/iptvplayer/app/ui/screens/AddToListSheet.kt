package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand

/**
 * Zestaw emotek do wyboru przy tworzeniu/edycji listy — zamiast liczyć na klawiaturę
 * emoji (niewygodna albo w ogóle niedostępna z pilota na Google TV), użytkownik po
 * prostu wybiera z gotowej siatki dotykiem albo strzałkami pilota.
 *
 * Celowo BEZ gwiazdki (⭐) — to zarezerwowana ikonka wbudowanej listy Ulubione, więc
 * żeby uniknąć dwóch list wyglądających tak samo w "Moje listy", nie ma jej tutaj do
 * wyboru (patrz też AppViewModel.setListEmoji, który dodatkowo blokuje to od strony danych).
 */
val LIST_EMOJI_CHOICES = listOf(
    "🎬", "🍿", "📺", "❤️", "🎄", "🎃", "💥",
    "😂", "😱", "🕵️", "🚀", "⚽", "🎵", "👨\u200d👩\u200d👧\u200d👦", "🏆",
)

/** Kilka gotowych akcentów kolorystycznych do wyboru dla listy — bez pełnego selektora
 * kolorów (na pilocie byłby niewygodny), po prostu paleta próbek do stuknięcia. */
val LIST_COLOR_CHOICES = listOf(
    Accent, Accent2, Danger,
    androidx.compose.ui.graphics.Color(0xFF7ED957), // zielony
    androidx.compose.ui.graphics.Color(0xFFB388FF), // fioletowy
    androidx.compose.ui.graphics.Color(0xFFFF8A65), // pomarańczowy
)

/**
 * Okno "Dodaj do listy" — pokazuje wszystkie listy (Ulubione zawsze na górze) jako
 * zaznaczalne wiersze + na dole pole do szybkiego utworzenia nowej listy. Działa
 * identycznie dla pojedynczej pozycji i dla wielu naraz (z trybu zaznaczania) —
 * `alreadyInListIds` mówi, które listy są już zaznaczone (patrz
 * AppViewModel.listIdsContainingAll).
 */
@Composable
fun AddToListSheet(
    lists: List<CustomList>,
    alreadyInListIds: Set<String>,
    itemCountLabel: String? = null,
    onToggleList: (listId: String) -> Unit,
    onCreateList: (name: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
    // Gdy true: samo zaznaczenie/odznaczenie listy od razu zamyka okno, bez konieczności
    // dodatkowego naciśnięcia "Gotowe". Domyślnie false (zachowanie jak dotychczas) —
    // świadomy wybór użytkownika w Ustawieniach, bo część osób woli zaznaczyć KILKA list
    // na raz przed zamknięciem (patrz zgłoszenie), a to by to uniemożliwiało.
    autoCloseOnSelect: Boolean = false,
) {
    var showCreate by remember { mutableStateOf(false) }
    val sortedLists = remember(lists) {
        lists.sortedWith(compareByDescending<CustomList> { it.id == CustomList.FAVORITES_ID }.thenBy { it.createdAt })
    }
    fun toggleList(listId: String) {
        onToggleList(listId)
        if (autoCloseOnSelect) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(tr("addToList.title"))
                if (itemCountLabel != null) {
                    Text(itemCountLabel, style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        },
        text = {
            if (showCreate) {
                CreateListInline(
                    onConfirm = { name, emoji ->
                        onCreateList(name, emoji)
                        showCreate = false
                    },
                    onCancel = { showCreate = false },
                )
            } else {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(sortedLists, key = { it.id }) { list ->
                            val checked = list.id in alreadyInListIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusFrame(shape = RoundedCornerShape(8.dp))
                                    .clickable { toggleList(list.id) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(list.emoji ?: "🎬", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    list.name,
                                    color = TextMain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${list.items.size}",
                                    color = TextDim,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Checkbox(checked = checked, onCheckedChange = { toggleList(list.id) })
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusFrame(shape = RoundedCornerShape(8.dp))
                            .clickable { showCreate = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("common.newList"), color = Accent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            if (!showCreate) TextButton(onClick = onDismiss) { Text(tr("common.done")) }
        },
        dismissButton = null,
    )
}

/** Samodzielne okno tworzenia nowej, pustej listy — używane z kafelka "+" w sekcji
 * "Moje listy" na Home (bez kontekstu żadnej konkretnej pozycji do dodania). */
@Composable
fun CreateListDialog(onConfirm: (name: String, emoji: String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("common.newList")) },
        text = { CreateListInline(onConfirm = onConfirm, onCancel = onDismiss) },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun CreateListInline(onConfirm: (name: String, emoji: String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(LIST_EMOJI_CHOICES.first()) }
    // tr() jest @Composable i nie może być wołane bezpośrednio wewnątrz onClick (zwykła
    // lambda () -> Unit, nie kontekst kompozycji) — dlatego domyślną nazwę liczymy TUTAJ,
    // w ciele Composable, i tylko gotowy String przechwytujemy w onClick niżej.
    val defaultNewListName = tr("common.newList")

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(tr("addToList.nameLabel")) },
            placeholder = { Text(tr("addToList.namePlaceholder")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand(),
        )
        Spacer(Modifier.height(10.dp))
        Text(tr("addToList.iconLabel"), color = TextDim, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(LIST_EMOJI_CHOICES) { e ->
                val selected = e == emoji
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) Accent else Surface2)
                        .tvFocusFrame(shape = CircleShape)
                        .clickable { emoji = e },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp))) {
                Text(tr("common.cancel"))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onConfirm(name.ifBlank { defaultNewListName }, emoji) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("common.create"))
            }
        }
    }
}

/** Mały pasek narzędzi trybu wielokrotnego zaznaczania — pokazuje się nad siatką
 * plakatów, gdy użytkownik włączy "Zaznacz" (patrz PosterGridScreen w GridScreens.kt
 * i ListDetailScreen.kt, gdzie ten sam pasek służy do usuwania z listy). */
@Composable
fun SelectionToolbar(
    selectedCount: Int,
    actionLabel: String = tr("common.addToList"),
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Check,
    isDestructive: Boolean = false,
    onAction: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selectedCount == 0) tr("addToList.selectItems") else tr("addToList.selectedCount", selectedCount),
            color = TextMain,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel, modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp))) {
            Text(tr("common.cancel"))
        }
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onAction,
            enabled = selectedCount > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDestructive) Danger else Accent,
                contentColor = if (isDestructive) TextMain else Bg,
            ),
            modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
        ) {
            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(actionLabel)
        }
    }
}
