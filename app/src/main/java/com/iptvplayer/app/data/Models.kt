package com.iptvplayer.app.data

/** Typ playlisty — tak jak w app.js: źródło Xtream Codes API albo zwykły plik M3U/M3U8. */
enum class PlaylistType { XTREAM, M3U }

data class PlaylistSource(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val url: String,          // dla M3U: pełny URL/plik; dla Xtream: host bazowy
    val username: String? = null,
    val password: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
)

/** Wynik ostatniej próby połączenia z daną playlistą (patrz AppViewModel.refreshAll) —
 * używane do wskaźnika statusu połączenia w headerze (zielony/żółty/czerwony) i do listy
 * playlist na ekranie Playlists, żeby było widać KTÓRA konkretnie ma problem. */
data class PlaylistStatus(
    val ok: Boolean,
    val errorMessage: String? = null,
    val checkedAt: Long = 0L,
    // Tylko dla playlist typu Xtream (M3U nie ma pojęcia "konta") — patrz
    // XtreamClient.fetchAccountInfo i AppViewModel.refreshAll. Null expiresAtMillis przy
    // ok=true i accountStatus!=null oznacza konto BEZTERMINOWE (Xtream: exp_date="0"), a nie
    // brak danych — rozróżnienie robi ekran (patrz PlaylistsScreen), sprawdzając accountStatus.
    val expiresAtMillis: Long? = null,
    val accountStatus: String? = null,
)

enum class MediaKind { LIVE, MOVIE, SERIES }

data class Channel(
    val id: String,
    val playlistId: String,
    val kind: MediaKind,
    val name: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val streamUrl: String,
    val epgChannelId: String? = null,   // do mapowania z XMLTV (tvg-id)
    val isFavorite: Boolean = false,
    // Numeryczne stream_id z Xtream API (tylko dla filmów) — potrzebne do dociągnięcia
    // szczegółów (opis/obsada/reżyser) przez action=get_vod_info&vod_id=X.
    val xtreamStreamId: String? = null,
    // Znacznik czasu dodania pozycji do playlisty (Xtream: pole "added", unix sekundy -> ms).
    // Używane do sekcji "Ostatnio dodane" na Home — odpowiednik "RECENTLY ADDED" w wersji web.
    val addedAt: Long = 0L,
    // Cofnij/catch-up (tylko Live, tylko Xtream Codes) — z pól "tv_archive"/
    // "tv_archive_duration" w get_live_streams (patrz XtreamClient.fetchLiveChannels).
    // catchupDays = ile dni wstecz dostawca faktycznie trzyma nagrania — poza tym oknem
    // nie ma czego odtwarzać, więc UI (EpgGuideDialog) filtruje po tym.
    val hasCatchup: Boolean = false,
    val catchupDays: Int = 0,
    // Numeryczne stream_id (jako Int, do budowy URL-a catch-up — patrz
    // XtreamClient.buildCatchupUrl) — inne niż xtreamStreamId wyżej (to pole istnieje
    // tylko dla filmów), więc osobne, żeby nie mieszać dwóch różnych przeznaczeń.
    val xtreamLiveStreamId: Int? = null,
)

data class SeriesInfo(
    val id: String,
    val playlistId: String,
    val name: String,
    val posterUrl: String? = null,
    val group: String? = null,
    val seasons: List<Season> = emptyList(),
    // Znacznik czasu dodania serialu do playlisty (Xtream: pole "last_modified"/"added") —
    // patrz Channel.addedAt, ten sam mechanizm dla sekcji "Ostatnio dodane" na Home.
    val addedAt: Long = 0L,
    // Opis/obsada/gatunek/ocena — z obiektu "info" w odpowiedzi get_series_info (Xtream
    // Codes zwraca to razem z sezonami/odcinkami w JEDNYM wywołaniu, w przeciwieństwie do
    // filmów, gdzie to osobny endpoint get_vod_info). Puste, dopóki fetchSeriesEpisodes się
    // nie wykona (czyli dopóki użytkownik nie wejdzie w szczegóły serialu) — patrz
    // AppViewModel.openSeriesDetail. Plakat/opis pochodzą WYŁĄCZNIE z playlisty użytkownika
    // (TMDB usunięte z aplikacji) — jeśli dostawca nie poda posterUrl, pozycja po prostu nie
    // ma plakatu, bez żadnego zapasowego źródła.
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
)

data class Season(
    val number: Int,
    val episodes: List<Episode> = emptyList(),
)

data class Episode(
    val id: String,
    val title: String,
    val streamUrl: String,
    val episodeNumber: Int,
)

