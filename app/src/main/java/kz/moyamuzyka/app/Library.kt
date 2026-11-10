package kz.moyamuzyka.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Трек библиотеки. uri — документ из системного выбора файлов (доступ сохранён постоянно). */
data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val size: Long,
    val fmt: String,
    val art: String?
)

/** Библиотека: список в памяти + сохранение в SharedPreferences. */
object Library {
    val tracks = mutableStateListOf<Track>()
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val s = ctx.getSharedPreferences("library", Context.MODE_PRIVATE).getString("tracks", null) ?: return
        runCatching {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                tracks.add(
                    Track(
                        o.getString("id"), o.getString("uri"), o.optString("title"), o.optString("artist"),
                        o.optString("album"), o.optString("genre"), o.optLong("dur"), o.optLong("size"),
                        o.optString("fmt"), o.optString("art").ifEmpty { null }
                    )
                )
            }
        }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        tracks.forEach { t ->
            a.put(JSONObject().apply {
                put("id", t.id); put("uri", t.uri); put("title", t.title); put("artist", t.artist)
                put("album", t.album); put("genre", t.genre); put("dur", t.durationMs); put("size", t.size)
                put("fmt", t.fmt); put("art", t.art ?: "")
            })
        }
        ctx.getSharedPreferences("library", Context.MODE_PRIVATE).edit().putString("tracks", a.toString()).apply()
    }

    /** Добавляет выбранные файлы; возвращает, сколько новых треков добавлено. */
    suspend fun add(ctx: Context, uris: List<Uri>): Int {
        val app = ctx.applicationContext
        var added = 0
        for (u in uris) {
            val key = u.toString()
            if (tracks.any { it.uri == key }) continue
            val t = withContext(Dispatchers.IO) { readTrack(app, u) } ?: continue
            withContext(Dispatchers.Main) { tracks.add(t) }
            added++
        }
        if (added > 0) withContext(Dispatchers.Main) { save(app) }
        return added
    }

    fun remove(ctx: Context, id: String) {
        val t = tracks.firstOrNull { it.id == id } ?: return
        tracks.remove(t)
        t.art?.let { runCatching { File(it).delete() } }
        save(ctx.applicationContext)
    }

    fun editTags(ctx: Context, id: String, title: String, artist: String, album: String, genre: String) {
        val i = tracks.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tracks[i]
        tracks[i] = t.copy(title = title.ifBlank { t.title }, artist = artist.trim(), album = album.trim(), genre = genre.trim())
        save(ctx.applicationContext)
    }

    private fun readTrack(ctx: Context, uri: Uri): Track? {
        var name = "Без названия"
        var size = 0L
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0) size = c.getLong(si)
                }
            }
        }
        val ext = name.substringAfterLast('.', "").uppercase()
        val base = name.substringBeforeLast('.', name)
        val id = UUID.randomUUID().toString()
        var title = base; var artist = ""; var album = ""; var genre = ""; var dur = 0L
        var art: String? = null
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(ctx, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            mmr.embeddedPicture?.let { art = saveArt(ctx, id, it) }
        } catch (e: Exception) {
            return null // файл не читается как аудио
        } finally {
            runCatching { mmr.release() }
        }
        return Track(id, uri.toString(), title, artist, album, genre, dur, size, ext.ifEmpty { "AUDIO" }, art)
    }

    /** Обложка сохраняется уменьшенной (до 600 px) для списка и уведомления. */
    private fun saveArt(ctx: Context, id: String, bytes: ByteArray): String? = runCatching {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
        var sample = 1
        while (b.outWidth / sample > 1200) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val k = 600f / maxOf(bmp.width, bmp.height)
        val sc = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val dir = File(ctx.filesDir, "art").apply { mkdirs() }
        val f = File(dir, "$id.jpg")
        f.outputStream().use { sc.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.absolutePath
    }.getOrNull()
}
