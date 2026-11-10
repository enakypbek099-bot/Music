package kz.moyamuzyka.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kz.moyamuzyka.app.Library
import kz.moyamuzyka.app.PlayerConnection

@Composable
fun AppRoot(conn: PlayerConnection) {
    var tab by remember { mutableIntStateOf(0) }
    var settingsPage by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && tab == 2 && settingsPage != 0) { settingsPage = 0 }

    // позиция воспроизведения обновляется по таймеру
    LaunchedEffect(Unit) { while (true) { conn.tick(); delay(300) } }

    val cur = Library.tracks.firstOrNull { it.id == conn.currentId }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            Column {
                if (cur != null) {
                    Row(
                        Modifier.fillMaxWidth().background(CardBg).clickable { showPlayer = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(cur.art, 44.dp, corner = 10.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(cur.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cur.artist.ifBlank { "Неизвестный исполнитель" }, fontSize = 12.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { conn.togglePlay() }) {
                            Icon(if (conn.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (conn.isPlaying) "Пауза" else "Играть", modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { conn.next() }) { Icon(Icons.Rounded.SkipNext, "Дальше", modifier = Modifier.size(30.dp)) }
                    }
                }
                NavigationBar(containerColor = CardBg) {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Rounded.LibraryMusic, null) }, label = { Text("Библиотека") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Rounded.GraphicEq, null) }, label = { Text("Эквалайзер") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Rounded.Settings, null) }, label = { Text("Настройки") })
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().background(Bg).padding(pad)) {
            when (tab) {
                0 -> LibraryScreen(conn)
                1 -> EqScreen()
                else -> SettingsScreen(settingsPage) { settingsPage = it }
            }
        }
    }

    if (showPlayer) {
        Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            PlayerScreen(conn) { showPlayer = false }
        }
    }
}
