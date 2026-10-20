package com.iptvplayer.app.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.VLCVideoLayout

/** Dobiera bufor sieciowy (w milisekundach) do AKTUALNYCH warunków połączenia, sprawdzanych
 * raz, w momencie tworzenia odtwarzacza (nie zmienia się już w trakcie oglądania — libVLC
 * nie pozwala zmienić tego ustawienia na już otwartym strumieniu bez jego ponownego
 * otwarcia, co samo w sobie powodowałoby zauważalne przerwanie).
 *
 * Zgłoszenie: przy słabym Wi-Fi odtwarzanie nadal się przycina mimo wcześniej zwiększonego
 * bufora (1500ms -> 4000ms dla WSZYSTKICH połączeń). Tutaj: siła sygnału Wi-Fi decyduje o
 * tym, ile bufora appka użyje — słabszy sygnał dostaje WIĘKSZY zapas.
 *
 * UCZCIWE ZASTRZEŻENIE: siła sygnału Wi-Fi (jak dobrze telefon "słyszy" router) to inna
 * rzecz niż faktyczna stabilność połączenia do serwera dostawcy IPTV — to tylko PRAKTYCZNE
 * przybliżenie, nie gwarancja trafności. Całość owinięta w try-catch — jeśli COKOLWIEK się
 * nie powiedzie (np. jakieś urządzenie zwróci nieoczekiwaną wartość), appka bezpiecznie
 * wraca do poprzedniej, stałej wartości 4000ms zamiast się wywrócić.
 */
private fun computeAdaptiveNetworkCachingMs(context: Context): Int {
    return try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = connectivityManager?.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!isWifi) {
            // Zgłoszenie: appka często oglądana poza domem, na karcie SIM telefonu — sygnał
            // GSM/LTE dostaje teraz TĘ SAMĄ logikę co Wi-Fi wyżej, tylko z odrobinę wyższymi
            // wartościami na każdym poziomie (nawet "pełny zasięg" komórkowy bywa mniej
            // stabilny niż dobre Wi-Fi, stąd większy zapas na każdym szczeblu).
            //
            // UCZCIWE ZASTRZEŻENIE: w przeciwieństwie do odczytu Wi-Fi (RSSI), tutaj mam
            // NIECO mniejszą pewność co do wymagań uprawnień na różnych wersjach Androida —
            // dlatego to CAŁE zapytanie o siłę sygnału komórkowego jest owinięte we WŁASNY,
            // dodatkowy try-catch (poza tym głównym, który i tak już tu jest) — jeśli
            // cokolwiek się nie powiedzie (starszy Android, ograniczenia uprawnień,
            // nietypowe zachowanie konkretnego producenta), appka po prostu spada do
            // ostrożnej, stałej wartości 5000ms zamiast się wywrócić.
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            // API 28 (Android 9), nie 26 — TelephonyManager.getSignalStrength() pojawiło się
            // dopiero w Androidzie 9. Na Androidzie 8.x wywołanie kończyło się błędem
            // NoSuchMethodError, którego catch(Exception) niżej NIE łapie (to Error, nie
            // Exception) — czyli zamknięciem appki przy starcie odtwarzania na danych komórkowych.
            if (isCellular && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val level = telephonyManager?.signalStrength?.level // 0 (najsłabszy) .. 4 (najsilniejszy)
                    if (level != null) {
                        return when (level) {
                            4, 3 -> 4000
                            2 -> 6000
                            else -> 10000
                        }
                    }
                } catch (e: Exception) {
                    // Spadamy do stałej wartości niżej — brak sygnału GSM nie jest tu
                    // traktowane jako błąd krytyczny, tylko jako "nie da się dopasować".
                }
            }
            return 5000
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return 4000
        val rssi = wifiManager.connectionInfo.rssi
        // Statyczna, starsza wersja calculateSignalLevel (numLevels jawnie podane) — celowo
        // ta, nie nowsza (WifiManager.calculateSignalLevel(rssi) bez numLevels), bo ta
        // nowsza wymaga API 30+, a appka wspiera urządzenia od dużo wcześniejszych wersji.
        @Suppress("DEPRECATION")
        val level = WifiManager.calculateSignalLevel(rssi, 5) // 0 (najsłabszy) .. 4 (najsilniejszy)
        when (level) {
            4, 3 -> 3000
            2 -> 5000
            else -> 8000
        }
    } catch (e: Exception) {
        4000
    }
}

/**
 * Silnik odtwarzania oparty o libVLC (ten sam core co desktopowy VLC).
 *
 * To jest kluczowa różnica względem wersji webowej: zamiast elementu <video>, który jest
 * ograniczony do kodeków wbudowanych w przeglądarkę, tutaj mamy natywny dekoder obsługujący
 * H.264, H.265/HEVC (w tym 10-bit), AV1, VP8/VP9, MPEG-2, AC-3, E-AC-3, DTS, TrueHD, MKV, TS,
 * HLS, DASH itd. — bez transkodowania, niezależnie od tego co producent telefonu wspiera
 * natywnie w MediaCodec.
 */
