package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

private const val METROS_POR_GRADO_LAT = 111_194.9

private fun p(
    este: Double,
    norte: Double,
) = PuntoGeo(
    39.6 + norte / METROS_POR_GRADO_LAT,
    2.9 + este / (METROS_POR_GRADO_LAT * cos(Math.toRadians(39.6))),
)

/** El reloj monótono de las fijaciones arranca en un valor sin relación con la hora de pared del test. */
private const val MONOTONO_BASE_MS = 5_000_000L

private fun fijacion(
    punto: PuntoGeo,
    indice: Int,
) = Fijacion(punto, MONOTONO_BASE_MS + indice * 1000L, 10.0, null, 5.0)

/** Fuente que emite cada fijación a 1 Hz de tiempo virtual y luego se queda a la espera (o termina). */
private class FuenteFalsa(
    private val puntos: List<PuntoGeo>,
    private val terminaAlAcabar: Boolean = false,
    private val permiso: Boolean = true,
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> =
        flow {
            for ((indice, punto) in puntos.withIndex()) {
                emit(fijacion(punto, indice))
                delay(1000)
            }
            if (!terminaAlAcabar) awaitCancellation()
        }

    override fun tienePermiso() = permiso
}

private class EnrutadorFalso(
    private val respuestas: MutableList<ResultadoRuta>,
    private val tardaMs: Long = 200,
    private val reloj: () -> Long,
) : Enrutador {
    val llamadas = mutableListOf<Triple<PuntoGeo, PuntoGeo, Long>>()

    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta {
        llamadas += Triple(origen, destino, reloj())
        delay(tardaMs)
        return respuestas.removeAt(0)
    }
}

