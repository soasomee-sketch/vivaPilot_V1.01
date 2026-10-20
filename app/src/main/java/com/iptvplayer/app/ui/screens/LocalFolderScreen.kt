package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.data.LocalFolder
import com.iptvplayer.app.data.LocalVideoFile
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame

/**
 * Odpowiednik dawnej zakładki Playlists na dolnym pasku — zamiast playlist IPTV pozwala
 * podłączyć dowolny lokalny folder z urządzenia (karta SD, pamięć wewnętrzna, pendrive przez
 * OTG) i odtwarzać zapisane w nim filmy bezpośrednio przez wbudowany silnik libVLC. Cała
 * funkcjonalność playlist IPTV (M3U/Xtream) przeniosła się do zakładki Settings — patrz
 * SettingsScreen.onManagePlaylists.
 */
@Composable
fun LocalFolderScreen(
    folders: List<LocalFolder>,
    videos: List<LocalVideoFile>,
    isScanning: Boolean,
    errors: Map<String, String> = emptyMap(),
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit = {},
    onPlayVideo: (LocalVideoFile, List<LocalVideoFile>) -> Unit,
    // "Kontynuuj oglądanie" dla plików lokalnych (kind=MOVIE, streamUrl zaczyna się od
    // "content://") — świadomie OSOBNO od tej samej sekcji w zakładce Filmy (patrz
    // GridScreens.kt, gdzie te same wpisy są teraz jawnie WYKLUCZONE), żeby lokalne pliki
    // nie mieszały się z prawdziwym VOD z playlisty w jednej liście.
    continueWatching: List<com.iptvplayer.app.data.WatchProgress> = emptyList(),
    onResumeWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    onRemoveWatching: (com.iptvplayer.app.data.WatchProgress) -> Unit = {},
    // Cel fokusu przy zejściu pilotem W DÓŁ z dolnego paska nawigacji — ten sam mechanizm
    // co w LiveScreen.kt/HomeScreen.kt/GridScreens.kt (tam obszerniejszy komentarz z
    // uzasadnieniem). Ten ekran nie ma jednak żadnego elementu ZAWSZE obecnego u góry
    // (Kontynuuj oglądanie jest warunkowe, foldery bywają puste) — dlatego zamiast
    // podpinać się pod istniejący element, dodaje się niewidoczny, ale realnie
    // fokusowalny "kotwica" jako pierwsza pozycja listy (patrz niżej).
    topFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    // Automatyczne odświeżenie za KAŻDYM wejściem na tę zakładkę (nie tylko ręcznym
    // przyciskiem obok "+") — LocalFolderScreen jest tworzony od nowa przy każdym przełączeniu
    // na zakładkę Folder (patrz `when (currentTab)` w MainActivity), więc LaunchedEffect(Unit)
    // odpala się dokładnie raz PRZY WEJŚCIU, dokładnie tak jak oczekiwane. Ręczny przycisk
    // odświeżania zostaje obok — przydaje się, gdy ktoś dorzuci plik do folderu, siedząc już
    // na tej zakładce, bez wychodzenia i wracania.
    LaunchedEffect(Unit) { onRescan() }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOff, contentDescription = null, tint = TextDim, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("localFolder.empty"),
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                // Niewidoczna "kotwica" fokusu — patrz komentarz przy topFocusRequester
                // wyżej. ZAWSZE pierwsza pozycja listy, niezależnie od tego, czy jest
                // Kontynuuj oglądanie/foldery. 1.dp (nie 0.dp) celowo — element o
                // zerowym rozmiarze mógłby nie otrzymywać fokusu w niektórych
                // implementacjach Compose; 1dp jest praktycznie niewidoczny, ale na pewno
                // realny jako cel D-pada.
                item {
                    Spacer(
                        Modifier
                            .size(1.dp)
                            .then(if (topFocusRequester != null) Modifier.focusRequester(topFocusRequester) else Modifier)
                            .focusable(),
                    )
                }
                // "Kontynuuj oglądanie" dla plików lokalnych — patrz komentarz przy
                // parametrze continueWatching wyżej. Ten sam współdzielony komponent co w
                // Filmy/Seriale/Home, żeby wyglądał i działał identycznie.
                if (continueWatching.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                tr("home.continueWatching.title"),
                                color = TextDim,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            ContinueWatchingRow(items = continueWatching, onClick = onResumeWatching, onRemove = onRemoveWatching)
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                items(folders, key = { it.id }) { folder ->
                    val folderVideos = videos.filter { it.folderId == folder.id }
                    val error = errors[folder.id]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .tvFocusFrame(shape = RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = if (error != null) Danger else Accent)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                error
                                    ?: if (isScanning) tr("localFolder.scanning")
                                    else pluralFiles(folderVideos.size, LocalLang.current),
                                color = if (error != null) Danger else TextDim,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { onRemoveFolder(folder.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = tr("localFolder.disconnectDesc"), tint = Danger)
                        }
                    }

                    if (error == null && folderVideos.isEmpty() && !isScanning) {
                        Text(
                            tr("localFolder.noVideos"),
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                        )
                    }

                    folderVideos.forEach { video ->
                        VideoRow(video = video, onClick = { onPlayVideo(video, folderVideos) })
                    }
                }
            }
        }

        // Odśwież + Dodaj folder — dwa spiętrzone FAB-y. "Odśwież" naprawia realną lukę:
        // skanowanie foldera leci automatycznie tylko raz przy starcie appki i raz zaraz
        // po podłączeniu nowego folderu — jeśli użytkownik doda plik do JUŻ podłączonego
        // folderu później (np. przez Menedżera plików), appka o tym nie wie, dopóki ktoś
        // nie poprosi o ponowne skanowanie. Bez tego przycisku `onRescan` był martwym
        // parametrem — nigdzie w UI niepodpiętym.
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            if (folders.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = { if (!isScanning) onRescan() },
                    containerColor = Surface,
                    contentColor = if (isScanning) TextDim else TextMain,
                    modifier = Modifier.tvFocusFrame(shape = CircleShape),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = tr("localFolder.rescanDesc"))
                }
                Spacer(Modifier.height(12.dp))
            }
            FloatingActionButton(
                onClick = onAddFolder,
                containerColor = Accent,
                modifier = Modifier.tvFocusFrame(shape = CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = tr("localFolder.connectDesc"))
            }
        }
    }
}

