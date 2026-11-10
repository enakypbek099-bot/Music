package kz.moyamuzyka.app.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.moyamuzyka.app.dsp.EqBands

private var lastToast = ""
private var lastToastAt = 0L

/** Короткое сообщение; одинаковый текст не повторяется чаще раза в 1,5 с (слайдер шлёт события очень часто). */
fun toast(ctx: android.content.Context, msg: String) {
    val now = System.currentTimeMillis()
    if (msg == lastToast && now - lastToastAt < 1500) return
    lastToast = msg; lastToastAt = now
    Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun fmtDb(v: Float): String = (if (v > 0) "+" else "") + (if (v % 1f == 0f) v.toInt().toString() else v.toString())

@Composable
fun SoftCard(modifier: Modifier = Modifier, locked: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().alpha(if (locked) 0.45f else 1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) { content() }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Sub,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)
    )
}

/** Строка с переключателем. Нажатие на заблокированную строку тоже доходит до onToggle (там показывается причина). */
@Composable
fun ToggleHeader(title: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(on) { detectTapGestures { onToggle(!on) } },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
fun LabeledSlider(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float,
    onChange: (Float) -> Unit, onReset: (() -> Unit)? = null, dim: Boolean = false
) {
    val steps = (((range.endInclusive - range.start) / step).toInt() - 1).coerceAtLeast(0)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp).alpha(if (dim) 0.5f else 1f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text(
                valueText, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold,
                modifier = if (onReset != null) Modifier.pointerInput(Unit) { detectTapGestures { onReset() } } else Modifier
            )
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Обложка трека; если картинки нет — заглушка с нотой. Декодирование — в фоне. */
@Composable
fun Cover(path: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(corner)
    val b = bmp
    if (b != null) {
        Image(b, null, modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(Color(0xFF7FA2FF), Color(0xFF5B8CFF)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(size / 2)) }
    }
}

private fun yToDb(y: Float, h: Float, max: Float): Float {
    val f = 1f - (y / h).coerceIn(0f, 1f)
    return (Math.round((f * 2f - 1f) * max * 2f) / 2f).coerceIn(-max, max)
}

/** Вертикальный слайдер полосы эквалайзера: касание/перетаскивание задаёт дБ, двойное касание — 0. */
@Composable
fun VSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, maxDb: Float = EqBands.MAX_DB) {
    val cb by rememberUpdatedState(onChange)
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { cb(yToDb(it.y, size.height.toFloat(), maxDb)) }, onDoubleTap = { cb(0f) })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { cb(yToDb(it.y, size.height.toFloat(), maxDb)) },
                    onVerticalDrag = { ch, _ -> ch.consume(); cb(yToDb(ch.position.y, size.height.toFloat(), maxDb)) }
                )
            }
    ) {
        val cx = size.width / 2f
        val h = size.height
        val mid = h / 2f
        val y = h * (1f - (value + maxDb) / (2f * maxDb))
        drawLine(track, Offset(cx, 0f), Offset(cx, h), strokeWidth = 8f, cap = StrokeCap.Round)
        drawLine(Accent, Offset(cx, mid), Offset(cx, y), strokeWidth = 8f, cap = StrokeCap.Round)
        drawLine(track, Offset(cx - 16f, mid), Offset(cx + 16f, mid), strokeWidth = 2f)
        drawCircle(Color.White, 15f, Offset(cx, y))
        drawCircle(Accent, 15f, Offset(cx, y), style = Stroke(width = 4f))
    }
}

@Composable
fun Screen(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        content()
    }
}

@Composable
fun currentCtx() = LocalContext.current
