package app.trombito.rallycopilot.data.ubicacion

import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.PosicionEstimada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ARQ-1 y REN-9: una sola suscripción y un solo estimador para todos los consumidores de la posición. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PosicionEnVivoTest {
    private class Origen : FuenteUbicacion {
        val flujo = MutableSharedFlow<Fijacion>(extraBufferCapacity = 16)
        var suscripciones = 0
        var activas = 0

        override fun fijaciones(): Flow<Fijacion> =
            flujo
                .onStart {
                    suscripciones++
                    activas++
                }.onCompletion { activas-- }

        override fun tienePermiso(): Boolean = true
    }

    private var ahoraMs = 10_000L

    private fun TestScope.fuente(origen: FuenteUbicacion) =
        PosicionEnVivo(
            origen,
            CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
            ),
            {
                ahoraMs
            },
        )

    private fun fijacion(
        tiempoMs: Long,
        lon: Double = 2.9,
        velocidadMs: Double? = 20.0,
        rumbo: Double? = 90.0,
        precisionM: Double? = 5.0,
    ) = Fijacion(PuntoGeo(39.6, lon), tiempoMs, velocidadMs, rumbo, precisionM)

    @Test
    fun `varios consumidores comparten una sola suscripcion y los mismos valores`() =
        runTest(UnconfinedTestDispatcher()) {
            val origen = Origen()
            val fuente = fuente(origen)
            val mapa = mutableListOf<PosicionEstimada>()
            val sesion = mutableListOf<PosicionEstimada>()
            val voz = mutableListOf<Fijacion>()
            val trabajos =
                listOf(
                    launch { fuente.posiciones().toList(mapa) },
                    launch { fuente.posiciones().toList(sesion) },
                    launch { fuente.fijaciones().toList(voz) },
                )

            origen.flujo.emit(fijacion(10_000))

            assertEquals(1, origen.suscripciones)
            assertEquals(1, mapa.size)
            assertEquals(mapa, sesion)
            assertEquals(mapa[0].cruda, voz[0])
            assertEquals(20.0, mapa[0].velocidadMs!!, 1e-9)
            trabajos.forEach { it.cancel() }
        }

    @Test
    fun `sin velocidad ni rumbo del receptor salen los del movimiento`() =
        runTest(UnconfinedTestDispatcher()) {
            val origen = Origen()
            val recibidas = mutableListOf<PosicionEstimada>()
            val trabajo = launch { fuente(origen).posiciones().toList(recibidas) }

            origen.flujo.emit(fijacion(10_000, lon = 2.9000, velocidadMs = null, rumbo = null))
            // 0,001 grados de longitud a 39,6 N son unos 85,8 m al este en 5 s.
            origen.flujo.emit(fijacion(15_000, lon = 2.9010, velocidadMs = null, rumbo = null))

            assertNull(recibidas[0].velocidadMs)
            assertNull(recibidas[0].rumboGrados)
            assertEquals(17.2, recibidas[1].velocidadMs!!, 0.5)
            assertEquals(90.0, recibidas[1].rumboGrados!!, 1.0)
            // La fijación cruda no se toca: la grabación y las notas usan la del receptor.
            assertNull(recibidas[1].cruda.rumboGrados)
            trabajo.cancel()
        }

    @Test
    fun `una posicion imprecisa se pasa tal cual y no alimenta la velocidad`() =
        runTest(UnconfinedTestDispatcher()) {
            val origen = Origen()
            val recibidas = mutableListOf<PosicionEstimada>()
            val trabajo = launch { fuente(origen).posiciones().toList(recibidas) }

            origen.flujo.emit(fijacion(10_000, velocidadMs = 20.0))
            origen.flujo.emit(fijacion(11_000, lon = 2.95, velocidadMs = 1.0, precisionM = 60.0))

            assertTrue(recibidas[0].precisa)
            assertTrue(!recibidas[1].precisa)
            assertEquals(20.0, recibidas[1].velocidadMs!!, 1e-9)
            trabajo.cancel()
        }

    @Test
    fun `al irse el ultimo consumidor se retira la suscripcion y al volver se reabre`() =
        runTest(UnconfinedTestDispatcher()) {
            val origen = Origen()
            val fuente = fuente(origen)
            val a = launch { fuente.posiciones().toList(mutableListOf()) }
            val b = launch { fuente.posiciones().toList(mutableListOf()) }
            assertEquals(1, origen.activas)

            a.cancel()
            assertEquals(1, origen.activas)
            b.cancel()
            assertEquals(0, origen.activas)

            val c = launch { fuente.posiciones().toList(mutableListOf()) }
            assertEquals(2, origen.suscripciones)
            c.cancel()
        }

    @Test
    fun `quien llega con el gps en marcha recibe enseguida la ultima posicion`() =
        runTest(UnconfinedTestDispatcher()) {
            val origen = Origen()
            val fuente = fuente(origen)
            val primero = launch { fuente.posiciones().toList(mutableListOf()) }
            origen.flujo.emit(fijacion(10_000))

            val tardio = mutableListOf<PosicionEstimada>()
            val segundo = launch { fuente.posiciones().toList(tardio) }

            assertEquals(1, tardio.size)
            assertEquals(1, origen.suscripciones)
            primero.cancel()
            segundo.cancel()
        }

    @Test
    fun `una ultima posicion de mas de 2 min no se entrega a quien llega tarde`() =
        runTest(UnconfinedTestDispatcher()) {
            val origen = Origen()
            val fuente = fuente(origen)
            val primero = launch { fuente.posiciones().toList(mutableListOf()) }
            origen.flujo.emit(fijacion(10_000))
            ahoraMs = 10_000 + 121_000

            val tardio = mutableListOf<PosicionEstimada>()
            val segundo = launch { fuente.posiciones().toList(tardio) }

            assertTrue(tardio.isEmpty())
            primero.cancel()
            segundo.cancel()
        }

    @Test
    fun `si el origen termina terminan tambien los consumidores`() =
        runTest(UnconfinedTestDispatcher()) {
            val sinPermiso =
                object : FuenteUbicacion {
                    override fun fijaciones(): Flow<Fijacion> = emptyFlow()

                    override fun tienePermiso(): Boolean = false
                }
            val fuente = fuente(sinPermiso)

            assertTrue(fuente.posiciones().toList().isEmpty())
            assertTrue(fuente.fijaciones().toList().isEmpty())
        }

    @Test
    fun `si el origen falla terminan los consumidores sin tumbar el proceso`() =
        runTest(UnconfinedTestDispatcher()) {
            val roto =
                object : FuenteUbicacion {
                    override fun fijaciones(): Flow<Fijacion> =
                        flow {
                            emit(fijacion(10_000))
                            error("fallo del sistema")
                        }

                    override fun tienePermiso(): Boolean = true
                }
            val recibidas = mutableListOf<PosicionEstimada>()

            fuente(roto).posiciones().toList(recibidas)

            assertEquals(1, recibidas.size)
        }
}
