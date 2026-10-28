package app.trombito.rallycopilot.data.datosmapa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RepositorioDatosMapaImplTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var base: RallyDatabase
    private lateinit var dao: DatosMapaDao
    private lateinit var workManager: WorkManager
    private lateinit var repositorio: RepositorioDatosMapaImpl

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            contexto,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(contexto)
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.datosMapaDao()
        repositorio = RepositorioDatosMapaImpl(dao, base.ajustesMapasDao(), workManager, entorno.config, Dispatchers.IO)
    }

    @After
    fun cerrar() {
        base.close()
        entorno.close()
    }

    private fun trabajos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO).get()

    private fun esperar(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + ESPERA_MS
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "Tiempo agotado esperando la condición" }
            Thread.sleep(20)
        }
    }

    private fun esperarEstado(condicion: (EstadoDatosMapa) -> Boolean): EstadoDatosMapa =
        runBlocking { withTimeout(ESPERA_MS) { repositorio.estado.first(condicion) } }

    private fun instalarArchivo() =
        runBlocking {
            dao.registrarInstalacion(
                archivos = listOf(ArchivoInstaladoEntity("satelite", "pnoa.bin", "a".repeat(64), 500, "v1")),
                idsBorrados = emptyList(),
                version = "v1",
                avisar = false,
            )
        }

    private fun escribirEstilos(conSatelite: Boolean) {
        entorno.mapa.mkdirs()
        File(entorno.mapa, "vectorial.json")
            .writeText("""{"url":"pmtiles://{{DIR}}/mallorca.pmtiles","g":"{{DIR}}/glyphs"}""")
        File(entorno.mapa, "satelite.json").writeText("""{"url":"pmtiles://{{DIR}}/pnoa-mallorca-z16.pmtiles"}""")
        if (conSatelite) File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").writeText("x")
    }

    @Test
    fun `sin datos instalados los estilos son null y el estado esta vacio`() {
        assertNull(runBlocking { repositorio.estilos() })
        assertEquals(EstadoDatosMapa(), esperarEstado { true })
    }

    @Test
    fun `los estilos sustituyen DIR por la ruta local en cada aparicion`() {
        escribirEstilos(conSatelite = true)

        val estilos = requireNotNull(runBlocking { repositorio.estilos() })

        val raiz = "file://" + entorno.mapa.absolutePath.replace('\\', '/')
        assertEquals("""{"url":"pmtiles://$raiz/mallorca.pmtiles","g":"$raiz/glyphs"}""", estilos.vectorial)
        assertEquals("""{"url":"pmtiles://$raiz/pnoa-mallorca-z16.pmtiles"}""", estilos.satelite)
        assertFalse(estilos.vectorial.contains("{{DIR}}"))
    }

    @Test
    fun `sin el satelite instalado el estilo satelite es null`() {
        escribirEstilos(conSatelite = false)

        val estilos = requireNotNull(runBlocking { repositorio.estilos() })

        assertNull(estilos.satelite)
        assertNotNull(estilos.vectorial)
    }

    @Test
    fun `un estilo que apunta a la red se rechaza y se registra el error`() {
        escribirEstilos(conSatelite = true)
        File(entorno.mapa, "vectorial.json").writeText("""{"url":"https://evil.example/mallorca.json"}""")

        assertNull(runBlocking { repositorio.estilos() })

        assertEquals(MotivoError.MANIFEST_INVALIDO.name, runBlocking { dao.estado()?.ultimoError })
    }

    @Test
    fun `un estilo satelite que apunta a la red tambien invalida los estilos`() {
        escribirEstilos(conSatelite = true)
        File(entorno.mapa, "satelite.json").writeText("""{"sprite":"http://evil.example/s"}""")

        assertNull(runBlocking { repositorio.estilos() })
    }

    @Test
    fun `descargar encola un unico trabajo solo WiFi y borra el error anterior`() {
        runBlocking { dao.establecerError(MotivoError.SIN_RED.name) }

        repositorio.descargar()
        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        val trabajo = trabajos().single()
        assertEquals(WorkInfo.State.ENQUEUED, trabajo.state)
        assertEquals(NetworkType.UNMETERED, trabajo.constraints.requiredNetworkType)
        assertTrue("manual" in trabajo.tags)
        esperar { runBlocking { dao.estado()?.ultimoError } == null }
    }

    @Test
    fun `con solo WiFi desactivado el trabajo pide cualquier red`() {
        runBlocking { repositorio.guardarAjustes(AjustesMapas(soloWifi = false)) }

        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        assertEquals(NetworkType.CONNECTED, trabajos().single().constraints.requiredNetworkType)
    }

    @Test
    fun `cambiar solo WiFi reencola el trabajo pendiente con la restriccion nueva`() {
        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        runBlocking { repositorio.guardarAjustes(AjustesMapas(soloWifi = false)) }

        esperar {
            trabajos().any {
                it.state == WorkInfo.State.ENQUEUED && it.constraints.requiredNetworkType == NetworkType.CONNECTED
            }
        }
    }

    @Test
    fun `comprobar actualizacion sin datos no encola nada y con datos encola el automatico`() {
        repositorio.comprobarActualizacion()
        Thread.sleep(300)
        assertTrue(trabajos().isEmpty())

        instalarArchivo()
        repositorio.comprobarActualizacion()
        esperar { trabajos().isNotEmpty() }

        assertTrue("automatico" in trabajos().single().tags)
    }

    @Test
    fun `el estado combina lo instalado con la descarga que espera red`() {
        instalarArchivo()
        repositorio.descargar()

        val estado = esperarEstado { it.descarga != null }

        assertEquals(500L, estado.instalados?.bytes)
        assertEquals("v1", estado.instalados?.version)
        assertTrue(estado.instalados?.conSatelite == true)
        assertTrue(estado.descarga?.esperandoRed == true)
    }

    @Test
    fun `cancelar quita el trabajo y conserva los parciales`() {
        val parcial = File(entorno.config.directorioNuevo, "mallorca.pmtiles.part")
        parcial.parentFile?.mkdirs()
        parcial.writeText("parcial")
        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        repositorio.cancelar()

        esperar { trabajos().all { it.state == WorkInfo.State.CANCELLED } }
        assertTrue(parcial.isFile)
        assertNull(esperarEstado { it.descarga == null }.descarga)
    }

    @Test
    fun `borrar elimina la carpeta de datos y lo registrado`() {
        escribirEstilos(conSatelite = true)
        instalarArchivo()
        runBlocking { repositorio.guardarAjustes(AjustesMapas(soloWifi = false)) }

        runBlocking { repositorio.borrar() }

        assertFalse(entorno.mapa.exists())
        assertNull(esperarEstado { true }.instalados)
        assertFalse("Los ajustes se conservan", runBlocking { repositorio.ajustes.first() }.soloWifi)
    }

    @Test
    fun `descartar el aviso lo limpia`() {
        runBlocking {
            dao.registrarInstalacion(emptyList(), emptyList(), "v2", avisar = true)
            assertEquals("v2", repositorio.estado.first().avisoActualizado)

            repositorio.descartarAviso()

            assertNull(repositorio.estado.first().avisoActualizado)
        }
    }

    private companion object {
        const val ESPERA_MS = 10_000L
    }
}
