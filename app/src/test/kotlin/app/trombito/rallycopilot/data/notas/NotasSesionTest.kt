package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.navegacion.SesionNavegacion
import app.trombito.rallycopilot.data.navegacion.SolicitudNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.vias.NodoAviso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotasSesionTest {
    private class Escenario(
        val notas: MutableList<NotasEnCurso?>,
        val fallos: MutableList<Throwable>,
        val contexto: ContextoFalso,
        val sesion: SesionNavegacion,
    ) {
        val publicadas get() = notas.filterNotNull()
        val ultima get() = notas.lastOrNull()
    }

    private fun TestScope.escenario(
        fuente: FuenteUbicacion,
        contexto: ContextoNotas?,
        factor: Double? = null,
        respuestas: List<ResultadoRuta> = emptyList(),
    ): Escenario {
        val notas = mutableListOf<NotasEnCurso?>()
        val fallos = mutableListOf<Throwable>()
        val fuenteContexto = ContextoFalso(contexto)
        val fabrica =
            FabricaNotasSesion(
                fuenteContexto,
                CalibradorFalso(factor),
                { notas += it },
                StandardTestDispatcher(testScheduler),
                { fallos += it },
            )
        val sesion =
            SesionNavegacion(
                fuente,
                EnrutadorFalso(respuestas.toMutableList()),
                RecorridosFalso(),
                reloj = { testScheduler.currentTime },
                publicar = { _: EstadoNavegacion -> },
                motivoSinGps = "sin GPS",
                fabricaNotas = fabrica,
            )
        return Escenario(notas, fallos, fuenteContexto, sesion)
    }

    private fun CoroutineScope.lanzar(
        e: Escenario,
        solicitud: SolicitudNavegacion,
    ): Job = launch { e.sesion.correr(solicitud) }

    private fun guiada(puntos: List<PuntoGeo> = trazadoConCurva()) =
        SolicitudNavegacion.Guiada(Destino("Sóller", puntos.last()), PerfilRuta.SINUOSA, rutaDe(puntos))

    private fun gradoUnico(e: Escenario): Int {
        val nota = checkNotNull(e.ultima).hoja.notas.single()
        return checkNotNull(nota.grado)
    }

    private fun viaDeLaTraza(maxspeed: String? = null) = via(1, trazadoConCurva(), maxspeed)

    @Test
    fun `guiado genera la hoja al empezar y la posicion avanza`() =
        runTest {
            val traza = trazadoConCurva()
            val e = escenario(FuenteFalsa(fijacionesPor(traza)), contextoDe(listOf(viaDeLaTraza())))
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(30_500)

            assertTrue(e.fallos.toString(), e.fallos.isEmpty())
            val ultima = checkNotNull(e.publicadas.lastOrNull())
            assertEquals(OrigenNotas.GUIADO, ultima.origen)
            assertEquals(1, ultima.hoja.notas.size)
            assertEquals(20.0, ultima.vMs!!, 1e-9)
            val posiciones = e.publicadas.map { it.posicionM }
            assertTrue("posiciones $posiciones", posiciones.zipWithNext().all { (a, b) -> b >= a - 1e-6 })
            assertTrue("posicion final ${posiciones.last()}", posiciones.last() > 500.0)
            trabajo.cancel()
        }

    @Test
    fun `guiado sin datos de vias genera la hoja solo con curvas`() =
        runTest {
            val e = escenario(FuenteFalsa(fijacionesPor(trazadoConCurva())), contexto = null)
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(3_500)

            assertTrue(e.fallos.toString(), e.fallos.isEmpty())
            assertEquals(1, checkNotNull(e.ultima).hoja.notas.size)
            trabajo.cancel()
        }

    @Test
    fun `sin vias-notas la hoja solo tiene curvas y con ellas lleva los avisos`() =
        runTest {
            val parada = listOf(NodoAviso(p(300.0, 0.0), "stop"))
            val sinNotas = escenario(FuenteFalsa(fijacionesPor(trazadoConCurva())), contextoDe(listOf(viaDeLaTraza())))
            val conNotas =
                escenario(
                    FuenteFalsa(fijacionesPor(trazadoConCurva())),
                    contextoDe(listOf(viaDeLaTraza()), parada),
                )
            val a = lanzar(sinNotas, guiada())
            val b = lanzar(conNotas, guiada())
            advanceTimeBy(3_500)

            assertTrue((sinNotas.fallos + conNotas.fallos).toString(), (sinNotas.fallos + conNotas.fallos).isEmpty())
            assertEquals(1, checkNotNull(sinNotas.ultima).hoja.notas.size)
            assertTrue(checkNotNull(sinNotas.ultima).hoja.avisos.isEmpty())
            assertEquals(listOf(TipoAviso.STOP), checkNotNull(conNotas.ultima).hoja.avisos.map { it.tipo })
            a.cancel()
            b.cancel()
        }

    @Test
    fun `el limite de la via y el factor de calibracion no cambian el grado de la curva`() =
        runTest {
            val traza = trazadoConCurva()

            fun caso(
                limite: String? = null,
                factor: Double? = null,
            ) = escenario(FuenteFalsa(fijacionesPor(traza)), contextoDe(listOf(via(1, traza, limite))), factor)
            val sinLimite = caso()
            // Curva de R 60 m y unos 90 grados: el grado depende solo del ángulo, sea cual sea el límite o el factor.
            val conLimite = caso("40")
            val calibrada = caso("40", 0.4)
            val calibradaSinLimite = caso(factor = 0.4)
            val calibradaRapida = caso("40", 1.2)
            val todos = listOf(sinLimite, conLimite, calibrada, calibradaSinLimite, calibradaRapida)
            val trabajos = todos.map { lanzar(it, guiada()) }
            advanceTimeBy(3_500)

            val gradoSin = gradoUnico(sinLimite)
            assertEquals(4, gradoSin)
            todos.forEach { assertEquals(gradoSin, gradoUnico(it)) }
            // La calibración llega en segundo plano: antes y después hubo una hoja publicada.
            assertTrue(calibrada.publicadas.size >= 2)
            trabajos.forEach { it.cancel() }
        }

    @Test
    fun `tras un recalculo se descarta la hoja vieja y se genera la nueva`() =
        runTest {
            val fuera = p(300.0, -200.0)
            val nueva = recta(300.0, -200.0, haciaEste = true, largoM = 1000.0, pasoM = 10.0)
            val fijaciones =
                fijacionesPor(trazadoConCurva(), hastaM = 40.0) +
                    List(20) { fijacionEn(fuera, 90.0, it + 3) }
            val e =
                escenario(
                    FuenteFalsa(fijaciones),
                    contextoDe(listOf(viaDeLaTraza())),
                    respuestas = listOf(ResultadoRuta.Ok(rutaDe(nueva), 50)),
                )
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(12_000)

            assertTrue(e.fallos.toString(), e.fallos.isEmpty())
            val primeraConCurva = e.notas.indexOfFirst { it != null && it.hoja.notas.size == 1 }
            assertTrue("hoja inicial publicada", primeraConCurva >= 0)
            val nula = e.notas.drop(primeraConCurva).indexOfFirst { it == null }
            assertTrue("se retira la hoja de la ruta vieja al recalcular", nula >= 0)
            val ultima = checkNotNull(e.ultima)
            assertTrue(ultima.hoja.notas.isEmpty())
            assertEquals(OrigenNotas.GUIADO, ultima.origen)
            trabajo.cancel()
        }

    @Test
    fun `al llegar se publica null`() =
        runTest {
            val traza = trazadoConCurva()
            val fijaciones = fijacionesPor(traza) + fijacionEn(traza.last(), 0.0, 200)
            val e = escenario(FuenteFalsa(fijaciones), contextoDe(listOf(viaDeLaTraza())))
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(80_000)

            assertTrue(e.publicadas.isNotEmpty())
            assertNull(e.ultima)
            trabajo.cancel()
        }

    @Test
    fun `al cancelar la sesion se publica null`() =
        runTest {
            val e = escenario(FuenteFalsa(fijacionesPor(trazadoConCurva())), contextoDe(listOf(viaDeLaTraza())))
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(3_500)
            assertNotNull(e.ultima)

            trabajo.cancel()
            runCurrent()
            assertNull(e.ultima)
        }

    @Test
    fun `libre predice la carretera, avanza la posicion y regenera tras 500 m`() =
        runTest {
            val carretera = recta(0.0, 0.0, haciaEste = true, largoM = 4000.0)
            val fijaciones = fijacionesPor(carretera, desdeM = 100.0, hastaM = 1500.0)
            val e = escenario(FuenteFalsa(fijaciones), contextoDe(listOf(via(1, carretera))))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(80_000)

            assertTrue(e.fallos.toString(), e.fallos.isEmpty())
            val posiciones = e.publicadas.map { it.posicionM }
            assertTrue(posiciones.isNotEmpty())
            assertTrue(e.publicadas.all { it.origen == OrigenNotas.LIBRE })
            assertTrue("nunca pasa mucho de 500 m: ${posiciones.max()}", posiciones.max() < 560.0)
            val regeneraciones = posiciones.zipWithNext().count { (a, b) -> b < a - 400.0 }
            assertTrue("regeneraciones: $regeneraciones en $posiciones", regeneraciones >= 2)
            trabajo.cancel()
            runCurrent()
            assertNull(e.ultima)
        }

    @Test
    fun `libre predice la carretera aunque las fijaciones no traigan rumbo`() =
        runTest {
            // Como el GPS del emulador o un rumbo poco preciso: el sentido sale del movimiento.
            val carretera = recta(0.0, 0.0, haciaEste = true, largoM = 4000.0)
            val fijaciones =
                fijacionesPor(carretera, desdeM = 100.0, hastaM = 800.0).map { it.copy(rumboGrados = null) }
            val e = escenario(FuenteFalsa(fijaciones), contextoDe(listOf(via(1, carretera))))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(40_000)

            assertTrue(e.fallos.toString(), e.fallos.isEmpty())
            assertTrue(e.publicadas.isNotEmpty())
            trabajo.cancel()
        }

    @Test
    fun `libre regenera la hoja al salirse de la prediccion por otra via`() =
        runTest {
            val a = recta(0.0, 0.0, haciaEste = true, largoM = 2000.0)
            val b = recta(400.0, 0.0, haciaEste = false, largoM = 2000.0)
            val parada = listOf(NodoAviso(p(300.0, 0.0), "stop"))
            val este = (0..15).map { fijacionEn(p(100.0 + it * 20.0, 0.0), 90.0, it) }
            val norte = (1..20).map { fijacionEn(p(400.0, it * 20.0), 0.0, 15 + it) }
            val e = escenario(FuenteFalsa(este + norte), contextoDe(listOf(via(1, a), via(2, b)), parada))
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(60_000)

            assertTrue(e.fallos.toString(), e.fallos.isEmpty())
            val conParada = e.notas.indexOfFirst { it != null && it.hoja.avisos.any { a -> a.tipo == TipoAviso.STOP } }
            assertTrue("hoja de la carretera A con su stop", conParada >= 0)
            assertTrue("se retira la hoja al salirse", e.notas.drop(conParada).any { it == null })
            val ultima = checkNotNull(e.ultima)
            assertTrue(ultima.hoja.avisos.isEmpty())
            // La nueva predicción nació poco después del cruce (y ≈ 40 m) y el coche va por y = 400 m.
            assertTrue("posición sobre la nueva predicción: ${ultima.posicionM}", ultima.posicionM in 250.0..400.0)
            trabajo.cancel()
        }

    @Test
    fun `libre sin datos de vias no publica notas`() =
        runTest {
            val carretera = recta(0.0, 0.0, haciaEste = true, largoM = 4000.0)
            val e = escenario(FuenteFalsa(fijacionesPor(carretera, hastaM = 400.0)), contexto = null)
            val trabajo = lanzar(e, SolicitudNavegacion.Libre)
            advanceTimeBy(30_000)

            assertTrue(e.publicadas.isEmpty())
            assertTrue(e.fallos.isEmpty())
            trabajo.cancel()
        }

    @Test
    fun `el contexto de vias se carga una sola vez por sesion aunque se regenere`() =
        runTest {
            val traza = trazadoConCurva()
            val e = escenario(FuenteFalsa(fijacionesPor(traza)), contextoDe(listOf(via(1, traza))), factor = 1.2)
            val trabajo = lanzar(e, guiada())
            advanceTimeBy(5_000)

            assertEquals(1, e.contexto.llamadas)
            trabajo.cancel()
        }
}
