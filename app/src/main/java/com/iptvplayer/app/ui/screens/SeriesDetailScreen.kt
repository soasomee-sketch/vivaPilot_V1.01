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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.data.Episode
import com.iptvplayer.app.data.ListItemRef
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.SeriesInfo
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame

/** "X sezonów • Y odcinków" pod plakatem — polska gramatyka ma TRZY formy liczby mnogiej
 * (sezon/sezony/sezonów), więc dla "pl" zachowujemy dokładnie oryginalną logikę zamiast
 * jednego uproszczonego szablonu z tabeli tłumaczeń. Dla pozostałych języków wystarczy
 * prostsza, dwuwariantowa odmiana (liczba pojedyncza/mnoga), więc tam korzystamy z tr(). */
private fun formatSeasonsEpisodesSummary(seasonCount: Int, episodeCount: Int, lang: String): String {
    if (lang == "pl") {
        val seasonWord = if (seasonCount == 1) "sezon" else if (seasonCount in 2..4) "sezony" else "sezonów"
        return "$seasonCount $seasonWord · $episodeCount odcinków"
    }
    val seasonsPart = trFor(lang, if (seasonCount == 1) "seriesDetail.season.one" else "seriesDetail.season.many", seasonCount)
    val episodesPart = trFor(lang, if (episodeCount == 1) "seriesDetail.episode.one" else "seriesDetail.episode.many", episodeCount)
    return "$seasonsPart · $episodesPart"
}

/**
 * Ekran szczegółów serialu — odpowiednik openSeriesDetail()/renderSeriesDetail() z app.js:
 * plakat + tytuł na górze, poziomy pasek sezonów (chipy 1:1 jak kategorie), pod spodem
 * pionowa lista odcinków z przyciskiem odtwarzania. Dociąganie sezonów/odcinków dzieje się
 * leniwie w AppViewModel.openSeriesDetail() (get_series_info), tu tylko stan ładowania/błędu.
 */
