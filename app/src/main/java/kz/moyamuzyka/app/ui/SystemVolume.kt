package kz.moyamuzyka.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** Громкость телефона (поток музыки). Слайдер двигает настоящую системную громкость. */
class SystemVolume(ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max: Int = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    fun get(): Int = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun set(v: Int) = am.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, max), 0)
}

@Composable
fun SystemVolumeCard() {
    val ctx = currentCtx()
    val sv = remember { SystemVolume(ctx) }
    var vol by remember { mutableIntStateOf(sv.get()) }

    // кнопки громкости на корпусе обновляют слайдер
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { vol = sv.get() }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    // запасной опрос на случай, если система не прислала событие
    LaunchedEffect(Unit) { while (true) { delay(700); vol = sv.get() } }

    SoftCard {
        LabeledSlider(
            label = "Громкость телефона",
            valueText = "${vol * 100 / sv.max}%",
            value = vol.toFloat(), range = 0f..sv.max.toFloat(), step = 1f,
            onChange = { v -> val n = Math.round(v); vol = n; sv.set(n) }
        )
    }
}
