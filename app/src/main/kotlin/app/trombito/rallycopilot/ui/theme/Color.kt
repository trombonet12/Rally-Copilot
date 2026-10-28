package app.trombito.rallycopilot.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta de la marca Trombito (latón del trombón, rojo de las gafas) sobre carbón de retransmisión. Sin colores
// dinámicos. ContrastTest exige AA (≥ 4,5:1) en cada pareja de texto y fondo.

val DarkColors =
    darkColorScheme(
        primary = Color(0xFFE5AB46),
        onPrimary = Color(0xFF2A1C00),
        primaryContainer = Color(0xFF3A2A0A),
        onPrimaryContainer = Color(0xFFF6E7C6),
        secondary = Color(0xFF8FC1E3),
        onSecondary = Color(0xFF08202E),
        secondaryContainer = Color(0xFF142833),
        onSecondaryContainer = Color(0xFFD6E7F0),
        tertiary = Color(0xFFFF8F85),
        onTertiary = Color(0xFF3D0606),
        tertiaryContainer = Color(0xFF5C1014),
        onTertiaryContainer = Color(0xFFFFDAD5),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E0F12),
        onBackground = Color(0xFFECEDEF),
        surface = Color(0xFF0E0F12),
        onSurface = Color(0xFFECEDEF),
        surfaceVariant = Color(0xFF23262B),
        onSurfaceVariant = Color(0xFFBFC3C9),
        surfaceContainerLowest = Color(0xFF08090B),
        surfaceContainerLow = Color(0xFF15171A),
        surfaceContainer = Color(0xFF1A1C20),
        surfaceContainerHigh = Color(0xFF22252A),
        surfaceContainerHighest = Color(0xFF2C2F35),
        outline = Color(0xFF80858D),
        outlineVariant = Color(0xFF2C2F35),
        inverseSurface = Color(0xFFECEDEF),
        inverseOnSurface = Color(0xFF1A1C20),
        inversePrimary = Color(0xFF8C5A04),
    )

val LightColors =
    lightColorScheme(
        primary = Color(0xFF8C5A04),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF6E7C6),
        onPrimaryContainer = Color(0xFF3A2700),
        secondary = Color(0xFF2C6E92),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E7F0),
        onSecondaryContainer = Color(0xFF0E2A3A),
        tertiary = Color(0xFFB3151B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDAD5),
        onTertiaryContainer = Color(0xFF410003),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF3F2EE),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFF3F2EE),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFE3E2DD),
        onSurfaceVariant = Color(0xFF46484D),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F7F4),
        surfaceContainer = Color(0xFFF1F0EC),
        surfaceContainerHigh = Color(0xFFEBEAE5),
        surfaceContainerHighest = Color(0xFFE3E2DD),
        outline = Color(0xFF767980),
        outlineVariant = Color(0xFFC9CACD),
        inverseSurface = Color(0xFF2E3035),
        inverseOnSurface = Color(0xFFF1F0EC),
        inversePrimary = Color(0xFFE5AB46),
    )

/** Colores propios de la conducción (HUD de retransmisión): delta, avisos y franja de marca. */
@Immutable
data class ColoresRally(
    /** Delta a favor (más rápido que el mejor tiempo). */
    val deltaMejor: Color,
    /** Delta en contra. */
    val deltaPeor: Color,
    /** Banners de radar y límite de velocidad. */
    val aviso: Color,
    /** Franja roja de la marca (casco, rótulos). Decorativa: no lleva texto encima. */
    val franja: Color,
)

val RallyOscuro =
    ColoresRally(
        deltaMejor = Color(0xFF3DDC84),
        deltaPeor = Color(0xFFFF6B5E),
        aviso = Color(0xFFFFC94A),
        franja = Color(0xFFD8232A),
    )

val RallyClaro =
    ColoresRally(
        deltaMejor = Color(0xFF0B7A3E),
        deltaPeor = Color(0xFFB3151B),
        aviso = Color(0xFF8A5A00),
        franja = Color(0xFFD8232A),
    )

val LocalColoresRally = staticCompositionLocalOf { RallyOscuro }
