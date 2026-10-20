package com.iptvplayer.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.data.ListItemRef
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.SeriesInfo
import com.iptvplayer.app.data.WatchProgress
import com.iptvplayer.app.data.forContinueWatching
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.ui.AppUiState
import com.iptvplayer.app.ui.nav.Tab
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.notDpadFocusable
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import java.text.SimpleDateFormat
import java.util.*

/** Czytelny opis "kiedy ostatnio" dla znacznika lastRefreshAt spod przycisku Odśwież na Home —
 * 0L oznacza, że w tej instalacji jeszcze nigdy się nie udało (albo nie próbowano). `lang`
 * podawany jawnie (zamiast LocalLang.current wewnątrz) — to zwykła funkcja, nie @Composable,
 * więc czyta go wywołujący (patrz użycie niżej) i przekazuje też do SimpleDateFormat, żeby
 * nazwy dni/miesięcy też były w wybranym języku, nie w domyślnym języku urządzenia. */
private fun formatLastRefresh(timestampMs: Long, lang: String): String {
    if (timestampMs <= 0L) return trFor(lang, "home.lastRefresh.never")
    val diffMs = System.currentTimeMillis() - timestampMs
    val diffMin = diffMs / 60_000
    val locale = Locale(lang)
    return when {
        diffMin < 1 -> trFor(lang, "home.lastRefresh.justNow")
        diffMin < 60 -> trFor(lang, "home.lastRefresh.minutesAgo", diffMin)
        else -> {
            val sameDay = SimpleDateFormat("yyyyMMdd", locale).format(Date(timestampMs)) ==
                SimpleDateFormat("yyyyMMdd", locale).format(Date())
            if (sameDay) {
                SimpleDateFormat("HH:mm", locale).format(Date(timestampMs))
            } else {
                SimpleDateFormat("d MMM, HH:mm", locale).format(Date(timestampMs))
            }
        }
    }
}

/**
 * Odpowiednik zakładki Home z app.js: zegar, karta statusu playlisty, szybka nawigacja
 * kafelkowa (home-quicknav) i sekcja "kontynuuj oglądanie" (cw-section / cw-tile).
 */
