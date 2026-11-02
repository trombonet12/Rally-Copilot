package app.trombito.rallycopilot.data.detalle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import app.trombito.rallycopilot.data.datosmapa.EntornoDatosMapa
import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class PrecargaDetalleTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var contexto: Context
    private lateinit var entorno: EntornoDatosMapa
    private lateinit var base: RallyDatabase
    private lateinit var workManager: WorkManager
    private lateinit var pendientes: RutasPendientes
    private lateinit var precarga: PrecargaDetalleImpl
    private var energiaOk = true

    private val tramo = Tramo("t1", List(6) { PuntoGeo(39.6 + it * 0.001, 2.9) }, 556.0)

    @Before
    fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            contexto,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(contexto)
        entorno = EntornoDatosMapa(carpeta.newFolder("e"))
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        pendientes = RutasPendientes(carpeta.newFolder("rutas"))
        precarga = PrecargaDetalleImpl(workManager, entorno.config, base.ajustesMapasDao(), pendientes, Dispatchers.IO)
    }

    @After
    fun cerrar() {
        base.close()
        entorno.close()
    }

    private fun trabajos(nombre: String = TrabajoDetalleSatelite.NOMBRE_TRABAJO): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(nombre).get()

    private fun esperarTrabajos(
        n: Int,
        nombre: String = TrabajoDetalleSatelite.NOMBRE_TRABAJO,
    ) {
        val limite = System.currentTimeMillis() + ESPERA_MS
        while (trabajos(nombre).size < n) {
            check(System.currentTimeMillis() < limite) { "Sin $n trabajos encolados" }
            Thread.sleep(20)
        }
    }

    private fun ejecutor(fuente: FuenteTeselas) =
        EjecutorDetalle(
            entorno.config,
            FalsoRepositorioTramos(listOf(resumenFalso("t1")), trazas = mapOf("t1" to tramo)),
            base.datosMapaDao(),
            base.ajustesMapasDao(),
            fuente,
            { energiaOk },
            pendientes,
        )

    private fun ejecutarTrabajo(
        fuente: FuenteTeselas,
        zona: Pair<Rectangulo, Int>? = null,
        intento: Int = 0,
    ): Result {
        val fabrica =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = TrabajoDetalleSatelite(appContext, workerParameters, ejecutor(fuente))
            }
        val constructor = TestListenableWorkerBuilder<TrabajoDetalleSatelite>(contexto).setWorkerFactory(fabrica)
        if (zona != null) constructor.setInputData(TrabajoDetalleSatelite.datosDeZona(zona.first, zona.second))
        return runBlocking { constructor.setRunAttemptCount(intento).build().doWork() }
    }

    private fun instalarSatelite() =
        runBlocking {
            base.datosMapaDao().guardarArchivos(
                listOf(ArchivoInstaladoEntity("satelite", "pnoa-mallorca-z16.pmtiles", "h", 1_000, "v")),
            )
        }

    @Test
    fun `el automatico se encola solo con WiFi y no se duplica mientras espera`() {
        precarga.precargarTramos()
        esperarTrabajos(1)
        precarga.precargarTramos()
        precarga.precargarTramos()
        Thread.sleep(300)

        val trabajo = trabajos().single()
        assertEquals(WorkInfo.State.ENQUEUED, trabajo.state)
        assertTrue(TrabajoDetalleSatelite.ETIQUETA_AUTOMATICO in trabajo.tags)
        assertEquals(NetworkType.UNMETERED, trabajo.constraints.requiredNetworkType)
    }

    @Test
    fun `una ruta planificada se guarda como corredor pendiente y encola el automatico`() {
        val ruta = List(5) { PuntoGeo(39.7, 2.7 + it * 0.001) }

        precarga.precargarRuta(ruta)
        esperarTrabajos(1)

        val guardado = pendientes.leer().single().second
        assertEquals(TeselasPrecarga.corredor(ruta, 18), guardado)
        assertEquals(NetworkType.UNMETERED, trabajos().single().constraints.requiredNetworkType)
    }

    @Test
    fun `una ruta fuera de Mallorca no encola nada`() {
        precarga.precargarRuta(listOf(PuntoGeo(41.38, 2.17), PuntoGeo(41.39, 2.18)))
        Thread.sleep(300)

        assertTrue(trabajos().isEmpty())
    }

    @Test
    fun `la zona manual respeta Solo con WiFi`() {
        val zona = Rectangulo(2.65, 39.56, 2.651, 39.561)
        runBlocking {
            base.ajustesMapasDao().guardarAjustes(
                AjustesMapasEntity(soloWifi = false, limiteBytes = 4L shl 30),
            )
        }

        precarga.descargarZona(zona, 19)
        esperarTrabajos(1, TrabajoDetalleSatelite.NOMBRE_ZONA)

        val trabajo = trabajos(TrabajoDetalleSatelite.NOMBRE_ZONA).single()
        assertEquals(NetworkType.CONNECTED, trabajo.constraints.requiredNetworkType)
    }

    @Test
    fun `la zona manual por defecto exige WiFi`() {
        precarga.descargarZona(Rectangulo(2.65, 39.56, 2.651, 39.561), 18)
        esperarTrabajos(1, TrabajoDetalleSatelite.NOMBRE_ZONA)

        val trabajo = trabajos(TrabajoDetalleSatelite.NOMBRE_ZONA).single()
        assertEquals(NetworkType.UNMETERED, trabajo.constraints.requiredNetworkType)
    }

    @Test
    fun `una zona manual no espera detras del corredor automatico que aguarda WiFi`() {
        precarga.precargarTramos()
        esperarTrabajos(1)
        precarga.descargarZona(Rectangulo(2.65, 39.56, 2.651, 39.561), 18)
        esperarTrabajos(1, TrabajoDetalleSatelite.NOMBRE_ZONA)

        assertEquals(WorkInfo.State.ENQUEUED, trabajos(TrabajoDetalleSatelite.NOMBRE_ZONA).single().state)
        assertEquals(WorkInfo.State.ENQUEUED, trabajos().single().state)
    }

    @Test
    fun `el trabajo automatico termina con exito cuando baja el corredor`() {
        instalarSatelite()
        val fuente = FuenteFalsa()

        assertEquals(Result.success(), ejecutarTrabajo(fuente))

        assertTrue(fuente.pedidas.isNotEmpty())
    }

    @Test
    fun `con bateria baja el trabajo automatico reintenta`() {
        instalarSatelite()
        energiaOk = false

        assertEquals(Result.retry(), ejecutarTrabajo(FuenteFalsa()))
    }

    @Test
    fun `sin red reintenta y tras muchos intentos falla`() {
        instalarSatelite()
        val sinRed = FuenteTeselas { throw IOException("sin red") }

        assertEquals(Result.retry(), ejecutarTrabajo(sinRed, intento = 0))
        assertEquals(Result.failure(), ejecutarTrabajo(sinRed, intento = 10))
    }

    @Test
    fun `la zona manual baja sus teselas aunque la energia no cumpla`() {
        energiaOk = false
        val zona = Rectangulo(2.6500, 39.5600, 2.6510, 39.5610)
        val fuente = FuenteFalsa()

        assertEquals(Result.success(), ejecutarTrabajo(fuente, zona to 18))

        assertEquals(TeselasPrecarga.rectangulo(zona, 18).toSet(), fuente.pedidas.toSet())
    }

    @Test
    fun `el estado cuenta bytes y teselas y borrar lo deja todo a cero`() =
        runBlocking {
            val archivo = File(entorno.mapa, NOMBRE_DETALLE)
            AlmacenDetalle(archivo).use {
                it.guardar(
                    mapOf(
                        app.trombito.rallycopilot.motor.precarga
                            .Tesela(18, 1, 1) to ByteArray(3_000),
                    ),
                )
            }
            pendientes.guardar(TeselasPrecarga.corredor(tramo.traza, 18))

            val antes = precarga.estado.first()
            assertEquals(1L, antes.teselas)
            assertTrue(antes.bytes > 0)

            precarga.borrar()

            val despues = precarga.estado.first()
            assertEquals(0L, despues.teselas)
            assertEquals(0L, despues.bytes)
            assertFalse(archivo.exists())
            assertTrue(pendientes.leer().isEmpty())
        }

    private companion object {
        const val ESPERA_MS = 5_000L
    }
}
