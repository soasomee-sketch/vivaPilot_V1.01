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

/** Skok przewijania na jedno (krótkie) naciśnięcie strzałki lewo/prawo na pilocie (VOD; Live nie przewija). */
private const val SEEK_STEP_MS = 10_000L

/** Próg czasowy, po którym przytrzymanie strzałki lewo/prawo jest traktowane jako żądanie
 * zmiany na następny/poprzedni materiał zamiast zwykłego przewinięcia. */
private const val LONG_PRESS_MS = 450L

/** Próg przesunięcia (w px), po którym swipe w bok jest traktowany jako zmiana materiału. */
private const val SWIPE_THRESHOLD_DP = 90

/** Ile sekund trwa podgląd/potwierdzenie "następny/poprzedni materiał" (styl Netflix) zanim
 * automatycznie się zatwierdzi — w tym czasie można anulować i zostać przy bieżącym materiale. */
private const val SWITCH_CONFIRM_SECONDS = 5

/** Ile milisekund bez ŻADNEGO ruchu pozycji odtwarzania (mimo że silnik zgłasza się jako
 * odtwarzający) uznajemy za zacięcie się strumienia i wywołujemy automatyczne ponowne
 * połączenie — patrz "watchdog zacinania" w PlayerScreen. 10 sekund dobrane jako środek: na
 * tyle krótko, żeby zacięcie naprawiło się szybko, ale na tyle długo, żeby nie wywołać
 * niepotrzebnego reconnectu przy zwykłym, krótkim buforowaniu (chwilowy spadek prędkości
 * łącza), które samo się naprawia w kilka sekund. */
private const val STALL_RECOVERY_THRESHOLD_MS = 10_000L

/** Co ile sekund (ticków pętli odświeżania) zapisywać postęp "Kontynuuj oglądanie" —
 * patrz persistProgress w PlayerScreen. */
private const val PROGRESS_SAVE_EVERY_TICKS = 10

/** Cyfra z klawisza pilota (rząd cyfr albo klawiatura numeryczna), null dla innych klawiszy. */
private fun remoteDigitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

/**
 * Pełnoekranowy odtwarzacz — odpowiednik #playerOverlay z wersji HTML: kontrolki auto-hide,
 * pasek postępu z przewijaniem, przełącznik ścieżek audio/napisów, zapamiętywanie pozycji,
 * a dodatkowo nawigacja do następnego/poprzedniego materiału (swipe w bok, długie przytrzymanie
 * strzałek na pilocie, oraz automatyczne przejście dalej po zakończeniu materiału) — patrz
 * PlayQueue.kt po regułę, co jest "następne" dla danego rodzaju materiału.
 */
class PlayerActivity : ComponentActivity() {

    private lateinit var playerManager: VlcPlayerManager
    private lateinit var store: LocalStore
    private val hideHandler = Handler(Looper.getMainLooper())

    // Czy system w ogóle obsługuje PiP na tym urządzeniu — sprawdzane raz, używane zarówno
    // do pokazania/ukrycia przycisku PiP w Compose, jak i do decyzji czy próbować auto-PiP
    // przy wyjściu z appki (onUserLeaveHint). Większość boxów Android TV zgłasza to jako
    // false (PiP nie ma tam większego sensu — telewizor to już jest "duży ekran"), więc
    // przycisk sam się nie pojawi na TV bez potrzeby osobnego sprawdzania isTvDevice() tutaj.
    private var pipSupported = false

    // Aktualny stan PiP, czytany przez Compose (patrz PlayerScreen) — `by mutableStateOf`
    // działa tu identycznie jak w @Composable: każda zmiana tej właściwości (nawet
    // wywołana z callbacku cyklu życia Activity, POZA drzewem Compose) wywoła rekompozycję
    // wszystkiego, co ją czyta. W PiP Compose chowa WSZYSTKIE nakładki (pasek góra/dół,
    // dialogi) — system sam rysuje własne minimalne kontrolki nad małym okienkiem PiP.
    private var isInPipMode by mutableStateOf(false)

    // Lustrzane odbicie stanu odtwarzania po stronie Activity (Compose ma własne `isPlaying`,
    // ale to jest OSOBNY stan, potrzebny TUTAJ, poza drzewem Compose, żeby wiedzieć, jaką
    // ikonę play/pauza pokazać na przycisku w oknie PiP — patrz buildPipParams). Zwykła
    // właściwość Kotlina z własnym setterem (NIE mutableStateOf — nic w Compose jej wprost
    // nie czyta), która przy każdej zmianie sama odświeża widoczne akcje PiP, jeśli akurat
    // jesteśmy w PiP; poza PiP nic nie robi (setPictureInPictureParams w PiP nie jest
    // potrzebne, a poza API 26 nawet nie istnieje).
    private var lastKnownIsPlaying: Boolean = true
        set(value) {
            field = value
            if (isInPipMode) updatePipParams()
            updateMediaSessionState()
        }

    // Metadane bieżącej pozycji do powiadomienia/MediaSession (tytuł + plakat) — Compose
    // zgłasza zmiany przez onMediaMetadataChanged (patrz PlayerScreen), tak samo jak
    // lastKnownIsPlaying zgłasza zmiany stanu odtwarzania. Osobne od lastKnownIsPlaying,
    // bo zmieniają się w INNYCH momentach (przy zmianie materiału, nie przy pauzie).
    private var currentMediaTitle: String = ""
    private var currentMediaPositionMs: Long = 0L
    private var currentMediaDurationMs: Long = 0L

    private lateinit var mediaSession: MediaSessionCompat

    /**
     * Kontrolki na ekranie blokady i słuchawkach Bluetooth (play/pauza/przewijanie) —
     * świadomie w UPROSZCZONYM wariancie: sesja i powiadomienie żyją w cyklu życia SAMEJ
     * Activity (tworzone w onCreate, sprzątane w onDestroy), NIE jako pełny foreground
     * Service. Oznacza to: kontrolki działają, dopóki PlayerActivity żyje (czyli podczas
     * zwykłego oglądania i w PiP) — ale dźwięk NIE przetrwa po całkowitym opuszczeniu
     * appki (co i tak jest zgodne z resztą architektury, patrz onStop -> finish()).
     * Pełny foreground Service (odtwarzanie audio w tle PO zamknięciu appki) to
     * świadomie osobne, większe rozszerzenie na przyszłość, nie robione teraz.
     */
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "VivaPilotPlayerSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playerManager.resume()
                    lastKnownIsPlaying = true
                }
                override fun onPause() {
                    playerManager.pause()
                    lastKnownIsPlaying = false
                }
                override fun onSeekTo(pos: Long) {
                    playerManager.seekTo(pos)
                    currentMediaPositionMs = pos
                    updateMediaSessionState()
                }
            })
            isActive = true
        }
    }

    /** Odświeża stan PlaybackStateCompat (play/pauza/pozycja) w sesji I treść powiadomienia —
     * wołane przy KAŻDEJ zmianie stanu odtwarzania lub metadanych (patrz settery/callbacki
     * wyżej), żeby ekran blokady zawsze pokazywał aktualny stan, nie ten sprzed chwili. */
    private fun updateMediaSessionState() {
        if (!::mediaSession.isInitialized) return
        val state = if (lastKnownIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO,
                )
                .setState(state, currentMediaPositionMs, if (lastKnownIsPlaying) 1f else 0f)
                .build(),
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMediaTitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentMediaDurationMs)
                .build(),
        )
        showMediaNotification(state)
    }

    /** Buduje i pokazuje powiadomienie w stylu MediaStyle. Przyciski w nakładce wołają
     * prosto do mediaSession przez MediaButtonReceiver (androidx.media) — sprawdzone,
     * gotowe rozwiązanie zamiast pisania kolejnego własnego BroadcastReceivera (mamy już
     * jeden do PiP, nie ma sensu duplikować tej samej idei drugi raz w tym samym pliku). */
    private fun showMediaNotification(playbackState: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existing == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "VivaPilot",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "VivaPilot — kontrolki odtwarzania" },
                )
            }
        }

        val playPauseAction = if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                trFor(store.getLanguage(), "player.pip.pause"),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE),
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                trFor(store.getLanguage(), "player.pip.play"),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY),
            )
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.iptvplayer.app.R.mipmap.ic_launcher)
            .setContentTitle(currentMediaTitle)
            .setOnlyAlertOnce(true)
            .setOngoing(playbackState == PlaybackStateCompat.STATE_PLAYING)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, PlayerActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        // Brak zgody na powiadomienia (API 33+) — po prostu nie pokazujemy nic, kontrolki
        // ekranu blokady zwyczajnie nie zadziałają, ale reszta appki działa normalnie dalej.
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    /**
     * Odbiera dotknięcia przycisków w NAKŁADCE systemowej okna PiP (cofnij 10s / play-pauza /
     * do przodu 10s — patrz buildPipParams). Rejestrowana z `RECEIVER_NOT_EXPORTED` i
     * intencjami ograniczonymi do własnego pakietu (`.setPackage(packageName)` w
     * buildPipParams) — inne aplikacje nie mogą jej ani zobaczyć, ani nic do niej wysłać, więc
     * to bezpieczne, mimo że technicznie jest to "zwykły" BroadcastReceiver.
     */
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(receivedContext: Context, receivedIntent: Intent) {
            if (!::playerManager.isInitialized) return
            when (receivedIntent.action) {
                ACTION_PIP_PLAY_PAUSE -> {
                    if (playerManager.mediaPlayer.isPlaying) playerManager.pause() else playerManager.resume()
                    // Odczyt PO zmianie (nie odwrócenie starej wartości) — pewniejsze, bo
                    // odzwierciedla to, co odtwarzacz FAKTYCZNIE teraz robi, a nie to, co
                    // zakładaliśmy, że powinien robić.
                    lastKnownIsPlaying = playerManager.mediaPlayer.isPlaying
                }
                ACTION_PIP_SEEK_BACK -> {
                    val target = (playerManager.mediaPlayer.time - SEEK_STEP_MS).coerceAtLeast(0)
                    playerManager.seekTo(target)
                }
                ACTION_PIP_SEEK_FORWARD -> {
                    val duration = playerManager.mediaPlayer.length
                    val proposed = playerManager.mediaPlayer.time + SEEK_STEP_MS
                    val target = if (duration > 0) proposed.coerceAtMost(duration) else proposed
                    playerManager.seekTo(target)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.iptvplayer.app.data.CrashLogger.breadcrumb("player", "onCreate")
        // Blokada wygaszania/usypiania ekranu na CZAS ODTWARZANIA — bez tego telefon usypiał
        // ekran po swoim standardowym czasie bezczynności DOTYKU, mimo że wideo cały czas
        // leciało (system nie ma jak wiedzieć, że użytkownik "coś robi" tylko dlatego, że
        // patrzy). Krytyczny błąd zgłoszony przez użytkownika — obecny od samego początku,
        // appka nigdy nie ustawiała tej flagi. Wystarczy ustawić raz tutaj — flaga jest
        // przypisana do OKNA tej konkretnej Activity, więc sama przestaje obowiązywać, gdy
        // Activity się zamyka/chowa (nic nie trzeba osobno "wyłączać" w onStop/onDestroy).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()
        pipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        // Telewizor fizycznie się nie obraca — tam odtwarzacz zawsze zostaje poziomy, tak jak
        // dotychczas (dawne android:screenOrientation="sensorLandscape" w manifeście). Telefon/
        // tablet dostaje pełną orientację, więc film da się teraz oglądać też w pionie —
        // ustawiane programowo (a nie sztywno w manifeście), właśnie żeby móc to rozróżnić.
        requestedOrientation = if (applicationContext.isTvDevice()) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }

        playerManager = VlcPlayerManager(this)
        store = LocalStore(this)
        setupMediaSession()

        // RECEIVER_NOT_EXPORTED: odbiornik ma słuchać WYŁĄCZNIE naszych własnych intencji
        // (patrz buildPipParams — każda ma .setPackage(packageName)), nigdy z zewnątrz innej
        // aplikacji. Wymagane jawnie od API 33 (inaczej appka wywala się przy starcie), a
        // ContextCompat samo dobiera poprawne zachowanie dla starszych wersji Androida.
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_PLAY_PAUSE)
                addAction(ACTION_PIP_SEEK_BACK)
                addAction(ACTION_PIP_SEEK_FORWARD)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        @Suppress("DEPRECATION")
        val queue = (intent.getSerializableExtra(EXTRA_QUEUE) as? NavQueue)
            ?.takeIf { it.items.isNotEmpty() }
            ?: run { finish(); return }
        val forceStartFromZero = intent.getBooleanExtra(EXTRA_FORCE_START_FROM_ZERO, false)

        setContent {
            IptvPlayerTheme {
                // Osobna Activity = osobne drzewo Compose, więc CompositionLocalProvider
                // z MainActivity tu NIE sięga — trzeba dostarczyć LocalLang jeszcze raz,
                // tym razem czytając zapisany język wprost z LocalStore tej Activity.
                androidx.compose.runtime.CompositionLocalProvider(
                    com.iptvplayer.app.i18n.LocalLang provides store.getLanguage(),
                ) {
                    PlayerScreen(
                        initialQueue = queue,
                        forceStartFromZero = forceStartFromZero,
                        playerManager = playerManager,
                        store = store,
                        onBack = { finish() },
                        pipSupported = pipSupported,
                        isInPipMode = isInPipMode,
                        onEnterPip = { enterPip() },
                        onPlayingStateChanged = { lastKnownIsPlaying = it },
                        onMediaMetadataChanged = { title, positionMs, durationMs ->
                            currentMediaTitle = title
                            currentMediaPositionMs = positionMs
                            currentMediaDurationMs = durationMs
                            updateMediaSessionState()
                        },
                    )
                }
            }
        }
    }

    /**
     * Aktywność przestała być w ogóle widoczna — dzieje się to przy zamknięciu okna PiP
     * (krzyżyk/przeciągnięcie), przy zwykłym wyjściu z appki bez PiP (np. Home na
     * urządzeniu bez wsparcia PiP) oraz przy cofnięciu/finish(). NIE dzieje się przy samym
     * WEJŚCIU w PiP — tam aktywność zostaje w stanie "started", cały czas widoczna, tylko
     * w mniejszym oknie — więc to nigdy nie ubija odtwarzania w momencie zwinięcia do PiP.
     *
     * Wcześniej samo mediaPlayer.stop() wystarczało, żeby uciszyć dźwięk, ale zostawiało
     * CAŁY odtwarzacz (razem z sesją sprzętowego dekodera wideo) żywy aż do onDestroy() —
     * a Android NIE gwarantuje, kiedy to wywoła (może zwlekać kilka-kilkanaście sekund,
     * albo dłużej, jeśli aktywność zostanie przywrócona z powrotem zanim system zdąży ją
     * zniszczyć). To dawało DRUGI, poważniejszy błąd: jeśli użytkownik zdążył odpalić NOWY
     * film, zanim stary odtwarzacz faktycznie się zwolnił, dwie sesje dekodera próbowały
     * działać jednocześnie — większość urządzeń wspiera ich naraz tylko jedną-dwie, więc
     * nowy odtwarzacz dostawał czarny ekran (dźwięk czasem grał, obrazu brak).
     *
     * finish() wymusza natychmiastowe, deterministyczne przejście do onDestroy() (a więc i
     * do pełnego playerManager.release()) zamiast czekać, aż system sam zdecyduje się
     * posprzątać — usuwa całą niepewność co do TEGO, kiedy zasoby faktycznie się zwolnią.
     */
    override fun onStop() {
        super.onStop()
        com.iptvplayer.app.data.CrashLogger.breadcrumb("player", "onStop")
        // Pełne zwolnienie odtwarzacza TUTAJ (nie dopiero w onDestroy() niżej) — wcześniej
        // trzymałem to w onDestroy(), bo release() wołane od razu w onStop() mogło (zanim
        // Compose zdążył anulować pętlę zapisu postępu) sprawić, że ta pętla odpytywała już
        // zwolniony, natywny odtwarzacz — psując zapis "Kontynuuj oglądanie". TERAZ pętla
        // zapisu (LaunchedEffect w PlayerScreen) ma własne zabezpieczenie (`runCatching`
        // wokół odczytu pozycji/duration/isPlaying) — jedna nieudana iteracja PO release()
        // po prostu się pomija, zamiast psuć cokolwiek. Dzięki temu można bezpiecznie
        // wrócić do WCZEŚNIEJSZEGO powodu trzymania release() właśnie tutaj: onStop()
        // ZAWSZE poprzedza onDestroy(), czasem zauważalnie wcześniej — a w tej luce czasowej
        // stara instancja libVLC mogła wciąż żyć, podczas gdy użytkownik zdążył już otworzyć
        // COŚ INNEGO, co tworzy WŁASNĄ, DRUGĄ instancję libVLC (np. grid EPG z podglądem
        // wideo, patrz EpgGridScreen.kt) — podejrzewany udział w zgłoszonych awariach appki
        // przy Live zaraz po oglądaniu.
        playerManager.release()
        // Powiadomienie z kontrolkami traci sens w momencie, gdy odtwarzacz i tak zaraz
        // zniknie (patrz finish() niżej) — bez tego zostałoby "zawieszone" z martwymi
        // przyciskami do czasu, aż system sam je usunie.
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) }
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        com.iptvplayer.app.data.CrashLogger.breadcrumb("player", "onDestroy")
        // Wyrejestrowanie odbiornika przycisków PiP — pominięcie tego to wyciek (odbiornik
        // przypięty do martwej Activity) i potencjalny crash przy próbie ponownego
        // zarejestrowania go w nowej instancji tej samej klasy dla kolejnego materiału.
        runCatching { unregisterReceiver(pipActionReceiver) }
        if (::mediaSession.isInitialized) mediaSession.release()
        // playerManager.release() NIE woła się już tutaj drugi raz — dzieje się w onStop()
        // wyżej, które zawsze poprzedza to wywołanie. Podwójne zwolnienie tych samych
        // natywnych zasobów libVLC to osobne, własne ryzyko awarii.
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // System potrafi przywrócić paski nawigacji/statusu po powrocie fokusu okna
        // (np. po zaciągnięciu powiadomienia) — trzeba je schować ponownie. W PiP okno
        // jest małe i nie ma własnych pasków systemowych do chowania, więc pomijamy.
        if (hasFocus && !isInPipMode) enterImmersiveMode()
    }

    /**
     * Automatyczne wejście w PiP, gdy użytkownik "wychodzi" z appki (przycisk Home, przełączenie
     * na inną appkę, gest przywołania ostatnich aplikacji) zamiast po prostu zatrzymywać
     * odtwarzanie w tle — dokładnie to, o co chodzi w "zminimalizuj, nie zamykaj". Wywoływane
     * przez system automatycznie, więc pilot na telewizorze (gdzie i tak nie ma "przycisku
     * Home" prowadzącego tu w ten sposób) nigdy realnie go nie odpali.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    /**
     * Wejście w tryb obrazu-w-obrazie — wywoływane zarówno automatycznie (onUserLeaveHint),
     * jak i ręcznie z przycisku PiP w kontrolkach odtwarzacza (patrz PlayerScreen), oraz
     * podwójnym tapnięciem na wideo. Stały format 16:9 zamiast rzeczywistych wymiarów
     * strumienia — libVLC nie wystawia ich tu w prosty sposób, a 16:9 i tak pokrywa
     * zdecydowaną większość treści VOD/Live; dla innych proporcji system sam dorysuje wąskie
     * czarne pasy w oknie PiP, nic się nie psuje.
     *
     * PiP jest ZAWSZE wolne, niezależnie od okresu próbnego/zakupu — patrz TrialGate.kt.
     */
    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !pipSupported) return
        try {
            enterPictureInPictureMode(buildPipParams(lastKnownIsPlaying))
        } catch (e: Exception) {
            // Część urządzeń (niektóre boxy Android TV, appki uruchomione w oknie
            // wielozadaniowości, albo PiP wyłączony ręcznie w ustawieniach systemu dla tej
            // appki) zgłasza obsługę PiP przez PackageManager, a mimo to odrzuca wejście —
            // to nie powinno wywalać odtwarzacza, więc po prostu zostajemy w normalnym widoku.
        }
    }

    /** Odświeża ikony/etykiety przycisków w JUŻ otwartym oknie PiP (np. po zmianie
     * play<->pauza) — bez tego przycisk pokazywałby stary stan aż do zamknięcia i ponownego
     * otwarcia PiP. Bezpieczne wołanie w każdym momencie: no-op poniżej API 26 albo gdy PiP
     * nie jest wspierane, a próba ustawienia parametrów PiP poza samym PiP też jest
     * nieszkodliwa (system po prostu zapamiętuje je na następne wejście). */
    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !pipSupported) return
        try {
            setPictureInPictureParams(buildPipParams(lastKnownIsPlaying))
        } catch (e: Exception) {
            // Tak samo jak w enterPip() — pojedyncze urządzenia potrafią to odrzucić mimo
            // zgłoszonej obsługi PiP, nie ma powodu wywalać dla tego całego odtwarzacza.
        }
    }

    /**
     * Buduje parametry okna PiP wraz z trzema własnymi przyciskami w jego nakładce: cofnij
     * 10s / play-pauza (ikona zależna od aktualnego stanu) / do przodu 10s — to jest ta
     * "bezpieczna, zawsze działająca" wersja sterowania w PiP: RemoteAction + PendingIntent +
     * BroadcastReceiver to oficjalne, udokumentowane API Androida do tego celu (nie próba
     * przechwytywania dotyku na samym okienku PiP, które należy do systemu, nie do appki, i
     * nie da się tego zrobić niezawodnie). Ikony z android.R.drawable — systemowe, obecne na
     * każdym urządzeniu z Androidem, więc zero ryzyka brakującego zasobu.
     */
    private fun buildPipParams(isPlayingNow: Boolean): PictureInPictureParams {
        val lang = store.getLanguage()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun action(requestCode: Int, iconRes: Int, titleKey: String, action: String) = RemoteAction(
            Icon.createWithResource(this, iconRes),
            trFor(lang, titleKey),
            trFor(lang, titleKey),
            PendingIntent.getBroadcast(this, requestCode, Intent(action).setPackage(packageName), flags),
        )

        val actions = listOf(
            action(1, android.R.drawable.ic_media_rew, "player.pip.seekBack", ACTION_PIP_SEEK_BACK),
            action(
                2,
                if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlayingNow) "player.pip.pause" else "player.pip.play",
                ACTION_PIP_PLAY_PAUSE,
            ),
            action(3, android.R.drawable.ic_media_ff, "player.pip.seekForward", ACTION_PIP_SEEK_FORWARD),
        )

        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
            .build()
    }

    /**
     * Pełny tryb immersyjny: chowa pasek statusu i pasek nawigacji (w tym przyciski
     * boczne widoczne w orientacji poziomej), a użytkownik może je tymczasowo przywołać
     * przeciągnięciem od krawędzi ekranu — wtedy system chowa je znów automatycznie.
     */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        /** Jedyny ekstra potrzebny do uruchomienia odtwarzacza — cała kolejka nawigacji
         * (bieżąca pozycja + następne/poprzednie), patrz PlayQueue.kt. */
        const val EXTRA_QUEUE = "extra_queue"

        /** Opcjonalny ekstra — jeśli true, PIERWSZY odtwarzany element w tej kolejce
         * pomija automatyczne wznowienie z zapisanej pozycji i startuje od zera, mimo że
         * zapisany postęp dla niego istnieje. NIE kasuje zapisanego postępu (patrz
         * PlayerScreen.forceStartFromZero) — to bezpieczna, jednorazowa alternatywa dla
         * przycisku "Odtwórz od początku" w szczegółach filmu/serialu, obok "Kontynuuj
         * oglądanie" (zgłoszenie: był tylko jeden przycisk, który zawsze wznawiał, bez
         * możliwości świadomego startu od zera przy istniejącym postępie). Dotyczy TYLKO
         * pierwszego elementu — przełączanie na kolejne pozycje w tej samej sesji (np.
         * następny odcinek) dalej normalnie sprawdza WŁASNY zapisany postęp tamtej pozycji. */
        const val EXTRA_FORCE_START_FROM_ZERO = "extra_force_start_from_zero"

        // Akcje broadcastów dla przycisków w nakładce okna PiP (patrz buildPipParams i
        // pipActionReceiver) — nazwy sklejone z packageName na końcu, żeby nie kolidowały z
        // niczym systemowym ani z inną aplikacją mającą podobny pomysł na nazewnictwo.
        private const val ACTION_PIP_PLAY_PAUSE = "com.iptvplayer.app.PIP_PLAY_PAUSE"
        private const val ACTION_PIP_SEEK_BACK = "com.iptvplayer.app.PIP_SEEK_BACK"
        private const val ACTION_PIP_SEEK_FORWARD = "com.iptvplayer.app.PIP_SEEK_FORWARD"

        // Powiadomienie z kontrolkami odtwarzania (patrz setupMediaSession/showMediaNotification).
        private const val NOTIFICATION_CHANNEL_ID = "playback_controls"
        private const val NOTIFICATION_ID = 1001
    }
}

