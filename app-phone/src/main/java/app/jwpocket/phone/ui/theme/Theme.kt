package app.jwpocket.phone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Pocket {
    val Background = Color(0xFF0B0C10)
    val Surface = Color(0xFF15171D)
    val SurfaceHigh = Color(0xFF1E2129)
    val SurfaceHighest = Color(0xFF282C36)
    val Accent = Color(0xFFFF3D5A)
    val AccentSoft = Color(0x33FF3D5A)
    val Success = Color(0xFF3DDC97)
    val TextPrimary = Color(0xFFF4F5F7)
    val TextSecondary = Color(0xFFA3A8B4)
    val TextMuted = Color(0xFF6E7380)
    val Scrim = Color(0xCC000000)
}

private val colors = darkColorScheme(
    primary = Pocket.Accent,
    onPrimary = Color.White,
    primaryContainer = Pocket.AccentSoft,
    onPrimaryContainer = Pocket.TextPrimary,
    secondary = Pocket.TextSecondary,
    background = Pocket.Background,
    onBackground = Pocket.TextPrimary,
    surface = Pocket.Background,
    onSurface = Pocket.TextPrimary,
    surfaceVariant = Pocket.SurfaceHigh,
    onSurfaceVariant = Pocket.TextSecondary,
    surfaceContainer = Pocket.Surface,
    surfaceContainerLow = Pocket.Surface,
    surfaceContainerHigh = Pocket.SurfaceHigh,
    surfaceContainerHighest = Pocket.SurfaceHighest,
    surfaceContainerLowest = Pocket.Background,
    secondaryContainer = Pocket.SurfaceHighest,
    onSecondaryContainer = Pocket.TextPrimary,
    outline = Color(0xFF3A3F4B),
    outlineVariant = Color(0xFF262A33),
    error = Color(0xFFFF6B6B),
)

private val base = Typography()
private val typography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