/** Wpis EPG (program telewizyjny) sparsowany z XMLTV, z cache 5-minutowym jak w app.js (epgCache). */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Wpis pozycji na niestandardowej liście użytkownika — id + typ, żeby jedna lista mogła
 * swobodnie mieszać kanały live, filmy i seriale (np. "Święta" = film + serial + kanał na
 * żywo), tym samym mechanizmem co WatchProgress.kind.
 */
data class ListItemRef(
    val id: String,
    val kind: MediaKind,
    // Kolejność dodania — do sortowania w widoku listy (najnowsze na końcu/początku).
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Niestandardowa lista użytkownika ("Święta", "Akcja" itp.) — odpowiednik "Moja lista"
 * z Netflixa, tylko że może ich być wiele naraz. Lista o id="favorites" jest wbudowana:
 * to dotychczasowe Ulubione (gwiazdka na kartach), nie da się jej skasować ani zmienić
 * nazwy, zawsze pojawia się pierwsza w sekcji "Moje listy" na Home.
 */
data class CustomList(
    val id: String,
    val name: String,
    val emoji: String? = "🎬",
    val colorHex: String? = null,
    val items: List<ListItemRef> = emptyList(),
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val FAVORITES_ID = "favorites"
    }
}

/**
 * Format pliku kopii zapasowej — eksport/import ulubionych, playlist, ukrytych kategorii
 * i historii oglądania (odpowiednik "Backup/Restore" z app.js, tam opartego o
 * JSON.stringify(localStorage) / JSON.parse). `version` pozwala w przyszłości
 * obsłużyć migrację starszych plików kopii zapasowej.
 */
data class BackupData(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val playlists: List<PlaylistSource> = emptyList(),
    val favorites: List<String> = emptyList(),
    val hiddenCategories: Map<String, List<String>> = emptyMap(),
    val watchProgress: Map<String, WatchProgress> = emptyMap(),
    // Dodane w wersji 2 — niestandardowe listy (w tym wbudowana "favorites"). Pole
    // opcjonalne z pustą listą domyślną, żeby stare kopie zapasowe (wersja 1, bez tego
    // pola) nadal wczytywały się poprawnie przez Gson bez wyjątku.
    val customLists: List<CustomList> = emptyList(),
)

data class WatchProgress(
    val channelOrEpisodeId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAt: Long,
    // --- rozszerzone o dane potrzebne do wyświetlenia kafelka i wznowienia bez
    // ponownego przeszukiwania list (działa jednakowo dla live/movie/odcinka serialu) ---
    val title: String = "",
    val posterUrl: String? = null,
    val kind: MediaKind = MediaKind.LIVE,
    val streamUrl: String = "",
    val userAgent: String? = null,
    val referer: String? = null,
    // Wypełnione TYLKO dla odcinków serialu — patrz QueueEntry.seriesId. Używane do
    // grupowania w sekcji "Kontynuuj oglądanie" (patrz forContinueWatching niżej).
    val seriesId: String? = null,
)

/** Lista do sekcji "Kontynuuj oglądanie" — dla serialu pokazujemy TYLKO najnowszy oglądany
 * odcinek zamiast osobnego kafelka na każdy zaczęty odcinek (przy kilku rozpoczętych
 * odcinkach tego samego serialu robiło się to przytłaczające — kilka identycznych plakatów
 * pod rząd, patrz zgłoszenie użytkownika). Filmy/kanały live/pliki lokalne nie mają seriesId,
 * więc każdy z nich i tak zostaje własną, osobną pozycją — bez zmiany zachowania.
 */
fun Map<String, WatchProgress>.forContinueWatching(): List<WatchProgress> =
    values
        .groupBy { it.seriesId ?: it.channelOrEpisodeId }
        .map { (_, group) -> group.maxBy { it.updatedAt } }
        .sortedByDescending { it.updatedAt }

/**
 * Szczegółowe informacje o filmie (opis, obsada, reżyser, gatunek, ocena, czas trwania) —
 * odpowiednik danych pobieranych w app.js z action=get_vod_info Xtream API i wyświetlanych
 * w kafelku szczegółów filmu (dokładnie ten sam ekran co na zrzucie z wersji webowej).
 * Dociągane leniwie, tak samo jak sezony/odcinki serialu przez get_series_info.
 */
data class VodDetail(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val durationLabel: String? = null,
    val rating: Double? = null,
)

