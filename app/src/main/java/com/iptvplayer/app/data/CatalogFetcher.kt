package com.iptvplayer.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Wynik pobrania katalogu ze wszystkich playlist naraz — patrz CatalogFetcher.fetchAll. */
data class CatalogFetchResult(
    val live: List<Channel>,
    val movies: List<Channel>,
    val series: List<SeriesInfo>,
    val statuses: Map<String, PlaylistStatus>,
    val error: String?,
    val checkedAt: Long,
)

/**
 * Pobranie kanałów/filmów/seriali ze wszystkich playlist — wydzielone z
 * AppViewModel.refreshAll, żeby ten sam kod mógł działać też W TLE, bez otwartej appki
 * (nocne odświeżanie, patrz CatalogRefreshWorker). Dzięki temu po uruchomieniu appki od
 * razu widać świeży katalog z lokalnej pamięci, zamiast wczorajszego.
 *
 * Wolne od stanu ekranu — tylko zapytania sieciowe i złożenie wyniku. Wołać WYŁĄCZNIE z
 * wątku w tle (zapytania sieciowe są blokujące).
 */
object CatalogFetcher {
    // Jeden wspólny klient HTTP dla playlist M3U — wcześniej przy każdym odświeżeniu
    // tworzony był nowy (osobna pula połączeń i wątków za każdym razem).
    private val m3uHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun fetchAll(playlists: List<PlaylistSource>): CatalogFetchResult {
        val allLive = mutableListOf<Channel>()
        val allMovies = mutableListOf<Channel>()
        val allSeries = mutableListOf<SeriesInfo>()
        val statuses = mutableMapOf<String, PlaylistStatus>()
        var error: String? = null
        val checkedAt = System.currentTimeMillis()

        for (source in playlists) {
            try {
                var account: XtreamAccountInfo? = null
                when (source.type) {
                    PlaylistType.XTREAM -> {
                        val client = XtreamClient(source)
                        allLive += client.fetchLiveChannels()
                        allMovies += client.fetchMovies()
                        allSeries += client.fetchSeriesList()
                        // Data ważności konta — celowo nie rzuca wyjątku (patrz
                        // XtreamClient.fetchAccountInfo), brak tej informacji nie przerywa
                        // reszty odświeżania.
                        account = client.fetchAccountInfo()
                    }
                    PlaylistType.M3U -> {
                        val requestBuilder = Request.Builder().url(source.url)
                        source.userAgent?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("User-Agent", it) }
                        val content = m3uHttp.newCall(requestBuilder.build())
                            .execute().use { it.body?.string() }.orEmpty()
                        val entries = M3UParser.parse(content)
                        allLive += M3UParser.toChannels(entries, source.id)
                    }
                }
                statuses[source.id] = PlaylistStatus(
                    ok = true,
                    checkedAt = checkedAt,
                    expiresAtMillis = account?.expiresAtMillis,
                    accountStatus = account?.status,
                )
            } catch (e: Exception) {
                error = "Błąd wczytywania '${source.name}': ${e.message}"
                statuses[source.id] = PlaylistStatus(ok = false, errorMessage = e.message, checkedAt = checkedAt)
            }
        }
        return CatalogFetchResult(allLive, allMovies, allSeries, statuses, error, checkedAt)
    }
}
