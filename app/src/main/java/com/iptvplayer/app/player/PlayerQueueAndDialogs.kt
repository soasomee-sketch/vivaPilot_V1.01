package com.iptvplayer.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.session.MediaButtonReceiver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.iptvplayer.app.data.DownloadHelper
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.data.EpgProgram
import com.iptvplayer.app.data.EpgRepository
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.WatchProgress
import com.iptvplayer.app.ui.theme.Accent
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.theme.Danger
import com.iptvplayer.app.ui.theme.IptvPlayerTheme
import com.iptvplayer.app.ui.tv.notDpadFocusable
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import com.iptvplayer.app.util.isTvDevice
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.roundToInt

// Wydzielone z PlayerActivity.kt (plik miał ~3000 linii — każda poprawka w odtwarzaczu
// niosła ryzyko przypadkowego zepsucia czegoś obok). Tu: półka z plakatami kolejki
// ("dalej w kolejce") i okna dialogowe odtwarzacza (wysyłanie na TV, skok do czasu).
// Działanie bez zmian — to wyłącznie przeniesienie kodu do osobnego pliku.

/**
 * Pozioma "półka" pod paskiem postępu z CAŁĄ kolejką nawigacji (patrz PlayQueue.kt) — dla
 * kanału Live pokazuje resztę kanałów z tej samej grupy, dla serialu wszystkie odcinki, dla
 * filmu podobne filmy, a dla materiału odtworzonego z niestandardowej listy — resztę tej
 * listy. W odróżnieniu od strzałek/swipe'a (o jedną pozycję) i od NextUpOverlay (tylko
 * bezpośredni sąsiad w kolejce), tutaj widz widzi WSZYSTKO naraz i może wybrać dowolną
 * pozycję jednym dotknięciem/OK — bez czekania na żadne odliczanie.
 */
@Composable
internal fun QueueBrowserRow(
    items: List<QueueEntry>,
    activeIndex: Int,
    browserFocused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    // Wołane przy każdym rozpoczęciu przewijania palcem — patrz LaunchedEffect niżej.
    // Bez tego 4-sekundowy timer auto-ukrywania kontrolek (patrz showControls w
    // PlayerScreen) tykał dalej NIEZALEŻNIE od tego, czy użytkownik akurat aktywnie
    // przewijał tę półkę — zgłoszenie: panel z plakatami chował się w trakcie korzystania.
    onUserActivity: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    // Doscrolowanie do bieżącej pozycji przy KAŻDEJ zmianie activeIndex — niezależnie od
    // tego, czy zmiana przyszła stąd (tap na plakacie), czy z zupełnie innej ścieżki
    // (swipe, długie przytrzymanie strzałki, automatyczne przejście po zakończeniu materiału)
    // — półka ma zawsze pokazywać bieżącą pozycję, nawet jeśli użytkownik jej nie dotknął.
    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }
    // Reset timera auto-ukrywania przy KAŻDYM rozpoczęciu przewijania palcem — samo
    // przewijanie LazyRow nie przechodzi przez żaden z istniejących onClick/onKeyEvent,
    // więc bez tego było całkowicie niewidoczne dla mechanizmu "użytkownik jest aktywny".
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onUserActivity()
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            // hasFocus (nie isFocused!) jest prawdziwe też wtedy, gdy fokus ma dowolny
            // plakat WEWNĄTRZ tej listy, nie tylko sam kontener — dokładnie to, czego
            // potrzebuje globalna obsługa klawiszy wyżej (patrz browserRowFocused).
            .onFocusChanged { onFocusedChange(it.hasFocus) },
    ) {
        // Ręczna pętla zamiast itemsIndexed — pozwala wstawić dodatkowy element (separator
        // sezonu) MIĘDZY kafelkami, kiedy numer sezonu się zmienia, czego sam itemsIndexed
        // nie umożliwia. Grupowanie wg sezonu zamiast płaskiej listy wszystkich odcinków —
        // przy serialach z wieloma sezonami dawało to wcześniej długi, nieczytelny ciąg
        // identycznie wyglądających plakatów bez żadnego punktu odniesienia.
        items.forEachIndexed { idx, entry ->
            val previousSeason = items.getOrNull(idx - 1)?.seasonNumber
            if (entry.seasonNumber != null && entry.seasonNumber != previousSeason) {
                item(key = "season_${entry.seasonNumber}_$idx") {
                    SeasonDivider(seasonNumber = entry.seasonNumber)
                }
            }
            item(key = "${idx}_${entry.id}") {
                QueueBrowserTile(
                    entry = entry,
                    isActive = idx == activeIndex,
                    onClick = { onSelect(idx) },
                )
            }
        }
    }
    // Poza fokusem pilota (np. po dotknięciu ekranu) `browserRowFocused` i tak wraca do
    // false, gdy kontrolki się chowają — patrz AnimatedVisibility(controlsVisible) wyżej,
    // które usuwa tę Composable z drzewa i razem z nią cały jej stan fokusu.
}

