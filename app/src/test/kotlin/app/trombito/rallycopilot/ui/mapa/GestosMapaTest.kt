package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.gestures.AndroidGesturesManager
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GestosMapaTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `inclinar es mas facil que girar con dos dedos en vertical`() {
        val gestos = AndroidGesturesManager(contexto)
        // Los valores del SDK: inclinar exige 16 dp, solo con los dedos casi en horizontal (20 grados), mientras que
        // girar se dispara con 3 grados y, al ser excluyentes, se llevaba casi todos los gestos de dos dedos.
        gestos.rotateGestureDetector.angleThreshold = 3f
        gestos.shoveGestureDetector.maxShoveAngle = 20f
        gestos.shoveGestureDetector.pixelDeltaThreshold = 16f * DENSIDAD

        ajustarGestosMapa(gestos, DENSIDAD)

        assertEquals(UMBRAL_INCLINAR_DP * DENSIDAD, gestos.shoveGestureDetector.pixelDeltaThreshold, 0.001f)
        assertEquals(ANGULO_MAXIMO_INCLINAR, gestos.shoveGestureDetector.maxShoveAngle, 0.001f)
        assertEquals(ANGULO_MINIMO_GIRAR, gestos.rotateGestureDetector.angleThreshold, 0.001f)
        assertTrue(gestos.shoveGestureDetector.pixelDeltaThreshold < 16f * DENSIDAD)
        assertTrue(gestos.shoveGestureDetector.maxShoveAngle > 20f)
        assertTrue(gestos.rotateGestureDetector.angleThreshold > 3f)
    }

    private companion object {
        const val DENSIDAD = 2.5f
    }
}
