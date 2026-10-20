package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.data.EpgProgram
import com.iptvplayer.app.data.EpgRepository
import com.iptvplayer.app.data.ListItemRef
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import com.iptvplayer.app.util.containsFolded
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Format godzin współdzielony przez wiersz kanału (teraz/dalej) i dialog pełnego przewodnika.
val epgTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * Odpowiednik zakładki Live z app.js: search-bar, chipy kategorii (group-title),
 * lista kanałów z logo i podpisem "teraz leci" pobieranym z EPG (Xtream get_short_epg / XMLTV).
 */
@Composable
fun LiveScreen(
    channels: List<Channel>,
    favorites: Set<String>,
    // URL EPG jest właściwością PLAYLISTY (PlaylistSource.epgUrl), nie stały globalnie —
    // różne playlisty mogą mieć różne źródła EPG. Wcześniej przekazywano tu jeden epgUrl
    // brany z pierwszej playlisty, co dawało błędne dane dla kanałów z innych playlist.
    epgUrlFor: (Channel) -> String? = { null },
    epgRepository: EpgRepository,
    onToggleFavorite: (String) -> Unit,
    onPlay: (Channel) -> Unit,
    globalSearchEnabled: Boolean = false,
    onToggleGlobalSearch: (Boolean) -> Unit = {},
    allMovies: List<Channel> = emptyList(),
    allSeries: List<com.iptvplayer.app.data.SeriesInfo> = emptyList(),
    onOpenSeries: (com.iptvplayer.app.data.SeriesInfo) -> Unit = {},
    onOpenMovie: (Channel) -> Unit = {},
    // Własne reguły wyszukiwania nastrojowego z Ustawień — patrz GlobalSearchResults.
    customMoodRules: List<Pair<String, String>> = emptyList(),
    watchProgress: Map<String, com.iptvplayer.app.data.WatchProgress> = emptyMap(),
    onResumeWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    onRemoveWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    // Cel fokusu przy zejściu pilotem W DÓŁ z dolnego paska nawigacji — patrz jego jawne
    // podłączenie w RootScreen (MainActivity.kt). Bez tego jedyną drogą z paska nawigacji
    // "do góry" ekranu było naciśnięcie GÓRA, które przez domyślne, geometryczne
    // wyszukiwanie kierunkowe Compose czasem lądowało gdzieś w środku przewiniętej listy
    // kanałów, zamiast na samej górze (zgłoszenie). Pole wyszukiwania jest ZAWSZE na górze,
    // niezależnie od przewinięcia listy niżej — stąd to naturalny, przewidywalny cel.
    topFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    onCreateList: (name: String, emoji: String) -> CustomList = { n, e -> CustomList(id = "", name = n, emoji = e) },
    // Otwiera pełną siatkę EPG (kanały × oś czasu, styl TiviMate) — patrz EpgGridScreen.kt,
    // rysowana jako nakładka na poziomie RootScreen (MainActivity), nie tutaj, żeby mieć
    // od razu dostęp do pełnej listy playlist (potrzebnej do odtwarzania w podglądzie).
    // Otwiera pełną siatkę EPG (kanały × oś czasu, styl TiviMate) — patrz EpgGridScreen.kt,
    // rysowana jako nakładka na poziomie RootScreen (MainActivity). Przekazuje AKTUALNIE
    // WIDOCZNĄ listę (po kategorii/ulubionych/wyszukiwaniu, patrz `filtered` niżej), nie
    // wszystkie kanały — użytkownik, który akurat patrzy na kategorię "Informacyjne", chce
    // pełny EPG DLA TEJ kategorii, nie dla całej playlisty z setkami niezwiązanych kanałów.
    onOpenEpgGrid: (List<Channel>) -> Unit = {},
    // Uruchamia "cofnij" — odtwarza wybrany program od początku (patrz EpgGuideDialog).
    // null przekazane przez MainActivity, gdy kanał nie wspiera catch-up (Xtream, tv_archive).
    onWatchCatchup: (channel: Channel, startMillis: Long, durationMinutes: Int) -> Unit = { _, _, _ -> },
    autoCloseAddToList: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    // Rozwinięty widok WSZYSTKICH kategorii w siatce — patrz przycisk obok poziomego paska
    // niżej. Zwinięty domyślnie (zachowanie jak dotychczas), bo większość użytkowników i tak
    // ma tylko kilka kategorii, dla których poziomy pasek wystarcza.
    var showAllCategoriesExpanded by remember { mutableStateOf(false) }
    // "+" na wierszu kanału — id kanału, dla którego akurat pokazane jest okno "Dodaj do listy".
    var addToListTargetId by remember { mutableStateOf<String?>(null) }
    // Osobny stan dla "Dodaj do listy" z poziomu wyników wyszukiwania globalnego — TYP
    // ListItemRef (nie sam String jak addToListTargetId wyżej), bo global search z poziomu
    // Live może zwrócić też filmy/seriale, nie tylko kanały (addToListTargetId zakłada
    // niejawnie kind=LIVE, tutaj kind bywa różny zależnie od wybranego wyniku).
    var globalSearchAddTarget by remember { mutableStateOf<ListItemRef?>(null) }

    val groups = remember(channels) { channels.mapNotNull { it.group }.distinct().sorted() }
    val groupCounts = remember(channels) { channels.mapNotNull { it.group }.groupingBy { it }.eachCount() }

    val filtered = remember(channels, query, selectedGroup, favOnly, favorites) {
        channels.filter { ch ->
            (query.isBlank() || ch.name.containsFolded(query)) &&
                (selectedGroup == null || ch.group == selectedGroup) &&
                (!favOnly || ch.id in favorites)
        }
    }
    // Okruszek do loga awarii (patrz CrashLogger, Ustawienia -> Log błędów) — zmiana grupy
    // to jedno z miejsc, gdzie zgłaszano powtarzające się awarie appki; złapie to
    // niezależnie, którą ścieżką użytkownik wybrał grupę (kompaktowy pasek czy rozwinięta
    // siatka), bez duplikowania wywołania w obu miejscach osobno.
    LaunchedEffect(selectedGroup, favOnly) {
        com.iptvplayer.app.data.CrashLogger.breadcrumb("live", "grupa=$selectedGroup ulubione=$favOnly (${filtered.size} kanałów)")
    }

    val showingGlobalResults = globalSearchEnabled && query.isNotBlank()
    // Gdy trwa wpisywanie zapytania, chowamy chipy i "Kontynuuj oglądanie", żeby wyniki
    // wyszukiwania nie chowały się pod klawiaturą.
    val isSearching = query.isNotBlank()
    val continueWatchingLive = remember(watchProgress) {
        watchProgress.values.filter { it.kind == com.iptvplayer.app.data.MediaKind.LIVE }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // --- search-bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextDim, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextFieldCompat(query, onValueChange = { query = it }, placeholder = tr("live.search.placeholder"), focusRequester = topFocusRequester)
        }

        // Wejście do pełnej siatki EPG (kanały × oś czasu, styl TiviMate) — dopełnienie
        // istniejącej ikony ⓘ przy pojedynczym kanale (ta pokazuje tylko jeden wiersz).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .tvFocusFrame(shape = RoundedCornerShape(10.dp))
                .clickable(onClick = { onOpenEpgGrid(filtered) })
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = TextMain, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("epgGrid.openButton"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))

        GlobalSearchToggleRow(enabled = globalSearchEnabled, onToggle = onToggleGlobalSearch)
        Spacer(Modifier.height(6.dp))

        if (showingGlobalResults) {
            GlobalSearchResults(
                query = query,
                liveChannels = channels,
                movies = allMovies,
                series = allSeries,
                favorites = favorites,
                onAddToList = { id, kind -> globalSearchAddTarget = ListItemRef(id = id, kind = kind) },
                onPlayChannel = onPlay,
                onOpenSeries = onOpenSeries,
                onOpenMovie = onOpenMovie,
                customMoodRules = customMoodRules,
            )
        } else {
            // --- chipy, "Kontynuuj oglądanie" i lista kanałów są teraz WEWNĄTRZ jednej,
            // wspólnej LazyColumn (jako elementy nagłówkowe) — dzięki temu scrollują się
            // razem z listą zamiast zostawać na stałe nad nią i zabierać miejsce na ekranie. ---
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                // --- chips: Ulubione + kategorie (ukryte podczas wyszukiwania) ---
                if (!isSearching) {
                    item {
                        Column {
                            // Lista WSZYSTKICH chipów (Ulubione + Wszystkie + każda kategoria)
                            // w jednej kolekcji — używana zarówno do wąskiego poziomego paska,
                            // jak i do rozwiniętego widoku siatki niżej, żeby nie duplikować
                            // definicji tych samych przycisków w dwóch miejscach.
                            // Prosta lokalna klasa zamiast Triple(...) { lambda } — jawna,
                            // pewna składnia zamiast polegania na wnioskowaniu typu Kotlina
                            // dla trzeciego parametru konstruktora z końcową lambdą.
                            data class ChipDef(val label: String, val active: Boolean, val onClick: () -> Unit)
                            val allChipDefs = buildList {
                                add(ChipDef(tr("live.filter.favorites"), favOnly) { favOnly = !favOnly })
                                add(ChipDef(tr("live.filter.all", channels.size), selectedGroup == null) { selectedGroup = null })
                                groups.forEach { g ->
                                    add(ChipDef(tr("live.filter.groupCount", g, groupCounts[g] ?: 0), selectedGroup == g) { selectedGroup = g })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rozwija/chowa siatkę WSZYSTKICH kategorii naraz niżej — bez
                                // tego jedynym sposobem dotarcia do dalszej kategorii było
                                // przewijanie poziome przez dziesiątki pozycji (zgłoszenie).
                                // CELOWO PO LEWEJ (nie za listą chipów) — przy playlistach z
                                // dziesiątkami/setkami kategorii pilot musiałby przejść przez
                                // WSZYSTKIE z nich, żeby dotrzeć do przycisku po prawej
                                // (kolejne zgłoszenie: "daleko potrzeba przechodzić przez
                                // wszystkie grupy"). Po lewej to pierwszy przystanek D-pada.
                                IconButton(
                                    onClick = { showAllCategoriesExpanded = !showAllCategoriesExpanded },
                                    modifier = Modifier.padding(start = 16.dp).tvFocusFrame(shape = CircleShape),
                                ) {
                                    Text(
                                        if (showAllCategoriesExpanded) "\u25B4" else "\u25BE",
                                        color = TextDim,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item {
                                        FilterChipCompat(label = tr("live.filter.favorites"), active = favOnly, accent = Accent2, onClick = { favOnly = !favOnly })
                                    }
                                    item {
                                        FilterChipCompat(label = tr("live.filter.all", channels.size), active = selectedGroup == null, onClick = { selectedGroup = null })
                                    }
                                    items(groups) { g ->
                                        FilterChipCompat(label = tr("live.filter.groupCount", g, groupCounts[g] ?: 0), active = selectedGroup == g, onClick = { selectedGroup = g })
                                    }
                                }
                            }
                            if (showAllCategoriesExpanded) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    // Ręczne dzielenie na wiersze po 2 zamiast FlowRow — pewność,
                                    // że się skompiluje, bez polegania na niesprawdzonej wcześniej
                                    // w tym projekcie zależności. Kosztem odrobiny nierównego
                                    // wyglądu przy różnej długości etykiet, nie funkcjonalności.
                                    allChipDefs.chunked(2).forEach { rowChips ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            rowChips.forEach { (label, active, onClick) ->
                                                // Wybór DOWOLNEJ kategorii od razu zwija
                                                // siatkę z powrotem — zgłoszenie: po wyborze
                                                // grupy lista powinna się zwinąć.
                                                FilterChipCompat(
                                                    label = label,
                                                    active = active,
                                                    onClick = { onClick(); showAllCategoriesExpanded = false },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // --- Kontynuuj oglądanie (tylko żywe kanały) — ukryte podczas wyszukiwania ---
                if (!isSearching && continueWatchingLive.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                tr("home.continueWatching.title"),
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            ContinueWatchingRow(
                                items = continueWatchingLive,
                                onClick = onResumeWatching,
                                onRemove = onRemoveWatching,
                                favorites = favorites,
                                onToggleFavorite = { onToggleFavorite(it.channelOrEpisodeId) },
                                onAddToList = { addToListTargetId = it.channelOrEpisodeId },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tr("live.empty.noChannels"), color = TextDim)
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { channel ->
                        LiveChannelRow(
                            channel = channel,
                            isFavorite = channel.id in favorites,
                            epgUrl = epgUrlFor(channel),
                            epgRepository = epgRepository,
                            onToggleFavorite = { onToggleFavorite(channel.id) },
                            onClick = { onPlay(channel) },
                            onAddToList = { addToListTargetId = channel.id },
                        )
                    }
                }
            }
        }
    }

    if (addToListTargetId != null) {
        val targetId = addToListTargetId!!
        val refs = listOf(ListItemRef(id = targetId, kind = MediaKind.LIVE))
        val alreadyIn = remember(customLists, targetId) {
            customLists.filter { list -> list.items.any { it.id == targetId } }.map { it.id }.toSet()
        }
        AddToListSheet(
            lists = customLists,
            alreadyInListIds = alreadyIn,
            autoCloseOnSelect = autoCloseAddToList,
            onToggleList = { listId -> onToggleManyInList(listId, refs) },
            onCreateList = { name, emoji ->
                val newList = onCreateList(name, emoji)
                onToggleManyInList(newList.id, refs)
            },
            onDismiss = { addToListTargetId = null },
        )
    }
    if (globalSearchAddTarget != null) {
        val target = globalSearchAddTarget!!
        val alreadyIn = remember(customLists, target) {
            customLists.filter { list -> list.items.any { it.id == target.id } }.map { it.id }.toSet()
        }
        AddToListSheet(
            lists = customLists,
            alreadyInListIds = alreadyIn,
            autoCloseOnSelect = autoCloseAddToList,
            onToggleList = { listId -> onToggleManyInList(listId, listOf(target)) },
            onCreateList = { name, emoji ->
                val newList = onCreateList(name, emoji)
                onToggleManyInList(newList.id, listOf(target))
            },
            onDismiss = { globalSearchAddTarget = null },
        )
    }
}

@Composable
private fun LiveChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    epgUrl: String?,
    epgRepository: EpgRepository,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onAddToList: () -> Unit,
) {
    var nowPlaying by remember(channel.id, epgUrl) { mutableStateOf<EpgProgram?>(null) }
    var nextProgram by remember(channel.id, epgUrl) { mutableStateOf<EpgProgram?>(null) }
    val hasEpgSource = epgUrl != null && channel.epgChannelId != null

    LaunchedEffect(channel.id, epgUrl) {
        val chId = channel.epgChannelId
        if (epgUrl != null && chId != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                nowPlaying = runCatching { epgRepository.getNowPlaying(epgUrl, chId) }.getOrNull()
                nextProgram = runCatching { epgRepository.getNextProgram(epgUrl, chId) }.getOrNull()
            }
        } else {
            nowPlaying = null
            nextProgram = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusFrame(shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(channel.name.take(2).uppercase(), color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = TextMain, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val current = nowPlaying
            Text(
                if (current != null) {
                    "${epgTimeFormat.format(Date(current.startMillis))}–${epgTimeFormat.format(Date(current.endMillis))} ${current.title}"
                } else {
                    "—"
                },
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val next = nextProgram
            if (next != null) {
                Text(
                    tr("live.nextProgram", epgTimeFormat.format(Date(next.startMillis)), next.title),
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Przycisk "ⓘ" (przewodnik na dziś dla pojedynczego kanału) CELOWO USUNIĘTY —
        // powodował ciągłe zawieszanie/zamykanie aplikacji (zgłoszenie). Pełny grid EPG
        // (przycisk "EPG" w Live) działa dobrze i zostaje jedynym sposobem sprawdzenia
        // programu. hasEpgSource/nowPlaying/nextProgram wyżej zostają — nadal zasilają
        // samą etykietę "teraz leci" pod nazwą kanału, tylko już bez klikalnej ikony.
        // Gwiazdka ulubionych została usunięta — dodawanie do Ulubionych (i do dowolnej
        // innej listy) odbywa się teraz przez ten sam znaczek "+", który otwiera okno
        // "Dodaj do listy" (patrz onAddToList w LiveScreen), gdzie Ulubione jest zawsze
        // pierwszą, niemożliwą do usunięcia pozycją do wyboru.
        IconButton(
            onClick = onAddToList,
            modifier = Modifier.tvFocusFrame(shape = CircleShape),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = tr("common.addToList"),
                tint = if (isFavorite) Accent2 else TextDim,
            )
        }
    }
}

/**
 * Pełny przewodnik programowy na dziś dla jednego kanału — lista programów z godzinami,
 * aktualny program podświetlony. Otwierany ikoną ⓘ na wierszu kanału (patrz LiveChannelRow)
 * lub bezpośrednio z odtwarzacza podczas oglądania na pełnym ekranie (przycisk EPG w górnym
 * pasku PlayerActivity, patrz osobny plik) — nie zastępuje odtwarzania (zwykłe dotknięcie
 * wiersza nadal od razu odtwarza kanał).
 *
 * Celowo przyjmuje proste, luźne parametry zamiast całego obiektu Channel — Channel żyje
 * w pakiecie ui.screens, a PlayerActivity jest w osobnym pakiecie player i nie ma (i nie
 * powinien mieć) po niego sięgać tylko po nazwę/tvg-id kanału.
 */
@Composable
fun EpgGuideDialog(
    channelName: String,
    epgChannelId: String?,
    epgUrl: String?,
    epgRepository: EpgRepository,
    onDismiss: () -> Unit,
    // null = kanał nie wspiera cofania (przycisk się nie pokazuje przy żadnym programie).
    // catchupDays ogranicza, jak daleko wstecz dostawca faktycznie trzyma nagrania — patrz
    // Channel.catchupDays, wypełniane z pól "tv_archive"/"tv_archive_duration" Xtream API.
    onWatchFromStart: ((startMillis: Long, durationMinutes: Int) -> Unit)? = null,
    catchupDays: Int = 0,
) {
    val cacheKey = epgChannelId ?: channelName
    var programs by remember(cacheKey, epgUrl) { mutableStateOf<List<EpgProgram>?>(null) }

    LaunchedEffect(cacheKey, epgUrl) {
        programs = if (epgUrl != null && epgChannelId != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { epgRepository.getTodayPrograms(epgUrl, epgChannelId) }.getOrNull().orEmpty()
            }
        } else {
            emptyList()
        }
    }

    val now = remember { System.currentTimeMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channelName) },
        text = {
            val list = programs
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                list.isEmpty() -> Text(tr("live.epgGuide.noData"), color = TextDim)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    // itemsIndexed + klucz złożony (indeks + startMillis) zamiast samego
                    // startMillis — prawdziwe pliki XMLTV od dostawców bywają niechlujne
                    // (zdarzają się dwa programy z DOKŁADNIE tym samym czasem startu, np.
                    // błąd po stronie dostawcy albo scalenie kilku źródeł). Sam startMillis
                    // jako klucz LazyColumn rzucał wtedy wyjątkiem "Key was already used" i
                    // wywalał całą aplikację — to była prawdopodobna przyczyna zgłoszonego
                    // crasha przy włączaniu EPG. Indeks w kluczu gwarantuje unikalność
                    // niezależnie od jakości danych źródłowych.
                    itemsIndexed(list, key = { index, program -> "${index}_${program.startMillis}" }) { _, program ->
                        val isNow = now in program.startMillis..program.endMillis
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "${epgTimeFormat.format(Date(program.startMillis))}–${epgTimeFormat.format(Date(program.endMillis))}",
                                    color = if (isNow) Accent else TextDim,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(96.dp),
                                )
                                Text(
                                    program.title,
                                    color = if (isNow) TextMain else TextDim,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!program.description.isNullOrBlank()) {
                                Text(
                                    program.description,
                                    color = TextDim,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 96.dp, top = 2.dp),
                                )
                            }
                            // "Cofnij" — tylko dla programów Z PRZESZŁOŚCI, tylko gdy kanał
                            // wspiera catch-up, i tylko w oknie faktycznie dostępnych nagrań
                            // (catchupDays) — poza tym oknem dostawca i tak nie ma już nagrania,
                            // więc przycisk by tylko kończył się błędem odtwarzania.
                            val catchupWindowStart = now - catchupDays * 24L * 60 * 60 * 1000
                            if (onWatchFromStart != null &&
                                program.endMillis < now &&
                                program.startMillis >= catchupWindowStart
                            ) {
                                TextButton(
                                    onClick = {
                                        val durationMinutes = ((program.endMillis - program.startMillis) / 60_000L)
                                            .toInt()
                                            .coerceAtLeast(1)
                                        onWatchFromStart(program.startMillis, durationMinutes)
                                    },
                                    modifier = Modifier.padding(start = 88.dp),
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr("live.catchup.watchFromStart"), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("common.close")) }
        },
    )
}

@Composable
fun FilterChipCompat(label: String, active: Boolean, accent: androidx.compose.ui.graphics.Color = Accent, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) accent else Surface)
            .tvFocusFrame(shape = RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (active) (if (accent == Accent2) Accent2OnColor else androidx.compose.ui.graphics.Color.White) else TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BasicTextFieldCompat(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 14.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = TextDim, style = MaterialTheme.typography.bodyMedium)
            inner()
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvKeyboardOnDemand(),
    )
}
