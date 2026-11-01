package app.trombito.rallycopilot.motor.geo

import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class AjusteCaminoTest {
    private val origen = PuntoGeo(39.6, 2.9)

    /** Punto a [norteM] y [esteM] metros del origen (plano local, de sobra para estas distancias). */
    private fun desdeOrigen(
        norteM: Double,
        esteM: Double,
    ) = PuntoGeo(
        origen.lat + norteM / M_POR_GRADO,
        origen.lon + esteM / (M_POR_GRADO * cos(Math.toRadians(origen.lat))),
    )

    /** Carretera recta de 1 km hacia el este. */
    private val recta = CaminoPlano(listOf(desdeOrigen(0.0, 0.0), desdeOrigen(0.0, 1_000.0)))

    /** 500 m al este y luego 500 m al norte: vértice en (0, 500). */
    private val esquina =
        CaminoPlano(listOf(desdeOrigen(0.0, 0.0), desdeOrigen(0.0, 500.0), desdeOrigen(500.0, 500.0)))

    private fun fijacion(
        punto: PuntoGeo,
        rumbo: Double? = null,
        velocidadMs: Double? = 15.0,
    ) = Fijacion(punto, tiempoMs = 0, velocidadMs = velocidadMs, rumboGrados = rumbo, precisionM = 5.0)

    private fun ajustar(
        camino: CaminoPlano,
        f: Fijacion,
        sentido: SentidoCamino = SentidoCamino.ADELANTE,
        previo: Double? = null,
    ) = AjusteCamino.ajustar(camino, sentido, f, previo)

    private fun diferencia(
        a: Double,
        b: Double,
    ): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    @Test
    fun `proyecta la fijacion sobre el camino y da la tangente`() {
        val r = ajustar(recta, fijacion(desdeOrigen(10.0, 400.0), rumbo = 100.0))

        assertTrue(r.enCamino)
        assertEquals(0.0, desdeOrigen(0.0, 400.0).distanciaA(r.punto), 0.5)
        assertEquals(0.0, diferencia(90.0, r.rumboGrados ?: Double.NaN), 0.5)
    }

    @Test
    fun `sin rumbo bruto tambien se ajusta al camino`() {
        val r = ajustar(recta, fijacion(desdeOrigen(-8.0, 200.0), rumbo = null))

        assertTrue(r.enCamino)
        assertEquals(0.0, desdeOrigen(0.0, 200.0).distanciaA(r.punto), 0.5)
        assertEquals(90.0, r.rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `el rumbo por 30 grados de error del GPS sale como el del camino`() {
        val r = ajustar(recta, fijacion(desdeOrigen(3.0, 300.0), rumbo = 60.0))

        assertTrue(r.enCamino)
        assertEquals(90.0, r.rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `en el vertice la tangente se suaviza y no salta`() {
        val enVertice = ajustar(esquina, fijacion(desdeOrigen(0.0, 500.0)))
        val antes = ajustar(esquina, fijacion(desdeOrigen(0.0, 496.0)))
        val lejosAntes = ajustar(esquina, fijacion(desdeOrigen(0.0, 400.0)))
        val despues = ajustar(esquina, fijacion(desdeOrigen(100.0, 500.0)))

        assertEquals(45.0, enVertice.rumboGrados ?: Double.NaN, 2.0)
        val rumboAntes = antes.rumboGrados ?: Double.NaN
        assertTrue("A 4 m del vértice ya gira un poco: $rumboAntes", rumboAntes > 45.0 && rumboAntes < 88.0)
        assertEquals(90.0, lejosAntes.rumboGrados ?: Double.NaN, 0.5)
        assertEquals(0.0, diferencia(0.0, despues.rumboGrados ?: Double.NaN), 0.5)
    }

    @Test
    fun `con rumbo contrario al camino de sentido unico la fijacion se queda como esta`() {
        val f = fijacion(desdeOrigen(5.0, 300.0), rumbo = 270.0)
        val r = ajustar(recta, f, SentidoCamino.ADELANTE)

        assertFalse(r.enCamino)
        assertEquals(f.punto, r.punto)
        assertEquals(270.0, r.rumboGrados ?: Double.NaN, 0.0)
    }

    @Test
    fun `el umbral de rumbo es 60 grados`() {
        assertTrue(ajustar(recta, fijacion(desdeOrigen(2.0, 300.0), rumbo = 31.0)).enCamino)
        assertFalse(ajustar(recta, fijacion(desdeOrigen(2.0, 300.0), rumbo = 29.0)).enCamino)
        assertTrue(ajustar(recta, fijacion(desdeOrigen(2.0, 300.0), rumbo = 149.0)).enCamino)
        assertFalse(ajustar(recta, fijacion(desdeOrigen(2.0, 300.0), rumbo = 151.0)).enCamino)
    }

    @Test
    fun `en doble sentido manda el rumbo del movimiento`() {
        val ida = ajustar(recta, fijacion(desdeOrigen(4.0, 300.0), rumbo = 80.0), SentidoCamino.AMBOS)
        val vuelta = ajustar(recta, fijacion(desdeOrigen(4.0, 300.0), rumbo = 260.0), SentidoCamino.AMBOS)

        assertTrue(ida.enCamino && vuelta.enCamino)
        assertEquals(90.0, ida.rumboGrados ?: Double.NaN, 0.5)
        assertEquals(270.0, vuelta.rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `en doble sentido sin rumbo bruto se queda con el sentido del rumbo previo`() {
        val f = fijacion(desdeOrigen(4.0, 300.0), rumbo = null)

        assertEquals(270.0, ajustar(recta, f, SentidoCamino.AMBOS, previo = 255.0).rumboGrados ?: Double.NaN, 0.5)
        assertEquals(90.0, ajustar(recta, f, SentidoCamino.AMBOS, previo = 100.0).rumboGrados ?: Double.NaN, 0.5)
        assertEquals(90.0, ajustar(recta, f, SentidoCamino.AMBOS, previo = null).rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `un camino recorrido hacia atras da la tangente invertida`() {
        val r = ajustar(recta, fijacion(desdeOrigen(2.0, 300.0), rumbo = 275.0), SentidoCamino.ATRAS)

        assertTrue(r.enCamino)
        assertEquals(270.0, r.rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `a mas de 25 metros del camino la fijacion queda tal cual`() {
        val lejos = fijacion(desdeOrigen(40.0, 300.0), rumbo = 85.0)
        val r = ajustar(recta, lejos)

        assertFalse(r.enCamino)
        assertEquals(lejos.punto, r.punto)
        assertEquals(85.0, r.rumboGrados ?: Double.NaN, 0.0)
        assertTrue(ajustar(recta, fijacion(desdeOrigen(24.0, 300.0), rumbo = 85.0)).enCamino)
        assertFalse(ajustar(recta, fijacion(desdeOrigen(26.0, 300.0), rumbo = 85.0)).enCamino)
    }

    @Test
    fun `parado el rumbo no cambia y la posicion se ajusta`() {
        val r = ajustar(recta, fijacion(desdeOrigen(6.0, 300.0), rumbo = 200.0, velocidadMs = 0.5), previo = 90.0)

        assertTrue(r.enCamino)
        assertEquals(0.0, desdeOrigen(0.0, 300.0).distanciaA(r.punto), 0.5)
        assertEquals(90.0, r.rumboGrados ?: Double.NaN, 0.0)
    }

    @Test
    fun `parado y sin rumbo previo usa la tangente del camino`() {
        val r = ajustar(recta, fijacion(desdeOrigen(6.0, 300.0), rumbo = 200.0, velocidadMs = 0.0))

        assertTrue(r.enCamino)
        assertEquals(90.0, r.rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `parado fuera del camino conserva el ultimo rumbo`() {
        val r = ajustar(recta, fijacion(desdeOrigen(60.0, 300.0), rumbo = 200.0, velocidadMs = 0.2), previo = 90.0)

        assertFalse(r.enCamino)
        assertEquals(90.0, r.rumboGrados ?: Double.NaN, 0.0)
    }

    @Test
    fun `la ventana de progreso elige el tramo cuando el camino pasa dos veces`() {
        // Ida al este, giro de 20 m al norte y vuelta al oeste: la fijación está a 10 m de ambos tramos.
        val ida =
            CaminoPlano(
                listOf(
                    desdeOrigen(0.0, 0.0),
                    desdeOrigen(0.0, 500.0),
                    desdeOrigen(20.0, 500.0),
                    desdeOrigen(20.0, 0.0),
                ),
            )
        val f = fijacion(desdeOrigen(10.0, 250.0), rumbo = null)

        val primero = AjusteCamino.ajustar(ida, SentidoCamino.ADELANTE, f, null, ventanaM = 0.0..400.0)
        val segundo = AjusteCamino.ajustar(ida, SentidoCamino.ADELANTE, f, null, ventanaM = 540.0..900.0)

        assertEquals(90.0, primero.rumboGrados ?: Double.NaN, 0.5)
        assertEquals(270.0, segundo.rumboGrados ?: Double.NaN, 0.5)
    }

    @Test
    fun `si la ventana no contiene el camino cercano la fijacion queda tal cual`() {
        val r =
            AjusteCamino.ajustar(
                recta,
                SentidoCamino.ADELANTE,
                fijacion(desdeOrigen(2.0, 900.0), rumbo = 90.0),
                null,
                ventanaM = 0.0..200.0,
            )

        assertFalse(r.enCamino)
    }

    private companion object {
        const val M_POR_GRADO = 111_194.9
    }
}
