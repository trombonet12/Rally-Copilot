package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.motor.analisis.SegmentoMapa
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** La traza coloreada por velocidad: lo que se escribe en las features y la expresión que lo lee. */
@RunWith(RobolectricTestRunner::class)
class MapaAnalisisTrazaTest {
    private fun segmento(
        desde: Int,
        kmh: Double,
    ) = SegmentoMapa(
        listOf(PuntoGeo(39.6 + desde * 0.0002, 2.9), PuntoGeo(39.6 + (desde + 1) * 0.0002, 2.9)),
        desde * 25.0,
        (desde + 1) * 25.0,
        kmh,
    )

    @Test
    fun `cada segmento va en una feature con su posicion numerica en la escala`() {
        val segmentos = listOf(segmento(0, 20.0), segmento(1, 60.0), segmento(2, 100.0))
        val escala = EscalaVelocidad.de(segmentos.map { it.velocidadKmh })

        val features = featuresTraza(segmentos, escala)

        assertEquals(3, features.size)
        val fracciones = features.map { it.getNumberProperty(PROPIEDAD_FRACCION).toDouble() }
        assertEquals(0.0, fracciones.first(), 1e-9)
        assertEquals(1.0, fracciones.last(), 1e-9)
        assertTrue("la del medio queda entre las dos: $fracciones", fracciones[1] > 0.0 && fracciones[1] < 1.0)
    }

    @Test
    fun `los segmentos de un solo punto no generan feature`() {
        val suelto = SegmentoMapa(listOf(PuntoGeo(39.6, 2.9)), 0.0, 0.0, 50.0)
        assertTrue(featuresTraza(listOf(suelto), EscalaVelocidad.de(listOf(50.0))).isEmpty())
    }

    @Test
    fun `el color lee la propiedad como numero y recorre las paradas de la leyenda`() {
        val expresion = colorPorVelocidad().toString()

        assertTrue(expresion, expresion.contains("to-number"))
        assertTrue(expresion, expresion.contains("interpolate"))
        assertTrue(expresion, expresion.contains("\"$PROPIEDAD_FRACCION\""))
    }

    @Test
    fun `la fuente de la traza no se simplifica para que no desaparezcan los segmentos cortos`() {
        assertEquals(0f, opcionesFuenteTraza()["tolerance"])
    }
}
