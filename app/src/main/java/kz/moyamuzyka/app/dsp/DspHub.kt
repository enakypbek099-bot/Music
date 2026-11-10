package kz.moyamuzyka.app.dsp

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Единый источник настроек звука. UI меняет состояние здесь, а DspProcessor
 * внутри PlaybackService читает его на каждом буфере — один плеер, одна цепочка.
 */
object DspHub {
    private val _state = MutableStateFlow(DspConfig())
    val state: StateFlow<DspConfig> = _state
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("dsp", Context.MODE_PRIVATE)
        prefs = p
        p.getString("cfg", null)?.let { s -> runCatching { _state.value = DspConfig.fromJson(s) } }
    }

    fun current(): DspConfig = _state.value

    private fun commit(c: DspConfig) {
        _state.value = c
        prefs?.edit()?.putString("cfg", c.toJson())?.apply()
    }

    /** Свободное изменение (эквалайзер, тон, баланс и т.п.). */
    fun update(f: (DspConfig) -> DspConfig) = commit(f(_state.value))

    /** Переключение режима баса. Возвращает текст отказа или null, если всё применено. */
    fun toggleBass(mode: BassMode): String? {
        val c = current()
        if (c.bass == mode) { commit(c.copy(bass = null)); return null }
        if (c.bass != null) return "Сначала отключите «${c.bass.title}»"
        if (c.ampActive) return "Сначала отключите «Усиление»"
        commit(c.copy(bass = mode))
        return null
    }

    /**
     * Изменение блока «Усиление»/«Лимиты». Если оно ВКЛЮЧАЕТ усиление при активном басе —
     * возвращает текст отказа и ничего не меняет.
     */
    fun ampChange(f: (DspConfig) -> DspConfig): String? {
        val c = current()
        val n = f(c)
        if (!c.ampActive && n.ampActive && c.bass != null) return "Сначала отключите «Бас»"
        commit(n)
        return null
    }

    /** «Сбросить всё»: оригинальный звук. */
    fun reset() = commit(DspConfig())
}
