package com.iptvplayer.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Wyciąga pojedynczą klatkę z lokalnego pliku wideo i zapisuje ją jako JPEG w cache appki —
 * pliki lokalne (w przeciwieństwie do VOD z Xtream) nie mają ŻADNYCH metadanych plakatu, więc
 * to jedyny sposób na pokazanie czegokolwiek innego niż generyczna ikonka (zgłoszenie
 * użytkownika: brak grafik na liście "Moje" i podczas odtwarzania).
 *
 * Celowo NIE dokładnie klatka 0:00 — w wielu plikach pierwsza klatka jest czarna/pusta
 * (czarne fade-in, karta tytułowa itp.), więc wycinamy z niewielkim przesunięciem w głąb
 * filmu (patrz FRAME_OFFSET_US). To nie jest "inteligentne" wykrywanie czarnej klatki (zbyt
 * ryzykowne/kosztowne jak na tę skalę), tylko prosty, rozsądny kompromis.
 */
object LocalVideoThumbnailer {

    // 3 sekundy w głąb filmu — na tyle blisko początku, żeby to wciąż był "pierwszy wygląd"
    // filmu, na tyle daleko, żeby ominąć typowe czarne klatki startowe/logo dystrybutora.
    private const val FRAME_OFFSET_US = 3_000_000L
    private const val THUMB_MAX_WIDTH = 480

    /**
     * Zwraca ścieżkę do już wyciągniętej (i zbuforowanej na dysku) miniatury, wyciągając ją
     * NAJPIERW, jeśli jeszcze nie istnieje. Wołane z tła (patrz AppViewModel) — to blokująca,
     * potencjalnie wolna operacja (otwarcie i sparsowanie pliku wideo), nigdy z wątku UI.
     * Zwraca null przy JAKIMKOLWIEK błędzie (uszkodzony plik, nieobsługiwany kodek, brak
     * uprawnień do URI itd.) — brak miniatury nie może w żaden sposób zepsuć reszty appki,
     * to czysto kosmetyczne wzbogacenie.
     */
    fun extractOrGetCached(context: Context, video: LocalVideoFile): String? {
        val cacheFile = cacheFileFor(context, video.id)
        // "file://" z przodu CELOWO — Coil (biblioteka ładująca obrazy w całej appce)
        // rozpoznaje surowe ścieżki plików niejednoznacznie bez jawnego schematu URI, a to
        // jest jedyne miejsce, gdzie ta ścieżka powstaje, więc lepiej ustandaryzować ją tu
        // raz, niż pamiętać o owijaniu jej w "file://" w KAŻDYM miejscu, gdzie jest używana.
        if (cacheFile.exists() && cacheFile.length() > 0) return "file://${cacheFile.absolutePath}"
        return runCatching { extract(context, video, cacheFile) }.getOrNull()
    }

    private fun extract(context: Context, video: LocalVideoFile, cacheFile: File): String? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(video.uri))
            // OPTION_CLOSEST_SYNC zamiast domyślnego (OPTION_CLOSEST) — szuka najbliższej
            // klatki kluczowej BLISKO żądanego czasu, szybsze i wystarczająco dokładne jak
            // na miniaturę (nie potrzeba klatki co do mikrosekundy).
            val frame = retriever.getFrameAtTime(FRAME_OFFSET_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                // Krótkie filmy (< 3s) albo pliki, gdzie ten konkretny offset zawiódł —
                // spróbuj po prostu najwcześniejszej klatki, jaką silnik potrafi dać.
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val scaled = scaleDown(frame)
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (scaled !== frame) frame.recycle()
            scaled.recycle()
            return "file://${cacheFile.absolutePath}"
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= THUMB_MAX_WIDTH) return bitmap
        val ratio = THUMB_MAX_WIDTH.toFloat() / bitmap.width
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, THUMB_MAX_WIDTH, targetHeight, true)
    }

    /** Nazwa pliku cache pochodzi z hasha URI (nie samego URI wprost) — URI potrafi zawierać
     * znaki niedozwolone w nazwach plików na niektórych systemach plików. */
    private fun cacheFileFor(context: Context, videoId: String): File {
        val hash = MessageDigest.getInstance("MD5").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "local_thumbs"), "$hash.jpg")
    }
}
