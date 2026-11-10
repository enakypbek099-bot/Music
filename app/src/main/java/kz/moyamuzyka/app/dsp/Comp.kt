package kz.moyamuzyka.app.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Компрессор/лимитер с мягким коленом. Работает по уровню, который передаёт вызывающий
 * (для стерео — максимум по каналам), поэтому стереообраз не «плывёт».
 */
class Comp {
    var on = false
        private set
    private var thr = 0.0
    private var knee = 0.0
    private var ratio = 1.0
    private var atk = 0.0
    private var rel = 0.0
    private var env = 0.0

    fun set(sr: Double, thrDb: Double, kneeDb: Double, ratio: Double, atkSec: Double, relSec: Double) {
        thr = thrDb; knee = kneeDb; this.ratio = ratio
        atk = exp(-1.0 / (max(atkSec, 1e-4) * sr))
        rel = exp(-1.0 / (max(relSec, 1e-3) * sr))
        on = true
    }

    fun off() {
        on = false
        env = 0.0
    }

    /** Возвращает линейный множитель громкости для текущего уровня сигнала. */
    fun gain(level: Double): Double {
        val lv = 20.0 * log10(max(level, 1e-9))
        val o = lv - thr
        val out = when {
            knee > 0 && 2 * o < -knee -> lv
            knee > 0 && 2 * abs(o) <= knee -> lv + (1.0 / ratio - 1.0) * (o + knee / 2).pow(2) / (2 * knee)
            o <= 0 -> lv
            else -> thr + o / ratio
        }
        val gr = lv - out
        env = if (gr > env) atk * env + (1 - atk) * gr else rel * env + (1 - rel) * gr
        return if (env < 1e-3) 1.0 else 10.0.pow(-env / 20.0)
    }
}
