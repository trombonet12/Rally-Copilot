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
    fun `una cresta con caida de pendiente de 20 puntos o mas en menos de 30 m es un salto`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) 20.0 else -20.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.SALTO, r.single().tipo)
        assertEquals(150.0, r.single().posicionM, 5.0)
    }

    @Test
    fun `una cresta con caida de 12 puntos ya no es un salto sino una rasante`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) 12.0 else -12.0 })
        assertEquals(listOf(TipoRasante.RASANTE), r.map { it.tipo })
    }

    @Test
    fun `una vaguada concava marcada es una compresion en su posicion`() {
        for (subida in listOf(-8.0 to 8.0, 0.0 to 14.0, -14.0 to 0.0)) {
            val r = detector.detectar(perfil(300) { if (it < 150.0) subida.first else subida.second })
            assertEquals(subida.toString(), listOf(TipoRasante.COMPRESION), r.map { it.tipo })
            assertEquals(150.0, r.single().posicionM, 8.0)
        }
    }

    @Test
    fun `una vaguada concava suave por debajo de 10 puntos no se avisa`() {
        assertTrue(detector.detectar(perfil(300) { if (it < 150.0) 0.0 else 7.0 }).isEmpty())
        assertTrue(detector.detectar(perfil(300) { if (it < 150.0) -4.0 else 4.0 }).isEmpty())
    }

    @Test
    fun `la compresion es simetrica del cambio de rasante convexo`() {
        val convexo = detector.detectar(perfil(300) { if (it < 150.0) 14.0 else 0.0 }).single()
        val concavo = detector.detectar(perfil(300) { if (it < 150.0) -14.0 else 0.0 }).single()
        assertEquals(TipoRasante.CAMBIO_RASANTE, convexo.tipo)
        assertEquals(TipoRasante.COMPRESION, concavo.tipo)
        assertEquals(convexo.posicionM, concavo.posicionM, 1.0)
    }

    @Test
    fun `una compresion y un cambio de rasante a menos de 60 m no se duplican`() {
        // Escalon convexo hacia los 150 m y vaguada a los 195 m: solo queda el primero.
        val r =
            detector.detectar(
                perfil(400) {
                    if (it < 150.0) {
                        16.0
                    } else if (it < 195.0) {
                        0.0
                    } else {
                        16.0
                    }
                },
            )
        assertEquals(r.toString(), 1, r.size)
        assertEquals(TipoRasante.CAMBIO_RASANTE, r.single().tipo)
    }

    @Test
    fun `una compresion se conserva a mas de 60 m de una rasante`() {
        val r =
            detector.detectar(
                perfil(600) {
                    if (it < 150.0) {
                        -8.0
                    } else if (it < 350.0) {
                        8.0
                    } else {
                        -8.0
                    }
                },
            )
        assertEquals(r.toString(), listOf(TipoRasante.COMPRESION, TipoRasante.RASANTE), r.map { it.tipo })
        assertEquals(150.0, r[0].posicionM, 8.0)
        assertEquals(350.0, r[1].posicionM, 6.0)
    }

    @Test
    fun `un escalon convexo de pendiente es un cambio de rasante`() {
        val r = detector.detectar(perfil(300) { if (it < 150.0) 12.0 else 0.0 })
        assertEquals(1, r.size)
        assertEquals(TipoRasante.CAMBIO_RASANTE, r.single().tipo)
        assertEquals(150.0, r.single().posicionM, 8.0)
    }

    @Test
    fun `un escalon convexo por debajo de 8 puntos no se avisa`() {
        assertTrue(detector.detectar(perfil(300) { if (it < 150.0) 7.0 else 0.0 }).isEmpty())
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
    fun `dos rasantes separadas mas de 60 m se conservan ordenadas`() {
        val r =
            detector.detectar(
                perfil(600) {
                    if (it < 150.0) {
                        12.0
                    } else if (it < 250.0) {
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
    fun `no hay duplicados a menos de 60 m`() {
        // Cresta con un cambio convexo de pendiente 45 m despues, dentro de la separacion minima.
        val r =
            detector.detectar(
                perfil(400) {
                    if (it < 150.0) {
                        10.0
                    } else if (it < 195.0) {
                        -4.0
                    } else {
                        -20.0
                    }
                },
            )
        assertEquals(r.toString(), listOf(TipoRasante.RASANTE), r.map { it.tipo })
        assertEquals(150.0, r.single().posicionM, 12.0)
    }

    @Test
    fun `los umbrales son parametros`() {
        val escalon = perfil(300) { if (it < 150.0) 12.0 else 0.0 }
        assertEquals(1, detector.detectar(escalon).size)
        assertTrue(DetectorRasantes(UmbralesRasantes(variacionCambioPuntos = 11.0)).detectar(escalon).isEmpty())
        val cresta = perfil(300) { if (it < 150.0) 8.0 else -8.0 }
        val estricto = DetectorRasantes(UmbralesRasantes(pendienteSubidaCrestaPct = 9.0, variacionSaltoPuntos = 99.0))
        assertTrue(estricto.detectar(cresta).none { it.tipo == TipoRasante.RASANTE })
        val vaguada = perfil(300) { if (it < 150.0) -8.0 else 8.0 }
        assertEquals(1, detector.detectar(vaguada).size)
        assertTrue(DetectorRasantes(UmbralesRasantes(variacionCompresionPuntos = 20.0)).detectar(vaguada).isEmpty())
    }
}
