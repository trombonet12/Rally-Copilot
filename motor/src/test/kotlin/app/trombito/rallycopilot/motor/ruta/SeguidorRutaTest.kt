package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class SeguidorRutaTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun m(
        tipo: TipoManiobra,
        inicio: Int,
        fin: Int,
        longitudM: Double,
        tiempoS: Double,
    ) = Maniobra(tipo, tipo.name, emptyList(), inicio, fin, longitudM, tiempoS, null)

    // L: 1000 m al este y 500 m al norte; giro a la izquierda en el vértice.
    private val ruta =
        Ruta(
            puntos = listOf(p(0.0, 0.0), p(1000.0, 0.0), p(1000.0, 500.0)),
            maniobras =
                listOf(
                    m(TipoManiobra.SALIDA, 0, 1, 1000.0, 100.0),
                    m(TipoManiobra.IZQUIERDA, 1, 2, 500.0, 50.0),
                    m(TipoManiobra.DESTINO, 2, 2, 0.0, 0.0),
                ),
            longitudM = 1500.0,
            tiempoS = 150.0,
        )

    @Test
    fun `al inicio la maniobra actual es el primer giro, no la salida`() {
        val e = SeguidorRuta(ruta).actualizar(p(0.0, 0.0), 1_000_000)
        assertEquals(1500.0, e.distanciaRestanteM, 1.0)
        assertEquals(150.0, e.tiempoRestanteS, 0.1)
        assertEquals(TipoManiobra.IZQUIERDA, e.maniobraActual?.tipo)
        assertEquals(1000.0, e.distanciaAManiobraM, 1.0)
        assertEquals(TipoManiobra.DESTINO, e.maniobraSiguiente?.tipo)
        assertEquals(0.0, e.distanciaALaRutaM, 0.5)
        assertFalse(e.llegado)
    }

    @Test
    fun `una posicion ligeramente detras del inicio no anuncia la salida ni una maniobra a 0 m`() {
        // Ruta recalculada: la salida (0..1) y el primer giro real a 300 m; el conductor ya iba 15 m por delante.
        val nueva =
            Ruta(
                puntos = listOf(p(0.0, 0.0), p(300.0, 0.0), p(300.0, 500.0)),
                maniobras =
                    listOf(
                        m(TipoManiobra.SALIDA, 0, 1, 300.0, 30.0),
                        m(TipoManiobra.DERECHA, 1, 2, 500.0, 50.0),
                        m(TipoManiobra.DESTINO, 2, 2, 0.0, 0.0),
                    ),
                longitudM = 800.0,
                tiempoS = 80.0,
            )
        val e = SeguidorRuta(nueva).actualizar(p(-15.0, 0.0), 0)
        assertEquals(TipoManiobra.DERECHA, e.maniobraActual?.tipo)
        assertEquals(300.0, e.distanciaAManiobraM, 1.0)
    }

    @Test
    fun `si la unica maniobra antes del destino es la salida, al inicio la actual es el destino`() {
        val soloSalida =
            ruta.copy(
                maniobras =
                    listOf(
                        m(TipoManiobra.SALIDA, 0, 1, 1000.0, 100.0),
                        m(TipoManiobra.DESTINO, 2, 2, 0.0, 0.0),
                    ),
            )
        val e = SeguidorRuta(soloSalida).actualizar(p(0.0, 0.0), 0)
        assertEquals(TipoManiobra.DESTINO, e.maniobraActual?.tipo)
        assertEquals(1500.0, e.distanciaAManiobraM, 1.0)
        assertNull(e.maniobraSiguiente)
    }

    @Test
    fun `en el primer tramo calcula restantes eta y maniobras`() {
        val e = SeguidorRuta(ruta).actualizar(p(400.0, 0.0), 1_000_000)
        assertEquals(1100.0, e.distanciaRestanteM, 1.0)
        assertEquals(110.0, e.tiempoRestanteS, 0.1)
        assertEquals(1_110_000.0, e.llegadaMs.toDouble(), 200.0)
        assertEquals(TipoManiobra.IZQUIERDA, e.maniobraActual?.tipo)
        assertEquals(600.0, e.distanciaAManiobraM, 1.0)
        assertEquals(TipoManiobra.DESTINO, e.maniobraSiguiente?.tipo)
        assertFalse(e.llegado)
    }

    @Test
    fun `tras el giro la maniobra pendiente es el destino`() {
        val e = SeguidorRuta(ruta).actualizar(p(1000.0, 200.0), 0)
        assertEquals(300.0, e.distanciaRestanteM, 1.0)
        assertEquals(30.0, e.tiempoRestanteS, 0.1)
        assertEquals(TipoManiobra.DESTINO, e.maniobraActual?.tipo)
        assertEquals(300.0, e.distanciaAManiobraM, 1.0)
        assertNull(e.maniobraSiguiente)
    }

    @Test
    fun `llega a menos de 30 m del final`() {
        val seguidor = SeguidorRuta(ruta)
        assertFalse(seguidor.actualizar(p(1000.0, 460.0), 0).llegado)
        val e = seguidor.actualizar(p(1000.0, 475.0), 1000)
        assertTrue(e.llegado)
        assertEquals(25.0, e.distanciaRestanteM, 1.0)
    }

    @Test
    fun `distancia a la ruta lateral`() {
        val e = SeguidorRuta(ruta).actualizar(p(400.0, 60.0), 0)
        assertEquals(60.0, e.distanciaALaRutaM, 0.5)
        assertEquals(1100.0, e.distanciaRestanteM, 1.0)
        assertFalse(e.llegado)
    }

    @Test
    fun `tolera retroceder unos metros pero no saltar atras en la misma carretera`() {
        val seguidor = SeguidorRuta(ruta)
        seguidor.actualizar(p(800.0, 0.0), 0)
        assertEquals(1500.0 - 790.0, seguidor.actualizar(p(790.0, 0.0), 1000).distanciaRestanteM, 1.0)
    }

    // Horquilla: ida 1000 m al este, 20 m al norte y vuelta al oeste por una calle paralela.
    private val horquilla =
        Ruta(
            puntos = listOf(p(0.0, 0.0), p(1000.0, 0.0), p(1000.0, 20.0), p(0.0, 20.0)),
            maniobras =
                listOf(
                    m(TipoManiobra.SALIDA, 0, 1, 1000.0, 100.0),
                    m(TipoManiobra.IZQUIERDA, 1, 2, 20.0, 2.0),
                    m(TipoManiobra.IZQUIERDA, 2, 3, 1000.0, 100.0),
                    m(TipoManiobra.DESTINO, 3, 3, 0.0, 0.0),
                ),
            longitudM = 2020.0,
            tiempoS = 202.0,
        )

    @Test
    fun `no salta al tramo de vuelta que pasa a 20 m`() {
        val seguidor = SeguidorRuta(horquilla)
        seguidor.actualizar(p(600.0, 0.0), 0)
        // (600, 20) está sobre la calle de vuelta (a 0 m) pero muy lejos por delante en la ruta.
        val e = seguidor.actualizar(p(600.0, 20.0), 1000)
        assertEquals(2020.0 - 600.0, e.distanciaRestanteM, 1.0)
        assertEquals(20.0, e.distanciaALaRutaM, 0.5)
    }

    @Test
    fun `acepta un salto cuando la posicion vuelve a caer sobre la ruta lejos de donde estaba`() {
        val seguidor = SeguidorRuta(horquilla)
        seguidor.actualizar(p(600.0, 0.0), 0)
        // Se reincorpora por la calle de vuelta a 200 m de su inicio: 1820 m recorridos.
        val e = seguidor.actualizar(p(200.0, 20.0), 120_000)
        assertEquals(2020.0 - 1820.0, e.distanciaRestanteM, 1.0)
        assertEquals(0.0, e.distanciaALaRutaM, 0.5)
        assertEquals(TipoManiobra.DESTINO, e.maniobraActual?.tipo)
    }

    @Test
    fun `la primera posicion busca en toda la ruta`() {
        val e = SeguidorRuta(horquilla).actualizar(p(300.0, 20.0), 0)
        assertEquals(2020.0 - 1720.0, e.distanciaRestanteM, 1.0)
    }

    // Recta de 3000 m al este para recorrerla hacia atrás.
    private val recta =
        Ruta(
            puntos = listOf(p(0.0, 0.0), p(3000.0, 0.0)),
            maniobras = listOf(m(TipoManiobra.DESTINO, 1, 1, 0.0, 0.0)),
            longitudM = 3000.0,
            tiempoS = 300.0,
        )

    @Test
    fun `avanzar nunca marca sentido contrario`() {
        val seguidor = SeguidorRuta(recta)
        for (i in 0..100) assertFalse(seguidor.actualizar(p(i * 17.0, 0.0), i * 1000L).sentidoContrario)
    }

    @Test
    fun `recorrer la ruta hacia atras a 60 kmh se marca antes de 100 m de retroceso`() {
        val seguidor = SeguidorRuta(recta)
        for (i in 0..90) seguidor.actualizar(p(i * 17.0, 0.0), i * 1000L)
        val maximo = 90 * 17.0
        var retroceso = 0.0
        var marcado = -1.0
        for (paso in 1..20) {
            retroceso = paso * 17.0
            val e = seguidor.actualizar(p(maximo - retroceso, 0.0), (90 + paso) * 1000L)
            if (e.sentidoContrario && marcado < 0) marcado = retroceso
            // Hasta superar 50 m de retroceso no se marca.
            if (retroceso <= 50.0) assertFalse("retroceso $retroceso", e.sentidoContrario)
        }
        assertTrue("marcado a $marcado m", marcado in 50.0..100.0)
    }

    @Test
    fun `el jitter de gps de mas menos 10 m no marca sentido contrario`() {
        val seguidor = SeguidorRuta(recta)
        var este = 500.0
        for (i in 0..200) {
            este += 17.0.coerceAtMost(3000.0 - este - 20.0)
            val jitter = if (i % 2 == 0) 10.0 else -10.0
            assertFalse(seguidor.actualizar(p(este + jitter, 0.0), i * 1000L).sentidoContrario)
        }
    }

    @Test
    fun `parado con jitter de 10 m tampoco marca sentido contrario`() {
        val seguidor = SeguidorRuta(recta)
        seguidor.actualizar(p(1000.0, 0.0), 0)
        for (i in 1..50) {
            val este = 1000.0 + if (i % 2 == 0) 10.0 else -10.0
            assertFalse(seguidor.actualizar(p(este, 0.0), i * 1000L).sentidoContrario)
        }
    }

    @Test
    fun `un retroceso breve de 20 m no marca sentido contrario y volver a avanzar tampoco`() {
        val seguidor = SeguidorRuta(recta)
        seguidor.actualizar(p(1000.0, 0.0), 0)
        assertFalse(seguidor.actualizar(p(990.0, 0.0), 1000).sentidoContrario)
        assertFalse(seguidor.actualizar(p(980.0, 0.0), 2000).sentidoContrario)
        assertFalse(seguidor.actualizar(p(1010.0, 0.0), 3000).sentidoContrario)
        assertFalse(seguidor.actualizar(p(1100.0, 0.0), 4000).sentidoContrario)
    }

    @Test
    fun `el sentido contrario se quita al volver cerca del progreso maximo`() {
        val seguidor = SeguidorRuta(recta)
        for (i in 0..60) seguidor.actualizar(p(1000.0 + i * 17.0, 0.0), i * 1000L)
        val maximo = 1000.0 + 60 * 17.0
        var e = seguidor.actualizar(p(maximo, 0.0), 61_000)
        for (paso in 1..5) e = seguidor.actualizar(p(maximo - paso * 17.0, 0.0), (61 + paso) * 1000L)
        assertTrue(e.sentidoContrario)
        e = seguidor.actualizar(p(maximo - 10.0, 0.0), 70_000)
        assertFalse(e.sentidoContrario)
    }
}
