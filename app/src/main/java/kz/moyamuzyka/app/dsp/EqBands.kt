package kz.moyamuzyka.app.dsp

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sinh

/** Частоты полос, добротность и пресеты эквалайзера (перенесено из HTML-версии). */
object EqBands {
    const val MAX_DB = 12f
    val COUNTS = listOf(5, 10, 12, 15, 16, 24, 31, 32)

    private val ISO = mapOf(
        5 to listOf(60f, 230f, 910f, 3600f, 14000f),
        10 to listOf(31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f),
        12 to listOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 6000f, 8000f, 12000f, 16000f),
        15 to listOf(25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f, 1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f),
        31 to listOf(
            20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f,
            800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f,
            12500f, 16000f, 20000f
        )
    )

    fun label(n: Int) = if (ISO.containsKey(n) && n != 5 && n != 12) "$n ISO" else "$n"

    fun freqs(n: Int): List<Float> = ISO[n] ?: List(n) { i ->
        val v = 20.0 * 10.0.pow(3.0 * i / (n - 1))
        val p = 10.0.pow(floor(log10(v)) - 1)
        (Math.round(v / p) * p).toFloat()
    }

    /** Добротность так, чтобы полосы перекрывались равномерно при любом их числе. */
    fun qFor(n: Int): Double {
        val bw = max(0.3, 9.97 / (n - 1))
        return 1.0 / (2.0 * sinh(ln(2.0) / 2.0 * bw))
    }

    fun fmtF(f: Float): String =
        if (f < 1000f) (if (f % 1f == 0f) f.toInt().toString() else f.toString())
        else {
            val k = f / 1000f
            (if (k % 1f == 0f) k.toInt().toString() else (Math.round(k * 100f) / 100f).toString()) + "k"
        }

    private fun interp(fs: List<Float>, gs: List<Float>, f: Float): Float {
        val x = log2(f)
        val last = fs.size - 1
        if (x <= log2(fs[0])) return gs[0]
        if (x >= log2(fs[last])) return gs[last]
        var i = 0
        while (log2(fs[i + 1]) < x) i++
        val a = log2(fs[i])
        val b = log2(fs[i + 1])
        return gs[i] + (gs[i + 1] - gs[i]) * (x - a) / (b - a)
    }

    private fun snap(v: Float) = (Math.round(v * 2f) / 2f).coerceIn(-MAX_DB, MAX_DB)

    /** Пересчёт кривой на другое число полос. */
    fun resample(oldN: Int, gains: List<Float>, newN: Int): List<Float> {
        val of = freqs(oldN)
        return freqs(newN).map { snap(interp(of, gains, it)) }
    }

    fun presetGains(name: String, n: Int): List<Float> {
        val g = PRESETS.firstOrNull { it.first == name }?.second ?: return List(n) { 0f }
        val base = ISO.getValue(10)
        return freqs(n).map { snap(interp(base, g, it)) }
    }

    val PRESETS: List<Pair<String, List<Float>>> = listOf(
        "Плоский" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Рок" to listOf(4.5f, 3.5f, 2.5f, 1f, -1f, -1.5f, .5f, 3f, 4f, 4.5f),
        "Поп" to listOf(-1.5f, 2.5f, 4f, 4.5f, 3f, 0f, -1.5f, -1.5f, -1.5f, -1.5f),
        "Джаз" to listOf(3.5f, 2.5f, 1f, 2f, -1.5f, -1.5f, 0f, 1.5f, 2.5f, 3.5f),
        "Классика" to listOf(3f, 2.5f, 2f, 1.5f, -.5f, -.5f, 0f, 2f, 2.5f, 3f),
        "Блюз" to listOf(2.5f, 1.5f, 0f, -1f, -2f, -1f, 0f, 1.5f, 2.5f, 3f),
        "Метал" to listOf(4f, 3f, 0f, -1.5f, -3f, -1f, 2f, 4.5f, 5.5f, 5.5f),
        "Хип-хоп" to listOf(5.5f, 5f, 3.5f, 2.5f, -1f, -1f, 1f, -.5f, 1.5f, 2.5f),
        "Электроника" to listOf(4.5f, 4f, 1.5f, 0f, -2f, 2f, 1f, 1.5f, 4f, 4.5f),
        "Танцевальная" to listOf(5.5f, 6.5f, 4f, 0f, 0f, -2f, -3f, -3f, 0f, 0f),
        "Кантри" to listOf(-1f, .5f, 1.5f, 2.5f, 2f, 1f, 0f, 2f, 3f, 3f),
        "Регги" to listOf(0f, 0f, -.5f, -4.5f, 0f, 4f, 4f, 0f, 0f, 0f),
        "Латино" to listOf(3f, 1.5f, 0f, 0f, -1.5f, -1.5f, -1.5f, 0f, 3.5f, 5f),
        "Акустика" to listOf(4.5f, 4f, 3f, 1f, 1.5f, 1.5f, 3f, 3.5f, 3f, 2f),
        "Вокал" to listOf(-3f, -3f, -2f, 1f, 3.5f, 3.5f, 3f, 1.5f, 0f, -1.5f),
        "Усиление баса" to listOf(6f, 5f, 4f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f),
        "Усиление высоких" to listOf(0f, 0f, 0f, 0f, 0f, 1.5f, 3.5f, 5f, 6f, 7f),
        "Живое выступление" to listOf(-2f, 0f, 2f, 3f, 3.5f, 3.5f, 3f, 2f, 1.5f, 1.5f),
        "Лаунж" to listOf(-2.5f, -1.5f, 0f, 2f, 3f, 2.5f, 1f, 0f, 1.5f, .5f),
        "R&B" to listOf(3f, 6.5f, 5f, 1.5f, -2f, -1f, 2f, 2.5f, 3f, 3.5f),
        "Наушники" to listOf(3f, 5.5f, 2.5f, -1.5f, -1f, 1f, 2.5f, 3.5f, 5f, 6f),
        "Подкаст" to listOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f)
    )
}
