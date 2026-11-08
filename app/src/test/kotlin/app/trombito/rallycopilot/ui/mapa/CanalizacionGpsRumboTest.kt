package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.comun.FalsaFuenteUbicacion
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A la búsqueda de vía va el rumbo estimado (el del movimiento) cuando el receptor no da rumbo. */
@OptIn(ExperimentalCoroutinesApi::class)
class CanalizacionGpsRumboTest {
    private class ProveedorQueAnota : ProveedorViaActual {
        val recibidas = mutableListOf<Fijacion>()
        var nombre = "Autopista"

        override suspend fun viaEn(fijacion: Fijacion): ViaActual? {
            recibidas += fijacion
            return ViaActual(nombre, "Ma-13", 120)
        }
    }

    private val ubicacion = FalsaFuenteUbicacion()
    private val vias = ProveedorQueAnota()
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

    /** Fijación hacia el norte, a ~11 m por segundo, sin rumbo del receptor. */
    private fun alNorte(n: Int) = Fijacion(PuntoGeo(39.7 + n * 0.0001, 2.9), n * 1_000L, null, null, 5.0)

    @Test
    fun `sin rumbo del receptor la busqueda de via recibe el rumbo del movimiento`() =
        runTest {
            recoger()
            (0..3).forEach { ubicacion.flujo.emit(alNorte(it)) }

            val ultima = vias.recibidas.last()
            val rumbo = ultima.rumboGrados
            assertNotNull("La vía no recibió rumbo: $ultima", rumbo)
            assertTrue("Rumbo $rumbo", Rumbo.diferencia(0.0, rumbo ?: Double.NaN) < 5.0)
        }

    @Test
    fun `con la via ya conocida cada fijacion da una sola lectura aunque cambie la via`() =
        runTest {
            val lecturas = recoger()
            ubicacion.flujo.emit(alNorte(0))
            val antes = lecturas.size
            vias.nombre = "Camí"
            ubicacion.flujo.emit(alNorte(1))
            assertEquals(antes + 1, lecturas.size)
            assertEquals("Camí", lecturas.last().via?.nombre)
        }
}
