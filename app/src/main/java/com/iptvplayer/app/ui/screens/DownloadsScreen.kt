package com.iptvplayer.app.ui.screens

import android.app.DownloadManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.DownloadRecord
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame
import kotlinx.coroutines.delay

/** Bieżący status jednego pobrania, odczytany z DownloadManager — DownloadRecord (patrz
 * Models.kt) trzyma tylko TRWAŁE metadane (tytuł/plakat/URL), ten stan trzeba za to
 * odpytywać na bieżąco, bo DownloadManager sam nie powiadamia Compose o postępie. */
private data class DownloadStatusInfo(
    val status: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val localUri: String?,
)

/**
 * Ekran "Pobrane" — lista wszystkiego, co appka kiedykolwiek zleciła systemowemu
 * DownloadManager (patrz DownloadHelper.downloadItem), z bieżącym postępem, możliwością
 * anulowania, ponowienia po błędzie i odtworzenia ukończonych pobrań. Bez tego ekranu
 * jedynym miejscem, gdzie było widać postęp pobierania, był systemowy pasek powiadomień —
 * niewygodne, bo znika po ściągnięciu wielu odcinków naraz.
 */
@Composable
fun DownloadsScreen(
    downloads: List<DownloadRecord>,
    onBack: () -> Unit,
    onRemove: (DownloadRecord) -> Unit,
    onRetry: (DownloadRecord) -> Unit,
    onPlayLocal: (uri: Uri, title: String, posterUrl: String?) -> Unit,
) {
    val context = LocalContext.current
    val downloadManager = remember { context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager }

    // Odpytywanie DownloadManager co 1.5s o bieżący status/postęp wszystkich pozycji naraz —
    // jedno zapytanie z filtrem po wielu ID zamiast osobnego zapytania per wiersz. Zapakowane
    // w runCatching: to system zewnętrzny względem appki, więc lepiej dmuchać na zimne niż
    // wywalić cały ekran przez nieoczekiwany format kursora na jakimś urządzeniu.
    var statuses by remember { mutableStateOf<Map<Long, DownloadStatusInfo>>(emptyMap()) }
    LaunchedEffect(downloads) {
        while (true) {
            if (downloads.isNotEmpty()) {
                runCatching {
                    val ids = downloads.map { it.managerId }.toLongArray()
                    val query = DownloadManager.Query().setFilterById(*ids)
                    val fresh = mutableMapOf<Long, DownloadStatusInfo>()
                    downloadManager.query(query)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                            fresh[id] = DownloadStatusInfo(
                                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                                localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)),
                            )
                        }
                    }
                    statuses = fresh
                }
            }
            delay(1500)
        }
    }

    var pendingDelete by remember { mutableStateOf<DownloadRecord?>(null) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = tr("common.back"), tint = TextMain)
            }
            Spacer(Modifier.width(4.dp))
            Text(tr("downloads.title"), color = TextMain, style = MaterialTheme.typography.titleMedium)
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(tr("downloads.empty"), color = TextDim, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(downloads, key = { it.managerId }) { record ->
                    DownloadRow(
                        record = record,
                        info = statuses[record.managerId],
                        onDelete = { pendingDelete = record },
                        onRetry = { onRetry(record) },
                        onPlay = { uriString ->
                            runCatching { Uri.parse(uriString) }.getOrNull()?.let {
                                onPlayLocal(it, record.title, record.posterUrl)
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("downloads.deleteConfirmTitle")) },
            text = { Text(tr("downloads.deleteConfirmText")) },
            confirmButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(
                    text = tr("common.delete"),
                    color = Danger,
                    onClick = {
                        pendingDelete = null
                        runCatching { downloadManager.remove(toDelete.managerId) }
                        onRemove(toDelete)
                    },
                )
            },
            dismissButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(
                    text = tr("common.cancel"),
                    initialFocus = true,
                    onClick = { pendingDelete = null },
                )
            },
        )
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    info: DownloadStatusInfo?,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (localUri: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface2),
        ) {
            if (!record.posterUrl.isNullOrBlank()) {
                AsyncImage(model = record.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(record.title, color = TextMain, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // Ostrzeżenie "pobranie niemożliwe" (sekcja 8) — status nieudanego pobrania jest
            // klikalny i pokazuje wyjaśnienie, że pobieranie zależy od źródła/urządzenia/
            // systemu, nie od samej aplikacji. Osobny stan per-wiersz (remember wewnątrz
            // DownloadRow), bo każdy wiersz listy ma własny niezależny status.
            var showFailureExplanation by remember { mutableStateOf(false) }
            when (info?.status) {
                DownloadManager.STATUS_SUCCESSFUL -> Text(tr("downloads.status.done"), color = Success, style = MaterialTheme.typography.labelSmall)
                DownloadManager.STATUS_FAILED -> Text(
                    tr("downloads.status.failed"),
                    color = Danger,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .tvFocusFrame(shape = RoundedCornerShape(4.dp))
                        .clickable { showFailureExplanation = true },
                )
                DownloadManager.STATUS_PAUSED -> Text(tr("downloads.status.paused"), color = Warning, style = MaterialTheme.typography.labelSmall)
                DownloadManager.STATUS_PENDING -> Text(tr("downloads.status.pending"), color = TextDim, style = MaterialTheme.typography.labelSmall)
                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (info.bytesTotal > 0) (info.bytesDownloaded.toFloat() / info.bytesTotal.toFloat()) else 0f
                    Text(
                        if (info.bytesTotal > 0) tr("downloads.status.runningPercent", (progress * 100).toInt()) else tr("downloads.status.running"),
                        color = Accent, style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (info.bytesTotal > 0) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Accent,
                            trackColor = Surface2,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Accent, trackColor = Surface2)
                    }
                }
                else -> Text(tr("downloads.status.unknown"), color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
            if (showFailureExplanation) {
                AlertDialog(
                    onDismissRequest = { showFailureExplanation = false },
                    title = { Text(tr("downloads.status.failed")) },
                    text = { Text(tr("downloads.failureExplanation.body")) },
                    confirmButton = {
                        TextButton(onClick = { showFailureExplanation = false }) { Text(tr("common.ok")) }
                    },
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        when (info?.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                IconButton(
                    onClick = { info.localUri?.let(onPlay) },
                    modifier = Modifier.tvFocusFrame(shape = CircleShape),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = tr("grid.quickPlayDesc"), tint = Accent)
                }
            }
            DownloadManager.STATUS_FAILED -> {
                IconButton(onClick = onRetry, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
                    Icon(Icons.Default.Refresh, contentDescription = tr("downloads.retryDesc"), tint = TextMain)
                }
            }
            else -> {}
        }
        IconButton(onClick = onDelete, modifier = Modifier.tvFocusFrame(shape = CircleShape)) {
            Icon(Icons.Default.Delete, contentDescription = tr("common.delete"), tint = Danger)
        }
    }
}