/** Wąski separator "S{numer}" wstawiany między kafelkami QueueBrowserRow, kiedy zmienia
 * się sezon — patrz pętla w QueueBrowserRow wyżej. Celowo skrócone "S2" zamiast "Sezon 2",
 * żeby zmieściło się na wąskim elemencie bez zaburzania rytmu poziomego paska. */
@Composable
internal fun SeasonDivider(seasonNumber: Int) {
    Column(
        // STAŁA wysokość zamiast fillMaxHeight() — ZNALEZIONA, POTWIERDZONA przyczyna
        // długo zgłaszanego błędu "przeskakujących plakatów w serialach" (nakładka
        // diagnostyczna pokazała bottomBarHeightPx=2188 przy 385-odcinkowym serialu —
        // niemal cała wysokość ekranu). LazyRow w QueueBrowserRow wyżej NIE MA własnej,
        // jawnej wysokości — określa ją na podstawie NAJWYŻSZEGO dziecka. fillMaxHeight()
        // na TYM separatorze żądał "wypełnij dostępną wysokość", ale skoro LazyRow jeszcze
        // NIE ustalił własnej wysokości (bo właśnie ZALEŻY od dzieci), Compose propagował w
        // górę maksymalne dostępne ograniczenia — aż do fillMaxSize() na zewnętrznym Boxie
        // odtwarzacza — więc separator "żądał" niemal całego ekranu, a to ON stawał się
        // najwyższym dzieckiem determinującym wysokość CAŁEGO wiersza (i w konsekwencji
        // całej kolumny "Dolny pasek", wypychając plakaty blisko górnej krawędzi ekranu).
        // Dotyczyło TYLKO seriali (separatory sezonów), nigdy filmów/na żywo — dokładnie
        // zgodnie ze zgłoszeniem. 72dp dobrane tak, by pasowało do wysokości zwykłego
        // kafelka QueueBrowserTile PO zmniejszeniu o 25% (obraz 99×9/16≈56dp + odstęp 4dp +
        // tytuł ~18dp — było 96dp przy szerokości kafelka 132dp, teraz 132×0.75=99dp).
        modifier = Modifier.width(30.dp).height(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .height(24.dp)
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.25f)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "S$seasonNumber",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .height(24.dp)
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.25f)),
        )
    }
}

