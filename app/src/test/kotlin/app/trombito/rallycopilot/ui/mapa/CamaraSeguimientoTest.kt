package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.ajustes.Orientacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalStateException

@OptIn(ExperimentalCoroutinesApi::class)
class CamaraSeguimientoTest {
    @Test
    fun `con rumbo arriba el mapa se inclina y con norte arriba va plano`() {
        assertEquals(50.0, inclinacionSeguimiento(Orientacion.RUMBO), 0.0)
        assertEquals(0.0, inclinacionSeguimiento(Orientacion.NORTE), 0.0)
    }

    @Test
    fun `en vertical el marcador queda hacia el 80 por ciento de la altura`() {
        val alto = 2000
        val relleno = rellenoSuperiorSeguimiento(anchoPx = 1080, altoPx = alto)
        assertEquals(0.80, centroVisible(relleno, alto), 0.001)
    }

    @Test
    fun `en horizontal baja menos que en vertical`() {
        val alto = 1080
        val horizontal = rellenoSuperiorSeguimiento(anchoPx = 2000, altoPx = alto)
        assertEquals(0.70, centroVisible(horizontal, alto), 0.001)
        assertTrue(horizontal.toDouble() / alto < rellenoSuperiorSeguimiento(1080, 2000).toDouble() / 2000)
    }

    @Test
    fun `sin alto medido no hay relleno`() {
        assertEquals(0, rellenoSuperiorSeguimiento(anchoPx = 0, altoPx = 0))
    }

    @Test
    fun `sin zona medida o en horizontal el coche queda a la altura de siempre`() {
        assertEquals(0.80, fraccionMarcador(1080, 2000), 0.0)
        assertEquals(0.80, fraccionMarcador(1080, 2000, zonaInferiorPx = 0, densidad = 3f), 0.0)
        assertEquals(0.70, fraccionMarcador(2000, 1080, zonaInferiorPx = 400, densidad = 3f), 0.0)
        assertEquals(rellenoSuperiorSeguimiento(1080, 2000), rellenoSuperiorSeguimiento(1080, 2000, 0.80))
    }

    @Test
    fun `una zona baja deja el coche donde estaba y una alta lo sube por encima de ella`() {
        // 412x915 dp a 2,625 px/dp: el 20 % de abajo son 183 dp; con 120 dp de zona el coche cabe en el 80 %.
        val d = 2.625f
        val alto = (915 * d).toInt()
        val baja = fraccionMarcador((412 * d).toInt(), alto, zonaInferiorPx = (120 * d).toInt(), densidad = d)
        assertEquals(0.80, baja, 0.0)
        val alta = fraccionMarcador((412 * d).toInt(), alto, zonaInferiorPx = (216 * d).toInt(), densidad = d)
        assertTrue(alta < 0.80)
        assertEquals((915 - 216 - 12 - 30).toDouble(), alta * 915, 0.5)
    }

    @Test
    fun `el coche queda sobre el borde de la zona en 360x640 y 412x915 con fuente, media y flecha`() {
        // (ancho dp, alto dp, densidad, zona dp): la zona crece con la fuente 1,3, la media (60) y la flecha (32).
        val casos =
            listOf(
                Caso(360, 640, 2f, 192),
                Caso(360, 640, 2f, 192 + 32 + 60),
                Caso(412, 915, 2.625f, 216),
                Caso(412, 915, 2.625f, 216 + 32 + 60 + 20),
            )
        casos.forEach { c ->
            val alto = c.altoDp * c.densidad
            val zona = (c.zonaDp * c.densidad).toInt()
            val fraccion = fraccionMarcador((c.anchoDp * c.densidad).toInt(), alto.toInt(), zona, c.densidad)
            val centroDp = fraccion * c.altoDp
            val bordeZonaDp = c.altoDp - c.zonaDp
            // Si no cabe a la altura de siempre, el icono entero (mitad 30 dp) queda a 12 dp o más sobre la zona.
            val esperado = minOf(0.80 * c.altoDp, bordeZonaDp - 12.0 - 30.0)
            assertEquals("caso $c", maxOf(esperado, 0.5 * c.altoDp), centroDp, 0.5)
            if (centroDp > 0.5 * c.altoDp + 0.5) assertTrue("caso $c", centroDp + 30 + 12 <= bordeZonaDp + 0.5)
        }
    }

