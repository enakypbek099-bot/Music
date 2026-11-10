package kz.moyamuzyka.app.dsp

import org.json.JSONArray
import org.json.JSONObject

/** Режимы баса (как в HTML-версии). */
enum class BassMode(val title: String) {
    INFRA("Инфра бас"),
    CLEAN("Чистый бас"),
    DEEP("Глубокий бас"),
    HARD("Хард бас"),
    DYN("Динамический бас")
}

/** Полное состояние звуковой обработки. Неизменяемое: процессор читает снимок целиком. */
data class DspConfig(
    val eqCount: Int = 10,
    val eqGains: List<Float> = List(10) { 0f },
    val preset: String = "Плоский",
    val preDb: Float = 0f,
    val toneLo: Int = 0,
    val toneHi: Int = 0,
    val limiter: Boolean = false,
    val bass: BassMode? = null,
    val dynDb: Float = 0f,
    val gnOn: Boolean = false,
    val gnPct: Int = 0,
    val bbOn: Boolean = false,
    val bbPct: Int = 0,
    val lmOn: Boolean = false,
    val maxBassDb: Int = 15,
    val maxVolDb: Int = 20,
    val bassHz: Int = 80,
    val balance: Float = 0f
) {
    /** Включено ли что-то из группы «Усиление» (несовместима с «Бас»). */
    val ampActive: Boolean get() = gnOn || bbOn || lmOn

    /** Действующие пределы: если переключатель «Лимиты» выключен — стандартные 15 / 20 / 80. */
    val effBassDb: Int get() = if (lmOn) maxBassDb else 15
    val effVolDb: Int get() = if (lmOn) maxVolDb else 20
    val effBassHz: Int get() = if (lmOn) bassHz else 80

    fun toJson(): String = JSONObject().apply {
        put("eqCount", eqCount)
        put("eqGains", JSONArray(eqGains.map { it.toDouble() }))
        put("preset", preset)
        put("preDb", preDb.toDouble())
        put("toneLo", toneLo); put("toneHi", toneHi)
        put("limiter", limiter)
        put("bass", bass?.name ?: "")
        put("dynDb", dynDb.toDouble())
        put("gnOn", gnOn); put("gnPct", gnPct)
        put("bbOn", bbOn); put("bbPct", bbPct)
        put("lmOn", lmOn)
        put("maxBassDb", maxBassDb); put("maxVolDb", maxVolDb); put("bassHz", bassHz)
        put("balance", balance.toDouble())
    }.toString()

    companion object {
        fun fromJson(s: String): DspConfig {
            val o = JSONObject(s)
            val n = o.optInt("eqCount", 10).let { if (it in EqBands.COUNTS) it else 10 }
            val arr = o.optJSONArray("eqGains")
            val gains = List(n) { i ->
                if (arr != null && i < arr.length()) arr.getDouble(i).toFloat().coerceIn(-EqBands.MAX_DB, EqBands.MAX_DB) else 0f
            }
            val b = o.optString("bass", "")
            return DspConfig(
                eqCount = n,
                eqGains = gains,
                preset = o.optString("preset", "Плоский"),
                preDb = o.optDouble("preDb", 0.0).toFloat(),
                toneLo = o.optInt("toneLo", 0), toneHi = o.optInt("toneHi", 0),
                limiter = o.optBoolean("limiter", false),
                bass = if (b.isEmpty()) null else runCatching { BassMode.valueOf(b) }.getOrNull(),
                dynDb = o.optDouble("dynDb", 0.0).toFloat(),
                gnOn = o.optBoolean("gnOn", false), gnPct = o.optInt("gnPct", 0),
                bbOn = o.optBoolean("bbOn", false), bbPct = o.optInt("bbPct", 0),
                lmOn = o.optBoolean("lmOn", false),
                maxBassDb = o.optInt("maxBassDb", 15), maxVolDb = o.optInt("maxVolDb", 20),
                bassHz = o.optInt("bassHz", 80),
                balance = o.optDouble("balance", 0.0).toFloat()
            )
        }
    }
}
