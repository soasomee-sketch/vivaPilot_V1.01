package com.iptvplayer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.documentfile.provider.DocumentFile
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.Episode
import com.iptvplayer.app.data.LocalVideoFile
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.util.isTvDevice
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.PlaylistStatus
import com.iptvplayer.app.data.PlaylistType
import com.iptvplayer.app.data.SeriesInfo
import com.iptvplayer.app.data.WatchProgress
import com.iptvplayer.app.data.XtreamClient
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.player.NavQueue
import com.iptvplayer.app.player.PlayerActivity
import com.iptvplayer.app.player.QueueEntry
import com.iptvplayer.app.player.QueueKind
import com.iptvplayer.app.player.SeriesSubQueue
import com.iptvplayer.app.ui.AppViewModel
import com.iptvplayer.app.ui.nav.IptvBottomNav
import com.iptvplayer.app.ui.nav.Tab
import com.iptvplayer.app.ui.screens.*
import com.iptvplayer.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // --- Backup/restore: SAF pickery muszą być zarejestrowane przed STARTED, stąd jako
    // pola aktywności (nie da się ich wywołać z wnętrza Composable). ---
    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeBackupToUri(it) }
        }
    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readBackupFromUri(it) }
        }

    // Wybór lokalnego folderu (zakładka Folder) — musi być zarejestrowany tu, z tego samego
    // powodu co pickery kopii zapasowej wyżej (SAF pickery nie da się wywołać z Composable).
    private val openFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { addLocalFolderFromUri(it) }
        }

    // "Zapisz potwierdzenie" na ekranie potwierdzenia zakupu (sekcja 6, patrz
    // PurchaseConfirmationScreen.kt) — ten sam wzorzec SAF co backup wyżej.
    private val savePurchaseConfirmationLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { writePurchaseConfirmationToUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blokada orientacji na PIONOWĄ, ale TYLKO na telefonie/tablecie — telewizor jest
        // fizycznie poziomy, więc blokowanie by go zepsuło (dlatego warunek isTvDevice(),
        // nie sztywny wpis w manifeście, który obowiązywałby wszędzie naraz). Zgłoszenie:
        // appka w trybie poziomym na telefonie jest nieczytelna (żaden ekran poza
        // odtwarzaczem nie ma dedykowanego układu na szeroki, niski ekran) — PlayerActivity
        // (osobna Activity, sam moment oglądania) ma WŁASNĄ, już wcześniej przemyślaną
        // obsługę orientacji i nie jest tym dotknięty.
        if (!isTvDevice()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Jednakowe zachowanie na każdej wersji Androida (na 15+ i tak wymuszone systemowo):
        // appka rysuje pod paskami systemowymi, a odstępy dodaje Box w setContent niżej.
        // Ciemne ikony paska stanu byłyby niewidoczne na ciemnym tle appki — stąd jawnie
        // styl "dark" (jasne ikony) niezależnie od motywu systemu.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            IptvPlayerTheme {
                // Język czytany TUTAJ (a nie w środku RootScreen) — CompositionLocalProvider
                // jest wprawdzie `inline`, ale jego lambda `content` jest oznaczona @Composable,
                // a Compose przepisuje takie lambdy pod mechanizm rekompozycji, co blokuje w
                // nich zwykłe `return` (błąd kompilacji "'return' is not allowed here"). RootScreen
                // ma sporo wczesnych `return` (zamykanie ekranów-nakładek) — muszą zostać
                // zwykłymi returnami Z FUNKCJI RootScreen, więc CompositionLocalProvider musi
                // obejmować sam WYWOŁANIE RootScreen(...), a nie jej wnętrze. CompositionLocal
                // i tak przenika całe poddrzewo kompozycji niezależnie od granic funkcji, więc
                // to nic nie zmienia w działaniu tr()/LocalLang.current w środku RootScreen.
                val state by viewModel.state.collectAsState()
                // Do PurchaseConfirmationScreen niżej (orderId/data zakupu) — osobny StateFlow
                // od `state` wyżej, patrz AppViewModel.billingState.
                val billingState by viewModel.billingState.collectAsState()
                // Ostrzeżenie przed eksportem kopii zapasowej (sekcja 8) — plik kopii zawiera
                // hasła źródeł w czystym tekście. Pokazywane PRZY KAŻDYM eksporcie (nie tylko
                // pierwszym, w odróżnieniu od ostrzeżenia Cast), bo stawka (ujawnienie hasła)
                // jest wyższa, a akcja rzadka — patrz showBackupExportWarning niżej.
                var showBackupExportWarning by remember { mutableStateOf(false) }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.iptvplayer.app.i18n.LocalLang provides state.language,
                ) {
                    // Android 15/16 (targetSdk 35+) rysuje appkę pod paskiem stanu i paskiem
                    // nawigacji ("edge-to-edge", bez możliwości wyłączenia od Androida 16) —
                    // bez tych odstępów górny pasek appki chowałby się pod zegarem/baterią
                    // telefonu, a dolna nawigacja pod systemowymi przyciskami. Na TV te
                    // odstępy wynoszą 0, więc nic się tam nie zmienia.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Bg)
                            .systemBarsPadding()
                            .imePadding(),
                    ) {
                    RootScreen(
                        viewModel = viewModel,
                        onPlayChannel = { launchPlayer(it) },
                        onPlayChannelFromStart = { launchPlayer(it, forceStartFromZero = true) },
                        onDownloadChannel = ::downloadChannel,
                        onDownloadProgress = ::downloadWatchProgress,
                        onDownloadEpisode = ::downloadEpisode,
                        onDownloadSeriesSeason = ::downloadSeriesSeason,
                        onPlayEpisode = { series, episode -> launchEpisodePlayer(series, episode) },
                        onPlayEpisodeFromStart = { series, episode -> launchEpisodePlayer(series, episode, forceStartFromZero = true) },
                        onResumeProgress = ::resumeWatchProgress,
                        onExportBackup = { showBackupExportWarning = true },
                        onImportBackup = ::startBackupImport,
                        onAddLocalFolder = ::startAddLocalFolder,
                        onPlayLocalVideo = ::playLocalVideo,
                        onRetryDownload = ::retryDownload,
                        onPlayDownloadedFile = ::playDownloadedFile,
                        onWatchCatchup = ::playChannelCatchup,
                    )
                    // Dialog "kup pełną wersję" — globalny, niezależny od tego, jaki ekran
                    // jest akurat widoczny, bo funkcje, które go wyzwalają (wyszukiwanie,
                    // Moje listy, pobieranie) są rozsiane po wielu różnych ekranach. Patrz
                    // AppViewModel.requestPremiumAccess po pełny mechanizm.
                    if (state.showPurchasePrompt) {
                        PurchasePromptDialog(
                            onBuy = { viewModel.openPrePurchaseScreen() },
                            onDismiss = { viewModel.dismissPurchasePrompt() },
                        )
                    }
                    // Ekran "przed zakupem" (sekcja 4) — nakładka PEŁNOEKRANOWA (nie dialog,
                    // tak jak PurchasePromptDialog wyżej), bo zawiera dwa checkboxy i małą
                    // czcionkę z linkiem do Regulaminu, które nie zmieściłyby się wygodnie w
                    // AlertDialog. JEDYNA droga do launchPurchaseFlow w całej appce — patrz
                    // AppViewModel.confirmPrePurchaseAndLaunchBilling.
                    if (state.showPrePurchaseScreen) {
                        BackHandler { viewModel.dismissPrePurchaseScreen() }
                        PrePurchaseScreen(
                            onProceed = { check1At, check2At -> viewModel.confirmPrePurchaseAndLaunchBilling(this@MainActivity, check1At, check2At) },
                            onCancel = { viewModel.dismissPrePurchaseScreen() },
                        )
                    }
                    // Ekran potwierdzenia zakupu (sekcja 6) — pokazuje się automatycznie od
                    // razu po udanym zakupie (patrz AppViewModel.init) i na żądanie z Ustawień
                    // (patrz openPurchaseConfirmationScreen).
                    if (state.showPurchaseConfirmationScreen) {
                        BackHandler { viewModel.dismissPurchaseConfirmationScreen() }
                        PurchaseConfirmationScreen(
                            billingState = billingState,
                            onSaveConfirmation = { startSavePurchaseConfirmation() },
                            onDone = { viewModel.dismissPurchaseConfirmationScreen() },
                        )
                    }
                    // Ostrzeżenie przed eksportem kopii zapasowej (sekcja 8) — patrz
                    // showBackupExportWarning wyżej. Dopiero po potwierdzeniu wywołujemy
                    // prawdziwy startBackupExport() (otwiera SAF "Zapisz jako").
                    if (showBackupExportWarning) {
                        AlertDialog(
                            onDismissRequest = { showBackupExportWarning = false },
                            title = { Text(tr("settings.backup.export")) },
                            text = { Text(tr("backupWarning.body")) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showBackupExportWarning = false
                                    startBackupExport()
                                }) { Text(tr("settings.backup.export")) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showBackupExportWarning = false }) {
                                    Text(tr("common.cancel"))
                                }
                            },
                        )
                    }
                    // MODUŁ TESTOWY (TYMCZASOWY) — w wersji debug: pomarańczowy znacznik "TEST" i
                    // panel (przyspieszanie czasu, symulacja zakupu); w release to pusta funkcja
                    // (app/src/release/.../testtools). Do usunięcia po zakończeniu testów.
                    com.iptvplayer.app.testtools.TestToolsHost(viewModel)
                    }
                }
            }
        }
        if (savedInstanceState == null) handleResumeIntent(intent)
    }

    private fun startBackupExport() {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        exportBackupLauncher.launch("iptv_backup_$stamp.json")
    }

    private fun startBackupImport() {
        importBackupLauncher.launch(arrayOf("application/json"))
    }

    private fun startAddLocalFolder() {
        openFolderLauncher.launch(null)
    }

    private fun startSavePurchaseConfirmation() {
        savePurchaseConfirmationLauncher.launch("VivaPilot_potwierdzenie_zakupu.txt")
    }

    /** Po wybraniu folderu w systemowym pickerze: najpierw trzeba PRZED zapisaniem czegokolwiek
     * przyznać sobie trwałe uprawnienie odczytu (inaczej dostęp zniknąłby po restarcie appki),
     * dopiero potem rejestrujemy folder w AppViewModel, które od razu go zeskanuje. */
    private fun addLocalFolderFromUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Toast.makeText(this, trFor(viewModel.state.value.language, "root.backup.folderAccessError", e.message ?: ""), Toast.LENGTH_LONG).show()
            return
        }
        val name = DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment ?: "Folder"
        viewModel.addLocalFolder(uri.toString(), name)
    }

    /** Odtwarzanie pliku z podłączonego folderu — kolejka next/prev to pozostałe pliki
     * z TEGO SAMEGO folderu, w tej samej kolejności co na liście (alfabetycznie, patrz
     * LocalFolderScanner.scanFolder), tak samo jak LIVE nawiguje po kanałach w grupie. */
    private fun playLocalVideo(video: LocalVideoFile, folderVideos: List<LocalVideoFile>) {
        val entries = folderVideos.map { queueEntryFromLocalVideo(it) }
        val idx = folderVideos.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        playQueue(NavQueue(QueueKind.LOCAL_FOLDER, entries, idx))
    }

    private fun queueEntryFromLocalVideo(video: LocalVideoFile): QueueEntry = QueueEntry(
        id = video.id,
        // MediaKind.MOVIE: lokalny plik zachowuje się jak zwykły VOD (przewijalny, ma
        // wznawianie postępu) — jedyna różnica to brak przycisku Pobierz, obsłużona osobno
        // w PlayerActivity po samym prefiksie "content://" w streamUrl.
        kind = MediaKind.MOVIE,
        title = video.title,
        streamUrl = video.uri,
        // Miniatura wyciągnięta z pliku (patrz LocalVideoThumbnailer) — jeśli jeszcze się
        // nie doładowała w tle, po prostu null, tak jak dotychczas (bez żadnego wyjątku).
        posterUrl = video.thumbnailPath,
        userAgent = null,
        referer = null,
    )

    /** Odtwarza JUŻ POBRANY plik (ekran "Pobrane") — DownloadManager zwraca dla ukończonych
     * pobrań URI w formie "content://…", więc trafia w TĘ SAMĄ gałąź obsługi co pliki z
     * zakładki Folder (patrz VlcPlayerManager.play), bez żadnego dodatkowego kodu. */
    private fun playDownloadedFile(uri: Uri, title: String, posterUrl: String?) {
        val entry = QueueEntry(
            id = uri.toString(),
            kind = MediaKind.MOVIE,
            title = title,
            streamUrl = uri.toString(),
            posterUrl = posterUrl,
            userAgent = null,
            referer = null,
        )
        playQueue(NavQueue(QueueKind.LOCAL_FOLDER, listOf(entry), 0))
    }

    /** Ponawia nieudane pobranie (ekran "Pobrane") — DownloadManager nie pozwala "wznowić"
     * starego zlecenia z nowym ID, więc po prostu zlecamy je jeszcze raz od zera, tym samym
     * kodem co pierwsze pobranie (co przy okazji doda nowy rekord metadanych) — stary
     * rekord kasujemy osobno (patrz DownloadsScreen.onRetry -> viewModel.removeDownload). */
    private fun retryDownload(record: com.iptvplayer.app.data.DownloadRecord) {
        viewModel.removeDownload(record.managerId)
        com.iptvplayer.app.data.DownloadHelper.downloadItem(
            context = this,
            streamUrl = record.streamUrl,
            title = record.title,
            userAgent = record.userAgent,
            referer = record.referer,
            lang = viewModel.state.value.language,
            posterUrl = record.posterUrl,
        )
        viewModel.refreshDownloads()
    }

    /** "Cofnij" — odtwarza WYBRANY PROGRAM od jego początku, zamiast normalnego strumienia
     * live danego kanału. Wymaga panelu Xtream Codes z włączonym catch-up (patrz
     * Channel.hasCatchup/catchupDays, wypełniane z pól "tv_archive"/"tv_archive_duration"),
     * M3U nie ma tu zastosowania (brak takiego pojęcia w tym formacie). */
    private fun playChannelCatchup(channel: Channel, startMillis: Long, durationMinutes: Int) {
        // Catch-up (cofanie się w czasie/archiwum) jest ZAWSZE wolne, tak jak reszta
        // oglądania/EPG — patrz TrialGate.kt: po próbie blokujemy tylko wyszukiwanie, Moje
        // listy i pobieranie.
        val playlist = viewModel.state.value.playlists.find { it.id == channel.playlistId } ?: return
        val streamId = channel.xtreamLiveStreamId ?: return
        val client = com.iptvplayer.app.data.XtreamClient(playlist)
        val catchupUrl = client.buildCatchupUrl(streamId, startMillis, durationMinutes)
        val entry = QueueEntry(
            id = "${channel.id}_catchup_$startMillis",
            kind = MediaKind.LIVE,
            title = channel.name,
            streamUrl = catchupUrl,
            posterUrl = channel.logoUrl,
            userAgent = playlist.userAgent,
            referer = playlist.referer,
        )
        playQueue(NavQueue(QueueKind.LIVE_GROUP, listOf(entry), 0))
    }

    private fun writeBackupToUri(uri: Uri) {
        val lang = viewModel.state.value.language
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(viewModel.exportBackupJson().toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream zwrócił null")
            Toast.makeText(this, trFor(lang, "root.backup.exportSuccess"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, trFor(lang, "root.backup.exportError", e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /** Zapisuje treść potwierdzenia zakupu (patrz buildPurchaseConfirmationFileContent w
     * PurchaseConfirmationScreen.kt) pod wybranym przez Użytkownika adresem SAF — sekcja 6:
     * "Zapisz potwierdzenie" zapisuje plik tekstowy z treścią potwierdzenia ORAZ pełnym
     * tekstem Regulaminu. */
    private fun writePurchaseConfirmationToUri(uri: Uri) {
        val lang = viewModel.state.value.language
        try {
            val content = buildPurchaseConfirmationFileContent(this, lang)
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream zwrócił null")
            Toast.makeText(this, trFor(lang, "purchaseConfirmation.saveSuccess"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, trFor(lang, "purchaseConfirmation.saveError", e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackupFromUri(uri: Uri) {
        val lang = viewModel.state.value.language
        try {
            val json = contentResolver.openInputStream(uri)
                ?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                ?: throw IllegalStateException("openInputStream zwrócił null")
            val ok = viewModel.importBackupJson(json)
            val message = trFor(lang, if (ok) "root.backup.importSuccess" else "root.backup.importFailed")
            Toast.makeText(this, message, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, trFor(lang, "root.backup.importError", e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Uruchamia odtwarzacz z gotową kolejką nawigacji (patrz NavQueue) — jeden wspólny punkt
     * wejścia dla wszystkich ścieżek odtwarzania (kanał, epizod, wznowienie), żeby zasada
     * "co jest następne/poprzednie" była liczona w jednym miejscu (patrz build*Queue niżej).
     */
    private fun playQueue(queue: NavQueue, forceStartFromZero: Boolean = false) {
        if (queue.items.isEmpty()) return
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_QUEUE, queue)
            if (forceStartFromZero) putExtra(PlayerActivity.EXTRA_FORCE_START_FROM_ZERO, true)
        }
        startActivity(intent)
    }

    private fun queueEntryFromChannel(channel: Channel): QueueEntry {
        val playlist = viewModel.state.value.playlists.find { it.id == channel.playlistId }
        return QueueEntry(
            id = channel.id,
            kind = channel.kind,
            title = channel.name,
            streamUrl = channel.streamUrl,
            posterUrl = channel.logoUrl,
            userAgent = playlist?.userAgent,
            referer = playlist?.referer,
            // Tylko dla LIVE ma to w ogóle sens (przewodnik programowy w odtwarzaczu) —
            // dla MOVIE/SERIES zostaje null, przycisk EPG się wtedy nie pokaże.
            epgChannelId = channel.epgChannelId.takeIf { channel.kind == MediaKind.LIVE },
            epgUrl = playlist?.epgUrl.takeIf { channel.kind == MediaKind.LIVE },
        )
    }

    /** Zwraca poprawną nazwę odcinka — "NazwaSerialu - SxxExx - TytułOdcinka" ALBO sam
     * episode.title, jeśli ten JUŻ zaczyna się od nazwy serialu (część dostawców zwraca
     * tytuł odcinka z doklejoną nazwą serialu na starcie — dawne sztywne doklejanie
     * powielało wtedy i nazwę serialu, i numer SxxExx). Wspólne dla paska plakatów w
     * odtwarzaczu (queueEntryFromEpisode) i nazw plików pobieranych odcinków
     * (downloadEpisode/downloadSeriesSeason) — ten sam defekt, jedna naprawa zamiast trzech
     * osobnych kopii tej samej logiki, które mogłyby z czasem się rozjechać. */
    private fun episodeDisplayTitle(series: SeriesInfo, episode: Episode, seasonNumber: Int?): String {
        if (episode.title.startsWith(series.name)) return episode.title
        val seasonLabel = seasonNumber?.toString()?.padStart(2, '0') ?: "??"
        return "${series.name} - S${seasonLabel}E${episode.episodeNumber.toString().padStart(2, '0')} - ${episode.title}"
    }

    private fun queueEntryFromEpisode(series: SeriesInfo, episode: Episode, seasonNumber: Int? = null): QueueEntry {
        val playlist = viewModel.state.value.playlists.find { it.id == series.playlistId }
        return QueueEntry(
            id = episode.id,
            kind = MediaKind.SERIES,
            title = episodeDisplayTitle(series, episode, seasonNumber),
            streamUrl = episode.streamUrl,
            posterUrl = series.posterUrl,
            userAgent = playlist?.userAgent,
            referer = playlist?.referer,
            seasonNumber = seasonNumber,
            seriesId = series.id,
        )
    }

    private fun flattenEpisodes(series: SeriesInfo): List<Pair<Int, Episode>> =
        series.seasons.sortedBy { it.number }.flatMap { s -> s.episodes.sortedBy { it.episodeNumber }.map { s.number to it } }

    /** LIVE bez kontekstu listy: następny/poprzedni = kolejny kanał w tej samej grupie
     * (kolejność zgodna z tym, co użytkownik widzi na ekranie Live). */
    private fun buildLiveGroupQueue(channel: Channel): NavQueue {
        val sameGroup = viewModel.state.value.liveChannels.filter { it.group == channel.group }
        val entries = sameGroup.map(::queueEntryFromChannel)
        val idx = sameGroup.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        return NavQueue(QueueKind.LIVE_GROUP, entries, idx)
    }

    /** MOVIE bez kontekstu listy: następny = kolejny "podobny film" (ta sama heurystyka co
     * sekcja "Podobne filmy" na ekranie szczegółów), bieżący film jako pozycja startowa. */
    private fun buildSimilarMoviesQueue(movie: Channel): NavQueue {
        val entries = mutableListOf(queueEntryFromChannel(movie))
        // similarMoviesFor liczy podobieństwo na miejscu (patrz komentarz przy tej metodzie
        // w AppViewModel) — działa identycznie, czy film odtworzono ze szczegółów, czy
        // szybkim przyciskiem ▶ prosto z plakatu, bez wcześniejszego wejścia w szczegóły.
        entries += viewModel.similarMoviesFor(movie).map(::queueEntryFromChannel)
        return NavQueue(QueueKind.SIMILAR_MOVIES, entries, 0)
    }

    /** SERIES: zawsze następny/poprzedni odcinek (wyjątek — ignoruje kontekst listy nawet
     * jeśli serial został odtworzony z poziomu jakiejś niestandardowej listy). */
    private fun buildSeriesEpisodesQueue(series: SeriesInfo, currentEpisode: Episode): NavQueue {
        val flat = flattenEpisodes(series)
        val entries = flat.map { (seasonNum, ep) -> queueEntryFromEpisode(series, ep, seasonNum) }
        val idx = flat.indexOfFirst { it.second.id == currentEpisode.id }.coerceAtLeast(0)
        return NavQueue(QueueKind.SERIES_EPISODES, entries, idx)
    }

    /**
     * LIVE/MOVIE odtworzone z wnętrza niestandardowej listy: następny/poprzedni materiał
     * bierzemy z kolejności tej listy. Pozycje-seriale w liście dostają własny podciąg
     * odcinków (seriesSubQueues) — jeśli trzeba, dociągamy go tutaj (get_series_info), żeby
     * PlayerActivity nie musiała już nic doczytywać z sieci przy nawigacji.
     */
    private suspend fun buildListQueue(list: com.iptvplayer.app.data.CustomList, currentItemId: String): NavQueue {
        val state = viewModel.state.value
        val entries = mutableListOf<QueueEntry>()
        val subQueues = mutableMapOf<String, SeriesSubQueue>()
        for (ref in list.items.sortedBy { it.addedAt }) {
            when (ref.kind) {
                MediaKind.LIVE, MediaKind.MOVIE -> {
                    val pool = if (ref.kind == MediaKind.LIVE) state.liveChannels else state.movies
                    val channel = pool.find { it.id == ref.id } ?: continue
                    entries += queueEntryFromChannel(channel)
                }
                MediaKind.SERIES -> {
                    var series = state.series.find { it.id == ref.id } ?: continue
                    if (series.seasons.isEmpty()) {
                        val source = state.playlists.find { it.id == series.playlistId }
                        if (source != null && source.type == PlaylistType.XTREAM) {
                            series = try {
                                val (seasons, _) = withContext(Dispatchers.IO) { XtreamClient(source).fetchSeriesEpisodes(series.id) }
                                series.copy(seasons = seasons)
                            } catch (e: Exception) {
                                series
                            }
                        }
                    }
                    val flat = flattenEpisodes(series)
                    if (flat.isEmpty()) continue
                    // start od wznowienia jeśli jakiś odcinek jest już w trakcie, inaczej od pierwszego
                    val resumeIdx = flat.indexOfFirst { state.watchProgress.containsKey(it.second.id) }
                    val startIdx = if (resumeIdx >= 0) resumeIdx else 0
                    val seriesEntries = flat.map { (seasonNum, ep) -> queueEntryFromEpisode(series, ep, seasonNum) }
                    // Osobne id "wejściowe" dla pozycji serialu w liście (żeby nie kolidowało
                    // z id konkretnego odcinka) — patrz stepQueue()/seriesSubQueues w PlayerActivity.
                    val headEntry = seriesEntries[startIdx].copy(id = "series-entry:${series.id}")
                    entries += headEntry
                    subQueues[headEntry.id] = SeriesSubQueue(seriesEntries, startIdx)
                }
            }
        }
        val idx = entries.indexOfFirst { it.id == currentItemId }.coerceAtLeast(0)
        return NavQueue(QueueKind.CUSTOM_LIST, entries, idx, subQueues)
    }

    private fun launchPlayer(channel: Channel, forceStartFromZero: Boolean = false) {
        lifecycleScope.launch {
            val state = viewModel.state.value
            val list = state.selectedListId?.let { id -> state.customLists.find { it.id == id } }
            val queue = when {
                list != null -> buildListQueue(list, channel.id)
                channel.kind == MediaKind.MOVIE -> buildSimilarMoviesQueue(channel)
                else -> buildLiveGroupQueue(channel)
            }
            playQueue(queue, forceStartFromZero = forceStartFromZero)
        }
    }

    /** Odtwarzanie odcinka serialu — korzysta z UA/Referer playlisty, do której należy serial.
     * Zawsze buduje kolejkę po odcinkach (wyjątek dla serialu, patrz buildSeriesEpisodesQueue). */
    private fun launchEpisodePlayer(series: SeriesInfo, episode: Episode, forceStartFromZero: Boolean = false) {
        playQueue(buildSeriesEpisodesQueue(series, episode), forceStartFromZero = forceStartFromZero)
    }

    /** Wznowienie odtwarzania z kafelka "Kontynuuj oglądanie" — bez kontekstu listy (Home nie
     * jest listą użytkownika), więc obowiązuje domyślna reguła per rodzaj materiału. */
    private fun resumeWatchProgress(progress: WatchProgress) {
        if (progress.streamUrl.isBlank()) return
        val state = viewModel.state.value
        val fallback = NavQueue(
            kind = QueueKind.SIMILAR_MOVIES,
            items = listOf(
                QueueEntry(
                    id = progress.channelOrEpisodeId,
                    kind = progress.kind,
                    title = progress.title,
                    streamUrl = progress.streamUrl,
                    posterUrl = progress.posterUrl,
                    userAgent = progress.userAgent,
                    referer = progress.referer,
                ),
            ),
            startIndex = 0,
        )
        val queue = when (progress.kind) {
            MediaKind.LIVE -> state.liveChannels.find { it.id == progress.channelOrEpisodeId }
                ?.let { buildLiveGroupQueue(it) } ?: fallback
            MediaKind.MOVIE -> state.movies.find { it.id == progress.channelOrEpisodeId }
                ?.let { buildSimilarMoviesQueue(it) } ?: fallback
            MediaKind.SERIES -> state.series
                .find { s -> s.seasons.any { season -> season.episodes.any { it.id == progress.channelOrEpisodeId } } }
                ?.let { series -> flattenEpisodes(series).find { it.second.id == progress.channelOrEpisodeId }
                    ?.let { (_, ep) -> buildSeriesEpisodesQueue(series, ep) } } ?: fallback
        }
        playQueue(queue)
    }

    private fun downloadChannel(channel: Channel) {
        // Zablokowane po okresie próbnym — patrz AppViewModel.requestPremiumAccess.
        if (viewModel.requestPremiumAccess()) return
        val playlist = viewModel.state.value.playlists.find { it.id == channel.playlistId }
        com.iptvplayer.app.data.DownloadHelper.downloadItem(
            context = this,
            streamUrl = channel.streamUrl,
            title = channel.name,
            userAgent = playlist?.userAgent,
            referer = playlist?.referer,
            lang = viewModel.state.value.language,
            posterUrl = channel.logoUrl,
        )
    }

    /** Pobieranie bezpośrednio z kafelka "Kontynuuj oglądanie" (Home/Live/Movies/Series) —
     * WatchProgress ma już zapisane streamUrl/userAgent/referer z chwili odtwarzania,
     * więc nie trzeba dodatkowo szukać oryginalnego kanału ani playlisty. */
    private fun downloadWatchProgress(progress: WatchProgress) {
        if (viewModel.requestPremiumAccess()) return
        com.iptvplayer.app.data.DownloadHelper.downloadItem(
            context = this,
            streamUrl = progress.streamUrl,
            title = progress.title,
            userAgent = progress.userAgent,
            referer = progress.referer,
            lang = viewModel.state.value.language,
            posterUrl = progress.posterUrl,
        )
    }

    /** Pobiera pojedynczy odcinek z listy odcinków serialu (ekran szczegółów) —
     * wcześniej pobranie odcinka było możliwe tylko z poziomu odtwarzacza. */
    private fun downloadEpisode(series: SeriesInfo, episode: Episode) {
        if (viewModel.requestPremiumAccess()) return
        val playlist = viewModel.state.value.playlists.find { it.id == series.playlistId }
        com.iptvplayer.app.data.DownloadHelper.downloadItem(
            context = this,
            streamUrl = episode.streamUrl,
            title = episodeDisplayTitle(series, episode, series.seasons.find { s -> s.episodes.any { it.id == episode.id } }?.number),
            userAgent = playlist?.userAgent,
            referer = playlist?.referer,
            lang = viewModel.state.value.language,
            posterUrl = series.posterUrl,
        )
    }

    /** Pobiera wszystkie odcinki wybranego sezonu naraz — przycisk pobierania na plakacie
     * serialu pyta najpierw który sezon (patrz SeasonPickerDialog), potem enqueue'uje po
     * kolei każdy odcinek do DownloadManager (silent=true, jedno zbiorcze podsumowanie). */
    private fun downloadSeriesSeason(series: SeriesInfo, seasonNumber: Int) {
        if (viewModel.requestPremiumAccess()) return
        val lang = viewModel.state.value.language
        val season = series.seasons.find { it.number == seasonNumber }
        if (season == null || season.episodes.isEmpty()) {
            Toast.makeText(this, trFor(lang, "root.seasonDownload.noEpisodesToast"), Toast.LENGTH_SHORT).show()
            return
        }
        val playlist = viewModel.state.value.playlists.find { it.id == series.playlistId }
        season.episodes.forEach { ep ->
            com.iptvplayer.app.data.DownloadHelper.downloadItem(
                context = this,
                streamUrl = ep.streamUrl,
                title = episodeDisplayTitle(series, ep, seasonNumber),
                userAgent = playlist?.userAgent,
                referer = playlist?.referer,
                silent = true,
                lang = lang,
                posterUrl = series.posterUrl,
            )
        }
        Toast.makeText(
            this,
            trFor(lang, "root.seasonDownload.startedToast", seasonNumber, season.episodes.size),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onResume() {
        super.onResume()
        // dogrywamy najświeższy postęp odtwarzania po powrocie z PlayerActivity —
        // ten zapisuje przez osobną instancję LocalStore, więc trzeba jawnie odświeżyć stan
        viewModel.refreshWatchProgress()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResumeIntent(intent)
    }

    /** Otwarcie appki z kafelka "Kontynuuj oglądanie" na ekranie głównym Android TV (patrz
     * WatchNextSync) — od razu wznawia ten konkretny film/odcinek. Extra jest usuwane po
     * użyciu, żeby np. obrót ekranu albo powrót do appki nie odpalał odtwarzania ponownie. */
    private fun handleResumeIntent(intent: Intent?) {
        val id = intent?.getStringExtra(com.iptvplayer.app.data.WatchNextSync.EXTRA_RESUME_ID) ?: return
        intent.removeExtra(com.iptvplayer.app.data.WatchNextSync.EXTRA_RESUME_ID)
        // Bramka akceptacji (sekcja 2 specyfikacji): dopóki Użytkownik nie zaakceptował
        // ekranu powitalnego, żadne odtwarzanie nie może ruszyć — również z kafelka
        // "Kontynuuj oglądanie" na ekranie głównym Android TV (zostaje po starszej wersji).
        if (!LocalStore(this).hasAcceptedOnboarding()) return
        val progress = viewModel.state.value.watchProgress[id]
            ?: LocalStore(this).getWatchProgress()[id]
            ?: return
        resumeWatchProgress(progress)
    }
}

/** Trzy stany wskaźnika połączenia w headerze — patrz ConnectionStatusPill. */
private enum class ConnectionHealth { OK, PARTIAL, DOWN }

/**
 * Zamiast poprzedniej mało użytecznej pigułki "⟳ X playlist" (sam tekst z liczbą playlist
 * nic nie mówił o tym, czy w ogóle działają) — kolorowa kropka statusu połączenia:
 * zielona = wszystkie playlisty odpowiadają, żółta = przynajmniej jedna ma problem (ale
 * nie wszystkie), czerwona = brak playlisty albo żadna nie odpowiada. Tap = ręczne
 * odświeżenie (jak dotychczasowa ikona Refresh); gdy status nie jest zielony, tap dodatkowo
 * pokazuje Toast z nazwami playlist, które akurat szwankują — żeby nie trzeba było
 * zgadywać/przechodzić do zakładki Playlists tylko po to, by to sprawdzić.
 */
@Composable
private fun ConnectionStatusPill(
    playlists: List<com.iptvplayer.app.data.PlaylistSource>,
    statuses: Map<String, PlaylistStatus>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val lang = LocalLang.current

    val health = when {
        playlists.isEmpty() -> ConnectionHealth.DOWN
        playlists.all { statuses[it.id]?.ok == true } -> ConnectionHealth.OK
        playlists.any { statuses[it.id]?.ok == true } -> ConnectionHealth.PARTIAL
        else -> ConnectionHealth.DOWN
    }
    val (dotColor, label) = when (health) {
        ConnectionHealth.OK -> Success to tr("root.connection.ok")
        ConnectionHealth.PARTIAL -> Warning to tr("root.connection.checkPlaylists")
        ConnectionHealth.DOWN -> Danger to if (playlists.isEmpty()) tr("home.status.noPlaylist") else tr("root.connection.noConnection")
    }
    val failing = playlists.filter { statuses[it.id]?.ok == false }
    val noResponseText = trFor(lang, "root.connection.noResponse")

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Surface)
            .clickable {
                if (failing.isNotEmpty()) {
                    val details = failing.joinToString("\n") { p ->
                        "${p.name}: ${statuses[p.id]?.errorMessage ?: noResponseText}"
                    }
                    Toast.makeText(context, details, Toast.LENGTH_LONG).show()
                }
                onRefresh()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isLoading) TextDim else dotColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (isLoading) tr("home.status.refreshing") else label,
            color = TextDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RootScreen(
    viewModel: AppViewModel,
    onPlayChannel: (Channel) -> Unit,
    // "Odtwórz od początku" — jawnie pomija automatyczne wznowienie z zapisanej pozycji,
    // obok "Kontynuuj oglądanie" (onPlayChannel/onPlayEpisode wyżej) w szczegółach
    // filmu/serialu. Patrz PlayerActivity.EXTRA_FORCE_START_FROM_ZERO.
    onPlayChannelFromStart: (Channel) -> Unit,
    onDownloadChannel: (Channel) -> Unit,
    onDownloadProgress: (WatchProgress) -> Unit,
    onDownloadEpisode: (SeriesInfo, com.iptvplayer.app.data.Episode) -> Unit,
    onDownloadSeriesSeason: (SeriesInfo, Int) -> Unit,
    onPlayEpisode: (SeriesInfo, com.iptvplayer.app.data.Episode) -> Unit,
    onPlayEpisodeFromStart: (SeriesInfo, com.iptvplayer.app.data.Episode) -> Unit,
    onResumeProgress: (com.iptvplayer.app.data.WatchProgress) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onAddLocalFolder: () -> Unit,
    onPlayLocalVideo: (com.iptvplayer.app.data.LocalVideoFile, List<com.iptvplayer.app.data.LocalVideoFile>) -> Unit,
    onRetryDownload: (com.iptvplayer.app.data.DownloadRecord) -> Unit,
    onPlayDownloadedFile: (Uri, String, String?) -> Unit,
    onWatchCatchup: (Channel, Long, Int) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // Do SettingsScreen (status "Pełna wersja: kupiona/niekupiona") i do
    // PurchaseConfirmationScreen wyżej — osobny StateFlow od `state`, patrz
    // AppViewModel.billingState.
    val billingState by viewModel.billingState.collectAsState()
    // Do Toastów niżej (np. przy pobieraniu sezonu) — brakowało tej deklaracji w samym
    // RootScreen (istniała tylko lokalnie w sąsiedniej ConnectionStatusPill), co dawało
    // "Unresolved reference: context" przy realnej kompilacji.
    val context = LocalContext.current

    // Bramka prawna — ekran powitalny (patrz OnboardingScreen.kt i obszerny komentarz przy
    // LocalStore.hasAcceptedOnboarding()). Wczesny return: NIC innego w appce nie renderuje
    // się, dopóki Użytkownik nie zaznaczy świadomie checkboxa (pełnoletność 18+, Regulamin,
    // Polityka prywatności) — appka dostarcza treść cyfrową (swoją pełną funkcjonalność)
    // natychmiast, więc wymóg prawny to poinformowanie o utracie prawa odstąpienia PRZED
    // rozpoczęciem korzystania, nie po (patrz też ekran przed zakupem, część 4 specyfikacji,
    // gdzie to samo jest potwierdzane po raz drugi, bezpośrednio przy zakupie).
    val localStore = remember { LocalStore(context) }
    var hasAcceptedOnboarding by remember { mutableStateOf(localStore.hasAcceptedOnboarding()) }
    if (!hasAcceptedOnboarding) {
        OnboardingScreen(
            onAccept = {
                // Dopiero TERAZ (po zapisaniu akceptacji w OnboardingScreen) startują usługi
                // sieciowe, Billing i ładowanie katalogu — patrz AppViewModel.onOnboardingAccepted.
                viewModel.onOnboardingAccepted()
                hasAcceptedOnboarding = true
            },
        )
        return
    }

    // Zakładka startowa: zależy od trybu uruchamiania appki (przycisk obok zegara na
    // Home, patrz AppUiState.continueFromLastPosition) — domyślnie zawsze Home, ale gdy
    // użytkownik włączył "kontynuuj", appka wraca do ostatnio otwartej głównej zakładki.
    // Czytane tylko RAZ przy pierwszej kompozycji (remember bez klucza) — kolejne zmiany
    // currentTab w trakcie sesji idą wyłącznie przez selectTab() niżej.
    var currentTab by remember {
        mutableStateOf(
            if (state.continueFromLastPosition) {
                Tab.entries.find { it.name == state.lastTab } ?: Tab.HOME
            } else {
                Tab.HOME
            },
        )
    }
    // Reklamy (sekcja 9, wydanie `ads` — patrz com.iptvplayer.app.ads.AdsController).
    // trialExpired/isPurchased liczone tu RAZEM z bramką reklam poniżej, żeby nie czytać
    // TrialGate.isExpired(context) (niereaktywne, zwykły getter) osobno w kilku miejscach.
    val adsTrialExpired = viewModel.isTrialExpired
    val adsIsPurchased = billingState.isPurchased
    val activity = context as? android.app.Activity

    // Jedyne miejsce zmiany zakładki — poza ustawieniem currentTab od razu zapisuje ją
    // jako "ostatnią" (LocalStore), żeby tryb "kontynuuj" miał zawsze aktualną wartość.
    // Zmiana zakładki to dokładnie "przejście między ekranami" z sekcji 9 — stąd tu (i
    // TYLKO tu, nigdy w PlayerActivity) wywołanie ewentualnej reklamy pełnoekranowej.
    val selectTab: (Tab) -> Unit = { tab ->
        com.iptvplayer.app.data.CrashLogger.breadcrumb("nav", "selectTab -> ${tab.name}")
        currentTab = tab
        viewModel.saveLastTab(tab.name)
        activity?.let { com.iptvplayer.app.ads.AdsController.maybeShowInterstitial(it, adsTrialExpired, adsIsPurchased) }
    }
    // Zgoda UMP + inicjalizacja SDK reklam (sekcja 9) — no-op w wariancie `noads`, patrz
    // com.iptvplayer.app.ads.AdsController (dwie osobne implementacje w app/src/ads i
    // app/src/noads). Wymaga Activity (okna/dialogi UMP), więc tylko gdy dostępna.
    if (activity != null) {
        com.iptvplayer.app.ads.AdsController.AdsGate(activity, adsTrialExpired, adsIsPurchased)
    }
    // odpowiednik state.globalSearch w app.js — jeden wspólny przełącznik dla Live/Movies/Series
    var globalSearchEnabled by remember { mutableStateOf(false) }
    // Ekran zarządzania playlistami IPTV, otwierany z wnętrza Settings (patrz
    // SettingsScreen.onManagePlaylists) — rysowany jako nakładka, tak samo jak ekrany
    // szczegółów niżej, żeby Wstecz naturalnie z niego wracał do Settings.
    var showPlaylistsManager by remember { mutableStateOf(false) }
    // Pełna siatka EPG (kanały × oś czasu, styl TiviMate), otwierana z zakładki Live —
    // rysowana jako nakładka tym samym wzorcem co showPlaylistsManager wyżej.
    var showEpgGrid by remember { mutableStateOf(false) }
    // Lista kanałów przekazana z LiveScreen w momencie otwarcia (po kategorii/ulubionych/
    // wyszukiwaniu, patrz LiveScreen.onOpenEpgGrid) — NIE state.liveChannels wprost, żeby
    // grid pokazywał to, na co użytkownik akurat patrzył, a nie zawsze wszystkie kanały.
    var epgGridChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    // Ekran "Pobrane" (lista pobrań z bieżącym postępem), otwierany z Ustawień — ten sam
    // wzorzec nakładki co showEpgGrid wyżej.
    var showDownloads by remember { mutableStateOf(false) }
    // Pełny tekst Regulaminu/Polityki prywatności (sekcja 7, otwierany z Ustawień ->
    // Informacje prawne) — ten sam wzorzec nakładki co showDownloads wyżej. `null` = brak
    // nakładki, w przeciwnym razie który dokument pokazać (patrz LegalDocumentScreen.kt).
    var showLegalDoc by remember { mutableStateOf<com.iptvplayer.app.data.LegalDocType?>(null) }
    // Ekran "Moje zgody" (tylko do odczytu, sekcja 7 specyfikacji) — zapisany rekord zgód;
    // otwierany z Ustawień -> Informacje prawne, ten sam wzorzec nakładki co showLegalDoc.
    var showConsents by remember { mutableStateOf(false) }

    // --- ekran szczegółów serialu (sezony/odcinki) rysowany jako nakładka nad zakładkami ---
    if (state.selectedSeries != null) {
        // Fizyczny przycisk Wstecz na pilocie (i gest/przycisk wstecz na telefonie) ma
        // zamykać ten ekran, a NIE zamykać całej aplikacji — bez tego jedyną drogą powrotu
        // byłby przycisk strzałki w samym interfejsie, co na pilocie bez dotyku jest
        // niewygodne, a na telefonie z gestem "wstecz" to zwyczajnie zaskakujący błąd.
        BackHandler { viewModel.closeSeriesDetail() }
        SeriesDetailScreen(
            series = state.selectedSeries!!,
            isFavorite = state.selectedSeries!!.id in state.favorites,
            isLoading = state.isLoadingSeriesDetail,
            errorMessage = state.seriesDetailError,
            onToggleFavorite = { viewModel.toggleFavorite(state.selectedSeries!!.id) },
            onBack = { viewModel.closeSeriesDetail() },
            onPlayEpisode = { ep -> onPlayEpisode(state.selectedSeries!!, ep) },
            onPlayEpisodeFromStart = { ep -> onPlayEpisodeFromStart(state.selectedSeries!!, ep) },
            onDownloadSeason = { viewModel.requestSeriesSeasonDownload(state.selectedSeries!!) },
            onDownloadEpisode = { ep -> onDownloadEpisode(state.selectedSeries!!, ep) },
            watchProgress = state.watchProgress,
            similarSeries = state.similarSeries,
            favorites = state.favorites,
            onToggleFavoriteId = { viewModel.toggleFavorite(it) },
            onSelectSimilar = { viewModel.openSeriesDetail(it) },
            customLists = state.customLists,
            onToggleManyInList = viewModel::toggleManyInList,
            onCreateList = viewModel::createList,
            autoCloseAddToList = state.autoCloseAddToList,
        )
        return
    }

    // --- ekran informacji o filmie (opis/obsada/reżyser) rysowany jako nakładka nad zakładkami ---
    if (state.selectedMovie != null) {
        BackHandler { viewModel.closeMovieDetail() }
        MovieDetailScreen(
            movie = state.selectedMovie!!,
            detail = state.selectedMovieDetail,
            isLoadingDetail = state.isLoadingMovieDetail,
            isFavorite = state.selectedMovie!!.id in state.favorites,
            onToggleFavorite = { viewModel.toggleFavorite(state.selectedMovie!!.id) },
            onBack = { viewModel.closeMovieDetail() },
            onPlay = { onPlayChannel(state.selectedMovie!!) },
            onPlayFromStart = { onPlayChannelFromStart(state.selectedMovie!!) },
            onDownload = { onDownloadChannel(state.selectedMovie!!) },
            watchProgress = state.watchProgress[state.selectedMovie!!.id],
            similarMovies = state.similarMovies,
            favorites = state.favorites,
            onToggleFavoriteId = { viewModel.toggleFavorite(it) },
            onSelectSimilar = { viewModel.openMovieDetail(it) },
            customLists = state.customLists,
            onToggleManyInList = viewModel::toggleManyInList,
            onCreateList = viewModel::createList,
            autoCloseAddToList = state.autoCloseAddToList,
        )
        return
    }

    // --- ekran pojedynczej listy ("Święta", "Akcja"…) rysowany jako nakładka nad zakładkami —
    // sprawdzany PO filmie/serialu, żeby otwarcie pozycji z wnętrza listy poprawnie nałożyło
    // się na wierzch (ta sama zasada co dotychczasowe zagnieżdżanie nakładek). ---
    val selectedList = state.customLists.find { it.id == state.selectedListId }
    if (selectedList != null) {
        BackHandler { viewModel.closeListDetail() }
        ListDetailScreen(
            list = selectedList,
            liveChannels = state.liveChannels,
            movies = state.movies,
            series = state.series,
            favorites = state.favorites,
            onBack = { viewModel.closeListDetail() },
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayLive = onPlayChannel,
            onOpenMovie = { viewModel.openMovieDetail(it) },
            onOpenSeries = { viewModel.openSeriesDetail(it) },
            onRename = { name -> viewModel.renameList(selectedList.id, name) },
            onSetEmoji = { emoji -> viewModel.setListEmoji(selectedList.id, emoji) },
            onDeleteList = { viewModel.deleteList(selectedList.id) },
            onRemoveItems = { ids -> ids.forEach { viewModel.removeItemFromList(selectedList.id, it) } },
            onAddItem = { id, kind -> viewModel.addItemToList(selectedList.id, id, kind) },
            onRemoveItem = { id -> viewModel.removeItemFromList(selectedList.id, id) },
        )
        return
    }

    // --- pełna siatka EPG (kanały × oś czasu), otwierana z zakładki Live — rysowana jako
    // nakładka nad zakładkami, tak samo jak ekrany szczegółów wyżej. Osobny (nie
    // globalny) odtwarzacz podglądu żyje wewnątrz samego EpgGridScreen, więc nic tu nie
    // trzeba dodatkowo zwalniać przy zamknięciu — patrz DisposableEffect w tamtym pliku. ---
    if (showEpgGrid) {
        BackHandler { showEpgGrid = false }
        com.iptvplayer.app.ui.screens.EpgGridScreen(
            channels = epgGridChannels,
            playlists = state.playlists,
            epgRepository = viewModel.epgRepository,
            onBack = { showEpgGrid = false },
            onWatchFullscreen = { channel ->
                showEpgGrid = false
                onPlayChannel(channel)
            },
        )
        return
    }

    // --- ekran "Pobrane", otwierany z Ustawień — rysowany jako nakładka nad zakładkami,
    // tak samo jak ekrany szczegółów wyżej. Świeży odczyt listy przy KAŻDYM wejściu
    // (viewModel.refreshDownloads()), bo DownloadHelper.downloadItem zapisuje bezpośrednio
    // do LocalStore z zupełnie innych ekranów — bez odświeżenia lista byłaby nieaktualna. ---
    if (showDownloads) {
        LaunchedEffect(Unit) { viewModel.refreshDownloads() }
        BackHandler { showDownloads = false }
        com.iptvplayer.app.ui.screens.DownloadsScreen(
            downloads = state.downloads,
            onBack = { showDownloads = false },
            onRemove = { viewModel.removeDownload(it.managerId) },
            onRetry = onRetryDownload,
            onPlayLocal = { uri, title, poster ->
                showDownloads = false
                onPlayDownloadedFile(uri, title, poster)
            },
        )
        return
    }

    // --- pełny tekst Regulaminu/Polityki prywatności, otwierany z Ustawień -> Informacje
    // prawne (sekcja 7) — rysowany jako nakładka, tak samo jak ekrany wyżej. ---
    if (showLegalDoc != null) {
        BackHandler { showLegalDoc = null }
        LegalDocumentScreen(type = showLegalDoc!!, onBack = { showLegalDoc = null })
        return
    }

    // --- "Moje zgody": zapisany rekord akceptacji, bez żadnej możliwości edycji (sekcja 7). ---
    if (showConsents) {
        BackHandler { showConsents = false }
        ConsentRecordScreen(onBack = { showConsents = false })
        return
    }

    // --- powiadomienie o zmianie Regulaminu/Polityki prywatności (sekcja 10 specyfikacji
    // zgodności prawnej z 09.10) — liczone RAZ przy pierwszym wejściu do RootScreen po tym
    // uruchomieniu (remember bez klucza). Baseline zaakceptowanych wersji ZAWSZE istnieje w
    // tym miejscu (zapisywany przy akceptacji ekranu powitalnego, patrz OnboardingScreen.kt),
    // więc jeśli wersja wbudowanego pliku jest INNA niż zapisana, dokument został zaktualizowany
    // w tej wersji appki. Kolejka (lista), bo oba dokumenty mogą się zmienić naraz — pokazujemy
    // po jednym na raz, "Przeczytaj" otwiera pełny tekst BEZ zdejmowania powiadomienia z
    // kolejki (patrz showLegalDoc wyżej — po powrocie z pełnego tekstu ten sam komunikat wraca),
    // tylko "OK" zapisuje nową wersję jako zaakceptowaną i przechodzi do kolejnego powiadomienia. ---
    var pendingLegalChanges by remember {
        mutableStateOf(
            com.iptvplayer.app.data.LegalDocType.entries.mapNotNull { type ->
                val current = runCatching { com.iptvplayer.app.data.LegalDocuments.load(context, type) }.getOrNull()
                    ?: return@mapNotNull null
                val acknowledged = localStore.getAcknowledgedLegalDocVersion(type.legalDocVersionKey)
                if (acknowledged != null && acknowledged != current.version) type to current else null
            },
        )
    }
    if (pendingLegalChanges.isNotEmpty()) {
        val (changedType, changedDoc) = pendingLegalChanges.first()
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("legalChange.title")) },
            text = {
                Text(
                    tr(
                        if (changedType == com.iptvplayer.app.data.LegalDocType.REGULAMIN) "legalChange.regulaminBody" else "legalChange.politykaBody",
                        changedDoc.effectiveDate,
                        changedDoc.changelogSummary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    localStore.setAcknowledgedLegalDocVersion(changedType.legalDocVersionKey, changedDoc.version)
                    pendingLegalChanges = pendingLegalChanges.drop(1)
                }) { Text(tr("common.ok")) }
            },
            dismissButton = {
                TextButton(onClick = { showLegalDoc = changedType }) { Text(tr("legalChange.readButton")) }
            },
        )
    }

    // --- ekran zarządzania playlistami IPTV, otwierany przyciskiem w Settings — rysowany
    // jako nakładka nad zakładkami, tak samo jak ekrany szczegółów wyżej. ---
    if (showPlaylistsManager) {
        BackHandler { showPlaylistsManager = false }
        Column(Modifier.fillMaxSize().background(Bg)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showPlaylistsManager = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = tr("common.back"), tint = TextMain)
                }
                Text(tr("root.playlistsTitle"), color = TextMain, style = MaterialTheme.typography.headlineSmall)
            }
            Box(Modifier.weight(1f)) {
                PlaylistsScreen(
                    playlists = state.playlists,
                    statuses = state.playlistStatuses,
                    onAddM3U = viewModel::addM3UPlaylist,
                    onAddXtream = viewModel::addXtreamPlaylist,
                    onEditM3U = viewModel::updateM3UPlaylist,
                    onEditXtream = viewModel::updateXtreamPlaylist,
                    onRemove = viewModel::removePlaylist,
                )
            }
        }
        return
    }

    // tytuł headera zgodny z aktywną zakładką — tak jak #headerTitle w app.js
    val headerTitle = tr(currentTab.labelKey)

    // Pilot/telefon: Wstecz na dowolnej zakładce innej niż Home wraca najpierw na Home
    // (standardowy wzorzec dla appek z dolną nawigacją — tak samo działa YouTube, Netflix
    // itp. na Android TV). Na SAMYM Home, Wstecz przenosi fokus pilota bezpośrednio na dolny
    // pasek nawigacji — bez tego jedynym sposobem dotarcia tam było przewijanie strzałkami
    // przez CAŁĄ zawartość ekranu od góry (przy dłuższych listach dziesiątki pozycji, patrz
    // zgłoszenie). Zamknięcie appki Wstecz-em na Home celowo USUNIĘTE z tego handlera — appka
    // ma do tego osobną, jawną drogę (przycisk "Wyjście" widoczny na Home), więc pilot nie
    // ryzykuje przypadkowego zamknięcia appki podczas zwykłej nawigacji.
    val bottomNavFocusRequester = remember { FocusRequester() }
    // Cel zejścia pilotem W DÓŁ z paska nawigacji, gdy aktywna jest zakładka Live — patrz
    // topFocusRequester w LiveScreen.kt (pole wyszukiwania, zawsze na górze niezależnie od
    // przewinięcia listy niżej) i jego jawne podłączenie do IptvBottomNav niżej. Bez tego
    // jedyną drogą z paska nawigacji "w górę" ekranu była strzałka GÓRA, która przez
    // domyślne wyszukiwanie kierunkowe Compose czasem lądowała gdzieś w środku przewiniętej
    // listy kanałów, zamiast na samej górze (zgłoszenie).
    val liveTopFocusRequester = remember { FocusRequester() }
    // Ten sam mechanizm dla Home/Filmy/Seriale — pole wyszukiwania na każdym z tych
    // ekranów jest ZAWSZE widoczne na górze, niezależnie od przewinięcia listy niżej.
    // Moje i Ustawienia CELOWO pominięte — nie mają odpowiednika pola wyszukiwania jako
    // naturalnego, zawsze obecnego celu, a przy okazji mają krócsze listy niż Live/Filmy/
    // Seriale (mniejsze ryzyko realnego wystąpienia zgłoszonego problemu).
    val homeTopFocusRequester = remember { FocusRequester() }
    val moviesTopFocusRequester = remember { FocusRequester() }
    val seriesTopFocusRequester = remember { FocusRequester() }
    // Moje/Ustawienia — uzupełnione dla pełnej spójności ze wszystkimi zakładkami
    // (zgłoszenie: brakowało tego samego mechanizmu tu i na Ustawieniach). Moje nie ma
    // żadnego elementu ZAWSZE obecnego u góry, więc LocalFolderScreen.kt dodaje własną,
    // niewidoczną kotwicę fokusu — patrz tam. Ustawienia mają logo na samej górze, które
    // już zawsze jest obecne, więc tam podpięte wprost pod nie.
    val folderTopFocusRequester = remember { FocusRequester() }
    val settingsTopFocusRequester = remember { FocusRequester() }
    // "Wstecz na Home = fokus na pasek nawigacji" ma sens WYŁĄCZNIE na telewizorze — to
    // rozwiązanie problemu z dotarciem tam pilotem, nie ma żadnego widocznego efektu na
    // dotyku (podświetlenie fokusu to pojęcie z pilota, nie z ekranu dotykowego), więc na
    // telefonie wyglądało to jak "nic się nie dzieje po Wstecz" (zgłoszenie: przycisk
    // Wstecz zawsze cofał "do początku" zamiast zachowywać się jak zwykle w Androidzie —
    // czyli zamykać appkę z Home, gdy nie ma dokąd dalej cofnąć). Na telefonie handler jest
    // WYŁĄCZONY na Home (enabled=false), więc Wstecz spada do domyślnego zachowania systemu.
    val isTv = remember { context.isTvDevice() }
    // "Naciśnij Wstecz ponownie, aby wyjść" — TYLKO na telefonie, na Home (zgłoszenie:
    // przypadkowe opuszczenie appki jednym naciśnięciem Wstecz na Home). Standardowy,
    // znany z wielu innych aplikacji wzorzec: pierwsze Wstecz na Home pokazuje krótki
    // komunikat i NIE zamyka appki; dopiero DRUGIE Wstecz w ciągu ~2 sekund faktycznie
    // wychodzi. Po tym czasie licznik się resetuje — trzeba zaczynać od nowa. TV pominięte
    // celowo: tam Wstecz na Home ma zupełnie inne, już ustalone znaczenie (patrz komentarz
    // przy poniższym BackHandler) — dodatkowe potwierdzenie tylko by przeszkadzało pilotem.
    var backPressedOnceAt by remember { mutableStateOf(0L) }
    val exitConfirmMessage = tr("home.pressBackAgainToExit")
    BackHandler(enabled = !isTv && currentTab == Tab.HOME) {
        val now = System.currentTimeMillis()
        if (now - backPressedOnceAt < 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnceAt = now
            android.widget.Toast.makeText(context, exitConfirmMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler(enabled = currentTab != Tab.HOME || isTv) {
        if (currentTab != Tab.HOME) {
            selectTab(Tab.HOME)
        } else {
            runCatching { bottomNavFocusRequester.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        // --- header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo appki — TYLKO na Home, żeby ten konkretny ekran (pierwszy, jaki
                // użytkownik widzi) miał wyraźną tożsamość zamiast wyglądać anonimowo jak
                // dotychczas (zgłoszenie). Pozostałe zakładki mają wystarczająco wyraźny
                // kontekst z samej nazwy (Live/Filmy/Seriale itd.), nie potrzebują logo.
                if (currentTab == Tab.HOME) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.mipmap.ic_launcher_round),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(headerTitle, color = TextMain, style = MaterialTheme.typography.headlineSmall)
            }
            ConnectionStatusPill(
                playlists = state.playlists,
                statuses = state.playlistStatuses,
                isLoading = state.isLoading,
                onRefresh = { viewModel.refreshAll() },
            )
        }

        // --- content ---
        Box(Modifier.weight(1f)) {
            when (currentTab) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    onNavigateTab = selectTab,
                    onRefresh = { viewModel.refreshAll() },
                    onResumeWatching = onResumeProgress,
                    onRemoveWatching = { viewModel.removeWatchProgress(it.channelOrEpisodeId) },
                    onOpenMovie = { viewModel.openMovieDetail(it) },
                    onOpenSeries = { viewModel.openSeriesDetail(it) },
                    onPlayLive = onPlayChannel,
                    // launchPlayer (onPlayChannel) już poprawnie rozróżnia MediaKind.MOVIE
                    // (buduje kolejkę "podobne filmy") od LIVE — ten sam handler nadaje się
                    // więc wprost do bezpośredniego odtworzenia filmu z plakatu, bez zmian.
                    onQuickPlayMovie = onPlayChannel,
                    onOpenList = { viewModel.openListDetail(it) },
                    onCreateList = viewModel::createList,
                    favorites = state.favorites,
                    onToggleFavorite = viewModel::toggleFavorite,
                    globalSearchEnabled = globalSearchEnabled,
                    onToggleGlobalSearch = { globalSearchEnabled = it },
                    onDownloadMovie = onDownloadChannel,
                    onDownloadProgress = onDownloadProgress,
                    onDownloadSeries = { viewModel.requestSeriesSeasonDownload(it) },
                    customLists = state.customLists,
                    onToggleManyInList = viewModel::toggleManyInList,
                    continueFromLastPosition = state.continueFromLastPosition,
                    onSetContinueFromLastPosition = viewModel::setContinueFromLastPosition,
                    onToggleHomeSectionCollapsed = viewModel::toggleHomeSectionCollapsed,
                    autoCloseAddToList = state.autoCloseAddToList,
                    topFocusRequester = homeTopFocusRequester,
                )
                Tab.LIVE -> LiveScreen(
                    channels = state.liveChannels,
                    favorites = state.favorites,
                    // Każdy kanał bierze EPG z WŁASNEJ playlisty (patrz Channel.playlistId),
                    // nie tylko z pierwszej — inaczej kanały z drugiej i kolejnych playlist
                    // dostawały błędne dane programu (albo żadne).
                    epgUrlFor = { ch -> state.playlists.find { it.id == ch.playlistId }?.epgUrl },
                    epgRepository = viewModel.epgRepository,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlay = onPlayChannel,
                    globalSearchEnabled = globalSearchEnabled,
                    onToggleGlobalSearch = { globalSearchEnabled = it },
                    allMovies = state.movies,
                    allSeries = state.series,
                    onOpenSeries = { viewModel.openSeriesDetail(it) },
                    onOpenMovie = { viewModel.openMovieDetail(it) },
                    watchProgress = state.watchProgress,
                    onResumeWatching = onResumeProgress,
                    onRemoveWatching = { viewModel.removeWatchProgress(it.channelOrEpisodeId) },
                    customLists = state.customLists,
                    onToggleManyInList = viewModel::toggleManyInList,
                    onCreateList = viewModel::createList,
                    onOpenEpgGrid = { filteredChannels ->
                        // Pełny grid EPG (kanały × oś czasu + catch-up) jest ZAWSZE wolny —
                        // patrz TrialGate.kt.
                        com.iptvplayer.app.data.CrashLogger.breadcrumb("epg", "otwarto grid EPG (${filteredChannels.size} kanałów)")
                        epgGridChannels = filteredChannels
                        showEpgGrid = true
                    },
                    onWatchCatchup = onWatchCatchup,
                    autoCloseAddToList = state.autoCloseAddToList,
                    customMoodRules = state.customMoodRules,
                    topFocusRequester = liveTopFocusRequester,
                )
                Tab.MOVIES -> MoviesScreen(
                    movies = state.movies,
                    favorites = state.favorites,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlay = onPlayChannel,
                    onDownload = onDownloadChannel,
                    onOpenMovie = { viewModel.openMovieDetail(it) },
                    globalSearchEnabled = globalSearchEnabled,
                    onToggleGlobalSearch = { globalSearchEnabled = it },
                    allLive = state.liveChannels,
                    allSeries = state.series,
                    onOpenSeries = { viewModel.openSeriesDetail(it) },
                    watchProgress = state.watchProgress,
                    onResumeWatching = onResumeProgress,
                    onRemoveWatching = { viewModel.removeWatchProgress(it.channelOrEpisodeId) },
                    onDownloadProgress = onDownloadProgress,
                    customLists = state.customLists,
                    onToggleManyInList = viewModel::toggleManyInList,
                    onCreateList = viewModel::createList,
                    autoCloseAddToList = state.autoCloseAddToList,
                    customMoodRules = state.customMoodRules,
                    topFocusRequester = moviesTopFocusRequester,
                    query = state.moviesSearchQuery,
                    onQueryChange = viewModel::setMoviesSearchQuery,
                )
                Tab.SERIES -> SeriesScreen(
                    series = state.series,
                    favorites = state.favorites,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpen = { viewModel.openSeriesDetail(it) },
                    onDownload = { viewModel.requestSeriesSeasonDownload(it) },
                    globalSearchEnabled = globalSearchEnabled,
                    onToggleGlobalSearch = { globalSearchEnabled = it },
                    allLive = state.liveChannels,
                    allMovies = state.movies,
                    onPlayChannel = onPlayChannel,
                    onOpenMovie = { viewModel.openMovieDetail(it) },
                    watchProgress = state.watchProgress,
                    onResumeWatching = onResumeProgress,
                    onRemoveWatching = { viewModel.removeWatchProgress(it.channelOrEpisodeId) },
                    customLists = state.customLists,
                    onToggleManyInList = viewModel::toggleManyInList,
                    onCreateList = viewModel::createList,
                    autoCloseAddToList = state.autoCloseAddToList,
                    customMoodRules = state.customMoodRules,
                    topFocusRequester = seriesTopFocusRequester,
                    query = state.seriesSearchQuery,
                    onQueryChange = viewModel::setSeriesSearchQuery,
                )
                Tab.FOLDER -> LocalFolderScreen(
                    folders = state.localFolders,
                    videos = state.localVideos,
                    isScanning = state.isScanningLocalFolders,
                    errors = state.localFolderErrors,
                    onAddFolder = onAddLocalFolder,
                    onRemoveFolder = viewModel::removeLocalFolder,
                    onRescan = viewModel::rescanLocalFolders,
                    onPlayVideo = onPlayLocalVideo,
                    // Tylko postęp plików LOKALNYCH (streamUrl zaczyna się od "content://")
                    // — patrz ten sam filtr, tym razem odwrócony, w GridScreens.kt (Filmy).
                    continueWatching = state.watchProgress.values.filter { it.streamUrl.startsWith("content://") },
                    onResumeWatching = onResumeProgress,
                    onRemoveWatching = { viewModel.removeWatchProgress(it.channelOrEpisodeId) },
                    topFocusRequester = folderTopFocusRequester,
                )
                Tab.SETTINGS -> SettingsScreen(
                    currentLang = state.language,
                    onLangSelected = viewModel::setLanguage,
                    autoCloseAddToList = state.autoCloseAddToList,
                    onAutoCloseAddToListChanged = viewModel::setAutoCloseAddToList,
                    playerDebugOverlay = state.playerDebugOverlay,
                    onPlayerDebugOverlayChanged = viewModel::setPlayerDebugOverlay,
                    customMoodRules = state.customMoodRules,
                    onAddCustomMoodRule = viewModel::addCustomMoodRule,
                    onRemoveCustomMoodRule = viewModel::removeCustomMoodRule,
                    allCategoriesLive = state.allCategoriesLive,
                    allCategoriesMovie = state.allCategoriesMovie,
                    allCategoriesSeries = state.allCategoriesSeries,
                    hiddenCategories = state.hiddenCategories,
                    onToggleCategory = viewModel::toggleCategoryHidden,
                    onSetAllCategoriesHidden = viewModel::setAllCategoriesHidden,
                    onApplyCategoryPrefilter = viewModel::applyCategoryPrefilter,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    playlists = state.playlists,
                    playlistStatuses = state.playlistStatuses,
                    onManagePlaylists = { showPlaylistsManager = true },
                    onOpenDownloads = { showDownloads = true },
                    topFocusRequester = settingsTopFocusRequester,
                    onOpenRegulamin = { showLegalDoc = com.iptvplayer.app.data.LegalDocType.REGULAMIN },
                    onOpenPolityka = { showLegalDoc = com.iptvplayer.app.data.LegalDocType.POLITYKA },
                    onOpenConsents = { showConsents = true },
                    isFullVersionPurchased = billingState.isPurchased,
                    onShowPurchaseConfirmation = { viewModel.openPurchaseConfirmationScreen() },
                    onRefreshPurchases = { viewModel.refreshPurchases() },
                )
            }
        }

        // Baner reklamowy (sekcja 9) — pasek nad dolną nawigacją, widoczny na WSZYSTKICH
        // zakładkach przeglądania (Home/Filmy/Seriale/Live/Folder/Ustawienia), czyli
        // dokładnie "ekranach przeglądania" z sekcji 9. Nigdy w PlayerActivity (osobna
        // Activity) i nigdy na żadnej nakładce pełnoekranowej (szczegóły serialu/filmu,
        // EPG grid, Pobrane, zarządzanie playlistami, dokumenty prawne) — te mają wcześniej
        // `return` powyżej w tej funkcji, więc kod niżej w ogóle się dla nich nie wykonuje.
        // No-op w wariancie `noads`, patrz com.iptvplayer.app.ads.AdsController.
        com.iptvplayer.app.ads.AdsController.BannerAdStrip(adsTrialExpired, adsIsPurchased)

        IptvBottomNav(
            current = currentTab,
            onSelect = selectTab,
            focusRequester = bottomNavFocusRequester,
            // Na razie podłączone tylko dla Live (patrz liveTopFocusRequester wyżej) —
            // konkretny, zgłoszony przypadek ("lista programów"). Pozostałe zakładki po
            // prostu nie mają jeszcze swojego celu (null), więc strzałka DÓŁ z paska
            // nawigacji nic dla nich nie robi, tak jak dotychczas.
            // Podłączone dla Home/Filmy/Seriale/Live — patrz komentarz przy deklaracji tych
            // FocusRequesterów wyżej (Moje/Ustawienia celowo pominięte, brak naturalnego
            // celu + krótsze listy).
            // Spójnie dla wszystkich sześciu zakładek — Moje i Ustawienia dołączone na
            // wyraźne życzenie (wcześniej pominięte, brak naturalnego pola wyszukiwania,
            // ale każde ma teraz WŁASNY sposób zakotwiczenia — patrz komentarze przy
            // folderTopFocusRequester/settingsTopFocusRequester wyżej).
            downFocusRequester = when (currentTab) {
                Tab.HOME -> homeTopFocusRequester
                Tab.MOVIES -> moviesTopFocusRequester
                Tab.SERIES -> seriesTopFocusRequester
                Tab.LIVE -> liveTopFocusRequester
                Tab.FOLDER -> folderTopFocusRequester
                Tab.SETTINGS -> settingsTopFocusRequester
            },
        )
    }

    // --- ekran ładowania przy pierwszym starcie appki — bez tego czarny/pusty ekran (dopóki
    // katalog się nie wczyta z cache ani z sieci) wyglądał na zawieszony, nie na "coś się
    // dzieje" (zgłoszenie: kilka-kilkanaście sekund czarnego ekranu na TV). Znika, gdy tylko
    // POJAWI SIĘ jakakolwiek treść — z cache ALBO z sieci, co pierwsze — nie czeka na PEŁNE
    // zakończenie odświeżenia (to i tak leci dalej w tle niezależnie). Warunek playlists.
    // isNotEmpty() celowo: użytkownik BEZ żadnej skonfigurowanej playlisty (pierwsze
    // uruchomienie appki) ma zobaczyć normalny, pusty Home z zachętą do dodania playlisty,
    // nie wieczny spinner czekający na dane, których i tak nikt nie zlecił pobrać.
    val showInitialLoadingSplash = state.playlists.isNotEmpty() &&
        state.liveChannels.isEmpty() && state.movies.isEmpty() && state.series.isEmpty() &&
        state.isLoading
    if (showInitialLoadingSplash) {
        Box(
            Modifier.fillMaxSize().background(Bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Logo appki na środku, z obracającym się pierścieniem WOKÓŁ niego (nie
                // osobno obok) — zgłoszenie: samo logo na dużym ekranie TV wyglądało na
                // zbyt małe/statyczne, brakowało wizualnego sygnału, że appka faktycznie
                // coś robi, nie tylko "wisi". CircularProgressIndicator BEZ podanego progress
                // to wariant "nieokreślony" — kręci się w kółko sam z siebie, bez potrzeby
                // ręcznego animowania.
                Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = Accent,
                        strokeWidth = 3.dp,
                    )
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.mipmap.ic_launcher_round),
                        contentDescription = null,
                        modifier = Modifier.size(62.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(tr("root.initialLoading"), color = TextDim, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    }

    // --- toast "wczytywanie…" przy pierwszym kliknięciu pobierania sezonu (gdy sezony
    // jeszcze nie są znane, trzeba je najpierw doładować przez get_series_info) ---
    var wasLoadingSeasonDownload by remember { mutableStateOf(false) }
    // tr() jest @Composable i nie może być wołane bezpośrednio wewnątrz LaunchedEffect
    // (suspend lambda, nie kontekst kompozycji) — dlatego tłumaczenia liczymy TUTAJ,
    // w ciele RootScreen, i tylko gotowe Stringi przechwytujemy w LaunchedEffect niżej.
    val seasonLoadingToastText = tr("root.seasonDownload.loadingToast")
    val seasonLoadFailedToastText = tr("root.seasonDownload.loadFailedToast")
    LaunchedEffect(state.isLoadingSeasonDownload) {
        if (state.isLoadingSeasonDownload && !wasLoadingSeasonDownload) {
            Toast.makeText(context, seasonLoadingToastText, Toast.LENGTH_SHORT).show()
        } else if (!state.isLoadingSeasonDownload && wasLoadingSeasonDownload && state.seasonDownloadTarget == null) {
            Toast.makeText(context, seasonLoadFailedToastText, Toast.LENGTH_LONG).show()
        }
        wasLoadingSeasonDownload = state.isLoadingSeasonDownload
    }

    // --- dialog wyboru sezonu do pobrania — wywoływany z przycisku pobierania na plakacie
    // serialu (siatka Series, Home, ekran szczegółów); patrz AppViewModel.requestSeriesSeasonDownload ---
    val seasonTarget = state.seasonDownloadTarget
    if (seasonTarget != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeSeasonDownloadPicker() },
            title = { Text(tr("root.seasonDownload.title", seasonTarget.name)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    seasonTarget.seasons.sortedBy { it.number }.forEach { season ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDownloadSeriesSeason(seasonTarget, season.number)
                                    viewModel.closeSeasonDownloadPicker()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tr("root.seasonDownload.seasonLabel", season.number),
                                color = TextMain,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                tr("root.seasonDownload.episodeCount", season.episodes.size),
                                color = TextDim,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closeSeasonDownloadPicker() }) {
                    Text(tr("common.cancel"))
                }
            },
        )
    }
}

/** Pokazywany, gdy użytkownik próbuje użyć funkcji zablokowanej po zakończeniu okresu
 * próbnego (wyszukiwanie, Moje listy, pobieranie) — patrz AppViewModel.requestPremiumAccess.
 * Samo przeglądanie/oglądanie filmów, seriali i kanałów na żywo NIGDY nie pokazuje tego
 * dialogu — zostaje w pełni dostępne nawet po zakończeniu próby, celowo. */
@Composable
private fun PurchasePromptDialog(onBuy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("trial.expiredTitle")) },
        // Wariant z reklamami (trial.expiredBodyAds) — sekcja 9, wydanie `ads` (patrz
        // com.iptvplayer.app.ads.AdsController).
        text = { Text(tr(if (com.iptvplayer.app.BuildConfig.FLAVOR == "ads") "trial.expiredBodyAds" else "trial.expiredBody")) },
        confirmButton = {
            // Otwiera ekran "przed zakupem" (sekcja 4, patrz PrePurchaseScreen.kt) —
            // launchPurchaseFlow wołane jest DOPIERO stamtąd, po zaznaczeniu obu pól.
            TextButton(onClick = onBuy) { Text(tr("trial.buyButton")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("trial.notNowButton")) }
        },
    )
}

// Dawny WithdrawalWaiverScreen (ekran "Zanim zaczniesz") został USUNIĘTY — zastąpiony przez
// OnboardingScreen (patrz ui/screens/OnboardingScreen.kt), wołany w RootScreen wyżej.
