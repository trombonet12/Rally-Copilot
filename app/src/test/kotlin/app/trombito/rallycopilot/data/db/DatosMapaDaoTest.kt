package app.trombito.rallycopilot.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.motor.datos.LIMITE_POR_DEFECTO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatosMapaDaoTest {
    private lateinit var base: RallyDatabase
    private lateinit var dao: DatosMapaDao
    private lateinit var ajustesDao: AjustesMapasDao

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.datosMapaDao()
        ajustesDao = base.ajustesMapasDao()
    }

    @After
    fun cerrar() = base.close()

    private fun archivo(
        id: String,
        sha: String = "a".repeat(64),
    ) = ArchivoInstaladoEntity(id = id, nombre = "$id.bin", sha256 = sha, bytes = 100, version = "v1")

    @Test
    fun `sin datos no hay archivos, ni ajustes, ni estado`() =
        runBlocking {
            assertTrue(dao.archivos().isEmpty())
            assertNull(ajustesDao.ajustes())
            assertNull(dao.estado())
        }

    @Test
    fun `los ajustes por defecto son solo WiFi y 2 GB`() {
        val ajustes = AjustesMapasEntity()
        assertTrue(ajustes.soloWifi)
        assertEquals(LIMITE_POR_DEFECTO, ajustes.limiteBytes)
    }

    @Test
    fun `guardar ajustes es un upsert de fila unica y el flujo emite el valor nuevo`() =
        runBlocking {
            ajustesDao.guardarAjustes(AjustesMapasEntity(soloWifi = false, limiteBytes = 5))
            ajustesDao.guardarAjustes(AjustesMapasEntity(soloWifi = true, limiteBytes = 7))

            val ajustes = ajustesDao.observarAjustes().first()
            assertEquals(AjustesMapasEntity(soloWifi = true, limiteBytes = 7), ajustes)
        }

    @Test
    fun `registrar instalacion sustituye archivos, borra ids y deja la version`() =
        runBlocking {
            dao.registrarInstalacion(listOf(archivo("a"), archivo("b")), emptyList(), "v1", avisar = false)
            dao.registrarInstalacion(listOf(archivo("a", sha = "b".repeat(64))), listOf("b"), "v2", avisar = true)

            val archivos = dao.observarArchivos().first()
            assertEquals(listOf("a"), archivos.map { it.id })
            assertEquals("b".repeat(64), archivos.single().sha256)
            val estado = dao.estado()
            assertEquals("v2", estado?.versionInstalada)
            assertEquals("v2", estado?.avisoActualizado)
        }

    @Test
    fun `el error y el aviso se guardan y se limpian sin tocar lo demas`() =
        runBlocking {
            dao.registrarInstalacion(listOf(archivo("a")), emptyList(), "v1", avisar = true)
            dao.establecerError("SIN_RED")
            assertEquals("SIN_RED", dao.observarEstado().first()?.ultimoError)

            dao.establecerError(null)
            dao.establecerAviso(null)

            val estado = dao.estado()
            assertNull(estado?.ultimoError)
            assertNull(estado?.avisoActualizado)
            assertEquals("v1", estado?.versionInstalada)
        }

    @Test
    fun `una instalacion correcta limpia el error anterior`() =
        runBlocking {
            dao.establecerError("HASH_INCORRECTO")
            dao.registrarInstalacion(listOf(archivo("a")), emptyList(), "v1", avisar = false)

            assertNull(dao.estado()?.ultimoError)
        }

    @Test
    fun `borrar la instalacion conserva los ajustes`() =
        runBlocking {
            ajustesDao.guardarAjustes(AjustesMapasEntity(soloWifi = false, limiteBytes = 9))
            dao.registrarInstalacion(listOf(archivo("a")), emptyList(), "v1", avisar = false)

            dao.borrarInstalacion()

            assertTrue(dao.archivos().isEmpty())
            assertNull(dao.estado()?.versionInstalada)
            assertEquals(9L, ajustesDao.ajustes()?.limiteBytes)
        }
}
