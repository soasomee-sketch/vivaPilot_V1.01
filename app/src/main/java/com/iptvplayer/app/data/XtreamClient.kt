package com.iptvplayer.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Klient Xtream Codes API — port logiki fetchowania list z app.js
 * (player_api.php?username=...&password=...&action=get_live_streams / get_vod_streams / get_series).
 */
class XtreamClient(
    private val source: PlaylistSource,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val gson = Gson()
    private val base get() = source.url.trimEnd('/')
    private val user get() = source.username.orEmpty()
    private val pass get() = source.password.orEmpty()

    private data class LiveStreamDto(
        @SerializedName("stream_id") val streamId: Int?,
        @SerializedName("name") val name: String?,
        @SerializedName("stream_icon") val icon: String?,
        @SerializedName("category_id") val categoryId: String?,
        @SerializedName("epg_channel_id") val epgChannelId: String?,
        @SerializedName("added") val added: String?,
        // JsonElement (nie String/Int) CELOWO — różne panele Xtream Codes zwracają te dwa
        // pola raz jako liczby, raz jako stringi ("1" vs 1), a Gson przy sztywnym typie
        // String/Int rzuciłby wyjątkiem przy niezgodności i wywalił parsowanie CAŁEJ listy
        // kanałów, nie tylko brak danych o catch-upie. JsonElement przyjmuje dowolny typ
        // bezpiecznie, konwersję robimy sami niżej (patrz asFlexibleString).
        @SerializedName("tv_archive") val tvArchive: com.google.gson.JsonElement? = null,
        @SerializedName("tv_archive_duration") val tvArchiveDuration: com.google.gson.JsonElement? = null,
    )

    private data class VodStreamDto(
        @SerializedName("stream_id") val streamId: Int?,
        @SerializedName("name") val name: String?,
        @SerializedName("stream_icon") val icon: String?,
        @SerializedName("category_id") val categoryId: String?,
        @SerializedName("container_extension") val ext: String?,
        @SerializedName("added") val added: String?,
    )

    /** Odpowiedź action=get_vod_info&vod_id=X — Xtream zagnieżdża właściwe dane opisowe w "info". */
    private data class VodInfoResponseDto(
        @SerializedName("info") val info: VodInfoDto?,
    )

    private data class VodInfoDto(
        @SerializedName("plot") val plot: String?,
        @SerializedName("cast") val cast: String?,
        @SerializedName("director") val director: String?,
        @SerializedName("genre") val genre: String?,
        @SerializedName("releasedate") val releaseDate: String?,
        @SerializedName("release_date") val releaseDateAlt: String?,
        @SerializedName("duration") val duration: String?,
        @SerializedName("rating") val rating: String?,
    )

    private data class SeriesDto(
        @SerializedName("series_id") val seriesId: Int?,
        @SerializedName("name") val name: String?,
        @SerializedName("cover") val cover: String?,
        @SerializedName("category_id") val categoryId: String?,
        @SerializedName("last_modified") val lastModified: String?,
    )

    private data class CategoryDto(
        @SerializedName("category_id") val categoryId: String?,
        @SerializedName("category_name") val categoryName: String?,
    )

    private data class EpisodeDto(
        @SerializedName("id") val id: String?,
        @SerializedName("episode_num") val episodeNum: Int?,
        @SerializedName("title") val title: String?,
        @SerializedName("container_extension") val ext: String?,
    )

    private data class SeriesInfoResponseDto(
        @SerializedName("episodes") val episodes: Map<String, List<EpisodeDto>>?,
        // Xtream zwraca to razem z sezonami/odcinkami w TYM SAMYM wywołaniu (w
        // przeciwieństwie do filmów, gdzie opis to osobny endpoint get_vod_info) — patrz
        // fetchSeriesEpisodes niżej, które teraz zwraca oba naraz zamiast tylko sezonów.
        @SerializedName("info") val info: SeriesDetailInfoDto?,
    )

    private data class SeriesDetailInfoDto(
        @SerializedName("plot") val plot: String?,
        @SerializedName("cast") val cast: String?,
        @SerializedName("genre") val genre: String?,
        @SerializedName("releaseDate") val releaseDate: String?,
        @SerializedName("release_date") val releaseDateAlt: String?,
        @SerializedName("rating") val rating: String?,
    )

    private data class AccountInfoResponseDto(
        @SerializedName("user_info") val userInfo: UserInfoDto?,
    )

    private data class UserInfoDto(
        @SerializedName("status") val status: String?,
        @SerializedName("exp_date") val expDate: String?,
    )

    private fun get(action: String): String {
        val url = "$base/player_api.php?username=$user&password=$pass&action=$action"
        val req = Request.Builder().url(url).apply {
            source.userAgent?.let { header("User-Agent", it) }
            source.referer?.let { header("Referer", it) }
        }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Xtream API HTTP ${resp.code} dla action=$action")
            return resp.body?.string().orEmpty()
        }
    }

    /**
     * Xtream zwraca w streamach/vod/series tylko liczbowe category_id (np. "1757") —
     * żeby pokazać w chipach czytelną nazwę (tak jak w app.js: liveCatMap/vodCatMap/
     * seriesCatMap budowane z get_live_categories / get_vod_categories / get_series_categories),
     * trzeba osobno pobrać listę kategorii i zmapować id -> category_name.
     */
    private fun fetchCategoryMap(action: String): Map<String, String> {
        return try {
            val json = get(action)
            val list = gson.fromJson(json, Array<CategoryDto>::class.java) ?: return emptyMap()
            list.mapNotNull { c -> c.categoryId?.let { id -> id to (c.categoryName ?: id) } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun categoryName(map: Map<String, String>, categoryId: String?): String {
        if (categoryId == null) return "Bez kategorii"
        return map[categoryId] ?: "Bez kategorii"
    }

    /** Xtream zwraca "added"/"last_modified" jako unix sekundy w formie stringa (czasem "0"). */
    private fun parseAddedAt(raw: String?): Long {
        val seconds = raw?.toLongOrNull() ?: return 0L
        return if (seconds > 0) seconds * 1000L else 0L
    }

    /** Bezpieczna konwersja JsonElement -> String, niezależnie czy dostawca zwrócił liczbę,
     * string, czy nic — patrz komentarz przy LiveStreamDto.tvArchive. */
    private fun com.google.gson.JsonElement?.asFlexibleString(): String? =
        if (this == null || this.isJsonNull) null else runCatching { this.asString }.getOrNull()

    /**
     * Dane konta (data ważności, status) — player_api.php wywołane BEZ parametru action.
     * To NIE jest treść playlisty (kanały/filmy/seriale), tylko metadane samego konta u
     * dostawcy, więc osobna, publiczna metoda zamiast doklejania do fetchLiveChannels itp.
     * Celowo nigdy nie rzuca wyjątku (zwraca null) — brak informacji o ważności konta nie
     * powinien wywalać całego odświeżania playlisty, to tylko dodatkowa, "miła w mieć" info.
     */
    fun fetchAccountInfo(): XtreamAccountInfo? {
        return try {
            val url = "$base/player_api.php?username=$user&password=$pass"
            val req = Request.Builder().url(url).apply {
                source.userAgent?.let { header("User-Agent", it) }
                source.referer?.let { header("Referer", it) }
            }.build()
            val json = http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            val info = gson.fromJson(json, AccountInfoResponseDto::class.java)?.userInfo ?: return null
            // exp_date="0" (albo puste/brak) to u Xtream utrwalona konwencja "konto
            // bezterminowe" — parseAddedAt(...) i tak zwraca wtedy 0L, więc takeIf > 0
            // poprawnie zamienia to na null (patrz komentarz przy PlaylistStatus).
            XtreamAccountInfo(
                status = info.status,
                expiresAtMillis = parseAddedAt(info.expDate).takeIf { it > 0 },
            )
        } catch (e: Exception) {
            null
        }
    }

    fun fetchLiveChannels(): List<Channel> {
        val catMap = fetchCategoryMap("get_live_categories")
        val json = get("get_live_streams")
        val list = gson.fromJson(json, Array<LiveStreamDto>::class.java) ?: return emptyList()
        return list.map { dto ->
            val sid = dto.streamId ?: 0
            Channel(
                // STABILNE id oparte na ID dostawcy (playlist + typ + stream_id) — NIE
                // losowy UUID jak poprzednio. Zgłoszenie: pozycje w listach użytkownika
                // (np. "Informacje" z kanałami na żywo) nigdy nie ładowały się poprawnie —
                // ekran pokazywał "Synchronizowanie z katalogiem..." bez końca (nawet po
                // 5 minutach), bo losowy UUID.randomUUID() generował ZUPEŁNIE NOWE ID przy
                // KAŻDYM odświeżeniu katalogu — stare odniesienia zapisane w liście (sprzed
                // odświeżenia) nigdy nie mogły dopasować się do nowego katalogu, bo ID było
                // inne za każdym razem. To samo dotyczyło ulubionych i historii oglądania
                // dla kanałów na żywo. "playlist + typ + stream_id" pozostaje identyczne
                // między odświeżeniami (dopóki dostawca nie zmieni własnego stream_id), i
                // jest unikalne między różnymi playlistami/typami nawet przy tym samym
                // stream_id z dwóch różnych źródeł.
                id = "${source.id}_live_$sid",
                playlistId = source.id,
                kind = MediaKind.LIVE,
                name = dto.name ?: "Unknown",
                logoUrl = dto.icon,
                group = categoryName(catMap, dto.categoryId),
                streamUrl = "$base/live/$user/$pass/$sid.ts",
                epgChannelId = dto.epgChannelId,
                addedAt = parseAddedAt(dto.added),
                hasCatchup = dto.tvArchive.asFlexibleString() == "1",
                catchupDays = dto.tvArchiveDuration.asFlexibleString()?.toIntOrNull() ?: 0,
                xtreamLiveStreamId = sid,
            )
        }
    }

    /**
     * URL "cofnij" (catch-up/timeshift) dla Xtream Codes — wzorzec zgodny ze specyfikacją
     * Xtream Codes API. Część paneli używa innej ścieżki (np. "/streaming/timeshift.php"
     * zamiast "/timeshift.php") — jeśli u konkretnego dostawcy to nie zadziała, to jest
     * jedyne miejsce do poprawienia.
     *
     * @param startMillis początek programu, który ma się odtworzyć od początku
     * @param durationMinutes długość NAGRANIA do pobrania (nie długość programu — zwykle
     *   wystarczy zaokrąglić w górę czas trwania programu, patrz wywołanie w LiveScreen)
     */
    fun buildCatchupUrl(streamId: Int, startMillis: Long, durationMinutes: Int): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        val start = fmt.format(java.util.Date(startMillis))
        return "$base/timeshift.php?username=$user&password=$pass&stream=$streamId&start=$start&duration=$durationMinutes"
    }

    fun fetchMovies(): List<Channel> {
        val catMap = fetchCategoryMap("get_vod_categories")
        val json = get("get_vod_streams")
        val list = gson.fromJson(json, Array<VodStreamDto>::class.java) ?: return emptyList()
        return list.map { dto ->
            val sid = dto.streamId ?: 0
            val ext = dto.ext ?: "mp4"
            Channel(
                // Ten sam powód i wzorzec co przy fetchLiveChannels() wyżej — STABILNE id,
                // nie losowy UUID, żeby listy/ulubione/historia oglądania dla filmów
                // przetrwały odświeżenie katalogu.
                id = "${source.id}_movie_$sid",
                playlistId = source.id,
                kind = MediaKind.MOVIE,
                name = dto.name ?: "Unknown",
                logoUrl = dto.icon,
                group = categoryName(catMap, dto.categoryId),
                streamUrl = "$base/movie/$user/$pass/$sid.$ext",
                xtreamStreamId = sid.toString(),
                addedAt = parseAddedAt(dto.added),
            )
        }
    }

    /**
     * Szczegóły filmu (opis, obsada, reżyser, gatunek, ocena, czas trwania) —
     * action=get_vod_info&vod_id=X. Dociągane leniwie dopiero po otwarciu ekranu
     * szczegółów, dokładnie tak jak sezony/odcinki serialu przez get_series_info.
     */
    fun fetchVodInfo(vodId: String): VodDetail {
        val json = get("get_vod_info&vod_id=$vodId")
        val dto = gson.fromJson(json, VodInfoResponseDto::class.java)?.info ?: return VodDetail()
        return VodDetail(
            plot = dto.plot?.trim()?.ifBlank { null },
            cast = dto.cast?.trim()?.ifBlank { null },
            director = dto.director?.trim()?.ifBlank { null },
            genre = dto.genre?.trim()?.ifBlank { null },
            releaseDate = (dto.releaseDate ?: dto.releaseDateAlt)?.trim()?.ifBlank { null },
            durationLabel = dto.duration?.trim()?.ifBlank { null },
            rating = dto.rating?.toDoubleOrNull(),
        )
    }

    /**
     * Pobiera sezony/odcinki ORAZ opis/obsadę/gatunek/ocenę dla pojedynczego serialu —
     * action=get_series_info&series_id=X. Xtream zwraca "episodes" jako mapę: numer sezonu
     * (string) -> lista odcinków, plus osobny obiekt "info" z opisem — oba w JEDNYM
     * wywołaniu, stąd zwracana para zamiast samej listy sezonów jak wcześniej.
     * Link streamu odcinka budujemy analogicznie do VOD: /series/user/pass/{id}.{ext}.
     */
    fun fetchSeriesEpisodes(seriesId: String): Pair<List<Season>, SeriesDetailInfo> {
        val url = "$base/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$seriesId"
        val req = Request.Builder().url(url).apply {
            source.userAgent?.let { header("User-Agent", it) }
            source.referer?.let { header("Referer", it) }
        }.build()
        val json = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Xtream API HTTP ${resp.code} dla get_series_info")
            resp.body?.string().orEmpty()
        }
        val dto = gson.fromJson(json, SeriesInfoResponseDto::class.java) ?: return emptyList<Season>() to SeriesDetailInfo()
        val seasons = dto.episodes.orEmpty().mapNotNull { (seasonKey, eps) ->
            val seasonNum = seasonKey.toIntOrNull() ?: return@mapNotNull null
            val episodes = eps.mapNotNull { e ->
                val eid = e.id ?: return@mapNotNull null
                val ext = e.ext ?: "mp4"
                Episode(
                    id = eid,
                    title = e.title ?: "Odcinek ${e.episodeNum ?: ""}",
                    streamUrl = "$base/series/$user/$pass/$eid.$ext",
                    episodeNumber = e.episodeNum ?: 0,
                )
            }.sortedBy { it.episodeNumber }
            Season(number = seasonNum, episodes = episodes)
        }.sortedBy { it.number }
        val info = dto.info?.let {
            SeriesDetailInfo(
                plot = it.plot?.trim()?.ifBlank { null },
                cast = it.cast?.trim()?.ifBlank { null },
                genre = it.genre?.trim()?.ifBlank { null },
                releaseDate = (it.releaseDate ?: it.releaseDateAlt)?.trim()?.ifBlank { null },
                rating = it.rating?.toDoubleOrNull(),
            )
        } ?: SeriesDetailInfo()
        return seasons to info
    }

    fun fetchSeriesList(): List<SeriesInfo> {
        val catMap = fetchCategoryMap("get_series_categories")
        val json = get("get_series")
        val list = gson.fromJson(json, Array<SeriesDto>::class.java) ?: return emptyList()
        return list.map { dto ->
            SeriesInfo(
                id = (dto.seriesId ?: 0).toString(),
                playlistId = source.id,
                name = dto.name ?: "Unknown",
                posterUrl = dto.cover,
                group = categoryName(catMap, dto.categoryId),
                addedAt = parseAddedAt(dto.lastModified),
            )
        }
    }
}
