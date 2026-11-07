package app.trombito.rallycopilot.data.datosmapa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.motor.datos.Complemento
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ActualizadorDatosTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var base: RallyDatabase
    private lateinit var dao: DatosMapaDao
    private lateinit var actualizador: ActualizadorDatos

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.datosMapaDao()
        actualizador = ActualizadorDatos(dao, base.ajustesMapasDao(), entorno.config)
    }

    @After
    fun cerrar() {
        base.close()
        entorno.close()
    }

    private fun actualizar(
        automatico: Boolean = false,
        progreso: MutableList<Pair<Long, Long>> = mutableListOf(),
    ) = runBlocking {
        // Una descarga manual de antes: el satélite; la automática, solo el paquete base y lo ya instalado.
        val pedidos = if (automatico) emptySet() else setOf(Complemento.SATELITE)
        actualizador.actualizar(automatico, pedidos) { h, t -> progreso += h to t }
    }

    private fun leer(nombre: String) = File(entorno.mapa, nombre).readBytes()

    @Test
    fun `sin datos instalados baja todo menos el satelite, lo verifica y lo registra`() {
        val contenidos = PaqueteDePrueba.contenidos("v1")
        val archivos = entorno.publicar("v1", contenidos)
        val progreso = mutableListOf<Pair<Long, Long>>()

        val resultado = actualizar(progreso = progreso)

        assertEquals(ResultadoActualizacion.Instalado, resultado)
        assertArrayEquals(contenidos.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertFalse("El satélite no se baja sin lo sembrado", File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").exists())
        assertTrue(entorno.servidor.peticionesA(entorno.rutaDe(archivos.first { it.id == "satelite" })).isEmpty())
        assertTrue(File(entorno.mapa, "vectorial.json").isFile)
        runBlocking {
            assertEquals(listOf("estilo", "vectorial", "vias"), dao.archivos().map { it.id })
            val estado = dao.estado()
            assertEquals("v1", estado?.versionInstalada)
            assertNull(estado?.avisoActualizado)
            assertNull(estado?.ultimoError)
        }
        val total = contenidos.filterKeys { it != "satelite" }.values.sumOf { it.size.toLong() }
        assertEquals(total to total, progreso.last())
        assertTrue(progreso.all { it.second == total })
    }

    @Test
    fun `con lo sembrado instalado una descarga manual baja el satelite y una automatica no`() {
        val contenidos = PaqueteDePrueba.contenidos("v1")
        val archivos = entorno.publicar("v1", contenidos)
        actualizar()
        val rutaSatelite = entorno.rutaDe(archivos.first { it.id == "satelite" })

        actualizar(automatico = true)
        assertTrue(
            "La actualización automática no estrena el satélite",
            entorno.servidor.peticionesA(rutaSatelite).isEmpty(),
        )

        assertEquals(ResultadoActualizacion.Instalado, actualizar())
        assertArrayEquals(contenidos.getValue("satelite"), leer("pnoa-mallorca-z16.pmtiles"))
        runBlocking { assertEquals(listOf("estilo", "satelite", "vectorial", "vias"), dao.archivos().map { it.id }) }
    }

    @Test
    fun `una actualizacion solo baja lo que cambio, borra lo que sobra y avisa si es automatica`() {
        val v1 = PaqueteDePrueba.contenidos("v1") + ("extra" to PaqueteDePrueba.aleatorio(9, 1000))
        entorno.publicar("v1", v1)
        actualizar()
        actualizar() // lo sembrado ya está: esta baja el satélite
        assertTrue(File(entorno.mapa, "extra.bin").isFile)

        val v2 = PaqueteDePrueba.contenidos("v1") + ("vectorial" to PaqueteDePrueba.aleatorio(77))
        val archivosV2 = entorno.publicar("v2", v2)
        val resultado = actualizar(automatico = true)

        assertEquals(ResultadoActualizacion.Instalado, resultado)
        assertArrayEquals(v2.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertFalse("extra ya no está en el manifest", File(entorno.mapa, "extra.bin").exists())
        val rutaViasV2 = entorno.rutaDe(archivosV2.first { it.id == "vias" })
        assertTrue("vias no cambió y no se vuelve a bajar", entorno.servidor.peticionesA(rutaViasV2).isEmpty())
        runBlocking {
            assertEquals(listOf("estilo", "satelite", "vectorial", "vias"), dao.archivos().map { it.id })
            assertEquals("v2", dao.estado()?.versionInstalada)
            assertEquals("v2", dao.estado()?.avisoActualizado)
        }
    }

    @Test
    fun `instalacion atomica - si un archivo falla no se sustituye ninguno y al reintentar se reutiliza`() {
        val v1 = PaqueteDePrueba.contenidos("v1")
        entorno.publicar("v1", v1)
        actualizar()
        actualizar() // lo sembrado ya está: esta baja el satélite
        val v2 = PaqueteDePrueba.contenidos("v2")
        val archivosV2 = entorno.publicar("v2", v2, corruptos = setOf("satelite"))

        val resultado = actualizar()

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.HASH_INCORRECTO), resultado)
        assertArrayEquals("El vectorial viejo sigue sirviendo", v1.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertArrayEquals(v1.getValue("satelite"), leer("pnoa-mallorca-z16.pmtiles"))
        runBlocking {
            val shaVectorial = dao.archivos().first { it.id == "vectorial" }.sha256
            assertEquals(PaqueteDePrueba.sha256(v1.getValue("vectorial")), shaVectorial)
            assertEquals("v1", dao.estado()?.versionInstalada)
            assertEquals(MotivoError.HASH_INCORRECTO.name, dao.estado()?.ultimoError)
        }

        // El servidor se arregla: lo ya verificado (vectorial, estilo, vias) no se vuelve a bajar.
        entorno.publicar("v2", v2)
        val rutaVectorial = entorno.rutaDe(archivosV2.first { it.id == "vectorial" })
        val antes = entorno.servidor.peticionesA(rutaVectorial).size
        assertEquals(ResultadoActualizacion.Instalado, actualizar())
        assertEquals(antes, entorno.servidor.peticionesA(rutaVectorial).size)
        assertArrayEquals(v2.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertArrayEquals(v2.getValue("satelite"), leer("pnoa-mallorca-z16.pmtiles"))
        runBlocking { assertNull(dao.estado()?.ultimoError) }
    }

    @Test
    fun `un plan que supera el limite no descarga nada`() {
        entorno.publicar("v1")
        val limitado = AjustesMapasEntity(soloWifi = true, limiteBytes = 100_000)
        runBlocking { base.ajustesMapasDao().guardarAjustes(limitado) }

        val resultado = actualizar()

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.SUPERA_LIMITE), resultado)
        val manifests = setOf(entorno.rutaManifest, entorno.rutaManifestExtra)
        assertTrue("Solo se pidieron los manifests", entorno.servidor.peticiones.all { it.ruta in manifests })
        runBlocking { assertEquals(MotivoError.SUPERA_LIMITE.name, dao.estado()?.ultimoError) }
    }

    @Test
    fun `sin espacio libre para lo que falta falla antes de descargar`() {
        entorno.publicar("v1")
        entorno.bytesLibres = 1_000

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.SIN_ESPACIO), actualizar())
        val manifests = setOf(entorno.rutaManifest, entorno.rutaManifestExtra)
        assertTrue(entorno.servidor.peticiones.all { it.ruta in manifests })
    }

    @Test
    fun `un manifest invalido se informa y no se descarga nada`() {
        entorno.publicarManifestCrudo("""{"esquema":9,"version":"x","generado":"y","archivos":[]}""")

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.MANIFEST_INVALIDO), actualizar())
        runBlocking { assertEquals(MotivoError.MANIFEST_INVALIDO.name, dao.estado()?.ultimoError) }
    }

    @Test
    fun `sin red lanza IOException y conserva lo descargado`() {
        entorno.publicar("v1")
        entorno.servidor.apagar()

        try {
            actualizar()
            fail("Debía lanzar IOException")
        } catch (expected: IOException) {
            // esperado: el trabajo reintenta con backoff
        }
        runBlocking { assertTrue(dao.archivos().isEmpty()) }
    }

    @Test
    fun `una descarga manual baja z16 y z17 y la automatica no estrena el z17`() {
        val contenidos = PaqueteDePrueba.contenidos("v1")
        entorno.publicar("v1", contenidos)
        val z17 = PaqueteDePrueba.aleatorio(17, 90_000)
        val extra = entorno.publicarExtra("v1", mapOf("satelite-z17" to z17)).single()
        actualizar() // lo sembrado

        actualizar(automatico = true)
        assertTrue(entorno.servidor.peticionesA(entorno.rutaDe(extra)).isEmpty())

        assertEquals(ResultadoActualizacion.Instalado, actualizar())

        assertArrayEquals(contenidos.getValue("satelite"), leer("pnoa-mallorca-z16.pmtiles"))
        assertArrayEquals(z17, leer("pnoa-mallorca-z17.pmtiles"))
        runBlocking {
            val ids = dao.archivos().map { it.id }
            assertEquals(listOf("estilo", "satelite", "satelite-z17", "vectorial", "vias"), ids)
        }
    }

    @Test
    fun `con z16 ya instalado una descarga manual baja solo el z17`() {
        val archivos = entorno.publicar("v1")
        actualizar()
        actualizar() // z16
        val extra = entorno.publicarExtra("v1").single()
        val z16 = entorno.rutaDe(archivos.first { it.id == "satelite" })
        val antesZ16 = entorno.servidor.peticionesA(z16).size

        assertEquals(ResultadoActualizacion.Instalado, actualizar())

        assertEquals(antesZ16, entorno.servidor.peticionesA(z16).size)
        assertEquals(1, entorno.servidor.peticionesA(entorno.rutaDe(extra)).size)
        assertTrue(File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").isFile)
    }

    @Test
    fun `la actualizacion automatica renueva el z17 instalado`() {
        entorno.publicar("v1")
        val v1 = PaqueteDePrueba.aleatorio(17, 90_000)
        entorno.publicarExtra("v1", mapOf("satelite-z17" to v1))
        actualizar()
        actualizar()
        val v2 = PaqueteDePrueba.aleatorio(18, 90_000)
        entorno.publicarExtra("v2", mapOf("satelite-z17" to v2))

        assertEquals(ResultadoActualizacion.Instalado, actualizar(automatico = true))

        assertArrayEquals(v2, leer("pnoa-mallorca-z17.pmtiles"))
    }

    @Test
    fun `sin manifest extra el z17 instalado se conserva y no hay error`() {
        entorno.publicar("v1")
        val z17 = PaqueteDePrueba.aleatorio(17, 90_000)
        entorno.publicarExtra("v1", mapOf("satelite-z17" to z17))
        actualizar()
        actualizar()
        entorno.servidor.servir(entorno.rutaManifestExtra, ByteArray(0)) // extra ilegible

        assertEquals(ResultadoActualizacion.Instalado, actualizar(automatico = true))
        entorno.publicarManifestExtraCrudo("""{"esquema":1,"version":"x","generado":"y","archivos":[""")
        assertEquals(ResultadoActualizacion.Instalado, actualizar())

        assertArrayEquals(z17, leer("pnoa-mallorca-z17.pmtiles"))
        runBlocking {
            assertTrue(dao.archivos().any { it.id == "satelite-z17" })
            assertNull(dao.estado()?.ultimoError)
        }
    }

    @Test
    fun `sin manifest extra publicado la descarga funciona como antes`() {
        val contenidos = PaqueteDePrueba.contenidos("v1")
        entorno.publicar("v1", contenidos)
        actualizar()

        assertEquals(ResultadoActualizacion.Instalado, actualizar())

        assertFalse(File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").exists())
        runBlocking { assertEquals(listOf("estilo", "satelite", "vectorial", "vias"), dao.archivos().map { it.id }) }
    }

    @Test
    fun `el z17 cuenta para el limite de espacio`() {
        val contenidos = PaqueteDePrueba.contenidos("v1")
        entorno.publicar("v1", contenidos)
        entorno.publicarExtra("v1")
        actualizar() // lo sembrado
        val sinZ17 = contenidos.values.sumOf { it.size.toLong() }
        runBlocking {
            base.ajustesMapasDao().guardarAjustes(
                AjustesMapasEntity(
                    soloWifi = true,
                    limiteBytes =
                        sinZ17 + 1_000,
                ),
            )
        }

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.SUPERA_LIMITE), actualizar())
        assertFalse(File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").exists())
    }

    @Test
    fun `un z17 corrupto no se instala y falla por hash`() {
        entorno.publicar("v1")
        val extra = entorno.publicarExtra("v1").single()
        actualizar()
        entorno.servidor.servir(entorno.rutaDe(extra), PaqueteDePrueba.aleatorio(99, 90_000))

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.HASH_INCORRECTO), actualizar())
        assertFalse(File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").exists())
        assertFalse(File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").exists())
    }

    private fun complemento(
        complementos: Set<Complemento>,
        solo: Boolean = true,
    ) = runBlocking { actualizador.actualizar(false, complementos, solo) }

    private fun paqueteConComplementos(version: String = "v1"): Map<String, ByteArray> =
        PaqueteDePrueba.contenidos(version) +
            mapOf(
                "relieve" to PaqueteDePrueba.aleatorio(21, 60_000),
                "curvas" to PaqueteDePrueba.aleatorio(22, 30_000),
                "voz-sharvard" to PaqueteDePrueba.aleatorio(23, 80_000),
                "voz-sharvard-tokens" to PaqueteDePrueba.aleatorio(24, 900),
            )

    @Test
    fun `una descarga normal o automatica no estrena relieve ni voz`() {
        entorno.publicar("v1", paqueteConComplementos())
        actualizar() // lo sembrado

        assertEquals(ResultadoActualizacion.Instalado, actualizar(automatico = true))

        runBlocking {
            assertEquals(listOf("estilo", "vectorial", "vias"), dao.archivos().map { it.id })
        }
        assertFalse(File(entorno.mapa, "relieve-mallorca.pmtiles").exists())
        assertFalse(File(entorno.mapa, "voz-sharvard.onnx").exists())
    }

    @Test
    fun `pedir la voz baja solo sus dos archivos y no toca el resto`() {
        val contenidos = paqueteConComplementos()
        entorno.publicar("v1", contenidos)
        actualizar() // lo sembrado
        val versionBase = runBlocking { dao.estado()?.versionInstalada }
        // El paquete base tiene una versión nueva, pero pedir la voz no debe bajarla.
        val v2 = entorno.publicar("v2", paqueteConComplementos("v2"))

        val resultado = complemento(setOf(Complemento.VOZ_SHARVARD))

        assertEquals(ResultadoActualizacion.Instalado, resultado)
        val voz = paqueteConComplementos("v2")
        assertArrayEquals(voz.getValue("voz-sharvard"), leer("voz-sharvard.onnx"))
        assertArrayEquals(voz.getValue("voz-sharvard-tokens"), leer("voz-sharvard-tokens.txt"))
        assertFalse(File(entorno.mapa, "relieve-mallorca.pmtiles").exists())
        assertArrayEquals(contenidos.getValue("vectorial"), leer("mallorca.pmtiles"))
        runBlocking {
            assertEquals(
                listOf("estilo", "vectorial", "vias", "voz-sharvard", "voz-sharvard-tokens"),
                dao.archivos().map { it.id },
            )
            assertEquals("el paquete base no se ha actualizado", versionBase, dao.estado()?.versionInstalada)
        }
        assertTrue(entorno.servidor.peticionesA(entorno.rutaDe(v2.first { it.id == "vectorial" })).isEmpty())
    }

    @Test
    fun `lo bajo demanda ya instalado se actualiza solo con una actualizacion automatica`() {
        entorno.publicar("v1", paqueteConComplementos())
        actualizar()
        complemento(setOf(Complemento.RELIEVE))
        val nuevo = paqueteConComplementos("v2")
        entorno.publicar("v2", nuevo)

        assertEquals(ResultadoActualizacion.Instalado, actualizar(automatico = true))

        assertArrayEquals(nuevo.getValue("relieve"), leer("relieve-mallorca.pmtiles"))
        assertArrayEquals(nuevo.getValue("curvas"), leer("curvas-mallorca.pmtiles"))
        assertFalse("la voz no pedida no se estrena", File(entorno.mapa, "voz-sharvard.onnx").exists())
    }

    @Test
    fun `pedir un complemento que el manifest no publica falla en vez de dar por buena la descarga`() {
        entorno.publicar("v1") // sin relieve, curvas ni voz
        actualizar()

        assertEquals(
            ResultadoActualizacion.Fallo(MotivoError.MANIFEST_INVALIDO),
            complemento(setOf(Complemento.VOZ_SHARVARD)),
        )
    }

    @Test
    fun `un archivo de voz corrupto no se instala`() {
        entorno.publicar("v1", paqueteConComplementos(), corruptos = setOf("voz-sharvard"))
        actualizar()

        assertEquals(
            ResultadoActualizacion.Fallo(MotivoError.HASH_INCORRECTO),
            complemento(setOf(Complemento.VOZ_SHARVARD)),
        )
        assertFalse(File(entorno.mapa, "voz-sharvard.onnx").exists())
        assertFalse(File(entorno.mapa, "voz-sharvard-tokens.txt").exists())
    }

    @Test
    fun `instalar un complemento no borra el satelite a medias que hay en nuevo`() {
        entorno.publicar("v1", paqueteConComplementos())
        actualizar()
        val parcial = File(entorno.config.directorioNuevo, "pnoa-mallorca-z16.pmtiles.part")
        parcial.parentFile.mkdirs()
        parcial.writeBytes(ByteArray(1_000))

        complemento(setOf(Complemento.RELIEVE))

        assertTrue("el .part del satelite sigue para reanudar", parcial.isFile)
        assertArrayEquals(
            PaqueteDePrueba.aleatorio(21, 60_000),
            leer("relieve-mallorca.pmtiles"),
        )
    }
}
