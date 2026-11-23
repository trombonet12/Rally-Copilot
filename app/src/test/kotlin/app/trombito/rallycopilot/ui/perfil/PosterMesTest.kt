package app.trombito.rallycopilot.ui.perfil

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.CategoriaHito
import app.trombito.rallycopilot.motor.analisis.Hito
import app.trombito.rallycopilot.motor.analisis.RecordBatido
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.RojoAcento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * El póster del resumen del mes: lo que se dibuja (solo cifras, el nivel, hitos y referencias de carretera), la imagen,
 * el archivo en la caché y el envío por el menú Compartir. Nada de ubicación en ninguna parte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterMesTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val resumen = temporadaDePrueba().temporada.resumenMes

    /** Un resumen con todo lo que podría delatar un sitio: calles, destinos, nombres de tramo y de récord. */
    private val resumenConLugares =
        resumen.copy(
            carreterasNuevas = listOf("Ma-10", "Carrer de Sant Miquel", "A-11", "Passeig del Born", "Ma-2141"),
            records =
                listOf(
                    RecordBatido("t", "Sóller - Deià (Cala Tuent)", "p", diaMs(17), 240_000L, 250_000L, 10_000L),
                ),
            hitosConseguidos =
                listOf(Hito("km-100", CategoriaHito.KM, 100, "100 km", 100.0, 1.0, diaMs(10))),
        )

    private fun contenido(r: ResumenMes = resumenConLugares) = ContenidoPoster.de(r, contexto.resources)

    @Test
    fun `el poster lleva el mes, los kilometros, las cifras y el nivel`() {
        val c = contenido()
        assertEquals("Mi noviembre de 2026", c.titulo)
        assertEquals("420,0", c.kilometros)
        assertEquals(
            listOf("3" to "viajes", "5" to "carreteras nuevas", "1" to "hitos", "1" to "récords"),
            c.cifras.map { it.valor to it.etiqueta },
        )
        assertEquals("Piloto de club", c.nivel)
        assertTrue(c.puntos, Regex("""\d+/1200""").matches(c.puntos))
        assertEquals(listOf("100 km"), c.hitos)
    }

    @Test
    fun `solo entran las referencias de carretera, nunca nombres de calles ni de lugares`() {
        val c = contenido()
        assertEquals(listOf("Ma-10", "A-11", "Ma-2141"), c.refsCarreteras)
        val dibujado = c.textos().joinToString(" | ")
        listOf("Sant Miquel", "Born", "Sóller", "Deià", "Tuent", "Carrer", "Passeig", "Tramo").forEach {
            assertFalse("«$it» en $dibujado", it in dibujado)
        }
    }

    @Test
    fun `ningun texto del poster parece una coordenada`() {
        val coordenada = Regex("""-?\d{1,3}[.,]\d{4,}""")
        contenido().textos().forEach { assertFalse("«$it» parece una coordenada", coordenada.containsMatchIn(it)) }
        contenido().textos().forEach { assertFalse("«$it»", it.contains("°") || it.contains("lat", ignoreCase = true)) }
    }

    @Test
    fun `como mucho se dibujan tres hitos y cinco placas`() {
        val muchos =
            resumenConLugares.copy(
                carreterasNuevas = (1..9).map { "Ma-$it" },
                hitosConseguidos =
                    (1..6).map { Hito("h$it", CategoriaHito.KM, it, "$it km", it.toDouble(), 1.0, diaMs(10)) },
            )
        val c = contenido(muchos)
        assertEquals(3, c.hitos.size)
        assertEquals(5, c.refsCarreteras.size)
        // Pero las cifras cuentan todo.
        assertEquals("9", c.cifras[1].valor)
        assertEquals("6", c.cifras[2].valor)
    }

    @Test
    fun `en el nivel maximo no hay siguiente y se dicen los puntos`() {
        val leyenda = resumen.nivel.copy(puntos = 12_000, siguiente = null, umbralSiguiente = null, progreso = 1.0)
        val c = contenido(resumen.copy(nivel = leyenda))
        assertEquals("12000 puntos", c.puntos)
        assertEquals(1f, c.progreso, 0f)
    }

    private fun mapaDelPoster(): Bitmap {
        val mascota = ContextCompat.getDrawable(contexto, R.drawable.mascota_bandera)?.mutate()
        return dibujarPoster(contenido(), mascota)
    }

    @Test
    fun `la imagen es un 4 por 5 con franja roja, fondo carbon y a Trombito`() {
        val mapa = mapaDelPoster()
        assertEquals(1080, mapa.width)
        assertEquals(1350, mapa.height)
        assertEquals(RojoAcento.toArgb(), mapa.getPixel(5, 5))
        assertEquals(CarbonOpaco.toArgb(), mapa.getPixel(540, 1340))
        // Trombito está abajo a la derecha: hay píxeles que no son el fondo en su cuadro.
        var distintos = 0
        for (y in 1000 until 1280 step 4) {
            for (x in 740 until 1010 step 4) if (mapa.getPixel(x, y) != CarbonOpaco.toArgb()) distintos++
        }
        assertTrue("Trombito no se dibuja: $distintos", distintos > 200)
    }

    @Test
    fun `sin mascota la imagen sigue saliendo`() {
        val mapa = dibujarPoster(contenido(), null)
        assertEquals(1080, mapa.width)
    }

    @Test
    fun `el archivo se guarda en la cache de compartir con un nombre sin ubicacion`() {
        val archivo = escribirPoster(contexto, resumenConLugares)
        assertNotNull(archivo)
        assertEquals(File(File(contexto.cacheDir, "compartir"), "resumen-2026-11.png"), archivo)
        assertTrue((archivo?.length() ?: 0L) > 0L)
        assertEquals("resumen-2026-11.png", nombreArchivoPoster(resumenConLugares))
    }

    @Test
    fun `los posters de otras veces se borran pero lo demas de la carpeta no`() {
        val carpeta = File(contexto.cacheDir, "compartir").apply { mkdirs() }
        val viejo = File(carpeta, "resumen-2026-10.png").apply { writeText("viejo") }
        val gpx = File(carpeta, "tramo.gpx").apply { writeText("<gpx/>") }
        escribirPoster(contexto, resumenConLugares)
        assertFalse(viejo.exists())
        assertTrue(gpx.exists())
    }

    @Test
    fun `el envio es un SEND de PNG con solo la imagen y permiso temporal de lectura`() {
        val uri = Uri.parse("content://${contexto.packageName}.compartir/compartir/resumen-2026-11.png")

        val selector = intentCompartirPoster(uri, "Compartir resumen del mes")
        assertEquals(Intent.ACTION_CHOOSER, selector.action)
        @Suppress("DEPRECATION")
        val envio = selector.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, envio?.action)
        assertEquals(TIPO_POSTER, envio?.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, envio?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue((envio?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(envio?.clipData is ClipData)
        // Nada más sale con la imagen: ni texto, ni asunto, ni ubicación.
        assertNull(envio?.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(envio?.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(setOf(Intent.EXTRA_STREAM), envio?.extras?.keySet().orEmpty())
    }
}
