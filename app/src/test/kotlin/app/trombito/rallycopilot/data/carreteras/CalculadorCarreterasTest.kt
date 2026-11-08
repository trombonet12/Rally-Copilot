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
        return trazas.getValue(id).mapIndexed { i, p -> PuntoRecorrido(p.lat, p.lon, i * 1_000L) }
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
}