class VlcPlayerManager(context: Context) {

    // Zapamiętany Context (application, żeby nie trzymać referencji do Activity) — potrzebny
    // w play() do otwierania deskryptorów plików lokalnych (patrz niżej), nie tylko przy
    // konstruowaniu LibVLC.
    private val appContext = context.applicationContext

    // Obliczone RAZ, przy tworzeniu odtwarzacza — patrz obszerny komentarz przy
    // computeAdaptiveNetworkCachingMs powyżej.
    private val adaptiveNetworkCachingMs = computeAdaptiveNetworkCachingMs(appContext)

    /** Bazowy bufor sieciowy — PlayerActivity zwiększa go przy kolejnych zacięciach tego
     * samego materiału (patrz reconnectStream w PlayerScreen). */
    val baseNetworkCachingMs: Int get() = adaptiveNetworkCachingMs

    // --- Stabilność odtwarzania: sygnały z silnika do PlayerActivity ---
    // Zgłoszenie: "odtwarzanie często się zatrzymuje, trzeba zamknąć i otworzyć ponownie".
    // Wcześniej appka nasłuchiwała TYLKO zdarzenia "Playing" — błąd strumienia
    // (EncounteredError) i urwanie strumienia (EndReached przy Live) przechodziły bez
    // żadnej reakcji, a jedyną obroną był watchdog w PlayerActivity (reaguje dopiero po
    // 10 s bez ruchu obrazu). Teraz te zdarzenia są od razu przekazywane dalej, więc
    // ponowne połączenie startuje natychmiast, a nie po kilkunastu sekundach.
    /** "error" = błąd strumienia, "ended" = strumień się zakończył/urwał. */
    var onStreamProblem: ((String) -> Unit)? = null
    /** true = silnik buforuje (brakuje danych), false = bufor pełny, gra normalnie. */
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    /** Sieć wróciła po wcześniejszej utracie — dobry moment na natychmiastowe wznowienie. */
    var onNetworkRestored: (() -> Unit)? = null

    // Blokada Wi-Fi w trybie wysokiej wydajności na czas odtwarzania — Android (szczególnie
    // na przystawkach TV i telefonach w trybie oszczędzania energii) potrafi usypiać kartę
    // Wi-Fi między pakietami, co przy strumieniu wideo objawia się właśnie cyklicznymi
    // przycięciami co kilka minut. To samo robi ExoPlayer (setWakeMode NETWORK) i aplikacja
    // VLC. Uprawnienie WAKE_LOCK appka już ma w manifeście.
    private val wifiLock: WifiManager.WifiLock? = runCatching {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wm?.createWifiLock(mode, "VivaPilot:playback")?.apply { setReferenceCounted(false) }
    }.getOrNull()

    private fun acquireWifiLock() { runCatching { wifiLock?.let { if (!it.isHeld) it.acquire() } } }
    private fun releaseWifiLock() { runCatching { wifiLock?.let { if (it.isHeld) it.release() } } }

