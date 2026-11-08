package app.trombito.rallycopilot.data.vias

import app.trombito.rallycopilot.data.datosmapa.EsperaDatosSembrados
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CargadorPkTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val archivo get() = File(carpeta.root, "pk-mallorca.tsv")
    private val posicion = PuntoGeo(39.7, 2.7)
    private var escrituras = 1

    private fun escribir(
        cabecera: String,
        vararg lineas: String,
    ) {
        archivo.writeText((listOf(cabecera) + lineas).joinToString("\n") + "\n")
        // Que dos escrituras seguidas no coincidan en fecha y tamaño.
        archivo.setLastModified(archivo.lastModified() + escrituras++ * 5_000)
    }

    private fun cargador(siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna) =
        CargadorPk(archivo, UnconfinedTestDispatcher(), siembra)

    @Test
    fun `sin archivo no hay punto kilometrico y no es un error`() =
        runTest(UnconfinedTestDispatcher()) {
            val cargador = cargador()

            assertNull(cargador.puntoKilometrico("Ma-10", posicion))
            assertEquals(0, cargador.lecturas)
        }

    @Test
    fun `lee el archivo una sola vez y da el kilometro de la carretera`() =
        runTest(UnconfinedTestDispatcher()) {
            escribir("#pk v1", "Ma-10\t23\t39.7001\t2.7", "Ma-11\t5\t39.7\t2.7", "linea rota")
            val cargador = cargador()

            val primero = cargador.puntoKilometrico("Ma-10", posicion)
            val segundo = cargador.puntoKilometrico("ma-10", posicion)

            assertEquals(23.0, primero!!.km, 1e-9)
            assertEquals(primero, segundo)
            assertNull(cargador.puntoKilometrico("Ma-99", posicion))
            assertEquals(1, cargador.lecturas)
        }

    @Test
    fun `relee el archivo si cambia`() =
        runTest(UnconfinedTestDispatcher()) {
            escribir("#pk v1", "Ma-10\t23\t39.7\t2.7")
            val cargador = cargador()
            assertEquals(23.0, cargador.puntoKilometrico("Ma-10", posicion)!!.km, 1e-9)

            escribir("#pk v1", "Ma-10\t24\t39.7\t2.7")

            assertEquals(24.0, cargador.puntoKilometrico("Ma-10", posicion)!!.km, 1e-9)
            assertEquals(2, cargador.lecturas)
        }

    @Test
    fun `un archivo con otra cabecera se trata como ausente y no se reintenta hasta que cambie`() =
        runTest(UnconfinedTestDispatcher()) {
            escribir("#pk v9", "Ma-10\t23\t39.7\t2.7")
            val cargador = cargador()

            assertNull(cargador.puntoKilometrico("Ma-10", posicion))
            assertNull(cargador.puntoKilometrico("Ma-10", posicion))
            assertEquals(1, cargador.lecturas)
        }

    @Test
    fun `espera a la siembra antes de mirar el archivo`() =
        runTest(UnconfinedTestDispatcher()) {
            val puerta = CompletableDeferred<Unit>()
            val cargador =
                cargador(
                    EsperaDatosSembrados {
                        puerta.await()
                        escribir("#pk v1", "Ma-10\t23\t39.7\t2.7")
                    },
                )

            val pendiente = async { cargador.puntoKilometrico("Ma-10", posicion) }

            assertFalse(pendiente.isCompleted)
            puerta.complete(Unit)
            assertEquals(23.0, pendiente.await()!!.km, 1e-9)
        }
}