@Composable
fun HomeScreen(
    state: AppUiState,
    onNavigateTab: (Tab) -> Unit,
    onRefresh: () -> Unit,
    onResumeWatching: (WatchProgress) -> Unit,
    onRemoveWatching: (WatchProgress) -> Unit = {},
    onOpenMovie: (Channel) -> Unit = {},
    onOpenSeries: (SeriesInfo) -> Unit = {},
    onPlayLive: (Channel) -> Unit = {},
    // Bezpośrednie odtworzenie filmu z plakatu "Ostatnio dodane" (ikonka ▶ na środku), bez
    // wchodzenia w szczegóły — patrz onQuickPlay w RecentlyAddedSection niżej. Dla seriali
    // osobnego parametru nie ma: tam decyduje istniejący postęp oglądania (onResumeWatching),
    // liczony automatycznie z watchProgress poniżej.
    onQuickPlayMovie: (Channel) -> Unit = {},
    onOpenList: (String) -> Unit = {},
    onCreateList: (name: String, emoji: String) -> com.iptvplayer.app.data.CustomList = { n, e -> com.iptvplayer.app.data.CustomList(id = "", name = n, emoji = e) },
    // --- wyszukiwanie na Home — te same funkcjonalności co na Live/Movies/Series:
    // pole szukania, przełącznik "Szukaj we wszystkich zakładkach" i lista wyników
    // (GlobalSearchResults), łącznie z gwiazdką ulubionych na wynikach live/filmy. ---
    favorites: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    globalSearchEnabled: Boolean = false,
    onToggleGlobalSearch: (Boolean) -> Unit = {},
    // --- pełna funkcjonalność kafelków (jak na plakatach w Movies/Series/Live): pobieranie
    // (tylko filmy), "Dodaj do listy" i ulubione (długie przytrzymanie) — te same handlery co
    // gdzie indziej, dorzucone do "Kontynuuj oglądanie" i "Ostatnio dodane" na Home. ---
    onDownloadMovie: (Channel) -> Unit = {},
    onDownloadProgress: (WatchProgress) -> Unit = {},
    // Pobieranie całego sezonu z plakatu serialu w "Ostatnio dodane" — otwiera dialog
    // wyboru sezonu (patrz MainActivity.requestSeriesSeasonDownload), tak samo jak na
    // plakatach w zakładce Series.
    onDownloadSeries: (SeriesInfo) -> Unit = {},
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    // --- Tryb uruchamiania appki (przycisk obok zegara) — patrz AppUiState.continueFromLastPosition
    // i LocalStore. Zamknięcie appki (drugi przycisk obok) nie potrzebuje osobnego callbacku z
    // MainActivity — HomeScreen sam woła activity.finishAffinity() przez LocalContext, tak samo
    // jak już robi to niżej dla Toastów przy odświeżaniu. ---
    continueFromLastPosition: Boolean = false,
    onSetContinueFromLastPosition: (Boolean) -> Unit = {},
    autoCloseAddToList: Boolean = false,
    onToggleHomeSectionCollapsed: (String) -> Unit = {},
    // Cel fokusu przy zejściu pilotem W DÓŁ z dolnego paska nawigacji — patrz ten sam
    // mechanizm w LiveScreen.kt (tam obszerniejszy komentarz z uzasadnieniem).
    topFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    // Cel "Dodaj do listy" wspólny dla "Kontynuuj oglądanie" i wszystkich sekcji
    // "Ostatnio dodane" na Home — jeden dialog obsługuje wszystkie kafelki na tym ekranie.
    var addToListTarget by remember { mutableStateOf<ListItemRef?>(null) }
    // Zegar (poprzednio: now/LaunchedEffect TUTAJ, aktualizowane co sekundę) PRZENIESIONY
    // do własnej, osobnej funkcji HomeClockDisplay niżej w pliku — patrz obszerny komentarz
    // tam. W skrócie: sekundowe tykanie zegara w TEJ funkcji odświeżałoby całe ciało
    // HomeScreen (bo Compose grupuje jeden zasięg przebudowy na całą funkcję, chyba że coś
    // jawnie go rozdzieli) — łącznie z ContinueWatchingRow/MyListsSection niżej, co
    // najprawdopodobniej rozregulowywało wewnętrzny stan fokusu (`isFocused` w
    // tvFocusFrame, opartym o starszy mechanizm composed{}) w tych konkretnych kartach co
    // sekundę. Zgłoszenie: niebieska ramka podświetlenia znikała po chwili na kartach
    // Kontynuuj oglądanie/Moje listy, a NIGDY na plakatach w gridzie Filmy/Seriale — które
    // nie sąsiadują z żadnym tak często tykającym stanem.
    // Ostatnio oglądany odcinek per serial — do warunkowej ikonki ▶ na plakatach "Ostatnio
    // dodane • Seriale" (patrz onQuickPlay w RecentlyAddedSection niżej). Ta sama logika co
    // w SeriesScreen (GridScreens.kt): grupowanie po seriesId, wybór najświeższego wpisu.
    val recentSeriesProgress = remember(state.watchProgress) {
        state.watchProgress.values
            .filter { it.kind == MediaKind.SERIES && it.seriesId != null }
            .groupBy { it.seriesId }
            .mapValues { (_, group) -> group.maxBy { it.updatedAt } }
    }

    var query by remember { mutableStateOf("") }
    val isSearching = query.isNotBlank()

    // Kontener NIE jest przewijalny sam w sobie (w przeciwieństwie do poprzedniej wersji) —
    // przewijalna jest tylko zawartość normalnego widoku Home (patrz niżej), a wyniki
    // wyszukiwania renderują własną LazyColumn (GlobalSearchResults). Zagnieżdżenie
    // LazyColumn wewnątrz przewijalnego Column powodowałoby crash (nieskończona wysokość),
    // dlatego oba warianty żyją w osobnym Box(weight(1f)) obok stałego paska wyszukiwania.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        // --- home-search-bar: dokładnie ten sam wzorzec co search-bar na Live/Movies/Series
        // (BasicTextField w zaokrąglonym Surface + ikona lupy), plus przełącznik globalnego
        // wyszukiwania pod spodem. Wyszukuje po wszystkich trzech typach naraz (live/filmy/seriale). ---
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
            Box(Modifier.weight(1f)) {
                HomeSearchField(query, onValueChange = { query = it }, placeholder = tr("home.search.placeholder"), focusRequester = topFocusRequester)
            }
            // --- Wyszukiwanie głosowe — mikrofon uruchamia systemowe rozpoznawanie mowy
            // (na TV: to samo co przycisk mikrofonu na pilocie Google TV), a rozpoznany tekst
            // trafia prosto do pola wyszukiwania. Przycisk pojawia się tylko, jeśli
            // urządzenie w ogóle ma usługę rozpoznawania mowy. ---
            val voiceContext = LocalContext.current
            val voiceLang = LocalLang.current
            val voiceAvailable = remember {
                runCatching {
                    android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .resolveActivity(voiceContext.packageManager) != null
                }.getOrDefault(false)
            }
            val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val spoken = result.data
                    ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) query = spoken
            }
            if (voiceAvailable) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .tvFocusFrame(shape = CircleShape)
                        .clickable {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, voiceLang)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, trFor(voiceLang, "home.search.voicePrompt"))
                            }
                            runCatching { voiceLauncher.launch(intent) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = tr("home.search.voiceDesc"),
                        tint = TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (isSearching) {
            // Na Home wynik wyszukiwania zawsze przeszukuje wszystko (live + filmy + seriale),
            // niezależnie od przełącznika "Szukaj we wszystkich zakładkach" — na Home nie ma
            // innego, węższego zakresu do którego mógłby się ograniczyć.
            Box(Modifier.weight(1f)) {
                GlobalSearchResults(
                    query = query,
                    liveChannels = state.liveChannels,
                    movies = state.movies,
                    series = state.series,
                    favorites = favorites,
                    onAddToList = { id, kind -> addToListTarget = ListItemRef(id = id, kind = kind) },
                    onPlayChannel = onPlayLive,
                    onOpenSeries = onOpenSeries,
                    onOpenMovie = onOpenMovie,
                    customMoodRules = state.customMoodRules,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScrollCompat()
                    .padding(bottom = 16.dp),
            ) {
                // --- home-clock + szybkie akcje (wyjście z appki, tryb uruchamiania) ---
                // Pierwszy element przewijanej treści Home, więc widoczny od razu po wejściu
                // na zakładkę, bez przewijania (za każdym powrotem na Home ta zakładka jest
                // tworzona od nowa, więc scroll zawsze wraca na górę). Umyślnie NIE wyniesione
                // poza scrollowalny obszar do osobnego, zawsze-widocznego paska — te dwa
                // przyciski są ważne, ale nie na tyle, by trwale zajmować miejsce nad treścią;
                // pierwszeństwo dostępu (od razu po wejściu na Home, bez dodatkowego scrolla)
                // wystarcza, a dotyczy to zwłaszcza pilota TV, gdzie ta zakładka i tak zwykle
                // jest pierwszym miejscem po uruchomieniu appki.
                val quickActionsContext = LocalContext.current
                val lang = LocalLang.current
                var showExitConfirm by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    HomeClockDisplay(lang = lang)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // --- Tryb uruchamiania: ikona/etykieta pokazują AKTUALNY tryb, dotknięcie
                        // przełącza na drugi. Skutek jest niewidoczny aż do następnego uruchomienia
                        // appki, więc potwierdzamy zmianę Toastem — ten sam wzorzec co przy
                        // odświeżaniu playlist niżej. ---
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface)
                                .tvFocusFrame(shape = RoundedCornerShape(12.dp))
                                .clickable {
                                    val newValue = !continueFromLastPosition
                                    onSetContinueFromLastPosition(newValue)
                                    Toast.makeText(
                                        quickActionsContext,
                                        trFor(lang, if (newValue) "home.startupMode.toastContinue" else "home.startupMode.toastHome"),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                if (continueFromLastPosition) Icons.Default.History else Icons.Default.Home,
                                contentDescription = tr(if (continueFromLastPosition) "home.startupMode.continueDesc" else "home.startupMode.homeDesc"),
                                tint = TextMain,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr(if (continueFromLastPosition) "home.startupMode.continueLabel" else "home.startupMode.homeLabel"),
                                color = TextMain,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        // --- Zamknięcie appki — szczególnie ważne dla pilotów TV, gdzie przycisk
                        // Home na pilocie zwykle tylko chowa appkę w tle zamiast ją zamykać, a
                        // gest "cofnij" bywa niedostępny lub niejednoznaczny. Potwierdzenie w
                        // dialogu chroni przed przypadkowym zamknięciem przy nawigacji strzałkami. ---
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface)
                                .tvFocusFrame(shape = RoundedCornerShape(12.dp))
                                .clickable { showExitConfirm = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = tr("home.exit.contentDescription"), tint = Danger)
                            Spacer(Modifier.height(4.dp))
                            Text(tr("home.exit.label"), color = TextMain, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text(tr("home.exit.dialogTitle")) },
                        text = { Text(tr("home.exit.dialogText")) },
                        confirmButton = {
                            com.iptvplayer.app.ui.tv.TvDialogButton(
                                text = tr("home.exit.confirm"),
                                color = Danger,
                                onClick = {
                                    showExitConfirm = false
                                    (quickActionsContext as? android.app.Activity)?.finishAffinity()
                                },
                            )
                        },
                        dismissButton = {
                            com.iptvplayer.app.ui.tv.TvDialogButton(
                                text = tr("common.cancel"),
                                initialFocus = true,
                                onClick = { showExitConfirm = false },
                            )
                        },
                    )
                }

                // --- home-status-card ---
                val homeContext = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            if (state.playlists.isEmpty()) tr("home.status.noPlaylist")
                            else tr("home.status.summary", state.playlists.size, state.liveChannels.size),
                            color = TextMain, style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            tr("home.status.moviesSeries", state.movies.size, state.series.size),
                            color = TextDim, style = MaterialTheme.typography.bodySmall,
                        )
                        // --- kiedy ostatni raz zakończyło się odświeżanie (patrz AppViewModel.lastRefreshAt) —
                        // dowód, że przycisk obok faktycznie coś robi, nawet gdy dane się nie zmieniły ---
                        Text(
                            if (state.isLoading) tr("home.status.refreshing")
                            else tr("home.status.lastRefreshed", formatLastRefresh(state.lastRefreshAt, lang)),
                            color = TextDim, style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    // Obrót ikony w kółko, dopóki isLoading == true — jedyny dotychczasowy
                    // "dowód" kliknięcia to samo domyślne podświetlenie (ripple) IconButtona,
                    // co ginęło w ułamku sekundy przy szybkim odświeżeniu i wyglądało jak
                    // martwy przycisk. Blokujemy też ponowne kliknięcie w trakcie odświeżania,
                    // żeby nie odpalać kilku równoległych zapytań na raz.
                    val infiniteTransition = rememberInfiniteTransition(label = "refresh-spin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "refresh-spin-angle",
                    )
                    IconButton(
                        onClick = {
                            if (state.playlists.isEmpty()) {
                                Toast.makeText(
                                    homeContext,
                                    trFor(lang, "home.status.refreshToast"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                onRefresh()
                            }
                        },
                        enabled = !state.isLoading,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = tr("home.status.refreshDesc"),
                            tint = TextDim,
                            modifier = if (state.isLoading) Modifier.rotate(rotation) else Modifier,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Sekcja "home-quicknav" (Live/Filmy/Seriale/Moje jako duże kafelki) CELOWO
                // USUNIĘTA — dublowała dolny pasek nawigacji, który daje ten sam dostęp do
                // każdej z tych zakładek (zgłoszenie: "nie ma co dublować").

                Spacer(Modifier.height(20.dp))

                // --- Continue watching (cw-section) ---
                if (state.watchProgress.isNotEmpty()) {
                    Text(
                        tr("home.continueWatching.title"),
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    ContinueWatchingRow(
                        items = state.watchProgress.forContinueWatching(),
                        onClick = onResumeWatching,
                        onRemove = onRemoveWatching,
                        favorites = favorites,
                        onToggleFavorite = { onToggleFavorite(it.channelOrEpisodeId) },
                        onDownload = onDownloadProgress,
                        onAddToList = { addToListTarget = ListItemRef(id = it.channelOrEpisodeId, kind = it.kind) },
                    )
                    Spacer(Modifier.height(20.dp))
                } else {
                    Text(
                        tr("home.continueWatching.empty"),
                        color = TextDim,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // --- Moje listy (wbudowane Ulubione + listy własne użytkownika, np. "Święta") —
                // pozioma "półka" kart-list zamiast dawnej siatki plakatów ulubionych: stuknięcie
                // w kartę otwiera pełną zawartość danej listy (ListDetailScreen). ---
                MyListsSection(
                    lists = state.customLists,
                    liveChannels = state.liveChannels,
                    movies = state.movies,
                    series = state.series,
                    onOpenList = onOpenList,
                    onCreateList = onCreateList,
                )

                // --- Ostatnio dodane (RECENTLY ADDED) — filmy / seriale / live, tak jak w wersji web ---
                RecentlyAddedSection(
                    title = tr("home.recentlyAdded.movies"),
                    sectionKey = "movies",
                    collapsed = "movies" in state.collapsedHomeSections,
                    onToggleCollapsed = { onToggleHomeSectionCollapsed("movies") },
                    items = state.recentMovies,
                    posterOf = { it.logoUrl },
                    titleOf = { it.name },
                    onClick = onOpenMovie,
                    isFavorite = { it.id in favorites },
                    onToggleFavorite = { onToggleFavorite(it.id) },
                    onDownload = onDownloadMovie,
                    onAddToList = { addToListTarget = ListItemRef(id = it.id, kind = MediaKind.MOVIE) },
                    onQuickPlay = { channel -> { onQuickPlayMovie(channel) } },
                )
                RecentlyAddedSection(
                    title = tr("home.recentlyAdded.series"),
                    sectionKey = "series",
                    collapsed = "series" in state.collapsedHomeSections,
                    onToggleCollapsed = { onToggleHomeSectionCollapsed("series") },
                    items = state.recentSeries,
                    posterOf = { it.posterUrl },
                    titleOf = { it.name },
                    onClick = onOpenSeries,
                    isFavorite = { it.id in favorites },
                    onToggleFavorite = { onToggleFavorite(it.id) },
                    onDownload = onDownloadSeries,
                    onAddToList = { addToListTarget = ListItemRef(id = it.id, kind = MediaKind.SERIES) },
                    // Tylko dla seriali z zapisanym postępem — wznawia ostatnio oglądany
                    // odcinek. Ten sam sposób grupowania po seriesId co w SeriesScreen
                    // (GridScreens.kt) i w sekcji "Kontynuuj oglądanie" wyżej na tej stronie.
                    onQuickPlay = { series ->
                        recentSeriesProgress[series.id]?.let { progress -> { onResumeWatching(progress) } }
                    },
                )
                RecentlyAddedSection(
                    title = tr("home.recentlyAdded.live"),
                    sectionKey = "live",
                    collapsed = "live" in state.collapsedHomeSections,
                    onToggleCollapsed = { onToggleHomeSectionCollapsed("live") },
                    items = state.recentLive,
                    posterOf = { it.logoUrl },
                    titleOf = { it.name },
                    onClick = onPlayLive,
                    isFavorite = { it.id in favorites },
                    onToggleFavorite = { onToggleFavorite(it.id) },
                    onAddToList = { addToListTarget = ListItemRef(id = it.id, kind = MediaKind.LIVE) },
                )
            }
        }
    }

    // --- okno "Dodaj do listy" wspólne dla "Kontynuuj oglądanie" i "Ostatnio dodane" na Home ---
    addToListTarget?.let { target ->
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
            onDismiss = { addToListTarget = null },
        )
    }
}

/**
 * Pozioma "półka" plakatów dla jednej z sekcji "Ostatnio dodane" na Home
 * (Filmy / Seriale / Live) — odpowiednik "RECENTLY ADDED · MOVIES/SERIES/LIVE"
 * z wersji webowej. Nic nie renderuje, jeśli lista jest pusta (np. playlista M3U
 * bez pola "added", albo brak playlisty w ogóle).
 */
@Composable
private fun <T> RecentlyAddedSection(
    title: String,
    // Klucz do zapamiętania stanu zwinięcia (patrz LocalStore.getCollapsedHomeSections) —
    // "movies"/"series"/"live", osobny dla każdej z trzech sekcji na Home.
    sectionKey: String,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    items: List<T>,
    posterOf: (T) -> String?,
    titleOf: (T) -> String,
    onClick: (T) -> Unit,
    // --- ten sam zestaw akcji co na plakatach w Movies/Series/Live (patrz PosterCard
    // w GridScreens.kt): pobieranie (tylko tam, gdzie handler przekazany — czyli filmy),
    // "Dodaj do listy" i ulubione pod długim przytrzymaniem. Wszystkie opcjonalne, więc
    // przycisk pojawia się na kafelku tylko gdy odpowiedni handler jest podany. ---
    isFavorite: (T) -> Boolean = { false },
    onToggleFavorite: ((T) -> Unit)? = null,
    onDownload: ((T) -> Unit)? = null,
    onAddToList: ((T) -> Unit)? = null,
    // Funkcja (nie stały callback!) — dla KAŻDEJ pozycji z osobna decyduje, czy ikonka ▶ na
    // środku plakatu ma faktycznie coś robić (zwraca null = zostaje czysto dekoracyjna, jak
    // wcześniej). Ten sam wzorzec co onQuickPlay w PosterCard/PosterGridScreen (GridScreens.kt) —
    // dla filmów zawsze zwraca akcję (odtwórz od razu), dla seriali tylko gdy istnieje
    // zapisany postęp (wtedy wznawia ostatni odcinek), inaczej trzeba wejść w szczegóły i
    // wybrać odcinek samemu.
    onQuickPlay: (T) -> (() -> Unit)? = { null },
) {
    if (items.isEmpty()) return
    // Nagłówek KLIKALNY (zwiń/rozwiń) — przydaje się, gdy dana kategoria u konkretnego
    // dostawcy nie ma plakatów (same szare kafelki, zgłoszenie: "Live u mnie") i tylko
    // zajmuje miejsce na ekranie bez żadnej wartości wizualnej. Stan zapamiętany trwale
    // (LocalStore), nie tylko na czas sesji.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusFrame(shape = RoundedCornerShape(6.dp))
            .clickable(onClick = onToggleCollapsed)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextDim, style = MaterialTheme.typography.labelSmall)
        Text(if (collapsed) "\u25BE" else "\u25B4", color = TextDim, style = MaterialTheme.typography.labelSmall)
    }
    if (collapsed) {
        Spacer(Modifier.height(8.dp))
        return
    }
    Spacer(Modifier.height(8.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items) { item ->
            RecentlyAddedTile(
                title = titleOf(item),
                posterUrl = posterOf(item),
                onClick = { onClick(item) },
                isFavorite = isFavorite(item),
                onToggleFavorite = onToggleFavorite?.let { handler -> { handler(item) } },
                onDownload = onDownload?.let { handler -> { handler(item) } },
                onAddToList = onAddToList?.let { handler -> { handler(item) } },
                onQuickPlay = onQuickPlay(item),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyAddedTile(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToList: (() -> Unit)? = null,
    // Środkowa ikonka ▶ na plakacie — wcześniej była czysto dekoracyjna (bez własnego
    // celu dotyku, tap gdziekolwiek na kafelku i tak szedł do onClick czyli szczegółów).
    // Teraz, jeśli podane, ikonka ma WŁASNY cel: natychmiastowe odtworzenie bez wchodzenia
    // w szczegóły — dokładnie tak, jak przycisk ▶ na plakatach w Movies/Series/Live
    // (patrz PosterCard w GridScreens.kt). null = brak akcji, ikonka zostaje jak dawniej.
    onQuickPlay: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .tvFocusFrame(shape = RoundedCornerShape(10.dp), scaleWhenFocused = 1.05f)
            // Krótki klik = odtwórz/otwórz (jak dotychczas). Długie przytrzymanie = ulubione —
            // ten sam wzorzec co PosterCard w GridScreens.kt.
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
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Bg.copy(alpha = 0.45f))
                    // Własny cel dotyku NIEZALEŻNY od reszty kafelka — musi być zadeklarowany
                    // PO tle (wyżej w kodzie), ale renderuje się na wierzchu, więc przechwytuje
                    // dotyk w tym miejscu wcześniej niż zewnętrzny combinedClickable na całym
                    // Column. Gdy onQuickPlay == null, ikonka zostaje czysto dekoracyjna jak
                    // dawniej (dotyk przechodzi do onClick otwierającego szczegóły).
                    .then(
                        if (onQuickPlay != null) {
                            Modifier.clickable(onClick = onQuickPlay)
                        } else {
                            Modifier
                        },
                    )
                    .padding(6.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = if (onQuickPlay != null) tr("grid.quickPlayDesc") else null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
            // --- przycisk pobierania — tylko filmy (patrz onDownload w RecentlyAddedSection na Home) ---
            if (onDownload != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.55f))
                        .notDpadFocusable()
                        .clickable(onClick = onDownload)
                        .padding(4.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = tr("common.download"), tint = TextMain, modifier = Modifier.size(14.dp))
                }
            }
            // --- przycisk "Dodaj do listy" (dowolna lista, w tym Ulubione) ---
            if (onAddToList != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.55f))
                        .notDpadFocusable()
                        .clickable(onClick = onAddToList)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = tr("common.addToList"),
                        tint = if (isFavorite) Accent2 else TextMain,
                        modifier = Modifier.size(14.dp),
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

/**
 * Pozioma "półka" kafelków "Kontynuuj oglądanie" — współdzielona przez Home oraz
 * zakładki Live/Movies/Series (tam pokazuje tylko pozycje danego typu, patrz `kind`).
 * Sortuje malejąco po dacie ostatniej aktywności, żeby ostatnio oglądane były pierwsze.
 */
@Composable
fun ContinueWatchingRow(
    items: List<WatchProgress>,
    onClick: (WatchProgress) -> Unit,
    onRemove: (WatchProgress) -> Unit = {},
    // --- ten sam zestaw akcji co na plakatach w Movies/Series/Live (patrz PosterCard
    // w GridScreens.kt). Wszystkie opcjonalne — przycisk pojawia się tylko gdy handler
    // jest podany, a pobieranie dodatkowo tylko dla pozycji typu MOVIE (tak jak na plakatach). ---
    favorites: Set<String> = emptySet(),
    onToggleFavorite: ((WatchProgress) -> Unit)? = null,
    onDownload: ((WatchProgress) -> Unit)? = null,
    onAddToList: ((WatchProgress) -> Unit)? = null,
) {
    val sorted = remember(items) { items.sortedByDescending { it.updatedAt } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sorted, key = { it.channelOrEpisodeId }) { progress: WatchProgress ->
            ContinueWatchingTile(
                progress,
                onClick = { onClick(progress) },
                onRemove = { onRemove(progress) },
                isFavorite = progress.channelOrEpisodeId in favorites,
                onToggleFavorite = onToggleFavorite?.let { handler -> { handler(progress) } },
                onDownload = onDownload?.takeIf { progress.kind == MediaKind.MOVIE }?.let { handler -> { handler(progress) } },
                onAddToList = onAddToList?.let { handler -> { handler(progress) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingTile(
    progress: WatchProgress,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToList: (() -> Unit)? = null,
) {
    val pct = if (progress.durationMillis > 0) (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f) else 0f
    // Menu akcji uruchamiane długim przytrzymaniem — zgłoszenie: ikonki pobierz/dodaj do
    // listy/usuń (X) są dotykowe (notDpadFocusable), pilotem w ogóle nieosiągalne, a
    // dodanie ich jako osobnych przystanków fokusu zaśmieciłoby nawigację MIĘDZY kafelkami
    // (trzy dodatkowe miejsca do przeskoczenia na każdym kafelku). To rozwiązanie
    // zaproponowane wprost: długie przytrzymanie OK pokazuje listę wszystkich dostępnych
    // akcji naraz — działa identycznie dotykiem i pilotem, bez dodatkowych przystanków
    // fokusu. Zastępuje POPRZEDNIE zachowanie długiego przytrzymania (bezpośrednio
    // przełączało ulubione) — teraz "Ulubione" to jedna z pozycji w tym samym menu.
    var showActionMenu by remember { mutableStateOf(false) }
    // Jawne wykrywanie długiego przytrzymania OK na pilocie — zgłoszenie: samo
    // combinedClickable (zaprojektowane głównie pod dotyk) niezawodnie nie wykrywało
    // długiego przytrzymania przycisku OK na TV, więc menu akcji (usuń/dodaj do
    // listy/pobierz) było w praktyce nieosiągalne pilotem. Ten sam wzorzec (repeatCount +
    // czas trwania), którego appka już używa w PlayerActivity.kt dla długiego przytrzymania
    // lewo/prawo — sprawdzony, działający mechanizm, nie nowy eksperyment.
    var tvPressStartMs by remember { mutableStateOf(0L) }
    var tvLongPressHandled by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(120.dp)
            // tvFocusFrame PRZED clip/background — patrz obszerny komentarz przy
            // MyListTile wyżej: ten sam powód, ta sama poprawka, ta sama kolejność co w
            // działającym PosterCard.
            .tvFocusFrame(shape = RoundedCornerShape(10.dp), scaleWhenFocused = 1.05f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key != Key.DirectionCenter && keyEvent.key != Key.Enter && keyEvent.key != Key.NumPadEnter) {
                    return@onKeyEvent false
                }
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        val repeatCount = keyEvent.nativeKeyEvent?.repeatCount ?: 0
                        if (repeatCount == 0) {
                            tvPressStartMs = System.currentTimeMillis()
                            tvLongPressHandled = false
                        } else if (!tvLongPressHandled && System.currentTimeMillis() - tvPressStartMs >= 500L) {
                            tvLongPressHandled = true
                            showActionMenu = true
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!tvLongPressHandled) onClick()
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = { showActionMenu = true })
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface3),
        ) {
            if (progress.posterUrl != null) {
                coil.compose.AsyncImage(model = progress.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(progress.title.take(2).uppercase(), color = TextDim, style = MaterialTheme.typography.labelSmall)
                }
            }
            // --- przycisk pobierania — tylko dla filmów (patrz onDownload wyżej) ---
            if (onDownload != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.6f))
                        .notDpadFocusable()
                        .clickable(onClick = onDownload)
                        .padding(4.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = tr("common.download"), tint = TextMain, modifier = Modifier.size(14.dp))
                }
            }
            // --- przycisk "Dodaj do listy" (dowolna lista, w tym Ulubione) ---
            if (onAddToList != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Bg.copy(alpha = 0.6f))
                        .notDpadFocusable()
                        .clickable(onClick = onAddToList)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = tr("common.addToList"),
                        tint = if (isFavorite) Accent2 else TextMain,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            // --- przycisk usuwania pozycji z "Kontynuuj oglądanie" (nie chcę kontynuować) —
            // zostaje ZAWSZE widoczny, niezależnie od pozostałych przycisków, bo to jedyne
            // miejsce skąd można usunąć wpis z tej konkretnej listy. ---
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Bg.copy(alpha = 0.6f))
                    .notDpadFocusable()
                    .clickable(onClick = onRemove)
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = tr("home.continueWatching.remove"),
                    tint = TextMain,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Surface3),
        ) {
            Box(Modifier.fillMaxWidth(pct).height(4.dp).background(Accent))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            progress.title.ifBlank { progress.channelOrEpisodeId },
            color = TextMain,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
    if (showActionMenu) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            title = { Text(progress.title.ifBlank { progress.channelOrEpisodeId }) },
            text = {
                Column {
                    if (onToggleFavorite != null) {
                        TextButton(
                            onClick = { onToggleFavorite(); showActionMenu = false },
                            modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                        ) {
                            Text(
                                if (isFavorite) tr("home.continueWatching.menu.unfavorite") else tr("home.continueWatching.menu.favorite"),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (onAddToList != null) {
                        TextButton(
                            onClick = { onAddToList(); showActionMenu = false },
                            modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                        ) {
                            Text(tr("common.addToList"), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (onDownload != null) {
                        TextButton(
                            onClick = { onDownload(); showActionMenu = false },
                            modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                        ) {
                            Text(tr("common.download"), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(
                        onClick = { onRemove(); showActionMenu = false },
                        modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                    ) {
                        Text(tr("home.continueWatching.remove"), color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(text = tr("common.close"), onClick = { showActionMenu = false })
            },
        )
    }
}

// Prosty helper żeby uniknąć dodatkowego importu scrollState w każdym miejscu
@Composable
private fun Modifier.verticalScrollCompat(): Modifier {
    val scrollState = rememberScrollState()
    return this.verticalScroll(scrollState)
}

/** Zegar (godzina + data) w nagłówku Home — WYDZIELONY do własnej, osobnej funkcji
 * @Composable specjalnie po to, żeby jego sekundowe tykanie (LaunchedEffect niżej) NIE
 * kaskadowało do reszty ekranu. Patrz obszerny komentarz przy miejscu, skąd `now`/
 * LaunchedEffect zostały stąd PRZENIESIONE (były wcześniej wprost w ciele HomeScreen) —
 * Compose grupuje jeden zasięg przebudowy na całą funkcję, w której dany stan jest
 * odczytywany, chyba że coś jawnie go rozdzieli (jak tutaj — osobna funkcja to osobny
 * zasięg). Bez tego rozdzielenia sekundowa aktualizacja zegara odświeżała całe ciało
 * HomeScreen, łącznie z ContinueWatchingRow/MyListsSection dalej w dole ekranu — co
 * najprawdopodobniej rozregulowywało wewnętrzny stan fokusu (`isFocused` w tvFocusFrame,
 * opartym o starszy mechanizm composed{}) w kartach tych sekcji co sekundę. Zgłoszenie:
 * niebieska ramka podświetlenia znikała po chwili na kartach Kontynuuj oglądanie/Moje
 * listy, a NIGDY na plakatach w gridzie Filmy/Seriale — te nie sąsiadują z żadnym tak
 * często tykającym stanem, stąd różnica w zachowaniu mimo identycznego tvFocusFrame. */
@Composable
private fun HomeClockDisplay(lang: String) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(1000)
        }
    }
    Column {
        Text(
            SimpleDateFormat("HH:mm", Locale(lang)).format(now),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = TextMain,
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale(lang)).format(now),
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
        )
    }
}

/** Pole tekstowe search-bara na Home — ten sam wygląd co SearchField/BasicTextFieldCompat
 * na Movies/Series/Live, powielone lokalnie żeby nie zmieniać widoczności tamtych private funs. */
@Composable
private fun HomeSearchField(
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

/**
 * Sekcja "MOJE LISTY" na Home — pozioma "półka" kart, jedna karta na listę (Ulubione
 * zawsze pierwsze, potem listy własne w kolejności utworzenia), plus kafelek "+" na
 * końcu do utworzenia nowej. Okładka karty: plakat pierwszej pozycji z listy, jeśli
 * jest dostępny, w przeciwnym razie duża emotka na jednolitym tle. Stuknięcie w kartę
 * otwiera ListDetailScreen z pełną zawartością tej listy.
 */
@Composable
private fun MyListsSection(
    lists: List<com.iptvplayer.app.data.CustomList>,
    liveChannels: List<Channel>,
    movies: List<Channel>,
    series: List<SeriesInfo>,
    onOpenList: (String) -> Unit,
    onCreateList: (name: String, emoji: String) -> com.iptvplayer.app.data.CustomList,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val sortedLists = remember(lists) {
        lists.sortedWith(compareByDescending<com.iptvplayer.app.data.CustomList> { it.isBuiltIn }.thenBy { it.createdAt })
    }

    Text(
        tr("home.myLists.title"),
        color = TextDim,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sortedLists, key = { it.id }) { list ->
            val coverItems = remember(list, liveChannels, movies, series) {
                findCoverPosters(list, liveChannels, movies, series)
            }
            MyListTile(
                name = list.name,
                emoji = list.emoji ?: "🎬",
                count = list.items.size,
                coverItems = coverItems,
                onClick = { onOpenList(list.id) },
            )
        }
        item {
            CreateListTile(onClick = { showCreateDialog = true })
        }
    }
    Spacer(Modifier.height(20.dp))

    if (showCreateDialog) {
        CreateListDialog(
            onConfirm = { name, emoji -> onCreateList(name, emoji); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }
}

/** Szuka do 4 pierwszych pozycji na liście do mozaikowej okładki karty (na wzór kolekcji
 * Netflixa) — dla KAŻDEJ zwraca parę (adres plakatu-lub-null, nazwa pozycji). Pomija
 * pozycje, których źródło już nie ma w aktualnie wczytanej playliście (np. usunięty kanał u
 * providera). Adres plakatu bywa null (część playlist, zwłaszcza kanały Live, nie ma logo w
 * ogóle) — w TAKIM przypadku ListCoverMosaic niżej pokazuje zamiast obrazka inicjały samej
 * nazwy, żeby okładka nadal odróżniała się wizualnie od innych list, zamiast całkiem pustego
 * kafelka (zgłoszenie: lista "informacyjne" z 11 pozycjami pokazywała tylko generyczną ikonkę
 * telewizora, bo żaden z tych konkretnych kanałów nie ma logo u dostawcy). */
private fun findCoverPosters(
    list: com.iptvplayer.app.data.CustomList,
    liveChannels: List<Channel>,
    movies: List<Channel>,
    series: List<SeriesInfo>,
    max: Int = 4,
): List<Pair<String?, String>> {
    val result = mutableListOf<Pair<String?, String>>()
    val seenUrls = mutableSetOf<String>()
    for (ref in list.items) {
        if (result.size >= max) break
        val (url, name) = when (ref.kind) {
            com.iptvplayer.app.data.MediaKind.LIVE -> liveChannels.find { it.id == ref.id }?.let { it.logoUrl to it.name } ?: continue
            com.iptvplayer.app.data.MediaKind.MOVIE -> movies.find { it.id == ref.id }?.let { it.logoUrl to it.name } ?: continue
            com.iptvplayer.app.data.MediaKind.SERIES -> series.find { it.id == ref.id }?.let { it.posterUrl to it.name } ?: continue
        }
        // Duplikaty tego samego adresu pomijane jak dotychczas — ale TYLKO gdy adres
        // faktycznie istnieje (dwie pozycje bez logo mają obie url=null, to nie duplikat
        // w sensie "ten sam obrazek dwa razy", tylko dwa OSOBNE brakujące obrazki).
        if (url != null && url in seenUrls) continue
        if (url != null) seenUrls.add(url)
        result.add(url to name)
    }
    return result
}

@Composable
private fun MyListTile(name: String, emoji: String, count: Int, coverItems: List<Pair<String?, String>>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            // tvFocusFrame PRZED clip/background — zgłoszenie: niebieska ramka fokusu
            // nigdy się nie pokazywała na tym kafelku (w przeciwieństwie do PosterCard,
            // gdzie działa niezawodnie). Modifier.border() rysowany przez tvFocusFrame
            // rozkłada się w połowie NA granicy kształtu — jeśli clip() zostanie
            // zastosowany WCZEŚNIEJ w łańcuchu (jak było tutaj), przycina wszystko
            // narysowane PÓŹNIEJ do tego samego kształtu, ucinając zewnętrzną połowę
            // grubości ramki niemal do niewidoczności. PosterCard ma tvFocusFrame jako
            // PIERWSZY modyfikator z tego samego powodu — ta sama kolejność tutaj.
            .tvFocusFrame(shape = RoundedCornerShape(12.dp), scaleWhenFocused = 1.05f)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            // Mozaika pokazuje się zawsze, gdy lista ma choć jedną pozycję — CoverMosaicCell
            // sama decyduje, czy pokazać prawdziwy plakat czy inicjały nazwy (patrz wyżej).
            // Pusta lista (0 pozycji) to jedyny przypadek, gdzie faktycznie nie ma czym
            // wypełnić kafelków, więc zostaje sama duża emotka na jednolitym tle.
            if (coverItems.isNotEmpty()) {
                ListCoverMosaic(coverItems, Modifier.fillMaxSize())
                // Delikatna nakładka pod spodem, żeby emotka/podpis były czytelne na jasnym plakacie.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .height(30.dp)
                        .background(Bg.copy(alpha = 0.55f)),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Surface3), contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (coverItems.isNotEmpty()) {
                Text(
                    emoji,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            color = TextMain,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            if (count == 1) tr("home.myLists.itemCountOne") else tr("home.myLists.itemCountMany", count),
            color = TextDim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/** Okładka karty listy jako mozaika plakatów (na wzór kolekcji Netflixa): 1 plakat wypełnia
 * całość, 2 dzielą kafelek pionowo, 3-4 układają się w siatkę 2×2. `urls` ma zawsze od 1 do 4
 * elementów w tym miejscu wywołania (patrz `coverUrls.isNotEmpty()` wyżej), ale funkcja jest
 * odporna też na dowolną inną liczbę, gdyby coś to wywołało z innym stanem w przyszłości. */
@Composable
private fun ListCoverMosaic(items: List<Pair<String?, String>>, modifier: Modifier = Modifier) {
    val gap = 1.dp
    when {
        items.isEmpty() -> Box(modifier.background(Surface3))
        items.size == 1 -> CoverMosaicCell(items[0].first, items[0].second, modifier)
        items.size == 2 -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            items.forEach { (url, name) ->
                CoverMosaicCell(url, name, Modifier.weight(1f).fillMaxHeight())
            }
        }
        items.size == 3 -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            CoverMosaicCell(items[0].first, items[0].second, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                CoverMosaicCell(items[1].first, items[1].second, Modifier.weight(1f).fillMaxWidth())
                CoverMosaicCell(items[2].first, items[2].second, Modifier.weight(1f).fillMaxWidth())
            }
        }
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                items.subList(0, 2).forEach { (url, name) ->
                    CoverMosaicCell(url, name, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                items.subList(2, minOf(4, items.size)).forEach { (url, name) ->
                    CoverMosaicCell(url, name, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Pojedyncza komórka mozaiki okładki listy — prawdziwy plakat, jeśli dostępny, w
 * przeciwnym razie inicjały nazwy pozycji na jednolitym tle (ten sam pomysł co fallback
 * PosterCard w GridScreens.kt). Kolor tła wyliczony z hasha nazwy — żeby różne pozycje BEZ
 * plakatu w tej samej mozaice wizualnie się od siebie odróżniały, zamiast wszystkie mieć
 * identyczne, jednolicie szare tło. */
@Composable
private fun CoverMosaicCell(url: String?, name: String, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        val hue = (name.hashCode().and(0xFFFFFF)) % 360
        val bg = androidx.compose.ui.graphics.Color.hsv(hue.toFloat(), 0.35f, 0.35f)
        Box(modifier.background(bg), contentAlignment = Alignment.Center) {
            Text(
                name.take(2).uppercase(),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CreateListTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .tvFocusFrame(shape = RoundedCornerShape(12.dp), scaleWhenFocused = 1.05f)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = tr("home.myLists.newList"), tint = TextDim, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            tr("home.myLists.newList"),
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
