package app.trombito.rallycopilot.domain.ritmo

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.IdVia
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.ruta.RitmoVia
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiempoEstimadoRutaTest {
    /** 1 000 m por la Ma-13 en 100 s (36 km/h para Valhalla) y 500 m sin vía en 50 s. */
    private val ruta =
        Ruta(
            puntos = listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.9), PuntoGeo(39.62, 2.9)),
            maniobras =
                listOf(
                    Maniobra(TipoManiobra.SALIDA, "salida", listOf("Ma-13"), 0, 1, 1000.0, 100.0, null),
                    Maniobra(TipoManiobra.IZQUIERDA, "izquierda", emptyList(), 1, 2, 500.0, 50.0, null),
                    Maniobra(TipoManiobra.DESTINO, "destino", emptyList(), 2, 2, 0.0, 0.0, null),
                ),
            longitudM = 1500.0,
            tiempoS = 150.0,
        )

    @Test
    fun `sin historico es el tiempo de Valhalla por el factor personal de partida`() {
        assertEquals(150.0 * 1.12, ruta.tiempoEstimadoS(PerfilRitmo()), 1e-9)
    }

    @Test
    fun `una via conocida y lenta alarga el tiempo`() {
        val lenta = PerfilRitmo(mapOf(IdVia("Ma-13") to RitmoVia(25.0, pasadas = 3, km = 9.0)))

        val estimado = ruta.tiempoEstimadoS(lenta)

        // Ma-13: 100 s / (25 / 36) = 144 s; el resto, 50 s × 1,12.
        assertEquals(144.0 + 56.0, estimado, 0.01)
        assertTrue(estimado > ruta.tiempoEstimadoS(PerfilRitmo()))
    }

    @Test
    fun `sin maniobras aplica el factor personal al tiempo de la ruta`() {
        val sinManiobras = ruta.copy(maniobras = emptyList())

        assertEquals(150.0 * 1.2, sinManiobras.tiempoEstimadoS(PerfilRitmo(factorPersonal = 1.2)), 1e-9)
    }

    @Test
    fun `SinPerfilRitmo da el perfil de partida`() =
        runTest {
            assertEquals(PerfilRitmo(), SinPerfilRitmo.perfil())
        }
}
