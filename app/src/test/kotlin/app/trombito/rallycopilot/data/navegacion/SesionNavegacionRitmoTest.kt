package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ritmo.ProveedorPerfilRitmo
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.IdVia
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.ruta.RitmoVia
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

private const val M_POR_GRADO_LAT = 111_194.9
private const val RELOJ_BASE_MS = 5_000_000L

private fun punto(
    este: Double,
    norte: Double = 0.0,
) = PuntoGeo(
    39.6 + norte / M_POR_GRADO_LAT,
    2.9 + este / (M_POR_GRADO_LAT * cos(Math.toRadians(39.6))),
)

/** Recta hacia el este de [longitudM] por la Ma-13 que Valhalla da a 36 km/h (0,1 s/m), con un solo tramo. */
private fun rutaRecta(
    longitudM: Double,
    origenNorte: Double = 0.0,
): Ruta =
    Ruta(
        puntos = listOf(punto(0.0, origenNorte), punto(longitudM, 0.0)),
        maniobras =
            listOf(
                Maniobra(TipoManiobra.SALIDA, "salida", listOf("Ma-13"), 0, 1, longitudM, longitudM * 0.1, null),
                Maniobra(TipoManiobra.DESTINO, "destino", emptyList(), 1, 1, 0.0, 0.0, null),
            ),
        longitudM = longitudM,
        tiempoS = longitudM * 0.1,
    )

/** Emite cada fijación con su instante monótono, a un ritmo de 1 ms de tiempo virtual entre ellas. */
private class FuenteDeInstantes(
    private val fijaciones: List<Fijacion>,
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> =
        flow {
            for (f in fijaciones) {
                emit(f)
                delay(1)
            }
            awaitCancellation()
        }

    override fun tienePermiso() = true
}

private class EnrutadorUnaVez(
    private val respuesta: Ruta,
) : Enrutador {
    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta = ResultadoRuta.Ok(respuesta, 10)
}

private class RecorridosQueGuardan : RepositorioRecorridos {
    val previstos = mutableListOf<Pair<Long, Double?>>()

    override suspend fun empezar(destinoNombre: String?) = ID

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) = Unit

    override suspend fun terminar(id: Long): Recorrido? = null

    override suspend fun guardarTiempoPrevisto(
        id: Long,
        segundos: Double?,
    ) {
        previstos += id to segundos
    }

    override suspend fun idsEnCurso() = emptyList<Long>()

    override fun listar() = emptyFlow<List<Recorrido>>()

    override suspend fun puntos(id: Long) = emptyList<PuntoRecorrido>()

    override suspend fun borrar(id: Long) = Unit

    companion object {
        const val ID = 11L
    }
}

private class PerfilFijo(
    private val perfil: PerfilRitmo,
) : ProveedorPerfilRitmo {
    override suspend fun perfil() = perfil
}

@OptIn(ExperimentalCoroutinesApi::class)
class SesionNavegacionRitmoTest {
    private val destino = Destino("Sóller", punto(30_000.0))

    private class Escenario(
        val estados: MutableList<EstadoNavegacion>,
        val recorridos: RecorridosQueGuardan,
        val sesion: SesionNavegacion,
    ) {
        /** El último estado de guiado con progreso. */
        fun progreso() = checkNotNull(estados.filterIsInstance<EstadoNavegacion.Guiando>().last().progreso)
    }

    private fun TestScope.escenario(
        fijaciones: List<Fijacion>,
        perfil: PerfilRitmo = PerfilRitmo(),
        enrutador: Enrutador = EnrutadorUnaVez(rutaRecta(30_000.0)),
    ): Escenario {
        val estados = mutableListOf<EstadoNavegacion>()
        val recorridos = RecorridosQueGuardan()
        val sesion =
            SesionNavegacion(
                FuenteDeInstantes(fijaciones),
                enrutador,
                recorridos,
                reloj = { testScheduler.currentTime },
                publicar = { estados += it },
                motivoSinGps = "sin GPS",
                perfilRitmo = PerfilFijo(perfil),
            )
        return Escenario(estados, recorridos, sesion)
    }

    private fun fijacion(
        este: Double,
        instanteS: Double,
        norte: Double = 0.0,
    ) = Fijacion(punto(este, norte), RELOJ_BASE_MS + (instanteS * 1_000).toLong(), 10.0, null, 5.0)

    private fun guiada(ruta: Ruta = rutaRecta(30_000.0)) =
        SolicitudNavegacion.Guiada(destino, PerfilRuta.MAS_RAPIDA, ruta)

    /** Una fijación cada 1 000 m en [km] intervalos, cada uno de [segundosPorKm] s de reloj. */
    private fun avanzando(
        km: Int,
        segundosPorKm: Double,
    ) = List(km + 1) { fijacion(it * 1_000.0, it * segundosPorKm) }

    @Test
    fun `sin historico la llegada es el tiempo de Valhalla por 1,12`() =
        runTest {
            val e = escenario(listOf(fijacion(0.0, 0.0)))
            val trabajo = launch { e.sesion.correr(guiada()) }
            advanceTimeBy(100)

            // 30 km a 36 km/h para Valhalla son 3 000 s; con el factor personal de partida, 3 360 s.
            assertEquals(3_360.0, e.progreso().tiempoRestanteS, 1.0)
            // La hora de pared de la primera fijación es 0 en el reloj virtual.
            assertEquals(3_360_000.0, e.progreso().llegadaMs.toDouble(), 1_500.0)
            trabajo.cancel()
        }

