package app.trombito.rallycopilot.data.resumen

import app.trombito.rallycopilot.data.carreteras.CalculadorCarreteras
import app.trombito.rallycopilot.data.carreteras.RecorridosFalsos
import app.trombito.rallycopilot.data.carreteras.lineaEste
import app.trombito.rallycopilot.data.carreteras.viaFalsa
import app.trombito.rallycopilot.data.carreteras.viasFalsas
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.domain.resumen.ContextoVias
import app.trombito.rallycopilot.domain.resumen.FuenteContextoVias
import app.trombito.rallycopilot.domain.tramos.SubtramoGuardado
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadorResumenViajeTest {
    private val ma10 = viaFalsa(1, "Ma-10", 0, 1_000)

    private fun subtramo(
        tramoRefId: String,
        tiempoMs: Long,
        mejorMs: Long?,
        inicio: Long = 0L,
    ) = SubtramoGuardado(tramoRefId, 0.0, 500.0, 500.0, inicio, tiempoMs, mejorMs)

    private fun TestScope.calculador(
        repo: RecorridosFalsos,
        tramos: FalsoRepositorioTramos = FalsoRepositorioTramos(),
        vias: FuenteContextoVias =
            CalculadorCarreteras(repo, viasFalsas(listOf(ma10)), StandardTestDispatcher(testScheduler)),
    ) = CalculadorResumenViaje(repo, tramos, vias, StandardTestDispatcher(testScheduler))

    @Test
    fun `un recorrido que no existe no tiene resumen`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            assertNull(calculador(repo).resumenDe(99L))
        }

    @Test
    fun `arma distancia, tiempo, destino y la llegada con el tiempo previsto`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            repo.lista.value = listOf(Recorrido(1, 5_000L, 95_000L, 900.0, "Sóller", tiempoPrevistoS = 80.0))
            val viaje = calculador(repo).resumenDe(1L)!!
            assertEquals(1L, viaje.recorridoId)
            assertEquals(5_000L, viaje.inicioMs)
            assertEquals("Sóller", viaje.destinoNombre)
            // 91 puntos cada 10 m y 1 s: 900 m en 90 s a 36 km/h.
            assertEquals(900.0, viaje.resumen.distanciaM, 10.0)
            assertEquals(90L, viaje.resumen.tiempoTotalS)
            assertEquals(36.0, viaje.resumen.mediaEnMarchaKmh!!, 0.5)
            assertEquals(80L, viaje.resumen.llegada.previstoS)
            assertEquals(10L, viaje.resumen.llegada.desviacionS)
        }

    @Test
    fun `sin tiempo previsto no hay llegada que comparar`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val viaje = calculador(repo).resumenDe(1L)!!
            assertNull(viaje.resumen.llegada.previstoS)
            assertNull(viaje.resumen.llegada.desviacionS)
        }

    @Test
    fun `un recorrido con un solo punto no tiene resumen`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 0)))
            assertNull(calculador(repo).resumenDe(1L))
        }

    @Test
    fun `los tramos hechos llevan su nombre, el delta con el mejor anterior y el record`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val tramos =
                FalsoRepositorioTramos(
                    resumenes = listOf(resumenFalso("a", "Ma-10 Valldemossa"), resumenFalso("b", "Ma-2141 Escorca")),
                )
            tramos.subtramosPorRecorrido[1L] =
                listOf(
                    subtramo("b", tiempoMs = 31_000L, mejorMs = 30_000L, inicio = 2L),
                    subtramo("a", tiempoMs = 28_000L, mejorMs = 30_000L, inicio = 1L),
                    subtramo("a", tiempoMs = 40_000L, mejorMs = null, inicio = 3L),
                )
            val hechos = calculador(repo, tramos).resumenDe(1L)!!.resumen.tramos
            val record = hechos.single { it.esRecord }
            assertEquals("Ma-10 Valldemossa", record.nombre)
            assertEquals(-2_000L, record.deltaMs)
            // Los récords primero, luego del que más mejoró al que más empeoró, y la primera vez al final.
            assertEquals(listOf(-2_000L, 1_000L, null), hechos.map { it.deltaMs })
            assertEquals(1, hechos.count { it.esRecord })
        }

    @Test
    fun `un subtramo de un tramo que ya no existe se ignora`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val tramos = FalsoRepositorioTramos(resumenes = listOf(resumenFalso("a")))
            tramos.subtramosPorRecorrido[1L] = listOf(subtramo("borrado", 1_000L, 2_000L))
            val resumen = calculador(repo, tramos).resumenDe(1L)!!.resumen
            assertTrue(resumen.tramos.isEmpty())
        }

    @Test
    fun `compara cada via con tu media de los demas viajes y no cuenta este`() =
        runTest {
            // El viaje 1 va a 36 km/h por la Ma-10 y el 2 a 72 km/h (puntos cada 20 m): el 2 es el doble de rápido.
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900), 2L to lineaEste(0, 900, pasoM = 20)))
            val viaje = calculador(repo).resumenDe(2L)!!
            val via = viaje.resumen.vias.single()
            assertEquals("Ma-10", via.ref)
            assertEquals(36.0, via.velMediaHistoricaKmh!!, 1.0)
            assertEquals(100.0, via.diferenciaPct!!, 5.0)
        }

    @Test
    fun `con un solo viaje la via no tiene historico`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val resumen = calculador(repo).resumenDe(1L)!!.resumen
            val via = resumen.vias.single()
            assertNull(via.velMediaHistoricaKmh)
            assertNull(via.diferenciaPct)
        }

    @Test
    fun `sin vias instaladas el resumen sale sin carreteras`() =
        runTest {
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            val sinVias = CalculadorCarreteras(repo, viasFalsas(null), StandardTestDispatcher(testScheduler))
            val viaje = calculador(repo, vias = sinVias).resumenDe(1L)!!
            assertTrue(viaje.resumen.vias.isEmpty())
            assertEquals(900.0, viaje.resumen.distanciaM, 10.0)
        }

    @Test
    fun `pide el contexto de vias del recorrido que resume`() =
        runTest {
            val pedidos = mutableListOf<Long>()
            val vias =
                FuenteContextoVias { id ->
                    pedidos += id
                    ContextoVias({ _, _ -> null }, emptyMap())
                }
            val repo = RecorridosFalsos(mapOf(1L to lineaEste(0, 900)))
            assertNotNull(calculador(repo, vias = vias).resumenDe(1L))
            assertEquals(listOf(1L), pedidos)
            assertFalse(pedidos.isEmpty())
        }
}
