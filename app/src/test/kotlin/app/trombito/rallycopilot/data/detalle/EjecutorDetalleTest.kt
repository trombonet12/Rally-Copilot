package app.trombito.rallycopilot.data.detalle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.datosmapa.EntornoDatosMapa
import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import app.trombito.rallycopilot.motor.precarga.Tesela
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
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

@RunWith(RobolectricTestRunner::class)
class EjecutorDetalleTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var base: RallyDatabase
    private lateinit var pendientes: RutasPendientes
    private var energiaOk = true

    private val tramo =
        Tramo("t1", List(6) { PuntoGeo(39.6 + it * 0.001, 2.9) }, 556.0)
    private val corredor = TeselasPrecarga.corredor(tramo.traza, 18)

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        entorno = EntornoDatosMapa(carpeta.newFolder("e"))
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        pendientes = RutasPendientes(carpeta.newFolder("rutas"))
    }

    @After
    fun cerrar() {
        base.close()
        entorno.close()
    }

    private fun instalarSatelite() =
        runBlocking {
            base.datosMapaDao().guardarArchivos(
                listOf(ArchivoInstaladoEntity("satelite", "pnoa-mallorca-z16.pmtiles", "h", 1_000, "v")),
            )
        }

    private fun ejecutor(
        fuente: FuenteTeselas,
        tramos: FalsoRepositorioTramos =
            FalsoRepositorioTramos(listOf(resumenFalso("t1")), trazas = mapOf("t1" to tramo)),
    ) = EjecutorDetalle(
        entorno.config,
        tramos,
        base.datosMapaDao(),
        base.ajustesMapasDao(),
        fuente,
        { energiaOk },
        pendientes,
    )

    private fun teselasGuardadas(): Int =
        AlmacenDetalle(File(entorno.mapa, NOMBRE_DETALLE)).use {
            it.contarTeselas().toInt()
        }

    @Test
    fun `automatico baja el corredor z18 de los tramos guardados`() =
        runBlocking {
            instalarSatelite()
            val fuente = FuenteFalsa()

            val r = ejecutor(fuente).automatico()

            assertEquals(ResultadoEjecucion.HECHO, r)
            assertEquals(corredor, fuente.pedidas.toSet())
            assertEquals(corredor.size, teselasGuardadas())
        }

    @Test
    fun `sin satelite instalado la descarga automatica no hace nada`() =
        runBlocking {
            val fuente = FuenteFalsa()

            assertEquals(ResultadoEjecucion.SIN_SATELITE, ejecutor(fuente).automatico())
            assertTrue(fuente.pedidas.isEmpty())
        }

    @Test
    fun `con bateria baja y sin cargar espera y no pide nada`() =
        runBlocking {
            instalarSatelite()
            energiaOk = false
            val fuente = FuenteFalsa()

            assertEquals(ResultadoEjecucion.ESPERA_ENERGIA, ejecutor(fuente).automatico())
            assertTrue(fuente.pedidas.isEmpty())
        }

    @Test
    fun `la ruta planificada pendiente se baja y su archivo se borra al terminar`() =
        runBlocking {
            instalarSatelite()
            val ruta = List(4) { PuntoGeo(39.7, 2.7 + it * 0.001) }
            val corredorRuta = TeselasPrecarga.corredor(ruta, 18)
            val archivo = checkNotNull(pendientes.guardar(corredorRuta))
            val fuente = FuenteFalsa()

            val r = ejecutor(fuente, FalsoRepositorioTramos()).automatico()

            assertEquals(ResultadoEjecucion.HECHO, r)
            assertEquals(corredorRuta, fuente.pedidas.toSet())
            assertFalse(archivo.exists())
        }

    @Test
    fun `una segunda pasada no repite las teselas ya bajadas`() =
        runBlocking {
            instalarSatelite()
            ejecutor(FuenteFalsa()).automatico()
            val segunda = FuenteFalsa()

            ejecutor(segunda).automatico()

            assertTrue(segunda.pedidas.isEmpty())
        }

    @Test
    fun `las teselas de mar no se guardan`() =
        runBlocking {
            instalarSatelite()
            val fuente = FuenteFalsa(mar = { it.y % 2 == 0 })

            ejecutor(fuente).automatico()

            val esperadas = corredor.count { it.y % 2 != 0 }
            assertEquals(esperadas, teselasGuardadas())
        }

    @Test
    fun `al llegar al limite de espacio para y conserva la ruta pendiente`() =
        runBlocking {
            instalarSatelite()
            base.ajustesMapasDao().guardarAjustes(AjustesMapasEntity(soloWifi = true, limiteBytes = 1_000L))
            val archivo = checkNotNull(pendientes.guardar(corredor))
            val fuente = FuenteFalsa()

            val r = ejecutor(fuente).automatico()

            assertEquals(ResultadoEjecucion.LIMITE, r)
            assertTrue(fuente.pedidas.isEmpty())
            assertTrue(archivo.exists())
        }

    @Test
    fun `una zona manual baja el rectangulo a z19 aunque no haya satelite ni se cumpla la energia`() =
        runBlocking {
            energiaOk = false
            val zona = Rectangulo(2.6500, 39.5600, 2.6510, 39.5610)
            val fuente = FuenteFalsa()

            val r = ejecutor(fuente).zona(zona, 19)

            assertEquals(ResultadoEjecucion.HECHO, r)
            val esperadas = TeselasPrecarga.rectangulo(zona, 19).toSet()
            assertTrue(esperadas.isNotEmpty())
            assertEquals(esperadas, fuente.pedidas.toSet())
            assertTrue(fuente.pedidas.all { it.z == 19 })
        }

    @Test
    fun `una zona fuera de Mallorca no pide nada`() =
        runBlocking {
            val fuente = FuenteFalsa()

            val r = ejecutor(fuente).zona(Rectangulo(0.0, 41.0, 0.01, 41.01), 18)

            assertEquals(ResultadoEjecucion.HECHO, r)
            assertTrue(fuente.pedidas.isEmpty())
        }

    @Test
    fun `las rutas pendientes ilegibles se descartan`() {
        val malo = File(carpeta.root, "rutas/ruta-0.txt").apply { writeText("a,b x") }
        assertTrue(pendientes.leer().isEmpty())
        assertFalse(malo.exists())
        assertEquals(setOf<Tesela>(), pendientes.leer().flatMap { it.second }.toSet())
    }
}
