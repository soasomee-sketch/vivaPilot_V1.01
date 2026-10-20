package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import com.iptvplayer.app.util.containsFolded
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.EpgProgram
import com.iptvplayer.app.data.EpgRepository
import com.iptvplayer.app.data.PlaylistSource
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.player.VlcPlayerManager
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val HOUR_WIDTH_DP = 220
private const val ROW_HEIGHT_DP = 68
private const val CHANNEL_COLUMN_WIDTH_DP = 128
private const val MIN_PROGRAM_WIDTH_DP = 50

/**
 * Pełna siatka EPG w stylu TiviMate — kanały w pionie, oś czasu w poziomie, z małym
 * podglądem wideo bieżąco wybranego kanału na górze. To dopełnienie mniejszego dialogu
 * EpgGuideDialog (jeden kanał, lista programów) o pełny, "gridowy" widok wszystkich
 * kanałów naraz — dokładnie to, czego brakowało wcześniej ("miał być pełny EPG jak
 * w TiviMate, a włącza się tylko info").
 *
 * Architektura synchronizacji przewijania w poziomie: WSZYSTKIE wiersze (nagłówek osi
 * czasu + każdy wiersz kanału w LazyColumn) czytają TEN SAM `horizontalOffsetPx` i
 * przesuwają swoją (szerszą niż ekran) zawartość o ten sam offset przez Modifier.offset
 * wewnątrz Modifier.clipToBounds() — więc mimo że LazyColumn wirtualizuje wiersze pionowo
 * (kluczowe przy playlistach z setkami kanałów), wszystkie aktualnie widoczne wiersze
 * przewijają się w poziomie idealnie zsynchronizowanie, bo dzielą jeden wspólny stan.
 */
