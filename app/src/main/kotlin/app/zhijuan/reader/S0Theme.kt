package app.zhijuan.reader

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

internal val ReaderPaperLight = Color(0xFFEEECDF)

@Composable
internal fun readerUsesDarkTheme(theme: S3ReaderTheme): Boolean = when (theme) {
    S3ReaderTheme.SYSTEM -> isSystemInDarkTheme()
    S3ReaderTheme.LIGHT -> false
    S3ReaderTheme.DARK -> true
}

@Composable
internal fun ReaderSystemBars(background: Color, useDarkColors: Boolean) {
    val view = LocalView.current
    val fallback = MaterialTheme.colorScheme.background
    if (!view.isInEditMode) {
        DisposableEffect(view, background, useDarkColors, fallback) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkColors
                    isAppearanceLightNavigationBars = !useDarkColors
                }
            }
            onDispose {
                if (window != null) {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = fallback.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = fallback.toArgb()
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !useDarkColors
                        isAppearanceLightNavigationBars = !useDarkColors
                    }
                }
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFA84F08),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E8D3),
    onPrimaryContainer = Color(0xFF4A2103),
    secondary = Color(0xFF2F6B47),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEBDD),
    onSecondaryContainer = Color(0xFF153B26),
    tertiary = Color(0xFF9A6500),
    onTertiary = Color.White,
    background = Color(0xFFFFF8EA),
    onBackground = Color(0xFF211B17),
    surface = Color(0xFFFFFCF4),
    onSurface = Color(0xFF211B17),
    surfaceVariant = Color(0xFFF3E8D3),
    onSurfaceVariant = Color(0xFF6D5E52),
    outline = Color(0xFFC9B9A5),
    outlineVariant = Color(0xFFE5D8C4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF332D27),
    inverseOnSurface = Color(0xFFFFF8EA),
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE59C57),
    onPrimary = Color(0xFF211B17),
    primaryContainer = Color(0xFF5B2B08),
    onPrimaryContainer = Color(0xFFFFDCC1),
    secondary = Color(0xFF76C796),
    onSecondary = Color(0xFF0D3921),
    secondaryContainer = Color(0xFF244C35),
    onSecondaryContainer = Color(0xFFB5F1C8),
    tertiary = Color(0xFFE1B75A),
    onTertiary = Color(0xFF3D2E00),
    background = Color(0xFF171411),
    onBackground = Color(0xFFF6EEDC),
    surface = Color(0xFF24201C),
    onSurface = Color(0xFFF6EEDC),
    surfaceVariant = Color(0xFF332D27),
    onSurfaceVariant = Color(0xFFCBBEAE),
    outline = Color(0xFF5C5148),
    outlineVariant = Color(0xFF443C35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF6EEDC),
    inverseOnSurface = Color(0xFF332D27),
    surfaceTint = Color.Transparent,
)

private val S0Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 46.sp, lineHeight = 56.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 30.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val S0Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ZhijuanS0Theme(theme: S3ReaderTheme = S3ReaderTheme.SYSTEM, content: @Composable () -> Unit) {
    val useDarkColors = readerUsesDarkTheme(theme)
    val colors = if (useDarkColors) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDarkColors
                isAppearanceLightNavigationBars = !useDarkColors
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = S0Typography,
        shapes = S0Shapes,
        content = content,
    )
}