/** Lekki odpowiednik VodDetail dla seriali — zwracany razem z sezonami/odcinkami przez
 * XtreamClient.fetchSeriesEpisodes (Xtream daje to w JEDNYM wywołaniu, w przeciwieństwie
 * do filmów, gdzie opis to osobny endpoint). Bez pola director/durationLabel — Xtream
 * zwykle nie podaje tego dla seriali, w przeciwieństwie do filmów. */
data class SeriesDetailInfo(
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
)

/**
 * Informacje o koncie z Xtream Codes (player_api.php BEZ parametru action) — data ważności
 * (kiedy wygasa wykupiony dostęp) i status konta. `expiresAtMillis == null` oznacza konto
 * bezterminowe (Xtream zwraca wtedy exp_date="0" albo puste) — patrz XtreamClient.fetchAccountInfo.
 */
data class XtreamAccountInfo(
    val status: String? = null,
    val expiresAtMillis: Long? = null,
)

/**
 * Lokalny folder na urządzeniu, podłączony przez użytkownika przez systemowy wybór folderu
 * (Storage Access Framework) — zastępuje zakładkę Playlists na dolnym pasku. Przechowujemy
 * tylko URI drzewa (content://…/tree/…) z przyznanym trwałym uprawnieniem odczytu — z tego
 * da się w każdej chwili ponownie zeskanować zawartość, także po restarcie aplikacji/telefonu.
 */
data class LocalFolder(
    val id: String,
    val uri: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Rekord pojedynczego pobrania, zapisywany lokalnie w momencie zlecenia go systemowemu
 * DownloadManager (patrz DownloadHelper.downloadItem) — sam DownloadManager NIE daje
 * żadnego sposobu na wylistowanie "moich pobrań z tej appki" (widzi tylko surowe ID),
 * więc appka musi sama pamiętać, CO zleciła, żeby ekran "Pobrane" (DownloadsScreen) mógł
 * pokazać listę z tytułami/plakatami, a nie gołymi numerami ID. `managerId` to identyfikator
 * zwrócony przez DownloadManager.enqueue() — po nim odpytuje się o BIEŻĄCY status/postęp za
 * każdym razem (ten rekord trzyma tylko METADANE, nie stan pobierania, który zmienia się
 * cały czas). streamUrl/userAgent/referer zostają zapisane, żeby dało się ponowić pobieranie
 * bez wracania do źródła (np. po nieudanym pobraniu z powodu chwilowego braku sieci).
 */
data class DownloadRecord(
    val managerId: Long,
    val title: String,
    val posterUrl: String? = null,
    val streamUrl: String,
    val userAgent: String? = null,
    val referer: String? = null,
    val enqueuedAt: Long = System.currentTimeMillis(),
)

/**
 * Pojedynczy plik wideo znaleziony w podłączonym folderze — wynik skanowania na żywo
 * (patrz LocalFolderScanner), nie jest trwale przechowywany, bo zawartość folderu może się
 * zmieniać między uruchomieniami. `id` = sam URI dokumentu, więc jest stabilne w obrębie
 * jednego urządzenia i nadaje się jako klucz kolejki odtwarzania (patrz QueueEntry.id).
 */
data class LocalVideoFile(
    val id: String,
    val folderId: String,
    val uri: String,
    val title: String,
    val sizeBytes: Long = 0L,
    // Ścieżka do WYCIĘTEJ KLATKI z filmu (zapisanej lokalnie w cache appki jako JPEG, w
    // formie gotowego URI "file://…", nie surowej ścieżki — patrz LocalVideoThumbnailer)
    // — null, dopóki nie zostanie wyciągnięta w tle (patrz
    // AppViewModel.extractThumbnailsInBackground). Pliki lokalne nie mają żadnych
    // metadanych plakatu (w przeciwieństwie do VOD z Xtream), więc to jedyny sposób na
    // pokazanie czegokolwiek innego niż generyczna ikonka na liście/w "Kontynuuj oglądanie".
    val thumbnailPath: String? = null,
)

/** Migawka katalogu (kanały/filmy/seriale) zapisywana na dysk po każdym udanym
 * odświeżeniu — patrz LocalStore.getCatalogCache/saveCatalogCache i
 * AppViewModel.init/refreshAll. Pozwala pokazać poprzednio pobrane dane NATYCHMIAST przy
 * starcie appki, zamiast czekać na sieć za każdym razem od zera (tak jak robi to TiviMate). */
data class CatalogCacheDto(
    val liveChannels: List<Channel>,
    val movies: List<Channel>,
    val series: List<SeriesInfo>,
    val cachedAt: Long,
)
