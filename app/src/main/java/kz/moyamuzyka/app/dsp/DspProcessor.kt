package kz.moyamuzyka.app.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Параметры режима баса: полка, три колокола, компрессор, синус-саб и подрезка (из HTML-версии). */
private class BassParams(
    val shF: Double, val shG: Double,
    val p1: DoubleArray, val p2: DoubleArray, val p3: DoubleArray,
    val cp: DoubleArray?,          // порог, колено, ratio, attack, release
    val osF: Double = 0.0, val osA: Double = 0.0, val osRel: Double = 0.0,
    val tr: Double
)

private fun bassParams(c: DspConfig): BassParams? = when (c.bass) {
    null -> null
    BassMode.INFRA -> BassParams(45.0, 9.0, d(100, 1, -2.5), d(60, 1, 0), d(140, 1, 0), null, 30.0, .5, .12, -3.0)
    BassMode.CLEAN -> BassParams(40.0, -2.0, d(65, 1.1, 4), d(105, 1, 2.5), d(230, 1, -3), null, 0.0, 0.0, 0.0, -2.5)
    BassMode.DEEP -> BassParams(90.0, 6.0, d(55, 1.3, 5), d(140, .8, 1.5), d(140, 1, 0), doubleArrayOf(-26.0, 12.0, 3.0, .02, .3), 48.0, .45, .35, -4.0)
    BassMode.HARD -> BassParams(80.0, 8.0, d(55, 1.2, 7), d(90, 1, 4), d(140, 1, 2), doubleArrayOf(-22.0, 6.0, 4.0, .01, .12), 45.0, .55, .15, -6.0)
    BassMode.DYN -> {
        val dd = c.dynDb.toDouble()
        BassParams(
            120.0, 4.0 + dd, d(80, .8, 2.0 + dd * .5), d(140, 1, 0), d(140, 1, 0),
            doubleArrayOf(-24.0, 12.0, 2.5, .012, .16), 0.0, 0.0, 0.0, -(max(0.0, 4.0 + dd) * .5 + 1.0)
        )
    }
}

private fun d(f: Number, q: Number, g: Number) = doubleArrayOf(f.toDouble(), q.toDouble(), g.toDouble())
private fun db2g(db: Double) = 10.0.pow(db / 20.0)

/**
 * Единая цепочка DSP внутри ExoPlayer (PCM 16 бит и float, до 8 каналов):
 * preamp → тон → бас → 32-полосный эквалайзер → усиление → баланс → лимитер.
 * Настройки читаются из DspHub; пересчёт коэффициентов — только при смене снимка.
 */
@UnstableApi
class DspProcessor : BaseAudioProcessor() {
    private var sr = 44100.0
    private var chs = 2
    private var isFloat = false
    private var applied: DspConfig? = null

    private var eqAll: Array<Biquad> = emptyArray()
    private var eqAct: Array<Biquad> = emptyArray()
    private var tLo = Biquad(1)
    private var tHi = Biquad(1)
    private var bHp = Biquad(1)
    private var bSh = Biquad(1)
    private var bP1 = Biquad(1)
    private var bP2 = Biquad(1)
    private var bP3 = Biquad(1)
    private var aSh = Biquad(1)
    private var aPk = Biquad(1)
    private var envLp = Biquad(1)
    private val bComp = Comp()
    private val bLim = Comp()
    private val fLim = Comp()
    private var fr = DoubleArray(2)

    // параметры текущего снимка
    private var preG = 1.0
    private var bassOn = false
    private var isDyn = false
    private var trimG = 1.0
    private var gnG = 1.0
    private var gL = 1.0
    private var gR = 1.0
    private var shF = 80.0
    private var shBase = 0.0
    private var hasOsc = false
    private var osF = 0.0
    private var osA = 0.0
    private var osRel = 0.1

