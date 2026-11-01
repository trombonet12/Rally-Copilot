package app.trombito.rallycopilot.data.datosmapa

import android.database.sqlite.SQLiteDatabase
import app.trombito.rallycopilot.data.busqueda.BuscadorSqlite
import app.trombito.rallycopilot.data.notas.ContextoNotasRepositorio
import app.trombito.rallycopilot.data.ruta.EnrutadorValhalla
import app.trombito.rallycopilot.data.ruta.FabricaMotorRutas
import app.trombito.rallycopilot.data.ruta.MotorRutas
import app.trombito.rallycopilot.data.vias.CargadorVias
import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Polyline6
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Los consumidores de `mapa/` miran sus archivos solo después de [EsperaDatosSembrados.esperar]: en estos tests el
 * archivo lo "siembra" la propia espera, así que si se leyera antes, no lo encontrarían.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EsperaSiembraConsumidoresTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val a = PuntoGeo(39.5696, 2.6502)
    private val b = PuntoGeo(39.7667, 2.7150)

    private fun escribirVias(archivo: File) {
        GZIPOutputStream(archivo.outputStream()).bufferedWriter(Charsets.UTF_8).use {
            it.write("#vias-mallorca v1\n")
            it.write("1\tprimary\t60\t\t\tno\tMA-1\tCarretera\t39600000,2900000;39600000,2901000\n")
        }
    }

    @Test
    fun `el cargador de vias no mira el archivo hasta que acaba la siembra y no bloquea mientras tanto`() =
        runTest(UnconfinedTestDispatcher()) {
            val vias = File(carpeta.root, "vias-mallorca.tsv.gz")
            val puerta = CompletableDeferred<Unit>()
            val cargador =
                CargadorVias(
                    vias,
                    UnconfinedTestDispatcher(testScheduler),
                    EsperaDatosSembrados {
                        puerta.await()
                        escribirVias(vias)
                    },
                )

            val pedido = async { cargador.datos() }

            assertFalse("sigue esperando a la siembra", pedido.isCompleted)
            assertEquals(0, cargador.lecturas)
            puerta.complete(Unit)
            val datos = pedido.await()
            assertNotNull("lee el archivo que la siembra acaba de dejar", datos)
            assertEquals(1, cargador.lecturas)
        }

    @Test
    fun `el enrutador espera la siembra antes de buscar el tar`() =
        runTest(UnconfinedTestDispatcher()) {
            val tar = File(carpeta.root, "valhalla-mallorca.tar")
            val shape = Polyline6.codificar(listOf(a, PuntoGeo(39.6, 2.66), b))
            val json =
                """{"trip":{"units":"kilometers","legs":[{"shape":"$shape","maneuvers":[
                {"type":1,"instruction":"Sal","street_names":["Ma-11"],"length":10.0,"time":600.0,
                 "begin_shape_index":0,"end_shape_index":1},
                {"type":4,"instruction":"Llegas","length":0.0,"time":0.0,
                 "begin_shape_index":2,"end_shape_index":2}]}]}}"""
            val fabrica =
                object : FabricaMotorRutas {
                    override fun crear(tar: File): MotorRutas =
                        object : MotorRutas {
                            override fun rutaJson(peticion: String) = json

                            override fun close() = Unit
                        }
                }
            val enrutador =
                EnrutadorValhalla(
                    tar,
                    fabrica,
                    UnconfinedTestDispatcher(testScheduler),
                    System::nanoTime,
                    EsperaDatosSembrados { tar.writeText("x") },
                )

            val resultado = enrutador.ruta(a, b, PerfilRuta.MAS_RAPIDA)

            assertTrue("con el tar sembrado calcula la ruta: $resultado", resultado is ResultadoRuta.Ok)
        }

    @Test
    fun `el buscador espera la siembra antes de abrir el indice`() =
        runTest(UnconfinedTestDispatcher()) {
            val indice = File(carpeta.root, "busqueda-mallorca.sqlite")
            val buscador =
                BuscadorSqlite(
                    indice,
                    UnconfinedTestDispatcher(testScheduler),
                    EsperaDatosSembrados { crearIndice(indice) },
                )

            val resultados = buscador.buscar("Sóller", null, 5)

            assertEquals(listOf("Sóller"), resultados.map { it.nombre })
            buscador.cerrar()
        }

    @Test
    fun `el contexto de notas espera la siembra antes de leer las vias y sus notas`() =
        runTest(UnconfinedTestDispatcher()) {
            var sembrado = false
            var sembradoAlLeerVias: Boolean? = null
            val fuente =
                object : FuenteVias {
                    override suspend fun datos(): DatosVias? {
                        sembradoAlLeerVias = sembrado
                        return null
                    }
                }
            val notas =
                ContextoNotasRepositorio(
                    fuente,
                    File(carpeta.root, "vias-notas-mallorca.tsv.gz"),
                    UnconfinedTestDispatcher(testScheduler),
                    EsperaDatosSembrados { sembrado = true },
                )

            notas.contexto()

            assertEquals(true, sembradoAlLeerVias)
        }

    private fun crearIndice(destino: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(destino, null)
        db.execSQL(
            "CREATE TABLE lugares (nombre TEXT NOT NULL, nombre_norm TEXT NOT NULL, tipo TEXT NOT NULL, " +
                "categoria TEXT NOT NULL, municipio TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, " +
                "importancia INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO lugares VALUES ('Sóller', 'soller', 'place', 'pueblo', 'Sóller', 39.766, 2.715, 80)",
        )
        db.version = 2
        db.close()
    }
}