    @Test
    fun `una zona enorme no sube el coche por encima del centro y el relleno nunca es negativo`() {
        assertEquals(0.5, fraccionMarcador(1080, 2000, zonaInferiorPx = 1900, densidad = 3f), 0.0)
        assertEquals(0, rellenoSuperiorSeguimiento(1080, 2000, fraccion = 0.5))
        assertEquals(0, rellenoSuperiorSeguimiento(1080, 2000, fraccion = 0.3))
    }

    @Test
    fun `el relleno con la fraccion de la zona deja el centro visible en esa fraccion`() {
        val alto = 2400
        val fraccion = fraccionMarcador(1080, alto, zonaInferiorPx = 600, densidad = 3f)
        assertEquals(fraccion, centroVisible(rellenoSuperiorSeguimiento(1080, alto, fraccion), alto), 0.001)
    }

    private data class Caso(
        val anchoDp: Int,
        val altoDp: Int,
        val densidad: Float,
        val zonaDp: Int,
    )

    @Test
    fun `la zona sube de inmediato y baja solo si se mantiene mas baja 3 segundos`() =
        runTest {
            val medidas = MutableSharedFlow<Int>()
            val vistas = mutableListOf<Int>()
            val trabajo = launch { medidas.sinRebotes(3_000L).collect { vistas += it } }
            runCurrent()
            medidas.emit(200)
            runCurrent()
            medidas.emit(260) // aparece la flecha de exceso: sube ya
            runCurrent()
            advanceTimeBy(1_000)
            medidas.emit(200) // desaparece: espera
            runCurrent()
            advanceTimeBy(2_000)
            medidas.emit(260) // vuelve a aparecer antes de los 3 s: no ha bajado
            runCurrent()
            advanceTimeBy(5_000)
            assertEquals(listOf(200, 260, 260), vistas)
            medidas.emit(200)
            runCurrent()
            advanceTimeBy(2_999)
            assertEquals("antes de 3 s sigue arriba", 3, vistas.size)
            advanceTimeBy(2)
            runCurrent()
            assertEquals("pasados 3 s baja", 200, vistas.last())
            assertEquals(4, vistas.size)
            trabajo.cancel()
        }

    @Test
    fun `un valor intermedio mas bajo reinicia la espera de la bajada`() =
        runTest {
            val medidas = MutableSharedFlow<Int>()
            val vistas = mutableListOf<Int>()
            val trabajo = launch { medidas.sinRebotes(3_000L).collect { vistas += it } }
            runCurrent()
            medidas.emit(300)
            runCurrent()
            medidas.emit(200)
            runCurrent()
            advanceTimeBy(2_000)
            medidas.emit(150)
            runCurrent()
            advanceTimeBy(2_000)
            assertEquals("la espera se reinició con 150", listOf(300), vistas)
            advanceTimeBy(1_100)
            runCurrent()
            assertEquals(listOf(300, 150), vistas)
            trabajo.cancel()
        }

    @Test
    fun `el descarte del seguimiento mientras la app mueve la cámara no es del usuario`() {
        val guarda = DescarteSeguimiento()
        assertTrue(guarda.esDelUsuario())
        guarda.aplicando {
            // MapLibre avisa del descarte al cambiar de modo (p. ej. norte arriba a rumbo arriba): lo provoca la app.
            assertFalse(guarda.esDelUsuario())
        }
        assertTrue(guarda.esDelUsuario())
    }

    @Test
    fun `si aplicar la cámara falla el descarte vuelve a ser del usuario`() {
        val guarda = DescarteSeguimiento()
        try {
            guarda.aplicando { throw IllegalStateException("fallo del mapa") }
        } catch (_: IllegalStateException) {
            // esperado: lo que se comprueba es que el estado se restablece
        }
        assertTrue(guarda.esDelUsuario())
    }

    /** Fracción del alto a la que cae el centro de la zona visible (donde la cámara pone el marcador). */
    private fun centroVisible(
        relleno: Int,
        alto: Int,
    ) = (relleno + (alto - relleno) / 2.0) / alto
}
