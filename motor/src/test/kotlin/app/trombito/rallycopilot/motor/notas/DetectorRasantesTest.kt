package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Perfiles sintéticos cada 5 m a partir de una pendiente (%) por tramos. */
class DetectorRasantesTest {
    private val detector = DetectorRasantes()

    /** Perfil de [largoM] m cuya pendiente en % la da [pendiente] en función de la posición. */
    private fun perfil(
        largoM: Int,
        pendiente: (Double) -> Double,
    ): List<Double> {
        var h = 100.0
        return (0..largoM / 5).map { k ->
            if (k > 0) h += pendiente((k - 0.5) * 5.0) / 100.0 * 5.0
            h
        }
    }

    @Test
    fun `una cresta de subida a bajada es una rasante en su posicion`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) 8.0 else -8.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.RASANTE, r.single().tipo)
        assertEquals(150.0, r.single().posicionM, 5.0)
    }

    @Test
    fun `la cresta se localiza donde esta y no en una muestra fija`() {
        val r = detector.detectar(perfil(400) { if (it < 237.0) 7.0 else -5.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.RASANTE, r.single().tipo)
        assertEquals(237.0, r.single().posicionM, 6.0)
    }

    @Test
    fun `una cresta con caida de pendiente brusca es un salto`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) 12.0 else -12.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.SALTO, r.single().tipo)
        assertEquals(150.0, r.single().posicionM, 5.0)
    }

    @Test
    fun `una vaguada es un cambio de rasante y no una cresta`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) -8.0 else 8.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.CAMBIO_RASANTE, r.single().tipo)
        assertEquals(150.0, r.single().posicionM, 8.0)
    }

    @Test
    fun `un escalon de pendiente es un cambio de rasante`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) 0.0 else 8.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.CAMBIO_RASANTE, r.single().tipo)
        assertEquals(150.0, r.single().posicionM, 8.0)
    }

    @Test
    fun `una rampa constante no genera nada ni en los extremos`() {
        assertTrue(detector.detectar(perfil(300) { 6.0 }).isEmpty())
        assertTrue(detector.detectar(perfil(300) { -9.0 }).isEmpty())
        assertTrue(detector.detectar(perfil(300) { 0.0 }).isEmpty())
    }

    @Test
    fun `una ondulacion suave por debajo de los umbrales no genera nada`() {
        assertTrue(detector.detectar(perfil(300) { if (it < 150.0) 2.5 else -2.5 }).isEmpty())
    }

    @Test
    fun `perfiles demasiado cortos o vacios no dan rasantes`() {
        assertTrue(detector.detectar(emptyList()).isEmpty())
        assertTrue(detector.detectar(listOf(1.0, 2.0)).isEmpty())
    }

    @Test
    fun `dos rasantes separadas mas de 30 m se conservan ordenadas`() {
        val r =
            detector.detectar(
                perfil(600) {
                    if (it < 150.0) {
                        0.0
                    } else if (it < 400.0) {
                        8.0
                    } else {
                        -8.0
                    }
                },
            )
        assertEquals(listOf(TipoRasante.CAMBIO_RASANTE, TipoRasante.RASANTE), r.map { it.tipo })
        assertEquals(150.0, r[0].posicionM, 8.0)
        assertEquals(400.0, r[1].posicionM, 6.0)
    }

    @Test
    fun `no hay duplicados a menos de 30 m`() {
        // Cresta fuerte con un cambio de pendiente justo detrás, dentro de la separación mínima.
        val r =
            detector.detectar(
                perfil(400) {
                    if (it < 150.0) {
                        14.0
                    } else if (it < 170.0) {
                        -14.0
                    } else {
                        -2.0
                    }
                },
            )
        assertTrue(r.isNotEmpty())
        assertTrue(r.zipWithNext().all { (a, b) -> b.posicionM - a.posicionM >= 30.0 })
        assertTrue(r.any { it.tipo != TipoRasante.CAMBIO_RASANTE })
    }

    @Test
    fun `los umbrales son parametros`() {
        val escalon = perfil(300) { if (it < 150.0) 0.0 else 8.0 }
        assertEquals(1, detector.detectar(escalon).size)
        assertTrue(DetectorRasantes(UmbralesRasantes(variacionCambioPuntos = 7.5)).detectar(escalon).isEmpty())
        val cresta = perfil(300) { if (it < 150.0) 8.0 else -8.0 }
        val estricto = DetectorRasantes(UmbralesRasantes(pendienteSubidaCrestaPct = 9.0, variacionSaltoPuntos = 99.0))
        assertTrue(estricto.detectar(cresta).none { it.tipo == TipoRasante.RASANTE })
    }
}
