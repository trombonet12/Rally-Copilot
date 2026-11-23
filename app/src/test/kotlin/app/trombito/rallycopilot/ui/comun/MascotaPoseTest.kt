package app.trombito.rallycopilot.ui.comun

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val LADO_PX = 432
private const val CALIDAD_PNG = 100
private const val OPACO = 255
private const val MASCARA_RGB = 0xFFFFFF

/**
 * Pinta los tres VectorDrawable de las poses con los gráficos nativos de Robolectric y comprueba que dibujan de verdad
 * (píxeles opacos, cuadros negros de la bandera dentro de su clip, las «z» del dormido). Deja los PNG en
 * `build/tmp/mascotas/` para revisarlos a ojo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MascotaPoseTest {
    private fun pintar(pose: PoseMascota): Bitmap {
        val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dibujo = checkNotNull(contexto.getDrawable(pose.recurso)).mutate()
        val mapa = Bitmap.createBitmap(LADO_PX, LADO_PX, Bitmap.Config.ARGB_8888)
        dibujo.setBounds(0, 0, LADO_PX, LADO_PX)
        dibujo.draw(Canvas(mapa))
        val carpeta = File("build/tmp/mascotas").apply { mkdirs() }
        File(carpeta, "${pose.name.lowercase()}.png").outputStream().use {
            mapa.compress(Bitmap.CompressFormat.PNG, CALIDAD_PNG, it)
        }
        return mapa
    }

    private fun opacos(mapa: Bitmap): Int {
        var total = 0
        for (y in 0 until mapa.height) {
            for (x in 0 until mapa.width) {
                if (Color.alpha(mapa.getPixel(x, y)) > 0) total++
            }
        }
        return total
    }

    private fun contar(
        mapa: Bitmap,
        rgb: Int,
    ): Int {
        var total = 0
        for (y in 0 until mapa.height) {
            for (x in 0 until mapa.width) {
                val p = mapa.getPixel(x, y)
                if (Color.alpha(p) == OPACO && (p and MASCARA_RGB) == rgb) total++
            }
        }
        return total
    }

    @Test
    fun `cada pose pinta y las tres difieren entre si`() {
        val mapas = PoseMascota.entries.associateWith { pintar(it) }
        mapas.forEach { (pose, mapa) ->
            assertTrue("$pose casi vacía", opacos(mapa) > LADO_PX * LADO_PX / 5)
            // El casco rojo de la mascota (#D8232A) está en todas.
            assertTrue("$pose sin casco", contar(mapa, 0xD8232A) > 1000)
        }
        val firmas = mapas.values.map { opacos(it) }.toSet()
        assertEquals("Las poses no pueden pintar lo mismo", PoseMascota.entries.size, firmas.size)
    }

    @Test
    fun `la bandera pinta cuadros negros recortados dentro de la tela`() {
        val mapa = pintar(PoseMascota.BANDERA)
        // Negro de los cuadros (#23262B): hay (los dos paños) pero sin salirse de su tela, que ocupa poco.
        val negros = contar(mapa, 0x23262B)
        assertTrue("Sin cuadros negros: $negros", negros > 300)
        assertTrue("Cuadros fuera de la tela (clip roto): $negros", negros < LADO_PX * LADO_PX / 8)
    }

    @Test
    fun `el recurso de cada pose existe y se llama como su drawable`() {
        assertEquals(R.drawable.mascota_saludando, PoseMascota.SALUDANDO.recurso)
        assertEquals(R.drawable.mascota_bandera, PoseMascota.BANDERA.recurso)
        assertEquals(R.drawable.mascota_dormido, PoseMascota.DORMIDO.recurso)
    }
}