@Composable
fun SeriesDetailScreen(
    series: SeriesInfo,
    isFavorite: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    // "Od początku" — osobny przycisk obok "Wznów", pokazywany TYLKO gdy istnieje zapisany
    // postęp dla TEGO serialu (patrz resumeProgress niżej). Ten sam mechanizm co w
    // MovieDetailScreen.kt — nie kasuje zapisanego postępu, tylko pomija go jednorazowo.
    onPlayEpisodeFromStart: (Episode) -> Unit,
    // --- pobieranie: cały sezon (dialog wyboru sezonu, rysowany przez wywołującego, bo
    // sezony są już tutaj załadowane) i pojedynczy odcinek z listy (bez konieczności
    // odtwarzania go najpierw — patrz EpisodeRow niżej) ---
    onDownloadSeason: () -> Unit = {},
    onDownloadEpisode: (Episode) -> Unit = {},
    watchProgress: Map<String, com.iptvplayer.app.data.WatchProgress> = emptyMap(),
    // --- "Inne seriale w tej kategorii" — liczone lokalnie w AppViewModel z już pobranej
    // listy state.series (wspólny group), bez dodatkowego zapytania do API ---
    similarSeries: List<SeriesInfo> = emptyList(),
    favorites: Set<String> = emptySet(),
    onToggleFavoriteId: (String) -> Unit = {},
    onSelectSimilar: (SeriesInfo) -> Unit = {},
    // --- "Dodaj do listy" — dowolna niestandardowa lista użytkownika, w tym Ulubione ---
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    onCreateList: (name: String, emoji: String) -> CustomList = { n, e -> CustomList(id = "", name = n, emoji = e) },
    autoCloseAddToList: Boolean = false,
) {
    var selectedSeason by remember(series.id) { mutableStateOf(series.seasons.firstOrNull()?.number) }
    var showAddToListSheet by remember { mutableStateOf(false) }

    // gdy dojdą sezony po leniwym doładowaniu, domyślnie zaznacz pierwszy
    LaunchedEffect(series.seasons) {
        if (selectedSeason == null) selectedSeason = series.seasons.firstOrNull()?.number
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // --- pasek góra: wstecz + tytuł + dodaj do listy + gwiazdka ulubionych ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = tr("common.back"), tint = TextMain)
            }
            Text(
                series.name,
                color = TextMain,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (series.seasons.isNotEmpty()) {
                IconButton(onClick = onDownloadSeason, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                    Icon(Icons.Default.Download, contentDescription = tr("seriesDetail.downloadSeason"), tint = TextDim)
                }
            }
            IconButton(onClick = { showAddToListSheet = true }, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                Icon(Icons.Default.Add, contentDescription = tr("common.addToList"), tint = TextDim)
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                    contentDescription = tr("common.favorites"),
                    tint = if (isFavorite) Accent2 else TextDim,
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // --- plakat + grupa/kategoria ---
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Box(
                        Modifier
                            .width(110.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2),
                    ) {
                        if (series.posterUrl != null) {
                            AsyncImage(model = series.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(series.name.take(2).uppercase(), color = TextDim)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(top = 4.dp)) {
                        Text(series.group ?: tr("globalSearch.noCategory"), color = TextDim, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        val seasonCount = series.seasons.size
                        val episodeCount = series.seasons.sumOf { it.episodes.size }
                        if (seasonCount > 0) {
                            Text(
                                formatSeasonsEpisodesSummary(seasonCount, episodeCount, LocalLang.current),
                                color = TextMain,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // --- "Oglądaj"/"Wznów" tuż pod plakatem — bez tego jedyną drogą do
                // odtwarzania było przewinięcie w dół i ręczne odszukanie właściwego odcinka
                // wśród sezonów (zgłoszenie). Znajduje NAJNOWIEJ oglądany odcinek TEGO
                // serialu po watchProgress.seriesId; jeśli nic jeszcze nie oglądano,
                // startuje od pierwszego odcinka pierwszego sezonu.
                val resumeProgress = remember(series.id, watchProgress) {
                    watchProgress.values.filter { it.seriesId == series.id }.maxByOrNull { it.updatedAt }
                }
                val resumeEpisode = remember(series.seasons, resumeProgress) {
                    if (resumeProgress != null) {
                        series.seasons.firstNotNullOfOrNull { s -> s.episodes.find { it.id == resumeProgress.channelOrEpisodeId } }
                    } else {
                        series.seasons.firstOrNull()?.episodes?.firstOrNull()
                    }
                }
                if (resumeEpisode != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlayEpisode(resumeEpisode) },
                            modifier = Modifier.weight(1f).tvFocusFrame(shape = RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (resumeProgress != null) tr("seriesDetail.resumeButton") else tr("seriesDetail.startButton"),
                                color = Color.Black,
                            )
                        }
                        // "Od początku" — WYŁĄCZNIE gdy jest zapisany postęp (bez niego przycisk
                        // wyżej i tak startuje od zera, drugi byłby zbędnym duplikatem).
                        // Restartuje TEN SAM odcinek (resumeEpisode — ten z zapisanym postępem),
                        // NIE przeskakuje do odcinka 1 sezonu 1 — jeśli ktoś jest przy odcinku 5,
                        // "od początku" oznacza od początku TEGO odcinka, nie całego serialu.
                        if (resumeProgress != null) {
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { onPlayEpisodeFromStart(resumeEpisode) },
                                modifier = Modifier.size(48.dp).tvFocusFrame(shape = RoundedCornerShape(10.dp)),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = tr("seriesDetail.playFromStartButton"))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // --- opis/gatunek/ocena — wyłącznie z Xtream (get_series_info); puste, jeśli
            // dostawca tego nie podał (TMDB usunięte z aplikacji, bez zapasowego źródła).
            // Pokazuje się dopiero po doładowaniu sezonów (to ten sam moment, kiedy Xtream
            // zwraca ten opis) — stąd zależność od series.plot, nie osobny stan ładowania. ---
            if (!series.plot.isNullOrBlank() || !series.genre.isNullOrBlank()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        if (!series.genre.isNullOrBlank() || series.rating != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!series.genre.isNullOrBlank()) {
                                    Text(series.genre, color = TextDim, style = MaterialTheme.typography.bodySmall)
                                }
                                if (series.rating != null) {
                                    if (!series.genre.isNullOrBlank()) Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Warning, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("%.1f".format(java.util.Locale.US, series.rating), color = TextDim, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        if (!series.plot.isNullOrBlank()) {
                            Text(series.plot, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // --- "Inne seriale w tej kategorii" ---
            if (similarSeries.isNotEmpty()) {
                item {
                    Text(
                        tr("seriesDetail.similarTitle"),
                        color = TextMain,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(similarSeries, key = { it.id }) { similar ->
                            Box(Modifier.width(110.dp)) {
                                PosterCard(
                                    title = similar.name,
                                    posterUrl = similar.posterUrl,
                                    isFavorite = similar.id in favorites,
                                    onToggleFavorite = { onToggleFavoriteId(similar.id) },
                                    onClick = { onSelectSimilar(similar) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            // --- stany ładowania / błędu ---
            if (isLoading && series.seasons.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                return@LazyColumn
            }
            if (errorMessage != null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(errorMessage, color = Danger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                return@LazyColumn
            }
            if (series.seasons.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(tr("seriesDetail.noEpisodesInfo"), color = TextDim)
                    }
                }
                return@LazyColumn
            }

            // --- chipy sezonów (z licznikiem obejrzanych odcinków w danym sezonie) ---
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(series.seasons) { season ->
                        val watchedCount = season.episodes.count { ep ->
                            val p = watchProgress[ep.id]
                            p != null && p.durationMillis > 0 && (p.positionMillis.toFloat() / p.durationMillis) >= 0.92f
                        }
                        val label = if (watchedCount > 0) {
                            tr("seriesDetail.seasonChipWatched", season.number, watchedCount, season.episodes.size)
                        } else {
                            tr("seriesDetail.seasonChip", season.number, season.episodes.size)
                        }
                        FilterChipCompat(
                            label = label,
                            active = selectedSeason == season.number,
                            onClick = { selectedSeason = season.number },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // --- lista odcinków wybranego sezonu ---
            val episodes = series.seasons.find { it.number == selectedSeason }?.episodes.orEmpty()
            if (episodes.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(tr("root.seasonDownload.noEpisodesToast"), color = TextDim)
                    }
                }
            } else {
                items(episodes) { ep ->
                    EpisodeRow(
                        episode = ep,
                        progress = watchProgress[ep.id],
                        onClick = { onPlayEpisode(ep) },
                        onDownload = { onDownloadEpisode(ep) },
                    )
                }
            }
        }
    }

    if (showAddToListSheet) {
        val refs = listOf(ListItemRef(id = series.id, kind = MediaKind.SERIES))
        val alreadyIn = remember(customLists, series.id) {
            customLists.filter { list -> list.items.any { it.id == series.id } }.map { it.id }.toSet()
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
            onDismiss = { showAddToListSheet = false },
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: com.iptvplayer.app.data.WatchProgress?,
    onClick: () -> Unit,
    onDownload: () -> Unit = {},
) {
    // Próg "obejrzane" — tak jak w typowych serwisach VOD, ostatnie ~7% (napisy końcowe)
    // liczymy jako ukończone, żeby nie trzeba było oglądać do samej sekundy zero.
    // Gdy prawdziwa długość jest nieznana (durationMillis=0 — zgłoszenie: pasek postępu w
    // ogóle się nie pokazywał dla takich odcinków), używamy TEJ SAMEJ "wirtualnej" długości
    // co pasek przewijania w odtwarzaczu (PlayerActivity.kt) — dla spójności wizualnej
    // między listą odcinków a samym odtwarzaczem. To wciąż tylko PRZYBLIŻENIE (nie znamy
    // naprawdę, ile % zostało obejrzane), ale lepsze niż całkowity brak jakiejkolwiek
    // wskazówki — a "obejrzane" (isWatched) celowo NIE korzysta z tego przybliżenia (zostaje
    // false, dopóki appka nie pozna prawdziwej długości), żeby nie oznaczać czegoś jako
    // ukończone na podstawie zgadywania.
    val ratio = if (progress != null && progress.durationMillis > 0) {
        (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f)
    } else if (progress != null && progress.positionMillis > 10_000) {
        val virtualDurationMs = maxOf(progress.positionMillis + 300_000L, 600_000L)
        (progress.positionMillis.toFloat() / virtualDurationMs).coerceIn(0f, 1f)
    } else 0f
    val isWatched = progress != null && progress.durationMillis > 0 && ratio >= 0.92f
    val isInProgress = progress != null && !isWatched && ratio > 0.02f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusFrame(shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface2),
                contentAlignment = Alignment.Center,
            ) {
                if (isWatched) {
                    Icon(Icons.Default.CheckCircle, contentDescription = tr("seriesDetail.watchedDesc"), tint = Accent, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isInProgress) Accent else TextDim, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${episode.episodeNumber}. ${episode.title}",
                    color = if (isWatched) TextDim else TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // --- pobierz pojedynczy odcinek — bez konieczności odtwarzania go najpierw
            // (wcześniej pobieranie odcinka było możliwe tylko z poziomu odtwarzacza) ---
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp).tvFocusFrame(shape = CircleShape)) {
                Icon(Icons.Default.Download, contentDescription = tr("seriesDetail.downloadEpisodeDesc"), tint = TextDim, modifier = Modifier.size(18.dp))
            }
        }
        // --- cienki pasek postępu pod tytułem — tylko dla odcinków oglądanych częściowo ---
        if (isInProgress) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .padding(start = 48.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Surface3),
            ) {
                Box(Modifier.fillMaxWidth(ratio).height(3.dp).background(Accent))
            }
        }
    }
}
