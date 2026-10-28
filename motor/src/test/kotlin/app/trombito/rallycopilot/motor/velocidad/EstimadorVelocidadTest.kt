package app.trombito.rallycopilot.motor.velocidad

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstimadorVelocidadTest {
    // 0,0001 grados de latitud son unos 11,12 m.
    private val inicio = PuntoGeo(39.5, 2.6)

    private fun alNorte(pasos: Int) = PuntoGeo(39.5 + 0.0001 * pasos, 2.6)

    private fun fijacion(
        punto: PuntoGeo,
        t: Long,
        v: Double? = null,
        rumbo: Double? = null,
    ) = Fijacion(punto, t, v, rumbo, precisionM = 5.0)

    @Test
    fun `kmh convierte y redondea al entero`() {
        assertEquals(0, kmh(0.0))
        assertEquals(36, kmh(10.0))
        assertEquals(50, kmh(13.89))
        assertEquals(4, kmh(1.1)) // 3,96
        assertEquals(1, kmh(0.4)) // 1,44
        assertEquals(0, kmh(0.1)) // 0,36
        assertEquals(100, kmh(27.78))
    }

    @Test
    fun `kmh devuelve cero para negativos, NaN e infinitos`() {
        assertEquals(0, kmh(-3.0))
        assertEquals(0, kmh(Double.NaN))
        assertEquals(0, kmh(Double.POSITIVE_INFINITY))
        assertEquals(0, kmh(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `usa la velocidad del GPS si viene`() {
        val e = EstimadorVelocidad()
        assertEquals(12.5, e.actualizar(fijacion(inicio, 1_000, v = 12.5))!!, 1e-9)
        assertEquals(12.5, e.vigente(1_000)!!, 1e-9)
    }

    @Test
    fun `la primera fijacion sin velocidad no da estimacion`() {
        val e = EstimadorVelocidad()
        assertNull(e.actualizar(fijacion(inicio, 1_000)))
        assertNull(e.vigente(1_000))
    }

    @Test
    fun `calcula la velocidad por distancia y tiempo si no viene`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        val v = e.actualizar(fijacion(alNorte(1), 1_000))!!
        assertEquals(inicio.distanciaA(alNorte(1)), v, 1e-6)
        assertEquals(40, kmh(v))
    }

    @Test
    fun `calcula con dos segundos de separacion`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        val v = e.actualizar(fijacion(alNorte(2), 2_000))!!
        assertEquals(inicio.distanciaA(alNorte(2)) / 2.0, v, 1e-6)
    }

    @Test
    fun `no calcula si el intervalo es mayor de cinco segundos`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        assertNull(e.actualizar(fijacion(alNorte(5), 6_000)))
        assertNull(e.vigente(6_000))
    }

    @Test
    fun `no calcula si el intervalo es menor de medio segundo y espera a la siguiente`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        assertNull(e.actualizar(fijacion(alNorte(1), 300)))
        // La referencia sigue siendo la de t=0.
        val v = e.actualizar(fijacion(alNorte(2), 1_000))!!
        assertEquals(inicio.distanciaA(alNorte(2)), v, 1e-6)
    }

    @Test
    fun `descarta un salto con velocidad implicita absurda y conserva la referencia`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        val v1 = e.actualizar(fijacion(alNorte(1), 1_000))!!
        // Salto de ~1,1 km en 1 s: 1100 m/s > 70.
        val salto = e.actualizar(fijacion(alNorte(100), 2_000))
        assertEquals(v1, salto!!, 1e-9)
        assertEquals(v1, e.vigente(2_000)!!, 1e-9)
        // La referencia sigue siendo alNorte(1) en t=1000.
        val v2 = e.actualizar(fijacion(alNorte(2), 3_000))!!
        assertEquals(alNorte(1).distanciaA(alNorte(2)) / 2.0, v2, 1e-6)
    }

    @Test
    fun `el umbral de salto es configurable`() {
        val e = EstimadorVelocidad(maxSaltoMs = 5.0)
        e.actualizar(fijacion(inicio, 0))
        // 11 m/s > 5: descartada, no hay velocidad.
        assertNull(e.actualizar(fijacion(alNorte(1), 1_000)))
    }

    @Test
    fun `la velocidad caduca pasado el limite`() {
        val e = EstimadorVelocidad(caducidadMs = 5_000)
        e.actualizar(fijacion(inicio, 1_000, v = 20.0))
        assertEquals(20.0, e.vigente(6_000)!!, 1e-9)
        assertNull(e.vigente(6_001))
    }

    @Test
    fun `sin fijaciones no hay velocidad ni rumbo`() {
        val e = EstimadorVelocidad()
        assertNull(e.vigente(0))
        assertNull(e.rumbo())
    }

    @Test
    fun `ignora una velocidad de GPS invalida y calcula por posicion`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        val v = e.actualizar(fijacion(alNorte(1), 1_000, v = Double.NaN))!!
        assertEquals(inicio.distanciaA(alNorte(1)), v, 1e-6)
    }

    @Test
    fun `una fijacion con tiempo anterior se descarta`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 5_000, v = 10.0))
        assertEquals(10.0, e.actualizar(fijacion(alNorte(1), 4_000, v = 30.0))!!, 1e-9)
    }

    @Test
    fun `una fijacion con el mismo tiempo y velocidad GNSS valida da su velocidad`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 5_000, v = 10.0))
        assertEquals(30.0, e.actualizar(fijacion(alNorte(1), 5_000, v = 30.0))!!, 1e-9)
    }

    @Test
    fun `primera sin velocidad y siguientes con velocidad y tiempo repetido dan 50 kmh`() {
        val e = EstimadorVelocidad()
        assertNull(e.actualizar(fijacion(inicio, 5_000)))
        val v = e.actualizar(fijacion(alNorte(1), 5_000, v = 13.89))!!
        assertEquals(50, kmh(v))
        assertEquals(50, kmh(e.actualizar(fijacion(alNorte(2), 5_000, v = 13.89))!!))
        assertEquals(50, kmh(e.actualizar(fijacion(alNorte(3), 5_000, v = 13.89))!!))
    }

    @Test
    fun `con el mismo tiempo y sin velocidad no calcula ni actualiza`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 5_000, v = 10.0))
        assertEquals(10.0, e.actualizar(fijacion(alNorte(1), 5_000))!!, 1e-9)
    }

    @Test
    fun `una velocidad GNSS con un salto de posicion absurdo y tiempo repetido se acepta sin calcular por posicion`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 5_000, v = 10.0))
        // La velocidad la da el receptor: el salto solo importa para la velocidad calculada por posicion.
        assertEquals(20.0, e.actualizar(fijacion(alNorte(100), 5_000, v = 20.0))!!, 1e-9)
    }

    @Test
    fun `el rumbo del GPS manda`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0, v = 10.0, rumbo = 123.0))
        assertEquals(123.0, e.rumbo()!!, 1e-9)
    }

    @Test
    fun `calcula el rumbo entre las dos ultimas fijaciones si se movio mas de tres metros`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        e.actualizar(fijacion(alNorte(1), 1_000))
        assertEquals(0.0, e.rumbo()!!, 0.5)

        val este = PuntoGeo(alNorte(1).lat, alNorte(1).lon + 0.0001)
        e.actualizar(fijacion(este, 2_000))
        assertEquals(90.0, e.rumbo()!!, 1.0)
    }

    @Test
    fun `el rumbo al sur es 180 y al oeste 270`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(alNorte(1), 0))
        e.actualizar(fijacion(inicio, 1_000))
        assertEquals(180.0, e.rumbo()!!, 0.5)

        val oeste = PuntoGeo(inicio.lat, inicio.lon - 0.0001)
        e.actualizar(fijacion(oeste, 2_000))
        assertEquals(270.0, e.rumbo()!!, 1.0)
    }

    @Test
    fun `si se movio menos de tres metros conserva el rumbo anterior`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0))
        e.actualizar(fijacion(alNorte(1), 1_000))
        val casiParado = PuntoGeo(alNorte(1).lat + 0.00001, alNorte(1).lon + 0.00002) // ~2,5 m
        e.actualizar(fijacion(casiParado, 2_000))
        assertEquals(0.0, e.rumbo()!!, 0.5)
    }

    @Test
    fun `una sola fijacion sin rumbo no da rumbo`() {
        val e = EstimadorVelocidad()
        e.actualizar(fijacion(inicio, 0, v = 5.0))
        assertNull(e.rumbo())
    }
}
