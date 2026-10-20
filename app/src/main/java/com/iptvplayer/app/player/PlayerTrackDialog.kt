package com.iptvplayer.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.session.MediaButtonReceiver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.iptvplayer.app.data.DownloadHelper
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.data.EpgProgram
import com.iptvplayer.app.data.EpgRepository
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.WatchProgress
import com.iptvplayer.app.ui.theme.Accent
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.theme.Danger
import com.iptvplayer.app.ui.theme.IptvPlayerTheme
import com.iptvplayer.app.ui.tv.notDpadFocusable
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import com.iptvplayer.app.util.isTvDevice
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.roundToInt

// Wydzielone z PlayerActivity.kt — okno "Ścieżki": wybór ścieżki dźwiękowej i napisów,
// synchronizacja dźwięku/napisów z obrazem oraz informacje o strumieniu.

/**
 * Okno wyboru ścieżki dźwięku i napisów — odpowiedź na to, że część filmów/seriali ma
 * więcej niż jedną ścieżkę audio (np. lektor + oryginał + inny język). Napisy mają zawsze
 * doklejoną ręcznie pozycję "Wyłączone" na początku (id -1), niezależnie od tego, czy dany
 * strumień w ogóle ma jakiekolwiek napisy do wyboru.
 */
@Composable
internal fun TrackPickerDialog(
    audioTracks: List<MediaPlayer.TrackDescription>,
    currentAudioTrackId: Int,
    subtitleTracks: List<MediaPlayer.TrackDescription>,
    currentSubtitleTrackId: Int,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    audioDelayMs: Long = 0L,
    subtitleDelayMs: Long = 0L,
    onAudioDelayChange: (Long) -> Unit = {},
    onSubtitleDelayChange: (Long) -> Unit = {},
    streamInfo: StreamInfo? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("player.tracksTitle")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(tr("player.audioTrackLabel"), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                if (audioTracks.isEmpty()) {
                    Text(
                        tr("player.audioTrackSingleOnly"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    audioTracks.forEach { t ->
                        TrackRow(
                            name = t.name?.takeIf { it.isNotBlank() } ?: tr("player.audioTrackFallback", t.id),
                            selected = t.id == currentAudioTrackId,
                            onClick = { onSelectAudio(t.id) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(tr("player.subtitlesLabel"), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                TrackRow(name = tr("player.subtitlesOff"), selected = currentSubtitleTrackId == -1, onClick = { onSelectSubtitle(-1) })
                subtitleTracks.forEach { t ->
                    TrackRow(
                        name = t.name?.takeIf { it.isNotBlank() } ?: tr("player.subtitleFallback", t.id),
                        selected = t.id == currentSubtitleTrackId,
                        onClick = { onSelectSubtitle(t.id) },
                    )
                }

                // --- Synchronizacja z obrazem (krok 100 ms) ---
                Spacer(Modifier.height(16.dp))
                Text(tr("player.syncLabel"), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                DelayRow(label = tr("player.audioDelayLabel"), valueMs = audioDelayMs, onChange = onAudioDelayChange)
                DelayRow(label = tr("player.subtitleDelayLabel"), valueMs = subtitleDelayMs, onChange = onSubtitleDelayChange)

                // --- Informacje o strumieniu ---
                if (streamInfo != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(tr("player.streamInfoLabel"), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    val lines = buildList {
                        if (streamInfo.width > 0 && streamInfo.height > 0) {
                            add(tr("player.streamInfo.resolution", "${streamInfo.width}×${streamInfo.height}"))
                        }
                        streamInfo.codec?.takeIf { it.isNotBlank() }?.let { add(tr("player.streamInfo.codec", it.trim())) }
                        if (streamInfo.fps > 0f) add(tr("player.streamInfo.fps", "%.2f".format(streamInfo.fps)))
                        if (streamInfo.bitrateKbps > 0) {
                            add(tr("player.streamInfo.bitrate", if (streamInfo.bitrateKbps >= 1000) "%.1f Mb/s".format(streamInfo.bitrateKbps / 1000f) else "${streamInfo.bitrateKbps} kb/s"))
                        }
                        add(tr("player.streamInfo.lostFrames", streamInfo.lostFrames.toString()))
                        add(tr("player.streamInfo.buffer", "${streamInfo.networkCachingMs} ms"))
                        add(tr(if (streamInfo.softwareDecoding) "player.streamInfo.decoderSoftware" else "player.streamInfo.decoderHardware"))
                    }
                    lines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            com.iptvplayer.app.ui.tv.TvDialogButton(text = tr("common.close"), onClick = onDismiss)
        },
    )
}

/** Wiersz "Dźwięk: [−] +200 ms [+]" w oknie ścieżek — krok 100 ms, środkowy przycisk
 * zeruje. Duże, fokusowalne przyciski, żeby dało się tym sterować pilotem. */
@Composable
internal fun DelayRow(label: String, valueMs: Long, onChange: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { onChange(valueMs - 100L) },
            modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        TextButton(
            onClick = { onChange(0L) },
            modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
        ) {
            Text(
                (if (valueMs > 0) "+" else "") + "$valueMs ms",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(
            onClick = { onChange(valueMs + 100L) },
            modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
internal fun TrackRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusFrame(shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
