package app.trombito.rallycopilot.data.perfil

import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.data.carreteras.RecorridosFalsos
import app.trombito.rallycopilot.data.carreteras.lineaEste
import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.data.carreteras.viasFalsas
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.motor.analisis.TipoConclusion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.detalladaFalsa
import app.trombito.rallycopilot.ui.comun.resumenFalso
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

class CalculadorTableroTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 6_000)

    private fun TestScope.calculador(
        recorridos: RecorridosFalsos,
        tramos: FalsoRepositorioTramos = FalsoRepositorioTramos(),
    ): CalculadorTablero {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val carreteras = CalculadorCarreteras(recorridos, viasFalsas(listOf(ma10)), dispatcher)
        return CalculadorTablero(
            carreteras,
            recorridos,
            tramos,
            dispatcher,
            ahoraMs = { 0L },
            zona = { ZoneOffset.UTC },
        )
    }

    private suspend fun CalculadorTablero.tablero(): TableroPiloto = observar().first()

    @Test
    fun `sin viajes el tablero esta vacio`() =
        runTest {
            val tablero = calculador(RecorridosFalsos(emptyMap())).tablero()
            assertEquals(TableroPiloto(), tablero)
        }

    @Test
    fun `la cabecera sale de los viajes y el ultimo es el mas reciente`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(0, 900)))
            repo.lista.value =
                listOf(
                    Recorrido(2L, 5_000L, 6_000L, 12_000.0, "Sóller"),
                    Recorrido(1L, 1_000L, 2_000L, 3_000.0, null),
                )
            val tablero = calculador(repo).tablero()
            assertEquals(2, tablero.viajes)
            assertEquals(15.0, tablero.kmTotales, 1e-9)
            assertEquals(1_000L, tablero.primerViajeMs)
            assertEquals(2L, tablero.ultimoViaje?.id)
            assertEquals("Sóller", tablero.ultimoViaje?.destinoNombre)
            assertEquals(12_000.0, tablero.ultimoViaje?.distanciaM ?: 0.0, 0.0)
        }

    @Test
    fun `los tramos guardados no suman kilometros ni viajes`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val tramos =
                FalsoRepositorioTramos(
                    resumenes =
                        listOf(
                            resumenFalso("viaje", pasadas = 1, longitudM = 40_000.0),
                            resumenFalso("via", pasadas = 1, longitudM = 15_000.0),
                        ),
                )
            val tablero = calculador(repo, tramos).tablero()
            assertEquals(1, tablero.viajes)
            assertEquals(2.0, tablero.kmTotales, 1e-9)
        }

    @Test
    fun `el ritmo es la media en marcha de las vias`() =
        runTest {
            // Puntos cada 10 m y 1 s: 36 km/h durante 6 km en la Ma-10.
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 6_000)))
            val ritmo = calculador(repo).tablero().conclusiones.single { it.tipo == TipoConclusion.RITMO }
            assertEquals("36 km/h", ritmo.cifra)
            assertTrue(ritmo.frase, "en marcha" in ritmo.frase)
        }

    @Test
    fun `sin kilometros suficientes no hay conclusiones pero si cabecera`() =
        runTest {
            val tablero = calculador(RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))).tablero()
            assertEquals(1, tablero.viajes)
            assertTrue(tablero.conclusiones.isEmpty())
        }

    @Test
    fun `no vuelve a leer los puntos de un viaje ya analizado`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val calculador = calculador(repo)
            calculador.tablero()
            // Una lectura para las carreteras y otra para la velocidad por grado.
            assertEquals(2, repo.lecturas)
            calculador.tablero()
            assertEquals(2, repo.lecturas)
            // Un viaje nuevo solo lee lo nuevo.
            repo.trazas[2L] = lineaEste(0, 900)
            repo.lista.value = repo.lista.value + Recorrido(2L, 2L, 3L, 2_000.0, null)
            calculador.tablero()
            assertEquals(4, repo.lecturas)
        }

    @Test
    fun `un record de verdad necesita dos pasadas y una mejor que la anterior`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val unica =
                FalsoRepositorioTramos(
                    resumenes = listOf(resumenFalso("a", pasadas = 1)),
                    pasadas = mapOf("a" to listOf(detalladaFalsa(1, "a", 250_000L, 1_000L, esMejor = true))),
                )
            assertFalse(calculador(repo, unica).tablero().conclusiones.any { it.tipo == TipoConclusion.RECORDS })

            val dos =
                FalsoRepositorioTramos(
                    resumenes = listOf(resumenFalso("a", nombre = "Ma-2141", pasadas = 2)),
                    pasadas =
                        mapOf(
                            "a" to
                                listOf(
                                    detalladaFalsa(2, "a", 240_000L, 2_000L, esMejor = true),
                                    detalladaFalsa(1, "a", 250_000L, 1_000L),
                                ),
                        ),
                )
            val record = calculador(repo, dos).tablero().conclusiones.single { it.tipo == TipoConclusion.RECORDS }
            assertEquals("1", record.cifra)
            assertTrue(record.frase, "Ma-2141" in record.frase)
        }

    @Test
    fun `un fallo al leer los puntos se propaga para que la pantalla lo cuente`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            repo.fallo = IOException("puntos")
            try {
                calculador(repo).tablero()
                fail("Debía fallar")
            } catch (esperado: IOException) {
                assertEquals("puntos", esperado.message)
            }
        }
}
