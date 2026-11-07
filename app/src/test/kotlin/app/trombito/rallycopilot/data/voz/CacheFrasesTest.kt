package app.trombito.rallycopilot.data.voz

import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CacheFrasesTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    private fun cache(maxBytes: Long = 1_000) = CacheFrases(carpeta.root, maxBytes)

    private fun pcm(muestras: Int) = ShortArray(muestras) { (it - 3).toShort() }

    private fun archivo(
        voz: VozCopiloto,
        texto: String,
    ) = File(carpeta.root, CacheFrases.clave(voz, texto) + ".pcm")

    @Test
    fun `la clave es voz y sha256 del texto en UTF-8`() {
        // sha256 de la cadena vacia, conocido
        assertEquals(
            "davefx/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            CacheFrases.clave(VozCopiloto.DAVEFX, ""),
        )
        assertNotEquals(CacheFrases.clave(VozCopiloto.DAVEFX, "a"), CacheFrases.clave(VozCopiloto.SHARVARD, "a"))
        assertNotEquals(CacheFrases.clave(VozCopiloto.DAVEFX, "a"), CacheFrases.clave(VozCopiloto.DAVEFX, "b"))
    }

    @Test
    fun `guarda y lee el PCM tal cual en voz sha256 pcm`() {
        val c = cache()
        val audio = pcm(10)

        c.guardar(VozCopiloto.SHARVARD, "izquierda cuatro", audio)

        assertTrue(c.contiene(VozCopiloto.SHARVARD, "izquierda cuatro"))
        assertFalse(c.contiene(VozCopiloto.DAVEFX, "izquierda cuatro"))
        assertEquals(20L, c.bytes(VozCopiloto.SHARVARD, "izquierda cuatro"))
        assertArrayEquals(audio, c.leer(VozCopiloto.SHARVARD, "izquierda cuatro"))
        assertTrue(archivo(VozCopiloto.SHARVARD, "izquierda cuatro").isFile)
        assertNull(c.leer(VozCopiloto.SHARVARD, "otra"))
    }

    @Test
    fun `expulsa lo menos usado al pasar del limite y leer renueva el uso`() {
        val c = cache(maxBytes = 100) // caben 5 frases de 20 bytes
        for (i in 1..5) c.guardar(VozCopiloto.DAVEFX, "f$i", pcm(10))
        c.leer(VozCopiloto.DAVEFX, "f1") // f1 pasa a ser la mas reciente; la mas antigua es f2

        c.guardar(VozCopiloto.DAVEFX, "f6", pcm(10))

        assertTrue(c.contiene(VozCopiloto.DAVEFX, "f1"))
        assertFalse(c.contiene(VozCopiloto.DAVEFX, "f2"))
        assertFalse(archivo(VozCopiloto.DAVEFX, "f2").exists())
        assertTrue(c.contiene(VozCopiloto.DAVEFX, "f6"))
        assertEquals(100L, c.tamanoTotal())
    }

    @Test
    fun `sobrescribir una frase no duplica su tamano`() {
        val c = cache()
        c.guardar(VozCopiloto.DAVEFX, "x", pcm(10))
        c.guardar(VozCopiloto.DAVEFX, "x", pcm(5))

        assertEquals(10L, c.tamanoTotal())
    }

    @Test
    fun `al reabrir reconstruye el indice y expulsa lo mas antiguo por fecha`() {
        val c = cache()
        c.guardar(VozCopiloto.DAVEFX, "vieja", pcm(10))
        c.guardar(VozCopiloto.DAVEFX, "nueva", pcm(10))
        archivo(VozCopiloto.DAVEFX, "vieja").setLastModified(1_000L)
        archivo(VozCopiloto.DAVEFX, "nueva").setLastModified(2_000L)

        val reabierta = CacheFrases(carpeta.root, maxBytes = 20)

        assertFalse(reabierta.contiene(VozCopiloto.DAVEFX, "vieja"))
        assertTrue(reabierta.contiene(VozCopiloto.DAVEFX, "nueva"))
        assertEquals(20L, reabierta.tamanoTotal())
    }

    @Test
    fun `un archivo de tamano impar se descarta`() {
        val ruta = archivo(VozCopiloto.DAVEFX, "rota")
        ruta.parentFile?.mkdirs()
        ruta.writeBytes(ByteArray(7))

        assertFalse(cache().contiene(VozCopiloto.DAVEFX, "rota"))
        assertFalse(ruta.exists())
    }

    @Test
    fun `la velocidad rapida tiene su carpeta y la normal conserva la clave de siempre`() {
        val normal = CacheFrases.clave(VozCopiloto.DAVEFX, "a")
        val rapida = CacheFrases.clave(VozCopiloto.DAVEFX, "a", VelocidadHabla.RAPIDA)

        assertEquals(normal, CacheFrases.clave(VozCopiloto.DAVEFX, "a", VelocidadHabla.NORMAL))
        assertNotEquals(normal, rapida)
        assertTrue(rapida.startsWith("davefx-rapida/"))
        assertEquals(normal.substringAfter('/'), rapida.substringAfter('/'))
    }

    @Test
    fun `cada velocidad guarda y lee su propio audio sin mezclarlos`() {
        val c = cache()

        c.guardar(VozCopiloto.DAVEFX, "a", pcm(10))
        assertFalse(c.contiene(VozCopiloto.DAVEFX, "a", VelocidadHabla.RAPIDA))
        assertNull(c.leer(VozCopiloto.DAVEFX, "a", VelocidadHabla.RAPIDA))

        c.guardar(VozCopiloto.DAVEFX, "a", pcm(6), VelocidadHabla.RAPIDA)

        assertArrayEquals(pcm(10), c.leer(VozCopiloto.DAVEFX, "a"))
        assertArrayEquals(pcm(6), c.leer(VozCopiloto.DAVEFX, "a", VelocidadHabla.RAPIDA))
        assertEquals(20L, c.bytes(VozCopiloto.DAVEFX, "a"))
        assertEquals(12L, c.bytes(VozCopiloto.DAVEFX, "a", VelocidadHabla.RAPIDA))
        assertEquals(32L, c.tamanoTotal())
    }

    @Test
    fun `al reabrir reconoce las frases de las dos velocidades`() {
        cache().apply {
            guardar(VozCopiloto.SHARVARD, "a", pcm(4))
            guardar(VozCopiloto.SHARVARD, "a", pcm(3), VelocidadHabla.RAPIDA)
        }

        val reabierta = cache()

        assertTrue(reabierta.contiene(VozCopiloto.SHARVARD, "a"))
        assertTrue(reabierta.contiene(VozCopiloto.SHARVARD, "a", VelocidadHabla.RAPIDA))
        assertEquals(14L, reabierta.tamanoTotal())
    }

    @Test
    fun `leer no toca la fecha del archivo hasta persistir el uso`() {
        val c = cache()
        c.guardar(VozCopiloto.DAVEFX, "a", pcm(10))
        val fichero = archivo(VozCopiloto.DAVEFX, "a")
        fichero.setLastModified(1_000L)

        c.leer(VozCopiloto.DAVEFX, "a")
        c.leer(VozCopiloto.DAVEFX, "a")
        assertEquals(1_000L, fichero.lastModified())

        c.persistirUso()
        assertTrue(fichero.lastModified() > 1_000L)
    }

    @Test
    fun `persistir el uso conserva el orden al reabrir`() {
        val c = cache(maxBytes = 1_000)
        c.guardar(VozCopiloto.DAVEFX, "vieja", pcm(10))
        c.guardar(VozCopiloto.DAVEFX, "nueva", pcm(10))
        archivo(VozCopiloto.DAVEFX, "vieja").setLastModified(1_000L)
        archivo(VozCopiloto.DAVEFX, "nueva").setLastModified(2_000L)
        c.leer(VozCopiloto.DAVEFX, "vieja") // ahora la mas reciente
        c.persistirUso()

        val reabierta = CacheFrases(carpeta.root, maxBytes = 20)

        assertTrue(reabierta.contiene(VozCopiloto.DAVEFX, "vieja"))
        assertFalse(reabierta.contiene(VozCopiloto.DAVEFX, "nueva"))
    }

    @Test
    fun `persistir el uso sin lecturas pendientes no hace nada y no falla con frases expulsadas`() {
        val c = cache(maxBytes = 20)
        c.persistirUso()
        c.guardar(VozCopiloto.DAVEFX, "a", pcm(10))
        c.leer(VozCopiloto.DAVEFX, "a")
        c.guardar(VozCopiloto.DAVEFX, "b", pcm(10))
        c.guardar(VozCopiloto.DAVEFX, "c", pcm(10)) // expulsa a "a" y "b" (limite de 20 bytes)

        c.persistirUso()

        assertFalse(archivo(VozCopiloto.DAVEFX, "a").exists())
        assertTrue(c.contiene(VozCopiloto.DAVEFX, "c"))
    }
}
