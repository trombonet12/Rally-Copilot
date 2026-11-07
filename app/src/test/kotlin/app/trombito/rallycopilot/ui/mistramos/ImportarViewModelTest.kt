package app.trombito.rallycopilot.ui.mistramos

import android.content.Intent
import android.net.Uri
import app.trombito.rallycopilot.data.escucha.EdicionesFalsas
import app.trombito.rallycopilot.data.importar.AbridorDocumentos
import app.trombito.rallycopilot.data.importar.DocumentoAbierto
import app.trombito.rallycopilot.data.importar.ImportadorTramos
import app.trombito.rallycopilot.data.tramos.GuardadorTramoArchivo
import app.trombito.rallycopilot.data.tramos.GuardadorTramos
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportarViewModelTest {
    private val repositorio = FalsoRepositorioTramos()
    private val gpx =
        checkNotNull(javaClass.getResourceAsStream("/importar/strava.gpx")).use { it.readBytes() }

    /** Cualquier `content://` abre el GPX de Strava; el resto no se puede abrir. */
    private val abridor =
        AbridorDocumentos { uri ->
            if (uri.toString().endsWith("/bueno")) {
                DocumentoAbierto(ByteArrayInputStream(gpx), "mi_ruta.gpx", gpx.size.toLong())
            } else {
                null
            }
        }

    private fun importador() =
        ImportadorTramos(
            abridor,
            GuardadorTramos(repositorio) { null },
            GuardadorTramoArchivo(repositorio, { null }, EdicionesFalsas()),
        )

    private fun vm() = ImportarViewModel(importador())

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private suspend fun ImportarViewModel.esperarFin(): EstadoImportacion {
        while (estado.value is EstadoImportacion.Importando || estado.value is EstadoImportacion.Inactivo) {
            kotlinx.coroutines.delay(5)
        }
        return estado.value
    }

    @Test
    fun `importar un archivo bueno termina Hecho con el tramo y vuelve a Inactivo al verlo`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importar(Uri.parse("content://docs/bueno"))
            val fin = vm.esperarFin()
            assertTrue(fin is EstadoImportacion.Hecho)
            assertEquals("Mañana por la Ma-13", (fin as EstadoImportacion.Hecho).nombre)
            assertEquals(1, repositorio.sinPasadas.size)
            vm.onVisto()
            assertEquals(EstadoImportacion.Inactivo, vm.estado.value)
        }

    @Test
    fun `un fallo se guarda con su motivo`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importar(Uri.parse("content://docs/malo"))
            assertEquals(EstadoImportacion.Fallo(MotivoFalloImportar.NO_SE_PUEDE_ABRIR), vm.esperarFin())
        }

    @Test
    fun `un archivo entrante pide ir a Mis tramos y espera confirmacion sin guardar nada`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importarEntrante(Uri.parse("content://docs/bueno"))
            kotlinx.coroutines.withTimeout(5_000) { vm.irAMisTramos.first() }
            val pendiente = vm.esperarFin()
            assertTrue(pendiente.toString(), pendiente is EstadoImportacion.Pendiente)
            assertEquals("Mañana por la Ma-13", (pendiente as EstadoImportacion.Pendiente).nombre)
            assertTrue("Unos 5 km: ${pendiente.longitudM}", pendiente.longitudM in 3_000.0..8_000.0)
            assertTrue("Hasta confirmar no se guarda", repositorio.sinPasadas.isEmpty())
        }

    @Test
    fun `confirmar guarda el tramo pendiente y termina Hecho`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importarEntrante(Uri.parse("content://docs/bueno"))
            vm.esperarFin()
            vm.confirmar()
            val fin = vm.esperarFin()
            assertTrue(fin.toString(), fin is EstadoImportacion.Hecho)
            assertEquals(1, repositorio.sinPasadas.size)
            vm.confirmar()
            assertEquals("Un segundo confirmar no guarda dos veces", 1, repositorio.sinPasadas.size)
        }

    @Test
    fun `cancelar descarta el archivo pendiente y no guarda nada`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importarEntrante(Uri.parse("content://docs/bueno"))
            vm.esperarFin()
            vm.cancelar()
            assertEquals(EstadoImportacion.Inactivo, vm.estado.value)
            vm.confirmar()
            assertEquals(EstadoImportacion.Inactivo, vm.estado.value)
            assertTrue(repositorio.sinPasadas.isEmpty())
        }

    @Test
    fun `un archivo entrante que no se puede leer avisa sin preguntar`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importarEntrante(Uri.parse("content://docs/malo"))
            assertEquals(EstadoImportacion.Fallo(MotivoFalloImportar.NO_SE_PUEDE_ABRIR), vm.esperarFin())
        }

    @Test
    fun `el selector propio importa directamente sin pedir confirmacion`() =
        kotlinx.coroutines.runBlocking {
            val vm = vm()
            vm.importar(Uri.parse("content://docs/bueno"))
            assertTrue(vm.esperarFin() is EstadoImportacion.Hecho)
        }

    @Test
    fun `el intent de Abrir con y el de Compartir entregan su content Uri`() {
        val uri = Uri.parse("content://docs/ruta")
        val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/gpx+xml")
        assertEquals(uri, ver.uriDeImportacion())
        val enviar = Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(uri, enviar.uriDeImportacion())
    }

    @Test
    fun `los file Uri, otras acciones y el arranque normal no importan nada`() {
        val privado = Uri.parse("file:///data/data/app.trombito.rallycopilot/databases/rally.db")
        assertNull(Intent(Intent.ACTION_VIEW, privado).uriDeImportacion())
        assertNull(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, privado).uriDeImportacion())
        assertNull(Intent(Intent.ACTION_MAIN).uriDeImportacion())
        assertNull(Intent(Intent.ACTION_SEND).uriDeImportacion())
    }
}
