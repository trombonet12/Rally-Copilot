package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** El ordinal de la rotonda se calcula sobre el grafo local y sustituye al de Valhalla (siempre 2). */
class CorrectorRotondasTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun nodo(k: Int) = p(30.0 * cos(Math.toRadians(45.0 * k)), 30.0 * sin(Math.toRadians(45.0 * k)))

    private fun lejos(k: Int) = p(100.0 * cos(Math.toRadians(45.0 * k)), 100.0 * sin(Math.toRadians(45.0 * k)))

    private fun via(
        id: Long,
        vararg puntos: PuntoGeo,
        junction: String? = null,
    ) = Via(id, "tertiary", null, null, null, SentidoUnico.NO, null, null, puntos.toList(), junction = junction)

    private val anillo = via(100, *(0..8).map { nodo(it % 8) }.toTypedArray(), junction = "roundabout")
    private val grafo = GrafoVias(listOf(anillo) + listOf(6, 0, 2, 4).map { via(200L + it, nodo(it), lejos(it)) })

    /**
     * Ruta que llega del sur, entra en la rotonda por el nodo 6 y sale por el nodo [salida]. Indices: 0 entrada lejana,
     * 1 nodo 6 (inicio de la maniobra), 2 nodo de salida (fin del anillo), 3 punto de salida lejano.
     */
    private fun ruta(
        salida: Int,
        valhalla: Int? = 2,
        fundida: Boolean = true,
    ): Ruta {
        val puntos = listOf(lejos(6), nodo(6), nodo(salida), lejos(salida))
        val rotonda =
            Maniobra(
                TipoManiobra.ROTONDA_ENTRADA,
                "Entra en la rotonda.",
                listOf("Ma-10"),
                1,
                if (fundida) 3 else 2,
                80.0,
                8.0,
                valhalla,
                salidaAnilloIndice = if (fundida) 2 else null,
            )
        val destino = Maniobra(TipoManiobra.DESTINO, "Llegas", emptyList(), 3, 3, 0.0, 0.0, null)
        return Ruta(puntos, listOf(rotonda, destino), 80.0, 8.0)
    }

    @Test
    fun `el ordinal calculado sustituye al de Valhalla para las salidas 1 2 3 y 4`() {
        val esperadas = mapOf(0 to "primera", 2 to "segunda", 4 to "tercera", 6 to "cuarta")
        esperadas.forEach { (salida, ordinal) ->
            val corregida = CorrectorRotondas.aplicar(ruta(salida), grafo)
            assertEquals("rotonda, $ordinal salida, Ma-10", FrasesManiobra.frase(corregida.maniobras[0]))
        }
        assertEquals(1, CorrectorRotondas.aplicar(ruta(0), grafo).maniobras[0].salidaRotonda)
        assertEquals(3, CorrectorRotondas.aplicar(ruta(4), grafo).maniobras[0].salidaRotonda)
    }

    @Test
    fun `sin salidaAnilloIndice usa el fin de la maniobra`() {
        assertEquals(3, CorrectorRotondas.aplicar(ruta(4, fundida = false), grafo).maniobras[0].salidaRotonda)
    }

    @Test
    fun `si el grafo no resuelve la rotonda se canta sin ordinal y no con el de Valhalla`() {
        val sinRotonda = GrafoVias(listOf(via(1, p(0.0, 0.0), p(100.0, 0.0))))
        val corregida = CorrectorRotondas.aplicar(ruta(4, valhalla = 2), sinRotonda)
        assertNull(corregida.maniobras[0].salidaRotonda)
        assertEquals("en la rotonda, sal hacia Ma-10", FrasesManiobra.frase(corregida.maniobras[0]))
    }

    @Test
    fun `no toca las maniobras que no son rotondas`() {
        val r = ruta(4)
        assertEquals(r.maniobras[1], CorrectorRotondas.aplicar(r, grafo).maniobras[1])
    }
}
