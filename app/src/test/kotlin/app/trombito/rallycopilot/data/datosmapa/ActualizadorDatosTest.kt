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
    ) = runBlocking { actualizador.actualizar(automatico) { h, t -> progreso += h to t } }

    private fun leer(nombre: String) = File(entorno.mapa, nombre).readBytes()

    @Test
    fun `la primera instalacion descarga todo, lo verifica y lo registra`() {
        val contenidos = PaqueteDePrueba.contenidos("v1")
        entorno.publicar("v1", contenidos)
        val progreso = mutableListOf<Pair<Long, Long>>()

        val resultado = actualizar(automatico = true, progreso = progreso)

        assertEquals(ResultadoActualizacion.Instalado, resultado)
        assertArrayEquals(contenidos.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertArrayEquals(contenidos.getValue("satelite"), leer("pnoa-mallorca-z16.pmtiles"))
        assertTrue(File(entorno.mapa, "vectorial.json").isFile)
        runBlocking {
            assertEquals(4, dao.archivos().size)
            val estado = dao.estado()
            assertEquals("v1", estado?.versionInstalada)
            assertNull("La primera instalación automática no es una actualización", estado?.avisoActualizado)
            assertNull(estado?.ultimoError)
        }
        val total = contenidos.values.sumOf { it.size.toLong() }
        assertEquals(total to total, progreso.last())
        assertTrue(progreso.all { it.second == total })
    }

    @Test
    fun `una actualizacion solo baja lo que cambio, borra lo que sobra y avisa si es automatica`() {
        val v1 = PaqueteDePrueba.contenidos("v1") + ("extra" to PaqueteDePrueba.aleatorio(9, 1000))
        entorno.publicar("v1", v1)
        actualizar()
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
        assertEquals("Solo se pidió el manifest", 1, entorno.servidor.peticiones.size)
        runBlocking { assertEquals(MotivoError.SUPERA_LIMITE.name, dao.estado()?.ultimoError) }
    }

    @Test
    fun `sin espacio libre para lo que falta falla antes de descargar`() {
        entorno.publicar("v1")
        entorno.bytesLibres = 1_000

        assertEquals(ResultadoActualizacion.Fallo(MotivoError.SIN_ESPACIO), actualizar())
        assertEquals(1, entorno.servidor.peticiones.size)
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
}
