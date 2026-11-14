package app.trombito.rallycopilot.data.carreteras

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Metros por grado de longitud a la latitud 39,6. */
private const val METROS_POR_GRADO_LON = 85_600.0

/** Línea hacia el este por la latitud 39,6 con un punto cada [pasoM] metros. */
internal fun lineaEste(
    desdeM: Int,
    hastaM: Int,
    pasoM: Int = 10,
): List<PuntoGeo> = (desdeM..hastaM step pasoM).map { PuntoGeo(39.6, 2.9 + it / METROS_POR_GRADO_LON) }

/** Recorridos en memoria con los puntos de cada uno; cuenta cuántas veces se leen. */
internal class RecorridosFalsos(
    trazas: Map<Long, List<PuntoGeo>>,
    /** Milisegundos entre dos puntos seguidos; 1 000 con puntos cada 10 m son 36 km/h. */
    private val pasoMs: Long = 1_000L,
) : RepositorioRecorridos {
    val trazas = trazas.toMutableMap()
    val lista = MutableStateFlow(trazas.keys.map { Recorrido(it, it, it + 1, 2_000.0, null) })
    var lecturas = 0
    var fallo: Throwable? = null

    override suspend fun empezar(destinoNombre: String?) = 0L

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) = Unit

    override suspend fun terminar(id: Long): Recorrido? = null

    override suspend fun idsEnCurso() = emptyList<Long>()

    override fun listar(): Flow<List<Recorrido>> = lista

    override suspend fun puntos(id: Long): List<PuntoRecorrido> {
        fallo?.let { throw it }
        lecturas++
        return trazas.getValue(id).mapIndexed { i, p -> PuntoRecorrido(p.lat, p.lon, i * pasoMs) }
    }

    override suspend fun borrar(id: Long) = Unit
}

/** Fuente de vías con las dadas, o sin datos si es null. */
internal fun viasFalsas(vias: List<Via>?) =
    object : FuenteVias {
        // La misma instancia en cada llamada, como el cargador real: la caché depende de ello.
        private val datos = vias?.let { DatosVias(it, IndiceVias(it)) }

        override suspend fun datos() = datos
    }

/** Vía recta hacia el este entre [desdeM] y [hastaM] metros, con un punto cada 100 m. */
internal fun viaFalsa(
    id: Long,
    ref: String?,
    desdeM: Int,
    hastaM: Int,
) = Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, null, lineaEste(desdeM, hastaM, 100))

/** Velocidad media por vía que calcula [CalculadorCarreteras] al emitir. */
private suspend fun CalculadorCarreteras.velocidades() = observar().first().velocidades

class CalculadorCarreterasTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)

    @Test
    fun `cuenta recorridos, kilometros y pasadas por via`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(50, 950)))
            val calculador = CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler))
            val resultado = calculador.observar().first()
            assertEquals(2, resultado.resumen.recorridos)
            assertEquals(1_800.0, resultado.resumen.metrosTotales, 20.0)
            val via = resultado.resumen.vias.single()
            assertEquals(2, via.pasadas)
            assertTrue(resultado.hayVias)
        }

    @Test
    fun `sin vias instaladas sigue habiendo kilometros`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val calculador = CalculadorCarreteras(repo, viasFalsas(null), StandardTestDispatcher(testScheduler))
            val resultado = calculador.observar().first()
            assertFalse(resultado.hayVias)
            assertEquals(900.0, resultado.resumen.metrosTotales, 10.0)
            assertEquals(0, resultado.resumen.vias.size)
        }

    @Test
    fun `no vuelve a leer los puntos de un recorrido ya analizado`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val calculador = CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler))
            calculador.observar().first()
            calculador.observar().first()
            assertEquals(1, repo.lecturas)
        }

    @Test
    fun `calcula la velocidad media en marcha de cada via y suma las pasadas`() =
        runTest {
            // Puntos cada 10 m y 1 s: 36 km/h; con puntos cada 0,5 s serían 72 km/h.
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val calculador = CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler))
            val via = calculador.velocidades().single()
            assertEquals(ma10.id, via.via.id)
            assertEquals(1, via.pasadas)
            assertEquals(36.0, via.velMediaKmh!!, 0.5)
            assertEquals(0.9, via.km, 0.02)
        }

    @Test
    fun `el doble de ritmo da el doble de velocidad media`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)), pasoMs = 500L)
            val calculador = CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler))
            assertEquals(72.0, calculador.velocidades().single().velMediaKmh!!, 1.0)
        }

    @Test
    fun `dos pasadas por la via suman pasadas y kilometros sin cambiar la media`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(0, 900)))
            val calculador = CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler))
            val via = calculador.velocidades().single()
            assertEquals(2, via.pasadas)
            assertEquals(36.0, via.velMediaKmh!!, 0.5)
            assertEquals(1.8, via.km, 0.04)
        }

    @Test
    fun `sin vias no hay velocidades`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val calculador = CalculadorCarreteras(repo, viasFalsas(null), StandardTestDispatcher(testScheduler))
            assertTrue(calculador.velocidades().isEmpty())
        }

    @Test
    fun `un recorrido parado no suma velocidad y uno ya analizado no se lee dos veces`() =
        runTest {
            // Un punto cada 10 m pero cada 60 s: hueco de más de 30 s, no cuenta como marcha.
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)), pasoMs = 60_000L)
            val calculador = CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler))
            assertTrue(calculador.velocidades().isEmpty())
            calculador.observar().first()
            assertEquals(1, repo.lecturas)
        }
}
