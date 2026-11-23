package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino
import app.trombito.rallycopilot.motor.tramos.TramosEnCamino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadorTramosPorDelanteTest {
    /** Carretera recta hacia el norte de [largoM] metros, con un punto cada 100 m. */
    private fun carretera(largoM: Double): List<PuntoGeo> = (0..(largoM / 100.0).toInt()).map { punto(it * 100.0) }

    private fun punto(m: Double) = PuntoGeo(39.5 + m / METROS_POR_GRADO, 2.9)

    private fun trozo(
        id: String,
        desdeM: Double,
        hastaM: Double,
    ): Tramo {
        val traza = (0..((hastaM - desdeM) / 100.0).toInt()).map { punto(desdeM + it * 100.0) }
        return Tramo(id, traza, traza.longitudMetros())
    }

    private fun notas(
        traza: List<PuntoGeo>?,
        posicionM: Double,
        origen: OrigenNotas = OrigenNotas.GUIADO,
        reconocimiento: String? = null,
    ) = NotasEnCurso(HojaNotas(emptyList()), posicionM, 20.0, origen, traza, tramoReconocimiento = reconocimiento)

    private fun real(vararg tramos: Tramo) = CalculadorTramosPorDelante(TramosEnCamino(tramos.toList())::sobre)

    /** Un trozo de tramo ya resuelto, para probar el calculador sin buscar de verdad. */
    private fun trozoFalso(
        hastaM: Double,
        acabaEnMeta: Boolean = true,
    ) = TramoEnCamino(
        tramoId = "F",
        desdeM = 0.0,
        hastaM = hastaM,
        geometria = listOf(punto(0.0), punto(hastaM)),
        salida = punto(0.0),
        meta = punto(hastaM),
        empiezaEnSalida = true,
        acabaEnMeta = acabaEnMeta,
        tramoDesdeM = 0.0,
        tramoHastaM = hastaM,
    )

    @Test
    fun `en guiado encuentra el tramo en la ruta restante y dice cuanto falta para su meta`() {
        val ruta = carretera(6_000.0)
        val r = real(trozo("A", 1_000.0, 3_000.0)).calcular(notas(ruta, 0.0))
        assertEquals("A", r.tramos.single().tramoId)
        assertEquals(3_000.0, r.finDeTramoM!!, 30.0)
    }

    @Test
    fun `la distancia a la meta baja con la posicion sin volver a buscar`() {
        val calculador = CalculadorTramosPorDelante { listOf(trozoFalso(1_000.0)) }
        val ruta = carretera(2_000.0)
        assertEquals(1_000.0, calculador.calcular(notas(ruta, 0.0)).finDeTramoM!!, 0.01)
        val despues = calculador.calcular(notas(ruta, 150.0))
        assertEquals(850.0, despues.finDeTramoM!!, 0.01)
        assertEquals(1, calculador.busquedas)
    }

    @Test
    fun `solo se vuelve a buscar cada 200 m recorridos y la lista es la misma entretanto`() {
        val calculador = CalculadorTramosPorDelante { listOf(trozoFalso(500.0)) }
        val ruta = carretera(5_000.0)
        val primera = calculador.calcular(notas(ruta, 0.0)).tramos
        assertSame(primera, calculador.calcular(notas(ruta, 120.0)).tramos)
        assertSame(primera, calculador.calcular(notas(ruta, 199.0)).tramos)
        assertEquals(1, calculador.busquedas)
        calculador.calcular(notas(ruta, 200.0))
        assertEquals(2, calculador.busquedas)
        calculador.calcular(notas(ruta, 380.0))
        assertEquals(2, calculador.busquedas)
        calculador.calcular(notas(ruta, 410.0))
        assertEquals(3, calculador.busquedas)
    }

    @Test
    fun `una ruta nueva vuelve a buscar aunque la posicion sea la misma`() {
        val calculador = CalculadorTramosPorDelante { listOf(trozoFalso(500.0)) }
        calculador.calcular(notas(carretera(5_000.0), 0.0))
        calculador.calcular(notas(carretera(5_000.0), 0.0))
        assertEquals(2, calculador.busquedas)
    }

    @Test
    fun `un retroceso grande tambien vuelve a buscar`() {
        val calculador = CalculadorTramosPorDelante { listOf(trozoFalso(500.0)) }
        val ruta = carretera(5_000.0)
        calculador.calcular(notas(ruta, 1_000.0))
        calculador.calcular(notas(ruta, 990.0))
        assertEquals(1, calculador.busquedas)
        calculador.calcular(notas(ruta, 950.0))
        assertEquals(2, calculador.busquedas)
    }

    @Test
    fun `en libre solo mira los proximos 3 km de la carretera prevista`() {
        val prevista = carretera(6_000.0)
        val lejos = trozo("lejos", 4_000.0, 5_000.0)
        assertTrue(real(lejos).calcular(notas(prevista, 0.0, OrigenNotas.LIBRE)).tramos.isEmpty())
        // El mismo tramo en guiado sí se ve.
        assertEquals(1, real(lejos).calcular(notas(prevista, 0.0, OrigenNotas.GUIADO)).tramos.size)
        // En libre, 2 km más adelante ya entra en la ventana.
        assertEquals(1, real(lejos).calcular(notas(prevista, 2_000.0, OrigenNotas.LIBRE)).tramos.size)
    }

    @Test
    fun `el trozo que se esta recorriendo se recorta al punto actual`() {
        val ruta = carretera(6_000.0)
        val r = real(trozo("A", 0.0, 4_000.0)).calcular(notas(ruta, 1_000.0)).tramos.single()
        assertEquals(3_000.0, r.longitudM, 30.0)
        assertTrue(!r.empiezaEnSalida)
        assertTrue(r.acabaEnMeta)
    }

    @Test
    fun `sin camino o en reconocimiento no hay tramos`() {
        val calculador = CalculadorTramosPorDelante { listOf(trozoFalso(500.0)) }
        assertTrue(calculador.calcular(null).tramos.isEmpty())
        assertTrue(calculador.calcular(notas(null, 0.0)).tramos.isEmpty())
        assertTrue(calculador.calcular(notas(carretera(2_000.0), 0.0, reconocimiento = "T")).tramos.isEmpty())
        assertEquals(0, calculador.busquedas)
    }

    @Test
    fun `un trozo que no acaba en la meta real no da fin de tramo`() {
        val calculador = CalculadorTramosPorDelante { listOf(trozoFalso(800.0, acabaEnMeta = false)) }
        val r = calculador.calcular(notas(carretera(2_000.0), 0.0))
        assertEquals(1, r.tramos.size)
        assertNull(r.finDeTramoM)
    }

    @Test
    fun `con varios tramos el fin de tramo es la meta mas cercana por delante`() {
        val ruta = carretera(6_000.0)
        val r = real(trozo("A", 500.0, 1_500.0), trozo("B", 3_000.0, 4_500.0)).calcular(notas(ruta, 2_000.0))
        assertEquals(listOf("B"), r.tramos.map { it.tramoId })
        assertNotNull(r.finDeTramoM)
        assertEquals(2_500.0, r.finDeTramoM!!, 30.0)
    }

    @Test
    fun `recortar da el trozo entre dos distancias con sus extremos exactos`() {
        val ruta = carretera(1_000.0)
        val corte = recortarTraza(ruta, 250.0, 620.0)
        assertEquals(370.0, corte.longitudMetros(), 1.0)
        assertEquals(0.0, corte.first().distanciaA(punto(250.0)), 0.5)
        assertEquals(0.0, corte.last().distanciaA(punto(620.0)), 0.5)
        assertTrue(recortarTraza(ruta, 2_000.0, 3_000.0).isEmpty())
    }

    private companion object {
        /** Metros por grado de latitud con el radio medio de `PuntoGeo`. */
        const val METROS_POR_GRADO = 111_194.93
    }
}
