package kr.home.gongsiping.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 색은 딱 이것만 쓴다: 종이, 먹, 회색 둘, 라임 하나 */
@Immutable
data class Palette(
    val bg: Color,
    val ink: Color,
    val sub: Color,
    val line: Color,
    val soft: Color,   // 아주 옅은 면 (차트 영역 등)
    val lime: Color = Color(0xFFC6F432),
    val onLime: Color = Color(0xFF141414),
    val dark: Boolean
)

val LightPalette = Palette(
    bg = Color(0xFFF4F1EA), ink = Color(0xFF141414), sub = Color(0xFF77736A),
    line = Color(0xFFD9D4C8), soft = Color(0xFFEAE6DC), dark = false
)
val DarkPalette = Palette(
    bg = Color(0xFF141414), ink = Color(0xFFF1EEE6), sub = Color(0xFF8F8B83),
    line = Color(0xFF2E2D2A), soft = Color(0xFF1E1E1C), dark = true
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Type {
    // 시스템 한글 글꼴(본고딕 계열). Pretendard 를 쓰려면 README 참고.
    private val f = FontFamily.Default
    val mega = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = (-0.04).em, lineHeight = 54.sp)
    val big = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.03).em, lineHeight = 38.sp)
    val title = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.02).em, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    val small = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.06.em)
    val num = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.03).em)
}

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.lime, onPrimary = p.onLime, background = p.bg, surface = p.bg,
        onSurface = p.ink, onBackground = p.ink, surfaceContainerHigh = p.soft
    ) else lightColorScheme(
        primary = p.ink, onPrimary = p.bg, background = p.bg, surface = p.bg,
        onSurface = p.ink, onBackground = p.ink, surfaceContainerHigh = p.bg
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
