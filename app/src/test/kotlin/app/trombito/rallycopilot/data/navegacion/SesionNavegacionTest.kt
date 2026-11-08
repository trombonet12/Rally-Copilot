package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.alertas.IncidenciasFalso
import app.trombito.rallycopilot.data.alertas.RadaresFalso
import app.trombito.rallycopilot.data.alertas.SesionAlertas
import app.trombito.rallycopilot.data.alertas.ViaFalsa
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.tramos.SesionCrono
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.distanciaRecorrido
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** Fuente con fijaciones ya hechas (para fijar la precisión de cada una), a 1 Hz de tiempo virtual. */
private class FuenteDeFijaciones(
    private val fijaciones: List<Fijacion>,
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> =
        flow {
            for (f in fijaciones) {
                emit(f)
                delay(1000)
            }
            awaitCancellation()
        }

    override fun tienePermiso() = true
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

private class LocutorDeSesion : Locutor {
    override val hablando: StateFlow<Boolean> = MutableStateFlow(false)
    val dichas = mutableListOf<String>()

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun preparar(textos: List<String>) = Unit

    override fun decir(
        texto: String,
        prioridad: PrioridadVoz,
        caducaMs: Long?,
    ) {
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String) = 1.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
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

    @Suppress("LongParameterList") // cada prueba fija solo lo que le importa; el resto va por defecto
    private fun TestScope.escenario(
        fuente: FuenteUbicacion,
        respuestas: List<ResultadoRuta> = emptyList(),
        enCurso: List<Long> = emptyList(),
        horaDePared: () -> Long = { testScheduler.currentTime },
        alertas: SesionAlertas? = null,
        copiloto: CopilotoVoz? = null,
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
                copiloto = copiloto,
                alertas = alertas,
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
            // 70 posiciones separadas ~10 m al norte: 690 m en total; el lote es de 30 puntos.
            val puntos = List(70) { p(0.0, it * 10.0) }
            val e = escenario(FuenteFalsa(puntos))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(69_500)
            runCurrent()

            val ultimo = e.estados.last() as EstadoNavegacion.Libre
            assertEquals(690.0, ultimo.distanciaM, 5.0)
            assertEquals(0L, ultimo.desdeMs)
            assertEquals(listOf(30, 30), e.recorridos.tamanos)
            assertTrue(e.recorridos.terminados.isEmpty())

            trabajo.cancel()
            runCurrent()
            // Al terminar se vuelcan siempre los puntos pendientes, aunque no llenen un lote.
            assertEquals(listOf(30, 30, 10), e.recorridos.tamanos)
            assertEquals(listOf(RecorridosFalso.ID), e.recorridos.terminados)
            assertEquals(null, e.recorridos.destino)
        }

    @Test
    fun `las fijaciones imprecisas de mas de 30 m no se graban ni suman distancia`() =
        runTest {
            // Interior: 5 posiciones aproximadas (80 m) que saltan 50 m; luego 5 buenas (5 m) separadas 10 m.
            val aproximadas = List(5) { fijacion(p(0.0, it * 50.0), it).copy(precisionM = 80.0) }
            val buenas = List(5) { fijacion(p(0.0, 1000.0 + it * 10.0), 5 + it) }
            val e = escenario(FuenteDeFijaciones(aproximadas + buenas))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(9_500)
            runCurrent()

            assertEquals(40.0, (e.estados.last() as EstadoNavegacion.Libre).distanciaM, 1.0)
            trabajo.cancel()
            runCurrent()
            assertEquals(5, e.recorridos.lotes.size)
            assertTrue(e.recorridos.lotes.all { it.lat >= p(0.0, 999.0).lat })
        }

    @Test
    fun `una fijacion justo en 30 m o sin precision si se usa`() =
        runTest {
            val limite = fijacion(p(0.0, 0.0), 0).copy(precisionM = 30.0)
            val sinPrecision = fijacion(p(0.0, 100.0), 1).copy(precisionM = null)
            val e = escenario(FuenteDeFijaciones(listOf(limite, sinPrecision)))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(1_500)
            runCurrent()
            trabajo.cancel()
            runCurrent()

            assertEquals(2, e.recorridos.lotes.size)
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
    fun `sesenta fijaciones paradas con ruido de 3 m graban un punto y 0 m`() =
        runTest {
            val ruido = listOf(0.0 to 0.0, 3.0 to 0.0, -3.0 to 0.0, 0.0 to 3.0, 0.0 to -3.0)
            val puntos = List(60) { p(ruido[it % ruido.size].first, ruido[it % ruido.size].second) }
            val e = escenario(FuenteFalsa(puntos))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(60_000)
            assertEquals(0.0, (e.estados.last() as EstadoNavegacion.Libre).distanciaM, 0.0)
            trabajo.cancel()
            runCurrent()

            assertEquals(1, e.recorridos.lotes.size)
        }

    @Test
    fun `ir y parar la distancia de la notificacion y la guardada coinciden`() =
        runTest {
            val ruido = listOf(0.0, 3.0, -2.0, 1.0)
            val puntos = List(20) { p(0.0, it * 10.0) } + List(40) { p(ruido[it % ruido.size], 190.0) }
            val e = escenario(FuenteFalsa(puntos))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(60_000)
            val notificada = (e.estados.last() as EstadoNavegacion.Libre).distanciaM
            trabajo.cancel()
            runCurrent()

            assertEquals(190.0, notificada, 3.0)
            assertEquals(20, e.recorridos.lotes.size)
            val guardados = e.recorridos.lotes.map { PuntoGeo(it.lat, it.lon) }
            assertEquals(notificada, guardados.distanciaRecorrido(), 1e-6)
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
    fun `dos segundos fuera de la ruta recalculan desde la posicion actual y adoptan la nueva`() =
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
            assertEquals(5_000L, cuando) // fuera desde t=3 s; a los 2 s sostenidos, t=5 s
            assertTrue(e.estados.any { it is EstadoNavegacion.Guiando && it.recalculando })
            val ultimo = e.estados.last() as EstadoNavegacion.Guiando
            assertFalse(ultimo.recalculando)
            assertEquals(nueva, ultimo.ruta)
            trabajo.cancel()
        }

    @Test
    fun `al adoptar la ruta recalculada nunca se publica sin progreso`() =
        runTest {
            // Regresión: sin progreso el banner enseña la primera maniobra de la ruta nueva (la salida, a 0 m).
            val fuera = p(300.0, -200.0)
            val nueva = rutaL(origen = fuera, esquina = p(1000.0, -200.0), fin = p(1000.0, 500.0))
            val e =
                escenario(
                    FuenteFalsa(listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0)) + List(20) { fuera }),
                    listOf(ResultadoRuta.Ok(nueva, 50)),
                )
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(12_000)

            val conNueva = e.estados.filterIsInstance<EstadoNavegacion.Guiando>().filter { it.ruta == nueva }
            assertTrue(conNueva.isNotEmpty())
            assertTrue("estados con la ruta nueva y sin progreso", conNueva.all { it.progreso != null })
            trabajo.cancel()
        }

    @Test
    fun `circular en sentido contrario por la ruta recalcula desde la posicion actual`() =
        runTest {
            // 51 posiciones avanzando cada 17 m (60 km/h) y luego marcha atrás por la misma carretera.
            val avance = List(51) { p(it * 17.0, 0.0) }
            val atras = List(4) { p(850.0 - (it + 1) * 17.0, 0.0) }
            val origenNuevo = p(850.0 - 4 * 17.0, 0.0)
            val nueva = rutaL(origen = origenNuevo)
            val e = escenario(FuenteFalsa(avance + atras), listOf(ResultadoRuta.Ok(nueva, 50)))
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(70_000)

            assertEquals(1, e.enrutador.llamadas.size)
            val (origen, _, cuando) = e.enrutador.llamadas.single()
            assertEquals(origenNuevo, origen)
            // Marcado a los 51 m de retroceso (3.ª posición hacia atrás, t=53 s) y confirmado 1 s después.
            assertEquals(54_000L, cuando)
            assertEquals(nueva, (e.estados.last() as EstadoNavegacion.Guiando).ruta)
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
            assertEquals(5_000L, cuando)
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
    fun `cancelar mientras se lee cronometrar trayectos no deja el recorrido abierto`() =
        runTest {
            val recorridos = RecorridosFalso()
            val sesion =
                SesionNavegacion(
                    FuenteFalsa(listOf(p(0.0, 0.0))),
                    EnrutadorFalso(mutableListOf(), reloj = { testScheduler.currentTime }),
                    recorridos,
                    reloj = { testScheduler.currentTime },
                    publicar = {},
                    motivoSinGps = "sin GPS",
                    crono = SesionCrono(FalsoRepositorioTramos(), {}, { testScheduler.currentTime }),
                    cronometrar = { awaitCancellation() },
                )
            val trabajo = launch { sesion.correr(SolicitudNavegacion.Libre) }
            runCurrent()
            assertTrue(recorridos.empezado)
            assertTrue(recorridos.terminados.isEmpty())

            trabajo.cancel()
            runCurrent()
            assertTrue(trabajo.isCompleted)
            assertEquals(listOf(RecorridosFalso.ID), recorridos.terminados)
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

    private fun copilotoCon(
        locutor: Locutor,
        modo: ModoCopiloto,
    ) = CopilotoVoz(locutor, flowOf(AjustesConduccion(modoCopiloto = modo, voz = VozCopiloto.DAVEFX)))

    private fun sesionAlertas(
        publicadas: MutableList<AlertasEnCurso?>,
        radares: List<Radar> = emptyList(),
        via: ViaActual? = null,
    ) = SesionAlertas(RadaresFalso(radares), IncidenciasFalso(), ViaFalsa(via), { publicadas += it }, { 0L })

    @Test
    fun `guiando las alertas de radar se publican y suenan en solo alertas, y al terminar se publican vacias`() =
        runTest {
            val radar = Radar("f1", TipoRadar.FIJO, p(600.0, 0.0), null, 80, null, "test")
            val publicadas = mutableListOf<AlertasEnCurso?>()
            val locutor = LocutorDeSesion()
            val e =
                escenario(
                    FuenteFalsa(List(10) { p(it * 100.0, 0.0) }),
                    alertas = sesionAlertas(publicadas, listOf(radar)),
                    copiloto = copilotoCon(locutor, ModoCopiloto.SOLO_ALERTAS),
                )
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(5_500)
            runCurrent()

            assertEquals(1, locutor.dichas.count { it.startsWith("radar a ") && it.endsWith("ochenta") })
            assertTrue(publicadas.any { it?.radarProximo != null })
            trabajo.cancel()
            runCurrent()
            assertNull(publicadas.last())
        }

    @Test
    fun `el exceso de velocidad suena una vez en solo alertas y en silencio solo se publica`() =
        runTest {
            val via = ViaActual("Ma-1", "Ma-1", 30)
            for (modo in listOf(ModoCopiloto.SOLO_ALERTAS, ModoCopiloto.SILENCIO)) {
                val publicadas = mutableListOf<AlertasEnCurso?>()
                val locutor = LocutorDeSesion()
                val e =
                    escenario(
                        FuenteFalsa(List(8) { p(0.0, it * 10.0) }),
                        alertas = sesionAlertas(publicadas, via = via),
                        copiloto = copilotoCon(locutor, modo),
                    )
                val trabajo = lanzar(e, SolicitudNavegacion.Libre)
                advanceTimeBy(7_500)
                runCurrent()

                val esperadas = if (modo == ModoCopiloto.SILENCIO) 0 else 1
                assertEquals(modo.name, esperadas, locutor.dichas.count { it == "límite treinta" })
                assertTrue(
                    modo.name,
                    publicadas
                        .filterNotNull()
                        .last()
                        .exceso.exceso,
                )
                trabajo.cancel()
                runCurrent()
            }
        }

    @Test
    fun `un recalculo rehace los avisos sobre la ruta nueva`() =
        runTest {
            // La ruta nueva pasa por un radar que la original no tocaba.
            val radar = Radar("f1", TipoRadar.FIJO, p(1000.0, -100.0), null, 80, null, "test")
            val publicadas = mutableListOf<AlertasEnCurso?>()
            val locutor = LocutorDeSesion()
            val fuera = p(300.0, -200.0)
            val nueva = rutaL(origen = fuera, esquina = p(1000.0, -200.0), fin = p(1000.0, 500.0))
            val e =
                escenario(
                    FuenteFalsa(listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0)) + List(30) { fuera }),
                    respuestas = listOf(ResultadoRuta.Ok(nueva, 50)),
                    alertas = sesionAlertas(publicadas, listOf(radar)),
                    copiloto = copilotoCon(locutor, ModoCopiloto.SOLO_ALERTAS),
                )
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(10_000)
            runCurrent()

            assertNotNull(
                e.estados
                    .filterIsInstance<EstadoNavegacion.Guiando>()
                    .last()
                    .progreso,
            )
            assertEquals(1, e.enrutador.llamadas.size)
            trabajo.cancel()
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
