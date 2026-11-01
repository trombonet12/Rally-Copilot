package app.trombito.rallycopilot.data.datosmapa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExtractorZipTest {
    @get:Rule val carpeta = TemporaryFolder()

    private fun zip(vararg nombres: String): File =
        carpeta.newFile().also { f -> f.writeBytes(PaqueteDePrueba.zip(nombres.associateWith { "x" })) }

    @Test
    fun `extrae entradas normales aunque el nombre lleve un punto no inicial`() {
        val destino = File(carpeta.root, "ok")

        ExtractorZip.extraer(zip("glyphs/Noto/0-255.pbf", "estilo.v2.json"), destino)

        assertTrue(File(destino, "glyphs/Noto/0-255.pbf").isFile)
        assertTrue(File(destino, "estilo.v2.json").isFile)
    }

    @Test
    fun `rechaza cualquier segmento que empiece por punto`() {
        val malos = listOf(".nuevo/x", ".oculto", "a/.oculto/b", "a/.git/config", "a/.nuevo", ".")
        for (nombre in malos) {
            val destino = File(carpeta.root, "d" + nombre.hashCode())
            assertThrows(nombre, ZipInseguroException::class.java) { ExtractorZip.extraer(zip(nombre), destino) }
            assertEquals(nombre, 0, destino.walkTopDown().count { it.isFile })
        }
    }
}
