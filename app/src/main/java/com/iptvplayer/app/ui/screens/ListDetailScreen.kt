package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.SeriesInfo
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import com.iptvplayer.app.util.containsFolded

/** Jedna rozwiązana pozycja listy (id+kind z ListItemRef dopasowane do faktycznego
 * kanału/filmu/serialu z aktualnie wczytanej treści) — pozycje, których źródło już nie
 * ma w playliście (np. usunięty kanał u providera), są po prostu pomijane w widoku. */
private sealed class ResolvedItem(val id: String, val title: String, val poster: String?) {
    class Live(val channel: Channel) : ResolvedItem(channel.id, channel.name, channel.logoUrl)
    class Movie(val channel: Channel) : ResolvedItem(channel.id, channel.name, channel.logoUrl)
    class Series(val info: SeriesInfo) : ResolvedItem(info.id, info.name, info.posterUrl)
}

private fun resolveItems(
    list: CustomList,
    liveChannels: List<Channel>,
    movies: List<Channel>,
    series: List<SeriesInfo>,
): List<ResolvedItem> {
    val liveById = liveChannels.associateBy { it.id }
    val moviesById = movies.associateBy { it.id }
    val seriesById = series.associateBy { it.id }
    return list.items.mapNotNull { ref ->
        when (ref.kind) {
            MediaKind.LIVE -> liveById[ref.id]?.let { ResolvedItem.Live(it) }
            MediaKind.MOVIE -> moviesById[ref.id]?.let { ResolvedItem.Movie(it) }
            MediaKind.SERIES -> seriesById[ref.id]?.let { ResolvedItem.Series(it) }
        }
    }
}

/**
 * Ekran pojedynczej listy ("Święta", "Akcja"…) — odpowiednik SeriesDetailScreen/
 * MovieDetailScreen jako nakładka nad zakładkami. Nagłówek: wstecz + emoji+nazwa
 * (edytowalna dla list niewbudowanych) + usuń listę. Body: siatka plakatów (ten sam
 * PosterCard co wszędzie indziej — gwiazdka i długi OK dalej znaczą "Ulubione", tak
 * jak w całej appce) + tryb zaznaczania do hurtowego usuwania z TEJ listy + "Dodaj
 * pozycje" otwierający prosty wyszukiwacz po całej bibliotece.
 */