    @Test
    fun `con una via conocida y lenta la llegada se retrasa`() =
        runTest {
            val lenta = PerfilRitmo(mapOf(IdVia("Ma-13") to RitmoVia(25.0, pasadas = 3, km = 12.0)))
            val sinHistorico = escenario(listOf(fijacion(0.0, 0.0), fijacion(10.0, 1.0)))
            val conHistorico = escenario(listOf(fijacion(0.0, 0.0), fijacion(10.0, 1.0)), lenta)
            val a = launch { sinHistorico.sesion.correr(guiada()) }
            val b = launch { conHistorico.sesion.correr(guiada()) }
            advanceTimeBy(100)

            // 0,1 s/m a 25 km/h en vez de a 36: 0,144 s/m frente a 0,112 s/m del factor de partida.
            assertEquals(29_990.0 * 0.144, conHistorico.progreso().tiempoRestanteS, 2.0)
            assertTrue(conHistorico.progreso().tiempoRestanteS > sinHistorico.progreso().tiempoRestanteS)
            a.cancel()
            b.cancel()
        }

    @Test
    fun `yendo mas lento de lo previsto la llegada se retrasa y al ritmo previsto no`() =
        runTest {
            // 4 km: lo previsto son 112 s por km (0,1 s/m × 1,12). Lento = 200 s por km; a ritmo = 112 s por km.
            val lento = escenario(avanzando(4, 200.0))
            val aRitmo = escenario(avanzando(4, 112.0))
            val a = launch { lento.sesion.correr(guiada()) }
            val b = launch { aRitmo.sesion.correr(guiada()) }
            advanceTimeBy(100)

            val base = 26_000.0 * 0.112
            assertEquals(base, aRitmo.progreso().tiempoRestanteS, 1.0)
            // r = 200/112 = 1,79 -> acotado a 1,6; con 4 km el peso es 0,4: factor 1 + 0,4 × 0,6 = 1,24.
            assertEquals(base * 1.24, lento.progreso().tiempoRestanteS, 2.0)
            assertTrue(lento.progreso().llegadaMs > aRitmo.progreso().llegadaMs)
            a.cancel()
            b.cancel()
        }

    @Test
    fun `yendo mas deprisa de lo previsto la llegada se adelanta`() =
        runTest {
            val rapido = escenario(avanzando(4, 70.0))
            val a = launch { rapido.sesion.correr(guiada()) }
            advanceTimeBy(100)

            // r = 70/112 = 0,625 -> acotado a 0,7; factor 1 + 0,4 × (0,7 - 1) = 0,88.
            assertEquals(26_000.0 * 0.112 * 0.88, rapido.progreso().tiempoRestanteS, 2.0)
            a.cancel()
        }

    @Test
    fun `el ritmo del viaje se conserva al recalcular la ruta`() =
        runTest {
            val fuera = punto(4_000.0, 300.0)
            val nueva = rutaRecta(30_000.0).copy(puntos = listOf(fuera, punto(30_000.0)))
            val fijaciones =
                avanzando(4, 200.0) +
                    List(4) { fijacion(4_000.0, 801.0 + it, norte = 300.0) }
            val e = escenario(fijaciones, enrutador = EnrutadorUnaVez(nueva))
            val trabajo = launch { e.sesion.correr(guiada()) }
            advanceTimeBy(200)
            runCurrent()

            val ultima = e.estados.filterIsInstance<EstadoNavegacion.Guiando>().last { it.ruta == nueva }
            val restante = checkNotNull(ultima.progreso).tiempoRestanteS
            // La ruta nueva tarda lo que dice Valhalla (3 360 s con el perfil de partida) y sigue el factor 1,24 del
            // viaje: no vuelve a 1.
            assertTrue("restante $restante", restante > 26_000.0 * 0.112 * 1.2)
            trabajo.cancel()
        }

    @Test
    fun `guiando guarda el tiempo de Valhalla de la ruta inicial en el recorrido`() =
        runTest {
            val ruta = rutaRecta(30_000.0)
            val e = escenario(listOf(fijacion(0.0, 0.0)), perfil = PerfilRitmo(factorPersonal = 1.3))
            val trabajo = launch { e.sesion.correr(guiada(ruta)) }
            advanceTimeBy(100)

            // Valhalla puro: sin factor personal ni histórico.
            assertEquals(listOf(RecorridosQueGuardan.ID to ruta.tiempoS), e.recorridos.previstos)
            trabajo.cancel()
        }

    @Test
    fun `en modo libre no se guarda tiempo previsto`() =
        runTest {
            val e = escenario(listOf(fijacion(0.0, 0.0)))
            val trabajo = launch { e.sesion.correr(SolicitudNavegacion.Libre) }
            advanceTimeBy(100)

            assertTrue(e.recorridos.previstos.isEmpty())
            assertNull(e.estados.filterIsInstance<EstadoNavegacion.Guiando>().lastOrNull())
            assertNotNull(e.estados.lastOrNull())
            trabajo.cancel()
        }
}
