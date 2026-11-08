package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.alertas.GeoPruebas.punto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class MediaTramoLibreTest {
    // Tramo real de OSM (osm-r20233503, Mallorca): recta de unos 940 m a 43 grados.
    private val ini = Radar("t-ini", TipoRadar.TRAMO_INICIO, PuntoGeo(39.486943, 2.746213), 43.0, 90, "t", "OSM")
    private val fin = Radar("t-fin", TipoRadar.TRAMO_FIN, PuntoGeo(39.493085, 2.753659), 43.0, 90, "t", "OSM")
    private val rectaM = ini.punto.distanciaA(fin.punto)
    private val dNorte = Math.toRadians(fin.punto.lat - ini.punto.lat) * PuntoGeo.RADIO_TIERRA_M
    private val dEste =
        Math.toRadians(fin.punto.lon - ini.punto.lon) * PuntoGeo.RADIO_TIERRA_M * cos(Math.toRadians(39.49))

    /** Punto a [norteM] y [esteM] metros del radar de inicio. */
    private fun pi(
        norteM: Double,
        esteM: Double,
    ) = punto(norteM, esteM, ini.punto)

    /** Punto de la recta inicio-fin a [m] metros del inicio (negativos: antes de él; más que la recta: tras el fin). */
    private fun enRecta(m: Double) = pi(m * dNorte / rectaM, m * dEste / rectaM)

    /** Fijaciones cada segundo recorriendo [vertices] a [vMs] m/s (la última es el último vértice). */
    private fun recorrer(
        vertices: List<PuntoGeo>,
        vMs: Double,
    ): List<Fijacion> {
        val acumulada = DoubleArray(vertices.size)
        for (i in 1 until vertices.size) acumulada[i] = acumulada[i - 1] + vertices[i - 1].distanciaA(vertices[i])
        val total = acumulada.last()
        val fijaciones = ArrayList<Fijacion>()
        var s = 0
        while (s * vMs < total) {
            val d = s * vMs
            val i = (1 until acumulada.size).first { acumulada[it] >= d }
            val f = (d - acumulada[i - 1]) / (acumulada[i] - acumulada[i - 1])
            val a = vertices[i - 1]
            val b = vertices[i]
            val p = PuntoGeo(a.lat + f * (b.lat - a.lat), a.lon + f * (b.lon - a.lon))
            fijaciones += Fijacion(p, s * 1000L, vMs, null, 5.0)
            s++
        }
        fijaciones += Fijacion(vertices.last(), Math.round(total / vMs * 1000), vMs, null, 5.0)
        return fijaciones
    }

    private fun medir(
        fijaciones: List<Fijacion>,
        radares: List<Radar> = listOf(ini, fin),
    ): List<EstadoTramo?> {
        val medidor = MediaTramoLibre(radares)
        return fijaciones.map(medidor::actualizar)
    }

    @Test
    fun `tramo recto a velocidad constante da esa velocidad y cierra en el fin`() {
        val estados = medir(recorrer(listOf(enRecta(-400.0), enRecta(rectaM + 300.0)), 25.0))
        val ultimoMedido = estados.filterNotNull().last()
        assertTrue(ultimoMedido.terminado)
        assertEquals(90.0, checkNotNull(ultimoMedido.mediaKmh), 0.5)
        assertEquals("t", ultimoMedido.tramo.id)
        assertEquals(90, ultimoMedido.tramo.limiteKmh)
        // Tras cerrar no sigue habiendo estado.
        assertNull(estados.last())
        assertEquals(1, estados.count { it?.terminado == true })
    }

    @Test
    fun `antes de recorrer 100 m no da media y despues si`() {
        val estados = medir(recorrer(listOf(enRecta(-400.0), enRecta(rectaM + 300.0)), 25.0)).filterNotNull()
        assertNull(estados.first().mediaKmh)
        assertNotNull(estados[8].mediaKmh)
        assertFalse(estados.first().terminado)
    }

    @Test
    fun `tramo con curvas mide la distancia recorrida y no la recta`() {
        // Sale hacia el este 900 m y vuelve hacia el fin: casi el doble de camino que la recta.
        val camino = listOf(enRecta(-300.0), pi(0.0, 0.0), pi(0.0, 900.0), fin.punto, enRecta(rectaM + 300.0))
        val final = medir(recorrer(camino, 20.0)).filterNotNull().last()
        assertTrue(final.terminado)
        // 20 m/s = 72 km/h; por la recta saldrian unos 41 km/h.
        assertEquals(72.0, checkNotNull(final.mediaKmh), 1.5)
    }

    @Test
    fun `avisa una vez cuando la media pasa del limite en la histeresis`() {
        val limite = ini.copy(limiteKmh = 50)
        val estados = medir(recorrer(listOf(enRecta(-400.0), enRecta(rectaM + 100.0)), 25.0), listOf(limite, fin))
        val frases = estados.filterNotNull().mapNotNull { it.frase?.id }.distinct()
        assertEquals(listOf("tramo-media-t-1"), frases)
        assertTrue(estados.filterNotNull().last().superaLimite)
    }

    @Test
    fun `en sentido contrario no arma`() {
        val estados = medir(recorrer(listOf(enRecta(rectaM + 400.0), enRecta(-300.0)), 25.0))
        assertTrue(estados.all { it == null })
    }

    @Test
    fun `el tramo del sentido contrario de la misma via arma desde el otro extremo`() {
        val ida = ini.copy(id = "u-ini", punto = fin.punto, tramoId = "u")
        val vuelta = fin.copy(id = "u-fin", punto = ini.punto, tramoId = "u")
        val estados =
            medir(recorrer(listOf(enRecta(rectaM + 400.0), enRecta(-300.0)), 25.0), listOf(ini, fin, ida, vuelta))
        val ultimo = estados.filterNotNull().last()
        assertEquals("u", ultimo.tramo.id)
        assertTrue(ultimo.terminado)
        assertEquals(90.0, checkNotNull(ultimo.mediaKmh), 0.5)
    }

    @Test
    fun `si ya se esta dentro del tramo al empezar no se mide`() {
        val estados = medir(recorrer(listOf(enRecta(5.0), enRecta(rectaM + 300.0)), 25.0))
        assertTrue(estados.all { it == null })
    }

    @Test
    fun `abandona si se aleja del fin y no cierra aunque pase despues por el fin`() {
        val camino = listOf(enRecta(-300.0), pi(0.0, 0.0), pi(-2500.0, 0.0), pi(-2500.0, 3000.0), fin.punto)
        val estados = medir(recorrer(camino, 20.0))
        assertTrue(estados.any { it != null && !it.terminado })
        assertTrue(estados.none { it?.terminado == true })
        assertNull(estados.last())
    }

    @Test
    fun `abandona si el recorrido pasa del doble de la recta mas 500 m sin alejarse del fin`() {
        // Va y viene entre 200 m y 700 m tras el inicio (nunca se aleja del fin mas que la recta).
        val a = enRecta(200.0)
        val b = enRecta(700.0)
        val camino = listOf(enRecta(-300.0), a, b, a, b, a, b, a, b, a, b, a, b, enRecta(rectaM + 300.0))
        val estados = medir(recorrer(camino, 20.0))
        assertTrue(estados.any { it != null && !it.terminado })
        assertTrue(estados.none { it?.terminado == true })
        assertNull(estados.last())
    }

    @Test
    fun `abandona si pasan mas de 30 minutos`() {
        val m = MediaTramoLibre(listOf(ini, fin))
        m.actualizar(Fijacion(enRecta(-100.0), 0L, 1.0, null, 5.0))
        assertNotNull(m.actualizar(Fijacion(enRecta(5.0), 5_000L, 1.0, null, 5.0)))
        // Se queda parado cerca del inicio mas de media hora, con fijaciones cada minuto.
        var t = 5_000L
        var ultimo: EstadoTramo? = null
        while (t < 5_000L + MediaTramoLibre.TIEMPO_MAX_MS + 120_000L) {
            t += 60_000L
            ultimo = m.actualizar(Fijacion(enRecta(10.0), t, 1.0, null, 5.0))
        }
        assertNull(ultimo)
    }

    @Test
    fun `un hueco largo entre fijaciones descarta la medida`() {
        val m = MediaTramoLibre(listOf(ini, fin))
        m.actualizar(Fijacion(enRecta(-100.0), 0L, 20.0, null, 5.0))
        assertNotNull(m.actualizar(Fijacion(enRecta(50.0), 7_000L, 20.0, null, 5.0)))
        val tras = m.actualizar(Fijacion(enRecta(300.0), 7_000L + MediaTramoLibre.HUECO_MAX_MS + 1, 20.0, null, 5.0))
        assertNull(tras)
    }

    @Test
    fun `sin tramos completos no hay estado`() {
        val soloInicio = medir(recorrer(listOf(enRecta(-400.0), enRecta(rectaM)), 25.0), listOf(ini))
        assertTrue(soloInicio.all { it == null })
    }
}
