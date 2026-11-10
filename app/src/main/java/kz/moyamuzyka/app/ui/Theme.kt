package kz.moyamuzyka.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFFE9EDF3)
val CardBg = Color(0xFFF4F6FA)
val Accent = Color(0xFF5B8CFF)
val Sub = Color(0xFF7C879A)

@Composable
fun MoyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            background = Bg,
            surface = CardBg,
            surfaceVariant = Color(0xFFDDE3EC),
            onSurface = Color(0xFF1F2733),
            onSurfaceVariant = Sub,
            outlineVariant = Color(0xFFC9D1DD)
        ),
        content = content
    )
}
