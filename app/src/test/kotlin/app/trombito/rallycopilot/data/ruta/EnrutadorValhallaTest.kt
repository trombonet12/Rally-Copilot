package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Polyline6
import com.valhalla.valhalla.ErrorResponse
import com.valhalla.valhalla.ValhallaException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EnrutadorValhallaTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val a = PuntoGeo(39.5696, 2.6502)
    private val b = PuntoGeo(39.7667, 2.7150)

    private class MotorFalso(
        val respuesta: (String) -> String,
    ) : MotorRutas {
        val peticiones = mutableListOf<String>()
        var cerrado = false

        override fun rutaJson(peticion: String): String {
            peticiones += peticion
            return respuesta(peticion)
        }

        override fun close() {
            cerrado = true
        }
    }

    private class FabricaFalsa(
        val respuesta: (String) -> String,
    ) : FabricaMotorRutas {
        val motores = mutableListOf<MotorFalso>()

        override fun crear(tar: File): MotorRutas = MotorFalso(respuesta).also { motores += it }
    }

    private fun rutaOk(medio: PuntoGeo = PuntoGeo(39.6, 2.66)): String {
        val shape = Polyline6.codificar(listOf(a, medio, b))
        return """{"trip":{"units":"kilometers","legs":[{"shape":"$shape","maneuvers":[
            {"type":1,"instruction":"Sal","street_names":["Ma-11"],"length":10.0,"time":600.0,
             "begin_shape_index":0,"end_shape_index":1},
            {"type":4,"instruction":"Llegas","length":0.0,"time":0.0,
             "begin_shape_index":2,"end_shape_index":2}]}]}}"""
    }

    private fun enrutador(
        tar: File,
        fabrica: FabricaMotorRutas,
    ) = EnrutadorValhalla(tar, fabrica, UnconfinedTestDispatcher(), System::nanoTime)

    private fun tarExistente(): File = carpeta.newFile("valhalla-mallorca.tar").also { it.writeText("x") }

    @Test
    fun `sin tar devuelve SinDatos y no abre el motor`() =
        runTest {
            val fabrica = FabricaFalsa { rutaOk() }
            val r = enrutador(File(carpeta.root, "no-existe.tar"), fabrica).ruta(a, b, PerfilRuta.MAS_RAPIDA)
            assertEquals(ResultadoRuta.SinDatos, r)
            assertTrue(fabrica.motores.isEmpty())
        }

    @Test
    fun `una ruta correcta se lee y se mide`() =
        runTest {
            val r = enrutador(tarExistente(), FabricaFalsa { rutaOk() }).ruta(a, b, PerfilRuta.SINUOSA)
            r as ResultadoRuta.Ok
            assertEquals(2, r.ruta.maniobras.size)
            assertEquals(10_000.0, r.ruta.longitudM, 0.001)
            assertTrue(r.calculoMs >= 0)
        }

    @Test
    fun `el motor se reutiliza entre peticiones`() =
        runTest {
            val fabrica = FabricaFalsa { rutaOk() }
            val e = enrutador(tarExistente(), fabrica)
            e.ruta(a, b, PerfilRuta.MAS_RAPIDA)
            e.ruta(b, a, PerfilRuta.MAS_RAPIDA)
            assertEquals(1, fabrica.motores.size)
            assertEquals(2, fabrica.motores[0].peticiones.size)
        }

    @Test
    fun `si el tar cambia se cierra el motor viejo y se abre otro`() =
        runTest {
            val tar = tarExistente()
            val fabrica = FabricaFalsa { rutaOk() }
            val e = enrutador(tar, fabrica)
            e.ruta(a, b, PerfilRuta.MAS_RAPIDA)
            tar.setLastModified(tar.lastModified() + 10_000)
            e.ruta(a, b, PerfilRuta.MAS_RAPIDA)
            assertEquals(2, fabrica.motores.size)
            assertTrue(fabrica.motores[0].cerrado)
        }

    @Test
    fun `un error de Valhalla pasa a Error con su mensaje`() =
        runTest {
            val fabrica =
                FabricaFalsa { _ ->
                    throw ValhallaException.Internal(ErrorResponse(171, "No suitable edges"))
                }
            val r = enrutador(tarExistente(), fabrica).ruta(a, b, PerfilRuta.MAS_RAPIDA)
            r as ResultadoRuta.Error
            assertTrue(r.motivo, "No suitable edges" in r.motivo)
        }

    @Test
    fun `una respuesta ilegible pasa a Error`() =
        runTest {
            val r = enrutador(tarExistente(), FabricaFalsa { _ -> "no es json" }).ruta(a, b, PerfilRuta.MAS_RAPIDA)
            assertTrue(r is ResultadoRuta.Error)
        }

    @Test
    fun `si el motor no se puede abrir pasa a Error`() =
        runTest {
            val fabrica = FabricaMotorRutas { error("no se pudo crear el actor") }
            val r = enrutador(tarExistente(), fabrica).ruta(a, b, PerfilRuta.MAS_RAPIDA)
            r as ResultadoRuta.Error
            assertTrue(r.motivo, "no se pudo crear el actor" in r.motivo)
        }

    private fun respuestaConLongitud(km: Double): String {
        val shape = Polyline6.codificar(listOf(a, b))
        return """{"trip":{"units":"kilometers","legs":[{"shape":"$shape","maneuvers":[
            {"type":1,"instruction":"Sal","street_names":["Ma-11"],"length":$km,"time":600.0,
             "begin_shape_index":0,"end_shape_index":1},
            {"type":4,"instruction":"Llegas","length":0.0,"time":0.0,
             "begin_shape_index":1,"end_shape_index":1}]}]}}"""
    }

    private fun sinuosa(
        kmExcluida: Double?,
        kmNormal: Double,
    ): Pair<ResultadoRuta, FabricaFalsa> {
        val fabrica =
            FabricaFalsa { peticion ->
                if ("exclude_tunnels" in peticion) {
                    kmExcluida?.let(::respuestaConLongitud)
                        ?: throw ValhallaException.Internal(ErrorResponse(442, "No path could be found"))
                } else {
                    respuestaConLongitud(kmNormal)
                }
            }
        var resultado: ResultadoRuta = ResultadoRuta.SinDatos
        runTest { resultado = enrutador(tarExistente(), fabrica).ruta(a, b, PerfilRuta.SINUOSA) }
        return resultado to fabrica
    }

    @Test
    fun `sinuosa con exclusion correcta y corta devuelve la excluida`() {
        val (r, fabrica) = sinuosa(kmExcluida = 30.0, kmNormal = 25.0)
        assertEquals(30_000.0, (r as ResultadoRuta.Ok).ruta.longitudM, 0.001)
        assertEquals(2, fabrica.motores[0].peticiones.size)
    }

    @Test
    fun `sinuosa con exclusion en el limite exacto devuelve la excluida`() {
        val (r, _) = sinuosa(kmExcluida = 40.0, kmNormal = 25.0)
        assertEquals(40_000.0, (r as ResultadoRuta.Ok).ruta.longitudM, 0.001)
    }

    @Test
    fun `sinuosa con exclusion sin ruta devuelve la normal`() {
        val (r, fabrica) = sinuosa(kmExcluida = null, kmNormal = 25.0)
        assertEquals(25_000.0, (r as ResultadoRuta.Ok).ruta.longitudM, 0.001)
        assertTrue("exclude_tunnels" in fabrica.motores[0].peticiones[0])
        assertTrue("exclude_tunnels" !in fabrica.motores[0].peticiones[1])
    }

    @Test
    fun `sinuosa con exclusion mas de 1,6 veces mayor devuelve la normal`() {
        val (r, _) = sinuosa(kmExcluida = 41.0, kmNormal = 25.0)
        assertEquals(25_000.0, (r as ResultadoRuta.Ok).ruta.longitudM, 0.001)
    }

    @Test
    fun `la mas rapida hace un solo calculo sin exclusion`() {
        val fabrica = FabricaFalsa { respuestaConLongitud(10.0) }
        runTest { enrutador(tarExistente(), fabrica).ruta(a, b, PerfilRuta.MAS_RAPIDA) }
        assertEquals(1, fabrica.motores[0].peticiones.size)
        assertTrue("exclude_tunnels" !in fabrica.motores[0].peticiones[0])
    }
}