/**
 * Otwiera bieżący strumień w zewnętrznej aplikacji VLC (jeśli zainstalowana) przez ACTION_VIEW —
 * to NIE jest to samo co wbudowany silnik libVLC używany domyślnie w PlayerScreen; tu po prostu
 * przekazujemy URL do osobnej, samodzielnie zainstalowanej appki VLC. Jeśli VLC nie jest
 * zainstalowane, kierujemy do Play Store zamiast wywalać wyjątek.
 */
private fun openInExternalVlc(context: Context, streamUrl: String, title: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(streamUrl), "video/*")
        setPackage("org.videolan.vlc")
        putExtra("title", title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Dla plików lokalnych (content://…) trzeba jawnie przekazać uprawnienie odczytu
        // zewnętrznej aplikacji — bez tej flagi VLC dostałby URI, do którego nie ma dostępu.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.videolan.vlc"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Oczekująca zmiana na następny/poprzedni materiał — podgląd z odliczaniem, który
 * użytkownik może anulować (patrz NextUpOverlay). `auto` odróżnia przejście wywołane
 * końcem materiału od ręcznego swipe'a/przytrzymania strzałki (na razie tylko do ew.
 * przyszłego rozróżnienia etykiety, wizualnie ten sam podgląd). */
private data class PendingSwitch(
    val direction: Int, // +1 = następny, -1 = poprzedni
    val targetQueue: NavQueue,
    val targetIndex: Int,
    val auto: Boolean,
)

/** Dominujący kierunek przeciągnięcia w gestach na ekranie odtwarzacza — ustalany RAZ, przy
 * pierwszym zauważalnym przesunięciu palca (patrz pointerInput w PlayerScreen), i trzymany
 * do końca gestu, żeby skośne przeciągnięcie nie "przeskakiwało" w połowie między zmianą
 * materiału (poziomo) a jasnością/głośnością (pionowo). */
private enum class GestureDirection { HORIZONTAL, VERTICAL }

/** Stan wizualnego feedbacku podczas gestu jasność/głośność — `isVolume=false` to lewa
 * połowa ekranu (jasność), `isVolume=true` to prawa (głośność multimediów). `level` to
 * ułamek 0f..1f aktualnej wartości, używany zarówno do wypełnienia paska jak i etykiety %. */
private data class GestureFeedback(val isVolume: Boolean, val level: Float)

/**
 * Krok w kolejce nawigacji o `direction` (+1/-1) względem `index` w `queue`. Jeśli lądujemy
 * na pozycji-serialu z listy użytkownika (patrz NavQueue.seriesSubQueues), od razu zwracamy
 * podmienioną kolejkę wskazującą na odcinki tego serialu — dalsza nawigacja porusza się już
 * tylko po nich (serial zawsze odtwarza kolejny odcinek, bez powrotu do listy).
 */
private fun stepQueue(queue: NavQueue, index: Int, direction: Int): Pair<NavQueue, Int>? {
    val newIndex = index + direction
    if (newIndex !in queue.items.indices) return null
    val landedEntry = queue.items[newIndex]
    val sub = queue.seriesSubQueues[landedEntry.id]
    return if (sub != null) {
        val episodesQueue = NavQueue(QueueKind.SERIES_EPISODES, sub.episodes, sub.startIndex)
        episodesQueue to sub.startIndex
    } else {
        queue to newIndex
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerScreen(
    initialQueue: NavQueue,
    // Patrz PlayerActivity.EXTRA_FORCE_START_FROM_ZERO — konsumowane JEDNORAZOWO w
    // LaunchedEffect(current.id, viewAttached) niżej, tylko dla pierwszego odtwarzanego
    // elementu tej sesji.
    forceStartFromZero: Boolean = false,
    playerManager: VlcPlayerManager,
    store: LocalStore,
    onBack: () -> Unit,
    pipSupported: Boolean,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    // Zgłasza do Activity KAŻDĄ zmianę stanu odtwarzania — Activity trzyma sobie kopię
    // (lastKnownIsPlaying), żeby wiedzieć, jaką ikonę play/pauza pokazać na przycisku w
    // nakładce okna PiP (patrz PlayerActivity.buildPipParams) — to dzieje się POZA drzewem
    // Compose, więc samo `isPlaying` tutaj nie wystarczy, Activity potrzebuje własnej kopii.
    onPlayingStateChanged: (Boolean) -> Unit = {},
    // Zgłasza do Activity zmiany tytułu/pozycji/czasu trwania — do treści powiadomienia z
    // kontrolkami odtwarzania (MediaSession). Ten sam wzorzec bridgingu co
    // onPlayingStateChanged wyżej: Activity żyje POZA drzewem Compose, więc potrzebuje
    // własnej kopii tych wartości, nie może po prostu "podejrzeć" stanu composable.
    onMediaMetadataChanged: (title: String, positionMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // --- kolejka nawigacji: co jest "bieżące" oraz co jest następne/poprzednie ---
    var activeQueue by remember { mutableStateOf(initialQueue) }
    var activeIndex by remember {
        mutableStateOf(initialQueue.startIndex.coerceIn(0, initialQueue.items.lastIndex.coerceAtLeast(0)))
    }
    val current = activeQueue.items[activeIndex]
    val hasNext = stepQueue(activeQueue, activeIndex, 1) != null
    val hasPrev = stepQueue(activeQueue, activeIndex, -1) != null

    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    LaunchedEffect(isPlaying) { onPlayingStateChanged(isPlaying) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    // Celowo NIE kluczowane po positionMs (tykałoby co sekundę, zbędnie przebudowując
    // powiadomienie) — system sam animuje upływ czasu w MediaStyle na podstawie prędkości
    // odtwarzania zgłoszonej w PlaybackStateCompat, nie trzeba raportować każdej sekundy z
    // osobna. durationMs w kluczu odświeża powiadomienie drugi raz, gdy realny czas trwania
    // faktycznie się pozna (zaczyna od 0, dopóki odtwarzacz go nie zwróci).
    LaunchedEffect(current.id, durationMs) { onMediaMetadataChanged(current.title, positionMs, durationMs) }
    // Pozycja podglądu PODCZAS przeciągania suwaka postępu — patrz komentarz przy Slider()
    // niżej (dlaczego to jest osobny stan, a nie bezpośrednie seekTo() na każdą zmianę).
    // null = użytkownik akurat nie przeciąga, suwak pokazuje prawdziwe positionMs.
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    var viewAttached by remember { mutableStateOf(false) }

    // Tryb dopasowania obrazu do ekranu (FIT/FILL) — patrz VlcPlayerManager.setScaleMode.
    // videoViewSizePx to rozmiar w pikselach RZECZYWISTEGO okna wideo (VLCVideoLayout, patrz
    // AndroidView niżej z Modifier.onSizeChanged), potrzebny do wyliczenia proporcji przy
    // przełączeniu na FILL — bez znajomości aktualnych wymiarów okna (które zmieniają się np.
    // przy obrocie ekranu) FILL nie miałby względem czego rozciągać obrazu.
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var videoViewSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // Zgłoszenie: po wejściu w PiP i powrocie obraz "osadzał się" dziwnie (np. na dole
    // ekranu), a wybrany wcześniej tryb rozciągnięcia się resetował. Przyczyna: PiP (i
    // obrót ekranu) zmienia RZECZYWISTY rozmiar okna wideo, ale scaleMode/setScaleMode
    // wcześniej wołane były WYŁĄCZNIE ręcznie, po naciśnięciu przycisku — więc libVLC
    // zostawał z proporcją wyliczoną dla STAREGO rozmiaru okna, niepasującą już do
    // nowego. To ten sam mechanizm tłumaczy zgłoszenie "przycisk czasem rozciąga tylko na
    // szerokość" — FILL liczy proporcję z aktualnego videoViewSizePx, więc jeśli ten
    // rozmiar akurat był nieaktualny w momencie przełączenia (np. tuż po obrocie ekranu,
    // zanim zdążył się zaktualizować), rozciągnięcie wychodziło tylko częściowo poprawne.
    // Naprawa: ponowne zastosowanie trybu PRZY KAŻDEJ zmianie rozmiaru okna, nie tylko przy
    // ręcznym przełączeniu — FIT i FILL są teraz zawsze zsynchronizowane z aktualnym,
    // prawdziwym rozmiarem, niezależnie od tego, co go zmieniło (PiP, obrót, cokolwiek
    // innego w przyszłości).
    LaunchedEffect(videoViewSizePx, scaleMode) {
        if (videoViewSizePx.width > 0 && videoViewSizePx.height > 0) {
            playerManager.setScaleMode(scaleMode, videoViewSizePx.width, videoViewSizePx.height)
        }
    }

    // Podgląd/potwierdzenie zmiany materiału (styl Netflix "Następny odcinek za Xs") —
    // uruchamiane zarówno ręcznie (swipe/przytrzymanie strzałki), jak i automatycznie po
    // zakończeniu bieżącego materiału (patrz pętla odświeżania paska postępu niżej).
    var pendingSwitch by remember { mutableStateOf<PendingSwitch?>(null) }

    fun requestSwitch(direction: Int, auto: Boolean) {
        if (pendingSwitch != null) return
        val (q, idx) = stepQueue(activeQueue, activeIndex, direction) ?: return
        pendingSwitch = PendingSwitch(direction, q, idx, auto)
    }

    fun commitSwitch(p: PendingSwitch) {
        activeQueue = p.targetQueue
        activeIndex = p.targetIndex
        pendingSwitch = null
        isPlaying = true
        positionMs = 0L
        durationMs = 0L
    }

    fun cancelSwitch() {
        pendingSwitch = null
    }

    // Auto-hide kontrolek po 4s bezczynności — jak w wersji web. `hideTrigger` pozwala
    // zrestartować odliczanie przy KAŻDEJ interakcji pilota (nie tylko dotykiem), nawet
    // jeśli kontrolki są już widoczne (samo ustawienie controlsVisible=true na true
    // ponownie nie wywołałoby LaunchedEffect od nowa, dlatego osobny licznik).
    // Zadeklarowane TUTAJ (przed jumpToQueueIndex/browserRowFocused niżej), bo w
    // przeciwieństwie do metod klasy, lokalne funkcje w Kotlinie NIE widzą się nawzajem
    // "w przód" — jumpToQueueIndex wywołuje showControls(), więc showControls() musi być
    // zadeklarowane wcześniej w tym samym bloku (błąd kompilacji potwierdził to wprost:
    // "Unresolved reference: showControls").
    var hideTrigger by remember { mutableStateOf(0) }
    fun showControls() {
        controlsVisible = true
        hideTrigger++
    }

    // Przełącznik dopasowania obrazu (FIT/FILL) — zadeklarowany PO showControls() z tego
    // samego powodu co jumpToQueueIndex niżej: wywołuje showControls(), więc showControls()
    // musi być zadeklarowane wcześniej w tym samym bloku.
    fun toggleScaleMode() {
        // setScaleMode NIE jest już wołane bezpośrednio tutaj — LaunchedEffect(videoViewSizePx,
        // scaleMode) wyżej robi to automatycznie za każdym razem, gdy zmieni się KTÓRYKOLWIEK
        // z tych dwóch (tryb ALBO rozmiar okna) — patrz obszerny komentarz przy jego deklaracji.
        scaleMode = if (scaleMode == VideoScaleMode.FIT) VideoScaleMode.FILL else VideoScaleMode.FIT
        showControls()
    }

    /**
     * Stosuje zapamiętaną preferencję języka audio/napisów na WŁAŚNIE rozpoczętym materiale —
     * wołane z playerManager.onPlaybackStarted (patrz LaunchedEffect(Unit) niżej), czyli
     * dopiero gdy libVLC faktycznie zdążył sparsować plik i zna dostępne ścieżki (od razu po
     * play() lista byłaby jeszcze pusta). Dopasowanie PO NAZWIE ścieżki (nie numerze) — numery
     * są przypadkowe i inne w każdym pliku, ale nazwa typu "Polski"/"Polish" zwykle powtarza
     * się między różnymi filmami od tego samego dostawcy.
     */
    fun applyTrackPreferences() {
        // Napisy: domyślnie WYŁĄCZONE, nawet jeśli plik źródłowy deklaruje jakąś ścieżkę jako
        // domyślną/wymuszoną (libVLC potrafi samo ją włączyć) — appka to świadomie nadpisuje,
        // chyba że użytkownik sam kiedyś wybrał konkretny język napisów.
        val subtitlesEnabled = store.getSubtitlesEnabled()
        if (!subtitlesEnabled) {
            playerManager.setSubtitleTrack(-1)
        } else {
            val preferredSub = store.getPreferredSubtitleLanguage()
            if (preferredSub != null) {
                playerManager.subtitleTracks()
                    .firstOrNull { it.name?.contains(preferredSub, ignoreCase = true) == true }
                    ?.let { playerManager.setSubtitleTrack(it.id) }
            }
        }
        // Audio: jeśli mamy zapamiętany język, spróbuj znaleźć pasującą ścieżkę — jeśli nie
        // ma dopasowania (np. film w ogóle nie ma takiego języka), zostaje to, co libVLC
        // wybrało samo (zwykle pierwsza/domyślna ścieżka), appka niczego nie wymusza na siłę.
        val preferredAudio = store.getPreferredAudioLanguage()
        if (preferredAudio != null) {
            playerManager.audioTracks()
                .firstOrNull { it.name?.contains(preferredAudio, ignoreCase = true) == true }
                ?.let { playerManager.setAudioTrack(it.id) }
        }
    }

    // Podłączenie applyTrackPreferences() do zdarzenia "playback faktycznie ruszył" — patrz
    // VlcPlayerManager.onPlaybackStarted. LaunchedEffect(Unit) zamiast bezpośredniego
    // przypisania w ciele funkcji: to samo Composable wywołuje się ponownie przy każdej
    // rekompozycji, ale przypisanie ma się wykonać raz, nie za każdym razem.
    // Znacznik "appka sama wykryła zawieszenie strumienia i właśnie łączy się ponownie" —
    // patrz obszerny komentarz przy "watchdogu zacinania" niżej (pętla odświeżania paska
    // postępu). Czyszczone razem z applyTrackPreferences w onPlaybackStarted, bo to
    // dokładnie ten sam moment ("libVLC faktycznie zaczął odtwarzać ponownie").
    var isReconnecting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        playerManager.onPlaybackStarted = { applyTrackPreferences(); isReconnecting = false }
    }

    // Znacznik "trwa doskakiwanie do zapamiętanej pozycji" — zgłoszenie: bez żadnej
    // wskazówki wznowienie (które teraz może potrwać kilka sekund, patrz
    // waitForStableDurationAndSeek w VlcPlayerManager) wyglądało jak "zawieszenie się" i
    // kusiło do naciskania przycisków w międzyczasie. mainHandler nie jest tu potrzebny —
    // VlcPlayerManager już woła ten callback z głównego wątku.
    var isCalibratingResume by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        playerManager.onCalibratingChanged = { isCalibratingResume = it }
    }

    // Fokus na poziomej "półce" z kolejką (patrz QueueBrowserRow niżej) — gdy pilot jest
    // akurat w niej, strzałki lewo/prawo mają nawigować MIĘDZY plakatami, a NIE przewijać/
    // zmieniać materiału jak zwykle (patrz wyjątek w globalnej obsłudze klawiszy niżej).
    var browserRowFocused by remember { mutableStateOf(false) }

    // TEN SAM wyjątek co wyżej, ale dla przycisków górnego/dolnego paska (powrót, pobierz,
    // timer, PiP, dopasowanie, zamknij, ścieżka audio, otwórz w VLC) — zgłoszenie: pilotem
    // dawało się dotrzeć TYLKO do przycisków osiąganych JEDNYM naciśnięciem góra/dół z
    // domyślnego punktu play/pauza (powrót, ścieżka audio), bo WEWNĄTRZ każdego paska
    // (poruszanie się lewo/prawo MIĘDZY przyciskami) strzałki były zawsze przechwytywane
    // pod globalne przewijanie ±10s, zanim standardowy mechanizm fokusu Compose zdążył
    // przesunąć zaznaczenie do sąsiedniego przycisku (patrz jawne left/right w
    // chainedFocus() niżej — bez tego wyjątku te połączenia nigdy nie miały szansy
    // zadziałać). Ustawiane przez chainedFocus() dla górnego paska oraz ręcznie dla
    // audiotrackFR/vlcFR w dolnym pasku.
    var barButtonFocused by remember { mutableStateOf(false) }

    // Ręczny wybór DOWOLNEJ pozycji z kolejki (tap/OK na plakacie w QueueBrowserRow) —
    // w odróżnieniu od requestSwitch/commitSwitch (o jedną pozycję, z podglądem do
    // anulowania) przełącza NATYCHMIAST, bez odliczania — to już jest świadomy wybór
    // widza, nie ma czego potwierdzać. Tak jak stepQueue(), obsługuje też wylądowanie na
    // pozycji-serialu z listy użytkownika (zamiana na podkolejkę odcinków).
    fun jumpToQueueIndex(index: Int) {
        if (index !in activeQueue.items.indices) return
        if (index == activeIndex) {
            showControls()
            return
        }
        pendingSwitch = null
        val landedEntry = activeQueue.items[index]
        val sub = activeQueue.seriesSubQueues[landedEntry.id]
        if (sub != null) {
            activeQueue = NavQueue(QueueKind.SERIES_EPISODES, sub.episodes, sub.startIndex)
            activeIndex = sub.startIndex
        } else {
            activeIndex = index
        }
        isPlaying = true
        positionMs = 0L
        durationMs = 0L
        showControls()
    }

    // --- Pilot: przełączanie kanałów cyframi, Kanał+/Kanał−, poprzedni kanał ---
    // Standard z telewizji (i z TiviMate): wpisanie 1-2-5 na pilocie przełącza na kanał
    // numer 125 w bieżącej grupie. Po ostatniej cyfrze appka czeka 1,5 s (można dopisać
    // kolejną), a na ekranie widać wpisywany numer i nazwę kanału docelowego.
    var channelDigits by remember { mutableStateOf("") }
    LaunchedEffect(channelDigits) {
        if (channelDigits.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500)
        val target = channelDigits.toIntOrNull()?.minus(1)
        channelDigits = ""
        if (target != null && target in activeQueue.items.indices) jumpToQueueIndex(target)
    }
    // Poprzedni kanał (przycisk "Last/Prev channel" na pilocie albo ikona w górnym pasku) —
    // śledzimy identyfikator poprzednio oglądanej pozycji, nie numer, bo kolejka potrafi
    // się zmienić (np. wejście w serial z listy użytkownika).
    var previousEntryId by remember { mutableStateOf<String?>(null) }
    var lastSeenEntryId by remember { mutableStateOf(current.id) }
    LaunchedEffect(current.id) {
        if (current.id != lastSeenEntryId) {
            previousEntryId = lastSeenEntryId
            lastSeenEntryId = current.id
        }
    }
    fun jumpToPreviousChannel(): Boolean {
        val prevId = previousEntryId ?: return false
        val idx = activeQueue.items.indexOfFirst { it.id == prevId }
        if (idx < 0) return false
        jumpToQueueIndex(idx)
        return true
    }
    /** Kanał+ / Kanał− — dla Live przełącza OD RAZU (jak w telewizorze), bez 5-sekundowego
     * podglądu "Następny za…", który ma sens dla odcinków seriali, ale nie przy zappingu. */
    fun zapChannel(direction: Int) {
        val (q, idx) = stepQueue(activeQueue, activeIndex, direction) ?: return
        if (current.kind == MediaKind.LIVE && q === activeQueue) jumpToQueueIndex(idx)
        else requestSwitch(direction = direction, auto = false)
    }

    // Okno wyboru ścieżki dźwięku/napisów — patrz TrackPickerDialog niżej.
    var showTracksDialog by remember { mutableStateOf(false) }
    var trackSelectionTick by remember { mutableStateOf(0) }

    // Nakładka diagnostyczna (Ustawienia -> Log błędów -> "Nakładka diagnostyczna w
    // odtwarzaczu") — celowo NIE zgaduję już czwarty raz przyczyny przeskakujących
    // plakatów/znikającego paska postępu przez samą analizę kodu. Ta nakładka pokazuje na
    // ŻYWO durationMs, controlsVisible i zmierzoną wysokość dolnego paska (patrz
    // bottomBarHeightPx niżej, mierzona przez onSizeChanged) — przy kolejnym zrzucie
    // ekranu z tym błędem będą na nim WIDOCZNE realne wartości wewnętrznego stanu w tym
    // dokładnym momencie, zamiast konieczności zgadywania z samego wyglądu.
    val playerDebugOverlayEnabled = remember { store.getPlayerDebugOverlay() }
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    var topBarHeightPx by remember { mutableStateOf(0) }

    // "Teraz leci" / "Dalej" dla oglądanego na żywo kanału — pokazywane w górnym pasku pod
    // tytułem, żeby nie trzeba było wychodzić z pełnego ekranu, żeby to sprawdzić. TYLKO dla
    // Live (VOD nie ma "teraz leci") i TYLKO gdy kanał faktycznie ma źródło EPG. Celowo
    // lżejsze niż usunięty wcześniej dialog EPG (ikona "i") — ten pobierał CAŁY dzień
    // programu (getTodayPrograms) i to on powodował zgłoszone awarie; tu tylko DWA konkretne
    // programy (getNowPlaying/getNextProgram), ten sam, sprawdzony sposób co w wierszu
    // kanału na liście Live (LiveChannelRow), plus okresowe odświeżanie co minutę — bez
    // niego napis zostałby przy pierwszym sprawdzonym programie na czas całej sesji
    // oglądania, nawet gdy w międzyczasie realnie leci już coś innego.
    val epgRepository = remember { EpgRepository() }
    var nowPlayingProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var nextProgram by remember { mutableStateOf<EpgProgram?>(null) }
    LaunchedEffect(current.id) {
        val epgUrl = current.epgUrl
        val chId = current.epgChannelId
        if (current.kind != MediaKind.LIVE || epgUrl == null || chId == null) {
            nowPlayingProgram = null
            nextProgram = null
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                nowPlayingProgram = runCatching { epgRepository.getNowPlaying(epgUrl, chId) }.getOrNull()
                nextProgram = runCatching { epgRepository.getNextProgram(epgUrl, chId) }.getOrNull()
            }
            kotlinx.coroutines.delay(60_000)
        }
    }

    // Zamknięcie CAŁEJ aplikacji wprost z pełnoekranowego odtwarzacza (przycisk "X" w
    // górnym pasku) — bez tego jedyny sposób to Wstecz kilka razy z powrotem przez listę
    // kanałów/ekran główny. Ten sam wzorzec potwierdzenia co na Home (home.exit.*) — celowo
    // ten sam tekst w tłumaczeniach, żeby dwa miejsca w appce robiące dokładnie to samo nie
    // brzmiały inaczej.
    var showExitConfirm by remember { mutableStateOf(false) }

    // Punkty fokusu pilota: cały ekran (żeby ZAWSZE łapać klawisze, nawet gdy kontrolki
    // są schowane) oraz przycisk play/pauza (żeby po pokazaniu kontrolek fokus od razu
    // na czymś sensownym wylądował, zamiast "zgubić się" pilotowi).
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocusRequester.requestFocus() } }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) runCatching { playPauseFocusRequester.requestFocus() }
    }

    LaunchedEffect(controlsVisible, hideTrigger) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(4000)
            controlsVisible = false
        }
    }

    var showCastDialog by remember { mutableStateOf(false) }
    // Ostrzeżenie o braku szyfrowania przy "Wyślij do TV" (sekcja 8 specyfikacji zgodności
    // prawnej z 09.10) — pokazywane TYLKO przy pierwszym użyciu tej funkcji na urządzeniu
    // (patrz LocalStore.hasAcknowledgedCastWarning niżej), potem już nie przeszkadza.
    var showCastWarningDialog by remember { mutableStateOf(false) }
    // Blokada ekranu — zapobiega przypadkowym dotknięciom (np. appka w kieszeni/torbie, albo
    // dziecko obok). Odblokowanie: długie przytrzymanie w DOWOLNYM miejscu ekranu (patrz
    // pełnoekranowa nakładka niżej), albo pojedyncze dotknięcie pokazuje mały przycisk
    // odblokowania, który sam znika po chwili, jeśli nieużyty.
    var isScreenLocked by remember { mutableStateOf(false) }
    var showLockUnlockHint by remember { mutableStateOf(false) }
    // Czas naciśnięcia OK na pilocie przy zablokowanym ekranie — patrz obsługa klawiszy
    // (root onKeyEvent) niżej, ten sam wzorzec co inne długie przytrzymania w projekcie.
    var lockKeyPressStartMs by remember { mutableStateOf(0L) }
    // Zgłoszenie: dodaj możliwość ręcznego wpisania docelowego czasu przy długim
    // przytrzymaniu przycisków -10s/+30s — patrz Box z combinedClickable/onKeyEvent przy
    // tych przyciskach niżej, i sam dialog CustomSeekDialog na końcu pliku.
    var showCustomSeekDialog by remember { mutableStateOf(false) }


    // --- gest jasność (lewa połowa ekranu) / głośność multimediów (prawa połowa) ---
    // Activity samego PlayerActivity jest jednocześnie Contextem tego Composable, więc rzutowanie
    // wystarcza — nie trzeba osobno przekazywać referencji do okna z zewnątrz.
    val activity = remember(context) { context as? android.app.Activity }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    // Widoczny tylko podczas przeciągania — czyszczony na null w onDragEnd/onDragCancel
    // gestu niżej, więc sam "znika automatycznie po puszczeniu" bez osobnego timera.
    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }

    fun adjustBrightness(deltaFraction: Float) {
        val act = activity ?: return
        val lp = act.window.attributes
        // BRIGHTNESS_OVERRIDE_NONE (-1f) oznacza "appka jeszcze nie ustawiała własnej
        // jasności, ekran idzie za jasnością systemową" — pierwszy gest w tej sesji
        // startuje wtedy od połowy zakresu, zamiast robić nieprzewidywalny "skok" od
        // nieznanej wartości systemowej.
        val current = if (lp.screenBrightness in 0f..1f) lp.screenBrightness else 0.5f
        val updated = (current + deltaFraction).coerceIn(0.02f, 1f)
        lp.screenBrightness = updated
        act.window.attributes = lp
        gestureFeedback = GestureFeedback(isVolume = false, level = updated)
    }

    fun adjustVolume(deltaFraction: Float) {
        val max = audioManager
            .getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            .coerceAtLeast(1)
        val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val updatedFraction = (current.toFloat() / max + deltaFraction).coerceIn(0f, 1f)
        val updatedVolume = (updatedFraction * max).roundToInt().coerceIn(0, max)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, updatedVolume, 0)
        gestureFeedback = GestureFeedback(isVolume = true, level = updatedVolume.toFloat() / max)
    }

    // Uruchamia odtwarzanie za każdym razem, gdy zmieni się bieżąca pozycja w kolejce
    // (nawigacja next/prev) — wznawia od zapisanej pozycji, jeśli materiał był już oglądany.
    // WYJĄTEK: forceStartFromZero (patrz parametr wyżej) — jeśli true, PIERWSZE odtworzenie
    // w tej sesji pomija sprawdzenie zapisanej pozycji i startuje od zera, mimo że taka
    // pozycja istnieje. Flaga jest "konsumowana" (ustawiana na false) zaraz po użyciu —
    // dotyczy WYŁĄCZNIE pierwszego elementu, każde kolejne przełączenie w tej samej sesji
    // (np. następny odcinek) z powrotem normalnie sprawdza WŁASNY zapisany postęp.
    var forceStartFromZero by remember { mutableStateOf(forceStartFromZero) }
    // Widoczne w nakładce diagnostycznej niżej — patrz breadcrumb tuż pod spodem. To NIE
    // trafi do logu awarii (breadcrumb zapisuje się na dysk tylko przy prawdziwym crashu),
    // więc dla błędu, który nie crashuje appki, potrzebny jest osobny, widoczny na żywo ślad.
    var resumeDebugInfo by remember { mutableStateOf("") }
    LaunchedEffect(current.id, viewAttached) {
        if (!viewAttached) return@LaunchedEffect
        val resumeMs = if (forceStartFromZero) {
            forceStartFromZero = false
            0L
        } else {
            store.getWatchProgress()[current.id]?.positionMillis ?: 0L
        }
        // Okruszek diagnostyczny — zgłoszenie: "Kontynuuj oglądanie" w serialach odtwarza od
        // początku, mimo że mechanizm wygląda poprawnie w całym kodzie (sprawdzone: budowanie
        // ID odcinka, zapis postępu, dopasowanie w SeriesDetailScreen, odświeżanie stanu po
        // powrocie z odtwarzacza). Pokazuje dokładnie jakiego ID appka szukała i czy
        // COKOLWIEK znalazła, więc przy następnym teście (z włączoną nakładką diagnostyczną,
        // patrz niżej) będzie wiadomo, czy to brak dopasowania ID, czy zapisana wartość 0,
        // czy coś innego.
        val found = store.getWatchProgress().containsKey(current.id)
        resumeDebugInfo = "resumeId=${current.id} forceZero=$forceStartFromZero znaleziono=$found resumeMs=$resumeMs"
        com.iptvplayer.app.data.CrashLogger.breadcrumb("resume", resumeDebugInfo)
        playerManager.play(current.streamUrl, current.userAgent, current.referer, resumeMs)
    }

    // Pętla odświeżania paska postępu + zapis WatchProgress dla BIEŻĄCEJ pozycji w kolejce
    // (nie tej, z którą odtwarzacz został pierwotnie otwarty — stąd zależność od current.id).
    // WAŻNE: delay() jest na KOŃCU pętli (nie na początku) — pierwszy zapis postępu leci
    // NATYCHMIAST po podłączeniu widoku, a nie dopiero po pełnej sekundzie. Wcześniej
    // delay(1000) na starcie pętli oznaczał, że jeśli ktoś wyszedł z odtwarzacza w mniej niż
    // sekundę od otwarcia (albo playerManager.durationMs() jeszcze przez chwilę zwracał 0,
    // zanim strumień się w pełni załadował), żaden zapis nigdy nie zdążył się wykonać —
    // "Kontynuuj oglądanie" wtedy wyglądało, jakby nic nie było oglądane, mimo że realnie
    // się oglądało. Stąd zgłoszenie "po jakimś czasie zaczęło działać" — działało dopiero,
    // gdy ktoś zostawał w odcinku wystarczająco długo, żeby złapać PIERWSZY tick starej pętli.
    // "Watchdog zacinania" — zgłoszenie: zarówno na żywo, jak i w filmach, po ok. 3-5
    // minutach materiał zacina się i nie leci dalej, mimo że appka wygląda, jakby dalej
    // "grała" (żaden komunikat błędu, żadne zamknięcie) — jedynym sposobem na przywrócenie
    // obrazu było ręczne zamknięcie odtwarzacza i otworzenie od nowa w tym samym miejscu,
    // po czym znów działało przez kolejne kilka minut. To ZNANY, udokumentowany problem
    // biblioteki libVLC-Android przy strumieniach IPTV (TS) — silnik potrafi "zawiesić" się
    // wewnętrznie (wątek dekodera przestaje odbierać dane po nieciągłości w strumieniu),
    // nadal zgłaszając się jako odtwarzający, ale bez żadnego postępu pozycji. Nie ma
    // oficjalnej naprawy tego po stronie samej biblioteki — jedyne niezawodne obejście to
    // dokładnie to, co robi ręcznie użytkownik: zamknąć i otworzyć ponownie ten sam strumień
    // od ostatniej znanej pozycji. Ten watchdog robi to SAM, automatycznie, więc użytkownik
    // nie musi już ręcznie tego robić za każdym razem.
    var lastMovingPositionMs by remember(current.id) { mutableStateOf(0L) }
    var lastMovingAtMs by remember(current.id) { mutableStateOf(System.currentTimeMillis()) }
    var reconnectTriggeredAtMs by remember(current.id) { mutableStateOf(0L) }
    // Ile razy TEN materiał już się zacinał — każde kolejne zacięcie dostaje "mocniejsze"
    // ponowne połączenie (większy bufor, a od trzeciego razu dekodowanie programowe), zamiast
    // w kółko powtarzać dokładnie te same ustawienia, które już raz zawiodły.
    var stallCount by remember(current.id) { mutableStateOf(0) }
    // Ostatnia sensowna (niezerowa) pozycja bieżącego materiału — w trakcie ponownego
    // łączenia silnik chwilowo zgłasza 0, więc wznowienie i zapis korzystają z tej wartości.
    var lastGoodPositionMs by remember(current.id) { mutableStateOf(0L) }
    // To samo dla czasu trwania — przy przejściu do następnego odcinka stan jest zerowany
    // ZANIM zapisze się postęp poprzedniego, więc bez tego obejrzany odcinek zapisywał się
    // z nieznanym czasem trwania i wracał do "Kontynuuj oglądanie" jako nieobejrzany.
    var lastGoodDurationMs by remember(current.id) { mutableStateOf(0L) }
    // Próby z rzędu BEZ powrotu obrazu (np. dłuższa awaria sieci) — odstęp między próbami
    // rośnie (3 s, 6 s, 12 s, 24 s, maks. 30 s), żeby nie "zasypywać" serwera dostawcy
    // zapytaniami (część dostawców blokuje wtedy konto na chwilę) i nie drenować baterii.
    // Zerowane, gdy tylko obraz znowu ruszy.
    var consecutiveFailedReconnects by remember(current.id) { mutableStateOf(0) }
    var nextReconnectAllowedAtMs by remember(current.id) { mutableStateOf(0L) }
    // Silnik zgłasza brak danych w buforze — kółko ładowania pokazywane dopiero, gdy stan
    // trwa dłużej niż ułamek sekundy (patrz showBufferingSpinner), żeby nie migało.
    var isBuffering by remember { mutableStateOf(false) }
    var showBufferingSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            kotlinx.coroutines.delay(700)
            showBufferingSpinner = true
        } else {
            showBufferingSpinner = false
        }
    }

    /** Jedno, wspólne miejsce ponownego łączenia ze strumieniem — wołane przez watchdog
     * zacinania, zdarzenia błędu/urwania strumienia z silnika i powrót sieci. Zwraca false,
     * jeśli próba została pominięta (za wcześnie po poprzedniej — patrz backoff wyżej). */
    fun reconnectStream(reason: String, ignoreBackoff: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!ignoreBackoff && now < nextReconnectAllowedAtMs) return false
        stallCount += 1
        consecutiveFailedReconnects += 1
        val backoffMs = (3_000L shl (consecutiveFailedReconnects - 1).coerceIn(0, 4)).coerceAtMost(30_000L)
        nextReconnectAllowedAtMs = now + backoffMs
        // Bufor: 1. zacięcie = bazowy +50%, 2. = x2, 3.+ = x2.5 (maks. 15 s).
        val base = playerManager.baseNetworkCachingMs
        val cachingMs = (base * (100 + 50 * stallCount.coerceAtMost(3)) / 100).coerceAtMost(15_000)
        val softwareDecoding = stallCount >= 3
        val resumeMs = if (current.kind == MediaKind.LIVE) 0L else maxOf(positionMs, lastGoodPositionMs)
        com.iptvplayer.app.data.CrashLogger.breadcrumb(
            "stall",
            "reconnect($reason) nr=$stallCount zRzedu=$consecutiveFailedReconnects bufor=${cachingMs}ms sw=$softwareDecoding id=${current.id} pos=$resumeMs",
        )
        isReconnecting = true
        reconnectTriggeredAtMs = now
        runCatching {
            playerManager.play(
                current.streamUrl, current.userAgent, current.referer, resumeMs,
                networkCachingMs = cachingMs,
                forceSoftwareDecoding = softwareDecoding,
            )
        }
        lastMovingPositionMs = positionMs
        lastMovingAtMs = now
        return true
    }

    // Podpięcie sygnałów z silnika — per materiał (current.id), żeby funkcja
    // reconnectStream zawsze operowała na BIEŻĄCYM materiale kolejki.
    LaunchedEffect(current.id) {
        playerManager.onBufferingChanged = { isBuffering = it }
        playerManager.onStreamProblem = { reason ->
            val nearRealEnd = current.kind != MediaKind.LIVE && run {
                // Koniec materiału VOD przy KOŃCU filmu to normalne zakończenie (dalej
                // działa zwykłe przejście do następnego odcinka) — reconnect tylko, gdy
                // strumień urwał się wyraźnie PRZED końcem albo długość jest nieznana
                // (wtedy nie da się odróżnić, więc bezpieczniej NIE wznawiać).
                val total = if (durationMs > 0) durationMs else playerManager.impliedDurationMs()
                total <= 0L || positionMs >= total - 15_000L
            }
            val justReconnected = isReconnecting && System.currentTimeMillis() - reconnectTriggeredAtMs < 4_000L
            if (!playerManager.isUserPaused && !nearRealEnd && !justReconnected) {
                reconnectStream(reason)
            }
        }
        playerManager.onNetworkRestored = {
            // Sieć wróciła — jeśli obraz stoi (albo trwa już nieudane łączenie), wznawiamy
            // natychmiast, z pominięciem odstępu między próbami.
            val frozen = System.currentTimeMillis() - lastMovingAtMs > 2_000L
            if (!playerManager.isUserPaused && (frozen || isReconnecting)) {
                consecutiveFailedReconnects = 0
                reconnectStream("network", ignoreBackoff = true)
            }
        }
    }

    /** Zapis "Kontynuuj oglądanie" dla bieżącego materiału.
     *
     * Optymalizacja: wcześniej zapis leciał CO SEKUNDĘ — za każdym razem appka wczytywała i
     * zapisywała od nowa całą listę "Kontynuuj oglądanie" (JSON w pamięci ustawień), przez
     * całe oglądanie. Na słabszych przystawkach TV to niepotrzebne obciążenie procesora i
     * pamięci flash. Teraz: natychmiast po starcie, potem co 10 s, przy pauzie oraz przy
     * wyjściu/zmianie materiału (DisposableEffect niżej) — dokładność wznowienia zostaje
     * praktycznie ta sama (maks. kilka sekund różnicy), a zapisów jest ~10x mniej.
     *
     * Live: sam wpis "ostatnio oglądany kanał" (pozycja 0 — dla Live nie ma "miejsca do
     * wznowienia"), patrz resumeWatchProgress w MainActivity.kt. VOD: pozycja i czas
     * trwania (0 = nieznany — wszystkie miejsca czytające durationMillis sprawdzają > 0). */
    fun persistProgress() {
        if (current.kind == MediaKind.LIVE) {
            store.saveWatchProgress(
                current.id,
                WatchProgress(
                    channelOrEpisodeId = current.id,
                    positionMillis = 0L,
                    durationMillis = 0L,
                    updatedAt = System.currentTimeMillis(),
                    title = current.title,
                    posterUrl = current.posterUrl,
                    kind = current.kind,
                    streamUrl = current.streamUrl,
                    userAgent = current.userAgent,
                    referer = current.referer,
                    seriesId = current.seriesId,
                ),
            )
            return
        }
        // W trakcie doskakiwania do zapamiętanej pozycji nic nie zapisujemy — silnik zgłasza
        // wtedy jeszcze pozycję sprzed przewinięcia (okolice zera), co nadpisałoby dobre
        // miejsce wznowienia, gdyby ktoś wyszedł w tych pierwszych sekundach.
        if (isCalibratingResume) return
        val pos = if (positionMs > 0 && !isReconnecting) positionMs else lastGoodPositionMs
        val dur = if (durationMs > 0) durationMs else lastGoodDurationMs
        if (pos <= 0L) return
        if (dur <= 0 && pos <= 3000) return
        store.saveWatchProgress(
            current.id,
            WatchProgress(
                channelOrEpisodeId = current.id,
                positionMillis = pos,
                durationMillis = dur,
                updatedAt = System.currentTimeMillis(),
                title = current.title,
                posterUrl = current.posterUrl,
                kind = current.kind,
                streamUrl = current.streamUrl,
                userAgent = current.userAgent,
                referer = current.referer,
                seriesId = current.seriesId,
            ),
        )
    }
    // Zapis przy opuszczeniu materiału (następny odcinek, wyjście z odtwarzacza) — żeby
    // rzadszy zapis w pętli nie gubił ostatnich sekund oglądania.
    DisposableEffect(current.id) {
        onDispose { runCatching { persistProgress() } }
    }

    LaunchedEffect(current.id, viewAttached) {
        if (!viewAttached) return@LaunchedEffect
        var tick = 0
        var savedOnPause = false
        while (true) {
            // runCatching wokół odczytu z odtwarzacza — zabezpieczenie NA WYPADEK, gdyby
            // Activity zaczęła się zamykać w trakcie tej pętli (odtwarzacz zwalniany w
            // onDestroy, patrz PlayerActivity) i ta iteracja zdążyła odpalić się PO
            // zwolnieniu natywnych zasobów, zanim Compose zdążył anulować tę korutynę —
            // bez tego jedna nieudana iteracja mogła by przerwać całą pętlę (i tym samym
            // zapis "Kontynuuj oglądanie") zamiast po prostu pominąć TEN JEDEN odczyt.
            val snapshot = runCatching {
                Triple(
                    playerManager.currentPositionMs().coerceAtLeast(0),
                    playerManager.durationMs().coerceAtLeast(0),
                    playerManager.isPlaying(),
                )
            }.getOrNull()
            if (snapshot != null) {
                val (posMs, durMs, playing) = snapshot
                positionMs = posMs
                durationMs = durMs
                isPlaying = playing
                // Wykrywanie zacinania — patrz obszerny komentarz przy "watchdog zacinania"
                // przy deklaracji lastMovingAtMs wyżej. Celowo WYŁĄCZONE, gdy trwa już
                // doskakiwanie do zapamiętanej pozycji (isCalibratingResume) albo własne
                // ponowne łączenie (isReconnecting) — w obu przypadkach pozycja i tak
                // chwilowo "nie rusza się", ale to oczekiwane, nie zacięcie.
                //
                // ZMIENIONE — zgłoszenie: przy REALNYCH problemach z siecią (nie tylko
                // krótkim wewnętrznym zawieszeniu demuxera) watchdog wcześniej nie reagował
                // wcale, mimo obrazu zamrożonego na dłużej niż próg. Przyczyna: poprzednia
                // wersja pilnowała zacinania TYLKO, gdy `playing` (mediaPlayer.isPlaying(),
                // czyli chwilowy stan SILNIKA) było równe true — ale przy realnej utracie
                // połączenia sieciowego silnik często sam, bez pytania, przechodzi w stan
                // "nie gram" (buforowanie/wewnętrzna pauza), co poprzednia wersja mylnie
                // odczytywała jako "użytkownik zapauzował ręcznie" i w ogóle nie próbowała
                // niczego naprawiać — obraz zostawał zamrożony w nieskończoność, aż do
                // ręcznego zamknięcia appki. Naprawa: gate teraz opiera się na
                // playerManager.isUserPaused — PRAWDZIWEJ intencji użytkownika (patrz
                // obszerny komentarz przy tej właściwości w VlcPlayerManager.kt), a nie na
                // chwiejnym stanie silnika. Dzięki temu watchdog reaguje na zacięcie
                // NIEZALEŻNIE od tego, czy silnik akurat zgłasza się jako "gra", czy "nie
                // gra" — jedyne, co ma znaczenie, to czy POZYCJA faktycznie się posuwa.
                //
                // Zabezpieczenie: gdyby SAMO ponowne łączenie się nie powiodło (np. całkowita
                // utrata sieci — libVLC nigdy nie wyśle zdarzenia Playing, więc isReconnecting
                // normalnie ustawione by na stałe w onPlaybackStarted nigdy by się nie
                // wyczyściło) — po 15 sekundach bez sukcesu po prostu zwalniamy blokadę i
                // pozwalamy watchdogowi spróbować ponownie, zamiast zostawiać użytkownika w
                // nieskończoność z samym kółkiem ładowania na ekranie. Przy DŁUŻSZEJ awarii
                // sieci (dłuższej niż jeden cykl) oznacza to, że appka po prostu próbuje
                // ponownie co ~15 sekund, aż połączenie wróci — dokładnie to, czego
                // użytkownik by chciał, zamiast poddania się po jednej nieudanej próbie.
                if (isReconnecting && System.currentTimeMillis() - reconnectTriggeredAtMs > 15_000L) {
                    isReconnecting = false
                }
                if (!isCalibratingResume && !isReconnecting && !playerManager.isUserPaused) {
                    if (posMs != lastMovingPositionMs) {
                        lastMovingPositionMs = posMs
                        lastMovingAtMs = System.currentTimeMillis()
                        // Obraz znowu rusza — seria nieudanych prób się skończyła, więc
                        // następne ewentualne zacięcie znowu startuje od krótkiego odstępu.
                        consecutiveFailedReconnects = 0
                        nextReconnectAllowedAtMs = 0L
                    } else {
                        val stalledForMs = System.currentTimeMillis() - lastMovingAtMs
                        if (stalledForMs >= STALL_RECOVERY_THRESHOLD_MS) {
                            reconnectStream("stall ${stalledForMs}ms playing=$playing")
                        }
                    }
                } else if (playerManager.isUserPaused) {
                    // Użytkownik świadomie zapauzował — zegar wyzerowany, żeby wznowienie
                    // odtwarzania zawsze dostawało pełne, świeże okno przed kolejną
                    // ewentualną próbą naprawy zacinania.
                    lastMovingAtMs = System.currentTimeMillis()
                }
                // Zapis postępu — ROZLUŹNIONY względem poprzedniej wersji: nie wymaga już
                // durationMs > 0, żeby zapisać SAMĄ POZYCJĘ. Zrywanie połączenia sieciowego
                // (albo zwykłe wolne buforowanie) potrafi opóźnić zgłoszenie czasu trwania
                // przez libVLC — mediaPlayer.length (patrz VlcPlayerManager.durationMs())
                // zwraca 0, dopóki metadane strumienia się nie sparsują. Przy starym,
                // ściślejszym warunku ANI pasek postępu, ANI zapis do "Kontynuuj oglądanie"
                // nie działały dla takich materiałów przez CAŁĄ sesję odtwarzania, nawet
                // jeśli duration pojawiłoby się PÓŹNIEJ (zgłoszenia: "jak zerwało
                // połączenie sieciowe... film nie dodał się do kontynuowania", a osobno —
                // korelacja "nowy film ma pasek, przerwany bez zapisu nie ma"). Wszystkie
                // miejsca w appce CZYTAJĄCE durationMillis już bezpiecznie sprawdzają > 0
                // przed dzieleniem (sprawdzone całym projektem) — zapisanie 0 jako
                // "nieznany czas trwania" niczego nie psuje, po prostu ta pozycja nie
                // pokaże paska procentowego w "Kontynuuj oglądanie", ale wznowi się we
                // właściwym miejscu.
                //
                // Zgłoszenie: "Kontynuuj oglądanie" nie pokazywało WCALE ostatnio oglądanego
                // kanału na żywo (tylko filmy/seriale) — bo ta gałąź była wcześniej jawnie
                // wykluczona dla LIVE. Pozycja "na żywo" faktycznie stale rośnie i nie ma
                // sensu zapisywać jej jako "miejsce do wznowienia" (stąd positionMillis=0/
                // durationMillis=0 niżej — kafelek nie pokaże paska procentowego, a
                // resumeWatchProgress w MainActivity.kt i tak dla LIVE nie próbuje przewijać,
                // tylko od razu buduje kolejkę kanału na żywo), ALE sam WPIS o tym, że ten
                // kanał był ostatnio oglądany, jest dokładnie tym, czego brakowało.
                // Zapamiętanie ostatniej SENSOWNEJ pozycji — w trakcie ponownego łączenia silnik
                // chwilowo zgłasza 0 (nowy strumień dopiero startuje); bez tego zapis albo
                // kolejny reconnect mogłyby cofnąć "Kontynuuj oglądanie" na sam początek.
                if (posMs > 0 && !isReconnecting && !isCalibratingResume) lastGoodPositionMs = posMs
                if (durMs > 0) lastGoodDurationMs = durMs
                // Zapis postępu RZADZIEJ niż co sekundę — patrz persistProgress wyżej.
                val pausedNow = playerManager.isUserPaused
                if (tick % PROGRESS_SAVE_EVERY_TICKS == 0 || (pausedNow && !savedOnPause)) {
                    persistProgress()
                }
                savedOnPause = pausedNow
                // Materiał się kończy — jeśli jest kolejna pozycja w kolejce, automatycznie
                // proponujemy przejście dalej (z podglądem do anulowania, patrz pendingSwitch).
                // WYMAGA znanego czasu trwania (bez niego nie da się wykryć "blisko końca"),
                // więc zostaje na ściślejszym warunku niż zapis postępu wyżej.
                if (durMs > 0 && hasNext && pendingSwitch == null && durationMs - positionMs in 0..800) {
                    requestSwitch(direction = 1, auto = true)
                }
            }
            tick++
            kotlinx.coroutines.delay(1000)
        }
    }

    // Wstecz na pilocie/telefonie: najpierw anuluje ewentualny podgląd zmiany materiału,
    // potem chowa kontrolki (jak w każdym normalnym odtwarzaczu wideo na Android TV),
    // dopiero kolejne Wstecz wychodzi z odtwarzacza (domyślne zachowanie Activity).
    BackHandler(enabled = controlsVisible) { controlsVisible = false }
    BackHandler(enabled = pendingSwitch != null) { cancelSwitch() }
    // Zablokowane Wstecz podczas blokady ekranu — celowo NIC nie robi poza pokazaniem
    // podpowiedzi odblokowania, żeby przypadkowe Wstecz (albo gest systemowy) nie wyszło
    // z odtwarzacza, kiedy ekran jest świadomie zablokowany. Dodane PO powyższych dwóch —
    // najnowszy BackHandler ma pierwszeństwo, więc to on przechwytuje Wstecz jako pierwszy,
    // kiedy ekran jest zablokowany, niezależnie od stanu controlsVisible/pendingSwitch.
    BackHandler(enabled = isScreenLocked) { showLockUnlockHint = true }

    // Śledzenie długiego przytrzymania strzałki lewo/prawo na pilocie — krótkie naciśnięcie
    // to przewijanie ±10s (jak dotychczas), długie przytrzymanie to zmiana materiału.
    var pressStartMs by remember { mutableStateOf(0L) }
    var longPressHandled by remember { mutableStateOf(false) }

    var outerBoxHeightPx by remember { mutableStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { outerBoxHeightPx = it.height }
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            // Obsługa pilota — działa identycznie dla klasycznego pilota ze strzałkami
            // i okrągłego pilota Google TV (okrągły touchpad i tak wysyła te same
            // standardowe KEYCODE_DPAD_*, Android tłumaczy to automatycznie).
            .onKeyEvent { keyEvent ->
                // W trybie PiP appka NIE rysuje własnych kontrolek (patrz visible =
                // controlsVisible && !isInPipMode niżej) — okienko PiP jest wtedy w całości
                // obsługiwane przez SYSTEM (Android TV pokazuje własne, systemowe sterowanie:
                // rozwiń/zamknij/RemoteAction). Zgłoszenie: powrót z PiP pilotem był
                // niemożliwy — przyczyna: ten handler przechwytywał KAŻDY klawisz (Wstecz,
                // OK, strzałki) bezwarunkowo, nawet w PiP, więc zdarzenia nigdy nie docierały
                // do systemowej obsługi okienka PiP, która ich potrzebuje. Wczesny return
                // false = appka w ogóle nie rusza tych zdarzeń, gdy jesteśmy w PiP.
                if (isInPipMode) return@onKeyEvent false
                // Blokada ekranu na pilocie — ten sam mechanizm (repeatCount + czas trwania)
                // co inne długie przytrzymania w projekcie. Wszystkie klawisze POZA długim OK
                // są tu połykane (return true, nic nie robiąc) — to jest CAŁY sens blokady,
                // przypadkowe naciśnięcia pilota (np. leżącego na kanapie) nie mają żadnego
                // efektu, dopóki ktoś świadomie nie przytrzyma OK dłużej.
                if (isScreenLocked) {
                    if (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                val repeatCount = keyEvent.nativeKeyEvent?.repeatCount ?: 0
                                if (repeatCount == 0) {
                                    lockKeyPressStartMs = System.currentTimeMillis()
                                } else if (System.currentTimeMillis() - lockKeyPressStartMs >= 800L) {
                                    isScreenLocked = false
                                    showLockUnlockHint = false
                                }
                            }
                            else -> {}
                        }
                    }
                    return@onKeyEvent true
                }
                when (keyEvent.key) {
                    Key.MediaPlayPause -> {
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        if (isPlaying) playerManager.pause() else playerManager.resume()
                        isPlaying = !isPlaying
                        showControls()
                        true
                    }
                    Key.MediaPlay -> {
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        playerManager.resume(); isPlaying = true; showControls(); true
                    }
                    Key.MediaPause -> {
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        playerManager.pause(); isPlaying = false; showControls(); true
                    }
                    // Lewo/prawo: krótkie naciśnięcie = globalny skrót przewijania ±10s (jak
                    // dotychczas, działa niezależnie od tego czy kontrolki są widoczne).
                    // Długie przytrzymanie = zmiana na poprzedni/następny materiał w kolejce —
                    // wykrywane przez repeatCount zdarzeń KeyDown, które Android generuje
                    // automatycznie przy trzymaniu klawisza.
                    Key.DirectionLeft, Key.DirectionRight -> {
                        // Gdy fokus jest akurat w poziomej "półce" z kolejką (QueueBrowserRow),
                        // NIE przechwytujemy strzałek — puszczamy je dalej, żeby standardowy
                        // mechanizm fokusu Compose mógł przesunąć zaznaczenie między plakatami
                        // zamiast przewijać/zmieniać materiał.
                        if (browserRowFocused || barButtonFocused) {
                            // Zgłoszenie: nawigacja pilotem MIĘDZY przyciskami paska (np. kilka
                            // naciśnięć w stronę +30s) nie resetowała 4-sekundowego czasomierza
                            // ukrywania kontrolek, bo ten wczesny return pomijał showControls()
                            // wywoływane niżej dla zwykłego przypadku — kontrolki (razem z
                            // przyciskiem, do którego użytkownik akurat próbował dotrzeć)
                            // potrafiły zniknąć w połowie nawigacji.
                            if (keyEvent.type == KeyEventType.KeyDown) showControls()
                            return@onKeyEvent false
                        }
                        val isRight = keyEvent.key == Key.DirectionRight
                        when (keyEvent.type) {
                            KeyEventType.KeyDown -> {
                                val repeatCount = keyEvent.nativeKeyEvent?.repeatCount ?: 0
                                if (repeatCount == 0) {
                                    pressStartMs = System.currentTimeMillis()
                                    longPressHandled = false
                                } else if (!longPressHandled &&
                                    System.currentTimeMillis() - pressStartMs >= LONG_PRESS_MS
                                ) {
                                    longPressHandled = true
                                    requestSwitch(direction = if (isRight) 1 else -1, auto = false)
                                }
                                showControls()
                                true
                            }
                            KeyEventType.KeyUp -> {
                                if (!longPressHandled && durationMs > 0) {
                                    val newPos = if (isRight) {
                                        (positionMs + SEEK_STEP_MS).coerceAtMost(durationMs)
                                    } else {
                                        (positionMs - SEEK_STEP_MS).coerceAtLeast(0)
                                    }
                                    playerManager.seekTo(newPos)
                                    positionMs = newPos
                                }
                                showControls()
                                true
                            }
                            else -> false
                        }
                    }
                    // OK/Enter: jeśli kontrolki są schowane, samo je pokazuje (nie
                    // przełącza od razu odtwarzania — spójnie z tym, że dotyk też tylko
                    // odsłania kontrolki). Jeśli widoczne i skupione na konkretnym
                    // przycisku, ten przycisk sam obsłuży swój klik wcześniej w łańcuchu
                    // zdarzeń i tu nigdy nie dotrze.
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        if (!controlsVisible) {
                            showControls()
                            true
                        } else {
                            showControls()
                            false
                        }
                    }
                    Key.ChannelUp, Key.ChannelDown -> {
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent true
                        zapChannel(if (keyEvent.key == Key.ChannelUp) 1 else -1)
                        true
                    }
                    Key.LastChannel -> {
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent true
                        jumpToPreviousChannel()
                        true
                    }
                    else -> {
                        val digit = remoteDigitOf(keyEvent.key)
                        if (digit != null && current.kind == MediaKind.LIVE && activeQueue.items.size > 1) {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                // Maks. 4 cyfry — więcej kanałów w jednej grupie praktycznie
                                // się nie zdarza, a chroni przed "zawieszonym" długim numerem.
                                if (channelDigits.length < 4) channelDigits += digit.toString()
                            }
                            return@onKeyEvent true
                        }
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        showControls()
                        false
                    }
                }
            }
            // Tap = pokaż/schowaj kontrolki (jak dotychczasowy .clickable). Podwójny tap =
            // wejście w PiP — detectTapGestures sam rozróżnia jedno od drugiego (czeka
            // krótką chwilę po pierwszym tapie, czy nie nadejdzie drugi), więc pojedynczy
            // tap nadal działa dokładnie tak jak wcześniej, bez dodatkowego opóźnienia w
            // reagowaniu na niego nie da się uniknąć — to nieunikniony kompromis przy
            // rozróżnianiu tap/dwuklik w każdej appce, nie tylko tutaj. Osobny detektor obok
            // przeciągania paska postępu w dolnym pasku, żeby się wzajemnie nie gryzły.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        hideTrigger++
                    },
                    onDoubleTap = { onEnterPip() },
                )
            }
            // Swipe poziomy = następny/poprzedni materiał (jak dotychczas). Swipe pionowy
            // po LEWEJ połowie = jasność, po PRAWEJ = głośność. Jeden wspólny detektor
            // (zamiast osobnych detectHorizontalDragGestures/detectVerticalDragGestures)
            // celowo — dwa niezależne detektory na tym samym strumieniu zdarzeń próbowałyby
            // przechwycić przeciągnięcie każdy na swój sposób i wzajemnie by się gryzły.
            // Tutaj kierunek jest ustalany RAZ na starcie gestu (patrz GestureDirection) na
            // podstawie tego, co w danym momencie dominuje — i trzymany do końca przeciągnięcia.
            .pointerInput(activeQueue, activeIndex) {
                val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
                var totalDragX = 0f
                var totalDragY = 0f
                var lockedDirection: GestureDirection? = null
                var gestureIsLeftHalf = true

                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragX = 0f
                        totalDragY = 0f
                        lockedDirection = null
                        gestureIsLeftHalf = offset.x < size.width / 2f
                    },
                    onDragEnd = {
                        if (lockedDirection == GestureDirection.HORIZONTAL && abs(totalDragX) >= thresholdPx) {
                            requestSwitch(direction = if (totalDragX < 0) 1 else -1, auto = false)
                        }
                        gestureFeedback = null
                    },
                    onDragCancel = { gestureFeedback = null },
                ) { change, dragAmount ->
                    totalDragX += dragAmount.x
                    totalDragY += dragAmount.y

                    // Dopóki gest nie przekroczył wewnętrznego progu detectDragGestures w
                    // ŻADNĄ stronę, w ogóle tu nie wchodzimy — to jest już "prawdziwe"
                    // przeciągnięcie, więc pierwsza porównywana para (totalDragX, totalDragY)
                    // wystarcza, żeby sensownie rozstrzygnąć dominujący kierunek.
                    if (lockedDirection == null) {
                        lockedDirection = if (abs(totalDragX) >= abs(totalDragY)) {
                            GestureDirection.HORIZONTAL
                        } else {
                            GestureDirection.VERTICAL
                        }
                    }

                    when (lockedDirection) {
                        GestureDirection.HORIZONTAL -> change.consume()
                        GestureDirection.VERTICAL -> {
                            change.consume()
                            // Przeciągnięcie w GÓRĘ (dragAmount.y ujemne) ma ZWIĘKSZAĆ jasność/
                            // głośność, stąd minus — to samo, co w YouTube/Google TV.
                            val delta = -dragAmount.y / size.height
                            if (gestureIsLeftHalf) adjustBrightness(delta) else adjustVolume(delta)
                        }
                        null -> {}
                    }
                }
            }
            // Gest "dwa palce na zewnątrz/do środka" (pinch) do przełączania dopasowania
            // obrazu — celowo OSOBNY, samodzielny pointerInput (nie połączony z detektorem
            // przeciągania wyżej), bo detectTransformGestures reaguje na wielodotyk, a
            // powyższy detectDragGestures na pojedynczy palec — naturalnie się nie gryzą
            // (dwa palce na ekranie to zupełnie inna sekwencja zdarzeń niż jeden).
            // CELOWO próg, nie ciągłe śledzenie skali: detectTransformGestures raportuje
            // PRZYROSTOWĄ zmianę skali klatka po klatce, więc kumulujemy ją i reagujemy
            // dopiero po przekroczeniu wyraźnego progu — to prostsze i pewniejsze niż
            // próba płynnego mapowania gestu na dokładną wartość skali, a przy okazji nie
            // da się "utknąć" w pośrednim, nieokreślonym stanie między FIT a FILL.
            .pointerInput(Unit) {
                var accumulatedZoom = 1f
                detectTransformGestures { _, _, zoom, _ ->
                    accumulatedZoom *= zoom
                    when {
                        accumulatedZoom > 1.15f -> {
                            if (scaleMode != VideoScaleMode.FILL) toggleScaleMode()
                            accumulatedZoom = 1f
                        }
                        accumulatedZoom < 0.87f -> {
                            if (scaleMode != VideoScaleMode.FIT) toggleScaleMode()
                            accumulatedZoom = 1f
                        }
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { videoViewSizePx = it },
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    playerManager.attachToView(layout)
                    viewAttached = true
                }
            }
        )

        // Znacznik "trwa doskakiwanie do zapamiętanej pozycji" — patrz isCalibratingResume
        // wyżej. Celowo NIEZALEŻNY od controlsVisible/AnimatedVisibility niżej — to ma się
        // pokazywać WŁAŚNIE wtedy, gdy użytkownik nic nie robi (więc kontrolki mogą być
        // akurat ukryte), żeby nie wyglądało na "zawieszenie się" appki. Ten sam warunek
        // pokazuje kółko też przy isReconnecting (patrz "watchdog zacinania" w pętli
        // odświeżania paska postępu niżej) — bez tego automatyczne ponowne łączenie po
        // zgłoszonym zacinaniu się materiału wyglądałoby dla użytkownika identycznie jak
        // sam błąd, którego ma naprawić (zamrożona klatka, żadnej informacji).
        // Wpisywany numer kanału (cyfry z pilota) + nazwa kanału docelowego — w prawym górnym
        // rogu, jak w telewizorze. Widoczne tylko w trakcie wpisywania (patrz channelDigits).
        if (channelDigits.isNotEmpty() && !isInPipMode) {
            val targetTitle = channelDigits.toIntOrNull()?.minus(1)
                ?.let { activeQueue.items.getOrNull(it)?.title }
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(channelDigits, color = Accent, style = MaterialTheme.typography.displaySmall)
                Text(
                    targetTitle ?: "—",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if ((isCalibratingResume || isReconnecting || (showBufferingSpinner && !playerManager.isUserPaused)) && !isInPipMode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
            }
        }

        AnimatedVisibility(
            // W PiP okno jest za małe na jakiekolwiek kontrolki — system i tak rysuje
            // własne, minimalne przyciski nad małym okienkiem, nasze nakładki tylko by
            // przeszkadzały (i zresztą nie dałoby się w nie trafić dotykiem/pilotem).
            visible = controlsVisible && !isInPipMode,
            modifier = Modifier.fillMaxSize(),
            // Jawne enter/exit = SAMO przenikanie (fade), bez domyślnego expandIn/shrinkOut
            // — te DOMYŚLNE animacje Compose animują też ROZMIAR kontenera (rośnie/kurczy
            // się), nie tylko przezroczystość. Przez krótką chwilę PODCZAS tej animacji
            // zawartość (górny pasek z tytułem/ikonami I dolny pasek z suwakiem/plakatami,
            // oba pozycjonowane TopStart/BottomStart WZGLĘDEM tego kontenera) renderowała
            // się w SKURCZONYM prostokącie, więc "góra" i "dół" wizualnie lądowały blisko
            // siebie/nachodziły na siebie — dokładnie zgłoszony błąd ("plakaty na górze
            // ekranu"), widoczny za KAŻDYM razem, gdy kontrolki się pokazują/chowają (co
            // tłumaczy, czemu zdarzało się to tak często i pozornie losowo). Sam fade nie
            // zmienia rozmiaru w ogóle — content od razu jest we właściwym miejscu,
            // zmienia się tylko jego przezroczystość.
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Górny pasek: powrót + tytuł + otwórz w VLC
                // Jawna trasa fokusu LEWO/PRAWO między przyciskami — z tym SAMYM powodem co
                // wcześniejsza naprawa "Ukryj wszystko"/"Pokaż wszystko" w Ustawieniach:
                // domyślne, geometryczne wyszukiwanie kierunkowe Compose zawodzi w gęsto
                // upakowanym rzędzie wielu małych przycisków (zgłoszenie: pilotem dawało się
                // zaznaczyć tylko JEDEN z nich). activeTopBarChain to lista TYLKO faktycznie
                // renderowanych w tej chwili przycisków (część pojawia się warunkowo,
                // zależnie od typu treści) — bez tego jawne left/right prowadziłyby czasem
                // donikąd, do przycisku, którego akurat nie ma na ekranie.
                val backFR = remember { FocusRequester() }
                val downloadFR = remember { FocusRequester() }
                val timerFR = remember { FocusRequester() }
                val pipFR = remember { FocusRequester() }
                val castFR = remember { FocusRequester() }
                val scaleModeFR = remember { FocusRequester() }
                val closeFR = remember { FocusRequester() }
                val prevChannelFR = remember { FocusRequester() }
                // "Poprzedni kanał" — tylko Live i tylko gdy faktycznie był jakiś poprzedni
                // kanał w tej samej kolejce (patrz previousEntryId/jumpToPreviousChannel).
                val showPrevChannelBtn = current.kind == MediaKind.LIVE &&
                    previousEntryId != null && activeQueue.items.any { it.id == previousEntryId }
                val showDownloadBtn = current.kind != MediaKind.LIVE && !current.streamUrl.startsWith("content://")
                val isPhoneDevice = remember { !context.isTvDevice() }
                val activeTopBarChain = buildList {
                    add(backFR)
                    if (showDownloadBtn) add(downloadFR)
                    add(timerFR)
                    if (showPrevChannelBtn) add(prevChannelFR)
                    if (pipSupported) add(pipFR)
                    // "Wyślij do TV" — TYLKO na telefonie (zgłoszenie: appka na telefonie ma
                    // rzucać obraz NA appkę na TV, w tej samej sieci Wi-Fi). Na TV appka jest
                    // odbiornikiem, nie ma sensu wysyłać sama do siebie.
                    if (isPhoneDevice) add(castFR)
                    add(scaleModeFR)
                    add(closeFR)
                }
                // Ten sam wzorzec dla dolnego paska (ścieżki audio + VLC) — zadeklarowane
                // TUTAJ (nie lokalnie przy samych przyciskach niżej), żeby środkowy przycisk
                // play/pauza mógł się do nich jawnie odwołać jako punkt wejścia UP/DOWN (patrz
                // jego modifier niżej) — Kotlin nie pozwala na odwołania w przód w ciele
                // funkcji, więc deklaracja musi poprzedzać WSZYSTKIE miejsca użycia.
                val audiotrackFR = remember { FocusRequester() }
                val vlcFR = remember { FocusRequester() }
                val bottomBarChain = listOf(audiotrackFR, vlcFR)
                // Zgłoszenie: na telefonie w poziomie (mało miejsca w pionie) centralny rząd
                // przycisków (poprzedni/-10s/play-pauza/+30s/następny) nakładał się na dolny
                // pasek (suwak + czas + miniatury "dalej w kolejce"). Przyczyna: te przyciski
                // są wyśrodkowane (Alignment.Center) względem CAŁEGO ekranu (outerBoxHeightPx),
                // nie względem samego obszaru wideo — a im mniej miejsca w pionie ma ekran, tym
                // bliżej środek całego ekranu wypada dolnego paska. Poprawka: przesunięcie całego
                // rzędu w górę o połowę wysokości dolnego paska (już zmierzonej, patrz
                // bottomBarHeightPx wyżej) — efektywnie wyśrodkowuje przyciski w obszarze NAD
                // dolnym paskiem, zamiast w środku całego ekranu, niezależnie od proporcji ekranu.
                //
                // Zgłoszenie (kolejne): na niektórych proporkach ekranu przyciski zaczęły
                // zamiast tego nachodzić na GÓRNY pasek — bo powyższa poprawka liczyła
                // przesunięcie tylko względem dolnego paska, całkowicie ignorując górny.
                // Poprawka: zamiast "cofnij o połowę dolnego paska", licz naprawdę środek
                // WOLNEGO obszaru między dolną krawędzią górnego paska a górną krawędzią
                // dolnego paska (topBarHeightPx / bottomBarHeightPx, obie zmierzone przez
                // onSizeChanged) i przesuń rząd przycisków dokładnie tam. To automatycznie
                // trzyma przyciski z dala od OBU pasków na każdej proporcji ekranu, zamiast
                // wymagać ręcznego doboru stałej dla każdego przypadku z osobna.
                val centerRowYOffset = with(density) {
                    if (outerBoxHeightPx <= 0) {
                        0.dp
                    } else {
                        val freeAreaTopPx = topBarHeightPx
                        val freeAreaBottomPx = outerBoxHeightPx - bottomBarHeightPx
                        val freeAreaCenterPx = (freeAreaTopPx + freeAreaBottomPx) / 2
                        val screenCenterPx = outerBoxHeightPx / 2
                        (freeAreaCenterPx - screenCenterPx).toDp()
                    }
                }
                // Centralny rząd (poprzedni/przewiń -10s/play-pauza/przewiń +30s/następny) —
                // zgłoszenie: "chcę mieć dostęp z pilota do WSZYSTKICH przycisków" — te
                // cztery boczne przyciski były do tej pory notDpadFocusable (tylko dotyk),
                // bo D-pad ma już RÓWNOWAŻNĄ funkcję wprost pod klawiszami (krótkie lewo/
                // prawo = 10s, długie przytrzymanie = poprzedni/następny) — ale skoro
                // wyraźnie miał być dostęp do WSZYSTKICH przycisków na ekranie, a nie tylko
                // do ich klawiszowych odpowiedników, dodane jawne połączenie lewo/prawo,
                // dokładnie tym samym wzorcem co activeTopBarChain/bottomBarChain wyżej.
                // Zbudowane WARUNKOWO (hasPrev/hasNext) — te przyciski i tak nie renderują
                // się wcale, gdy nie ma dokąd nawigować, więc nie mogą być częścią łańcucha.
                val prevFR = remember { FocusRequester() }
                val seekBackFR = remember { FocusRequester() }
                val seekForwardFR = remember { FocusRequester() }
                val nextFR = remember { FocusRequester() }
                val centerRowChain = buildList {
                    if (hasPrev) add(prevFR)
                    add(seekBackFR)
                    add(playPauseFocusRequester)
                    add(seekForwardFR)
                    if (hasNext) add(nextFR)
                }
                fun Modifier.centerRowFocus(requester: FocusRequester): Modifier {
                    val idx = centerRowChain.indexOf(requester)
                    return this
                        .focusRequester(requester)
                        .focusProperties {
                            centerRowChain.getOrNull(idx - 1)?.let { left = it }
                            centerRowChain.getOrNull(idx + 1)?.let { right = it }
                        }
                        // Ten sam wyjątek co przy górnym/dolnym pasku (patrz barButtonFocused
                        // wyżej) — bez tego globalna obsługa klawiszy przechwytywałaby lewo/
                        // prawo pod przewijanie, zanim left/right wyżej zdążyłoby przesunąć
                        // fokus do sąsiada w tym rzędzie.
                        .onFocusChanged { state -> barButtonFocused = state.isFocused || state.hasFocus }
                }
                fun Modifier.chainedFocus(requester: FocusRequester): Modifier {
                    val idx = activeTopBarChain.indexOf(requester)
                    return this
                        .focusRequester(requester)
                        .focusProperties {
                            activeTopBarChain.getOrNull(idx - 1)?.let { left = it }
                            activeTopBarChain.getOrNull(idx + 1)?.let { right = it }
                        }
                        // Patrz barButtonFocused wyżej — bez tego globalna obsługa klawiszy
                        // przechwytywałaby lewo/prawo pod przewijanie, zanim powyższe left/
                        // right w ogóle miałyby szansę przesunąć fokus do sąsiada.
                        .onFocusChanged { state -> barButtonFocused = state.isFocused || state.hasFocus }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        // Zmierzona wysokość górnego paska — patrz centerRowYOffset niżej:
                        // zgłoszenie, że centralny rząd przycisków (odtwórz/pauza, -10s/+30s)
                        // nachodzi na górny pasek na niektórych proporcjach ekranu. Wcześniej
                        // centerRowYOffset liczył tylko odległość od DOLNEGO paska, ignorując
                        // górny — stąd nachodzenie. Teraz oba paski są uwzględnione.
                        .onSizeChanged { topBarHeightPx = it.height },
                ) {
                    // --- wiersz 1: powrót (lewo) + wszystkie ikony (prawo) ---
                    // Tytuł CELOWO NIE jest już w tym samym wierszu co ikony (patrz wiersz 2
                    // niżej) — przy długich nazwach (kanał/serial/film) dzielenie miejsca z
                    // pięcioma ikonami po prawej zostawiało tytułowi za mało przestrzeni,
                    // więc zawsze się ucinał wielokropkiem, nawet gdy CAŁY ekran pod spodem
                    // był pusty i mógłby pomieścić pełną nazwę (zgłoszenie: "wciskana w
                    // prawy górny róg"). Osobny wiersz na same ikony ma ZAWSZE tyle miejsca,
                    // ile potrzeba, niezależnie od długości tytułu.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.chainedFocus(backFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = tr("common.back"), tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        // --- pobieranie: tylko VOD (filmy/odcinki), tak jak w gridzie — Live to ciągła transmisja, nie plik ---
                        // Lokalne pliki (folder podłączony przez SAF) są już na urządzeniu — nie
                        // ma czego "pobierać", więc przycisk pokazuje się tylko dla zdalnych VOD.
                        if (showDownloadBtn) {
                            IconButton(
                                onClick = {
                                    DownloadHelper.downloadItem(context, current.streamUrl, current.title, current.userAgent, current.referer, posterUrl = current.posterUrl)
                                },
                                modifier = Modifier.chainedFocus(downloadFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                            ) {
                                Icon(Icons.Default.Download, contentDescription = tr("common.download"), tint = Color.White)
                            }
                        }
                        // Przycisk EPG dla pojedynczego kanału (ikona "i") CELOWO USUNIĘTY —
                        // powodował ciągłe zawieszanie/zamykanie aplikacji (zgłoszenie). Pełny
                        // grid EPG dostępny z Live działa dobrze i zostaje jedynym sposobem
                        // sprawdzenia programu.
                        // Blokada ekranu — zastąpiła tu przycisk timera uśpienia (przeniesiony/
                        // usunięty z górnego paska na życzenie). Zapobiega przypadkowym dotknięciom
                        // innych elementów — po zablokowaniu cały ekran przechwytuje dotyk sam,
                        // patrz nakładka blokady niżej (przy showLockUnlockHint).
                        IconButton(
                            onClick = {
                                isScreenLocked = true
                                controlsVisible = false
                            },
                            modifier = Modifier.chainedFocus(timerFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = tr("player.lockScreenDesc"), tint = Color.White)
                        }
                        if (showPrevChannelBtn) {
                            IconButton(
                                onClick = { jumpToPreviousChannel() },
                                modifier = Modifier.chainedFocus(prevChannelFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                            ) {
                                Icon(Icons.Default.History, contentDescription = tr("player.previousChannelDesc"), tint = Color.White)
                            }
                        }
                        // Ręczne zminimalizowanie do PiP — poza tym samo wychodzi automatycznie
                        // przy Home/przełączeniu appki (patrz PlayerActivity.onUserLeaveHint).
                        // Pokazuje się tylko tam, gdzie system faktycznie zgłasza obsługę PiP —
                        // na większości telewizorów/boxów przycisk sam się nie pojawi.
                        if (pipSupported) {
                            IconButton(
                                onClick = onEnterPip,
                                modifier = Modifier.chainedFocus(pipFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                            ) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = tr("player.pipDesc"), tint = Color.White)
                            }
                        }
                        // "Wyślij do TV" — patrz obszerny komentarz przy castFR/CastManager.kt.
                        // Otwiera prosty dialog z listą wykrytych w sieci Wi-Fi instancji
                        // appki na TV; wybranie jednej wysyła BIEŻĄCY materiał (z aktualną
                        // pozycją odtwarzania) do niej.
                        if (isPhoneDevice) {
                            IconButton(
                                onClick = {
                                    // Cast ("Wyślij do TV") jest ZAWSZE wolne — patrz TrialGate.kt.
                                    // Ostrzeżenie o braku szyfrowania TYLKO przy pierwszym użyciu
                                    // (sekcja 8) — patrz showCastWarningDialog niżej.
                                    if (com.iptvplayer.app.data.LocalStore(context).hasAcknowledgedCastWarning()) {
                                        showCastDialog = true
                                    } else {
                                        showCastWarningDialog = true
                                    }
                                },
                                modifier = Modifier.chainedFocus(castFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = tr("player.castDesc"), tint = Color.White)
                            }
                        }
                        // Dopasowanie obrazu do ekranu (FIT/FILL) — obok gestu pinch (patrz osobny
                        // pointerInput wyżej, dotykowy dodatek). Ikona odzwierciedla AKTUALNY tryb:
                        // Fullscreen = "dotknij, żeby wypełnić ekran", FullscreenExit = "dotknij,
                        // żeby wrócić do oryginalnych proporcji".
                        IconButton(
                            onClick = { toggleScaleMode() },
                            modifier = Modifier.chainedFocus(scaleModeFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                        ) {
                            Icon(
                                if (scaleMode == VideoScaleMode.FIT) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                                contentDescription = tr(if (scaleMode == VideoScaleMode.FIT) "player.scaleFillDesc" else "player.scaleFitDesc"),
                                tint = Color.White,
                            )
                        }
                        // Zamknięcie CAŁEJ aplikacji — nie tylko wyjście z odtwarzacza.
                        // Potwierdzenie chroni przed przypadkowym naciśnięciem OK w rogu ekranu.
                        IconButton(
                            onClick = { showExitConfirm = true },
                            modifier = Modifier.chainedFocus(closeFR).tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = tr("home.exit.contentDescription"), tint = Color.White)
                        }
                    }
                    // --- wiersz 2: tytuł, PEŁNA szerokość ekranu, zawija się zamiast się ucinać ---
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            current.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            // Bez maxLines=1/ellipsis z poprzedniej wersji — teraz, mając CAŁĄ
                            // szerokość ekranu tylko dla siebie (nie dzieląc jej z ikonami),
                            // długi tytuł zawija się do drugiej linii zamiast się ucinać.
                            // maxLines=2 to rozsądny sufit (trzecia linia zaczęłaby zasłaniać
                            // obraz) — w tej rzadkiej sytuacji dalej ucina się wielokropkiem.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // "Teraz: X • Dalej: Y" — patrz LaunchedEffect(current.id) wyżej,
                        // gdzie nowPlayingProgram/nextProgram się aktualizują. Cała linia
                        // znika, gdy nie ma żadnych danych (VOD, albo Live bez źródła EPG),
                        // zamiast pokazywać pusty/mylący napis.
                        val epgLine = buildString {
                            nowPlayingProgram?.let { append(tr("player.epgNowLabel", it.title)) }
                            nextProgram?.let {
                                if (isNotEmpty()) append("  •  ")
                                append(tr("player.epgNextLabel", it.title))
                            }
                        }
                        if (epgLine.isNotEmpty()) {
                            Text(
                                epgLine,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Strzałki poprzedni/następny po bokach ekranu — dotykowy odpowiednik swipe'a/
                // długiego przytrzymania na pilocie, widoczne tylko gdy jest dokąd nawigować.
                if (hasPrev) {
                    IconButton(
                        onClick = { requestSwitch(direction = -1, auto = false) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = centerRowYOffset)
                            .padding(start = 8.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .centerRowFocus(prevFR)
                            .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = tr("player.previousDesc"), tint = Color.White)
                    }
                }
                if (hasNext) {
                    IconButton(
                        onClick = { requestSwitch(direction = 1, auto = false) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(y = centerRowYOffset)
                            .padding(end = 8.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .centerRowFocus(nextFR)
                            .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = tr("player.nextDesc"), tint = Color.White)
                    }
                }

                // Przewijanie o stałą wartość — DZIAŁA NIEZALEŻNIE od tego, czy appka zna
                // czas trwania materiału (durationMs), bo operuje wyłącznie na AKTUALNEJ
                // pozycji (positionMs), nie na proporcji względem całości. Zgłoszenie: w
                // niektórych serialach durationMs zostawało 0 nawet po dłuższej chwili
                // odtwarzania (najprawdopodobniej strumień z tego konkretnego źródła nie
                // zgłasza długości — poza kontrolą appki), przez co pasek postępu (patrz
                // "Dolny pasek" niżej) w ogóle się nie pokazywał, a na dotyk nie było ŻADNEGO
                // innego sposobu przewinięcia (przesunięcie w bok jest już zajęte pod zmianę
                // odcinka/kanału). Te dwa przyciski działają zawsze, jako niezawodna
                // alternatywa — 10s do tyłu / 30s do przodu to standardowy wzorzec znany z
                // YouTube i innych odtwarzaczy.
                //
                // Długie przytrzymanie (dotyk LUB OK na pilocie) otwiera dialog z ręcznym
                // wpisaniem docelowego czasu — ten sam, jawny mechanizm wykrywania długiego
                // przytrzymania co przy kafelku "Kontynuuj oglądanie" na Home (combinedClickable
                // samo w sobie niezawodnie nie wykrywa długiego OK na TV).
                var seekBackPressStartMs by remember { mutableStateOf(0L) }
                var seekBackLongPressHandled by remember { mutableStateOf(false) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = centerRowYOffset)
                        .offset(x = (-76).dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .centerRowFocus(seekBackFR)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.key != Key.DirectionCenter && keyEvent.key != Key.Enter && keyEvent.key != Key.NumPadEnter) {
                                return@onKeyEvent false
                            }
                            when (keyEvent.type) {
                                KeyEventType.KeyDown -> {
                                    val repeatCount = keyEvent.nativeKeyEvent?.repeatCount ?: 0
                                    if (repeatCount == 0) {
                                        seekBackPressStartMs = System.currentTimeMillis()
                                        seekBackLongPressHandled = false
                                    } else if (!seekBackLongPressHandled && System.currentTimeMillis() - seekBackPressStartMs >= 500L) {
                                        seekBackLongPressHandled = true
                                        showCustomSeekDialog = true
                                    }
                                    true
                                }
                                KeyEventType.KeyUp -> {
                                    if (!seekBackLongPressHandled) playerManager.seekTo((positionMs - 10_000).coerceAtLeast(0))
                                    true
                                }
                                else -> false
                            }
                        }
                        .combinedClickable(
                            onClick = { playerManager.seekTo((positionMs - 10_000).coerceAtLeast(0)) },
                            onLongClick = { showCustomSeekDialog = true },
                        )
                        .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = tr("player.seekBackDesc"), tint = Color.White, modifier = Modifier.size(26.dp))
                }
                var seekFwdPressStartMs by remember { mutableStateOf(0L) }
                var seekFwdLongPressHandled by remember { mutableStateOf(false) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = centerRowYOffset)
                        .offset(x = 76.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .centerRowFocus(seekForwardFR)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.key != Key.DirectionCenter && keyEvent.key != Key.Enter && keyEvent.key != Key.NumPadEnter) {
                                return@onKeyEvent false
                            }
                            when (keyEvent.type) {
                                KeyEventType.KeyDown -> {
                                    val repeatCount = keyEvent.nativeKeyEvent?.repeatCount ?: 0
                                    if (repeatCount == 0) {
                                        seekFwdPressStartMs = System.currentTimeMillis()
                                        seekFwdLongPressHandled = false
                                    } else if (!seekFwdLongPressHandled && System.currentTimeMillis() - seekFwdPressStartMs >= 500L) {
                                        seekFwdLongPressHandled = true
                                        showCustomSeekDialog = true
                                    }
                                    true
                                }
                                KeyEventType.KeyUp -> {
                                    if (!seekFwdLongPressHandled) {
                                        val target = if (durationMs > 0) (positionMs + 30_000).coerceAtMost(durationMs) else positionMs + 30_000
                                        playerManager.seekTo(target)
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .combinedClickable(
                            onClick = {
                                val target = if (durationMs > 0) (positionMs + 30_000).coerceAtMost(durationMs) else positionMs + 30_000
                                playerManager.seekTo(target)
                            },
                            onLongClick = { showCustomSeekDialog = true },
                        )
                        .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                ) {
                    Icon(Icons.Default.Forward30, contentDescription = tr("player.seekForwardDesc"), tint = Color.White, modifier = Modifier.size(26.dp))
                }

                // Środek: play/pause — domyślny punkt fokusu pilota, gdy kontrolki się pokazują
                IconButton(
                    onClick = {
                        if (isPlaying) playerManager.pause() else playerManager.resume()
                        isPlaying = !isPlaying
                        showControls()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = centerRowYOffset)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .focusRequester(playPauseFocusRequester)
                        .focusProperties {
                            // Jawne wejście z centralnego przycisku w górny/dolny pasek —
                            // bez tego domyślne, geometryczne wyszukiwanie kierunkowe
                            // Compose zawodziło przy wchodzeniu W RZĄD małych ikon z
                            // ZUPEŁNIE INNEJ osi (ten sam mechanizm, który naprawiłem już
                            // WEWNĄTRZ obu pasków — lewo/prawo między ikonami — ale
                            // przeoczyłem samo WEJŚCIE do nich). Zgłoszenie: małe ikony (X,
                            // PiP, VLC) wciąż niedostępne pilotem mimo tamtej poprawki.
                            activeTopBarChain.firstOrNull()?.let { up = it }
                            bottomBarChain.firstOrNull()?.let { down = it }
                            // Lewo/prawo do sąsiadów w centerRowChain (przewiń -10s/+30s,
                            // poprzedni/następny) — ten sam powód, ta sama poprawka co przy
                            // barButtonFocused wyżej: bez tego jawnego połączenia globalna
                            // obsługa klawiszy przechwytywałaby lewo/prawo pod przewijanie,
                            // zanim zdążyłoby przesunąć fokus do sąsiada w tym rzędzie.
                            val idx = centerRowChain.indexOf(playPauseFocusRequester)
                            centerRowChain.getOrNull(idx - 1)?.let { left = it }
                            centerRowChain.getOrNull(idx + 1)?.let { right = it }
                        }
                        .onFocusChanged { state -> barButtonFocused = state.isFocused || state.hasFocus }
                        .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }

                // Dolny pasek: pasek postępu + czas
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .onSizeChanged { bottomBarHeightPx = it.height },
                ) {
                    // Pasek przewijania POKAZYWANY ZAWSZE — poprzednio ukryty całkowicie, gdy
                    // durationMs=0 (zgłoszenie: "brak paska przewijania w serialach"). Dowód
                    // od użytkownika: prawdziwy VLC (ten sam silnik, otwierany przyciskiem
                    // "otwórz w VLC" obok) potrafi zbudować DZIAŁAJĄCY pasek nawet dla
                    // materiału bez znanej długości — więc to nie jest fundamentalne
                    // ograniczenie biblioteki, samo pokazanie paska nie powinno zależeć od
                    // znajomości durationMs. ZAKTUALIZOWANE: zamiast czysto zgadywanej
                    // "wirtualnej" długości, używamy teraz playerManager.impliedDurationMs()
                    // — potwierdzoną narzędziem diagnostycznym metodę wyliczania PRAWDZIWEJ,
                    // ukrytej długości ze stosunku .position/.time w danej chwili (trzy
                    // niezależne pomiary na tym samym materiale dały spójny wynik co do
                    // minuty). Fallback na starą, "rosnącą" zgadywankę zostaje TYLKO na
                    // pierwsze ułamki sekundy odtwarzania, zanim ten stosunek zdąży się
                    // ustalić (impliedDurationMs() zwraca wtedy 0).
                    val effectiveDurationMs = if (durationMs > 0) {
                        durationMs
                    } else {
                        val implied = playerManager.impliedDurationMs()
                        if (implied > 0) implied else maxOf((dragPositionMs ?: positionMs) + 300_000L, 600_000L)
                    }
                    run {
                        // notDpadFocusable(): przewijanie pilotem robi się strzałkami lewo/
                        // prawo (patrz globalna obsługa klawiszy wyżej) — suwak zostaje
                        // czysto dotykowy/myszowy, żeby pilot nie próbował "wskoczyć" na
                        // niego i przesuwać go pojedynczymi, bardzo drobnymi krokami.
                        //
                        // WAŻNE: onValueChange NIE woła już seekTo() na każdą, nawet
                        // najdrobniejszą zmianę podczas przeciągania — przy przeciąganiu
                        // Slider potrafi wysłać kilkadziesiąt takich zdarzeń na sekundę, a
                        // każde z nich to REALNY seek w silniku libVLC (dla strumienia
                        // sieciowego oznacza to nowe żądanie HTTP + oczekiwanie na
                        // klatkę kluczową) — kolejkowanie dziesiątek takich seeków pod rząd
                        // to dokładnie to, co dawało wrażenie "opóźnionego"/"zacinającego się"
                        // przewijania. Teraz onValueChange tylko przesuwa podgląd LOKALNIE
                        // (dragPositionMs, płynnie, zero kosztu), a właściwy, POJEDYNCZY seek
                        // leci dopiero w onValueChangeFinished — czyli raz, po puszczeniu.
                        Slider(
                            value = (dragPositionMs ?: positionMs).toFloat(),
                            onValueChange = { dragPositionMs = it.toLong() },
                            onValueChangeFinished = {
                                dragPositionMs?.let { target ->
                                    playerManager.seekTo(target)
                                    // Aktualizacja "na już" — bez tego pasek na ułamek
                                    // sekundy cofnąłby się do starej pozycji, dopóki pętla
                                    // odświeżania (co 1s) sama by go nie dogoniła.
                                    positionMs = target
                                }
                                dragPositionMs = null
                            },
                            valueRange = 0f..effectiveDurationMs.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                            modifier = Modifier.notDpadFocusable(),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(dragPositionMs ?: positionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // "--:--" zamiast myląco pokazywać 0:00 albo wirtualną,
                            // zmyśloną długość, gdy appka jej naprawdę nie zna.
                            Text(
                                if (durationMs > 0) formatTime(durationMs) else "--:--",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(4.dp))
                            // Ścieżki audio/napisów — celowo w dolnym pasku (nie w górnym obok
                            // Wstecz), żeby dało się do niej dojść pilotem strzałką w DÓŁ z
                            // przycisku play/pauza, bez kolidowania z lewo/prawo zarezerwowanym
                            // globalnie pod przewijanie/nawigację (patrz obsługa klawiszy wyżej).
                            // Jawne left/right między tymi dwoma przyciskami — ten sam powód co
                            // przy górnym pasku wyżej (zgłoszenie: pilotem dawało się zaznaczyć
                            // tylko jeden z dwóch, mimo że oba mają tvFocusFrame). audiotrackFR i
                            // vlcFR zadeklarowane WCZEŚNIEJ (razem z activeTopBarChain, patrz
                            // bottomBarChain) — żeby przycisk play/pauza mógł się do nich odwołać
                            // jako punkt wejścia DOWN.
                            IconButton(
                                onClick = { showTracksDialog = true; showControls() },
                                modifier = Modifier
                                    .focusRequester(audiotrackFR)
                                    .focusProperties { right = vlcFR }
                                    .onFocusChanged { state -> barButtonFocused = state.isFocused || state.hasFocus }
                                    .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = tr("player.tracksDesc"), tint = Color.White)
                            }
                            // "Otwórz w VLC" — celowo tuż obok ścieżek audio/napisów (nie w
                            // górnym pasku jak wcześniej), na wyraźne życzenie: ta pozycja jest
                            // wygodniejsza w użyciu.
                            IconButton(
                                onClick = { openInExternalVlc(context, current.streamUrl, current.title) },
                                modifier = Modifier
                                    .focusRequester(vlcFR)
                                    .focusProperties { left = audiotrackFR }
                                    .onFocusChanged { state -> barButtonFocused = state.isFocused || state.hasFocus }
                                    .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = tr("player.openInVlc"), tint = Color.White)
                            }
                        }
                    }

                    // Pozioma "półka" z CAŁĄ kolejką nawigacji (Live -> reszta grupy, Series ->
                    // wszystkie odcinki, Movie -> podobne filmy, patrz PlayQueue.kt) — w
                    // odróżnieniu od strzałek/swipe'a (tylko o jedną pozycję), widz może tu
                    // od razu wybrać DOWOLNĄ pozycję. Ukryta, gdy kolejka ma tylko bieżącą
                    // pozycję i nic więcej (np. pojedynczy film bez podobnych w bibliotece).
                    if (activeQueue.items.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        QueueBrowserRow(
                            items = activeQueue.items,
                            activeIndex = activeIndex,
                            browserFocused = browserRowFocused,
                            onFocusedChange = { browserRowFocused = it },
                            onSelect = { idx -> jumpToQueueIndex(idx) },
                            onUserActivity = { showControls() },
                        )
                    }
                }
            }
        }

        // Podgląd/potwierdzenie zmiany materiału (styl Netflix) — pokazuje się zarówno po
        // ręcznym swipe'u/przytrzymaniu strzałki, jak i automatycznie po zakończeniu materiału.
        // Odlicza SWITCH_CONFIRM_SECONDS i sam się zatwierdza, chyba że użytkownik anuluje.
        // Ukryte w PiP z tego samego powodu co reszta kontrolek wyżej.
        if (!isInPipMode) {
            pendingSwitch?.let { p ->
                NextUpOverlay(
                    targetEntry = p.targetQueue.items[p.targetIndex],
                    isNext = p.direction > 0,
                    onPlayNow = { commitSwitch(p) },
                    onCancel = { cancelSwitch() },
                )
            }
        }

        // Feedback gestu jasność/głośność — patrz pointerInput(activeQueue, activeIndex)
        // wyżej. Pokazuje się NIEZALEŻNIE od controlsVisible (widz zwykle robi ten gest
        // właśnie wtedy, gdy kontrolki są schowane, żeby nie zasłaniały obrazu).
        if (!isInPipMode) {
            gestureFeedback?.let { GestureFeedbackOverlay(it) }
        }

        // Nakładka diagnostyczna — patrz obszerny komentarz przy playerDebugOverlayEnabled
        // wyżej. CELOWO niezależna od controlsVisible (ma być widoczna na KAŻDYM zrzucie
        // ekranu, nie tylko gdy akurat pokazują się kontrolki) i niezależna od AnimatedVisibility
        // wyżej (to osobny, stały element — sam w sobie nie może być przyczyną przeskakiwania,
        // skoro nie ma żadnej animacji rozmiaru).
        if (playerDebugOverlayEnabled && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(8.dp),
            ) {
                Text(
                    "durationMs=$durationMs\npositionMs=$positionMs\ncontrolsVisible=$controlsVisible\nqueueSize=${activeQueue.items.size}\nbottomBarHeightPx=$bottomBarHeightPx\nouterBoxHeightPx=$outerBoxHeightPx\n$resumeDebugInfo",
                    color = Color(0xFF00FF00),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }

    if (showCastDialog && !isInPipMode) {
        CastPickerDialog(
            context = context,
            onSend = { target ->
                com.iptvplayer.app.cast.CastManager.sendCast(
                    target,
                    com.iptvplayer.app.cast.CastManager.CastPayload(
                        streamUrl = current.streamUrl,
                        title = current.title,
                        userAgent = current.userAgent,
                        referer = current.referer,
                        positionMs = positionMs,
                    ),
                ) { }
            },
            onDismiss = { showCastDialog = false },
        )
    }

    // Ostrzeżenie o braku szyfrowania przy pierwszym użyciu "Wyślij do TV" — sekcja 8.
    // Zaznaczenie "Rozumiem" zapisuje flagę na stałe (LocalStore) i OD RAZU otwiera
    // właściwy CastPickerDialog wyżej, więc Użytkownik nie musi klikać ikony Cast ponownie.
    if (showCastWarningDialog) {
        AlertDialog(
            onDismissRequest = { showCastWarningDialog = false },
            title = { Text(tr("player.castDesc")) },
            text = { Text(tr("castWarning.body")) },
            confirmButton = {
                TextButton(onClick = {
                    com.iptvplayer.app.data.LocalStore(context).setAcknowledgedCastWarning()
                    showCastWarningDialog = false
                    showCastDialog = true
                }) { Text(tr("common.understood")) }
            },
        )
    }

    // Pełnoekranowa nakładka blokady — przechwytuje CAŁY dotyk sama (żadne zdarzenie nie
    // dociera do kontrolek pod spodem), więc żadne przypadkowe dotknięcie nie ma efektu.
    // Dwa sposoby odblokowania, zgodnie ze zgłoszeniem: długie przytrzymanie w DOWOLNYM
    // miejscu odblokowuje od razu, a pojedyncze dotknięcie pokazuje mały przycisk
    // odblokowania (na wypadek, gdyby ktoś nie wiedział o długim przytrzymaniu) — ten
    // sam znika po 3 sekundach, jeśli nieużyty, żeby nie zaśmiecać ekranu na stałe.
    if (isScreenLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.001f)) // niemal przezroczyste, ale
                // WYMAGANE, żeby pointerInput w ogóle "widziało" dotknięcia w całym obszarze
                // (przezroczysty Box bez tła czasem nie przechwytuje dotyku poza narysowaną
                // treścią) — 0.001 jest wizualnie niezauważalne, ale wystarcza technicznie.
                .pointerInput(isScreenLocked) {
                    detectTapGestures(
                        onTap = {
                            showLockUnlockHint = true
                        },
                        onLongPress = {
                            isScreenLocked = false
                            showLockUnlockHint = false
                        },
                    )
                },
        ) {
            if (showLockUnlockHint) {
                LaunchedEffect(showLockUnlockHint) {
                    kotlinx.coroutines.delay(3000)
                    showLockUnlockHint = false
                }
                IconButton(
                    onClick = {
                        isScreenLocked = false
                        showLockUnlockHint = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .tvFocusFrame(shape = CircleShape, focusColor = Color.White),
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = tr("player.unlockScreenDesc"), tint = Color.White)
                }
            }
        }
    }

    if (showCustomSeekDialog && !isInPipMode) {
        CustomSeekDialog(
            currentPositionMs = positionMs,
            onSeek = { targetMs -> playerManager.seekTo(targetMs) },
            onDismiss = { showCustomSeekDialog = false },
        )
    }

    if (showTracksDialog && !isInPipMode) {
        // Świeży odczyt przy każdym otwarciu i po każdym wyborze (trackSelectionTick) —
        // libVLC nie powiadamia Compose o zmianie, więc trzeba dociągnąć stan ręcznie.
        val audioTracks = remember(trackSelectionTick) { playerManager.audioTracks() }
        val currentAudioId = remember(trackSelectionTick) { playerManager.currentAudioTrack() }
        val subtitleTracks = remember(trackSelectionTick) {
            playerManager.subtitleTracks().filter { it.id != -1 } // -1 = "wyłączone", dodajemy ręcznie niżej
        }
        val currentSubtitleId = remember(trackSelectionTick) { playerManager.currentSubtitleTrack() }
        var audioDelayUi by remember { mutableStateOf(playerManager.audioDelayMs) }
        var subtitleDelayUi by remember { mutableStateOf(playerManager.subtitleDelayMs) }
        // Informacje o strumieniu odświeżane co 2 s, dopóki okno jest otwarte (bitrate i
        // utracone klatki zmieniają się na bieżąco).
        var streamInfo by remember { mutableStateOf<StreamInfo?>(null) }
        LaunchedEffect(Unit) {
            while (true) {
                streamInfo = runCatching { playerManager.streamInfo() }.getOrNull()
                kotlinx.coroutines.delay(2_000)
            }
        }

        TrackPickerDialog(
            audioTracks = audioTracks,
            currentAudioTrackId = currentAudioId,
            subtitleTracks = subtitleTracks,
            currentSubtitleTrackId = currentSubtitleId,
            onSelectAudio = { id ->
                playerManager.setAudioTrack(id)
                trackSelectionTick++
                // Zapamiętanie NAZWY (nie numeru id) — patrz applyTrackPreferences wyżej,
                // dlaczego numer sam w sobie nic by tu nie znaczył przy kolejnym materiale.
                audioTracks.firstOrNull { it.id == id }?.name?.let { store.setPreferredAudioLanguage(it) }
            },
            onSelectSubtitle = { id ->
                playerManager.setSubtitleTrack(id)
                trackSelectionTick++
                if (id == -1) {
                    store.setSubtitlesEnabled(false)
                } else {
                    store.setSubtitlesEnabled(true)
                    subtitleTracks.firstOrNull { it.id == id }?.name?.let { store.setPreferredSubtitleLanguage(it) }
                }
            },
            audioDelayMs = audioDelayUi,
            subtitleDelayMs = subtitleDelayUi,
            onAudioDelayChange = { ms ->
                playerManager.changeAudioDelayMs(ms)
                audioDelayUi = playerManager.audioDelayMs
            },
            onSubtitleDelayChange = { ms ->
                playerManager.changeSubtitleDelayMs(ms)
                subtitleDelayUi = playerManager.subtitleDelayMs
            },
            streamInfo = streamInfo,
            onDismiss = { showTracksDialog = false },
        )
    }

    // Przycisk EPG dla pojedynczego kanału (ikona "i") wraz z jego dialogiem CELOWO
    // USUNIĘTY — powodował ciągłe zawieszanie/zamykanie aplikacji (zgłoszenie). Pełny grid
    // EPG dostępny z Live działa dobrze i zostaje jedynym sposobem sprawdzenia programu.

    if (showExitConfirm && !isInPipMode) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(tr("home.exit.dialogTitle")) },
            text = { Text(tr("home.exit.dialogText")) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    (context as? android.app.Activity)?.finishAffinity()
                }) { Text(tr("home.exit.confirm"), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(tr("common.cancel")) }
            },
        )
    }
}

