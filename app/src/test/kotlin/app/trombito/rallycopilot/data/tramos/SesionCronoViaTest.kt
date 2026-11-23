package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import app.trombito.rallycopilot.domain.tramos.TramoCronometrable
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val ORIGEN = PuntoGeo(39.70, 2.70)

private fun alEste(metros: Double): PuntoGeo {
    val grados = Math.toDegrees(metros / (PuntoGeo.RADIO_TIERRA_M * Math.cos(Math.toRadians(ORIGEN.lat))))
    return PuntoGeo(ORIGEN.lat, ORIGEN.lon + grados)
}

/** Tramo recto hacia el este desde el origen. */
private fun recto(
    id: String,
    largoM: Double,
) = Tramo(id, listOf(alEste(0.0), alEste(largoM)), largoM)

/** Coche hacia el este a 20 m/s con una fijación por segundo, de 100 m antes de la salida a [hastaM]. */
private fun conduccion(hastaM: Double): List<Fijacion> =
    (0..((hastaM + 100.0) / 20.0).toInt()).map {
        Fijacion(alEste(-100.0 + it * 20.0), 5_000_000L + it * 1_000L, 20.0, null, 5.0)
    }

class SesionCronoViaTest {
    private val publicados = mutableListOf<CronoEnCurso?>()

    private fun cronometrable(
        id: String,
        largoM: Double,
        tipo: TipoTramo,
    ) = TramoCronometrable(recto(id, largoM), null, tipo)

    @Test
    fun `con varios en marcha el panel muestra el tramo de via cuya meta esta mas cerca`() =
        runTest {
            // El viaje se entra el último: sin la prioridad sería el que se mostrara.
            val repo =
                FalsoRepositorioTramos(
                    cronometrablesIniciales =
                        listOf(
                            cronometrable("via-larga", 5_000.0, TipoTramo.VIA),
                            cronometrable("via-corta", 2_000.0, TipoTramo.VIA),
                            cronometrable("viaje", 6_000.0, TipoTramo.VIAJE),
                        ),
                )
            val crono = SesionCrono(repo, { publicados += it }, { testScheduler.currentTime })
            crono.arrancar(this)
            runCurrent()

            for (f in conduccion(2_500.0)) crono.alFijar(f)

            val porMetros = publicados.filterNotNull().filter { it.tramo != null }
            // Al principio manda la de meta más cercana; al cruzar la suya sigue la larga; el viaje no sale nunca.
            assertEquals("via-corta", porMetros[10].tramo?.id)
            assertEquals("via-larga", porMetros.last().tramo?.id)
            assertEquals(setOf("via-corta", "via-larga"), porMetros.mapNotNull { it.tramo?.id }.toSet())
        }

    @Test
    fun `sin tramos de via sigue mostrando el ultimo en que se entro`() =
        runTest {
            val repo =
                FalsoRepositorioTramos(
                    cronometrablesIniciales =
                        listOf(
                            cronometrable("viaje-a", 6_000.0, TipoTramo.VIAJE),
                            cronometrable("viaje-b", 3_000.0, TipoTramo.VIAJE),
                        ),
                )
            val crono = SesionCrono(repo, { publicados += it }, { testScheduler.currentTime })
            crono.arrancar(this)
            runCurrent()

            for (f in conduccion(1_000.0)) crono.alFijar(f)

            val ultimo = publicados.filterNotNull().last()
            assertEquals("viaje-b", ultimo.tramo?.id)
            assertEquals("viaje-b", crono.tramoActivo()?.id)
        }

    @Test
    fun `el tramo activo para las ediciones prefiere el viaje al de via`() =
        runTest {
            val repo =
                FalsoRepositorioTramos(
                    cronometrablesIniciales =
                        listOf(
                            cronometrable("viaje", 6_000.0, TipoTramo.VIAJE),
                            cronometrable("via", 2_000.0, TipoTramo.VIA),
                        ),
                )
            val crono = SesionCrono(repo, { publicados += it }, { testScheduler.currentTime })
            crono.arrancar(this)
            runCurrent()

            for (f in conduccion(1_000.0)) crono.alFijar(f)

            val ultimo = publicados.filterNotNull().last()
            assertEquals("via", ultimo.tramo?.id)
            assertEquals("viaje", crono.tramoActivo()?.id)
        }

    @Test
    fun `al cerrar trocea el viaje con todos sus puntos`() =
        runTest {
            val repo = FalsoRepositorioTramos()
            val troceados = mutableListOf<Pair<Long, Int>>()
            val crono =
                SesionCrono(
                    repo,
                    { publicados += it },
                    { testScheduler.currentTime },
                    trocear = { id, puntos -> troceados += id to puntos.size },
                )
            crono.arrancar(this)
            runCurrent()
            val puntos = (0..4).map { PuntoRecorrido(ORIGEN.lat, ORIGEN.lon, 1_000L + it, null) }

            crono.cerrar(Recorrido(7L, 1_000L, 1_004L, 1_500.0, null)) { puntos }
            runCurrent() // el troceado va en segundo plano

            assertEquals(listOf(7L to 5), troceados)
            assertNull(publicados.last()) // el cierre publica null
        }

    @Test
    fun `un fallo del motor al trocear no impide acabar el cierre`() =
        runTest {
            val repo = FalsoRepositorioTramos()
            val fallos = mutableListOf<Throwable>()
            var ancladas = 0
            val crono =
                SesionCrono(
                    repo,
                    { publicados += it },
                    { testScheduler.currentTime },
                    alFallar = { fallos += it },
                    anclarEdiciones = { ancladas++ },
                    trocear = { _, _ -> throw IllegalArgumentException("require del motor") },
                )
            crono.arrancar(this)
            runCurrent()
            val puntos = (0..4).map { PuntoRecorrido(ORIGEN.lat, ORIGEN.lon, 1_000L + it, null) }

            crono.cerrar(Recorrido(7L, 1_000L, 1_004L, 1_500.0, null)) { puntos }
            runCurrent()

            assertEquals(1, ancladas)
            assertEquals(1, fallos.size)
            assertNull(publicados.last())
        }

    @Test
    fun `cerrar no espera al troceado aunque el migrador tenga el candado`() =
        runTest {
            val repo = FalsoRepositorioTramos()
            val candado = CompletableDeferred<Unit>()
            val troceados = mutableListOf<Long>()
            var ancladas = 0
            val crono =
                SesionCrono(
                    repo,
                    { publicados += it },
                    { testScheduler.currentTime },
                    anclarEdiciones = { ancladas++ },
                    trocear = { id, _ ->
                        candado.await() // el guardador espera su candado mientras el migrador trocea otros viajes
                        troceados += id
                    },
                )
            crono.arrancar(this)
            runCurrent()
            val puntos = (0..4).map { PuntoRecorrido(ORIGEN.lat, ORIGEN.lon, 1_000L + it, null) }

            val cierre = launch { crono.cerrar(Recorrido(7L, 1_000L, 1_004L, 1_500.0, null)) { puntos } }
            runCurrent()

            assertTrue("el cierre no debe esperar al troceado", cierre.isCompleted)
            assertEquals(1, ancladas)
            assertNull(publicados.last())
            assertTrue(troceados.isEmpty())
            candado.complete(Unit)
            runCurrent()
            assertEquals(listOf(7L), troceados)
        }
}
