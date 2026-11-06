package app.trombito.rallycopilot.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.trombito.rallycopilot.ui.comun.TipoPlaca
import app.trombito.rallycopilot.ui.comun.colores
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
            // Tarjetas de seccion de Ajustes, su vista previa y la hoja rapida del mapa.
            "onSurface/surfaceContainerLow" to (s.onSurface to s.surfaceContainerLow),
            "onSurfaceVariant/surfaceContainerLow" to (s.onSurfaceVariant to s.surfaceContainerLow),
            "primary/surfaceContainerLow" to (s.primary to s.surfaceContainerLow),
            "onSurface/surfaceContainerLowest" to (s.onSurface to s.surfaceContainerLowest),
            "onSurfaceVariant/surfaceContainerLowest" to (s.onSurfaceVariant to s.surfaceContainerLowest),
            "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
            "primary/primaryContainer" to (s.primary to s.primaryContainer),
            "onSurface/primaryContainer" to (s.onSurface to s.primaryContainer),
            "primary/surfaceContainerHighest" to (s.primary to s.surfaceContainerHighest),
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

    /** Las placas de carretera llevan su texto sobre el color de la señal real: también AA. */
    @Test
    fun placasDeCarretera_meetAA() {
        val failures =
            TipoPlaca.entries.mapNotNull { tipo ->
                val c = tipo.colores()
                val r = ratio(c.texto, c.fondo)
                if (r < MIN_AA) "$tipo = ${"%.2f".format(r)}" else null
            }
        assertTrue("Contraste < $MIN_AA: $failures", failures.isEmpty())
    }

    /** Los colores de delta y aviso tambien se leen sobre las tarjetas y el fondo de la muestra de tema. */
    @Test
    fun coloresRally_meetAAOnCardsAndPanels() {
        val casos = listOf(Triple("oscuro", RallyOscuro, DarkColors), Triple("claro", RallyClaro, LightColors))
        val failures =
            casos.flatMap { (nombre, rally, esquema) ->
                val fondos =
                    mapOf(
                        "background" to esquema.background,
                        "surfaceContainerLow" to esquema.surfaceContainerLow,
                        "surfaceContainerLowest" to esquema.surfaceContainerLowest,
                    )
                fondos.flatMap { (fondo, color) ->
                    listOf("deltaMejor" to rally.deltaMejor, "deltaPeor" to rally.deltaPeor, "aviso" to rally.aviso)
                        .mapNotNull { (k, c) ->
                            val r = ratio(c, color)
                            if (r < MIN_AA) "$nombre $k/$fondo = ${"%.2f".format(r)}" else null
                        }
                }
            }
        assertTrue("Contraste < $MIN_AA: $failures", failures.isEmpty())
    }

    /** El carbón translúcido compuesto sobre el mapa más claro posible: texto, secundario y ámbar siguen en AA. */
    @Test
    fun carbonTranslucido_sobreMapaClaro_meetAA() {
        val mapas = mapOf("blanco" to Color.White, "mapa claro" to Color(0xFFF2EFE9), "calle" to Color(0xFFFFFFFF))
        val failures =
            mapas.flatMap { (nombre, mapa) ->
                val fondo = CarbonTranslucido.compositeOver(mapa)
                mapOf(
                    "TextoSobreCarbon" to TextoSobreCarbon,
                    "TextoSecundarioSobreCarbon" to TextoSecundarioSobreCarbon,
                    "AmbarSobreCarbon" to AmbarSobreCarbon,
                    "deltaPeor" to RallyOscuro.deltaPeor,
                    "deltaMejor" to RallyOscuro.deltaMejor,
                    "aviso" to RallyOscuro.aviso,
                ).mapNotNull { (k, c) ->
                    val r = ratio(c, fondo)
                    if (r < MIN_AA) "$nombre $k = ${"%.2f".format(r)}" else null
                }
            }
        assertTrue("Contraste < $MIN_AA: $failures", failures.isEmpty())
    }

    /** El ámbar del tema claro (títulos y activos) sobre blanco roto y sobre las tarjetas opacas. */
    @Test
    fun ambarClaro_sobreBlancoRoto_meetAA() {
        val fondos =
            mapOf(
                "blanco roto" to Color(0xFFF3F2EE),
                "blanco" to Color.White,
                "surfaceContainerHigh" to LightColors.surfaceContainerHigh,
            )
        val failures =
            fondos.mapNotNull { (k, f) ->
                val r = ratio(LightColors.primary, f)
                if (r < MIN_AA) "$k = ${"%.2f".format(r)}" else null
            }
        assertTrue("Contraste < $MIN_AA: $failures", failures.isEmpty())
    }

    /** Tarjeta opaca: título ámbar, texto y ayuda sobre surfaceContainerHigh en los dos temas. */
    @Test
    fun tarjetaOpaca_meetAA() {
        listOf("oscuro" to DarkColors, "claro" to LightColors).forEach { (nombre, s) ->
            val fondo = s.surfaceContainerHigh
            listOf("primary" to s.primary, "onSurface" to s.onSurface, "onSurfaceVariant" to s.onSurfaceVariant)
                .forEach { (k, c) -> assertTrue("$nombre $k", ratio(c, fondo) >= MIN_AA) }
        }
    }

    private companion object {
        const val MIN_AA = 4.5
    }
}
