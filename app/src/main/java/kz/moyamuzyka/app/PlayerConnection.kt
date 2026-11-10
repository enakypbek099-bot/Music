package kz.moyamuzyka.app

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/** Связь интерфейса с PlaybackService через MediaController. Всё состояние — Compose-состояние. */
class PlayerConnection(private val ctx: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    var isPlaying by mutableStateOf(false)
    var currentId by mutableStateOf<String?>(null)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { currentId = item?.mediaId; tick() }
        override fun onPlaybackStateChanged(state: Int) { tick() }
        override fun onShuffleModeEnabledChanged(on: Boolean) { shuffle = on }
        override fun onRepeatModeChanged(mode: Int) { repeat = mode }
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(ctx, "Не удалось воспроизвести: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            controller?.let { if (it.hasNextMediaItem()) { it.seekToNextMediaItem(); it.prepare(); it.play() } }
        }
    }

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            isPlaying = c.isPlaying
            currentId = c.currentMediaItem?.mediaId
            shuffle = c.shuffleModeEnabled
            repeat = c.repeatMode
            tick()
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    /** Обновляет позицию и длительность (вызывается по таймеру из интерфейса). */
    fun tick() {
        val c = controller ?: return
        positionMs = c.currentPosition.coerceAtLeast(0L)
        durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
    }

    private fun item(t: Track) = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist.ifBlank { null })
                .setAlbumTitle(t.album.ifBlank { null })
                .setArtworkUri(t.art?.let { Uri.fromFile(File(it)) })
                .build()
        )
        .build()

    /** Запускает трек index из списка list; очередь воспроизведения = показанный список. */
    fun play(list: List<Track>, index: Int) {
        val c = controller ?: run {
            Toast.makeText(ctx, "Плеер ещё запускается, попробуйте снова", Toast.LENGTH_SHORT).show()
            return
        }
        val ids = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        if (ids == list.map { it.id }) {
            c.seekToDefaultPosition(index)
        } else {
            c.setMediaItems(list.map(::item), index, 0L)
        }
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekToDefaultPosition(0) } }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000L || !c.hasPreviousMediaItem()) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms); tick() }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Убирает трек из очереди плеера (при удалении из библиотеки). */
    fun removeFromQueue(id: String) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).mediaId == id) c.removeMediaItem(i)
    }
}
