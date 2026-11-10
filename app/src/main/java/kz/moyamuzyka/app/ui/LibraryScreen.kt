package kz.moyamuzyka.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kz.moyamuzyka.app.Library
import kz.moyamuzyka.app.PlayerConnection
import kz.moyamuzyka.app.Track

@Composable
fun LibraryScreen(conn: PlayerConnection) {
    val ctx = currentCtx()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Track?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) } // "tags" | "info" | "del"
    var target by remember { mutableStateOf<Track?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        scope.launch {
            val n = Library.add(ctx, uris)
            toast(ctx, if (n > 0) "Добавлено: $n" else "Новых треков нет или файлы не читаются")
        }
    }

    val q = query.trim().lowercase()
    val shown = Library.tracks.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
    }

    Screen("МОЯ МУЗЫКА") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Поиск") }, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Rounded.Add, "Добавить музыку", tint = Accent, modifier = Modifier.size(30.dp))
            }
        }
        if (Library.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Библиотека пуста", fontSize = 16.sp, color = Sub)
                    Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Добавить музыку")
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(shown, key = { _, t -> t.id }) { i, t ->
                    val active = conn.currentId == t.id
                    SoftCard {
                        Row(
                            Modifier.fillMaxWidth().clickable { conn.play(shown, i) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(t.art, 52.dp, corner = 12.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (active) Accent else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    t.artist.ifBlank { "Неизвестный исполнитель" }, fontSize = 13.sp, color = Sub,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(fmtTime(t.durationMs), fontSize = 12.sp, color = Sub)
                            Box {
                                IconButton(onClick = { menuFor = t }) { Icon(Icons.Rounded.MoreVert, "Меню") }
                                DropdownMenu(expanded = menuFor?.id == t.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text("Изменить теги") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                        onClick = { target = t; dialog = "tags"; menuFor = null }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Информация") }, leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                        onClick = { target = t; dialog = "info"; menuFor = null }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Убрать из библиотеки") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                        onClick = { target = t; dialog = "del"; menuFor = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val t = target
    if (t != null && dialog == "info") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Закрыть") } },
            title = { Text("Информация") },
            text = {
                Column {
                    listOf(
                        "Название" to t.title, "Исполнитель" to t.artist.ifBlank { "—" }, "Альбом" to t.album.ifBlank { "—" },
                        "Жанр" to t.genre.ifBlank { "—" }, "Формат" to t.fmt, "Длительность" to fmtTime(t.durationMs),
                        "Размер" to "%.1f МБ".format(t.size / 1048576.0)
                    ).forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, color = Sub, fontSize = 14.sp)
                            Text(v, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp), maxLines = 2)
                        }
                    }
                }
            }
        )
    }
    if (t != null && dialog == "del") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = {
                TextButton(onClick = {
                    conn.removeFromQueue(t.id)
                    Library.remove(ctx, t.id)
                    dialog = null
                    toast(ctx, "Убрано. Сам файл на телефоне не удалён")
                }) { Text("Убрать") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
            title = { Text("Убрать трек?") },
            text = { Text(t.title) }
        )
    }
    if (t != null && dialog == "tags") {
        var title by remember(t.id) { mutableStateOf(t.title) }
        var artist by remember(t.id) { mutableStateOf(t.artist) }
        var album by remember(t.id) { mutableStateOf(t.album) }
        var genre by remember(t.id) { mutableStateOf(t.genre) }
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = {
                TextButton(onClick = {
                    Library.editTags(ctx, t.id, title, artist, album, genre)
                    dialog = null
                    toast(ctx, "Теги сохранены")
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
            title = { Text("Теги") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true)
                    OutlinedTextField(album, { album = it }, label = { Text("Альбом") }, singleLine = true)
                    OutlinedTextField(genre, { genre = it }, label = { Text("Жанр") }, singleLine = true)
                }
            }
        )
    }
}