@Composable
private fun VideoRow(video: LocalVideoFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .tvFocusFrame(shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Miniatura wyciągnięta z pliku (patrz LocalVideoThumbnailer) — dopóki się nie
        // doładuje w tle (albo się nie uda, np. uszkodzony plik), pokazuje generyczną
        // ikonkę filmu w tym samym miejscu, więc układ wiersza nie "skacze" po doładowaniu.
        Box(
            Modifier
                .size(width = 56.dp, height = 34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Bg),
            contentAlignment = Alignment.Center,
        ) {
            if (video.thumbnailPath != null) {
                coil.compose.AsyncImage(
                    model = video.thumbnailPath,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.Movie, contentDescription = null, tint = TextDim, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = TextMain,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.sizeBytes > 0) {
                Text(formatFileSize(video.sizeBytes), color = TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(Icons.Default.PlayArrow, contentDescription = tr("grid.quickPlayDesc"), tint = Accent)
    }
}

/** Liczba plików wideo w folderze — polska gramatyka ma TRZY formy (plik/pliki/plików),
 * więc dla "pl" zachowujemy dokładnie oryginalną logikę zamiast jednego uproszczonego
 * szablonu z tabeli tłumaczeń (ten sam wzorzec co formatSeasonsEpisodesSummary w
 * SeriesDetailScreen.kt). Dla pozostałych języków wystarczy prostsza odmiana pojedyncza/mnoga. */
private fun pluralFiles(count: Int, lang: String): String {
    if (lang == "pl") {
        val word = when {
            count == 1 -> "plik wideo"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "pliki wideo"
            else -> "plików wideo"
        }
        return "$count $word"
    }
    return trFor(lang, if (count == 1) "localFolder.videoFile.one" else "localFolder.videoFile.many", count)
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