/**
 * Karta podglądu "następny/poprzedni materiał" z odliczaniem — odpowiednik "Next episode in
 * 5s" z Netflixa. Umieszczona w rogu ekranu, żeby nie zasłaniać całego obrazu, z przyciskiem
 * odtworzenia od razu i anulowania (powrót do bieżącego materiału).
 */
@Composable
private fun BoxScope.NextUpOverlay(
    targetEntry: QueueEntry,
    isNext: Boolean,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    var secondsLeft by remember(targetEntry.id, isNext) { mutableStateOf(SWITCH_CONFIRM_SECONDS) }
    LaunchedEffect(targetEntry.id, isNext) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000)
            secondsLeft--
        }
        onPlayNow()
    }

    Card(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .widthIn(max = 320.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!targetEntry.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = targetEntry.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isNext) tr("player.switchNext", secondsLeft) else tr("player.switchPrevious", secondsLeft),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    targetEntry.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onCancel) { Text(tr("common.cancel")) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onPlayNow) { Text(tr("player.playNow")) }
                }
            }
        }
    }
}

/**
 * Krótki wizualny feedback gestu jasność/głośność — ikona (słońce/głośnik) + pionowy pasek
 * wypełnienia, wyśrodkowany po TEJ stronie ekranu, po której akurat jedzie palec (lewa =
 * jasność, prawa = głośność). Znika automatycznie w momencie puszczenia palca — czyszczone
 * na null w onDragEnd/onDragCancel gestu w PlayerScreen, więc ta Composable nie potrzebuje
 * własnego timera chowania.
 */
@Composable
private fun BoxScope.GestureFeedbackOverlay(feedback: GestureFeedback) {
    Box(
        modifier = Modifier
            .align(if (feedback.isVolume) Alignment.CenterEnd else Alignment.CenterStart)
            .padding(horizontal = 32.dp)
            .width(56.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        // Pasek wypełnienia rośnie OD DOŁU — tak jak w większości appek wideo (YouTube,
        // odtwarzacze systemowe), zgodnie z intuicją "przeciągam w górę = więcej".
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(feedback.level.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(8.dp))
                .background(Accent),
        )
        Icon(
            imageVector = if (feedback.isVolume) {
                if (feedback.level <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp
            } else {
                Icons.Default.WbSunny
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(22.dp),
        )
        Text(
            text = "${(feedback.level * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

internal fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
