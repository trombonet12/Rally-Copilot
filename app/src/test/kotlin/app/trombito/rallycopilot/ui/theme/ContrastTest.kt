package app.trombito.rallycopilot.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ContrastTest {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double {
        val r = channel(c.red)
        val g = channel(c.green)
        val b = channel(c.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun pairs(s: ColorScheme) =
        mapOf(
            "onPrimary/primary" to (s.onPrimary to s.primary),
            "onSurface/surface" to (s.onSurface to s.surface),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "onError/error" to (s.onError to s.error),
            "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondary/secondary" to (s.onSecondary to s.secondary),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
            "onBackground/background" to (s.onBackground to s.background),
            "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
            "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
            "onSurfaceVariant/surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
            "onSurfaceVariant/surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
            "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
            "inversePrimary/inverseSurface" to (s.inversePrimary to s.inverseSurface),
            "primary/surface" to (s.primary to s.surface),
            "tertiary/surface" to (s.tertiary to s.surface),
        )

    private fun check(
        name: String,
        scheme: ColorScheme,
    ) {
        val failures =
            pairs(scheme).mapNotNull { (k, v) ->
                val r = ratio(v.first, v.second)
                if (r < MIN_AA) "$name $k = ${"%.2f".format(r)}" else null
            }
        assertTrue("Contraste < $MIN_AA: $failures", failures.isEmpty())
    }

    @Test
    fun ratio_blackOnWhite_is21() = assertEquals(21.0, ratio(Color.Black, Color.White), 0.01)

    @Test
    fun ratio_isSymmetric() = assertEquals(ratio(Color.Red, Color.White), ratio(Color.White, Color.Red), 1e-9)

    @Test
    fun lightScheme_meetsAA() = check("light", LightColors)

    @Test
    fun darkScheme_meetsAA() = check("dark", DarkColors)

    /** Delta y avisos se leen como texto grande sobre el mapa o la superficie: también AA. */
    @Test
    fun coloresRally_meetAAOnSurface() {
        val casos =
            listOf(
                Triple("oscuro", RallyOscuro, DarkColors),
                Triple("claro", RallyClaro, LightColors),
            )
        val failures =
            casos.flatMap { (nombre, rally, esquema) ->
                listOf("deltaMejor" to rally.deltaMejor, "deltaPeor" to rally.deltaPeor, "aviso" to rally.aviso)
                    .mapNotNull { (k, c) ->
                        val r = ratio(c, esquema.surface)
                        if (r < MIN_AA) "$nombre $k = ${"%.2f".format(r)}" else null
                    }
            }
        assertTrue("Contraste < $MIN_AA: $failures", failures.isEmpty())
    }

    private companion object {
        const val MIN_AA = 4.5
    }
}
