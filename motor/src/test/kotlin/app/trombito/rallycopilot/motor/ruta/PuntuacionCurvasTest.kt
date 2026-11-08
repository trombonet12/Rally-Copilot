package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PuntuacionCurvasTest {
    private val ma2141 by lazy { PuntuacionCurvas.medir(TrazasDePrueba.cargarGpx("ma-2141-sa-calobra.gpx")) }
    private val ma13 by lazy { PuntuacionCurvas.medir(TrazasDePrueba.cargarGpx("ma-13-santa-maria-palma.gpx")) }
    private val ma10 by lazy { PuntuacionCurvas.medir(TrazasDePrueba.cargarGpx("ma-10-soller-lluc.gpx")) }

    @Test
    fun `la Ma-2141 revirada tiene mas de la mitad de su longitud en curvas`() {
        assertTrue("${ma2141.curvasKm} km de ${ma2141.longitudM} m", ma2141.curvasM > 0.5 * ma2141.longitudM)
        assertTrue(ma2141.indiceSinuosidad > 400.0)
    }

    @Test
    fun `la Ma-13 casi recta no tiene curvas ni sinuosidad`() {
        assertEquals(0.0, ma13.curvasM, 0.0)
        assertTrue(ma13.indiceSinuosidad < 50.0)
    }

    @Test
    fun `la Ma-2141 puntua muy por encima de la Ma-13 en km de curvas y en indice`() {
        assertTrue(ma2141.curvasKm > ma13.curvasKm + 5.0)
        assertTrue(ma2141.indiceSinuosidad > 10 * ma13.indiceSinuosidad)
    }

    @Test
    fun `la Ma-10 de la Tramuntana es sinuosa`() {
        assertTrue(ma10.curvasKm > 10.0)
        assertTrue(ma10.indiceSinuosidad > 200.0)
    }

    @Test
    fun `una recta mide cero y conserva la longitud`() {
        val p = PuntuacionCurvas.medir(TrazasDePrueba.recta(2_000.0))
        assertEquals(0.0, p.curvasM, 0.0)
        assertEquals(0, p.numeroCurvas)
        assertEquals(2_000.0, p.longitudM, 5.0)
    }

    @Test
    fun `una polilinea sin longitud da la puntuacion vacia`() {
        assertEquals(PuntuacionCurvas.VACIA, PuntuacionCurvas.medir(emptyList()))
        val punto = TrazasDePrueba.recta(0.0)
        assertEquals(PuntuacionCurvas.VACIA, PuntuacionCurvas.medir(punto))
    }

    @Test
    fun `una serpiente cuenta casi todo su largo y una curva abierta larguisima no cuenta`() {
        val serpiente =
            TrazasDePrueba.enPlano((0..600).map { i -> (150.0 * sin(i * 5.0 / 120.0)) to i * 5.0 })
        val s = PuntuacionCurvas.medir(serpiente)
        assertTrue("${s.curvasM} de ${s.longitudM}", s.curvasM > 0.8 * s.longitudM)
        val abierta = TrazasDePrueba.enPlano(TrazasDePrueba.arcoEnMetros(radioM = 3_000.0, anguloGrados = 40.0))
        assertEquals(0.0, PuntuacionCurvas.medir(abierta).curvasM, 0.0)
    }

    @Test
    fun `las curvas cerradas separadas mas de 150 m no se funden y las cercanas si`() {
        fun dosHorquillas(rectaEntreM: Double): List<Pair<Double, Double>> {
            val primera =
                TrazasDePrueba.arcoEnMetros(
                    60.0,
                    90.0,
                    izquierda = true,
                    entradaM = 100.0,
                    salidaM = rectaEntreM,
                )
            val (x0, y0) = primera.last()
            val segunda = TrazasDePrueba.arcoEnMetros(60.0, 90.0, izquierda = false, entradaM = 0.0, salidaM = 100.0)
            // La salida de la primera apunta al oeste (gira 90° a la izquierda): la segunda se coloca girada.
            return primera + segunda.map { (x, y) -> (x0 - y) to (y0 + x) }
        }
        val juntas = PuntuacionCurvas.medir(TrazasDePrueba.enPlano(dosHorquillas(100.0)))
        val separadas = PuntuacionCurvas.medir(TrazasDePrueba.enPlano(dosHorquillas(300.0)))
        assertEquals(2, juntas.numeroCurvas)
        assertEquals(2, separadas.numeroCurvas)
        assertTrue("${juntas.curvasM} vs ${separadas.curvasM}", juntas.curvasM > separadas.curvasM + 50.0)
    }
}
