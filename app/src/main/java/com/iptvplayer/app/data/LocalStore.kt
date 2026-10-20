package com.iptvplayer.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Prosty magazyn lokalny na SharedPreferences + JSON (Gson) — celowo utrzymany tak samo
 * "płasko" jak localStorage w app.js (klucze: playlists, favorites, watchProgress, settings),
 * żeby logika przenoszenia danych między wersją web i Android była 1:1 zrozumiała.
 */
class LocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("iptv_store", Context.MODE_PRIVATE)
    // Rejestr zgód (VivaPilot 1.12, punkt 7 promptu) — lista zdarzeń zgody w ZASZYFROWANYM
    // pliku w prywatnej pamięci appki, patrz ConsentVault.kt i ConsentEvent.kt. Żadna funkcja
    // appki nie czyści tego pliku ani nie edytuje zapisanych zdarzeń (tylko dopisywanie).
    private val consentVault = ConsentVault(appContext)
    // Zgody z ekranu przed zakupem, które czekają na wykrycie udanego zakupu — OSOBNY plik
    // prywatnych preferencji (nie "iptv_store"), wyłączony z automatycznej kopii Androida
    // (res/xml/backup_rules.xml), żeby po przywróceniu appki na innym urządzeniu nie zostały
    // cudze, nieaktualne znaczniki czasu. To NIE jest rejestr zgód — zgoda wchodzi do rejestru
    // dopiero po udanym zakupie (patrz recordPurchaseConsent).
    private val pendingPrefs = appContext.getSharedPreferences(PENDING_PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        // Jednorazowe sprzątanie po usunięciu integracji TMDB z aplikacji: starsze instalacje
        // mogły mieć zapisany klucz API TMDB w Ustawieniach — appka już go nigdzie nie czyta,
        // ale zostawiony w SharedPreferences byłby martwymi, poufnymi danymi. `remove()` na
        // nieistniejącym kluczu jest bezpieczne/tanie, więc nie trzeba osobnej flagi "czy już
        // posprzątano" — to wywołanie jest no-opem po pierwszym uruchomieniu nowej wersji.
        if (prefs.contains(KEY_LEGACY_TMDB_API_KEY)) {
            prefs.edit().remove(KEY_LEGACY_TMDB_API_KEY).apply()
        }
        // Jednorazowe sprzątanie po zastąpieniu starego ekranu "Zanim zaczniesz"
        // (WithdrawalWaiverScreen, jedno potwierdzenie o utracie prawa odstąpienia) nowym
        // ekranem powitalnym OnboardingScreen (jeden checkbox: pełnoletność 18+ / Regulamin /
        // Polityka, z zapisem numeru wersji Regulaminu) — patrz hasAcceptedOnboarding()
        // niżej. CELOWO nie migrujemy starej flagi na nową: to inne, szersze potwierdzenie
        // (obejmuje też Regulamin w konkretnej wersji), więc nawet dotychczasowi
        // użytkownicy muszą zobaczyć i zaakceptować nowy ekran raz.
        if (prefs.contains(KEY_LEGACY_WITHDRAWAL_WAIVER_ACK)) {
            prefs.edit().remove(KEY_LEGACY_WITHDRAWAL_WAIVER_ACK).apply()
        }
        // Do wersji 1.01 akceptacja ekranu powitalnego (i tekst "16 lat") była zapisywana w
        // "iptv_store". Od 1.02 jedynym źródłem prawdy jest rejestr zgód (consentVault), a
        // stare wpisy są sprzątane. CELOWO bez migracji: wcześniejsza akceptacja dotyczyła
        // innego oświadczenia (16 lat, inna wersja Regulaminu), więc Użytkownik musi
        // zaakceptować ekran powitalny w nowej treści (18 lat) raz jeszcze.
        if (prefs.contains(KEY_ONBOARDING_ACCEPTED) || prefs.contains(KEY_PREPURCHASE_CONFIRMED_AT)) {
            prefs.edit()
                .remove(KEY_ONBOARDING_ACCEPTED)
                .remove(KEY_ONBOARDING_ACCEPTED_AT)
                .remove(KEY_ONBOARDING_ACCEPTED_REGULAMIN_VERSION)
                .remove(KEY_PREPURCHASE_CONFIRMED_AT)
                .apply()
        }
    }

    // --- Playlists ---
    fun getPlaylists(): List<PlaylistSource> =
        prefs.getString(KEY_PLAYLISTS, null)?.let {
            gson.fromJson(it, object : TypeToken<List<PlaylistSource>>() {}.type)
        } ?: emptyList()

    fun savePlaylists(list: List<PlaylistSource>) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(list)).apply()
    }

    // --- Favorites (set of channel/episode ids) ---
    fun getFavoriteIds(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun setFavorite(id: String, favorite: Boolean) {
        val current = getFavoriteIds().toMutableSet()
        if (favorite) current += id else current -= id
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    // --- Niestandardowe listy użytkownika (w tym wbudowana "favorites") ---
    fun getLists(): List<CustomList> =
        prefs.getString(KEY_LISTS, null)?.let {
            gson.fromJson(it, object : TypeToken<List<CustomList>>() {}.type)
        } ?: emptyList()

    fun saveLists(lists: List<CustomList>) {
        prefs.edit().putString(KEY_LISTS, gson.toJson(lists)).apply()
    }

    // --- Watch progress (continue watching) ---
    fun getWatchProgress(): Map<String, WatchProgress> =
        prefs.getString(KEY_PROGRESS, null)?.let {
            gson.fromJson(it, object : TypeToken<Map<String, WatchProgress>>() {}.type)
        } ?: emptyMap()

    fun saveWatchProgress(id: String, progress: WatchProgress) {
        val map = getWatchProgress().toMutableMap()
        map[id] = progress
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(map)).apply()
    }

    fun removeWatchProgress(id: String) {
        val map = getWatchProgress().toMutableMap()
        map.remove(id)
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(map)).apply()
    }

    // --- Podłączone lokalne foldery (zastępują zakładkę Playlists na dolnym pasku) ---
    fun getLocalFolders(): List<LocalFolder> =
        prefs.getString(KEY_LOCAL_FOLDERS, null)?.let {
            gson.fromJson(it, object : TypeToken<List<LocalFolder>>() {}.type)
        } ?: emptyList()

    fun saveLocalFolders(list: List<LocalFolder>) {
        prefs.edit().putString(KEY_LOCAL_FOLDERS, gson.toJson(list)).apply()
    }

    // --- Rekordy pobrań (metadane, patrz DownloadRecord) — bieżący status/postęp odpytuje
    // się każdorazowo z DownloadManager po managerId, to tylko trwały spis "co zleciłem". ---
    fun getDownloads(): List<DownloadRecord> =
        prefs.getString(KEY_DOWNLOADS, null)?.let {
            gson.fromJson(it, object : TypeToken<List<DownloadRecord>>() {}.type)
        } ?: emptyList()

    fun addDownload(record: DownloadRecord) {
        val list = getDownloads() + record
        prefs.edit().putString(KEY_DOWNLOADS, gson.toJson(list)).apply()
    }

    fun removeDownload(managerId: Long) {
        val list = getDownloads().filter { it.managerId != managerId }
        prefs.edit().putString(KEY_DOWNLOADS, gson.toJson(list)).apply()
    }

    /** Podmienia stary wpis (nieudane pobranie) na nowy, po ponowieniu z innym managerId —
     * DownloadManager nie pozwala "wznowić" starego ID z nowym zleceniem, trzeba enqueue()
     * od nowa, więc i rekord w appce dostaje nowe ID (patrz DownloadsScreen.onRetry). */
    fun replaceDownload(oldManagerId: Long, newRecord: DownloadRecord) {
        val list = getDownloads().filter { it.managerId != oldManagerId } + newRecord
        prefs.edit().putString(KEY_DOWNLOADS, gson.toJson(list)).apply()
    }

    // --- Settings: język, ostatnia zakładka, itp. ---
    fun getLanguage(): String = prefs.getString(KEY_LANG, "pl") ?: "pl"
    fun setLanguage(lang: String) = prefs.edit().putString(KEY_LANG, lang).apply()

    // --- Preferowana ścieżka audio/napisów (GLOBALNA, nie per film) — patrz
    // VlcPlayerManager.applyTrackPreferences w PlayerActivity. Zapisujemy NAZWĘ ścieżki
    // (np. "Polski", "English"), nie jej numeryczny indeks — indeksy ścieżek są przypadkowe
    // i różne w KAŻDYM pliku, więc "ścieżka nr 2" z jednego filmu nie ma żadnego związku ze
    // "ścieżką nr 2" w innym. Przy starcie kolejnego materiału appka szuka wśród dostępnych
    // ścieżek tej o NAJBARDZIEJ PODOBNEJ nazwie i się na nią przełącza automatycznie.
    // Napisy domyślnie WYŁĄCZONE (subtitlesEnabled=false) — libVLC sam potrafi włączyć
    // ścieżkę zadeklarowaną w źródle jako domyślna/wymuszona, appka to świadomie nadpisuje.
    fun getPreferredAudioLanguage(): String? = prefs.getString(KEY_PREFERRED_AUDIO_LANG, null)
    fun setPreferredAudioLanguage(name: String?) = prefs.edit().putString(KEY_PREFERRED_AUDIO_LANG, name).apply()
    fun getSubtitlesEnabled(): Boolean = prefs.getBoolean(KEY_SUBTITLES_ENABLED, false)
    fun setSubtitlesEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SUBTITLES_ENABLED, enabled).apply()
    fun getPreferredSubtitleLanguage(): String? = prefs.getString(KEY_PREFERRED_SUBTITLE_LANG, null)
    fun setPreferredSubtitleLanguage(name: String?) = prefs.edit().putString(KEY_PREFERRED_SUBTITLE_LANG, name).apply()

    // --- "Dodaj do listy": czy samo zaznaczenie od razu zamyka okno, czy trzeba jeszcze
    // nacisnąć "Gotowe" (domyślnie: trzeba, jak dotychczas) — patrz AddToListSheet.kt. ---
    fun getAutoCloseAddToList(): Boolean = prefs.getBoolean(KEY_AUTOCLOSE_ADD_TO_LIST, false)
    fun setAutoCloseAddToList(value: Boolean) = prefs.edit().putBoolean(KEY_AUTOCLOSE_ADD_TO_LIST, value).apply()

    // Nakładka diagnostyczna w odtwarzaczu (Ustawienia -> Log błędów) — pokazuje na żywo
    // durationMs/controlsVisible/wysokość dolnego paska, żeby przy kolejnym zrzucie ekranu z
    // przeskakującymi plakatami mieć TWARDE dane o wewnętrznym stanie w tym momencie, zamiast
    // zgadywać z samego wyglądu. Domyślnie wyłączona — czysto deweloperskie narzędzie.
    // Domyślnie WŁĄCZONA (nie false) — tymczasowo, na czas diagnozowania przeskakujących
    // plakatów w serialach. Poprzedni test (domyślnie wyłączona) nie pokazał żadnych
    // danych, bo trzeba było ręcznie przełączyć ustawienie — czego nietrudno przeoczyć.
    // Włączenie domyślnie gwarantuje, że KOLEJNY test od razu przechwyci potrzebne dane,
    // bez dodatkowego kroku do zapamiętania. Można wyłączyć w Ustawieniach, gdy już nie
    // będzie potrzebna.
    // Domyślnie WYŁĄCZONA — tymczasowo była domyślnie włączona (true) na czas
    // diagnozowania przeskakujących plakatów w serialach i błędu wznowienia oglądania.
    // Oba znalezione i naprawione — wraca do standardowego, wyłączonego stanu; nadal
    // można ją włączyć ręcznie w Ustawieniach, jeśli znów będzie potrzebna.
    fun getPlayerDebugOverlay(): Boolean = prefs.getBoolean(KEY_PLAYER_DEBUG_OVERLAY, false)
    fun setPlayerDebugOverlay(value: Boolean) = prefs.edit().putBoolean(KEY_PLAYER_DEBUG_OVERLAY, value).apply()

    // --- Okres próbny (5 dni) — patrz obszerny komentarz w TrialGate.kt po pełne
    // wyjaśnienie, co dokładnie blokujemy po jego zakończeniu. Data zapisywana RAZ, przy
    // pierwszym uruchomieniu appki — kolejne odczyty tylko SPRAWDZAJĄ, ile czasu minęło,
    // nigdy jej nie nadpisują. To JEDYNA warstwa śledzenia próby — celowo bez żadnego
    // dodatkowego "znacznika" po stronie Google Play (patrz BillingManager.kt). ---
    fun getFirstRunTimestamp(): Long? {
        val v = prefs.getLong(KEY_FIRST_RUN_TIMESTAMP, -1L)
        return if (v < 0) null else v
    }
    fun setFirstRunTimestampIfAbsent(): Long {
        val existing = getFirstRunTimestamp()
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_FIRST_RUN_TIMESTAMP, now).apply()
        return now
    }

    // --- Debugowe przesunięcie zegara okresu próbnego — WYŁĄCZNIE do testowania zakończenia
    // triala bez czekania 5 realnych dni (patrz TrialGate.isExpired, które dodaje tę wartość
    // do "upływu czasu", ale TYLKO gdy BuildConfig.DEBUG — w wersji release ta wartość jest
    // zawsze ignorowana, niezależnie od tego, co tu zapisano). UI: przycisk w Ustawieniach,
    // widoczny wyłącznie w buildzie debug (patrz SettingsScreen.kt). ---
    fun getDebugTrialOffsetMillis(): Long = prefs.getLong(KEY_DEBUG_TRIAL_OFFSET, 0L)
    fun addDebugTrialOffsetDays(days: Int) {
        val next = getDebugTrialOffsetMillis() + days * 24L * 60 * 60 * 1000
        prefs.edit().putLong(KEY_DEBUG_TRIAL_OFFSET, next).apply()
    }
    fun resetDebugTrialOffset() = prefs.edit().putLong(KEY_DEBUG_TRIAL_OFFSET, 0L).apply()
    // Panel testowy (app/src/debug/.../testtools) — ustawienie przesunięcia zegara próby na
    // dokładną wartość (np. "dzień 0" albo "koniec próby"). Używane wyłącznie w buildzie debug.
    fun setDebugTrialOffsetMillis(millis: Long) = prefs.edit().putLong(KEY_DEBUG_TRIAL_OFFSET, millis).apply()
    // Panel testowy: "zacznij okres próbny od nowa" — pierwsze uruchomienie = teraz, przesunięcie 0.
    fun restartTrialForTesting() = prefs.edit()
        .putLong(KEY_FIRST_RUN_TIMESTAMP, System.currentTimeMillis())
        .putLong(KEY_DEBUG_TRIAL_OFFSET, 0L)
        .apply()
    // Panel testowy: kasuje WYŁĄCZNIE rejestr zgód i oczekujące zgody zakupowe (appka po restarcie
    // znów pokaże ekran powitalny). Produkt celowo nie ma takiej funkcji dla użytkownika — to
    // narzędzie tylko do testów (debug).
    fun clearConsentRecordForTesting(): Boolean {
        pendingPrefs.edit().clear().commit()
        return consentVault.clearForTesting()
    }

    // Ekran powitalny (pierwsze uruchomienie, patrz OnboardingScreen.kt) — jeden checkbox
    // obejmujący łącznie: oświadczenie o pełnoletności (18 lat), akceptację Regulaminu i
    // zapoznanie się z Polityką prywatności. Zapisywane RAZ, trwale, jako ZDARZENIE 1 w
    // rejestrze zgód (ConsentVault, patrz ConsentEvent.kt) — nie pytamy o to przy każdym
    // uruchomieniu. Zapisujemy numery wersji Regulaminu i Polityki (np. "1.12_PL") oraz
    // wersję appki z chwili akceptacji — patrz LegalDocuments.kt i mechanizm zmiany wersji
    // (sekcja 10), który porówna je z wersjami wbudowanymi w nowszą wersję appki.
    //
    // Jedyne źródło prawdy dla bramki "bez akceptacji appka nie działa" (IptvApp,
    // AppViewModel, CatalogRefreshWorker, RootScreen). Ponieważ rejestr leży w pliku
    // wyłączonym z kopii zapasowej Androida, appka przywrócona z kopii na nowym urządzeniu
    // nie ma rejestru i znów pokazuje ekran powitalny — nowa instalacja = nowa akceptacja.
    fun hasAcceptedOnboarding(): Boolean =
        consentVault.readAll().any { it.eventType == ConsentEvent.TYPE_ONBOARDING }

    /**
     * Zapisuje zdarzenie 1 (akceptacja ekranu powitalnego). Zapis JEDNORAZOWY: jeśli zdarzenie
     * już istnieje, nic nie robi (nie ma w appce żadnej drogi edycji ani cofnięcia zgody).
     * Zapis synchroniczny — to dowód zgody, nie chcemy go stracić przy zamknięciu procesu tuż
     * po kliknięciu. Zgoda "pełnoletność" i "Regulamin" mają ten sam moment (jedno kliknięcie).
     */
    fun setOnboardingAccepted(regulaminVersion: String, polityVersion: String, appVersion: String) {
        if (hasAcceptedOnboarding()) return
        val now = ConsentEvent.formatUtc(System.currentTimeMillis())
        consentVault.append(
            ConsentEvent(
                consentEventId = java.util.UUID.randomUUID().toString(),
                eventType = ConsentEvent.TYPE_ONBOARDING,
                timestampUtc = now,
                appVersion = appVersion,
                termsVersion = legalVersionTag(regulaminVersion),
                privacyPolicyVersion = legalVersionTag(polityVersion),
                platform = ConsentEvent.PLATFORM_GOOGLE_PLAY,
                storeTransactionId = null,
                age18DeclaredAtUtc = now,
                termsAcceptedAtUtc = now,
            ),
        )
    }

    /** Wszystkie zdarzenia zgody w kolejności zapisu (pusta lista, jeśli ekran powitalny nie
     * został jeszcze zaakceptowany). Tylko do odczytu — patrz ConsentRecordScreen.kt. */
    fun getConsentEvents(): List<ConsentEvent> = consentVault.readAll()

    // Numer wersji dokumentu z oznaczeniem języka, np. "1.12" -> "1.12_PL" (dokumenty prawne są
    // wbudowane w appkę wyłącznie po polsku, patrz LegalDocuments.kt).
    private fun legalVersionTag(version: String): String = version + "_PL"

    // Mechanizm zmiany wersji dokumentów prawnych (sekcja 10) — osobno dla Regulaminu i
    // Polityki, bo mogą się zmieniać w różnym tempie. Zapisuje numer wersji, dla której
    // komunikat o zmianie już się pokazał, żeby nie pokazywać go wielokrotnie.
    fun getAcknowledgedLegalDocVersion(docKey: String): String? =
        prefs.getString(KEY_LEGAL_DOC_VERSION_PREFIX + docKey, null)
    fun setAcknowledgedLegalDocVersion(docKey: String, version: String) =
        prefs.edit().putString(KEY_LEGAL_DOC_VERSION_PREFIX + docKey, version).apply()

    // Ekran "przed zakupem" (sekcja 4, patrz PrePurchaseScreen.kt) — data i godzina
    // zaznaczenia pola 1 i pola 2 (OSOBNO, bo Użytkownik zaznacza je w różnych chwilach).
    // Zapisywane jako "oczekujące" w chwili naciśnięcia "Przejdź do płatności" i dopiero po
    // wykryciu udanego zakupu DOPISYWANE do rejestru zgód jako ZDARZENIE 2 razem z numerem
    // zamówienia (patrz recordPurchaseConsent) — jeśli płatność zostanie anulowana, rejestr
    // zgód pozostaje bez zdarzenia zakupu. Kolejne naciśnięcie "Przejdź do płatności"
    // nadpisuje oczekujące wartości.
    fun setPendingPurchaseConsents(check1AtMillis: Long, check2AtMillis: Long) {
        pendingPrefs.edit()
            .putLong(P_PENDING_CHECK1_AT, check1AtMillis)
            .putLong(P_PENDING_CHECK2_AT, check2AtMillis)
            .commit()
    }
    fun hasPendingPurchaseConsents(): Boolean = pendingPrefs.getLong(P_PENDING_CHECK1_AT, 0L) > 0L

    /** Czy zakup o TYM numerze zamówienia jest już zapisany w rejestrze zgód. Chroni przed
     * ponownym zapisem przy KAŻDYM starcie appki (stan zakupu z Billing jest co start liczony
     * od nowa, więc przejście "niekupiono -> kupiono" zdarza się po każdym uruchomieniu). */
    fun isPurchaseRecordedFor(orderId: String?): Boolean =
        consentVault.readAll().any {
            (it.eventType == ConsentEvent.TYPE_PURCHASE || it.eventType == ConsentEvent.TYPE_PURCHASE_RESTORED) &&
                it.storeTransactionId == orderId
        }

    /**
     * Dopisuje do rejestru zgód ZDARZENIE 2 (zakup): numer zamówienia ze sklepu oraz — jeśli
     * Użytkownik przeszedł przez ekran przed zakupem w tej instalacji — zgody z pola 1
     * (external_features_acknowledged) i pola 2 (digital_content_waiver_accepted), każda z
     * własnym znacznikiem czasu. Zwraca true, gdy te zgody były dostępne (czyli to zakup
     * świeżo zrobiony przez ekran przed zakupem), a false, gdy zakup został tylko wykryty
     * (np. przywrócony po reinstalacji) — wtedy zdarzenie ma typ PURCHASE_RESTORED i nie
     * zawiera zgód zakupowych, bo w tej instalacji nikt ich nie składał.
     * Bez ekranu powitalnego rejestr nie ma zdarzenia 1 — wtedy nic nie zapisujemy.
     */
    fun recordPurchaseConsent(orderId: String?): Boolean {
        if (!hasAcceptedOnboarding()) return false
        val check1 = pendingPrefs.getLong(P_PENDING_CHECK1_AT, 0L)
        val check2 = pendingPrefs.getLong(P_PENDING_CHECK2_AT, 0L)
        val fromPurchaseFlow = check1 > 0L && check2 > 0L
        val appVersion = com.iptvplayer.app.BuildConfig.VERSION_NAME
        // Wersje dokumentów obowiązujące w chwili zakupu — te, które Użytkownik mógł otworzyć
        // linkami na ekranie przed zakupem (LegalDocuments czyta wbudowane pliki).
        val terms = runCatching { LegalDocuments.load(appContext, LegalDocType.REGULAMIN).version }.getOrDefault(appVersion)
        val privacy = runCatching { LegalDocuments.load(appContext, LegalDocType.POLITYKA).version }.getOrDefault(appVersion)
        val saved = consentVault.append(
            ConsentEvent(
                consentEventId = java.util.UUID.randomUUID().toString(),
                eventType = if (fromPurchaseFlow) ConsentEvent.TYPE_PURCHASE else ConsentEvent.TYPE_PURCHASE_RESTORED,
                timestampUtc = ConsentEvent.formatUtc(System.currentTimeMillis()),
                appVersion = appVersion,
                termsVersion = legalVersionTag(terms),
                privacyPolicyVersion = legalVersionTag(privacy),
                platform = ConsentEvent.PLATFORM_GOOGLE_PLAY,
                storeTransactionId = orderId,
                externalFeaturesAcknowledgedAtUtc = if (fromPurchaseFlow) ConsentEvent.formatUtc(check1) else null,
                digitalContentWaiverAcceptedAtUtc = if (fromPurchaseFlow) ConsentEvent.formatUtc(check2) else null,
            ),
        )
        if (saved) pendingPrefs.edit().clear().commit()
        return fromPurchaseFlow
    }

    // Migawka numeru wersji i daty Regulaminu obowiązującej W DNIU ZAKUPU (sekcja 6: "Regulamin
    // obowiązujący w dniu zakupu: wersja [numer] z dnia [data]") — zapisywana RAZ, przez
    // AppViewModel, w chwili pierwszego wykrycia zakupu jako PURCHASED (patrz
    // AppViewModel.billingManager.onStateChanged). Celowo HISTORYCZNA migawka, nie odczyt na
    // żywo z assets/legal/regulamin.txt — ten plik mógł się później zmienić w nowszej wersji
    // appki (patrz mechanizm zmiany wersji, sekcja 10), a potwierdzenie zakupu musi zostać
    // dokładne na moment zakupu, niezależnie od późniejszych aktualizacji dokumentu.
    fun setPurchaseRegulaminSnapshot(version: String, effectiveDate: String) {
        prefs.edit()
            .putString(KEY_PURCHASE_REGULAMIN_VERSION, version)
            .putString(KEY_PURCHASE_REGULAMIN_DATE, effectiveDate)
            .apply()
    }
    fun getPurchaseRegulaminVersion(): String? = prefs.getString(KEY_PURCHASE_REGULAMIN_VERSION, null)
    fun getPurchaseRegulaminDate(): String? = prefs.getString(KEY_PURCHASE_REGULAMIN_DATE, null)

    // Ostrzeżenie "Wyślij do TV" (sekcja 8, pierwsze użycie Cast) — pokazywane TYLKO raz,
    // przy pierwszym tapnięciu ikony Cast w PlayerActivity.kt. Sama funkcja Cast pozostaje
    // zawsze darmowa (patrz TrialGate.kt) — to ostrzeżenie o braku szyfrowania w sieci
    // domowej, wymagane niezależnie od okresu próbnego/zakupu.
    fun hasAcknowledgedCastWarning(): Boolean = prefs.getBoolean(KEY_CAST_WARNING_ACK, false)
    fun setAcknowledgedCastWarning() = prefs.edit().putBoolean(KEY_CAST_WARNING_ACK, true).apply()
    // Panel testowy — przywraca stan "ostrzeżenie jeszcze się nie pokazało". Tylko build debug.
    fun resetCastWarningAckForTesting() = prefs.edit().remove(KEY_CAST_WARNING_ACK).apply()

    // Krótka informacja przed PIERWSZYM oknem zgody UMP (sekcja 9, wydanie `ads` — patrz
    // com.iptvplayer.app.ads.AdsController w app/src/ads). Pokazywana TYLKO raz w całym
    // cyklu życia instalacji — kolejne uruchomienia idą od razu do requestConsentInfoUpdate
    // (który sam decyduje, czy właściwy formularz zgody jest w ogóle potrzebny). Ta metoda
    // jest tu (we wspólnym LocalStore), a nie w samym AdsController, bo app/src/noads ma
    // WŁASNĄ, pustą implementację AdsController bez żadnego zapisu stanu — a LocalStore to
    // zwykła klasa współdzielona, więc nie psuje zasady "zero kodu reklamowego w noads"
    // (to tylko nieużywany w noads klucz w SharedPreferences, nie kod reklamowy).
    fun hasShownAdsConsentIntro(): Boolean = prefs.getBoolean(KEY_ADS_CONSENT_INTRO_SHOWN, false)
    fun setShownAdsConsentIntro() = prefs.edit().putBoolean(KEY_ADS_CONSENT_INTRO_SHOWN, true).apply()
    // Panel testowy — przywraca stan "informacja o reklamach jeszcze się nie pokazała". Tylko build debug.
    fun resetAdsConsentIntroForTesting() = prefs.edit().remove(KEY_ADS_CONSENT_INTRO_SHOWN).apply()

    // --- własne reguły wyszukiwania nastrojowego (Ustawienia -> Wyszukiwanie) ---
    // Każda reguła to "hasło|fragment_kategorii" (np. "kosmici|sci-fi") — użytkownik
    // definiuje WŁASNE dopasowania OBOK wbudowanego słownika (patrz MOOD_RULES w
    // GlobalSearch.kt). Format "trigger|keyword" w StringSet zamiast pełnego JSON-a —
    // ten sam, prosty, bezzależnościowy wzorzec co reszta LocalStore w tym pliku.
    fun getCustomMoodRules(): List<Pair<String, String>> =
        (prefs.getStringSet(KEY_CUSTOM_MOOD_RULES, emptySet()) ?: emptySet())
            .mapNotNull { raw ->
                val parts = raw.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
            }

    fun setCustomMoodRules(rules: List<Pair<String, String>>) {
        val encoded = rules.map { (trigger, keyword) -> "$trigger|$keyword" }.toSet()
        prefs.edit().putStringSet(KEY_CUSTOM_MOOD_RULES, encoded).apply()
    }

    // --- Zwinięte sekcje "Ostatnio dodane" na Home (klucze: "movies"/"series"/"live") —
    // przydatne, gdy dana kategoria u konkretnego dostawcy nie ma plakatów (same szare
    // kafelki) i tylko zajmuje miejsce na ekranie. Zapamiętane trwale, nie tylko na czas
    // sesji — patrz HomeScreen.RecentlyAddedSection. ---
    fun getCollapsedHomeSections(): Set<String> =
        prefs.getStringSet(KEY_COLLAPSED_HOME_SECTIONS, emptySet()) ?: emptySet()
    fun setCollapsedHomeSections(keys: Set<String>) =
        prefs.edit().putStringSet(KEY_COLLAPSED_HOME_SECTIONS, keys).apply()

    // --- Tryb uruchamiania appki (przycisk obok zegara na Home) — false (domyślnie) =
    // appka zawsze startuje na Home; true = wraca do ostatnio otwartej głównej zakładki
    // (Live/Movies/Series/Folder/Settings). Ważne dla pilotów TV — użytkownik oglądający
    // na bieżąco serial/kanał live nie chce za każdym uruchomieniem appki lądować na Home. ---
    fun getContinueFromLastPosition(): Boolean = prefs.getBoolean(KEY_STARTUP_CONTINUE, false)
    fun setContinueFromLastPosition(value: Boolean) {
        prefs.edit().putBoolean(KEY_STARTUP_CONTINUE, value).apply()
    }

    // Ostatnio otwarta główna zakładka (Tab.name) — zapisywana przy KAŻDEJ zmianie
    // zakładki niezależnie od trybu uruchamiania powyżej, żeby wartość była gotowa,
    // gdyby użytkownik włączył tryb "kontynuuj" dopiero później.
    fun getLastTab(): String? = prefs.getString(KEY_LAST_TAB, null)
    fun setLastTab(tabName: String) {
        prefs.edit().putString(KEY_LAST_TAB, tabName).apply()
    }

    // --- Znacznik czasu ostatniego odświeżenia playlist (przycisk Odśwież na Home) —
    // 0L / brak wartości = jeszcze nigdy nie odświeżano w tej instalacji. ---
    fun getLastRefreshAt(): Long = prefs.getLong(KEY_LAST_REFRESH, 0L)
    fun setLastRefreshAt(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_REFRESH, timestampMs).apply()
    }

    // --- Ukryte kategorie (menedżer kategorii w Settings) — odpowiednik
    // disabledCategories_live / _movie / _series w app.js. Osobny Set per typ. ---
    fun getHiddenCategories(type: String): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_CATS_PREFIX + type, emptySet()) ?: emptySet()

    fun setHiddenCategories(type: String, categories: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_CATS_PREFIX + type, categories).apply()
    }

    // --- Backup/restore: eksport/import ulubionych, playlist, ukrytych kategorii
    // i historii oglądania do/z jednego pliku JSON. ---

    /** Buduje snapshot aktualnych danych do zapisania w pliku kopii zapasowej. */
    fun buildBackup(): BackupData = BackupData(
        playlists = getPlaylists(),
        favorites = getFavoriteIds().toList(),
        hiddenCategories = mapOf(
            "live" to getHiddenCategories("live").toList(),
            "movie" to getHiddenCategories("movie").toList(),
            "series" to getHiddenCategories("series").toList(),
        ),
        watchProgress = getWatchProgress(),
        customLists = getLists(),
    )

    fun exportBackupJson(): String = gson.toJson(buildBackup())

    /**
     * Nadpisuje lokalne dane zawartością kopii zapasowej. Zwraca false (bez żadnych zmian),
     * jeśli plik jest uszkodzony/ma zły format — tak samo jak walidacja JSON.parse w app.js
     * przed nadpisaniem localStorage.
     */
    fun importBackupJson(json: String): Boolean {
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            null
        } ?: return false

        savePlaylists(data.playlists)
        prefs.edit().putStringSet(KEY_FAVORITES, data.favorites.toSet()).apply()
        setHiddenCategories("live", data.hiddenCategories["live"].orEmpty().toSet())
        setHiddenCategories("movie", data.hiddenCategories["movie"].orEmpty().toSet())
        setHiddenCategories("series", data.hiddenCategories["series"].orEmpty().toSet())
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(data.watchProgress)).apply()
        // Stare kopie zapasowe (wersja 1) nie mają jeszcze customLists — zapisujemy to, co
        // przyszło (może być pusta lista), a właściwą migrację starych "favorites" do
        // wbudowanej listy robi AppViewModel po wczytaniu (potrzebuje znać typ id, którego
        // LocalStore nie zna — patrz AppViewModel.migrateFavoritesIfNeeded).
        saveLists(data.customLists)
        return true
    }

    // --- Cache katalogu (kanały/filmy/seriale) — patrz AppViewModel.init/refreshAll.
    // CELOWO osobny plik, nie SharedPreferences: przy tysiącach pozycji (u testujących
    // widziane: kilkanaście tysięcy filmów) to potrafi być kilka MB danych, a
    // SharedPreferences wczytuje WSZYSTKIE swoje klucze do pamięci od razu przy pierwszym
    // dostępie do PREF-ów — trzymanie tak dużego blobu tam obciążałoby KAŻDY, nawet
    // zupełnie niezwiązany odczyt z SharedPreferences w całej appce (np. samo sprawdzenie
    // języka przy starcie), nie tylko ten jeden. Kategorie (allCategoriesLive/Movie/Series)
    // CELOWO nie są tu cache'owane osobno — to tylko `.group` z tych samych list, więc
    // AppViewModel odtwarza je dokładnie tak samo jak w refreshAll, bez duplikowania danych.
    private val catalogCacheFile get() = java.io.File(appContext.filesDir, "catalog_cache.json")

    /** Wczytuje ostatnio zapisany katalog — null, jeśli appka jeszcze nigdy się nie
     * odświeżyła pomyślnie, albo plik jest uszkodzony/nieczytelny. Celowo CICHO (bez
     * wyjątku): appka ma się po prostu zachować tak, jakby cache nie istniał, i pobrać
     * wszystko od nowa z sieci — awaria cache NIE MOŻE zablokować normalnego działania. */
    // Optymalizacja pamięci: odczyt i zapis STRUMIENIOWO (bezpośrednio z/do pliku), zamiast
    // budować cały katalog jako jeden ogromny tekst w pamięci. Przy dużych playlistach
    // (dziesiątki tysięcy pozycji) ten tekst potrafił mieć kilkadziesiąt MB i istniał w
    // pamięci RÓWNOCZEŚNIE z gotowymi obiektami — na przystawkach TV z małą ilością RAM to
    // prosta droga do zamknięcia appki przez brak pamięci (OutOfMemoryError). Zapis idzie
    // najpierw do pliku tymczasowego, a dopiero potem podmienia właściwy — jeśli appka
    // zostanie zamknięta w połowie zapisu, poprzedni katalog zostaje nietknięty. Blokada
    // CATALOG_LOCK: appka i nocne odświeżanie w tle (CatalogRefreshWorker) nigdy nie
    // piszą/czytają tego pliku jednocześnie.
    fun getCatalogCache(): CatalogCacheDto? = runCatching {
        synchronized(CATALOG_LOCK) {
            if (!catalogCacheFile.exists()) return null
            catalogCacheFile.bufferedReader().use { reader ->
                gson.fromJson(reader, CatalogCacheDto::class.java)
            }
        }
    }.getOrNull()

    /** Zapisuje świeżo pobrany katalog — wołane po KAŻDYM udanym odświeżeniu (patrz
     * AppViewModel.refreshAll), żeby NASTĘPNE uruchomienie appki miało co pokazać
     * NATYCHMIAST, zanim sieć w ogóle zdąży odpowiedzieć (patrz init w AppViewModel). */
    fun saveCatalogCache(liveChannels: List<Channel>, movies: List<Channel>, series: List<SeriesInfo>) {
        runCatching {
            synchronized(CATALOG_LOCK) {
                val tmp = java.io.File(catalogCacheFile.parentFile, catalogCacheFile.name + ".tmp")
                tmp.bufferedWriter().use { writer ->
                    gson.toJson(CatalogCacheDto(liveChannels, movies, series, System.currentTimeMillis()), writer)
                }
                if (!tmp.renameTo(catalogCacheFile)) {
                    // Na części systemów plików rename nie nadpisuje istniejącego pliku —
                    // wtedy usuwamy stary i próbujemy jeszcze raz.
                    catalogCacheFile.delete()
                    tmp.renameTo(catalogCacheFile)
                }
            }
        }
    }

    companion object {
        private val CATALOG_LOCK = Any()
        // Oczekujące zgody zakupowe — patrz pendingPrefs. Nazwa pliku musi zgadzać się z wpisami
        // w res/xml/backup_rules.xml i data_extraction_rules.xml (wyłączenie z kopii).
        const val PENDING_PREFS_NAME = "consent_pending"
        private const val P_PENDING_CHECK1_AT = "pending_purchase_check1_at"
        private const val P_PENDING_CHECK2_AT = "pending_purchase_check2_at"
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_PROGRESS = "watch_progress"
        private const val KEY_LANG = "language"
        // Legacy — TMDB usunięte z aplikacji; klucz zostaje tu WYŁĄCZNIE jako cel migracji
        // czyszczącej w init{} powyżej, nie jako aktywnie używane ustawienie.
        private const val KEY_LEGACY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_PREFERRED_AUDIO_LANG = "preferred_audio_lang"
        private const val KEY_SUBTITLES_ENABLED = "subtitles_enabled"
        private const val KEY_PREFERRED_SUBTITLE_LANG = "preferred_subtitle_lang"
        private const val KEY_AUTOCLOSE_ADD_TO_LIST = "autoclose_add_to_list"
        private const val KEY_PLAYER_DEBUG_OVERLAY = "player_debug_overlay"
        private const val KEY_FIRST_RUN_TIMESTAMP = "first_run_timestamp"
        private const val KEY_DEBUG_TRIAL_OFFSET = "debug_trial_offset_millis"
        // Legacy (do 1.01, w "iptv_store") — zastąpione rekordem zgód, sprzątane w init{}.
        private const val KEY_ONBOARDING_ACCEPTED = "onboarding_accepted"
        private const val KEY_ONBOARDING_ACCEPTED_AT = "onboarding_accepted_at"
        private const val KEY_ONBOARDING_ACCEPTED_REGULAMIN_VERSION = "onboarding_accepted_regulamin_version"
        private const val KEY_LEGAL_DOC_VERSION_PREFIX = "legal_doc_version_ack_"
        // Legacy (do 1.01) — jeden wspólny znacznik czasu; od 1.02 osobne daty pól 1 i 2.
        private const val KEY_PREPURCHASE_CONFIRMED_AT = "prepurchase_confirmed_at"
        private const val KEY_PURCHASE_REGULAMIN_VERSION = "purchase_regulamin_version"
        private const val KEY_PURCHASE_REGULAMIN_DATE = "purchase_regulamin_date"
        private const val KEY_CAST_WARNING_ACK = "cast_warning_ack"
        private const val KEY_ADS_CONSENT_INTRO_SHOWN = "ads_consent_intro_shown"
        // Legacy — zastąpione ekranem powitalnym i rekordem zgód (patrz init{} powyżej, sprzątanie).
        private const val KEY_LEGACY_WITHDRAWAL_WAIVER_ACK = "withdrawal_waiver_ack"
        private const val KEY_CUSTOM_MOOD_RULES = "custom_mood_rules"
        private const val KEY_COLLAPSED_HOME_SECTIONS = "collapsed_home_sections"
        private const val KEY_HIDDEN_CATS_PREFIX = "hidden_categories_"
        private const val KEY_LISTS = "custom_lists"
        private const val KEY_LAST_REFRESH = "last_refresh_at"
        private const val KEY_LOCAL_FOLDERS = "local_folders"
        private const val KEY_DOWNLOADS = "download_records"
        private const val KEY_STARTUP_CONTINUE = "startup_continue_last_position"
        private const val KEY_LAST_TAB = "last_tab"
    }
}
