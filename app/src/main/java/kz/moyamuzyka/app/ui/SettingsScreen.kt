package kz.moyamuzyka.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.moyamuzyka.app.dsp.BassMode
import kz.moyamuzyka.app.dsp.DspHub

@Composable
fun SettingsScreen(page: Int, onPage: (Int) -> Unit) {
    if (page == 1) BassScreen(onBack = { onPage(0) }) else SettingsMain(onPage)
}

@Composable
private fun SettingsMain(onPage: (Int) -> Unit) {
    val ctx = currentCtx()
    val c by DspHub.state.collectAsState()
    val blocked = c.bass == null && c.ampActive
    Screen("НАСТРОЙКИ") {
        SoftCard(locked = blocked) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (blocked) toast(ctx, "Сначала отключите «Усиление»") else onPage(1)
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Бас", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(c.bass?.title ?: "Выкл", fontSize = 13.sp, color = if (c.bass != null) Accent else Sub)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Sub)
            }
        }
    }
}

@Composable
private fun BassScreen(onBack: () -> Unit) {
    val ctx = currentCtx()
    val c by DspHub.state.collectAsState()
    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", modifier = Modifier.size(26.dp)) }
            Text("БАС", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BassMode.values().forEach { m ->
                // заблокирован, если включён другой режим или «Усиление»
                val locked = (c.bass != null && c.bass != m) || (c.bass == null && c.ampActive)
                SoftCard(locked = locked) {
                    Column {
                        ToggleHeader(m.title, c.bass == m) { DspHub.toggleBass(m)?.let { toast(ctx, it) } }
                        if (m == BassMode.DYN) {
                            LabeledSlider(
                                "Уровень", fmtDb(c.dynDb) + " dB", c.dynDb, -6f..6f, 0.5f,
                                onChange = { v -> DspHub.update { it.copy(dynDb = v) } },
                                onReset = { DspHub.update { it.copy(dynDb = 0f) } },
                                dim = c.bass != m
                            )
                        }
                    }
                }
            }
        }
    }
}
