package kz.moyamuzyka.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.moyamuzyka.app.dsp.DspHub
import kz.moyamuzyka.app.dsp.EqBands

@Composable
fun EqScreen() {
    val ctx = currentCtx()
    val c by DspHub.state.collectAsState()
    var presetMenu by remember { mutableStateOf(false) }
    val freqs = EqBands.freqs(c.eqCount)
    val lockedByBass = c.bass != null

    // отказ включить «Усиление» при активном басе
    fun amp(f: (kz.moyamuzyka.app.dsp.DspConfig) -> kz.moyamuzyka.app.dsp.DspConfig) {
        DspHub.ampChange(f)?.let { toast(ctx, it) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("ЭКВАЛАЙЗЕР", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

        // число полос
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EqBands.COUNTS.forEach { n ->
                FilterChip(
                    selected = c.eqCount == n,
                    onClick = {
                        if (n != c.eqCount) DspHub.update {
                            it.copy(eqCount = n, eqGains = EqBands.resample(it.eqCount, it.eqGains, n), preset = "Свой")
                        }
                    },
                    label = { Text(EqBands.label(n)) }
                )
            }
        }

        // пресеты
        Box(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { presetMenu = true }) {
                Text("Пресет: ${c.preset}")
                Icon(Icons.Rounded.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                EqBands.PRESETS.forEach { (name, _) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        presetMenu = false
                        DspHub.update { it.copy(preset = name, eqGains = EqBands.presetGains(name, it.eqCount)) }
                    })
                }
            }
        }

        SoftCard(Modifier.padding(top = 10.dp)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                LabeledSlider(
                    "Предусиление", fmtDb(c.preDb) + " dB", c.preDb, -12f..12f, 0.5f,
                    onChange = { v -> DspHub.update { it.copy(preDb = v) } },
                    onReset = { DspHub.update { it.copy(preDb = 0f) } }
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    for (i in 0 until c.eqCount) {
                        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(fmtDb(c.eqGains[i]), fontSize = 10.sp, color = Sub)
                            VSlider(
                                value = c.eqGains[i],
                                onChange = { v ->
                                    DspHub.update { cfg ->
                                        val g = cfg.eqGains.toMutableList(); g[i] = v
                                        cfg.copy(eqGains = g, preset = "Свой")
                                    }
                                },
                                modifier = Modifier.height(170.dp).width(48.dp)
                            )
                            Text(EqBands.fmtF(freqs[i]), fontSize = 10.sp, color = Sub, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        SectionTitle("Тон")
        SoftCard {
            Column(Modifier.padding(vertical = 8.dp)) {
                LabeledSlider("Низкие", "${c.toneLo}%", c.toneLo.toFloat(), 0f..100f, 1f,
                    onChange = { v -> DspHub.update { it.copy(toneLo = Math.round(v)) } },
                    onReset = { DspHub.update { it.copy(toneLo = 0) } })
                LabeledSlider("Высокие", "${c.toneHi}%", c.toneHi.toFloat(), 0f..100f, 1f,
                    onChange = { v -> DspHub.update { it.copy(toneHi = Math.round(v)) } },
                    onReset = { DspHub.update { it.copy(toneHi = 0) } })
                ToggleHeader("Лимитер", c.limiter) { on -> DspHub.update { it.copy(limiter = on) } }
            }
        }

        SectionTitle("Эффекты")
        SoftCard(locked = lockedByBass) {
            Column(Modifier.padding(vertical = 6.dp)) {
                ToggleHeader("Усиление", c.gnOn) { on -> amp { it.copy(gnOn = on) } }
                LabeledSlider(
                    "Уровень", "${c.gnPct}% · +${Math.round(c.gnPct / 100f * c.effVolDb)} dB", c.gnPct.toFloat(), 0f..100f, 1f,
                    onChange = { v -> amp { it.copy(gnPct = Math.round(v), gnOn = it.gnOn || v > 0f) } },
                    onReset = { amp { it.copy(gnPct = 0) } }, dim = !c.gnOn
                )
            }
        }
        SoftCard(Modifier.padding(top = 8.dp), locked = lockedByBass) {
            Column(Modifier.padding(vertical = 6.dp)) {
                ToggleHeader("Усиление басов", c.bbOn) { on -> amp { it.copy(bbOn = on) } }
                LabeledSlider(
                    "Уровень", "${c.bbPct}% · +${Math.round(c.bbPct / 100f * c.effBassDb)} dB", c.bbPct.toFloat(), 0f..100f, 1f,
                    onChange = { v -> amp { it.copy(bbPct = Math.round(v), bbOn = it.bbOn || v > 0f) } },
                    onReset = { amp { it.copy(bbPct = 0) } }, dim = !c.bbOn
                )
            }
        }

        SectionTitle("Звук")
        SystemVolumeCard()
        SoftCard(Modifier.padding(top = 8.dp)) {
            val b = c.balance
            LabeledSlider(
                "Аудио баланс", if (b == 0f) "Центр" else (if (b < 0) "L " else "R ") + fmtDb(Math.abs(b)).removePrefix("+"),
                b, -5f..5f, 0.5f,
                onChange = { v -> DspHub.update { it.copy(balance = v) } },
                onReset = { DspHub.update { it.copy(balance = 0f) } }
            )
        }

        SectionTitle("Пределы усиления")
        SoftCard(locked = lockedByBass) {
            Column(Modifier.padding(bottom = 8.dp)) {
                ToggleHeader("Лимиты", c.lmOn) { on -> amp { it.copy(lmOn = on) } }
                LabeledSlider(
                    "Макс. усиление низких частот", "${c.maxBassDb} dB", c.maxBassDb.toFloat(), 0f..15f, 1f,
                    onChange = { v -> amp { it.copy(maxBassDb = Math.round(v), lmOn = true) } },
                    onReset = { amp { it.copy(maxBassDb = 15, lmOn = true) } }, dim = !c.lmOn
                )
                LabeledSlider(
                    "Макс. усиление громкости", "${c.maxVolDb} dB", c.maxVolDb.toFloat(), 0f..20f, 1f,
                    onChange = { v -> amp { it.copy(maxVolDb = Math.round(v), lmOn = true) } },
                    onReset = { amp { it.copy(maxVolDb = 20, lmOn = true) } }, dim = !c.lmOn
                )
                LabeledSlider(
                    "Частота усиления басов", "${c.bassHz} Hz", c.bassHz.toFloat(), 30f..200f, 5f,
                    onChange = { v -> amp { it.copy(bassHz = Math.round(v / 5f) * 5, lmOn = true) } },
                    onReset = { amp { it.copy(bassHz = 80, lmOn = true) } }, dim = !c.lmOn
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { DspHub.reset(); toast(ctx, "Всё сброшено · оригинальный звук") }) { Text("Сбросить всё") }
            Text("Вернуть оригинальный звук", fontSize = 12.5.sp, color = Sub, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
