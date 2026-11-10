package kz.moyamuzyka.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Биквад-фильтр (транспонированная форма II) с отдельным состоянием на каждый канал. */
class Biquad(channels: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    /** false — фильтр прозрачный, его можно пропускать. */
    var active = false
        private set

    fun process(ch: Int, x: Double): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun identity() {
        b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
        active = false
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0
        a1 = na1 / a0; a2 = na2 / a0
        active = true
    }

    private fun w0(sr: Double, f: Double) = 2.0 * PI * min(f, sr * 0.45) / sr

    /** Пиковый (колокол) фильтр: частота f, добротность q, усиление db. */
    fun peaking(sr: Double, f: Double, q: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = w0(sr, f)
        val al = sin(w) / (2.0 * q)
        val c = cos(w)
        set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }

    /** Нижняя полка (наклон S = 1). */
    fun lowShelf(sr: Double, f: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = w0(sr, f)
        val c = cos(w)
        val al = sin(w) / 2.0 * sqrt(2.0)
        val sa = 2.0 * sqrt(a) * al
        set(
            a * ((a + 1) - (a - 1) * c + sa),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - sa
        )
    }

    /** Верхняя полка (наклон S = 1). */
    fun highShelf(sr: Double, f: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = w0(sr, f)
        val c = cos(w)
        val al = sin(w) / 2.0 * sqrt(2.0)
        val sa = 2.0 * sqrt(a) * al
        set(
            a * ((a + 1) + (a - 1) * c + sa),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - sa
        )
    }

    fun highPass(sr: Double, f: Double, q: Double) {
        val w = w0(sr, f)
        val c = cos(w)
        val al = sin(w) / (2.0 * q)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
    }

    fun lowPass(sr: Double, f: Double, q: Double) {
        val w = w0(sr, f)
        val c = cos(w)
        val al = sin(w) / (2.0 * q)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }
}