@Composable
fun EpgGridScreen(
    channels: List<Channel>,
    playlists: List<PlaylistSource>,
    epgRepository: EpgRepository,
    onBack: () -> Unit,
    onWatchFullscreen: (Channel) -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    fun epgUrlFor(ch: Channel): String? = playlists.find { it.id == ch.playlistId }?.epgUrl

    // Tylko kanały z faktycznym źródłem EPG mają tu sens — reszta i tak nie miałaby czego
    // pokazać w swoim wierszu (patrz ten sam warunek co przy ikonie ⓘ w LiveScreen).
    val gridChannels = remember(channels, playlists) {
        channels.filter { it.epgChannelId != null && epgUrlFor(it) != null }
    }

    // Filtrowanie grid EPG po grupie/wyszukiwaniu — ten sam mechanizm co w LiveScreen.kt,
    // ale WYŁĄCZNIE dla wyświetlania: dane programu (programsByChannel niżej) pobiera się
    // dalej dla WSZYSTKICH gridChannels naraz, niezależnie od filtra, żeby wpisywanie w
    // polu wyszukiwania nie wymuszało ponownego pobierania EPG za każdym znakiem
    // (zgłoszenie: brakowało grup kanałów i wyszukiwania w pełnym gridzie — przy setkach
    // kanałów z EPG przewijanie WSZYSTKICH naraz, bez możliwości zawężenia, było
    // niepraktyczne).
    var epgQuery by remember { mutableStateOf("") }
    var epgSelectedGroup by remember { mutableStateOf<String?>(null) }
    val epgGroups = remember(gridChannels) { gridChannels.mapNotNull { it.group }.distinct().sorted() }
    val filteredGridChannels = remember(gridChannels, epgQuery, epgSelectedGroup) {
        gridChannels.filter { ch ->
            (epgQuery.isBlank() || ch.name.containsFolded(epgQuery)) &&
                (epgSelectedGroup == null || ch.group == epgSelectedGroup)
        }
    }

    var selectedChannel by remember { mutableStateOf(gridChannels.firstOrNull()) }

    // WAŻNE dla wydajności: horizontalOffsetPx trzymany jako NAZWANY obiekt MutableState
    // (nie ukryty za `by`), żeby dało się przekazać SAM OBIEKT stanu w dół do
    // EpgTimeAxisHeader/EpgChannelRow, a te odczytują .value DOPIERO wewnątrz własnego
    // Modifier.offset { } (patrz tam). Bez tego — czyli przekazując gotową wartość Float
    // jako zwykły parametr — KAŻDA zmiana offsetu (a podczas przeciągania to dziesiątki
    // razy na sekundę) wymuszałaby pełną REKOMPOZYCJĘ nagłówka osi czasu i każdego
    // widocznego wiersza kanału (razem z przeliczeniem wszystkich bloków programów),
    // zamiast taniego przeliczenia samego układu (layout) — realnie odczuwalne szarpanie
    // przy przeciąganiu siatki z wieloma kanałami/programami.
    val horizontalOffsetState = remember { mutableStateOf(0f) }
    var horizontalOffsetPx by horizontalOffsetState

    // --- Podgląd wideo: OSOBNA instancja odtwarzacza, żyjąca tylko na czas tego ekranu —
    // NIE jest to ten sam odtwarzacz co PlayerActivity (osobna Activity, osobny cykl
    // życia). Zwalniana przy opuszczeniu ekranu (DisposableEffect), żeby nie zostawić
    // żywej sesji dekodera w tle — dokładnie ten sam problem, który wcześniej dawał
    // czarny ekran przy odtwarzaniu nowego materiału (patrz PlayerActivity.onStop). ---
    val previewPlayerManager = remember { VlcPlayerManager(context) }
    DisposableEffect(Unit) {
        onDispose { previewPlayerManager.release() }
    }
    var previewViewAttached by remember { mutableStateOf(false) }
    LaunchedEffect(selectedChannel?.id, previewViewAttached) {
        val ch = selectedChannel
        if (ch != null && previewViewAttached) {
            val playlist = playlists.find { it.id == ch.playlistId }
            previewPlayerManager.play(ch.streamUrl, playlist?.userAgent, playlist?.referer)
        }
    }

    // --- Program dnia dla WSZYSTKICH widocznych kanałów naraz, pogrupowane po epgUrl
    // (różne playlisty mogą mieć różne źródła EPG) — każdy plik XMLTV parsuje się i tak
    // tylko raz na URL (patrz cache w EpgRepository), więc to tanie nawet dla wielu kanałów. ---
    var programsByChannel by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    var isLoadingEpg by remember { mutableStateOf(true) }
    LaunchedEffect(gridChannels) {
        isLoadingEpg = true
        val merged = mutableMapOf<String, List<EpgProgram>>()
        withContext(Dispatchers.IO) {
            gridChannels.groupBy { epgUrlFor(it) }.forEach { (url, chs) ->
                if (url != null) {
                    val ids = chs.mapNotNull { it.epgChannelId }
                    runCatching { epgRepository.getTodayProgramsForChannels(url, ids) }
                        .getOrNull()
                        ?.let { merged.putAll(it) }
                }
            }
        }
        programsByChannel = merged
        isLoadingEpg = false
    }

    // --- Oś czasu: cały dzisiejszy dzień kalendarzowy, tak samo jak EpgGuideDialog. ---
    val dayStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val hourWidthPx = with(density) { HOUR_WIDTH_DP.dp.toPx() }
    val totalWidthPx = 24 * hourWidthPx

    fun xForTime(millis: Long): Float = ((millis - dayStartMillis) / 3_600_000f) * hourWidthPx

    var viewportWidthPx by remember { mutableStateOf(0f) }

    fun clampOffset(value: Float): Float {
        val minOffset = -(totalWidthPx - viewportWidthPx).coerceAtLeast(0f)
        return value.coerceIn(minOffset, 0f)
    }

    // Auto-doscrolowanie do "teraz" raz, gdy poznamy szerokość ekranu — bieżący czas ląduje
    // blisko lewej krawędzi widocznego obszaru (15% wcięcia), żeby widać było zarówno to,
    // co leci teraz, jak i kawałek tego, co będzie za chwilę.
    var didAutoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(viewportWidthPx) {
        if (viewportWidthPx > 0f && !didAutoScroll) {
            val nowX = xForTime(System.currentTimeMillis())
            horizontalOffsetPx = clampOffset(-(nowX - viewportWidthPx * 0.15f))
            didAutoScroll = true
        }
    }

    val hourStep = hourWidthPx // krok przewijania strzałkami lewo/prawo na pilocie = 1h

    // Obsługa pilota: góra/dół zmienia wybrany (podglądany) kanał, lewo/prawo przewija oś
    // czasu o 1h. Własna obsługa (nie domyślne przechodzenie fokusem Compose) jest tu
    // konieczna, bo siatka NIE używa standardowego LazyRow do przewijania w poziomie
    // (patrz komentarz przy Modifier.offset niżej) — Compose nie wie samo z siebie, jak
    // "dowieźć fokus" do elementu poza aktualnie widocznym, przesuniętym oknem.
    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocusRequester.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        val list = filteredGridChannels
                        val idx = list.indexOfFirst { it.id == selectedChannel?.id }.coerceAtLeast(0)
                        val nextIdx = if (keyEvent.key == Key.DirectionDown) idx + 1 else idx - 1
                        list.getOrNull(nextIdx)?.let { selectedChannel = it }
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val delta = if (keyEvent.key == Key.DirectionRight) hourStep else -hourStep
                        horizontalOffsetPx = clampOffset(horizontalOffsetPx + delta)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        selectedChannel?.let(onWatchFullscreen)
                        true
                    }
                    else -> false
                }
            },
    ) {
        EpgPreviewHeader(
            channel = selectedChannel,
            currentProgram = selectedChannel?.epgChannelId?.let { chId ->
                programsByChannel[chId]?.find { System.currentTimeMillis() in it.startMillis..it.endMillis }
            },
            playerManager = previewPlayerManager,
            onViewAttached = { previewViewAttached = true },
            onBack = onBack,
            onWatchFullscreen = { selectedChannel?.let(onWatchFullscreen) },
        )

        // Wyszukiwanie + chipy grup — patrz obszerny komentarz przy epgQuery/epgSelectedGroup
        // wyżej. Ten sam wizualny wzorzec co w LiveScreen.kt, ale własna, lokalna kopia pola
        // wyszukiwania (tamto jest prywatne dla LiveScreen.kt, nie do współdzielenia wprost).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = epgQuery,
                onValueChange = { epgQuery = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                decorationBox = { inner ->
                    if (epgQuery.isEmpty()) Text(tr("epgGrid.searchPlaceholder"), color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    inner()
                },
                modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand(),
            )
        }
        if (epgGroups.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item {
                    FilterChipCompat(label = tr("live.filter.all", gridChannels.size), active = epgSelectedGroup == null, onClick = { epgSelectedGroup = null })
                }
                items(epgGroups) { g ->
                    FilterChipCompat(label = g, active = epgSelectedGroup == g, onClick = { epgSelectedGroup = g })
                }
            }
        }

        if (isLoadingEpg) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (gridChannels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(tr("epgGrid.noChannels"), color = TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else if (filteredGridChannels.isEmpty()) {
            // Kanały z EPG istnieją, ale wyszukiwanie/filtr grupy akurat nic nie znalazły —
            // ODRÓŻNIONE od "gridChannels.isEmpty()" wyżej (tam appka w ogóle nie ma kanałów
            // z EPG), żeby komunikat trafnie opisywał sytuację.
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(tr("epgGrid.noSearchResults"), color = TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportWidthPx = (it.width - with(density) { CHANNEL_COLUMN_WIDTH_DP.dp.toPx() }) }
                    .pointerInputHorizontalDrag { deltaPx ->
                        horizontalOffsetPx = clampOffset(horizontalOffsetPx + deltaPx)
                    },
            ) {
                Column(Modifier.fillMaxSize()) {
                    EpgTimeAxisHeader(dayStartMillis = dayStartMillis, hourWidthPx = hourWidthPx, offsetState = horizontalOffsetState)
                    LazyColumn {
                        items(filteredGridChannels, key = { it.id }) { channel ->
                            EpgChannelRow(
                                channel = channel,
                                programs = channel.epgChannelId?.let { programsByChannel[it] }.orEmpty(),
                                dayStartMillis = dayStartMillis,
                                hourWidthPx = hourWidthPx,
                                offsetState = horizontalOffsetState,
                                isSelected = channel.id == selectedChannel?.id,
                                onSelect = { selectedChannel = channel },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Prosty, samodzielny detektor przeciągnięcia POZIOMEGO (bez pionowego) — celowo osobny
 * od wbudowanego przewijania LazyColumn (pionowe przeciągnięcie ma trafiać normalnie do
 * LazyColumn pod spodem, tylko poziome ma być przechwycone tutaj, do zsynchronizowanego
 * przewijania osi czasu). detectHorizontalDragGestures samo różnicuje kierunek progiem
 * dotyku, więc oba gesty (pionowy do LazyColumn, poziomy tutaj) współistnieją bez kolizji —
 * ten sam wzorzec co współistniejące detectTapGestures/detectDragGestures w PlayerActivity. */
private fun Modifier.pointerInputHorizontalDrag(onDrag: (Float) -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures { change, dragAmount ->
            change.consume()
            onDrag(dragAmount)
        }
    },
)

@Composable
private fun EpgPreviewHeader(
    channel: Channel?,
    currentProgram: EpgProgram?,
    playerManager: VlcPlayerManager,
    onViewAttached: () -> Unit,
    onBack: () -> Unit,
    onWatchFullscreen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { layout ->
                        playerManager.attachToView(layout)
                        onViewAttached()
                    }
                },
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).tvFocusFrame(shape = CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = tr("common.back"), tint = Color.White)
            }
            if (channel != null) {
                IconButton(
                    onClick = onWatchFullscreen,
                    modifier = Modifier.align(Alignment.TopEnd).tvFocusFrame(shape = CircleShape),
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = tr("epgGrid.watchFullscreen"), tint = Color.White)
                }
            }
        }
        if (channel != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(channel.name, color = TextMain, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentProgram != null) {
                    Text(
                        currentProgram.title,
                        color = TextDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgTimeAxisHeader(dayStartMillis: Long, hourWidthPx: Float, offsetState: androidx.compose.runtime.State<Float>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(start = CHANNEL_COLUMN_WIDTH_DP.dp)
            .clipToBounds(),
    ) {
        Row(
            modifier = Modifier
                // .value odczytane TU, wewnątrz lambdy offsetu — dopiero to daje efekt
                // "tylko re-layout, bez rekompozycji" przy każdej zmianie przesunięcia
                // (patrz obszerny komentarz przy horizontalOffsetState w EpgGridScreen).
                .offset { androidx.compose.ui.unit.IntOffset(offsetState.value.toInt(), 0) }
                .width(with(LocalDensity.current) { (24 * hourWidthPx).toDp() }),
        ) {
            repeat(24) { hour ->
                Box(Modifier.width(with(LocalDensity.current) { hourWidthPx.toDp() })) {
                    Text(
                        timeFormat.format(Date(dayStartMillis + hour * 3_600_000L)),
                        color = TextDim,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        // Pionowa linia "teraz" — liczona TĄ SAMĄ metodą co w każdym wierszu kanału niżej,
        // więc mimo że to osobne Composable, wizualnie tworzą jedną, ciągłą linię. Poza
        // widocznym obszarem po prostu zostaje przycięta przez clipToBounds() na
        // rodzicu — nie trzeba osobno sprawdzać, czy mieści się w oknie. Cała kalkulacja
        // celowo WEWNĄTRZ lambdy offsetu (nie jako `val nowX = ...` w ciele funkcji) —
        // z tego samego powodu co offsetState.value wyżej.
        Box(
            Modifier
                .offset {
                    val nowX = ((System.currentTimeMillis() - dayStartMillis) / 3_600_000f) * hourWidthPx + offsetState.value
                    androidx.compose.ui.unit.IntOffset(nowX.toInt(), 0)
                }
                .width(2.dp)
                .fillMaxHeight()
                .background(Accent2),
        )
    }
}

@Composable
private fun EpgChannelRow(
    channel: Channel,
    programs: List<EpgProgram>,
    dayStartMillis: Long,
    hourWidthPx: Float,
    offsetState: androidx.compose.runtime.State<Float>,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp)
            .background(if (isSelected) Surface2 else Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .width(CHANNEL_COLUMN_WIDTH_DP.dp)
                .fillMaxHeight()
                .tvFocusFrame(shape = RoundedCornerShape(6.dp))
                .clickable(onClick = onSelect)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                channel.name,
                color = if (isSelected) Accent else TextMain,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .clipToBounds(),
        ) {
            if (programs.isEmpty()) {
                // Wyraźne rozróżnienie "brak danych EPG dla tego kanału" od "ekran się
                // zawiesił" — bez tego pusty pasek wyglądał identycznie w obu przypadkach,
                // co przy kanałach bez pasującego tvg-id w źródle XMLTV sprawiało wrażenie,
                // że appka przestała działać po przewinięciu w dół.
                Text(
                    tr("epgGrid.noDataForChannel"),
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(offsetState.value.toInt(), 0) }
                    .fillMaxHeight()
                    .width(with(density) { (24 * hourWidthPx).toDp() }),
            ) {
                programs.forEach { program ->
                    // safeProgramX/WidthPx — DODATKOWA, bardziej rygorystyczna warstwa
                    // zabezpieczeń NAD istniejącym coerceIn niżej. Zgłoszony crash
                    // ("Can't represent a size... in Constraints") wystąpił MIMO
                    // coerceIn na widthPx — możliwe wytłumaczenie: skrajnie uszkodzone
                    // dane z XMLTV (np. rok z literówką u dostawcy, poprawnie sparsowany
                    // przez SimpleDateFormat, ale semantycznie bezsensowny) mogły
                    // wygenerować NaN/nieskończoność PRZED coerceIn — Float.NaN nie jest
                    // ani mniejszy, ani większy od żadnej granicy, więc coerceIn na NaN
                    // MOŻE zwrócić NaN niezmienione, zamiast go przyciąć. Jawne
                    // sprawdzenie isFinite() eliminuje tę lukę całkowicie, niezależnie od
                    // przyczyny złych danych u źródła.
                    val rawStartX = ((program.startMillis - dayStartMillis) / 3_600_000f) * hourWidthPx
                    val startX = if (rawStartX.isFinite()) rawStartX.coerceIn(-24 * hourWidthPx, 24 * hourWidthPx) else 0f
                    val rawWidthPx = ((program.endMillis - program.startMillis) / 3_600_000f) * hourWidthPx
                    val widthPx = if (rawWidthPx.isFinite()) {
                        rawWidthPx.coerceIn(with(density) { MIN_PROGRAM_WIDTH_DP.dp.toPx() }, 24 * hourWidthPx)
                    } else {
                        with(density) { MIN_PROGRAM_WIDTH_DP.dp.toPx() }
                    }
                    val isNow = System.currentTimeMillis() in program.startMillis..program.endMillis
                    Box(
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(startX.toInt(), 0) }
                            .width(with(density) { widthPx.toDp() })
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isNow) Accent.copy(alpha = 0.25f) else Surface)
                            .clickable(onClick = onSelect)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            program.title,
                            color = if (isNow) Accent else TextDim,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
