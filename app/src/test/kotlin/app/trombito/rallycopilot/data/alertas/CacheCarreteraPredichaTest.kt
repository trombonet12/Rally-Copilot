package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.motor.alertas.Incidencia
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** La caché de la carretera predicha se compara por identidad de traza, radares e incidencias y por antigüedad. */
class CacheCarreteraPredichaTest {
    private val metrosPorGrado = PuntoGeo.RADIO_TIERRA_M * Math.PI / 180.0

    private fun punto(norte: Double) = PuntoGeo(39.6 + norte / metrosPorGrado, 2.9)

    private val traza = (0..4).map { punto(it * 500.0) }
    private val radares = listOf(Radar("r1", TipoRadar.FIJO, punto(1000.0), null, 80, null, "test"))
    private val incidencias =
        listOf(
            Incidencia("i1", TipoIncidencia.OBRAS, listOf(punto(1200.0)), "Ma-13", "Obras", null, null, null),
        )
    private val cache = CacheCarreteraPredicha()

    @Test
    fun `los mismos datos dentro del minuto reutilizan la carretera`() {
        val primera = cache.obtener(traza, radares, incidencias, 1_000L)
        assertSame(primera, cache.obtener(traza, radares, incidencias, 1_000L))
        assertSame(primera, cache.obtener(traza, radares, incidencias, 1_000L + CacheCarreteraPredicha.REFILTRO_MS - 1))
    }

    @Test
    fun `cambiar de via invalida la caja con otra traza y otro radar de alertas`() {
        val primera = cache.obtener(traza, radares, incidencias, 0L)
        val otraVia = (0..4).map { punto(it * 500.0 + 10.0) }
        val segunda = cache.obtener(otraVia, radares, incidencias, 0L)
        assertNotSame(primera, segunda)
        assertSame(otraVia, segunda.traza)
        assertNotSame(primera.radar, segunda.radar)
    }

    @Test
    fun `cambiar de sentido invalida aunque la geometria sea la misma`() {
        val primera = cache.obtener(traza, radares, incidencias, 0L)
        val contraria = traza.reversed()
        val segunda = cache.obtener(contraria, radares, incidencias, 0L)
        assertNotSame(primera, segunda)
        assertEquals(traza.reversed(), segunda.traza)
        // Y volver a la original ya no es la de antes: la caché guarda solo la última.
        assertNotSame(primera, cache.obtener(traza, radares, incidencias, 0L))
    }

    @Test
    fun `radares nuevos rehacen tambien el calculo de radares`() {
        val primera = cache.obtener(traza, radares, incidencias, 0L)
        val segunda = cache.obtener(traza, radares + radares.first().copy(id = "r2"), incidencias, 0L)
        assertNotSame(primera, segunda)
        assertNotSame(primera.radar, segunda.radar)
    }

    @Test
    fun `incidencias nuevas refiltran pero conservan los radares ya colocados`() {
        val primera = cache.obtener(traza, radares, incidencias, 0L)
        val segunda = cache.obtener(traza, radares, incidencias.toList(), 0L)
        assertNotSame(primera, segunda)
        assertNotSame(primera.filtro, segunda.filtro)
        assertSame(primera.radar, segunda.radar)
    }

    @Test
    fun `al minuto se refiltran las incidencias para que caduquen y se conservan los radares`() {
        val primera = cache.obtener(traza, radares, incidencias, 0L)
        val segunda = cache.obtener(traza, radares, incidencias, CacheCarreteraPredicha.REFILTRO_MS)
        assertNotSame(primera, segunda)
        assertSame(primera.radar, segunda.radar)
        assertEquals(CacheCarreteraPredicha.REFILTRO_MS, segunda.construidaMs)
    }

    @Test
    fun `un reloj que retrocede no reutiliza una carretera construida en el futuro`() {
        val primera = cache.obtener(traza, radares, incidencias, 100_000L)
        val segunda = cache.obtener(traza, radares, incidencias, 50_000L)
        assertNotSame(primera, segunda)
        assertEquals(50_000L, segunda.construidaMs)
    }
}
