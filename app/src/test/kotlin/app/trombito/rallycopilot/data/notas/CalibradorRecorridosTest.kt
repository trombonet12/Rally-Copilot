package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.motor.notas.PerfilVelocidad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class CalibradorRecorridosTest {
    /** Una carretera que serpentea: media onda cada ~200 m, radios del orden de 130 m. */
    private fun serpenteo(medias: Int): List<PuntoRecorrido> =
        (0..medias * 20).map { i ->
            val punto = p(i * 10.0, 30.0 * sin(2 * PI * i * 10.0 / 400.0))
            PuntoRecorrido(punto.lat, punto.lon, i * 1000L, 15.0)
        }

    @Test
    fun `sin recorridos no hay factor`() =
        runTest {
            assertNull(CalibradorRecorridos(RecorridosFalso()).factor())
        }

    @Test
    fun `con pocas curvas no hay factor`() =
        runTest {
            assertNull(CalibradorRecorridos(RecorridosFalso(listOf(serpenteo(10)))).factor())
        }

    @Test
    fun `con curvas bastantes calibra con la velocidad real`() =
        runTest {
            val factor = CalibradorRecorridos(RecorridosFalso(listOf(serpenteo(80)))).factor()

            assertNotNull(factor)
            assertTrue("factor $factor", factor!! in PerfilVelocidad.FACTOR_MIN..PerfilVelocidad.FACTOR_MAX)
        }
}
