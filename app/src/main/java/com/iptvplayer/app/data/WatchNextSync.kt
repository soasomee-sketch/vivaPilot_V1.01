package com.iptvplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BaseColumns
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.iptvplayer.app.MainActivity
import com.iptvplayer.app.util.isTvDevice

/**
 * "Kontynuuj oglądanie" na EKRANIE GŁÓWNYM Android TV / Google TV (systemowy rząd
 * "Watch Next" / "Kontynuuj oglądanie"), jeszcze przed otwarciem appki — większość
 * odtwarzaczy IPTV tego nie ma, a to najwygodniejszy sposób powrotu do przerwanego filmu
 * albo odcinka prosto z pilota.
 *
 * Działa jako "odbicie" appkowej listy Kontynuuj oglądanie (LocalStore.getWatchProgress):
 * - filmy i odcinki seriali (bez kanałów na żywo — tam nie ma czego "kontynuować"),
 * - maks. 10 najświeższych, bez obejrzanych prawie do końca,
 * - pozycje usunięte z listy w appce znikają też z ekranu głównego,
 * - pozycja, którą użytkownik sam usunął z ekranu głównego TV, nie wraca, dopóki nie
 *   obejrzy jej ponownie.
 *
 * Wybranie kafelka na ekranie głównym otwiera appkę z EXTRA_RESUME_ID i od razu wznawia
 * odtwarzanie (patrz MainActivity.handleResumeIntent). Na telefonach nic nie robi.
 * Wołać z wątku w tle (zapytania do systemowej bazy programów TV).
 */
object WatchNextSync {
    const val EXTRA_RESUME_ID = "com.iptvplayer.app.RESUME_ID"
    private const val MAX_ITEMS = 10

    private val PROJECTION = arrayOf(
        BaseColumns._ID,
        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE,
        TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
    )

    private data class ExistingRow(val rowId: Long, val browsable: Boolean, val lastEngagementMs: Long)

    fun sync(context: Context, progress: Map<String, WatchProgress>) {
        if (!context.isTvDevice()) return
        runCatching { doSync(context.applicationContext, progress) }
            .onFailure { CrashLogger.breadcrumb("watchNext", "sync failed: ${it.message}") }
    }

    private fun doSync(context: Context, progress: Map<String, WatchProgress>) {
        val resolver = context.contentResolver
        val wanted = progress.forContinueWatching()
            .asSequence()
            .filter { it.kind != MediaKind.LIVE && it.streamUrl.isNotBlank() }
            .filterNot { it.durationMillis > 0 && it.positionMillis >= it.durationMillis * 95 / 100 }
            .take(MAX_ITEMS)
            .toList()
        val wantedIds = wanted.map { it.channelOrEpisodeId }.toSet()

        // Obecne wpisy TEJ appki w rzędzie Watch Next (system zwraca tylko nasze własne).
        val existing = mutableMapOf<String, ExistingRow>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val internalId = c.getString(1) ?: continue
                existing[internalId] = ExistingRow(
                    rowId = c.getLong(0),
                    browsable = c.getInt(2) != 0,
                    lastEngagementMs = c.getLong(3),
                )
            }
        }

        // Usunięcie tego, czego już nie ma w appkowym "Kontynuuj oglądanie".
        existing.filterKeys { it !in wantedIds }.values.forEach { row ->
            resolver.delete(TvContractCompat.buildWatchNextProgramUri(row.rowId), null, null)
        }

        for (item in wanted) {
            val id = item.channelOrEpisodeId
            val row = existing[id]
            if (row != null && !row.browsable) {
                // Użytkownik sam usunął tę pozycję z ekranu głównego — wraca tylko, jeśli od
                // tamtej pory faktycznie oglądał ją dalej.
                if (item.updatedAt <= row.lastEngagementMs + 60_000L) continue
                resolver.delete(TvContractCompat.buildWatchNextProgramUri(row.rowId), null, null)
            }
            val values = buildProgram(context, item).toContentValues()
            if (row != null && row.browsable) {
                resolver.update(TvContractCompat.buildWatchNextProgramUri(row.rowId), values, null, null)
            } else {
                val uri = resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
                if (uri != null && uri != Uri.EMPTY) runCatching { ContentUris.parseId(uri) }
            }
        }
    }

    private fun buildProgram(context: Context, item: WatchProgress): WatchNextProgram {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_RESUME_ID, item.channelOrEpisodeId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val builder = WatchNextProgram.Builder()
        builder
            .setType(
                if (item.kind == MediaKind.SERIES) TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                else TvContractCompat.PreviewPrograms.TYPE_MOVIE,
            )
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setLastEngagementTimeUtcMillis(item.updatedAt)
            .setTitle(item.title.ifBlank { "VivaPilot" })
            .setInternalProviderId(item.channelOrEpisodeId)
            .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
        item.posterUrl?.takeIf { it.isNotBlank() }?.let { builder.setPosterArtUri(Uri.parse(it)) }
        // Pasek postępu na kafelku — system przyjmuje wartości w int (ms), stąd ograniczenie.
        if (item.durationMillis > 0) {
            builder.setDurationMillis(item.durationMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            builder.setLastPlaybackPositionMillis(item.positionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return builder.build()
    }
}