private class RecorridosFalso(
    var enCurso: List<Long> = emptyList(),
) : RepositorioRecorridos {
    var destino: String? = "sin empezar"
    var empezado = false
    val lotes = mutableListOf<PuntoRecorrido>()
    val tamanos = mutableListOf<Int>()
    val terminados = mutableListOf<Long>()

    override suspend fun empezar(destinoNombre: String?): Long {
        empezado = true
        destino = destinoNombre
        return ID
    }

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) {
        assertEquals(ID, id)
        lotes += puntos
        tamanos += puntos.size
    }

    override suspend fun terminar(id: Long): Recorrido? {
        terminados += id
        return null
    }

    override suspend fun idsEnCurso() = enCurso

    override fun listar() = emptyFlow<List<Recorrido>>()

    override suspend fun puntos(id: Long) = emptyList<PuntoRecorrido>()

    override suspend fun borrar(id: Long) = Unit

    companion object {
        const val ID = 7L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SesionNavegacionTest {
    // L: 1000 m al este y 500 m al norte; giro a la izquierda en el vértice.
    private fun rutaL(
        origen: PuntoGeo = p(0.0, 0.0),
        esquina: PuntoGeo = p(1000.0, 0.0),
        fin: PuntoGeo = p(1000.0, 500.0),
    ) = Ruta(
        puntos = listOf(origen, esquina, fin),
        maniobras =
            listOf(
                Maniobra(TipoManiobra.SALIDA, "salida", emptyList(), 0, 1, 1000.0, 100.0, null),
                Maniobra(TipoManiobra.IZQUIERDA, "izquierda", emptyList(), 1, 2, 500.0, 50.0, null),
                Maniobra(TipoManiobra.DESTINO, "destino", emptyList(), 2, 2, 0.0, 0.0, null),
            ),
        longitudM = 1500.0,
        tiempoS = 150.0,
    )

    private val destino = Destino("Sóller", p(1000.0, 500.0))

    private class Escenario(
        val estados: MutableList<EstadoNavegacion>,
        val recorridos: RecorridosFalso,
        val enrutador: EnrutadorFalso,
        val sesion: SesionNavegacion,
    )

    private fun TestScope.escenario(
        fuente: FuenteUbicacion,
        respuestas: List<ResultadoRuta> = emptyList(),
        enCurso: List<Long> = emptyList(),
        horaDePared: () -> Long = { testScheduler.currentTime },
    ): Escenario {
        val estados = mutableListOf<EstadoNavegacion>()
        val recorridos = RecorridosFalso(enCurso)
        val enrutador = EnrutadorFalso(respuestas.toMutableList(), reloj = { testScheduler.currentTime })
        val sesion =
            SesionNavegacion(
                fuente,
                enrutador,
                recorridos,
                reloj = horaDePared,
                publicar = { estados += it },
                motivoSinGps = "sin GPS",
            )
        return Escenario(estados, recorridos, enrutador, sesion)
    }

    private fun CoroutineScope.lanzar(
        e: Escenario,
        solicitud: SolicitudNavegacion,
    ): Job = launch { e.sesion.correr(solicitud) }

    private fun guiada(ruta: Ruta = rutaL()) = SolicitudNavegacion.Guiada(destino, PerfilRuta.SINUOSA, ruta)

    @Test
    fun `modo libre graba por lotes y suma la distancia`() =
        runTest {
            // 25 posiciones separadas ~10 m al norte: 240 m en total.
            val puntos = List(25) { p(0.0, it * 10.0) }
            val e = escenario(FuenteFalsa(puntos))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(24_500)
            runCurrent()

            val ultimo = e.estados.last() as EstadoNavegacion.Libre
            assertEquals(240.0, ultimo.distanciaM, 5.0)
            assertEquals(0L, ultimo.desdeMs)
            assertEquals(listOf(10, 10), e.recorridos.tamanos)
            assertTrue(e.recorridos.terminados.isEmpty())

            trabajo.cancel()
            runCurrent()
            assertEquals(listOf(10, 10, 5), e.recorridos.tamanos)
            assertEquals(listOf(RecorridosFalso.ID), e.recorridos.terminados)
            assertEquals(null, e.recorridos.destino)
        }

    @Test
    fun `el ruido del gps parado no suma distancia`() =
        runTest {
            val puntos = List(10) { p(if (it % 2 == 0) 0.0 else 2.0, 0.0) }
            val e = escenario(FuenteFalsa(puntos))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(10_000)
            assertEquals(0.0, (e.estados.last() as EstadoNavegacion.Libre).distanciaM, 0.01)
            trabajo.cancel()
        }

    @Test
    fun `guiando actualiza el progreso sobre la ruta`() =
        runTest {
            val e = escenario(FuenteFalsa(listOf(p(0.0, 0.0), p(400.0, 0.0), p(800.0, 0.0))))
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(2_500)

            val ultimo = e.estados.last() as EstadoNavegacion.Guiando
            assertEquals(destino, ultimo.destino)
            assertEquals(PerfilRuta.SINUOSA, ultimo.perfil)
            assertFalse(ultimo.recalculando)
            val progreso = checkNotNull(ultimo.progreso)
            assertEquals(800.0, progreso.progresoM, 2.0)
            assertEquals(TipoManiobra.IZQUIERDA, progreso.maniobraActual?.tipo)
            assertEquals("Sóller", e.recorridos.destino)
            trabajo.cancel()
        }

    @Test
    fun `cinco segundos fuera de la ruta recalculan desde la posicion actual y adoptan la nueva`() =
        runTest {
            // 3 posiciones en la ruta y luego 200 m al sur, parado.
            val fuera = p(300.0, -200.0)
            val nueva = rutaL(origen = fuera, esquina = p(1000.0, -200.0), fin = p(1000.0, 500.0))
            val e =
                escenario(
                    FuenteFalsa(listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0)) + List(20) { fuera }),
                    listOf(ResultadoRuta.Ok(nueva, 50)),
                )
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(12_000)

            assertEquals(1, e.enrutador.llamadas.size)
            val (origen, hacia, cuando) = e.enrutador.llamadas.single()
            assertEquals(fuera, origen)
            assertEquals(destino.punto, hacia)
            assertEquals(8_000L, cuando) // fuera desde t=3 s; a los 5 s sostenidos, t=8 s
            assertTrue(e.estados.any { it is EstadoNavegacion.Guiando && it.recalculando })
            val ultimo = e.estados.last() as EstadoNavegacion.Guiando
            assertFalse(ultimo.recalculando)
            assertEquals(nueva, ultimo.ruta)
            trabajo.cancel()
        }

    @Test
    fun `un salto de la hora de pared no altera la deteccion de salida de ruta`() =
        runTest {
            val fuera = p(300.0, -200.0)
            val nueva = rutaL(origen = fuera, esquina = p(1000.0, -200.0), fin = p(1000.0, 500.0))
            var salto = 0L
            val e =
                escenario(
                    FuenteFalsa(listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0)) + List(20) { fuera }),
                    listOf(ResultadoRuta.Ok(nueva, 50)),
                    horaDePared = { testScheduler.currentTime + salto },
                )
            val trabajo = lanzar(e, guiada())
            // La hora de pared retrocede una hora justo después de salirse de la ruta (t = 3 s).
            launch {
                delay(3_500)
                salto = -3_600_000L
            }
            advanceTimeBy(12_000)

            assertEquals(1, e.enrutador.llamadas.size)
            val (_, _, cuando) = e.enrutador.llamadas.single()
            assertEquals(8_000L, cuando)
            assertEquals(nueva, (e.estados.last() as EstadoNavegacion.Guiando).ruta)
            trabajo.cancel()
        }

    @Test
    fun `si el recalculo falla sigue con la ruta vieja y reintenta a los 10 segundos`() =
        runTest {
            val fuera = p(300.0, -200.0)
            val nueva = rutaL(origen = fuera, esquina = p(1000.0, -200.0), fin = p(1000.0, 500.0))
            val vieja = rutaL()
            val e =
                escenario(
                    FuenteFalsa(listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0)) + List(40) { fuera }),
                    listOf(ResultadoRuta.Error("sin datos"), ResultadoRuta.Ok(nueva, 50)),
                )
            val trabajo = lanzar(e, guiada(vieja))
            advanceTimeBy(30_000)

            assertEquals(2, e.enrutador.llamadas.size)
            val primera = e.enrutador.llamadas[0].third
            val segunda = e.enrutador.llamadas[1].third
            assertTrue("reintento a los 10 s: $primera -> $segunda", segunda - primera in 10_000L..11_000L)
            val tras = e.estados.takeWhile { !(it is EstadoNavegacion.Guiando && it.ruta == nueva) }
            assertTrue(tras.filterIsInstance<EstadoNavegacion.Guiando>().all { it.ruta == vieja })
            assertEquals(nueva, (e.estados.last() as EstadoNavegacion.Guiando).ruta)
            trabajo.cancel()
        }

    @Test
    fun `al llegar publica Llegada, espera 10 segundos y cierra el recorrido`() =
        runTest {
            val e = escenario(FuenteFalsa(listOf(p(900.0, 0.0), p(1000.0, 400.0), p(1000.0, 495.0))))
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(2_500)

            assertEquals(EstadoNavegacion.Llegada(destino), e.estados.last())
            assertTrue(trabajo.isActive)
            assertTrue(e.recorridos.terminados.isEmpty())

            advanceTimeBy(10_000)
            runCurrent()
            assertTrue(trabajo.isCompleted)
            assertEquals(listOf(RecorridosFalso.ID), e.recorridos.terminados)
            assertEquals(3, e.recorridos.lotes.size)
        }

    @Test
    fun `terminar cancela y cierra el recorrido volcando lo pendiente`() =
        runTest {
            val e = escenario(FuenteFalsa(List(4) { p(0.0, it * 20.0) }))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(3_500)
            assertTrue(e.recorridos.lotes.isEmpty())

            trabajo.cancel()
            runCurrent()
            assertEquals(4, e.recorridos.lotes.size)
            assertEquals(listOf(RecorridosFalso.ID), e.recorridos.terminados)
        }

    @Test
    fun `si el gps deja de emitir publica Error y cierra el recorrido`() =
        runTest {
            val e = escenario(FuenteFalsa(listOf(p(0.0, 0.0)), terminaAlAcabar = true))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(EstadoNavegacion.Error("sin GPS"), e.estados.last())
            assertTrue(trabajo.isCompleted)
            assertEquals(listOf(RecorridosFalso.ID), e.recorridos.terminados)
        }

    @Test
    fun `cerrarHuerfanos termina los recorridos abiertos`() =
        runTest {
            val e = escenario(FuenteFalsa(emptyList()), enCurso = listOf(3L, 4L))
            e.sesion.cerrarHuerfanos()
            assertEquals(listOf(3L, 4L), e.recorridos.terminados)
            assertFalse(e.recorridos.empezado)
        }
}
