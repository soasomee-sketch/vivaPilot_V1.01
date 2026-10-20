package com.iptvplayer.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Skanuje podłączony folder (SAF, content://…/tree/…) w poszukiwaniu plików wideo —
 * rekurencyjnie, żeby znaleźć też pliki w podfolderach (np. "Filmy/Akcja/…"). Nie korzysta
 * z MediaStore celowo: MediaStore indeksuje tylko foldery znane systemowi (DCIM, Movies…),
 * a użytkownik może podłączyć DOWOLNY folder (karta SD, pendrive przez OTG, folder w chmurze
 * zamontowany jako dostawca dokumentów itd.) — SAF działa z każdym z nich jednakowo.
 */
object LocalFolderScanner {

    // Limit głębokości rekursji — zabezpieczenie przed patologicznie zagnieżdżoną strukturą
    // folderów (i przed dostawcami dokumentów, które potrafią zgłaszać cykliczne odnośniki).
    private const val MAX_DEPTH = 8

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
        "ts", "mpg", "mpeg", "3gp", "m2ts", "mts", "vob", "ogv",
    )

    /**
     * Zwraca listę plików wideo znalezionych w folderze. Rzuca wyjątek, jeśli folder jest
     * niedostępny (np. odłączony nośnik albo cofnięte uprawnienie) — wywołujący (patrz
     * AppViewModel.rescanLocalFolders) łapie to per-folder, żeby jeden zepsuty folder nie
     * przerywał skanowania pozostałych.
     */
    fun scanFolder(context: Context, folder: LocalFolder): List<LocalVideoFile> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri))
            ?: throw IllegalStateException("Folder niedostępny")
        if (!root.isDirectory) throw IllegalStateException("To nie jest folder")

        val result = mutableListOf<LocalVideoFile>()
        walk(root, folder.id, depth = 0, out = result)
        return result.sortedBy { it.title.lowercase() }
    }

    private fun walk(dir: DocumentFile, folderId: String, depth: Int, out: MutableList<LocalVideoFile>) {
        if (depth > MAX_DEPTH) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }
        for (child in children) {
            when {
                child.isDirectory -> walk(child, folderId, depth + 1, out)
                isVideoFile(child) -> {
                    val rawName = child.name ?: continue
                    out += LocalVideoFile(
                        id = child.uri.toString(),
                        folderId = folderId,
                        uri = child.uri.toString(),
                        title = rawName.substringBeforeLast('.'),
                        sizeBytes = child.length(),
                    )
                }
            }
        }
    }

    private fun isVideoFile(doc: DocumentFile): Boolean {
        val mime = doc.type
        if (mime != null && mime.startsWith("video/")) return true
        val name = doc.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }
}
