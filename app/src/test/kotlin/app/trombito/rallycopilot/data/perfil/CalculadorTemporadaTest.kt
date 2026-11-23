package app.trombito.rallycopilot.data.perfil

import app.trombito.rallycopilot.data.carreteras.RecorridosFalsos
import app.trombito.rallycopilot.data.carreteras.lineaEste
import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.data.carreteras.viasFalsas
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.domain.perfil.CarreteraCarne
import app.trombito.rallycopilot.domain.perfil.TemporadaPiloto
import app.trombito.rallycopilot.motor.analisis.NivelPiloto
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import app.trombito.rallycopilot.ui.perfil.AHORA_PRUEBA_MS
import app.trombito.rallycopilot.ui.perfil.diaMs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

class CalculadorTemporadaTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 6_000)

    private fun TestScope.calculador(
        recorridos: RecorridosFalsos,
        tramos: FalsoRepositorioTramos = FalsoRepositorioTramos(),
        sinVias: Boolean = false,
    ) = CalculadorTemporada(
        recorridos,
        tramos,
        viasFalsas(if (sinVias) null else listOf(ma10)),
        StandardTestDispatcher(testScheduler),
        ahoraMs = { AHORA_PRUEBA_MS },
        zona = { ZoneOffset.UTC },
    )

    private suspend fun CalculadorTemporada.temporada(): TemporadaPiloto = observar().first()

    private fun tresViajes(): RecorridosFalsos {
        val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(0, 900), 3L to lineaEste(0, 900)))
        repo.lista.value =
            listOf(
                Recorrido(3L, diaMs(17), diaMs(17) + 1, 20_000.0, "Sóller"),
                Recorrido(2L, diaMs(10), diaMs(10) + 1, 10_000.0, null),
                Recorrido(1L, diaMs(3), diaMs(3) + 1, 12_000.0, null),
            )
        return repo
    }

    @Test
    fun `sin viajes la temporada esta vacia`() =
        runTest {
            val datos = calculador(RecorridosFalsos(emptyMap())).temporada()
            assertEquals(0, datos.temporada.viajes)
            assertEquals(NivelPiloto.DEBUTANTE, datos.temporada.nivel.nivel)
            assertTrue(datos.carne.isEmpty())
        }

    @Test
    fun `los viajes dan kilometros, semanas, racha y nivel`() =
        runTest {
            val temporada = calculador(tresViajes()).temporada().temporada
            assertEquals(3, temporada.viajes)
            assertEquals(42.0, temporada.kmTotales, 1e-9)
            // Tres semanas seguidas (2, 9 y 16 de noviembre) y la actual ya tiene la semana pasada viva.
            assertEquals(3, temporada.racha.actual)
            assertEquals(listOf(12.0, 10.0, 20.0, 0.0), temporada.kmPorSemana.map { it.km })
            assertEquals(1, temporada.carreterasDistintas)
            assertEquals(3, temporada.resumenMes.viajes)
        }

    @Test
    fun `el carne cuenta las carreteras de cada viaje`() =
        runTest {
            val carne = calculador(tresViajes()).temporada().carne
            assertEquals(listOf(CarreteraCarne("Ma-10", 3)), carne)
        }

    @Test
    fun `sin vias siguen saliendo los kilometros pero no hay carreteras`() =
        runTest {
            val datos = calculador(tresViajes(), sinVias = true).temporada()
            assertEquals(42.0, datos.temporada.kmTotales, 1e-9)
            assertEquals(0, datos.temporada.carreterasDistintas)
            assertTrue(datos.carne.isEmpty())
        }

    @Test
    fun `los records salen de las pasadas que mejoran la anterior`() =
        runTest {
            val tramos =
                FalsoRepositorioTramos(
                    resumenes = listOf(resumenFalso("a", nombre = "Ma-2141", pasadas = 2)),
                    pasadas =
                        mapOf(
                            "a" to
                                listOf(
                                    detalladaFalsa(2, "a", 240_000L, diaMs(17)),
                                    detalladaFalsa(1, "a", 250_000L, diaMs(3)),
                                ),
                        ),
                )
            val record =
                calculador(tresViajes(), tramos)
                    .temporada()
                    .temporada.records
                    .single()
            assertEquals("Ma-2141", record.nombreTramo)
            assertEquals(10_000L, record.mejoraMs)
            assertEquals(diaMs(17), record.fechaMs)
        }

    @Test
    fun `un tramo con una sola pasada no da records`() =
        runTest {
            val tramos =
                FalsoRepositorioTramos(
                    resumenes = listOf(resumenFalso("a", pasadas = 1)),
                    pasadas = mapOf("a" to listOf(detalladaFalsa(1, "a", 250_000L, diaMs(3)))),
                )
            assertTrue(
                calculador(tresViajes(), tramos)
                    .temporada()
                    .temporada.records
                    .isEmpty(),
            )
        }

    @Test
    fun `no vuelve a leer los puntos de un viaje ya analizado`() =
        runTest {
            val repo = tresViajes()
            val calculador = calculador(repo)
            calculador.temporada()
            assertEquals(3, repo.lecturas)
            calculador.temporada()
            assertEquals(3, repo.lecturas)
            repo.trazas[4L] = lineaEste(0, 900)
            repo.lista.value = repo.lista.value + Recorrido(4L, diaMs(18), diaMs(18) + 1, 1_000.0, null)
            calculador.temporada()
            assertEquals(4, repo.lecturas)
        }

    @Test
    fun `un fallo al leer los puntos se propaga para que la pantalla lo cuente`() =
        runTest {
            val repo = tresViajes()
            repo.fallo = IOException("puntos")
            try {
                calculador(repo).temporada()
                fail("Debía fallar")
            } catch (esperado: IOException) {
                assertEquals("puntos", esperado.message)
            }
        }
}
