package com.iptvplayer.app.ui

import android.app.Application
import com.iptvplayer.app.IptvApp
import com.iptvplayer.app.billing.BillingState
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iptvplayer.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class AppUiState(
    val playlists: List<PlaylistSource> = emptyList(),
    val liveChannels: List<Channel> = emptyList(),
    val movies: List<Channel> = emptyList(),
    val series: List<SeriesInfo> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val watchProgress: Map<String, WatchProgress> = emptyMap(),
    // --- "Ostatnio dodane" na Home (Movies/Series/Live) — odpowiednik sekcji
    // "RECENTLY ADDED" z wersji web, posortowane malejąco po dacie dodania z Xtream API. ---
    val recentMovies: List<Channel> = emptyList(),
    val recentSeries: List<SeriesInfo> = emptyList(),
    val recentLive: List<Channel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // --- Kiedy ostatni raz zakończyło się odświeżanie playlist (przycisk Odśwież na Home) —
    // 0L = jeszcze nigdy w tej instalacji. Pokazywane pod licznikiem filmów/seriali na Home. ---
    val lastRefreshAt: Long = 0L,
    // --- Status ostatniej próby połączenia PER playlista (patrz AppViewModel.refreshAll) —
    // źródło danych dla wskaźnika połączenia w headerze (zielony/żółty/czerwony) i dla
    // ekranu Playlists, żeby było widać KTÓRA konkretnie playlista ma problem. ---
    val playlistStatuses: Map<String, PlaylistStatus> = emptyMap(),
    // --- menedżer kategorii (Settings) — odpowiednik disabledCategories w app.js ---
    val hiddenCategories: Map<String, Set<String>> = mapOf("live" to emptySet(), "movie" to emptySet(), "series" to emptySet()),
    val allCategoriesLive: List<String> = emptyList(),
    val allCategoriesMovie: List<String> = emptyList(),
    val allCategoriesSeries: List<String> = emptyList(),
    // --- ekran szczegółów serialu (sezony/odcinki) ---
    val selectedSeries: SeriesInfo? = null,
    val isLoadingSeriesDetail: Boolean = false,
    val seriesDetailError: String? = null,
    // --- dialog wyboru sezonu do pobrania (przycisk pobierania na plakacie serialu) —
    // niezależny od selectedSeries, bo można go otworzyć wprost z siatki/kafelka, bez
    // wchodzenia na ekran szczegółów. isLoadingSeasonDownload = trwa dociąganie odcinków
    // (get_series_info) zanim dialog będzie mógł pokazać listę sezonów do wyboru. ---
    val seasonDownloadTarget: SeriesInfo? = null,
    val isLoadingSeasonDownload: Boolean = false,
    // --- ekran szczegółów filmu (opis/obsada/reżyser) ---
    val selectedMovie: Channel? = null,
    val selectedMovieDetail: VodDetail? = null,
    val isLoadingMovieDetail: Boolean = false,
    // --- "Podobne filmy" na ekranie szczegółów — liczone lokalnie z state.movies,
    // bez żadnego dodatkowego zapytania do API (patrz computeSimilarMovies). ---
    val similarMovies: List<Channel> = emptyList(),
    // --- "Inne seriale w tej kategorii" na ekranie szczegółów serialu — analogicznie,
    // liczone lokalnie z state.series po wspólnym Channel/SeriesInfo.group. ---
    val similarSeries: List<SeriesInfo> = emptyList(),
    // --- Niestandardowe listy użytkownika ("Moje listy" na Home) — lista o id="favorites"
    // jest wbudowana i to dokładnie to samo co dotychczasowe `favorites` niżej (pole
    // zostaje, wyliczane teraz z tej listy, żeby cały istniejący kod gwiazdek działał
    // bez zmian). Kolejność w tej liście = kolejność kart w sekcji "Moje listy" na Home. ---
    val customLists: List<CustomList> = emptyList(),
    val selectedListId: String? = null,
    // --- Zakładka Folder (zastępuje dawną zakładkę Playlists na dolnym pasku) — lokalne
    // foldery podłączone przez użytkownika (SAF) i wynik ich ostatniego skanowania. ---
    val localFolders: List<LocalFolder> = emptyList(),
    val localVideos: List<LocalVideoFile> = emptyList(),
    val isScanningLocalFolders: Boolean = false,
    // Komunikat błędu per folder (np. odłączony nośnik/cofnięte uprawnienie) — klucz = LocalFolder.id.
    val localFolderErrors: Map<String, String> = emptyMap(),
    // Rekordy pobrań (metadane — tytuł/plakat/URL do ponowienia), patrz DownloadRecord i
    // ekran DownloadsScreen. Bieżący status/postęp KAŻDEGO pobrania odpytuje się osobno z
    // samego DownloadManager (system), nie trzyma się tego w tym stanie — to tylko trwały
    // spis "co appka kiedykolwiek zleciła", nie migawka aktualnego postępu.
    val downloads: List<DownloadRecord> = emptyList(),
    // --- Tryb uruchamiania appki (przycisk obok zegara na Home, patrz LocalStore) —
    // continueFromLastPosition steruje tym, którą zakładkę RootScreen wybiera jako
    // startową; lastTab jest odczytywany TYLKO raz, przy pierwszej kompozycji RootScreen. ---
    val continueFromLastPosition: Boolean = false,
    val lastTab: String? = null,
    // --- Aktualny język appki (patrz i18n/Strings.kt) — kod ISO z SUPPORTED_LANGUAGES,
    // domyślnie "pl". Dostarczany do całego drzewa UI przez CompositionLocalProvider w
    // MainActivity.setContent (patrz LocalLang), więc zmiana w Ustawieniach odświeża
    // natychmiast wszystkie przetłumaczone ekrany, bez restartu appki. ---
    val language: String = "pl",
    // "Dodaj do listy": czy samo zaznaczenie od razu zamyka okno — patrz AddToListSheet.kt
    // i LocalStore.getAutoCloseAddToList. Domyślnie false: trzeba nacisnąć "Gotowe", jak
    // dotychczas — to funkcja opt-in, nie zmiana domyślnego zachowania.
    val autoCloseAddToList: Boolean = false,
    // Nakładka diagnostyczna w odtwarzaczu — patrz obszerny komentarz przy
    // getPlayerDebugOverlay w LocalStore.kt.
    val playerDebugOverlay: Boolean = false,
    // Zwinięte sekcje "Ostatnio dodane" na Home (klucze: "movies"/"series"/"live") — patrz
    // LocalStore.getCollapsedHomeSections i HomeScreen.RecentlyAddedSection.
    val collapsedHomeSections: Set<String> = emptySet(),
    // Własne reguły wyszukiwania nastrojowego zdefiniowane przez użytkownika (Ustawienia)
    // — para (hasło, fragment kategorii), łączona z wbudowanym słownikiem w GlobalSearch.kt.
    val customMoodRules: List<Pair<String, String>> = emptyList(),
    // Zapytania wyszukiwania na ekranach Filmy/Seriale/Na żywo — zgłoszenie: otwarcie
    // szczegółów filmu/serialu i powrót KASOWAŁO wpisane wyszukiwanie, trzeba było wpisywać
    // od nowa. Przyczyna: ekran szczegółów jest rysowany jako wczesny `return` w tej samej
    // funkcji, co siatka — Compose traktuje to jako "pominięcie" wywołania siatki podczas
    // gdy szczegóły są widoczne, więc jej WEWNĘTRZNY remember(query) był kasowany przy
    // każdym powrocie. Trzymanie tego tutaj, w stanie ViewModelu (który NIE jest częścią
    // tej warunkowo pomijanej struktury), rozwiązuje to raz na zawsze.
    val moviesSearchQuery: String = "",
    val seriesSearchQuery: String = "",
    val liveSearchQuery: String = "",
    // Pokazanie dialogu "kup pełną wersję" — ustawiane centralnie przez
    // AppViewModel.requestPremiumAccess(), wołane z KAŻDEGO miejsca, które próbuje użyć
    // funkcji zablokowanej po okresie próbnym (wyszukiwanie, Moje listy, pobieranie).
    val showPurchasePrompt: Boolean = false,
    // Ekran "przed zakupem" (sekcja 4 specyfikacji z 09.10, patrz PrePurchaseScreen.kt) —
    // pokazuje się PO naciśnięciu "Kup Pełną wersję" w PurchasePromptDialog, PRZED
    // wywołaniem launchPurchaseFlow/oknem płatności Google Play. Jedyna droga do
    // launchPurchaseFlow w całej appce — patrz openPrePurchaseScreen/confirmPrePurchase niżej.
    val showPrePurchaseScreen: Boolean = false,
    // Ekran potwierdzenia zakupu (sekcja 6, patrz PurchaseConfirmationScreen.kt) — pokazuje
    // się automatycznie RAZ, zaraz po wykryciu udanego zakupu (patrz init{} niżej), a także
    // na żądanie z Ustawień -> Informacje prawne -> Zakup -> "Pokaż potwierdzenie" (patrz
    // openPurchaseConfirmationScreen niżej).
    val showPurchaseConfirmationScreen: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    val epgRepository = EpgRepository()

    // --- Okres próbny i stan zakupu — patrz obszerny komentarz w TrialGate.kt (liczba dni
    // próby: TrialGate.TRIAL_DAYS) i BillingManager.kt. `billingState` odzwierciedla na bieżąco
    // to, co wie BillingManager (instancja WSPÓLNA dla całej appki, trzymana w IptvApp) —
    // osobny StateFlow, nie część AppUiState, żeby zmiany stanu zakupu (rzadkie) nie
    // wymuszały przebudowy WSZYSTKIEGO, co czyta AppUiState (częste, duże zmiany typu
    // katalog). ---
    private val billingManager = (app as IptvApp).billingManager
    // Deklarowane PRZED blokiem init (kolejność inicjalizacji w Kotlinie) — init woła
    // startCatalogLoading(), które czyta i ustawia tę flagę; deklaracja niżej zresetowałaby ją.
    private var catalogLoadingStarted = false
    private val _billingState = MutableStateFlow(billingManager.state)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    fun launchPurchaseFlow(activity: android.app.Activity) = billingManager.launchPurchaseFlow(activity)

    /** Ponownie odpytuje Google Play o zakupy użytkownika — wołane automatycznie przy
     * starcie appki (BillingManager.start()) i ręcznie z przycisku "Sprawdź zakup ponownie"
     * w Ustawieniach › Zakup (patrz SettingsScreen.kt). */
    fun refreshPurchases() = billingManager.refreshPurchases()

    /** true = minęło 5 dni od pierwszego uruchomienia I appka nie została kupiona. Gdy
     * true, ekrany UI powinny ukrywać/blokować WSZYSTKO poza samym "gołym" odtwarzaniem:
     * przeglądanie i odtwarzanie Live/Filmy/Seriale/Folder, gwiazdka ulubionych, wybór
     * ścieżki audio/napisów, kontrola rodzicielska, mini-EPG ("teraz leci") i kopia
     * zapasowa zostają w pełni dostępne — reszta (wyszukiwanie, Moje listy, pobieranie)
     * wymaga zakupu.
     * Patrz obszerny komentarz w TrialGate.kt (ta sama reguła używana też przez
     * PlayerActivity, która nie ma dostępu do tej instancji ViewModelu). */
    val isTrialExpired: Boolean
        get() = com.iptvplayer.app.billing.TrialGate.isExpired(getApplication())

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    // listy "surowe" (bez filtrowania po ukrytych kategoriach) — potrzebne w Settings,
    // żeby menedżer kategorii mógł pokazać i przywrócić też te aktualnie ukryte
    private var rawLive: List<Channel> = emptyList()
    private var rawMovies: List<Channel> = emptyList()
    private var rawSeries: List<SeriesInfo> = emptyList()
    // cache szczegółów filmu po id kanału — żeby nie dociągać get_vod_info ponownie
    // za każdym otwarciem tego samego plakatu w trakcie sesji
    private val vodDetailCache = mutableMapOf<String, VodDetail>()

    init {
        billingManager.onStateChanged = {
            val newState = billingManager.state
            _billingState.value = newState
            handlePurchaseDetected(newState)
        }
        // Stan zakupu mógł zostać ustawiony PRZED utworzeniem tego ViewModelu (odpowiedź Billing
        // bywa szybsza niż start MainActivity) — wtedy onStateChanged wyżej go nie dostał.
        // Wołanie jest bezpieczne: handlePurchaseDetected działa tylko raz na numer zamówienia.
        handlePurchaseDetected(billingManager.state)
        _state.value = _state.value.copy(
            playlists = store.getPlaylists(),
            favorites = store.getFavoriteIds(),
            watchProgress = store.getWatchProgress(),
            hiddenCategories = mapOf(
                "live" to store.getHiddenCategories("live"),
                "movie" to store.getHiddenCategories("movie"),
                "series" to store.getHiddenCategories("series"),
            ),
            customLists = store.getLists(),
            lastRefreshAt = store.getLastRefreshAt(),
            localFolders = store.getLocalFolders(),
            downloads = store.getDownloads(),
            continueFromLastPosition = store.getContinueFromLastPosition(),
            lastTab = store.getLastTab(),
            language = store.getLanguage(),
            autoCloseAddToList = store.getAutoCloseAddToList(),
            playerDebugOverlay = store.getPlayerDebugOverlay(),
            collapsedHomeSections = store.getCollapsedHomeSections(),
            customMoodRules = store.getCustomMoodRules(),
        )
        // Wczytanie z lokalnego cache NATYCHMIAST (osobna korutyna, na wątku IO — samo
        // parsowanie JSON-a przy kilkunastu tysiącach pozycji to wciąż odczyt z dysku, nie
        // z sieci, więc typowo ułamek sekundy, a nie sekundy oczekiwania na serwer
        // dostawcy) — użytkownik widzi POPRZEDNIO pobrany katalog niemal od razu, zamiast
        // czarnego/pustego ekranu na czas trwania odświeżenia sieciowego niżej. refreshAll()
        // i tak leci RÓWNOLEGLE i podmieni to świeższymi danymi, gdy tylko odpowie serwer —
        // to nie ZAMIAST odświeżenia, tylko coś do pokazania W MIĘDZYCZASIE (patrz
        // LocalStore.getCatalogCache/saveCatalogCache, ten sam wzorzec co w TiviMate).
        // BRAMKA AKCEPTACJI (sekcja 2 specyfikacji: "Bez akceptacji aplikacja nie działa"):
        // ładowanie katalogu i odświeżanie z sieci startuje dopiero, gdy Użytkownik
        // zaakceptował ekran powitalny — wcześniej (pierwsze uruchomienie, ekran powitalny
        // na wierzchu) nie wolno pobierać żadnej listy ani łączyć się z żadnym serwerem.
        // Przy kolejnych uruchomieniach (akceptacja już zapisana) startuje od razu, jak
        // dotąd. Patrz onOnboardingAccepted() niżej.
        if (store.hasAcceptedOnboarding()) startCatalogLoading()
    }

    /**
     * Reakcja na wykryty zakup Pełnej wersji. Stan zakupu z Billing jest liczony od nowa przy
     * KAŻDYM starcie appki, więc wszystko poniżej jest chronione warunkiem
     * isPurchaseRecordedFor(): dzieje się DOKŁADNIE RAZ dla danego numeru zamówienia, w chwili
     * pierwszego wykrycia (migawka Regulaminu "z dnia zakupu", wpis do rekordu zgód, ekran
     * potwierdzenia) — a nie przy każdym uruchomieniu kupionej appki.
     */
    private fun handlePurchaseDetected(newState: BillingState) {
        if (!newState.isPurchased || store.isPurchaseRecordedFor(newState.orderId)) return
        runCatching { LegalDocuments.load(getApplication(), LegalDocType.REGULAMIN) }
            .getOrNull()
            ?.let { store.setPurchaseRegulaminSnapshot(it.version, it.effectiveDate) }
        // Dopisanie do rekordu zgód (sekcja 2 specyfikacji): daty zaznaczenia pola 1 i 2 z
        // ekranu przed zakupem + numer zamówienia. Zwraca true, gdy zakup został zrobiony
        // teraz, przez ekran przed zakupem.
        val purchasedNow = store.recordPurchaseConsent(newState.orderId)
        // Automatyczne pokazanie ekranu potwierdzenia od razu po udanym zakupie (sekcja 6: "po
        // udanym zakupie i później w Ustawieniach › Zakup") — tylko dla zakupu zrobionego w tej
        // appce przez ekran przed zakupem. Zakup przywrócony po reinstalacji (bez tego ekranu)
        // nie wyświetla go sam z siebie; jest dostępny na żądanie w Ustawieniach › Informacje
        // prawne › Zakup.
        if (purchasedNow) {
            _state.value = _state.value.copy(showPurchaseConfirmationScreen = true)
        }
    }

    /**
     * Wołane przez OnboardingScreen w chwili kliknięcia przycisku akceptacji. Uruchamia
     * wszystko, co przez ekran powitalny było wstrzymane: usługi sieciowe aplikacji
     * (Billing, odświeżanie w tle, odbiornik rzutowania — patrz IptvApp.startNetworkServices)
     * oraz wczytanie/odświeżenie katalogu.
     */
    fun onOnboardingAccepted() {
        (getApplication<Application>() as IptvApp).startNetworkServices()
        startCatalogLoading()
    }

    private fun startCatalogLoading() {
        if (catalogLoadingStarted) return
        catalogLoadingStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val cache = store.getCatalogCache() ?: return@launch
            // Zabezpieczenie przed wyścigiem z refreshAll() (leci równolegle, patrz niżej):
            // jeśli sieć akurat odpowiedziała SZYBCIEJ niż zdążył się wczytać/sparsować ten
            // cache (mało prawdopodobne przy dysku vs sieć, ale nie niemożliwe), NIE
            // nadpisuj już-świeższych danych stertą starych — rawLive/rawMovies/rawSeries
            // niepuste oznacza, że coś już je wypełniło pierwsze.
            if (rawLive.isNotEmpty() || rawMovies.isNotEmpty() || rawSeries.isNotEmpty()) return@launch
            rawLive = cache.liveChannels
            rawMovies = cache.movies
            rawSeries = cache.series
            applyHiddenCategoryFilter()
            _state.value = _state.value.copy(
                allCategoriesLive = cache.liveChannels.mapNotNull { it.group }.distinct().sorted(),
                allCategoriesMovie = cache.movies.mapNotNull { it.group }.distinct().sorted(),
                allCategoriesSeries = cache.series.mapNotNull { it.group }.distinct().sorted(),
            )
        }
        refreshAll()
        rescanLocalFolders()
    }

    // Zarządzanie playlistami (dodawanie/edycja/usuwanie źródeł) jest ZAWSZE wolne, również
    // po okresie próbnym — patrz TrialGate.kt: zablokowane po próbie jest tylko wyszukiwanie,
    // pełny widok "Moje listy" i pobieranie, nie samo zarządzanie źródłami.
    fun addM3UPlaylist(name: String, url: String, userAgent: String? = null) {
        val source = PlaylistSource(id = UUID.randomUUID().toString(), name = name, type = PlaylistType.M3U, url = url, userAgent = userAgent)
        persistNewPlaylist(source)
    }

    fun addXtreamPlaylist(name: String, host: String, username: String, password: String, epgUrl: String?, userAgent: String? = null) {
        val source = PlaylistSource(
            id = UUID.randomUUID().toString(), name = name, type = PlaylistType.XTREAM,
            url = host, username = username, password = password, epgUrl = epgUrl, userAgent = userAgent,
        )
        persistNewPlaylist(source)
    }

    /** Edycja istniejącej playlisty M3U — nadpisuje nazwę/URL i od razu odświeża, żeby
     * błędnie wpisane dane (literówka w URL itp.) dało się poprawić bez usuwania i
     * dodawania playlisty od nowa (co gubiłoby jej pozycję na liście). */
    fun updateM3UPlaylist(id: String, name: String, url: String, userAgent: String? = null) {
        updateExistingPlaylist(id) { it.copy(name = name, url = url, type = PlaylistType.M3U, userAgent = userAgent) }
    }

    fun updateXtreamPlaylist(id: String, name: String, host: String, username: String, password: String, epgUrl: String?, userAgent: String? = null) {
        updateExistingPlaylist(id) {
            it.copy(name = name, url = host, username = username, password = password, epgUrl = epgUrl, type = PlaylistType.XTREAM, userAgent = userAgent)
        }
    }

    private fun updateExistingPlaylist(id: String, transform: (PlaylistSource) -> PlaylistSource) {
        val updated = _state.value.playlists.map { if (it.id == id) transform(it) else it }
        store.savePlaylists(updated)
        _state.value = _state.value.copy(playlists = updated)
        refreshAll()
    }

    private fun persistNewPlaylist(source: PlaylistSource) {
        val updated = _state.value.playlists + source
        store.savePlaylists(updated)
        _state.value = _state.value.copy(playlists = updated)
        refreshAll()
    }

    fun removePlaylist(id: String) {
        val updated = _state.value.playlists.filterNot { it.id == id }
        store.savePlaylists(updated)
        _state.value = _state.value.copy(playlists = updated)
        refreshAll()
    }

    fun toggleFavorite(id: String) {
        val kind = findKind(id) ?: return
        toggleItemInList(CustomList.FAVORITES_ID, id, kind)
    }

    // Patrz obszerny komentarz przy AppUiState.moviesSearchQuery — trzymane w ViewModelu
    // (nie jako remember{} w samym ekranie), żeby przetrwało otwarcie/zamknięcie szczegółów
    // filmu/serialu.
    fun setMoviesSearchQuery(q: String) { if (requestPremiumAccess()) return; _state.value = _state.value.copy(moviesSearchQuery = q) }
    fun setSeriesSearchQuery(q: String) { if (requestPremiumAccess()) return; _state.value = _state.value.copy(seriesSearchQuery = q) }
    fun setLiveSearchQuery(q: String) { if (requestPremiumAccess()) return; _state.value = _state.value.copy(liveSearchQuery = q) }

    /** Przełącznik trybu uruchamiania appki (przycisk obok zegara na Home) — patrz
     * AppUiState.continueFromLastPosition / LocalStore.setContinueFromLastPosition. */
    fun setContinueFromLastPosition(value: Boolean) {
        store.setContinueFromLastPosition(value)
        _state.value = _state.value.copy(continueFromLastPosition = value)
    }

    /** Zapamiętuje ostatnio otwartą główną zakładkę — wywoływane przy KAŻDEJ zmianie
     * zakładki w RootScreen (patrz MainActivity), niezależnie od aktualnego trybu
     * uruchamiania, żeby wartość była aktualna, gdy tryb "kontynuuj" zostanie włączony. */
    fun saveLastTab(tabName: String) {
        store.setLastTab(tabName)
    }

    /** Zmiana języka appki (patrz i18n/Strings.kt) — trwale zapisana, więc obowiązuje też
     * po restarcie. Aktualizacja _state wystarczy, żeby CAŁE UI odświeżyło się natychmiast
     * (patrz LocalLang w MainActivity), bez potrzeby ponownego uruchamiania appki. */
    fun setLanguage(lang: String) {
        store.setLanguage(lang)
        _state.value = _state.value.copy(language = lang)
    }

    fun setAutoCloseAddToList(value: Boolean) {
        store.setAutoCloseAddToList(value)
        _state.value = _state.value.copy(autoCloseAddToList = value)
    }

    fun setPlayerDebugOverlay(value: Boolean) {
        store.setPlayerDebugOverlay(value)
        _state.value = _state.value.copy(playerDebugOverlay = value)
    }

    fun toggleHomeSectionCollapsed(sectionKey: String) {
        val current = _state.value.collapsedHomeSections
        val updated = if (sectionKey in current) current - sectionKey else current + sectionKey
        store.setCollapsedHomeSections(updated)
        _state.value = _state.value.copy(collapsedHomeSections = updated)
    }

    fun addCustomMoodRule(trigger: String, keyword: String) {
        val t = trigger.trim().lowercase()
        val k = keyword.trim().lowercase()
        if (t.isBlank() || k.isBlank()) return
        val updated = _state.value.customMoodRules + (t to k)
        store.setCustomMoodRules(updated)
        _state.value = _state.value.copy(customMoodRules = updated)
    }

    fun removeCustomMoodRule(rule: Pair<String, String>) {
        val updated = _state.value.customMoodRules - rule
        store.setCustomMoodRules(updated)
        _state.value = _state.value.copy(customMoodRules = updated)
    }

    /** Znajduje typ pozycji po samym id — potrzebne tam, gdzie wywołujący zna tylko id
     * (np. istniejące gwiazdki ulubionych na kartach), a nie ma pod ręką pełnego obiektu
     * Channel/SeriesInfo. Ulubione/listy nigdy nie dotyczą pojedynczych odcinków serialu,
     * więc wystarczy sprawdzić live/movies/series. */
    private fun findKind(id: String): MediaKind? = when {
        _state.value.liveChannels.any { it.id == id } || rawLive.any { it.id == id } -> MediaKind.LIVE
        _state.value.movies.any { it.id == id } || rawMovies.any { it.id == id } -> MediaKind.MOVIE
        _state.value.series.any { it.id == id } || rawSeries.any { it.id == id } -> MediaKind.SERIES
        else -> null
    }

    // ══════════════════════════ Niestandardowe listy ("Moje listy") ══════════════════════════

    /** Tworzy nową, pustą listę i zwraca ją (np. żeby od razu otworzyć jej ekran szczegółów). */
    fun createList(name: String, emoji: String? = "🎬"): CustomList {
        // Ta sama zasada co w setListEmoji — nowej liście też nie można nadać gwiazdki,
        // niezależnie od tego, skąd akurat przyszło wywołanie.
        val safeEmoji = if (emoji == FAVORITES_EMOJI) "🎬" else emoji
        val list = CustomList(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Nowa lista" },
            emoji = safeEmoji,
        )
        persistLists(_state.value.customLists + list)
        return list
    }

    fun renameList(listId: String, name: String) {
        if (listId == CustomList.FAVORITES_ID) return // wbudowanej listy nie da się przemianować
        updateList(listId) { it.copy(name = name.ifBlank { it.name }) }
    }

    /** Zmiana ikonki listy — Ulubionych nie da się przeikonkować (na stałe ⭐, tak samo jak
     * nazwy się nie da zmienić), a żadnej INNEJ liście nie można nadać gwiazdki, żeby nie
     * powielać wizualnie znaczka zarezerwowanego dla Ulubionych w "Moje listy" (patrz też
     * LIST_EMOJI_CHOICES, gdzie gwiazdki celowo nie ma do wyboru — to tu jest tylko druga,
     * "twardsza" linia obrony na wypadek innej ścieżki wywołania w przyszłości). */
    fun setListEmoji(listId: String, emoji: String?) {
        if (listId == CustomList.FAVORITES_ID) return
        if (emoji == FAVORITES_EMOJI) return
        updateList(listId) { it.copy(emoji = emoji) }
    }

    fun setListColor(listId: String, colorHex: String?) {
        updateList(listId) { it.copy(colorHex = colorHex) }
    }

    /** Usuwa całą listę (nie da się usunąć wbudowanych Ulubionych — tam trzeba odznaczyć
     * pozycje pojedynczo gwiazdką, tak jak dotychczas). Jeśli usuwana lista jest akurat
     * otwarta na ekranie szczegółów, zamyka ten ekran. */
    fun deleteList(listId: String) {
        if (listId == CustomList.FAVORITES_ID) return
        persistLists(_state.value.customLists.filterNot { it.id == listId })
        if (_state.value.selectedListId == listId) closeListDetail()
    }

    fun addItemToList(listId: String, id: String, kind: MediaKind) {
        updateList(listId) { list ->
            if (list.items.any { it.id == id }) list
            else list.copy(items = list.items + ListItemRef(id = id, kind = kind))
        }
    }

    fun removeItemFromList(listId: String, id: String) {
        updateList(listId) { it.copy(items = it.items.filterNot { ref -> ref.id == id }) }
    }

    fun toggleItemInList(listId: String, id: String, kind: MediaKind) {
        val list = _state.value.customLists.find { it.id == listId }
        val already = list?.items?.any { it.id == id } == true
        if (already) removeItemFromList(listId, id) else addItemToList(listId, id, kind)
    }

    /** Dodaje/usuwa CAŁY zestaw zaznaczonych pozycji naraz (tryb wielokrotnego zaznaczania
     * na siatce Movies/Series) — jeśli wszystkie zaznaczone są już na liście, usuwa
     * wszystkie; w przeciwnym razie dokłada brakujące. Ten sam wzorzec co np. dodawanie
     * do albumu w Zdjęciach Google. */
    fun toggleManyInList(listId: String, refs: List<ListItemRef>) {
        val list = _state.value.customLists.find { it.id == listId } ?: return
        val existingIds = list.items.map { it.id }.toSet()
        val allPresent = refs.isNotEmpty() && refs.all { it.id in existingIds }
        updateList(listId) { current ->
            if (allPresent) {
                val removeIds = refs.map { it.id }.toSet()
                current.copy(items = current.items.filterNot { it.id in removeIds })
            } else {
                val toAdd = refs.filter { it.id !in existingIds }
                current.copy(items = current.items + toAdd)
            }
        }
    }

    /** Id list, które już zawierają WSZYSTKIE podane pozycje — do zaznaczania checkboxów
     * w oknie "Dodaj do listy" (dla pojedynczej pozycji to zwykłe „czy jest na liście”). */
    fun listIdsContainingAll(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        return _state.value.customLists.filter { list ->
            val present = list.items.map { it.id }.toSet()
            ids.all { it in present }
        }.map { it.id }.toSet()
    }

    private fun updateList(listId: String, transform: (CustomList) -> CustomList) {
        val updated = _state.value.customLists.map { if (it.id == listId) transform(it) else it }
        persistLists(updated)
    }

    private fun persistLists(lists: List<CustomList>) {
        store.saveLists(lists)
        _state.value = _state.value.copy(
            customLists = lists,
            // Ulubione (gwiazdki w całej appce) to teraz zwykła pochodna wbudowanej listy —
            // cały dotychczasowy kod sprawdzający `id in favorites` działa bez zmian.
            favorites = lists.find { it.id == CustomList.FAVORITES_ID }?.items?.map { it.id }?.toSet().orEmpty(),
        )
    }

    fun openListDetail(listId: String) {
        // Zablokowane po okresie próbnym — patrz requestPremiumAccess niżej i obszerny
        // komentarz przy isTrialExpired.
        if (requestPremiumAccess()) return
        _state.value = _state.value.copy(selectedListId = listId)
    }

    fun closeListDetail() {
        _state.value = _state.value.copy(selectedListId = null)
    }

    /** Centralny punkt kontrolny dla WSZYSTKICH funkcji zablokowanych po okresie próbnym.
     * Zwraca true (i pokazuje dialog zakupu), jeśli próba minęła i appka nie została
     * kupiona — w takim wypadku WOŁAJĄCY powinien przerwać akcję (patrz `if (...) return`
     * powyżej). Zwraca false, gdy wszystko w porządku — akcja może przebiegać normalnie. */
    fun requestPremiumAccess(): Boolean {
        if (!isTrialExpired) return false
        _state.value = _state.value.copy(showPurchasePrompt = true)
        return true
    }

    fun dismissPurchasePrompt() {
        _state.value = _state.value.copy(showPurchasePrompt = false)
    }

    /** Wołane z "Kup Pełną wersję" w PurchasePromptDialog — NIE woła launchPurchaseFlow
     * bezpośrednio (sekcja 4 specyfikacji z 09.10: przed oknem płatności Google Play musi
     * się pokazać ekran z dwoma checkboxami, patrz PrePurchaseScreen.kt). */
    fun openPrePurchaseScreen() {
        _state.value = _state.value.copy(showPurchasePrompt = false, showPrePurchaseScreen = true)
    }

    fun dismissPrePurchaseScreen() {
        _state.value = _state.value.copy(showPrePurchaseScreen = false)
    }

    /** Wołane PO "Przejdź do płatności" na PrePurchaseScreen (oba checkboxy już zaznaczone —
     * patrz enabled na przycisku w PrePurchaseScreen.kt). Zapisuje daty/godziny zaznaczenia
     * pola 1 i pola 2 (do rekordu zgód, sekcja 2 specyfikacji) i DOPIERO TERAZ uruchamia
     * właściwy proces zakupu w Google Play Billing. */
    fun confirmPrePurchaseAndLaunchBilling(activity: android.app.Activity, check1AtMillis: Long, check2AtMillis: Long) {
        // Daty zaznaczenia pola 1 i 2 czekają w rekordzie zgód jako "oczekujące" — trafią do
        // niego na stałe (razem z numerem zamówienia) dopiero po wykryciu udanego zakupu, patrz
        // billingManager.onStateChanged w init oraz LocalStore.recordPurchaseConsent.
        store.setPendingPurchaseConsents(check1AtMillis, check2AtMillis)
        _state.value = _state.value.copy(showPrePurchaseScreen = false)
        launchPurchaseFlow(activity)
    }

    /** Wołane z Ustawień -> Informacje prawne -> Zakup -> "Pokaż potwierdzenie" (patrz
     * SettingsScreen.kt) — ten sam ekran co ten pokazywany automatycznie zaraz po zakupie. */
    fun openPurchaseConfirmationScreen() {
        _state.value = _state.value.copy(showPurchaseConfirmationScreen = true)
    }

    fun dismissPurchaseConfirmationScreen() {
        _state.value = _state.value.copy(showPurchaseConfirmationScreen = false)
    }

    /**
     * Jednorazowa migracja: appka wcześniej miała tylko płaski zbiór ulubionych bez
     * informacji o typie (`Set<String>` w SharedPreferences). Przy pierwszym uruchomieniu
     * po aktualizacji trzeba dociągnąć typ każdego id, dopasowując je do aktualnie
     * wczytanej zawartości (live/movies/series) — dlatego wywoływane dopiero PO
     * refreshAll(), a nie w init(). Bezpieczna do wywołania wielokrotnego: nic nie robi,
     * jeśli wbudowana lista "favorites" już istnieje.
     */
    private fun migrateLegacyFavoritesIfNeeded() {
        if (_state.value.customLists.any { it.id == CustomList.FAVORITES_ID }) return
        val legacyIds = store.getFavoriteIds()
        val items = legacyIds.mapNotNull { id -> findKind(id)?.let { ListItemRef(id = id, kind = it) } }
        val favoritesList = CustomList(
            id = CustomList.FAVORITES_ID,
            name = "Ulubione",
            emoji = FAVORITES_EMOJI,
            items = items,
            isBuiltIn = true,
        )
        persistLists(listOf(favoritesList) + _state.value.customLists)
    }

    /** Ukryj/pokaż pojedynczą kategorię — odpowiednik toggleCategoryDisabled() w app.js. */
    fun toggleCategoryHidden(type: String, category: String) {
        val current = _state.value.hiddenCategories[type].orEmpty()
        val updated = if (category in current) current - category else current + category
        store.setHiddenCategories(type, updated)
        _state.value = _state.value.copy(hiddenCategories = _state.value.hiddenCategories + (type to updated))
        applyHiddenCategoryFilter()
    }

    /** "Zaznacz wszystkie" / "Odznacz wszystkie" dla danego typu — odpowiednik cat-bulk-btn w app.js. */
    fun setAllCategoriesHidden(type: String, allCategories: List<String>, hidden: Boolean) {
        val updated = if (hidden) allCategories.toSet() else emptySet()
        store.setHiddenCategories(type, updated)
        _state.value = _state.value.copy(hiddenCategories = _state.value.hiddenCategories + (type to updated))
        applyHiddenCategoryFilter()
    }

    /**
     * Wstępna preselekcja kategorii po prefiksie w nazwie (np. "PL" dla kategorii typu
     * "PL| FILMOWE") — jednorazowa akcja z Ustawień, NIE stałe ustawienie. Ukrywa WSZYSTKIE
     * kategorie (we wszystkich trzech typach: Live/Movie/Series) NIE zaczynające się od
     * podanego prefiksu, i pokazuje te, które się zaczynają — pełny reset, nie łączenie z
     * poprzednim stanem ukrycia, żeby wynik był przewidywalny niezależnie od tego, co było
     * ukryte wcześniej. Użytkownik może potem doprecyzować ręcznie pojedyncze kategorie,
     * dokładnie tak jak teraz — to tylko PUNKT STARTOWY, nie ograniczenie na stałe.
     */
    fun applyCategoryPrefilter(prefix: String) {
        if (prefix.isBlank()) return
        val trimmedPrefix = prefix.trim()
        val types = listOf(
            "live" to _state.value.allCategoriesLive,
            "movie" to _state.value.allCategoriesMovie,
            "series" to _state.value.allCategoriesSeries,
        )
        var updatedHidden = _state.value.hiddenCategories
        for ((type, allCategories) in types) {
            val toHide = allCategories.filterNot { it.startsWith(trimmedPrefix, ignoreCase = true) }.toSet()
            store.setHiddenCategories(type, toHide)
            updatedHidden = updatedHidden + (type to toHide)
        }
        _state.value = _state.value.copy(hiddenCategories = updatedHidden)
        applyHiddenCategoryFilter()
    }

    private fun applyHiddenCategoryFilter() {
        val hidden = _state.value.hiddenCategories
        val hiddenLive = hidden["live"].orEmpty()
        val hiddenMovie = hidden["movie"].orEmpty()
        val hiddenSeries = hidden["series"].orEmpty()
        val visibleLive = rawLive.filter { (it.group ?: "Bez kategorii") !in hiddenLive }
        val visibleMovies = rawMovies.filter { (it.group ?: "Bez kategorii") !in hiddenMovie }
        val visibleSeries = rawSeries.filter { (it.group ?: "Bez kategorii") !in hiddenSeries }
        _state.value = _state.value.copy(
            liveChannels = visibleLive,
            movies = visibleMovies,
            series = visibleSeries,
            recentMovies = visibleMovies.sortedByDescending { it.addedAt }.take(RECENT_LIMIT),
            recentSeries = visibleSeries.sortedByDescending { it.addedAt }.take(RECENT_LIMIT),
            recentLive = visibleLive.sortedByDescending { it.addedAt }.take(RECENT_LIMIT),
        )
    }

    /**
     * Otwiera ekran szczegółów serialu (sezony/odcinki) — odpowiednik openSeriesDetail()
     * z app.js. Jeśli sezony nie są jeszcze znane (fetchSeriesList zwraca tylko podstawowe
     * dane), dociąga je leniwie przez get_series_info i cache'uje w rawSeries/state.series.
     */
    fun openSeriesDetail(series: SeriesInfo) {
        _state.value = _state.value.copy(
            selectedSeries = series,
            seriesDetailError = null,
            similarSeries = computeSimilarSeries(series),
        )
        if (series.seasons.isNotEmpty()) return

        val source = _state.value.playlists.find { it.id == series.playlistId }
        if (source == null || source.type != PlaylistType.XTREAM) return

        _state.value = _state.value.copy(isLoadingSeriesDetail = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (seasons, fetchedInfo) = XtreamClient(source).fetchSeriesEpisodes(series.id)
                val updated = series.copy(
                    seasons = seasons,
                    plot = fetchedInfo.plot,
                    cast = fetchedInfo.cast,
                    genre = fetchedInfo.genre,
                    releaseDate = fetchedInfo.releaseDate,
                    rating = fetchedInfo.rating,
                )
                rawSeries = rawSeries.map { if (it.id == series.id) updated else it }
                _state.value = _state.value.copy(
                    isLoadingSeriesDetail = false,
                    selectedSeries = updated,
                    series = _state.value.series.map { if (it.id == series.id) updated else it },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingSeriesDetail = false,
                    seriesDetailError = "Błąd wczytywania odcinków: ${e.message}",
                )
            }
        }
    }

    fun closeSeriesDetail() {
        _state.value = _state.value.copy(selectedSeries = null, seriesDetailError = null, similarSeries = emptyList())
    }

    /**
     * Zapewnia, że sezony/odcinki danego serialu są znane, i wywołuje `onReady` z gotowym
     * obiektem — używane przez przycisk "Pobierz sezon" na plakacie (siatka Series, Home),
     * gdzie w przeciwieństwie do ekranu szczegółów sezony NIE są jeszcze doładowane.
     * Jeśli `series` ma już sezony (np. bo to state.selectedSeries z otwartego ekranu
     * szczegółów), wywołuje callback od razu bez zapytania sieciowego.
     */
    fun ensureSeriesEpisodesLoaded(series: SeriesInfo, onReady: (SeriesInfo) -> Unit) {
        if (series.seasons.isNotEmpty()) {
            onReady(series)
            return
        }
        val source = _state.value.playlists.find { it.id == series.playlistId }
        if (source == null || source.type != PlaylistType.XTREAM) {
            onReady(series)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val updated = try {
                val (seasons, _) = XtreamClient(source).fetchSeriesEpisodes(series.id)
                val merged = series.copy(seasons = seasons)
                rawSeries = rawSeries.map { if (it.id == series.id) merged else it }
                _state.value = _state.value.copy(
                    series = _state.value.series.map { if (it.id == series.id) merged else it },
                    selectedSeries = if (_state.value.selectedSeries?.id == series.id) merged else _state.value.selectedSeries,
                )
                merged
            } catch (e: Exception) {
                series
            }
            withContext(Dispatchers.Main) { onReady(updated) }
        }
    }

    /** Otwiera dialog wyboru sezonu do pobrania — wywoływane bezpośrednio z przycisku
     * pobierania na plakacie (siatka Series, Home, ekran szczegółów). Doładowuje odcinki
     * w tle jeśli trzeba, dialog pokazuje się dopiero gdy sezony są już znane. */
    fun requestSeriesSeasonDownload(series: SeriesInfo) {
        _state.value = _state.value.copy(isLoadingSeasonDownload = true)
        ensureSeriesEpisodesLoaded(series) { updated ->
            _state.value = _state.value.copy(
                isLoadingSeasonDownload = false,
                // null gdy się nie udało doładować odcinków (np. brak sieci) — MainActivity
                // pokazuje wtedy toast zamiast pustego dialogu.
                seasonDownloadTarget = if (updated.seasons.isNotEmpty()) updated else null,
            )
        }
    }

    fun closeSeasonDownloadPicker() {
        _state.value = _state.value.copy(seasonDownloadTarget = null)
    }

    /** "Inne seriale w tej kategorii" — jak computeSimilarMovies(), ale dla seriali nie mamy
     * dociąganego osobno pola genre, więc dopasowanie jest wyłącznie po wspólnym group. */
    private fun computeSimilarSeries(series: SeriesInfo): List<SeriesInfo> {
        if (series.group.isNullOrBlank()) return emptyList()
        return _state.value.series
            .filter { it.id != series.id && it.group == series.group }
            .sortedBy { it.name }
            .take(SIMILAR_LIMIT)
    }

    /**
     * Otwiera ekran informacji o filmie — odpowiednik kliknięcia plakatu w app.js, gdzie
     * opis/obsada/reżyser/gatunek/ocena były dociągane bezpośrednio z danych playlisty
     * (Xtream: action=get_vod_info&vod_id=X, cache'owane w pamięci na czas sesji).
     */
    fun openMovieDetail(movie: Channel) {
        val cached = vodDetailCache[movie.id]
        _state.value = _state.value.copy(
            selectedMovie = movie,
            selectedMovieDetail = cached,
            similarMovies = computeSimilarMovies(movie),
        )
        if (cached != null) return

        val source = _state.value.playlists.find { it.id == movie.playlistId }
        val vodId = movie.xtreamStreamId
        if (source == null || source.type != PlaylistType.XTREAM || vodId == null) return

        _state.value = _state.value.copy(isLoadingMovieDetail = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val detail = XtreamClient(source).fetchVodInfo(vodId)
                vodDetailCache[movie.id] = detail
                // użytkownik mógł już zamknąć/zmienić ekran zanim odpowiedź wróciła —
                // aktualizujemy widoczny stan tylko jeśli to nadal ten sam film
                if (_state.value.selectedMovie?.id == movie.id) {
                    _state.value = _state.value.copy(
                        isLoadingMovieDetail = false,
                        selectedMovieDetail = detail,
                        // gatunek z dociągniętego detalu mógł odblokować dodatkowe trafienia
                        // po wspólnym genre — przeliczamy listę "Podobnych" jeszcze raz
                        similarMovies = computeSimilarMovies(movie),
                    )
                } else {
                    _state.value = _state.value.copy(isLoadingMovieDetail = false)
                }
            } catch (e: Exception) {
                if (_state.value.selectedMovie?.id == movie.id) {
                    _state.value = _state.value.copy(isLoadingMovieDetail = false)
                }
            }
        }
    }

    fun closeMovieDetail() {
        _state.value = _state.value.copy(
            selectedMovie = null,
            selectedMovieDetail = null,
            isLoadingMovieDetail = false,
            similarMovies = emptyList(),
        )
    }

    /**
     * "Podobne filmy" — prosta heurystyka licząca się lokalnie, bez żadnego dodatkowego
     * zapytania do API (ani M3U, ani Xtream Codes nie mają wbudowanego silnika rekomendacji
     * jak Netflix). Punktacja: +2 za wspólną kategorię (Channel.group, dostępne od razu dla
     * każdego filmu), +1 za wspólny gatunek (VodDetail.genre) — ale genre jest dociągane
     * leniwie tylko dla otwieranych ekranów szczegółów, więc ten bonus działa opportunistycznie
     * tylko dla filmów, których VodDetail jest już w vodDetailCache z bieżącej sesji.
     */
    /** Wersja PUBLICZNA computeSimilarMovies — do użycia poza ViewModelem, w miejscach
     * budujących kolejkę odtwarzania BEZ przechodzenia przez ekran szczegółów filmu (patrz
     * MainActivity.buildSimilarMoviesQueue). Ekran szczegółów sam liczy podobne filmy i
     * zapisuje je w state.similarMovies — ale szybkie odtworzenie z plakatu (przycisk ▶ na
     * Home/w siatce Movies) całkowicie omija ten ekran, więc state.similarMovies zostawał
     * pusty albo nieaktualny (z poprzednio oglądanego filmu) — stąd zgłoszenie "w filmach
     * brak takich podpowiedzi". Ta metoda liczy je na miejscu, niezależnie od tego stanu. */
    fun similarMoviesFor(movie: Channel): List<Channel> = computeSimilarMovies(movie)

    private fun computeSimilarMovies(movie: Channel): List<Channel> {
        val currentGenres = parseGenres(vodDetailCache[movie.id]?.genre)
        return _state.value.movies.asSequence()
            .filter { it.id != movie.id }
            .map { candidate ->
                var score = 0
                if (!movie.group.isNullOrBlank() && candidate.group == movie.group) score += 2
                if (currentGenres.isNotEmpty()) {
                    val candidateGenres = parseGenres(vodDetailCache[candidate.id]?.genre)
                    if (candidateGenres.intersect(currentGenres).isNotEmpty()) score += 1
                }
                candidate to score
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Channel, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
            .take(SIMILAR_LIMIT)
            .toList()
    }

    private fun parseGenres(genre: String?): Set<String> =
        genre?.split(",", "/").orEmpty().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** Odświeża "Kontynuuj oglądanie" po powrocie z PlayerActivity (który zapisuje postęp
     * przez własną instancję LocalStore) — wywoływane z MainActivity.onResume(). */
    fun refreshWatchProgress() {
        _state.value = _state.value.copy(watchProgress = store.getWatchProgress())
        syncWatchNext()
    }

    /** Odbicie "Kontynuuj oglądanie" na ekranie głównym Android TV — patrz WatchNextSync
     * (na telefonie nic nie robi). W tle, bo to zapytania do systemowej bazy programów TV.
     * ZAWSZE aktywne, niezależnie od okresu próbnego/zakupu — Watch Next nie jest objęte
     * blokadą (patrz TrialGate.kt). */
    private fun syncWatchNext() {
        viewModelScope.launch(Dispatchers.IO) {
            WatchNextSync.sync(getApplication<Application>(), _state.value.watchProgress)
        }
    }

    /** Usuwa pojedynczą pozycję z "Kontynuuj oglądanie" — np. gdy użytkownik nie chce
     * kontynuować i chce sprzątnąć kafelek z Home/Live/Movies/Series. */
    fun removeWatchProgress(id: String) {
        store.removeWatchProgress(id)
        _state.value = _state.value.copy(watchProgress = _state.value.watchProgress - id)
        syncWatchNext()
    }

    /** Eksport kopii zapasowej (playlisty, ulubione, ukryte kategorie, historia oglądania). */
    fun exportBackupJson(): String = store.exportBackupJson()

    /** Import kopii zapasowej z pliku JSON — nadpisuje lokalne dane i odświeża stan/UI. */
    fun importBackupJson(json: String): Boolean {
        val ok = store.importBackupJson(json)
        if (ok) {
            _state.value = _state.value.copy(
                playlists = store.getPlaylists(),
                favorites = store.getFavoriteIds(),
                watchProgress = store.getWatchProgress(),
                hiddenCategories = mapOf(
                    "live" to store.getHiddenCategories("live"),
                    "movie" to store.getHiddenCategories("movie"),
                    "series" to store.getHiddenCategories("series"),
                ),
                customLists = store.getLists(),
                selectedListId = null,
            )
            refreshAll()
        }
        return ok
    }

    fun refreshAll() {
        val playlists = _state.value.playlists
        if (playlists.isEmpty()) return
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            // Samo pobieranie wydzielone do CatalogFetcher — ten sam kod używa też nocne
            // odświeżanie w tle (CatalogRefreshWorker).
            val result = CatalogFetcher.fetchAll(playlists)
            val allLive = result.live
            val allMovies = result.movies
            val allSeries = result.series
            val statuses = result.statuses
            val error = result.error
            val checkedAt = result.checkedAt

            // Zapisujemy znacznik czasu niezależnie od tego, czy któraś playlista zwróciła
            // błąd — próba odświeżenia się zakończyła, więc to i tak "ostatni raz gdy
            // aplikacja próbowała pobrać świeże dane", a nie tylko pełny sukces.
            val refreshedAt = checkedAt
            store.setLastRefreshAt(refreshedAt)
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = error,
                lastRefreshAt = refreshedAt,
                playlistStatuses = statuses,
                allCategoriesLive = allLive.mapNotNull { it.group }.distinct().sorted(),
                allCategoriesMovie = allMovies.mapNotNull { it.group }.distinct().sorted(),
                allCategoriesSeries = allSeries.mapNotNull { it.group }.distinct().sorted(),
            )
            rawLive = allLive
            rawMovies = allMovies
            rawSeries = allSeries
            vodDetailCache.clear()
            applyHiddenCategoryFilter()
            migrateLegacyFavoritesIfNeeded()
            // Zapis do lokalnego cache — patrz LocalStore.saveCatalogCache i komentarz przy
            // wczytywaniu w init{} niżej. Zapisujemy NIEZALEŻNIE od tego, czy któraś
            // playlista akurat zwróciła błąd (error != null) — nawet CZĘŚCIOWO świeże dane
            // (z playlist, które akurat odpowiedziały) są lepsze niż brak czegokolwiek do
            // pokazania przy następnym starcie appki. Już na wątku IO (patrz launch wyżej),
            // więc bez dodatkowego przełączania kontekstu.
            store.saveCatalogCache(allLive, allMovies, allSeries)
        }
    }

    // ══════════════════════════ Folder (lokalne pliki na urządzeniu) ══════════════════════════

    /**
     * Rejestruje nowo podłączony folder (URI drzewa z już przyznanym trwałym uprawnieniem
     * odczytu — patrz MainActivity.startAddLocalFolder, które musi wywołać
     * takePersistableUriPermission PRZED wywołaniem tej metody) i od razu go skanuje.
     */
    fun addLocalFolder(uri: String, name: String) {
        val folder = LocalFolder(id = UUID.randomUUID().toString(), uri = uri, name = name)
        val updated = _state.value.localFolders + folder
        store.saveLocalFolders(updated)
        _state.value = _state.value.copy(localFolders = updated)
        rescanLocalFolders()
    }

    /** Świeży odczyt listy pobrań z LocalStore — wywoływane przy KAŻDYM wejściu na ekran
     * "Pobrane" (patrz LaunchedEffect w SettingsScreen), bo nowe pobrania mogły zostać
     * zlecone gdzie indziej w appce (DownloadHelper.downloadItem zapisuje bezpośrednio do
     * LocalStore, nie przez ten ViewModel — trzeba więc "podciągnąć" świeży stan). */
    fun refreshDownloads() {
        _state.value = _state.value.copy(downloads = store.getDownloads())
    }

    /** Usuwa TYLKO rekord metadanych z appki — samo anulowanie/skasowanie pobrania w
     * DownloadManager (system) robi wywołujący (patrz DownloadsScreen.onDelete), bo do tego
     * potrzeba DownloadManager, którego ten ViewModel nie trzyma. */
    fun removeDownload(managerId: Long) {
        store.removeDownload(managerId)
        _state.value = _state.value.copy(downloads = store.getDownloads())
    }

    /** Odłącza folder — zwalnia też trwałe uprawnienie SAF, żeby nie zostawiać "wiszących"
     * uprawnień do folderów, których appka już nie używa. */
    fun removeLocalFolder(id: String) {
        val folder = _state.value.localFolders.find { it.id == id }
        val updated = _state.value.localFolders.filterNot { it.id == id }
        store.saveLocalFolders(updated)
        _state.value = _state.value.copy(
            localFolders = updated,
            localVideos = _state.value.localVideos.filterNot { it.folderId == id },
            localFolderErrors = _state.value.localFolderErrors - id,
        )
        folder?.let {
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(it.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: Exception) {
                // Uprawnienie mogło już zostać cofnięte spoza appki (np. w Ustawieniach
                // systemowych) — nie ma nic do zrobienia, więc po prostu ignorujemy.
            }
        }
    }

    /** Skanuje wszystkie podłączone foldery od nowa — wywoływane po dodaniu/usunięciu
     * folderu oraz przy starcie appki (foldery mogły zmienić zawartość między sesjami). */
    fun rescanLocalFolders() {
        val folders = _state.value.localFolders
        if (folders.isEmpty()) {
            _state.value = _state.value.copy(localVideos = emptyList(), localFolderErrors = emptyMap())
            return
        }
        _state.value = _state.value.copy(isScanningLocalFolders = true)
        viewModelScope.launch(Dispatchers.IO) {
            val allVideos = mutableListOf<LocalVideoFile>()
            val errors = mutableMapOf<String, String>()
            val context = getApplication<Application>()
            for (folder in folders) {
                try {
                    allVideos += LocalFolderScanner.scanFolder(context, folder)
                } catch (e: Exception) {
                    errors[folder.id] = e.message ?: "Nie udało się odczytać folderu"
                }
            }
            _state.value = _state.value.copy(
                isScanningLocalFolders = false,
                localVideos = allVideos,
                localFolderErrors = errors,
            )
            extractThumbnailsInBackground(allVideos)
        }
    }

    /**
     * Wyciąga miniatury dla plików lokalnych JEDEN PO DRUGIM w tle (nie równolegle — kilka
     * jednoczesnych MediaMetadataRetriever na słabszym urządzeniu mogłoby zjeść sporo pamięci
     * naraz) i aktualizuje stan PO KAŻDEJ z osobna — miniatury pojawiają się na liście
     * stopniowo, zamiast czekać, aż WSZYSTKIE się doczytają. LocalVideoThumbnailer sam
     * sprawdza cache na dysku, więc powtórne wywołanie (np. przy każdym wejściu na zakładkę
     * Moje, patrz LocalFolderScreen.onRescan) kosztuje realnie tylko dla NOWYCH plików.
     */
    private fun extractThumbnailsInBackground(videos: List<LocalVideoFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            for (video in videos) {
                val path = LocalVideoThumbnailer.extractOrGetCached(context, video) ?: continue
                _state.value = _state.value.copy(
                    localVideos = _state.value.localVideos.map {
                        if (it.id == video.id) it.copy(thumbnailPath = path) else it
                    },
                )
            }
        }
    }

    companion object {
        // Liczba kafelków pokazywanych w każdej sekcji "Ostatnio dodane" na Home.
        private const val RECENT_LIMIT = 12
        // Liczba kafelków pokazywanych w sekcji "Podobne filmy" na ekranie szczegółów.
        private const val SIMILAR_LIMIT = 15
        // Ikonka zarezerwowana wyłącznie dla wbudowanej listy Ulubione — patrz setListEmoji/createList.
        private const val FAVORITES_EMOJI = "⭐"
        // Liczba dni okresu próbnego: patrz TrialGate.TRIAL_DAYS (jedno miejsce, używane
        // też przez PlayerActivity, która nie ma dostępu do tej instancji ViewModelu).
    }
}