    // Nasłuch zmian sieci — po UTRACIE i ponownym POJAWIENIU się połączenia (np. router
    // się zrestartował, Wi-Fi na chwilę zniknęło, przełączenie Wi-Fi <-> LTE) appka od razu
    // wznawia strumień, zamiast czekać, aż watchdog zauważy zamrożony obraz.
    private var networkWasLost = false
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: android.net.Network) {
            networkWasLost = true
        }
        override fun onAvailable(network: android.net.Network) {
            if (networkWasLost) {
                networkWasLost = false
                // Krótkie opóźnienie — zaraz po "onAvailable" połączenie bywa jeszcze
                // niegotowe (DHCP/DNS), natychmiastowa próba często by się nie udała.
                mainHandler.postDelayed({ onNetworkRestored?.invoke() }, 1500L)
            }
        }
    }
    private var networkCallbackRegistered = false

    private val libVLC: LibVLC = LibVLC(
        appContext,
        arrayListOf(
            // USUNIĘTE na próbę: "--no-drop-late-frames" i "--no-skip-frames" — obie flagi
            // BLOKUJĄ dekoderowi możliwość pomijania/odrzucania klatek podczas nadrabiania
            // zaległości (sensowne dla transmisji live, żeby nic nie "zniknęło"). Ale
            // PRZEWIJANIE z definicji polega na pominięciu fragmentu materiału — jeśli
            // libVLC traktuje strumień bez znanej długości podobnie do live (co tłumaczyłoby
            // WSZYSTKIE dotychczasowe objawy: brak durationMs, nietrzymające się przewijanie,
            // niedziałające wznowienie), te dwie flagi mogły nieumyślnie kolidować z samym
            // mechanizmem przewijania właśnie dla TEGO typu strumieni. To już piąta próba w
            // tym temacie (poprzednie: czas ustawienia pozycji, powtórzenia, dekoder
            // sprzętowy x2) — żadna dotąd nie pomogła, więc pewność co do tej też jest
            // ograniczona, ale to bezpośrednio powiązana z mechaniką przewijania hipoteza,
            // której jeszcze nie sprawdziłem.
            "--rtsp-tcp",
            // Kolejna próba (szósta) — potwierdzenie z zewnętrznego researchu (AI Mode):
            // przy durationMs=0 odtwarzacz prawdopodobnie traktuje materiał jak transmisję
            // na żywo (bez końca osi czasu), co tłumaczy WSZYSTKIE dotychczasowe objawy
            // naraz. --input-fast-seek włącza mniej precyzyjne, ale bardziej niezawodne
            // przewijanie oparte o klatki kluczowe zamiast dokładnego czasu — czasem pomaga
            // właśnie w przypadkach, gdy silnik ma trudność z przewijaniem strumieni bez
            // jasno zasygnalizowanej struktury/długości.
            "--input-fast-seek",
            // ZMIENIONE ze stałych 4000ms na wartość DOBRANĄ DO aktualnej siły sygnału
            // Wi-Fi (patrz computeAdaptiveNetworkCachingMs) — zgłoszenie: przy słabym Wi-Fi
            // nadal się przycinało mimo wcześniej zwiększonego, ale STAŁEGO dla wszystkich
            // bufora. Silny sygnał dostaje mniejszy bufor (szybszy start), słaby — większy
            // (więcej odporności na przycinanie), koszt: odrobinę dłuższy start przy słabym
            // połączeniu.
            "--network-caching=$adaptiveNetworkCachingMs",
            // Automatyczne próby ponownego połączenia przy zerwaniu strumienia HTTP — inny
            // przypadek niż samo "wolne" połączenie: to dotyczy sytuacji, gdy połączenie na
            // chwilę faktycznie PADA (nie tylko zwalnia), a nie tylko buforuje mniej danych.
            // String przekazywany do silnika (nie wywołanie API Kotlina) — nawet jeśli
            // nazwa okaże się niepoprawna dla tej wersji, zostanie po prostu zignorowana
            // przy starcie, bez ryzyka błędu kompilacji.
            "--http-reconnect",
            "--avcodec-hw=any",         // preferuj dekodowanie sprzętowe, fallback software — przywrócone: eksperyment "none" nie zmienił niczego w zgłoszonym problemie, a "any" jest lepsze dla wydajności/baterii
            // Wymuszenie standardowego backendu audio Androida (AudioTrack) zamiast
            // pozostawienia libVLC własnego, domyślnego wyboru (zwykle OpenSLES/AAudio na
            // nowszych urządzeniach) — zgłoszenie: podłączony zewnętrzny głośnik Bluetooth
            // w ogóle nie dostawał dźwięku. To znany, udokumentowany problem w społeczności
            // VLC-Android — część backendów audio omija standardowy system routingu
            // Androida, przez co Bluetooth (i inne przełączanie wyjścia audio na poziomie
            // systemu) nie działa poprawnie. android_audiotrack korzysta z tego samego,
            // standardowego mechanizmu co pozostałe aplikacje multimedialne na Androidzie.
            "--aout=android_audiotrack",
        )
    )

    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Wołane, gdy libVLC faktycznie ZACZYNA odtwarzać nowy materiał (MediaPlayer.Event.
    // Playing) — to jest pierwszy moment, kiedy audioTracks()/subtitleTracks() w ogóle
    // zwracają cokolwiek sensownego (zaraz po samym play() lista ścieżek jeszcze nie
    // istnieje, bo libVLC dopiero zaczyna parsować plik). Używane przez PlayerActivity do
    // zastosowania zapamiętanych preferencji języka/napisów (patrz applyTrackPreferences).
    var onPlaybackStarted: (() -> Unit)? = null

    // Zgłoszenie: użytkownik nie wiedział, że appka W OGÓLE coś robi podczas oczekiwania na
    // ustabilizowanie się .position przy wznowieniu (może potrwać kilka sekund) — bez
    // żadnego wskaźnika wyglądało to jak "zawieszenie się", co kusiło do naciskania
    // przycisków w międzyczasie. Ten callback informuje PlayerActivity, kiedy pokazać/ukryć
    // kółko ładowania na środku ekranu — true na czas oczekiwania w
    // waitForStableDurationAndSeek, false zaraz po wykonaniu (albo poddaniu się) seeka.
    var onCalibratingChanged: ((Boolean) -> Unit)? = null

    // Pozycja startowa "w oczekiwaniu" na ustawienie — patrz komentarz w play() niżej.
    // Zgłoszenie: "kontynuuj oglądanie" w serialach zawsze zaczynało od zera, mimo że
    // appka POPRAWNIE odnajdywała zapisaną pozycję i przekazywała ją tutaj (potwierdzone
    // nakładką diagnostyczną: resumeMs=24120, ale rzeczywista pozycja odtwarzania i tak
    // zaczynała się od ~0). Przyczyna: mediaPlayer.time = X ustawiane od razu po play()
    // było najprawdopodobniej ignorowane przez libVLC, bo medium w tym momencie JESZCZE
    // nie jest gotowe do przewijania (play() startuje proces otwierania/buforowania
    // ASYNCHRONICZNIE — samo wywołanie funkcji nie oznacza, że odtwarzacz już COKOLWIEK
    // otworzył). Naprawa: ustawiamy pozycję DOPIERO gdy nadejdzie zdarzenie Playing niżej —
    // to pierwszy moment, kiedy odtwarzacz na pewno akceptuje przewijanie.
    private var pendingSeekMs: Long = 0L

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
        mediaPlayer.setEventListener(object : MediaPlayer.EventListener {
            override fun onEvent(event: MediaPlayer.Event) {
                // Zdarzenia problemów ze strumieniem — patrz onStreamProblem/onBufferingChanged
                // wyżej. Wszystko przez mainHandler, bo libVLC woła ten listener z własnego,
                // natywnego wątku, a odbiorcy (PlayerScreen) dotykają stanu Compose.
                when (event.type) {
                    MediaPlayer.Event.EncounteredError ->
                        mainHandler.post { onStreamProblem?.invoke("error") }
                    MediaPlayer.Event.EndReached ->
                        mainHandler.post { onStreamProblem?.invoke("ended") }
                    MediaPlayer.Event.Buffering -> {
                        val buffering = event.buffering < 100f
                        mainHandler.post { onBufferingChanged?.invoke(buffering) }
                    }
                    MediaPlayer.Event.Playing ->
                        mainHandler.post {
                            onBufferingChanged?.invoke(false)
                            reapplyDelays()
                        }
                }
                if (event.type == MediaPlayer.Event.Playing) {
                    // libVLC wywołuje listener z WŁASNEGO wątku natywnego, nie z głównego —
                    // Handler.post() gwarantuje, że onPlaybackStarted (a przez niego każdy
                    // kod, który mogłby dotknąć stanu Compose) zawsze wykona się na głównym
                    // wątku, niezależnie skąd faktycznie przyszło to zdarzenie.
                    val seekMs = pendingSeekMs
                    pendingSeekMs = 0L
                    mainHandler.post {
                        // ZMIENIONE — potwierdzone narzędziem diagnostycznym (patrz seekTo
                        // wyżej po pełne wyjaśnienie): .time nie trzyma nowej pozycji dla
                        // materiałów bez znanego czasu trwania, .position TAK. Gdy .length
                        // > 0 (prawdziwa długość znana), .time nadal działa niezawodnie —
                        // zostaje bez zmian, z tymi samymi powtórzeniami co dotychczas. Gdy
                        // .length <= 0, w TYM momencie (zaraz po Playing) .position/.time
                        // jeszcze nie miały czasu ustalić żadnego sensownego stosunku między
                        // sobą (odtwarzanie dopiero się zaczęło) — więc odkładamy próbę o
                        // 1 sekundę, dając normalnemu odtwarzaniu od zera chwilę na
                        // "rozpędzenie się", zanim policzymy domniemaną długość i przewiniemy
                        // przez .position. Oznacza to króciutki, około-sekundowy błysk
                        // początku materiału przed doskokiem do zapamiętanej pozycji — mniej
                        // wygodne niż natychmiastowe wznowienie, ale wciąż nieporównywalnie
                        // lepsze niż "kontynuuj oglądanie" zawsze zaczynające od zera.
                        if (seekMs > 0) {
                            if (mediaPlayer.length > 0) {
                                mediaPlayer.time = seekMs
                                listOf(400L, 900L, 1800L).forEach { delayMs ->
                                    mainHandler.postDelayed({ mediaPlayer.time = seekMs }, delayMs)
                                }
                            } else {
                                // ZMIENIONE — pojedyncza próba po 1 sekundzie lądowała w
                                // KOMPLETNIE złym miejscu (zgłoszenie: cel 10:04, wylądowało
                                // na 39:27!). Przyczyna: przy tak małej wartości .position
                                // (ułamek sekundy po starcie) DZIELENIE przez nią wzmacnia
                                // KAŻDĄ niedokładność odczytu do absurdalnych rozmiarów —
                                // klasyczny problem numeryczny przy dzieleniu przez bardzo
                                // małą liczbę. Naprawa: waitForStableDurationAndSeek niżej
                                // NIE ufa jednemu odczytowi — czeka, aż DWA kolejne pomiary
                                // (co pół sekundy) dadzą do siebie PODOBNY wynik, zanim w
                                // ogóle spróbuje przewinąć.
                                onCalibratingChanged?.invoke(true)
                                waitForStableDurationAndSeek(seekMs)
                            }
                        }
                        // Zabezpieczenie — zgłoszenie: czasem trzeba było ręcznie nacisnąć
                        // play po uruchomieniu materiału, mimo że play() jest już wołane w
                        // openMedia(). Wywołanie play() na materiale, który już gra, jest
                        // bezpiecznym no-opem — to tylko dodatkowa gwarancja na wypadek
                        // jakiegoś skrajnego przypadku, w którym libVLC pozostawił sesję w
                        // stanie wstrzymanym mimo wcześniejszego wywołania.
                        mediaPlayer.play()
                        onPlaybackStarted?.invoke()
                    }
                }
            }
        })
    }

    // Otwarty deskryptor pliku dla bieżąco odtwarzanego pliku LOKALNEGO (content://, patrz
    // play() niżej) — musi zostać żywy przez CAŁY czas odtwarzania, bo libVLC czyta z niego
    // na bieżąco (w przeciwieństwie do zwykłego URI, gdzie nie trzeba nic samemu trzymać
    // otwartego). Zamykamy go dopiero przy kolejnym play() albo przy release(), nigdy od razu
    // po utworzeniu Media — zamknięcie za wcześnie objawiałoby się identycznie jak brak
    // odtwarzania w ogóle (czarny ekran, 0:00).
    private var openLocalFileDescriptor: android.os.ParcelFileDescriptor? = null

    private fun closeLocalFileDescriptor() {
        try {
            openLocalFileDescriptor?.close()
        } catch (e: Exception) {
            // nieistotne — plik i tak zaraz przestaje być używany
        }
        openLocalFileDescriptor = null
    }

    fun attachToView(layout: VLCVideoLayout) {
        mediaPlayer.attachViews(layout, null, false, false)
    }

    fun detachFromView() {
        mediaPlayer.detachViews()
    }

    /**
     * Odtwarza strumień/plik. Wspiera opcjonalny User-Agent/Referer — odpowiednik
     * #EXTVLCOPT:http-user-agent / http-referrer z playlist M3U wymagających autoryzacji.
     */
    // Prawdziwa INTENCJA użytkownika (chce, żeby leciało, czy chce pauzy) — NIEZALEŻNA od
    // chwilowego stanu silnika mediaPlayer.isPlaying(), który podczas problemów z siecią
    // potrafi migać między true/false (buforowanie, wewnętrzne wstrzymanie przy zerwanym
    // połączeniu) BEZ ŻADNEGO udziału użytkownika. Scentralizowane właśnie TUTAJ (a nie jako
    // osobna flaga w każdym miejscu PlayerActivity, które woła pause()/resume()) — WSZYSTKIE
    // miejsca w appce (przycisk play/pauza, blokada ekranu, PiP, słuchawki Bluetooth/ekran
    // blokady systemu) i tak wołają wyłącznie te dwie metody, więc to jedyne miejsce, przez
    // które MUSI przejść każda zmiana stanu — naturalny, pojedynczy punkt prawdy. Używane
    // przez "watchdog zacinania" w PlayerActivity, żeby odróżnić: "użytkownik świadomie
    // zapauzował" (nie naprawiaj niczego) od "silnik przestał grać SAM, bez pytania" (to
    // właśnie objaw zacięcia/problemu z siecią, który watchdog ma naprawić).
    var isUserPaused: Boolean = false
        private set

    /**
     * @param networkCachingMs bufor sieciowy TYLKO dla tego jednego otwarcia strumienia
     *   (null = bazowy, dobrany do siły sygnału). Zwiększany przy kolejnych zacięciach tego
     *   samego materiału — patrz reconnectStream w PlayerScreen.
     * @param forceSoftwareDecoding true = dekodowanie programowe zamiast sprzętowego. Część
     *   telewizorów/przystawek ma dekodery sprzętowe, które "zawieszają się" po nieciągłości
     *   w strumieniu IPTV (obraz stoi, dźwięk czasem gra) — gdy ten sam materiał zacina się
     *   kilka razy z rzędu, PlayerScreen przełącza go na dekodowanie programowe.
     */
    fun play(
        url: String,
        userAgent: String? = null,
        referer: String? = null,
        startPositionMs: Long = 0L,
        networkCachingMs: Int? = null,
        forceSoftwareDecoding: Boolean = false,
    ) {
        isUserPaused = false
        acquireWifiLock()
        // Poprzedni lokalny plik (jeśli grał) trzeba zamknąć PRZED otwarciem następnego —
        // inaczej wycieklibyśmy deskryptory przy każdym kolejnym odcinku/pliku z folderu.
        closeLocalFileDescriptor()

        val media = if (url.startsWith("content://")) {
            // Pliki dodane przez zakładkę Folder (Storage Access Framework) mają URI
            // "content://…", którego natywny silnik libVLC NIE potrafi otworzyć bezpośrednio —
            // nie zna Android ContentResolvera, rozumie tylko ścieżki plików i protokoły
            // sieciowe (http/rtsp itd.). Bez tej gałęzi Media(libVLC, Uri) po prostu cicho
            // nie odtwarza nic (czarny ekran, 0:00) — dokładnie tak jak zgłoszono. Rozwiązanie:
            // otworzyć deskryptor pliku samemu przez ContentResolver i przekazać JEGO, a nie
            // sam URI — to jest dokładnie to, co robi też sam VLC dla plików z SAF.
            val uri = android.net.Uri.parse(url)
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Nie udało się otworzyć pliku lokalnego: $url")
            openLocalFileDescriptor = pfd
            Media(libVLC, pfd.fileDescriptor)
        } else {
            Media(libVLC, android.net.Uri.parse(url))
        }
        userAgent?.let { media.addOption(":http-user-agent=$it") }
        referer?.let { media.addOption(":http-referrer=$it") }
        // Przywrócone na true — eksperyment z wyłączeniem (false) nie zmienił niczego w
        // zgłoszonym problemie z przewijaniem, więc wraca lepsza dla wydajności/baterii
        // wartość domyślna, żeby nie kumulować nieskutecznych eksperymentów.
        media.setHWDecoderEnabled(!forceSoftwareDecoding, false)
        networkCachingMs?.let { media.addOption(":network-caching=$it") }
        lastCachingMs = networkCachingMs ?: adaptiveNetworkCachingMs
        lastSoftwareDecoding = forceSoftwareDecoding
        // DRUGA, ostrożniejsza próba (pierwsza z Media.Parse.ParseNetwork nie skompilowała
        // się — ta stała nie istnieje w tej wersji biblioteki). Tym razem: WERSJA BEZ ŻADNYCH
        // DODATKOWYCH STAŁYCH — parseAsync() bez argumentów to znacznie prostsza,
        // dłużej obecna część API, mniejsza szansa na kolejny błąd kompilacji, bo nie
        // odwołuje się do niczego poza samą nazwą metody. Cel ten sam: skłonić libVLC do
        // aktywnego rozpoznania metadanych (czas trwania) zamiast czekać, aż demuxer
        // natrafi na nie "przy okazji" podczas zwykłego odtwarzania — dotyczy głównie
        // seriali, gdzie durationMs bywa 0 nawet po dłuższej chwili. Nadal NIE mam
        // możliwości potwierdzenia, że to coś zmieni — to kolejna próba, nie pewna poprawka.
        runCatching { media.parseAsync() }
        mediaPlayer.media = media
        media.release()
        // Pozycja startowa NIE jest ustawiana tutaj wprost — patrz pendingSeekMs i obszerny
        // komentarz przy nim wyżej (zgłoszenie: "kontynuuj oglądanie" zawsze zaczynało od
        // zera, mimo poprawnie odnajdywanej zapisanej pozycji). Zapisujemy ją tylko do
        // "zapamiętania" — faktyczne mediaPlayer.time nastąpi dopiero w zdarzeniu Playing
        // w init wyżej, gdy odtwarzacz na pewno jest gotowy do przewijania.
        pendingSeekMs = startPositionMs
        mediaPlayer.play()
    }

    fun pause() { isUserPaused = true; mediaPlayer.pause() }
    fun resume() { isUserPaused = false; mediaPlayer.play() }
    fun stop() = mediaPlayer.stop()

    /** Prawdziwa długość, jeśli libVLC ją zna (.length > 0) — a jeśli nie, DOMNIEMANA
     * długość wyliczona ze stosunku .position (ułamek 0.0–1.0) do .time (bieżąca pozycja w
     * ms) W TYM MOMENCIE odtwarzania. Potwierdzone narzędziem diagnostycznym na materiale z
     * durationMs=0: .position TRZYMA nową pozycję (w przeciwieństwie do .time, które jest
     * ignorowane), a stosunek position/time w danej chwili konsekwentnie ujawnia prawdziwą,
     * nigdy niezgłoszoną przez .length długość materiału (potwierdzone trzema niezależnymi
     * pomiarami w trakcie testów — wszystkie w granicach ~43–44 minut dla tego samego
     * odcinka). Zwraca 0, jeśli .position jest jeszcze zbyt bliskie zeru, żeby dawało
     * sensowny wynik (np. w pierwszej chwili po starcie odtwarzania).
     */
    fun impliedDurationMs(): Long {
        val real = mediaPlayer.length
        if (real > 0) return real
        val pos = mediaPlayer.position
        val time = mediaPlayer.time
        // PRÓG PRZYWRÓCONY do bezpieczniejszej wartości — poprzednie obniżenie (do 0.00005)
        // było BŁĘDEM: pozwalało na policzenie czegokolwiek, ale przy tak mikroskopijnej
        // wartości .position DZIELENIE wzmacnia każdą niedokładność odczytu do absurdalnych
        // rozmiarów (zgłoszenie: cel 10:04, appka wylądowała na 39:27 — to właśnie ten
        // mechanizm). Prawdziwym zabezpieczeniem przed zbyt wczesnym, niestabilnym odczytem
        // jest teraz waitForStableDurationAndSeek niżej (czeka na DWA spójne pomiary), a nie
        // sam próg tutaj — próg wraca więc do wartości, przy której POJEDYNCZY odczyt ma w
        // ogóle sens liczbowo.
        return if (pos > 0.003f && time > 0) (time / pos).toLong() else 0L
    }

    /** Odpowiedź na to, że POJEDYNCZY, zbyt wczesny odczyt .position/.time potrafi dać
     * kompletnie błędny wynik (patrz komentarz w impliedDurationMs) — więc zamiast ufać
     * jednej próbie po stałym opóźnieniu, ta funkcja odpytuje co pół sekundy i przewija
     * DOPIERO gdy dwa kolejne pomiary dają do siebie PODOBNY wynik (różnica poniżej 15%) —
     * to bezpośrednio sprawdza stabilność, zamiast zgadywać "ile czasu to wystarczająco
     * dużo". Poddaje się po ~10 sekundach (ostatni dostępny wynik, nawet niepewny, jest
     * lepszy niż nic) — w praktyce dla typowych materiałów stabilizuje się dużo szybciej. */
    private fun waitForStableDurationAndSeek(targetMs: Long, attempt: Int = 0, lastImplied: Long = 0L) {
        val duration = impliedDurationMs()
        if (duration > 0 && lastImplied > 0) {
            val ratio = duration.toFloat() / lastImplied.toFloat()
            if (ratio in 0.85f..1.15f) {
                mediaPlayer.position = (targetMs.toFloat() / duration).coerceIn(0f, 1f)
                onCalibratingChanged?.invoke(false)
                return
            }
        }
        if (attempt >= 20) {
            // ~10 sekund minęło bez dwóch spójnych pomiarów — używamy ostatniego dostępnego
            // wyniku (jeśli jest), zamiast czekać w nieskończoność.
            if (duration > 0) mediaPlayer.position = (targetMs.toFloat() / duration).coerceIn(0f, 1f)
            onCalibratingChanged?.invoke(false)
            return
        }
        mainHandler.postDelayed({ waitForStableDurationAndSeek(targetMs, attempt + 1, duration) }, 500L)
    }

    fun seekTo(positionMs: Long) {
        // ZMIENIONE — potwierdzone narzędziem diagnostycznym: .time (poprzednie podejście,
        // razem z powtórzeniami niżej) NIE trzyma nowej pozycji dla materiałów bez znanego
        // czasu trwania, podczas gdy .position (ułamek) TRZYMA. Gdy .length > 0 (prawdziwa
        // długość znana — filmy, część seriali), .time nadal działa niezawodnie i zostaje
        // bez zmian. Gdy .length <= 0, liczymy ułamek względem DOMNIEMANEJ długości
        // (impliedDurationMs) i przewijamy przez .position zamiast .time.
        val duration = impliedDurationMs()
        if (mediaPlayer.length > 0) {
            mediaPlayer.time = positionMs
            mainHandler.postDelayed({ mediaPlayer.time = positionMs }, 300L)
        } else if (duration > 0) {
            mediaPlayer.position = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            // Domniemana długość jeszcze nieznana (za wcześnie po starcie odtwarzania) —
            // .time i tak się nie utrzyma, ale to jedyna dostępna w tym momencie opcja.
            mediaPlayer.time = positionMs
        }
    }
    fun currentPositionMs(): Long = mediaPlayer.time
    fun durationMs(): Long = mediaPlayer.length
    fun isPlaying(): Boolean = mediaPlayer.isPlaying

    /**
     * Dopasowanie obrazu do ekranu:
     * - FIT (domyślnie): oryginalne proporcje materiału, ewentualne czarne pasy góra/dół
     *   albo po bokach — standardowe zachowanie libVLC (aspectRatio=null, scale=0).
     * - FILL: obraz rozciągnięty tak, żeby wypełnić CAŁY dostępny obszar bez czarnych pasów —
     *   robione przez wymuszenie na materiale proporcji aktualnego okna wideo
     *   (aspectRatio = "szerokośćOkna:wysokośćOkna" w pikselach), więc dla materiału o innych
     *   proporcjach niż ekran wygląda to na rozciągnięcie/ściśnięcie obrazu, nie przycięcie.
     *   Świadomy wybór: dokładne "przytnij, żeby wypełnić" (bez rozciągania) wymagałoby
     *   znajomości RZECZYWISTYCH wymiarów piksela strumienia wideo, których libVLC nie
     *   wystawia tu w prosty, niezawodny sposób (ten sam powód, dla którego PiP używa
     *   stałego 16:9 zamiast realnych wymiarów strumienia, patrz PlayerActivity.buildPipParams).
     */
    fun setScaleMode(mode: VideoScaleMode, viewWidthPx: Int, viewHeightPx: Int) {
        when (mode) {
            VideoScaleMode.FIT -> {
                mediaPlayer.setAspectRatio(null)
                mediaPlayer.setScale(0f)
            }
            VideoScaleMode.FILL -> {
                if (viewWidthPx > 0 && viewHeightPx > 0) {
                    mediaPlayer.setAspectRatio("$viewWidthPx:$viewHeightPx")
                    mediaPlayer.setScale(0f)
                }
            }
        }
    }

    /** Dostępne ścieżki audio (do przełącznika w UI, tak jak w plannowanym menu ustawień odtwarzacza). */
    fun audioTracks(): List<MediaPlayer.TrackDescription> = mediaPlayer.audioTracks?.toList() ?: emptyList()
    fun setAudioTrack(id: Int) { mediaPlayer.setAudioTrack(id) }
    fun currentAudioTrack(): Int = mediaPlayer.audioTrack

    /** Dostępne ścieżki napisów. */
    fun subtitleTracks(): List<MediaPlayer.TrackDescription> = mediaPlayer.spuTracks?.toList() ?: emptyList()
    fun setSubtitleTrack(id: Int) { mediaPlayer.setSpuTrack(id) }
    fun currentSubtitleTrack(): Int = mediaPlayer.spuTrack

    // --- Synchronizacja dźwięku i napisów z obrazem ---
    // Częsty problem w IPTV: dźwięk albo napisy wyprzedzają/spóźniają się względem obrazu.
    // Wartości w milisekundach (dodatnia = opóźnij, ujemna = przyspiesz); libVLC przyjmuje
    // mikrosekundy. Zapamiętane tutaj i nakładane ponownie przy każdym starcie odtwarzania
    // (patrz reapplyDelays w zdarzeniu Playing) — silnik zeruje je przy nowym strumieniu,
    // a przecież po automatycznym ponownym połączeniu przesunięcie ma zostać.
    var audioDelayMs: Long = 0L
        private set
    var subtitleDelayMs: Long = 0L
        private set

    fun changeAudioDelayMs(value: Long) {
        audioDelayMs = value.coerceIn(-5_000L, 5_000L)
        runCatching { mediaPlayer.setAudioDelay(audioDelayMs * 1000L) }
    }

    fun changeSubtitleDelayMs(value: Long) {
        subtitleDelayMs = value.coerceIn(-10_000L, 10_000L)
        runCatching { mediaPlayer.setSpuDelay(subtitleDelayMs * 1000L) }
    }

    private fun reapplyDelays() {
        if (audioDelayMs != 0L) runCatching { mediaPlayer.setAudioDelay(audioDelayMs * 1000L) }
        if (subtitleDelayMs != 0L) runCatching { mediaPlayer.setSpuDelay(subtitleDelayMs * 1000L) }
    }

    // Ustawienia ostatniego otwarcia strumienia — do panelu "Informacje o strumieniu".
    private var lastCachingMs: Int = adaptiveNetworkCachingMs
    private var lastSoftwareDecoding: Boolean = false

    /** Migawka technicznych informacji o bieżącym strumieniu (rozdzielczość, kodek, bitrate,
     * utracone klatki) — przydatne przy diagnozowaniu zacięć ("czy to słaby sygnał, czy
     * po prostu bardzo ciężki strumień 4K?"). Każde pole osobno w runCatching — część
     * strumieni/urządzeń nie udostępnia niektórych danych, wtedy po prostu ich nie pokazujemy. */
    fun streamInfo(): StreamInfo {
        var width = 0
        var height = 0
        var codec: String? = null
        var fps = 0f
        runCatching {
            val track = mediaPlayer.currentVideoTrack
            if (track != null) {
                width = track.width
                height = track.height
                codec = track.codec
                if (track.frameRateDen > 0) fps = track.frameRateNum.toFloat() / track.frameRateDen
            }
        }
        var bitrateKbps = 0
        var lostFrames = 0
        runCatching {
            val media = mediaPlayer.media
            if (media != null) {
                val stats = media.stats
                if (stats != null) {
                    // Ten sam przelicznik co w desktopowym VLC (okno "Informacje o mediach").
                    bitrateKbps = (stats.inputBitrate * 8000f).toInt()
                    lostFrames = stats.lostPictures
                }
                media.release()
            }
        }
        return StreamInfo(
            width = width,
            height = height,
            codec = codec,
            fps = fps,
            bitrateKbps = bitrateKbps,
            lostFrames = lostFrames,
            networkCachingMs = lastCachingMs,
            softwareDecoding = lastSoftwareDecoding,
        )
    }

    fun release() {
        onStreamProblem = null
        onBufferingChanged = null
        onNetworkRestored = null
        releaseWifiLock()
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        closeLocalFileDescriptor()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
    }
}

/** Techniczne informacje o strumieniu — patrz VlcPlayerManager.streamInfo(). 0/null = brak danych. */
data class StreamInfo(
    val width: Int,
    val height: Int,
    val codec: String?,
    val fps: Float,
    val bitrateKbps: Int,
    val lostFrames: Int,
    val networkCachingMs: Int,
    val softwareDecoding: Boolean,
)

/** Tryb dopasowania obrazu do ekranu — patrz VlcPlayerManager.setScaleMode. */
enum class VideoScaleMode { FIT, FILL }
