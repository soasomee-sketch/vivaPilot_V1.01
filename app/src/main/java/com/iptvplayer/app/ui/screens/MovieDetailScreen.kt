package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.CustomList
import com.iptvplayer.app.data.ListItemRef
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.VodDetail
import com.iptvplayer.app.data.WatchProgress
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame

/**
 * Ekran informacji o filmie — otwierany kliknięciem plakatu (zamiast natychmiastowego
 * odtwarzania). Odpowiada widokowi ze starej wersji webowej: plakat + tytuł, znaczki
 * rok/czas trwania/ocena/gatunek, opis, obsada i reżyser, na dole przycisk odtwarzania
 * i pobierania. Dane opisowe (plot/cast/director/genre/rating/duration) są dociągane
 * leniwie z Xtream API (action=get_vod_info) — dokładnie tak jak w app.js, gdzie te
 * informacje pochodziły bezpośrednio z danych playlisty.
 */
@Composable
fun MovieDetailScreen(
    movie: Channel,
    detail: VodDetail?,
    isLoadingDetail: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    // "Odtwórz od początku" — osobny przycisk obok "Kontynuuj oglądanie", pokazywany TYLKO
    // gdy istnieje zapisany postęp (patrz hasProgress niżej). Woła onPlayChannelFromStart w
    // MainActivity.kt, które pomija automatyczne wznowienie z zapisanej pozycji, ale JEJ NIE
    // KASUJE — można później i tak normalnie wznowić przyciskiem "Kontynuuj".
    onPlayFromStart: () -> Unit,
    onDownload: () -> Unit,
    watchProgress: WatchProgress? = null,
    // --- "Podobne filmy" (ta sama kategoria / gatunek), liczone lokalnie w AppViewModel
    // z już pobranej listy state.movies — bez dodatkowego zapytania do API ---
    similarMovies: List<Channel> = emptyList(),
    favorites: Set<String> = emptySet(),
    onToggleFavoriteId: (String) -> Unit = {},
    onSelectSimilar: (Channel) -> Unit = {},
    // --- "Dodaj do listy" — dowolna niestandardowa lista użytkownika, w tym Ulubione ---
    customLists: List<CustomList> = emptyList(),
    onToggleManyInList: (listId: String, refs: List<ListItemRef>) -> Unit = { _, _ -> },
    onCreateList: (name: String, emoji: String) -> CustomList = { n, e -> CustomList(id = "", name = n, emoji = e) },
    autoCloseAddToList: Boolean = false,
) {
    var showAddToListSheet by remember { mutableStateOf(false) }

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
                movie.name,
                color = TextMain,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
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

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // --- plakat + tytuł + kategoria ---
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .width(130.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2),
                ) {
                    // Wyłącznie plakat z playlisty (movie.logoUrl) — TMDB usunięte z aplikacji,
                    // więc gdy dostawca nie poda własnego plakatu, pozycja po prostu go nie ma.
                    val posterUrl = movie.logoUrl
                    if (posterUrl != null) {
                        AsyncImage(model = posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(movie.name.take(2).uppercase(), color = TextDim)
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(movie.group ?: tr("globalSearch.noCategory"), color = TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        movie.name,
                        color = TextMain,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )

                    // --- pasek postępu, jeśli film był już częściowo obejrzany ---
                    if (watchProgress != null && watchProgress.durationMillis > 0) {
                        val ratio = (watchProgress.positionMillis.toFloat() / watchProgress.durationMillis).coerceIn(0f, 1f)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Surface3),
                        ) {
                            Box(Modifier.fillMaxWidth(ratio).height(4.dp).background(Accent))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("movieDetail.watchedPercent", (ratio * 100).toInt()),
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // --- duży przycisk odtwarzania (▶ Odtwórz / ▶ Kontynuuj oglądanie) + "od początku"
            // (tylko gdy jest zapisany postęp) + pobierz obok --- CELOWO zaraz pod
            // plakatem/tytułem, PRZED znaczkami/opisem/obsadą — zgłoszenie: przy długim opisie
            // przycisk "uciekał" daleko w dół, wymagając przewijania, mimo że użytkownik
            // odruchowo sięgał po niego od razu przy plakacie. Kolejność metadanych
            // (rok/ocena/gatunek/opis/obsada/reżyser) zostaje bez zmian, tylko PRZESUNIĘTA niżej.
            val hasProgress = watchProgress != null && watchProgress.durationMillis > 0 &&
                (watchProgress.positionMillis.toFloat() / watchProgress.durationMillis) < 0.92f
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).height(48.dp).tvFocusFrame(shape = RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasProgress) tr("movieDetail.continueButton") else tr("movieDetail.playButton"))
                }
                // "Od początku" — WYŁĄCZNIE gdy jest zapisany postęp (bez postępu "Odtwórz"
                // wyżej i tak zawsze startuje od zera, drugi przycisk byłby zbędnym duplikatem).
                // Kompaktowa ikona (nie pełny przycisk z tekstem) — na wąskim ekranie telefonu
                // trzy pełnowymiarowe przyciski obok siebie by się nie zmieściły.
                if (hasProgress) {
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = onPlayFromStart,
                        modifier = Modifier.size(48.dp).tvFocusFrame(shape = RoundedCornerShape(10.dp)),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = tr("movieDetail.playFromStartButton"), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.size(48.dp).tvFocusFrame(shape = RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = tr("common.download"), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // --- znaczki: rok, czas trwania, ocena, gatunek — jak w wersji webowej ---
            val year = detail?.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
            val badges = buildList {
                year?.let { add(it) }
                detail?.durationLabel?.let { add(it) }
                detail?.rating?.let { add("★ %.3f".format(it)) }
                detail?.genre?.let { add(it) }
            }
            if (isLoadingDetail && badges.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("movieDetail.loadingInfo"), color = TextDim, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
            } else if (badges.isNotEmpty()) {
                FlowBadgeRow(badges)
                Spacer(Modifier.height(14.dp))
            }

            // --- opis ---
            if (detail?.plot != null) {
                Text(detail.plot, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
            }

            // --- obsada ---
            if (detail?.cast != null) {
                Row {
                    Text(tr("movieDetail.castLabel"), color = TextMain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(detail.cast, color = TextDim, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
            }

            // --- reżyser ---
            if (detail?.director != null) {
                Row {
                    Text(tr("movieDetail.directorLabel"), color = TextMain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(detail.director, color = TextDim, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
            }

            // --- "Podobne filmy": ta sama kategoria (i wspólny gatunek, jeśli już znany) ---
            if (similarMovies.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    tr("movieDetail.similarTitle"),
                    color = TextMain,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(similarMovies, key = { it.id }) { similar ->
                        Box(Modifier.width(110.dp)) {
                            PosterCard(
                                title = similar.name,
                                posterUrl = similar.logoUrl,
                                isFavorite = similar.id in favorites,
                                onToggleFavorite = { onToggleFavoriteId(similar.id) },
                                onClick = { onSelectSimilar(similar) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddToListSheet) {
        val refs = listOf(ListItemRef(id = movie.id, kind = MediaKind.MOVIE))
        val alreadyIn = remember(customLists, movie.id) {
            customLists.filter { list -> list.items.any { it.id == movie.id } }.map { it.id }.toSet()
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

/** Prosty poziomo przewijany rząd "pigułek" (rok/czas/ocena/gatunek) — jak chip-bar pod plakatem w app.js. */
@Composable
private fun FlowBadgeRow(badges: List<String>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badges.forEach { text ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Surface2)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text, color = TextMain, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
