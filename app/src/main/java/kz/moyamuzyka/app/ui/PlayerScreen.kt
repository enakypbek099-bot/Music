package kz.moyamuzyka.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kz.moyamuzyka.app.Library
import kz.moyamuzyka.app.PlayerConnection

@Composable
fun PlayerScreen(conn: PlayerConnection, onClose: () -> Unit) {
    val t = Library.tracks.firstOrNull { it.id == conn.currentId }
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = conn.durationMs.coerceAtLeast(1L)
    val pos = drag ?: (conn.positionMs.toFloat() / dur)

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", modifier = Modifier.size(32.dp)) }
        }
        Box(Modifier.padding(top = 12.dp)) { Cover(t?.art, 300.dp, corner = 28.dp) }
        Text(
            t?.title ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 28.dp)
        )
        Text(t?.artist?.ifBlank { "Неизвестный исполнитель" } ?: "", fontSize = 15.sp, color = Sub, modifier = Modifier.padding(top = 4.dp))

        Slider(
            value = pos.coerceIn(0f, 1f),
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { conn.seekTo((it * dur).toLong()) }; drag = null },
            modifier = Modifier.padding(top = 24.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(if (drag != null) (drag!! * dur).toLong() else conn.positionMs), fontSize = 12.sp, color = Sub)
            Text(fmtTime(conn.durationMs), fontSize = 12.sp, color = Sub)
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { conn.toggleShuffle() }) {
                Icon(Icons.Rounded.Shuffle, "Перемешать", tint = if (conn.shuffle) Accent else Sub, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = { conn.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Назад", modifier = Modifier.size(38.dp)) }
            IconButton(onClick = { conn.togglePlay() }, modifier = Modifier.size(72.dp).clip(CircleShape).background(Accent)) {
                Icon(
                    if (conn.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (conn.isPlaying) "Пауза" else "Играть", tint = Color.White, modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { conn.next() }) { Icon(Icons.Rounded.SkipNext, "Дальше", modifier = Modifier.size(38.dp)) }
            IconButton(onClick = { conn.cycleRepeat() }) {
                Icon(
                    if (conn.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Повтор",
                    tint = if (conn.repeat == Player.REPEAT_MODE_OFF) Sub else Accent, modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
