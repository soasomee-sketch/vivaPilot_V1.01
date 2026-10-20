package com.iptvplayer.app.data

import java.util.UUID

/**
 * Parser M3U/M3U8 — port logiki z app.js (obsługa #EXTINF, tvg-id, tvg-logo, group-title
 * oraz #EXTVLCOPT:http-user-agent / http-referrer używanych przez playlisty wymagające auth).
 */
object M3UParser {

    data class ParsedEntry(
        val name: String,
        val logoUrl: String?,
        val group: String?,
        val tvgId: String?,
        val streamUrl: String,
        val userAgent: String?,
        val referer: String?,
    )

    private val attrRegex = Regex("""(\S+?)="([^"]*)"""")

    fun parse(content: String): List<ParsedEntry> {
        val lines = content.lines()
        val results = mutableListOf<ParsedEntry>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        var pendingUA: String? = null
        var pendingReferer: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U") -> continue

                line.startsWith("#EXTINF") -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    pendingLogo = attrs["tvg-logo"]
                    pendingGroup = attrs["group-title"]
                    pendingTvgId = attrs["tvg-id"]
                    // Nazwa kanału to tekst po ostatnim przecinku w linii #EXTINF
                    pendingName = line.substringAfterLast(",").trim().ifEmpty { attrs["tvg-name"] ?: "Unknown" }
                }

                line.startsWith("#EXTVLCOPT:http-user-agent") -> {
                    pendingUA = line.substringAfter("=", "").trim()
                }

                line.startsWith("#EXTVLCOPT:http-referrer") -> {
                    pendingReferer = line.substringAfter("=", "").trim()
                }

                line.startsWith("#") -> continue // inne tagi ignorujemy

                else -> {
                    // linia URL — zamyka bieżący wpis
                    results += ParsedEntry(
                        name = pendingName ?: "Unknown",
                        logoUrl = pendingLogo,
                        group = pendingGroup,
                        tvgId = pendingTvgId,
                        streamUrl = line,
                        userAgent = pendingUA,
                        referer = pendingReferer,
                    )
                    pendingName = null; pendingLogo = null; pendingGroup = null
                    pendingTvgId = null; pendingUA = null; pendingReferer = null
                }
            }
        }
        return results
    }

    fun toChannels(entries: List<ParsedEntry>, playlistId: String): List<Channel> =
        entries.map {
            Channel(
                // STABILNE id (playlist + URL strumienia) zamiast losowego UUID — ten sam
                // powód co w XtreamClient.kt (fetchLiveChannels/fetchMovies): losowy UUID
                // generował nowe id przy KAŻDYM ponownym parsowaniu playlisty, przez co listy
                // użytkownika/ulubione/historia oglądania nigdy nie mogły dopasować się do
                // odświeżonego katalogu. M3U nie ma numerycznego stream_id jak Xtream, ale
                // URL strumienia jest unikalny w obrębie playlisty i pozostaje identyczny
                // między odświeżeniami (dopóki playlista faktycznie się nie zmieni).
                id = "${playlistId}_${it.streamUrl}",
                playlistId = playlistId,
                kind = MediaKind.LIVE,
                name = it.name,
                logoUrl = it.logoUrl,
                group = it.group,
                streamUrl = it.streamUrl,
                epgChannelId = it.tvgId,
            )
        }
}
