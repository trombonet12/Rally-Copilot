package app.trombito.rallycopilot.data.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProveedorViaActualOsmTest {
    @get:Rule val carpeta = TemporaryFolder()

    private fun proveedor(archivo: File) = ProveedorViaActualOsm(archivo, UnconfinedTestDispatcher())

    private fun fijacion(
        lat: Double,
        lon: Double,
    ) = Fijacion(PuntoGeo(lat, lon), tiempoMs = 0, velocidadMs = 15.0, rumboGrados = 90.0, precisionM = 4.0)

    private fun linea(
        id: Int,
        maxspeed: String,
        ref: String,
        nombre: String,
        lat: String,
    ) = "$id\tprimary\t$maxspeed\t\t\tno\t$ref\t$nombre\t${lat}00000,2900000;${lat}00000,2901000"

    private fun escribir(
        archivo: File,
        vararg lineas: String,
        cabecera: String = "#vias-mallorca v1",
    ) {
        GZIPOutputStream(archivo.outputStream()).bufferedWriter(Charsets.UTF_8).use { salida ->
            salida.write(cabecera + "\n")
            lineas.forEach { salida.write(it + "\n") }
        }
    }

    private fun archivoDeVias(
        maxspeedPrimera: String = "60",
        nombre: String = "vias-mallorca.tsv.gz",
    ): File {
        val archivo = File(carpeta.root, nombre)
        escribir(
            archivo,
            linea(1, maxspeedPrimera, "MA-1", "Carretera de prueba", "396"),
            linea(2, "", "", "Cami sin limite", "397"),
        )
        return archivo
    }

    @Test
    fun `devuelve el limite de la via con maxspeed y null en la que no lo tiene`() =
        runTest(UnconfinedTestDispatcher()) {
            val proveedor = proveedor(archivoDeVias())

            val conLimite = proveedor.viaEn(fijacion(39.6, 2.9005))
            val sinLimite = proveedor.viaEn(fijacion(39.7, 2.9005))

            assertEquals(60, conLimite?.limiteKmh)
            assertEquals("MA-1", conLimite?.ref)
            assertEquals("Carretera de prueba", conLimite?.nombre)
            assertNotNull(sinLimite)
            assertNull(sinLimite?.limiteKmh)
            assertNull(sinLimite?.ref)
            assertEquals("Cami sin limite", sinLimite?.nombre)
        }

    @Test
    fun `lejos de cualquier via devuelve null`() =
        runTest(UnconfinedTestDispatcher()) {
            assertNull(proveedor(archivoDeVias()).viaEn(fijacion(39.65, 2.95)))
        }

    @Test
    fun `sin archivo devuelve null`() =
        runTest(UnconfinedTestDispatcher()) {
            val proveedor = proveedor(File(carpeta.root, "no-existe.tsv.gz"))

            assertNull(proveedor.viaEn(fijacion(39.6, 2.9005)))
        }

    @Test
    fun `un archivo que no es gzip o con otra cabecera devuelve null`() =
        runTest(UnconfinedTestDispatcher()) {
            val corrupto = File(carpeta.root, "corrupto.tsv.gz").apply { writeText("esto no es gzip") }
            val otraVersion = File(carpeta.root, "v2.tsv.gz")
            escribir(otraVersion, linea(1, "60", "MA-1", "X", "396"), cabecera = "#vias-mallorca v2")

            assertNull(proveedor(corrupto).viaEn(fijacion(39.6, 2.9005)))
            assertNull(proveedor(otraVersion).viaEn(fijacion(39.6, 2.9005)))
        }

    @Test
    fun `recarga las vias cuando el archivo cambia`() =
        runTest(UnconfinedTestDispatcher()) {
            val archivo = archivoDeVias(maxspeedPrimera = "60")
            val proveedor = proveedor(archivo)
            assertEquals(60, proveedor.viaEn(fijacion(39.6, 2.9005))?.limiteKmh)
            val modificadoAntes = archivo.lastModified()

            escribir(archivo, linea(1, "80", "MA-1", "Carretera de prueba", "396"))
            archivo.setLastModified(modificadoAntes + 5000)

            assertEquals(80, proveedor.viaEn(fijacion(39.6, 2.9005))?.limiteKmh)
            assertNull(proveedor.viaEn(fijacion(39.7, 2.9005)))
        }

    @Test
    fun `si el archivo desaparece devuelve null y al volver se carga de nuevo`() =
        runTest(UnconfinedTestDispatcher()) {
            val archivo = archivoDeVias()
            val proveedor = proveedor(archivo)
            assertEquals(60, proveedor.viaEn(fijacion(39.6, 2.9005))?.limiteKmh)

            archivo.delete()
            assertNull(proveedor.viaEn(fijacion(39.6, 2.9005)))

            archivoDeVias()
            assertEquals(60, proveedor.viaEn(fijacion(39.6, 2.9005))?.limiteKmh)
        }
}