    // состояние огибающей баса, синус-саба и динамического баса
    private var msq = 0.0
    private var pk = 0.0
    private var dynAvg = 0.0
    private var dynGain = 0.0
    private var oscG = 0.0
    private var phase = 0.0
    private var dynCnt = 0
    private var kMs = 0.0
    private var pkD = 0.0
    private var kDyn = 0.0
    private var kOscAtk = 0.0
    private var kOscRel = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount !in 1..8) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sr = inputAudioFormat.sampleRate.toDouble()
        chs = inputAudioFormat.channelCount
        isFloat = enc == C.ENCODING_PCM_FLOAT
        eqAll = Array(32) { Biquad(chs) }
        eqAct = emptyArray()
        tLo = Biquad(chs); tHi = Biquad(chs)
        bHp = Biquad(chs); bSh = Biquad(chs); bP1 = Biquad(chs); bP2 = Biquad(chs); bP3 = Biquad(chs)
        aSh = Biquad(chs); aPk = Biquad(chs)
        envLp = Biquad(1)
        fr = DoubleArray(chs)
        msq = 0.0; pk = 0.0; dynAvg = 0.0; oscG = 0.0; phase = 0.0; dynCnt = 0
        kMs = 1.0 - exp(-1.0 / (0.0116 * sr))
        pkD = 0.9995.pow(60.0 / sr)
        kDyn = 1.0 - 0.97.pow(60.0 / sr)
        kOscAtk = 1.0 - exp(-1.0 / (0.012 * sr))
        applied = null
        return inputAudioFormat
    }

    /** Пересчёт всех коэффициентов под новый снимок настроек. */
    private fun rebuild(c: DspConfig) {
        val n = c.eqCount
        val f = EqBands.freqs(n)
        val q = EqBands.qFor(n)
        val act = ArrayList<Biquad>()
        for (i in 0 until 32) {
            val b = eqAll[i]
            val g = if (i < n) c.eqGains[i] else 0f
            if (i < n && abs(g) > 0.01f) {
                b.peaking(sr, f[i].toDouble(), q, g.toDouble()); act.add(b)
            } else b.identity()
        }
        eqAct = act.toTypedArray()
        preG = db2g(c.preDb.toDouble())

        val lo = c.toneLo / 100.0 * 12.0
        val hi = c.toneHi / 100.0 * 12.0
        if (abs(lo) > 0.01) tLo.lowShelf(sr, 120.0, lo) else tLo.identity()
        if (abs(hi) > 0.01) tHi.highShelf(sr, 8000.0, hi) else tHi.identity()

        val bp = bassParams(c)
        bassOn = bp != null
        isDyn = c.bass == BassMode.DYN
        if (bp != null) {
            bHp.highPass(sr, 18.0, .7)
            shF = bp.shF; shBase = bp.shG
            bSh.lowShelf(sr, bp.shF, bp.shG)
            dynGain = bp.shG
            fun setPk(b: Biquad, a: DoubleArray) { if (abs(a[2]) > 0.01) b.peaking(sr, a[0], a[1], a[2]) else b.identity() }
            setPk(bP1, bp.p1); setPk(bP2, bp.p2); setPk(bP3, bp.p3)
            val cp = bp.cp
            if (cp != null) bComp.set(sr, cp[0], cp[1], cp[2], cp[3], cp[4]) else bComp.off()
            bLim.set(sr, -2.0, 2.0, 20.0, .003, .08)
            trimG = db2g(bp.tr)
            hasOsc = bp.osA > 0.0
            osF = bp.osF; osA = bp.osA; osRel = bp.osRel
            kOscRel = 1.0 - exp(-1.0 / (max(osRel, 0.01) * sr))
            envLp.lowPass(sr, 120.0, .7)
        } else {
            bHp.identity(); bSh.identity(); bP1.identity(); bP2.identity(); bP3.identity()
            bComp.off(); bLim.off(); trimG = 1.0; hasOsc = false; oscG = 0.0
        }

        // «Усиление»: усиление басов (полка + колокол) и общее усиление, пределы — из «Лимитов»
        val bd = if (c.bbOn) c.bbPct / 100.0 * c.effBassDb else 0.0
        val bf = c.effBassHz.toDouble()
        if (abs(bd) > 0.01) {
            aSh.lowShelf(sr, min(400.0, bf * 1.8), bd)
            aPk.peaking(sr, bf, .9, bd * .6)
        } else { aSh.identity(); aPk.identity() }
        gnG = db2g(if (c.gnOn) c.gnPct / 100.0 * c.effVolDb else 0.0)

        // баланс: -5 (всё влево) … +5 (всё вправо)
        val pan = (c.balance / 5.0).coerceIn(-1.0, 1.0)
        gL = if (pan > 0) 1.0 - pan else 1.0
        gR = if (pan < 0) 1.0 + pan else 1.0

        // финальный защитный лимитер: при включённом «Лимитере» работает жёстче
        if (c.limiter) fLim.set(sr, -6.0, 0.0, 20.0, .003, .12) else fLim.set(sr, -1.5, 0.0, 20.0, .004, .18)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val c = DspHub.current()
        if (c !== applied) { rebuild(c); applied = c }

        val bytes = if (isFloat) 4 else 2
        val frames = inputBuffer.remaining() / (bytes * chs)
        val out = replaceOutputBuffer(frames * bytes * chs)
        for (i in 0 until frames) {
            for (ch in 0 until chs) {
                fr[ch] = if (isFloat) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
            }
            processFrame()
            for (ch in 0 until chs) {
                val v = fr[ch].coerceIn(-1.0, 1.0)
                if (isFloat) out.putFloat(v.toFloat()) else out.putShort(Math.round(v * 32767.0).toInt().toShort())
            }
        }
        out.flip()
    }

    private fun processFrame() {
        val x = fr
        var mono = 0.0
        // preamp и тон (до баса)
        for (ch in 0 until chs) {
            var v = x[ch] * preG
            if (tLo.active) v = tLo.process(ch, v)
            if (tHi.active) v = tHi.process(ch, v)
            x[ch] = v
            mono += v
        }
        mono /= chs

        if (bassOn) {
            // огибающая НЧ-составляющей — для синус-саба и динамического баса
            var r = 0.0
            if (isDyn || hasOsc) {
                val lp = envLp.process(0, mono)
                msq += (lp * lp - msq) * kMs
                r = sqrt(msq)
                pk = max(r, pk * pkD)
            }
            if (isDyn) {
                dynAvg += (r - dynAvg) * kDyn
                if (++dynCnt >= 64) {
                    dynCnt = 0
                    val q = if (dynAvg > .004) (r / dynAvg).coerceIn(.7, 1.4) else 1.0
                    val target = shBase + (q - 1.0) * 3.5
                    dynGain += (target - dynGain) * (1.0 - exp(-64.0 / (0.05 * sr)))
                    bSh.lowShelf(sr, shF, dynGain)
                }
            }
            for (ch in 0 until chs) {
                var v = bHp.process(ch, x[ch])
                v = bSh.process(ch, v)
                if (bP1.active) v = bP1.process(ch, v)
                if (bP2.active) v = bP2.process(ch, v)
                if (bP3.active) v = bP3.process(ch, v)
                x[ch] = v
            }
            if (bComp.on) {
                var lvl = 0.0
                for (ch in 0 until chs) lvl = max(lvl, abs(x[ch]))
                val g = bComp.gain(lvl)
                for (ch in 0 until chs) x[ch] *= g
            }
            var lvl2 = 0.0
            for (ch in 0 until chs) lvl2 = max(lvl2, abs(x[ch]))
            val g2 = bLim.gain(lvl2)
            var osc = 0.0
            if (hasOsc) {
                val n = if (pk > .01) r / pk else 0.0
                val tg = osA * n * n
                oscG += (tg - oscG) * (if (tg > oscG) kOscAtk else kOscRel)
                phase += 2.0 * PI * osF / sr
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                osc = sin(phase) * oscG
            }
            for (ch in 0 until chs) x[ch] = x[ch] * g2 * trimG + osc
        }

        // эквалайзер, усиление, баланс
        for (ch in 0 until chs) {
            var v = x[ch]
            for (b in eqAct) v = b.process(ch, v)
            if (aSh.active) v = aSh.process(ch, v)
            if (aPk.active) v = aPk.process(ch, v)
            x[ch] = v * gnG
        }
        if (chs >= 2) { x[0] *= gL; x[1] *= gR }

        // защитный лимитер (стерео-связанный)
        var lvl = 0.0
        for (ch in 0 until chs) lvl = max(lvl, abs(x[ch]))
        val g = fLim.gain(lvl)
        for (ch in 0 until chs) x[ch] *= g
    }
}
