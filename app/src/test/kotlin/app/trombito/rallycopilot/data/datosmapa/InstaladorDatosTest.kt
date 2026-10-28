package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstaladorDatosTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var instalador: InstaladorDatos

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        instalador = InstaladorDatos(entorno.config)
    }

    @After
    fun cerrar() = entorno.close()

    /** Deja el archivo como si ya estuviera descargado y verificado en `.nuevo/`. */
    private fun verificar(
        id: String,
        bytes: ByteArray,
    ): ArchivoDatos {
        val archivo = entorno.archivo(id, bytes, "v2")
        instalador.verificado(archivo).also {
            it.parentFile?.mkdirs()
            it.writeBytes(bytes)
        }
        return archivo
    }

    private fun instalarViejo(
        nombre: String,
        texto: String,
    ) = File(entorno.mapa, nombre).also {
        it.parentFile?.mkdirs()
        it.writeText(texto)
    }

    @Test
    fun `instala los archivos, descomprime el zip y borra lo sobrante`() {
        val contenidos = PaqueteDePrueba.contenidos("v2")
        val archivos = contenidos.map { (id, bytes) -> verificar(id, bytes) }
        instalarViejo("sobrante.bin", "viejo")
        instalarViejo("mallorca.pmtiles", "viejo")

        instalador.instalar(archivos, listOf("sobrante.bin"))

        assertArrayEquals(contenidos.getValue("vectorial"), File(entorno.mapa, "mallorca.pmtiles").readBytes())
        assertArrayEquals(contenidos.getValue("vias"), File(entorno.mapa, "vias-mallorca.bin").readBytes())
        assertTrue(File(entorno.mapa, "vectorial.json").readText().contains("""{{DIR}}"""))
        assertEquals("glifos v2", File(entorno.mapa, "glyphs/Noto/0-255.pbf").readText())
        assertFalse("El zip no se queda en mapa/", File(entorno.mapa, "estilo-mapa.zip").exists())
        assertFalse(File(entorno.mapa, "sobrante.bin").exists())
        assertFalse(".nuevo se limpia", entorno.config.directorioNuevo.exists())
    }

    @Test
    fun `un zip con entrada que sale de la carpeta se rechaza sin tocar nada`() {
        val zipMalo = PaqueteDePrueba.zip(mapOf("vectorial.json" to "{}", "../evil.txt" to "pwned"))
        val estilo = verificar("estilo", zipMalo)
        val vectorial = verificar("vectorial", PaqueteDePrueba.aleatorio(1))
        val viejo = instalarViejo("mallorca.pmtiles", "viejo")

        try {
            instalador.instalar(listOf(vectorial, estilo), emptyList())
            fail("Debía rechazar el zip")
        } catch (expected: ZipInseguroException) {
            // esperado
        }

        assertFalse("No debe escribir fuera", File(entorno.mapa.parentFile, "evil.txt").exists())
        assertFalse(File(entorno.mapa, "evil.txt").exists())
        assertEquals("viejo", viejo.readText())
        assertTrue("Lo verificado sigue en .nuevo para reintentar", instalador.estaVerificado(vectorial))
    }

    @Test
    fun `un zip con ruta absoluta o que apunta a nuevo se rechaza`() {
        listOf("/tmp/evil.txt", "..\\evil.txt", ".nuevo/x.bin", "C:/evil.txt").forEach { nombre ->
            val estilo = verificar("estilo", PaqueteDePrueba.zip(mapOf(nombre to "x")))
            try {
                instalador.instalar(listOf(estilo), emptyList())
                fail("Debía rechazar la entrada $nombre")
            } catch (expected: ZipInseguroException) {
                // esperado
            }
        }
    }

    @Test
    fun `un zip con una entrada que pisaria otro archivo del manifest se rechaza sin tocar nada`() {
        val zipMalo = PaqueteDePrueba.zip(mapOf("vectorial.json" to "{}", "vias-mallorca.bin" to "falso"))
        val estilo = verificar("estilo", zipMalo)
        val vias = verificar("vias", PaqueteDePrueba.aleatorio(2, 40_000))
        val viejo = instalarViejo("vias-mallorca.bin", "viejo")

        assertThrows(ZipInseguroException::class.java) { instalador.instalar(listOf(estilo, vias), emptyList()) }

        assertEquals("viejo", viejo.readText())
        assertFalse(File(entorno.mapa, "vectorial.json").exists())
    }

    @Test
    fun `un zip no puede pisar un archivo del manifest que no se descarga ahora`() {
        val zipMalo = PaqueteDePrueba.zip(mapOf("pnoa-mallorca-z16.pmtiles" to "falso"))
        val estilo = verificar("estilo", zipMalo)
        val satelite = entorno.archivo("satelite", PaqueteDePrueba.aleatorio(3), "v2")

        assertThrows(ZipInseguroException::class.java) {
            instalador.instalar(listOf(estilo), emptyList(), listOf(satelite.nombre))
        }
    }

    @Test
    fun `una entrada con el mismo nombre en una subcarpeta no es un conflicto`() {
        val estilo = verificar("estilo", PaqueteDePrueba.zip(mapOf("glyphs/vias-mallorca.bin" to "x")))
        val vias = verificar("vias", PaqueteDePrueba.aleatorio(2, 40_000))

        instalador.instalar(listOf(estilo, vias), emptyList())

        assertTrue(File(entorno.mapa, "glyphs/vias-mallorca.bin").isFile)
    }

    @Test
    fun `un zip con mas de 2000 entradas se rechaza y con 2000 se acepta`() {
        val aceptable = verificar("estilo", PaqueteDePrueba.zip((1..2000).associate { "g/$it.pbf" to "x" }))
        instalador.instalar(listOf(aceptable), emptyList())
        assertTrue(File(entorno.mapa, "g/2000.pbf").isFile)

        val excesivo = verificar("estilo", PaqueteDePrueba.zip((1..2001).associate { "g/$it.pbf" to "x" }))
        assertThrows(ZipInseguroException::class.java) { instalador.instalar(listOf(excesivo), emptyList()) }
    }

    @Test
    fun `cuenta como espacio ya gastado lo que hay en nuevo`() {
        val a = entorno.archivo("vectorial", PaqueteDePrueba.aleatorio(1, 1000), "v2")
        val b = entorno.archivo("vias", PaqueteDePrueba.aleatorio(2, 500), "v2")
        instalador.parcial(a).also {
            it.parentFile?.mkdirs()
            it.writeBytes(ByteArray(300))
        }
        verificar("vias", PaqueteDePrueba.aleatorio(2, 500))

        assertEquals(800L, instalador.bytesEnCurso(listOf(a, b)))
    }
}