@Composable
fun ListDetailScreen(
    list: CustomList,
    liveChannels: List<Channel>,
    movies: List<Channel>,
    series: List<SeriesInfo>,
    favorites: Set<String>,
    onBack: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayLive: (Channel) -> Unit,
    onOpenMovie: (Channel) -> Unit,
    onOpenSeries: (SeriesInfo) -> Unit,
    onRename: (String) -> Unit,
    onSetEmoji: (String) -> Unit,
    onDeleteList: () -> Unit,
    onRemoveItems: (List<String>) -> Unit,
    onAddItem: (id: String, kind: MediaKind) -> Unit,
    onRemoveItem: (id: String) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val resolved = remember(list, liveChannels, movies, series) {
        resolveItems(list, liveChannels, movies, series)
    }

    if (addMode) {
        AddItemsPicker(
            list = list,
            liveChannels = liveChannels,
            movies = movies,
            series = series,
            onToggleItem = { id, kind, present -> if (present) onRemoveItem(id) else onAddItem(id, kind) },
            onDone = { addMode = false },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // --- pasek góra: wstecz + emoji + nazwa (edytowalna) + usuń listę ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = tr("common.back"), tint = TextMain)
            }
            Text(list.emoji ?: "🎬", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                list.name,
                color = TextMain,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (!list.isBuiltIn) {
                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                    Icon(Icons.Default.Edit, contentDescription = tr("listDetail.renameDesc"), tint = TextDim)
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                    Icon(Icons.Default.Delete, contentDescription = tr("listDetail.deleteListDesc"), tint = Danger)
                }
            }
        }

        // --- pasek akcji: Dodaj pozycje / Zaznacz ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { addMode = true },
                modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(10.dp)),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("listDetail.addItems"))
            }
            if (resolved.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        selectionMode = !selectionMode
                        selectedIds = emptySet()
                    },
                    modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(10.dp)),
                ) {
                    Text(if (selectionMode) tr("common.cancel") else tr("grid.selectionToggle.select"))
                }
            }
        }

        if (selectionMode) {
            SelectionToolbar(
                selectedCount = selectedIds.size,
                actionLabel = tr("listDetail.removeFromList"),
                actionIcon = Icons.Default.RemoveCircle,
                isDestructive = true,
                onAction = {
                    onRemoveItems(selectedIds.toList())
                    selectedIds = emptySet()
                    selectionMode = false
                },
                onCancel = { selectionMode = false; selectedIds = emptySet() },
            )
        }

        Spacer(Modifier.height(6.dp))

        if (resolved.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    // Rozróżnienie DWÓCH różnych sytuacji, które wcześniej wyglądały
                    // identycznie: (1) lista faktycznie nie ma żadnych zapisanych pozycji —
                    // pokazuje zachętę do dodania pierwszych; (2) lista MA zapisane pozycje
                    // (list.items.isNotEmpty()), ale resolveItems akurat nic nie dopasowało w
                    // aktualnie załadowanym katalogu (liveChannels/movies/series) — dzieje się
                    // to PRZEJŚCIOWO, np. w trakcie odświeżania playlisty w tle, gdy katalog
                    // jest chwilowo pusty/niepełny. Zgłoszenie: "wszystkie pozycje zniknęły,
                    // chociaż w podpisie listy zostało że są tam pozycje" — bez tego
                    // rozróżnienia appka myląco sugerowała, że lista się wyczyściła, mimo że
                    // same zapisane odniesienia (list.items) nigdy nie zostały naruszone.
                    if (list.items.isEmpty()) tr("listDetail.emptyHint") else tr("listDetail.syncingHint"),
                    color = TextDim,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                // Ten sam powód co w GridScreens.kt — Adaptive zamiast sztywnego Fixed(3),
                // żeby plakaty na dużym ekranie TV nie robiły się nieproporcjonalnie ogromne.
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(resolved, key = { it.id }) { item ->
                    if (selectionMode) {
                        SelectablePosterCard(
                            title = item.title,
                            posterUrl = item.poster,
                            selected = item.id in selectedIds,
                            onToggle = {
                                selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                            },
                        )
                    } else {
                        PosterCard(
                            title = item.title,
                            posterUrl = item.poster,
                            isFavorite = item.id in favorites,
                            onToggleFavorite = { onToggleFavorite(item.id) },
                            onClick = {
                                when (item) {
                                    is ResolvedItem.Live -> onPlayLive(item.channel)
                                    is ResolvedItem.Movie -> onOpenMovie(item.channel)
                                    is ResolvedItem.Series -> onOpenSeries(item.info)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameListDialog(
            currentName = list.name,
            currentEmoji = list.emoji ?: "🎬",
            onConfirm = { name, emoji ->
                onRename(name)
                onSetEmoji(emoji)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(tr("listDetail.deleteConfirmTitle", list.name)) },
            text = { Text(tr("listDetail.deleteConfirmText")) },
            // TvDialogButton — widoczny fokus pod pilota, start na "Anuluj" (patrz komentarz
            // w TvDialogButton.kt: bez tego "w prawo + OK" trafiało w niewidocznie
            // zaznaczone "Anuluj" i lista się nie usuwała).
            confirmButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(
                    text = tr("common.delete"),
                    color = Danger,
                    onClick = { showDeleteConfirm = false; onDeleteList() },
                )
            },
            dismissButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(
                    text = tr("common.cancel"),
                    initialFocus = true,
                    onClick = { showDeleteConfirm = false },
                )
            },
        )
    }
}

/** Wariant PosterCard używany w trybie zaznaczania — cała karta jest klikalna (przełącza
 * zaznaczenie), z checkboxem w rogu zamiast gwiazdki. Gwiazdka/ulubione są tu celowo
 * ukryte, żeby nie było dwóch sprzecznych znaczeń kliknięcia na tej samej karcie. Bez
 * `private`, bo tego samego komponentu używa też tryb zaznaczania w GridScreens.kt. */
@Composable
fun SelectablePosterCard(title: String, posterUrl: String?, selected: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .tvFocusFrame(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2),
        ) {
            if (posterUrl != null) {
                AsyncImage(model = posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(title.take(2).uppercase(), color = TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.45f)))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) Accent else Bg.copy(alpha = 0.55f))
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = if (selected) Bg else TextDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextMain, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RenameListDialog(
    currentName: String,
    currentEmoji: String,
    onConfirm: (name: String, emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var emoji by remember { mutableStateOf(currentEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("listDetail.renameDialogTitle")) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("addToList.nameLabel")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand(),
                )
                Spacer(Modifier.height(10.dp))
                Text(tr("addToList.iconLabel"), color = TextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LIST_EMOJI_CHOICES) { e ->
                        val sel = e == emoji
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (sel) Accent else Surface2)
                                .tvFocusFrame(shape = CircleShape)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(e, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { currentName }, emoji) }) { Text(tr("common.save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("common.cancel")) }
        },
    )
}

/** Sealed helper analogiczny do GlobalItem w GlobalSearch.kt — jeden ujednolicony wynik
 * niezależnie od typu, do wyszukiwarki "Dodaj pozycje" wewnątrz listy. */
private sealed class PickItem {
    abstract val id: String
    abstract val name: String
    abstract val group: String?
    abstract val logo: String?
    abstract val kind: MediaKind

    data class Live(val channel: Channel) : PickItem() {
        override val id get() = channel.id
        override val name get() = channel.name
        override val group get() = channel.group
        override val logo get() = channel.logoUrl
        override val kind get() = MediaKind.LIVE
    }
    data class Movie(val channel: Channel) : PickItem() {
        override val id get() = channel.id
        override val name get() = channel.name
        override val group get() = channel.group
        override val logo get() = channel.logoUrl
        override val kind get() = MediaKind.MOVIE
    }
    data class Series(val info: SeriesInfo) : PickItem() {
        override val id get() = info.id
        override val name get() = info.name
        override val group get() = info.group
        override val logo get() = info.posterUrl
        override val kind get() = MediaKind.SERIES
    }
}

/** Prosty wyszukiwacz po całej bibliotece (live + filmy + seriale) do dociągania pozycji
 * do listy — odpowiednik GlobalSearchResults, tylko że stuknięcie w wynik nie otwiera
 * odtwarzania/szczegółów, tylko przełącza obecność na TEJ liście (checkbox). */
@Composable
private fun AddItemsPicker(
    list: CustomList,
    liveChannels: List<Channel>,
    movies: List<Channel>,
    series: List<SeriesInfo>,
    onToggleItem: (id: String, kind: MediaKind, alreadyPresent: Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Wybrana kategoria (Live/Filmy/Seriale) — null = pokazujemy TRZY KAFELKI kategorii
    // zamiast płaskiej, wymieszanej listy wszystkiego naraz. Zgłoszenie: setki pozycji
    // różnych typów pod rząd, bez żadnego podziału, było bezużyteczne do przeglądania
    // (dawało się tylko wpisać dokładną nazwę). Wpisanie CZEGOKOLWIEK w wyszukiwarkę —
    // NIEZALEŻNIE od tego, czy kategoria jest wybrana — nadal przeszukuje (patrz niżej),
    // to jedyna część tego ekranu, której celowo nie ruszam.
    var selectedKind by remember { mutableStateOf<MediaKind?>(null) }
    val currentIds = remember(list) { list.items.map { it.id }.toSet() }

    val allItems = remember(liveChannels, movies, series) {
        buildList {
            liveChannels.forEach { add(PickItem.Live(it)) }
            movies.forEach { add(PickItem.Movie(it)) }
            series.forEach { add(PickItem.Series(it)) }
        }
    }
    val filtered = remember(allItems, query, selectedKind) {
        val scoped = if (selectedKind != null) allItems.filter { it.kind == selectedKind } else allItems
        if (query.isBlank()) scoped.take(200) else scoped.filter { it.name.containsFolded(query) }
    }
    // Dopóki nic nie wpisano I nie wybrano kategorii — pokazujemy KAFELKI, nie listę wcale
    // (lista i tak by nic sensownego nie pokazała: setki pozycji różnych typów pod rząd).
    val showCategoryPicker = selectedKind == null && query.isBlank()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                // Wewnątrz wybranej kategorii Wstecz najpierw wraca do kafelków, dopiero
                // drugi raz zamyka cały ekran — ten sam, przewidywalny wzorzec co w resztę
                // appki (Wstecz nigdy nie "przeskakuje" od razu przez dwa poziomy naraz).
                onClick = { if (selectedKind != null) selectedKind = null else onDone() },
                modifier = Modifier.tvFocusFrame(shape = CircleShape),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = tr("common.done"), tint = TextMain)
            }
            Text(
                tr("listDetail.addToListTitle", list.name),
                color = TextMain,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(tr("listDetail.searchLibraryPlaceholder"), color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    inner()
                },
                modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand(),
            )
        }
        if (showCategoryPicker) {
            Column(Modifier.padding(16.dp)) {
                AddItemsCategoryTile(
                    label = tr("tab.live"),
                    count = liveChannels.size,
                    onClick = { selectedKind = MediaKind.LIVE },
                )
                Spacer(Modifier.height(10.dp))
                AddItemsCategoryTile(
                    label = tr("tab.movies"),
                    count = movies.size,
                    onClick = { selectedKind = MediaKind.MOVIE },
                )
                Spacer(Modifier.height(10.dp))
                AddItemsCategoryTile(
                    label = tr("tab.series"),
                    count = series.size,
                    onClick = { selectedKind = MediaKind.SERIES },
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { pick ->
                    val present = pick.id in currentIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusFrame(shape = RoundedCornerShape(10.dp))
                            .clickable { onToggleItem(pick.id, pick.kind, present) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Surface2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pick.logo != null) {
                                AsyncImage(model = pick.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(pick.name.take(2).uppercase(), color = TextDim, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pick.name, color = TextMain, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pick.group ?: tr("globalSearch.noCategory"), color = TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(checked = present, onCheckedChange = { onToggleItem(pick.id, pick.kind, present) })
                    }
                }
            }
        }
    }
}

/** Pojedynczy kafelek kategorii (Live/Filmy/Seriale) na starcie ekranu "Dodaj pozycje" —
 * patrz showCategoryPicker w AddItemsPicker wyżej. */
@Composable
private fun AddItemsCategoryTile(label: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .tvFocusFrame(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextMain, style = MaterialTheme.typography.bodyLarge)
        Text("$count", color = TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}