@Composable
internal fun QueueBrowserTile(entry: QueueEntry, isActive: Boolean, onClick: () -> Unit) {
    // Zmiana z pionowego 2:3 (jak plakat filmowy) na poziome 16:9 — zgłoszenie: obraz w
    // pionowym kafelku był przycinany, bo źródłowe miniatury (zwłaszcza logo kanałów Live i
    // kadry z odcinków seriali, w przeciwieństwie do plakatów filmów) są zwykle poziome, nie
    // pionowe. Szerokość zwiększona ze 108dp do 132dp — przy 16:9 to i tak NIŻSZY kafelek niż
    // poprzednio (132 × 9/16 ≈ 74dp obrazu, wcześniej 108dp), a szerszy kafelek to mniej
    // kafelków mieszczących się naraz na ekranie (zgłoszenie: "wciąż ich za dużo").
    // Zgłoszenie (kolejne): "zmniejsz plakaty podczas oglądania o 25%, żeby było więcej
    // przestrzeni na ekranie" — 132dp × 0.75 = 99dp, aspect ratio 16:9 zachowane, więc obraz
    // skaluje się proporcjonalnie razem z szerokością (99 × 9/16 ≈ 56dp).
    Column(
        modifier = Modifier
            .width(99.dp)
            .tvFocusFrame(shape = RoundedCornerShape(8.dp), focusColor = Color.White)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .then(
                    if (isActive) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp)) else Modifier,
                ),
        ) {
            if (!entry.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(entry.title.take(2).uppercase(), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
            // Wyraźny znacznik "to leci teraz" — na telewizorze z odległości kilku metrów
            // sama ramka w kolorze akcentu bywa słabo widoczna, stąd dodatkowo plakietka z ikoną.
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Accent.copy(alpha = 0.9f))
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.title,
            color = if (isActive) Accent else Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Lista instancji appki na TV wykrytych w tej samej sieci Wi-Fi (patrz CastManager.kt) —
 * wybranie jednej wysyła do niej aktualnie odtwarzany materiał. Wyszukiwanie uruchamia się
 * przy otwarciu okna i zatrzymuje przy zamknięciu — DisposableEffect gwarantuje, że
 * zatrzymanie nastąpi też gdy okno zniknie z INNEGO powodu niż kliknięcie "Zamknij" (np.
 * powrót do Home podczas gdy okno było otwarte).
 */
@Composable
internal fun CastPickerDialog(
    context: android.content.Context,
    onSend: (com.iptvplayer.app.cast.CastManager.CastTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = remember { mutableStateListOf<com.iptvplayer.app.cast.CastManager.CastTarget>() }
    var sentTo by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val listener = com.iptvplayer.app.cast.CastManager.discoverTvTargets(
            context,
            onFound = { target ->
                val idx = targets.indexOfFirst { it.name == target.name }
                if (idx >= 0) targets[idx] = target else targets.add(target)
            },
            onLost = { name -> targets.removeAll { it.name == name } },
        )
        onDispose { com.iptvplayer.app.cast.CastManager.stopDiscovery(context, listener) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("player.castDialogTitle")) },
        text = {
            Column {
                if (targets.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("player.castSearching"), color = TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    targets.forEach { target ->
                        TextButton(
                            onClick = { onSend(target); sentTo = target.name },
                            modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                        ) {
                            Text(
                                if (sentTo == target.name) tr("player.castSentTo", target.name) else target.name,
                                modifier = Modifier.fillMaxWidth(),
                            )
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

/**
 * Ręczne wpisanie docelowego czasu do przewinięcia — otwierane długim przytrzymaniem
 * przycisków -10s/+30s (dotyk LUB OK na pilocie, patrz Box z combinedClickable/onKeyEvent
 * przy tych przyciskach). Akceptuje elastyczny format: same sekundy ("90"), MM:SS ("1:30")
 * albo HH:MM:SS ("1:05:30") — dopasowane do tego, co użytkownik naturalnie wpisze, bez
 * zmuszania do jednego sztywnego formatu.
 */
@Composable
internal fun CustomSeekDialog(
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(formatTime(currentPositionMs)) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("player.customSeekTitle")) },
        text = {
            Column {
                Text(tr("player.customSeekHint"), color = TextDim, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("player.customSeekError"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ms = parseFlexibleTimeToMs(text)
                    if (ms == null) {
                        error = true
                    } else {
                        onSeek(ms)
                        onDismiss()
                    }
                },
                modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) { Text(tr("player.customSeekGo")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("common.cancel")) }
        },
    )
}

/** Parsuje "90" / "1:30" / "1:05:30" na milisekundy — null, jeśli tekst jest niepoprawny
 * (nie same cyfry i dwukropki, albo pusty). Celowo tolerancyjne wobec spacji na końcach. */
internal fun parseFlexibleTimeToMs(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":").map { it.trim() }
    if (parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return null
    val numbers = parts.mapNotNull { it.toLongOrNull() }
    if (numbers.size != parts.size) return null
    return when (numbers.size) {
        1 -> numbers[0] * 1000L
        2 -> (numbers[0] * 60 + numbers[1]) * 1000L
        3 -> (numbers[0] * 3600 + numbers[1] * 60 + numbers[2]) * 1000L
        else -> null
    }
}
