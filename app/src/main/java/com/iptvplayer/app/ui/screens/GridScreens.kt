package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.data.ListItemRef
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.SeriesInfo
import com.iptvplayer.app.data.forContinueWatching
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.notDpadFocusable
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import com.iptvplayer.app.util.containsFolded

/**
 * Odpowiednik zakładek Movies/Series z app.js: search-bar + chip-row (Ulubione / Wszystkie / kategorie)
 * dokładnie jak w renderToolbar() z iptv-web-player.html, plus siatka plakatów z gwiazdką ulubionych.
 */
@Composable
fun MoviesScreen(
    movies: List<Channel>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onPlay: (Channel) -> Unit,
    onDownload: (Channel) -> Unit = {},
    onOpenMovie: (Channel) -> Unit = {},
    // Własne reguły wyszukiwania nastrojowego z Ustawień — patrz GlobalSearchResults.
    customMoodRules: List<Pair<String, String>> = emptyList(),
    topFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    globalSearchEnabled: Boolean = false,
    onToggleGlobalSearch: (Boolean) -> Unit = {},
    allLive: List<Channel> = emptyList(),
    allSeries: List<SeriesInfo> = emptyList(),
    onOpenSeries: (SeriesInfo) -> Unit = {},
    watchProgress: Map<String, com.iptvplayer.app.data.WatchProgress> = emptyMap(),
    onResumeWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    onRemoveWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    // Pobieranie bezpośrednio z kafelka "Kontynuuj oglądanie" (ten sam plik co onDownload,
    // ale operuje na WatchProgress zamiast na Channel — patrz ContinueWatchingRow).
    onDownloadProgress: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    onCreateList: (name: String, emoji: String) -> CustomList,
    autoCloseAddToList: Boolean = false,
    // Patrz obszerny komentarz przy AppUiState.moviesSearchQuery (MainActivity.kt) —
    // trzymane w ViewModelu, nie lokalnie, żeby przetrwało otwarcie/zamknięcie szczegółów.
    query: String = "",
    onQueryChange: (String) -> Unit = {},
) {
    // Stan TYLKO dla "Dodaj do listy" z poziomu wyników wyszukiwania globalnego (patrz
    // globalResults niżej) — PosterGridScreen ma WŁASNY, wewnętrzny mechanizm tego samego
    // dialogu dla zwykłej siatki, ale globalResults renderuje się OBOK niej (osobna gałąź
    // ekranu wyszukiwania), bez dostępu do tamtego wewnętrznego stanu — stąd osobna,
    // niezależna kopia tego samego wzorca, nie próba dzielenia stanu między nimi.
    var globalSearchAddTarget by remember { mutableStateOf<ListItemRef?>(null) }
    // Kliknięcie plakatu otwiera ekran informacji o filmie (jak dla seriali) —
    // natychmiastowe odtwarzanie jest dostępne przez osobny przycisk ▶ na plakacie/karcie.
    PosterGridScreen(
        items = movies,
        favorites = favorites,
        searchPlaceholder = tr("grid.movies.searchPlaceholder"),
        emptyHint = tr("grid.movies.empty"),
        noResultsHint = tr("grid.noResultsHint"),
        id = { it.id },
        title = { it.name },
        group = { it.group },
        poster = { it.logoUrl },
        addedAt = { it.addedAt },
        kind = MediaKind.MOVIE,
        onToggleFavorite = onToggleFavorite,
        onClick = onOpenMovie,
        onQuickPlay = { item -> { onPlay(item) } },
        onDownload = onDownload,
        globalSearchEnabled = globalSearchEnabled,
        onToggleGlobalSearch = onToggleGlobalSearch,
        // Pliki lokalne (kind=MOVIE, ale streamUrl zaczyna się od "content://") świadomie
        // WYŁĄCZONE stąd — mają WŁASNĄ sekcję "Kontynuuj oglądanie" w zakładce Moje
        // (LocalFolderScreen), żeby nie mieszać dwóch zupełnie różnych źródeł treści w
        // jednej liście (zgłoszenie: lokalny plik pokazywał się w Filmy zamiast w Moje).
        continueWatching = watchProgress.values.filter {
            it.kind == com.iptvplayer.app.data.MediaKind.MOVIE && !it.streamUrl.startsWith("content://")
        },
        onResumeWatching = onResumeWatching,
        onRemoveWatching = onRemoveWatching,
        onDownloadProgress = onDownloadProgress,
        customLists = customLists,
        onToggleManyInList = onToggleManyInList,
        onCreateList = onCreateList,
        autoCloseAddToList = autoCloseAddToList,
        query = query,
        onQueryChange = onQueryChange,
        topFocusRequester = topFocusRequester,
        globalResults = { query ->
            GlobalSearchResults(
                query = query,
                liveChannels = allLive,
                movies = movies,
                series = allSeries,
                favorites = favorites,
                onAddToList = { id, kind -> globalSearchAddTarget = ListItemRef(id = id, kind = kind) },
                onPlayChannel = onPlay,
                onOpenSeries = onOpenSeries,
                onOpenMovie = onOpenMovie,
                customMoodRules = customMoodRules,
            )
        },
    )
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
fun SeriesScreen(
    series: List<SeriesInfo>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onOpen: (SeriesInfo) -> Unit,
    // Pobieranie całego sezonu z plakatu — otwiera dialog wyboru sezonu (patrz
    // MainActivity.requestSeriesSeasonDownload), tak jak przycisk pobierania na plakatach
    // filmów, tylko że tu najpierw trzeba wybrać, który sezon pobrać.
    onDownload: (SeriesInfo) -> Unit = {},
    globalSearchEnabled: Boolean = false,
    onToggleGlobalSearch: (Boolean) -> Unit = {},
    allLive: List<Channel> = emptyList(),
    allMovies: List<Channel> = emptyList(),
    onPlayChannel: (Channel) -> Unit = {},
    onOpenMovie: (Channel) -> Unit = {},
    // Własne reguły wyszukiwania nastrojowego z Ustawień — patrz GlobalSearchResults.
    customMoodRules: List<Pair<String, String>> = emptyList(),
    topFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    watchProgress: Map<String, com.iptvplayer.app.data.WatchProgress> = emptyMap(),
    onResumeWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    onRemoveWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    onCreateList: (name: String, emoji: String) -> CustomList,
    autoCloseAddToList: Boolean = false,
    // Patrz obszerny komentarz przy AppUiState.seriesSearchQuery (MainActivity.kt) —
    // trzymane w ViewModelu, nie lokalnie, żeby przetrwało otwarcie/zamknięcie szczegółów.
    query: String = "",
    onQueryChange: (String) -> Unit = {},
) {
    // Stan TYLKO dla "Dodaj do listy" z poziomu wyników wyszukiwania globalnego — ten sam
    // niezależny wzorzec co w MoviesScreen wyżej (patrz tamten komentarz).
    var globalSearchAddTarget by remember { mutableStateOf<ListItemRef?>(null) }
    // Ostatnio oglądany odcinek per serial (dla tych, które mają postęp) — do warunkowego
    // przycisku ▶ bezpośrednio na plakacie (patrz onQuickPlay niżej). Grupowanie po
    // seriesId, wybór najświeższego wpisu, dokładnie ta sama logika co forContinueWatching,
    // tylko zaindeksowana po ID serialu zamiast zwracana jako płaska lista.
    val latestProgressBySeriesId = remember(watchProgress) {
        watchProgress.values
            .filter { it.kind == com.iptvplayer.app.data.MediaKind.SERIES && it.seriesId != null }
            .groupBy { it.seriesId }
            .mapValues { (_, group) -> group.maxBy { it.updatedAt } }
    }
    PosterGridScreen(
        items = series,
        favorites = favorites,
        searchPlaceholder = tr("grid.series.searchPlaceholder"),
        emptyHint = tr("grid.series.empty"),
        noResultsHint = tr("grid.noResultsHint"),
        id = { it.id },
        title = { it.name },
        group = { it.group },
        poster = { it.posterUrl },
        addedAt = { it.addedAt },
        kind = MediaKind.SERIES,
        onToggleFavorite = onToggleFavorite,
        onClick = onOpen,
        onDownload = onDownload,
        // Przycisk ▶ na plakacie — TYLKO dla seriali z zapisanym postępem (patrz komentarz
        // przy onQuickPlay w PosterGridScreen). Działa niezależnie od tego, czy pozycja jest
        // akurat widoczna w sekcji "Kontynuuj oglądanie" — usunięcie jej stamtąd (krzyżyk)
        // kasuje tylko WIDOK tej sekcji, nie sam zapisany postęp odtwarzania.
        onQuickPlay = { s -> latestProgressBySeriesId[s.id]?.let { progress -> { onResumeWatching(progress) } } },
        globalSearchEnabled = globalSearchEnabled,
        onToggleGlobalSearch = onToggleGlobalSearch,
        continueWatching = watchProgress.forContinueWatching().filter { it.kind == com.iptvplayer.app.data.MediaKind.SERIES },
        onResumeWatching = onResumeWatching,
        onRemoveWatching = onRemoveWatching,
        customLists = customLists,
        // Bez onDownloadProgress w "Kontynuuj oglądanie": tam WatchProgress to pojedynczy
        // odcinek, nie cały serial — pobieranie sezonu z tego miejsca nie ma sensu.
        onToggleManyInList = onToggleManyInList,
        onCreateList = onCreateList,
        autoCloseAddToList = autoCloseAddToList,
        query = query,
        onQueryChange = onQueryChange,
        topFocusRequester = topFocusRequester,
        globalResults = { query ->
            GlobalSearchResults(
                query = query,
                liveChannels = allLive,
                movies = allMovies,
                series = series,
                favorites = favorites,
                onAddToList = { id, kind -> globalSearchAddTarget = ListItemRef(id = id, kind = kind) },
                onPlayChannel = onPlayChannel,
                onOpenSeries = onOpen,
                onOpenMovie = onOpenMovie,
                customMoodRules = customMoodRules,
            )
        },
    )
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
private fun <T> PosterGridScreen(
    items: List<T>,
    favorites: Set<String>,
    searchPlaceholder: String,
    emptyHint: String,
    noResultsHint: String,
    id: (T) -> String,
    title: (T) -> String,
    group: (T) -> String?,
    poster: (T) -> String?,
    // Wołane WYŁĄCZNIE do sortowania po dacie dodania (patrz SortMode.ADDED_NEWEST/OLDEST)
    // — ta sama wartość co addedAt na Channel/SeriesInfo, przekazana przez wywołującego,
    // żeby PosterGridScreen (generyczne <T>) nie musiało znać konkretnego typu.
    addedAt: (T) -> Long,
    kind: MediaKind,
    onToggleFavorite: (String) -> Unit,
    onClick: (T) -> Unit,
    onDownload: ((T) -> Unit)? = null,
    // Osobny przycisk ▶ na karcie do natychmiastowego odtworzenia bez wchodzenia w szczegóły.
    // Funkcja (nie stały callback!) — dla KAŻDEJ pozycji z osobna decyduje, czy przycisk się
    // w ogóle pokazać (zwraca null = brak przycisku). Dla filmów zawsze zwraca odtwarzanie
    // od początku; dla seriali zwraca wznowienie ostatnio oglądanego odcinka, ale TYLKO gdy
    // taki postęp istnieje — serial nigdy nie oglądany nie ma jednoznacznego "odtwórz",
    // trzeba najpierw wybrać odcinek w szczegółach.
    onQuickPlay: (T) -> (() -> Unit)? = { null },
    globalSearchEnabled: Boolean,
    onToggleGlobalSearch: (Boolean) -> Unit,
    globalResults: @Composable (query: String) -> Unit,
    continueWatching: List<com.iptvplayer.app.data.WatchProgress> = emptyList(),
    onResumeWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    onRemoveWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    onDownloadProgress: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    onCreateList: (name: String, emoji: String) -> CustomList,
    autoCloseAddToList: Boolean = false,
    // Cel fokusu przy zejściu pilotem W DÓŁ z dolnego paska nawigacji — ten sam mechanizm
    // co w LiveScreen.kt/HomeScreen.kt (tam obszerniejszy komentarz z uzasadnieniem).
    topFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    // Zapytanie wyszukiwania jako PARAMETR zamiast wewnętrznego remember{} — patrz obszerny
    // komentarz przy AppUiState.moviesSearchQuery (MainActivity.kt): trzymanie tego lokalnie
    // w tym ekranie kasowało wpisane wyszukiwanie przy każdym otwarciu/zamknięciu szczegółów
    // filmu/serialu, bo ten ekran jest warunkowo pomijany podczas gdy szczegóły są widoczne.
    query: String,
    onQueryChange: (String) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            Text(emptyHint, color = TextDim)
        }
        return
    }

    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    // Rozwinięty widok WSZYSTKICH kategorii w siatce — ten sam mechanizm co w LiveScreen.kt
    // (patrz tam obszerniejszy komentarz), tu tylko skrócony odnośnik.
    var showAllCategoriesExpanded by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.DEFAULT) }
    var showSortDialog by remember { mutableStateOf(false) }
    // Zakres roku jako TEKST (nie Int) — pole tekstowe potrzebuje czegoś do wyświetlenia
    // podczas pisania (pusty String, niedokończona liczba itp.), parsowanie na Int dopiero
    // przy faktycznym filtrowaniu niżej. Puste pole = brak ograniczenia z tej strony zakresu.
    var yearMinInput by remember { mutableStateOf("") }
    var yearMaxInput by remember { mutableStateOf("") }

    // --- tryb wielokrotnego zaznaczania: do budowania własnej listy hurtowo, np. dorzucenia
    // od razu kilkunastu filmów świątecznych zamiast pojedynczo przez "+" na każdej karcie ---
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    // --- okno "Dodaj do listy": albo dla jednej karty (kliknięcie "+"), albo dla całego
    // bieżącego zaznaczenia (przycisk "Dodaj do listy" w pasku SelectionToolbar) ---
    var singleAddTargetId by remember { mutableStateOf<String?>(null) }
    var showBulkAddSheet by remember { mutableStateOf(false) }

    val groups = remember(items) { items.mapNotNull { group(it) }.distinct().sorted() }
    val groupCounts = remember(items) { items.mapNotNull { group(it) }.groupingBy { it }.eachCount() }

    val filtered = remember(items, query, selectedGroup, favOnly, favorites, sortMode, yearMinInput, yearMaxInput) {
        val yearMin = yearMinInput.toIntOrNull()
        val yearMax = yearMaxInput.toIntOrNull()
        val base = items.filter { it2 ->
            (query.isBlank() || title(it2).containsFolded(query)) &&
                (selectedGroup == null || group(it2) == selectedGroup) &&
                (!favOnly || id(it2) in favorites) &&
                // Filtr zakresu roku — TYLKO gdy użytkownik faktycznie wpisał którąś granicę.
                // Pozycje bez rozpoznawalnego roku w tytule (yearFromTitle zwraca null) są
                // celowo WYKLUCZANE z wyniku, gdy zakres jest aktywny — inaczej pokazywałyby
                // się pozycje spoza szukanego zakresu tylko dlatego, że nie dało się ustalić
                // ich roku, co byłoby mylące.
                ((yearMin == null && yearMax == null) ||
                    yearFromTitle(title(it2))?.let { y -> (yearMin == null || y >= yearMin) && (yearMax == null || y <= yearMax) } == true)
        }
        when (sortMode) {
            SortMode.DEFAULT -> base
            SortMode.NAME_ASC -> base.sortedBy { title(it).lowercase() }
            SortMode.NAME_DESC -> base.sortedByDescending { title(it).lowercase() }
            SortMode.ADDED_NEWEST -> base.sortedByDescending { addedAt(it) }
            SortMode.ADDED_OLDEST -> base.sortedBy { addedAt(it) }
            // Pozycje bez rozpoznawalnego roku (null) lądują na KOŃCU niezależnie od kierunku
            // sortowania — "brak danych" nie powinien wypychać się na czoło listy, udając
            // "najstarszy"/"najnowszy" tylko dlatego, że tytuł nie pasował do wzorca roku.
            // Jawny Comparator (nie compareBy+nullsLast) — pewność co do składni zamiast
            // polegania na dokładnym kształcie konkretnego przeciążenia z biblioteki standardowej.
            SortMode.YEAR_NEWEST -> base.sortedWith(Comparator { a, b ->
                val ya = yearFromTitle(title(a)); val yb = yearFromTitle(title(b))
                when {
                    ya == null && yb == null -> 0
                    ya == null -> 1
                    yb == null -> -1
                    else -> yb.compareTo(ya)
                }
            })
            SortMode.YEAR_OLDEST -> base.sortedWith(Comparator { a, b ->
                val ya = yearFromTitle(title(a)); val yb = yearFromTitle(title(b))
                when {
                    ya == null && yb == null -> 0
                    ya == null -> 1
                    yb == null -> -1
                    else -> ya.compareTo(yb)
                }
            })
        }
    }

    val showingGlobalResults = globalSearchEnabled && query.isNotBlank()
    // Gdy użytkownik zaczyna wpisywać zapytanie, chowamy chipy i "Kontynuuj oglądanie" —
    // inaczej te sekcje zajmują miejsce nad klawiaturą i wyniki wyszukiwania są niewidoczne.
    val isSearching = query.isNotBlank()

    Column(Modifier.fillMaxSize().background(Bg)) {
        // --- search-bar + przełącznik trybu zaznaczania (zostaje na stałe u góry, nie scrolluje się) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextDim, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                SearchField(query, onValueChange = onQueryChange, placeholder = searchPlaceholder, focusRequester = topFocusRequester)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { showSortDialog = true },
                modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp), scaleWhenFocused = 1.05f),
            ) {
                Text(tr("grid.sort.button"))
            }
            // Przycisk "Zaznacz" (tryb zaznaczania wielu pozycji naraz) CELOWO USUNIĘTY na
            // wyraźne życzenie użytkownika. Reszta mechanizmu (selectionMode, selectedIds,
            // pasek zbiorczych akcji, checkboxy na kartach) zostaje w kodzie jako CELOWO
            // MARTWA — bez tego przycisku selectionMode nigdy nie przełącza się na true,
            // więc żadna z tych ścieżek się nie aktywuje. Bezpieczniejsze niż wycinanie
            // kilku powiązanych ze sobą miejsc naraz (realne ryzyko rozjechania czegoś
            // innego), a efekt końcowy dla użytkownika jest identyczny.
        }

        if (showSortDialog) {
            SortFilterDialog(
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                yearMinInput = yearMinInput,
                onYearMinChange = { yearMinInput = it },
                yearMaxInput = yearMaxInput,
                onYearMaxChange = { yearMaxInput = it },
                onDismiss = { showSortDialog = false },
            )
        }

        if (selectionMode) {
            SelectionToolbar(
                selectedCount = selectedIds.size,
                actionLabel = tr("common.addToList"),
                onAction = { showBulkAddSheet = true },
                onCancel = { selectionMode = false; selectedIds = emptySet() },
            )
        }

        GlobalSearchToggleRow(enabled = globalSearchEnabled, onToggle = onToggleGlobalSearch)
        Spacer(Modifier.height(6.dp))

        if (showingGlobalResults) {
            globalResults(query)
        } else {
            // --- chipy, "Kontynuuj oglądanie" i siatka plakatów są teraz WEWNĄTRZ jednej,
            // wspólnej LazyVerticalGrid (jako pełnoszerokie elementy nagłówkowe) — dzięki temu
            // scrollują się razem z listą zamiast zostawać na stałe nad nią i zabierać miejsce
            // na ekranie (tak jak wcześniej, gdy były osobnym, nieprzewijalnym Column powyżej). ---
            LazyVerticalGrid(
                // GridCells.Adaptive zamiast Fixed(3) — sztywne 3 kolumny na DUŻYM ekranie TV
                // (gdzie gęstość pikseli jest dużo niższa niż na telefonie) dawały plakaty tak
                // szerokie, że pojedynczy rząd nie mieścił się w pionie na ekranie. Adaptive
                // samo dobiera liczbę kolumn do FAKTYCZNIE dostępnej szerokości — więcej
                // kolumn na szerokim ekranie telewizora, mniej na wąskim telefonie — więc
                // pojedynczy plakat ma sensowny, przewidywalny rozmiar niezależnie od
                // urządzenia, zamiast zależeć od jego fizycznej szerokości/gęstości.
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // --- chipy: Ulubione + kategorie (ukryte podczas wyszukiwania i zaznaczania) ---
                if (!isSearching && !selectionMode) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            data class ChipDef(val label: String, val active: Boolean, val onClick: () -> Unit)
                            val allChipDefs = buildList {
                                add(ChipDef(tr("live.filter.favorites"), favOnly) { favOnly = !favOnly })
                                add(ChipDef(tr("live.filter.all", items.size), selectedGroup == null) { selectedGroup = null })
                                groups.forEach { g ->
                                    add(ChipDef(tr("live.filter.groupCount", g, groupCounts[g] ?: 0), selectedGroup == g) { selectedGroup = g })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // CELOWO PO LEWEJ — patrz obszerny komentarz przy tym samym
                                // przycisku w LiveScreen.kt (ten sam powód: przy playlistach
                                // z dziesiątkami/setkami kategorii pilot musiałby przejść
                                // przez wszystkie chipy, żeby dotrzeć do przycisku po prawej).
                                IconButton(
                                    onClick = { showAllCategoriesExpanded = !showAllCategoriesExpanded },
                                    modifier = Modifier.tvFocusFrame(shape = CircleShape),
                                ) {
                                    Text(
                                        if (showAllCategoriesExpanded) "\u25B4" else "\u25BE",
                                        color = TextDim,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item {
                                        FilterChipCompat(label = tr("live.filter.favorites"), active = favOnly, accent = Accent2, onClick = { favOnly = !favOnly })
                                    }
                                    item {
                                        FilterChipCompat(label = tr("live.filter.all", items.size), active = selectedGroup == null, onClick = { selectedGroup = null })
                                    }
                                    items(groups) { g ->
                                        FilterChipCompat(label = tr("live.filter.groupCount", g, groupCounts[g] ?: 0), active = selectedGroup == g, onClick = { selectedGroup = g })
                                    }
                                }
                            }
                            if (showAllCategoriesExpanded) {
                                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                                    allChipDefs.chunked(2).forEach { rowChips ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            rowChips.forEach { (label, active, onClick) ->
                                                // Wybór DOWOLNEJ kategorii z rozwiniętej siatki
                                                // od razu ją zwija z powrotem — zgłoszenie:
                                                // po wyborze grupy lista powinna się zwinąć,
                                                // zamiast zostawać rozwinięta i zajmować miejsce.
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

                // --- Kontynuuj oglądanie (przefiltrowane po typie: filmy albo seriale) ---
                // Chowane w trakcie wpisywania zapytania, żeby zrobić miejsce na wyniki nad klawiaturą.
                if (!isSearching && !selectionMode && continueWatching.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(
                                tr("home.continueWatching.title"),
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            ContinueWatchingRow(
                                items = continueWatching,
                                onClick = onResumeWatching,
                                onRemove = onRemoveWatching,
                                favorites = favorites,
                                onToggleFavorite = { onToggleFavorite(it.channelOrEpisodeId) },
                                onDownload = onDownloadProgress,
                                onAddToList = { singleAddTargetId = it.channelOrEpisodeId },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(noResultsHint, color = TextDim)
                        }
                    }
                } else {
                    // key = id(item) — zgłoszenie: appka działa wolniej/przycina na TV boxie.
                    // Ten katalog ma tysiące pozycji (16054 filmów w typowej konfiguracji) —
                    // bez stabilnego klucza Compose identyfikuje elementy listy POZYCYJNIE
                    // (wg indeksu), co jest znacznie mniej wydajne przy przewijaniu dużych,
                    // filtrowalnych list niż identyfikacja po prawdziwym, stabilnym ID.
                    items(filtered, key = { id(it) }) { item ->
                        val itemId = id(item)
                        if (selectionMode) {
                            SelectablePosterCard(
                                title = title(item),
                                posterUrl = poster(item),
                                selected = itemId in selectedIds,
                                onToggle = {
                                    selectedIds = if (itemId in selectedIds) selectedIds - itemId else selectedIds + itemId
                                },
                            )
                        } else {
                            PosterCard(
                                title = title(item),
                                posterUrl = poster(item),
                                isFavorite = itemId in favorites,
                                onToggleFavorite = { onToggleFavorite(itemId) },
                                onClick = { onClick(item) },
                                onDownload = onDownload?.let { dl -> { dl(item) } },
                                onQuickPlay = onQuickPlay(item),
                                onAddToList = { singleAddTargetId = itemId },
                            )
                        }
                    }
                }
            }
        }
    }

    // --- okno "Dodaj do listy" — dla pojedynczej karty ("+" na kafelku) albo dla całego
    // bieżącego zaznaczenia naraz (przycisk w SelectionToolbar) ---
    val activeRefs: List<ListItemRef>? = when {
        singleAddTargetId != null -> listOf(ListItemRef(id = singleAddTargetId!!, kind = kind))
        showBulkAddSheet && selectedIds.isNotEmpty() -> selectedIds.map { ListItemRef(id = it, kind = kind) }
        else -> null
    }
    if (activeRefs != null) {
        val refIds = remember(activeRefs) { activeRefs.map { it.id } }
        val alreadyIn = remember(refIds, customLists) {
            customLists.filter { list ->
                val present = list.items.map { it.id }.toSet()
                refIds.all { it in present }
            }.map { it.id }.toSet()
        }
        AddToListSheet(
            lists = customLists,
            alreadyInListIds = alreadyIn,
            itemCountLabel = if (activeRefs.size > 1) tr("home.myLists.itemCountMany", activeRefs.size) else null,
            autoCloseOnSelect = autoCloseAddToList,
            onToggleList = { listId -> onToggleManyInList(listId, activeRefs) },
            onCreateList = { name, emoji ->
                val newList = onCreateList(name, emoji)
                onToggleManyInList(newList.id, activeRefs)
            },
            onDismiss = {
                singleAddTargetId = null
                if (showBulkAddSheet) {
                    showBulkAddSheet = false
                    selectionMode = false
                    selectedIds = emptySet()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onQuickPlay: (() -> Unit)? = null,
    // Otwiera okno "Dodaj do listy" (dowolna niestandardowa lista, w tym Ulubione) —
    // osobno od gwiazdki, która zawsze i wszędzie oznacza tylko szybki skrót do Ulubionych.
    onAddToList: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .tvFocusFrame(shape = RoundedCornerShape(12.dp), scaleWhenFocused = 1.05f)
            // Zwykły klik = to samo co dotychczas (otwórz szczegóły / odtwórz). Długie
            // przytrzymanie OK na pilocie = przełącz ulubione — dzięki temu gwiazdka
            // nałożona na plakat (patrz niżej) nie musi być osobnym przystankiem fokusu
            // pilota, co przy nawigacji samymi strzałkami byłoby mylące (trzy nakładające
            // się mini-ikony na jednym kafelku). Na dotyku/myszy nic się nie zmienia.
            .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite),
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
            // Gwiazdka ulubionych w rogu karty została usunięta z widoku (na życzenie) —
            // ulubione dla filmów/seriali przełącza się teraz długim przytrzymaniem karty
            // (combinedClickable onLongClick = onToggleFavorite, patrz wyżej) albo przez
            // "Dodaj do listy" (onAddToList niżej), gdzie Ulubione zawsze jest pierwsze.
            // --- przycisk pobierania (poster-download w app.js) — tylko filmy, nie seriale ---
            if (onDownload != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.55f))
                        .notDpadFocusable()
                        .clickable(onClick = onDownload)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = tr("common.download"),
                        tint = TextMain,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // --- przycisk ▶ do natychmiastowego odtworzenia, bez wchodzenia w szczegóły filmu ---
            if (onQuickPlay != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Accent)
                        .notDpadFocusable()
                        .clickable(onClick = onQuickPlay)
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = tr("grid.quickPlayDesc"),
                        tint = Bg,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // --- przycisk "Dodaj do listy" (dowolna lista, w tym Ulubione) ---
            // W PRZECIWIEŃSTWIE do pobierania/odtwarzania wyżej (notDpadFocusable — mają
            // łatwe alternatywy: pełne szczegóły filmu/serialu, albo już otwarty odtwarzacz),
            // TEN przycisk musi być dostępny pilotem — jedyna inna droga do dodania z
            // poziomu SIATKI to długie przytrzymanie OK (combinedClickable.onLongClick
            // wyżej), które na pilocie TV bywa zawodne/trudne do odkrycia (zgłoszenie: "nie
            // mogę dodawać do ulubionych z poziomu plakatu"). Jeden dodatkowy przystanek
            // D-pad na kartę to uzasadniony koszt za realnie działającą funkcję.
            if (onAddToList != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.55f))
                        .tvFocusFrame(shape = CircleShape)
                        .clickable(onClick = onAddToList)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = tr("common.addToList"),
                        tint = TextMain,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = TextMain,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchField(
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

/** Tryb sortowania list Filmy/Seriale — patrz przycisk sortowania w PosterGridScreen.
 * CELOWO bez sortowania po ocenie: Xtream w podstawowym wykazie (get_vod_streams) NIE
 * podaje ani oceny, ani roku produkcji — to dane z OSOBNEGO zapytania NA KAŻDĄ pozycję z
 * osobna (get_vod_info), przy katalogu liczącym dziesiątki tysięcy pozycji pobranie tego
 * dla WSZYSTKICH byłoby nierealne. Rok da się w rozsądnej większości przypadków wyciągnąć
 * z samej NAZWY (dostawcy często dopisują "(RRRR)" w tytule, patrz yearFromTitle niżej) —
 * to darmowe i natychmiastowe, w przeciwieństwie do oceny. */
enum class SortMode { DEFAULT, NAME_ASC, NAME_DESC, ADDED_NEWEST, ADDED_OLDEST, YEAR_NEWEST, YEAR_OLDEST }

/** Wyciąga 4-cyfrowy rok z tytułu, np. "PL - Straszny film (2026)" -> 2026 — bierze
 * OSTATNIE takie dopasowanie w tekście (zwykle rok jest na końcu nazwy, nie na początku,
 * gdzie mogłoby przypadkowo trafić na coś innego). Zwraca null, gdy tytuł nie ma takiego
 * wzorca — te pozycje są wtedy traktowane jako "bez roku" (na końcu przy sortowaniu,
 * pomijane przy filtrze zakresu), nie jako błąd.
 */
internal fun yearFromTitle(title: String): Int? =
    Regex("""\((\d{4})\)""").findAll(title).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("""\b(19\d{2}|20\d{2})\b""").findAll(title).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()

/** Okno wyboru sortowania + zakresu roku — patrz SortMode i przycisk "Sortuj" w
 * PosterGridScreen wyżej. Prosta lista opcji (RadioButton) + dwa pola tekstowe na zakres,
 * bez suwaka — pole tekstowe jest jednoznaczne i nie wymaga znajomości granic z góry
 * (użytkownik może wpisać dowolny rok, appka sama go zinterpretuje). */
@Composable
private fun SortFilterDialog(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    yearMinInput: String,
    onYearMinChange: (String) -> Unit,
    yearMaxInput: String,
    onYearMaxChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SortMode.DEFAULT to tr("grid.sort.default"),
        SortMode.NAME_ASC to tr("grid.sort.nameAsc"),
        SortMode.NAME_DESC to tr("grid.sort.nameDesc"),
        SortMode.ADDED_NEWEST to tr("grid.sort.addedNewest"),
        SortMode.ADDED_OLDEST to tr("grid.sort.addedOldest"),
        SortMode.YEAR_NEWEST to tr("grid.sort.yearNewest"),
        SortMode.YEAR_OLDEST to tr("grid.sort.yearOldest"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("grid.sort.title")) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusFrame(shape = RoundedCornerShape(8.dp))
                            .clickable { onSortModeChange(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sortMode == mode, onClick = { onSortModeChange(mode) })
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("grid.sort.yearRangeLabel"), color = TextDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = yearMinInput,
                        onValueChange = { onYearMinChange(it.filter(Char::isDigit).take(4)) },
                        label = { Text(tr("grid.sort.yearFrom")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).tvKeyboardOnDemand(),
                    )
                    OutlinedTextField(
                        value = yearMaxInput,
                        onValueChange = { onYearMaxChange(it.filter(Char::isDigit).take(4)) },
                        label = { Text(tr("grid.sort.yearTo")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).tvKeyboardOnDemand(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("common.done")) }
        },
        dismissButton = {
            TextButton(onClick = {
                onSortModeChange(SortMode.DEFAULT)
                onYearMinChange("")
                onYearMaxChange("")
            }) { Text(tr("grid.sort.reset")) }
        },
    )
}
