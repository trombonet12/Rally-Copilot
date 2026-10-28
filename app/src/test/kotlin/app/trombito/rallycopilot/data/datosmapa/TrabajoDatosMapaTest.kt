package app.trombito.rallycopilot.data.datosmapa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.domain.datos.MotivoError
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrabajoDatosMapaTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var contexto: Context
    private lateinit var entorno: EntornoDatosMapa
    private lateinit var base: RallyDatabase
    private lateinit var dao: DatosMapaDao

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.datosMapaDao()
    }

    @After
    fun cerrar() {
        base.close()
        entorno.close()
    }

    private fun ejecutar(
        automatico: Boolean = false,
        intento: Int = 0,
    ): Result {
        val fabrica =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker {
                    val actualizador = ActualizadorDatos(dao, base.ajustesMapasDao(), entorno.config)
                    return TrabajoDatosMapa(appContext, workerParameters, actualizador)
                }
            }
        val trabajo =
            TestListenableWorkerBuilder<TrabajoDatosMapa>(contexto)
                .setWorkerFactory(fabrica)
                .setInputData(workDataOf(TrabajoDatosMapa.CLAVE_AUTOMATICO to automatico))
                .setRunAttemptCount(intento)
                .build()
        return runBlocking { trabajo.doWork() }
    }

    @Test
    fun `descarga e instala el paquete y termina con exito`() {
        entorno.publicar("v1")

        assertEquals(Result.success(), ejecutar())

        assertTrue(File(entorno.mapa, "mallorca.pmtiles").isFile)
        assertTrue(File(entorno.mapa, "vectorial.json").isFile)
        runBlocking { assertEquals(4, dao.archivos().size) }
    }

    @Test
    fun `un hash incorrecto es un fallo definitivo y deja el error guardado`() {
        entorno.publicar("v1", corruptos = setOf("vias"))

        assertEquals(Result.failure(), ejecutar())

        runBlocking { assertEquals(MotivoError.HASH_INCORRECTO.name, dao.estado()?.ultimoError) }
    }

    @Test
    fun `sin red reintenta y tras muchos intentos se rinde con SIN_RED`() {
        entorno.publicar("v1")
        entorno.servidor.apagar()

        assertEquals(Result.retry(), ejecutar(intento = 0))
        assertEquals(Result.failure(), ejecutar(intento = 10))

        runBlocking { assertEquals(MotivoError.SIN_RED.name, dao.estado()?.ultimoError) }
    }
}
