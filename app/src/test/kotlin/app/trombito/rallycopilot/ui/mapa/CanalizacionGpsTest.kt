package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoProveedorVia
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.fijacion
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** REN-9b: una emisión por fijación y una segunda solo si cambia la vía. */
@OptIn(ExperimentalCoroutinesApi::class)
class CanalizacionGpsTest {
    private val ubicacion = FalsaFuenteUbicacion()
    private val vias = FalsoProveedorVia()
    private val canalizacion =
        CanalizacionGps(
            ubicacion,
            vias,
            FalsoControlNavegacion(),
            FalsoRepositorioAjustesConduccion(),
            FalsoLocutor(),
            reloj = { 0L },
        )

    private fun TestScope.recoger(): MutableList<LecturaMapa> {
        val lecturas = mutableListOf<LecturaMapa>()
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { canalizacion.lecturas.collect { lecturas += it } }
        return lecturas
    }

    private suspend fun emitir(f: Fijacion) = ubicacion.flujo.emit(f)

    @Test
    fun `sin via cada fijacion da una sola lectura`() =
        runTest {
            val lecturas = recoger()
            assertEquals(1, lecturas.size) // la vacía inicial

            emitir(fijacion(tiempoMs = 0, velocidadMs = 10.0))
            assertEquals(2, lecturas.size)
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 10.0))
            assertEquals(3, lecturas.size)
            assertNull(lecturas.last().via)
        }

    @Test
    fun `con la misma via solo la primera fijacion da una segunda lectura`() =
        runTest {
            vias.via = ViaActual("Carretera", "Ma-1110", 50)
            val lecturas = recoger()

            emitir(fijacion(tiempoMs = 0, velocidadMs = 10.0))
            // La velocidad sin esperar a la vía, y de nuevo con ella.
            assertEquals(3, lecturas.size)
            assertNull(lecturas[1].via)
            assertEquals("Ma-1110", lecturas[2].via?.ref)

            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 10.0))
            emitir(fijacion(tiempoMs = 2_000, velocidadMs = 10.0))
            assertEquals(5, lecturas.size)
        }

    @Test
    fun `si la via cambia hay una segunda lectura con la via nueva`() =
        runTest {
            vias.via = ViaActual("Carretera", "Ma-1110", 50)
            val lecturas = recoger()
            emitir(fijacion(tiempoMs = 0, velocidadMs = 10.0))
            val antes = lecturas.size

            vias.via = ViaActual("Camí", null, null)
            emitir(fijacion(tiempoMs = 1_000, velocidadMs = 10.0))

            assertEquals(antes + 2, lecturas.size)
            assertEquals("Camí", lecturas.last().via?.nombre)
        }
}
