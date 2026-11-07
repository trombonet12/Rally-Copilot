package app.trombito.rallycopilot.data.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Copia del modelo Vosk y carga/recarga de [FabricaReconocedorVosk] con dobles: sin biblioteca nativa. */
@RunWith(RobolectricTestRunner::class)
class ModeloVoskTest {
    @get:Rule val carpeta = TemporaryFolder()

    /** Árbol de assets en memoria; [falloEn] hace fallar la apertura de ese archivo (copia cortada a medias). */
    private class OrigenFalso(
        private val archivos: Map<String, String>,
        private val falloEn: String? = null,
    ) : OrigenModelo {
        var aperturas = 0

        override fun list(ruta: String): List<String> =
            archivos.keys
                .filter { it.startsWith("$ruta/") }
                .map { it.removePrefix("$ruta/").substringBefore("/") }
                .distinct()

        override fun open(ruta: String): InputStream {
            aperturas++
            if (ruta == falloEn) throw IOException("corte")
            return ByteArrayInputStream(archivos[ruta]?.toByteArray() ?: throw FileNotFoundException(ruta))
        }
    }

    private val modelo =
        mapOf(
            "asr/vosk-model/am/final.mdl" to "am",
            "asr/vosk-model/conf/model.conf" to "conf",
        )

    private val destinoBase get() = File(carpeta.root, "asr")

    @Test
    fun `la copia crea el modelo con su marcador de version`() {
        val destino = CopiaModeloVosk.asegurar(OrigenFalso(modelo), destinoBase)

        assertEquals("am", File(destino, "am/final.mdl").readText())
        assertEquals(CopiaModeloVosk.VERSION, File(destino, ".version").readText())
        assertFalse(File(destinoBase, "vosk-model.tmp").exists())
    }

    @Test
    fun `con la version ya copiada no se vuelve a copiar`() {
        CopiaModeloVosk.asegurar(OrigenFalso(modelo), destinoBase)
        val origen = OrigenFalso(modelo)

        CopiaModeloVosk.asegurar(origen, destinoBase)

        assertEquals(0, origen.aperturas)
    }

    @Test
    fun `una copia cortada a medias no deja modelo incompleto ni carpeta temporal y se rehace`() {
        val roto = OrigenFalso(modelo, falloEn = "asr/vosk-model/conf/model.conf")

        assertThrows(IOException::class.java) { CopiaModeloVosk.asegurar(roto, destinoBase) }

        assertFalse(File(destinoBase, "vosk-model").exists())
        assertFalse(File(destinoBase, "vosk-model.tmp").exists())
        val destino = CopiaModeloVosk.asegurar(OrigenFalso(modelo), destinoBase)
        assertTrue(File(destino, "conf/model.conf").isFile)
    }

    @Test
    fun `un marcador de otra version fuerza una copia nueva`() {
        val destino = CopiaModeloVosk.asegurar(OrigenFalso(modelo), destinoBase)
        File(destino, ".version").writeText("vosk-antiguo")
        File(destino, "sobra.txt").writeText("viejo")

        CopiaModeloVosk.asegurar(OrigenFalso(modelo), destinoBase)

        assertEquals(CopiaModeloVosk.VERSION, File(destino, ".version").readText())
        assertFalse(File(destino, "sobra.txt").exists())
    }

    private class ReconocedorFalso : Reconocedor {
        override fun aceptar(
            muestras: ShortArray,
            n: Int,
        ) = false

        override fun parcial() = ""

        override fun resultado() = ""

        override fun fin() = ""

        override fun close() = Unit
    }

    private class ModeloFalso : ModeloNativo {
        var cerrado = false

        override fun reconocedor(gramaticaJson: String): Reconocedor = ReconocedorFalso()

        override fun close() {
            cerrado = true
        }
    }

    private class CargadorFalso(
        private val alCargar: (Int) -> ModeloNativo,
    ) : CargadorModeloVosk {
        var cargas = 0

        override fun cargar(carpeta: File): ModeloNativo = alCargar(++cargas)
    }

    private fun fabrica(
        cargador: CargadorModeloVosk,
        copiar: () -> File = { carpeta.root },
    ) = FabricaReconocedorVosk(copiar, cargador)

    @Test
    fun `si la copia falla no hay reconocedor y se reintenta despues`() {
        var intentos = 0
        val cargador = CargadorFalso { ModeloFalso() }
        val fabrica =
            fabrica(cargador, copiar = { if (++intentos == 1) throw IOException("disco lleno") else carpeta.root })

        assertNull(fabrica.crear("[]"))
        assertNotNull(fabrica.crear("[]"))
        assertEquals(1, cargador.cargas)
    }

    @Test
    fun `un modelo incompleto que no carga da null y no revienta`() {
        val fabrica = fabrica(CargadorFalso { throw IOException("modelo incompleto") })

        assertNull(fabrica.crear("[]"))
    }

    @Test
    fun `sin la biblioteca nativa da null y no revienta`() {
        val fabrica = fabrica(CargadorFalso { throw UnsatisfiedLinkError("libvosk") })

        assertNull(fabrica.crear("[]"))
    }

    @Test
    fun `el modelo se conserva entre reconocedores y tras liberar se recarga`() {
        val modelos = mutableListOf<ModeloFalso>()
        val cargador = CargadorFalso { ModeloFalso().also(modelos::add) }
        val fabrica = fabrica(cargador)

        fabrica.crear("[]")
        fabrica.crear("[]")
        assertEquals(1, cargador.cargas)
        fabrica.liberar()
        assertTrue(modelos[0].cerrado)
        assertNotNull(fabrica.crear("[]"))

        assertEquals(2, cargador.cargas)
        assertFalse(modelos[1].cerrado)
    }

    @Test
    fun `liberar sin modelo cargado no hace nada y precopiar no carga el modelo`() {
        val cargador = CargadorFalso { ModeloFalso() }
        var copias = 0
        val fabrica =
            fabrica(
                cargador,
                copiar = {
                    copias++
                    carpeta.root
                },
            )

        fabrica.liberar()
        fabrica.precopiar()

        assertEquals(1, copias)
        assertEquals(0, cargador.cargas)
    }

    @Test
    fun `precopiar con la copia fallida no revienta`() {
        fabrica(CargadorFalso { ModeloFalso() }, copiar = { throw IOException("sin espacio") }).precopiar()
    }
}
