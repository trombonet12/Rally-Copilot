package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.copia.CopiaDatos
import app.trombito.rallycopilot.domain.copia.CopiaInvalidaException
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import app.trombito.rallycopilot.domain.copia.ResumenCopia
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

private class FalsaCopia : CopiaDatos {
    var fallo: CopiaInvalidaException? = null
    var exportadas = 0
    var importadas = 0

    override suspend fun exportar(salida: OutputStream): ResumenCopia {
        fallo?.let { throw it }
        exportadas++
        return RESUMEN
    }

    override suspend fun importar(entrada: InputStream): ResumenCopia {
        fallo?.let { throw it }
        importadas++
        return RESUMEN
    }

    companion object {
        val RESUMEN = ResumenCopia(12, "1", 1, 1, 0, 0, 0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CopiaViewModelTest {
    private val copia = FalsaCopia()
    private var reinicios = 0

    @Before
    fun preparar() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear() = CopiaViewModel(copia) { reinicios++ }

    @Test
    fun `exportar escribe y avisa`() =
        runTest {
            val vm = crear()
            vm.exportar { ByteArrayOutputStream() }
            assertEquals(EstadoCopia.Trabajando, vm.estado.value)
            advanceUntilIdle()
            assertEquals(EstadoCopia.Exportada, vm.estado.value)
            assertEquals(1, copia.exportadas)
            vm.avisoVisto()
            assertEquals(EstadoCopia.Inactiva, vm.estado.value)
        }

    @Test
    fun `importar bien avisa y reinicia`() =
        runTest {
            val vm = crear()
            vm.importar { ByteArrayInputStream(ByteArray(1)) }
            advanceUntilIdle()
            assertEquals(1, copia.importadas)
            assertEquals(1, reinicios)
            assertEquals(EstadoCopia.Importada, vm.estado.value)
        }

    @Test
    fun `una copia invalida da el motivo y no reinicia`() =
        runTest {
            copia.fallo = CopiaInvalidaException(MotivoCopiaInvalida.ESQUEMA_FUTURO)
            val vm = crear()
            vm.importar { ByteArrayInputStream(ByteArray(1)) }
            advanceUntilIdle()
            assertEquals(EstadoCopia.Fallo(MotivoCopiaInvalida.ESQUEMA_FUTURO), vm.estado.value)
            assertEquals(0, reinicios)
        }

    @Test
    fun `si no se puede abrir el archivo falla sin tocar nada`() =
        runTest {
            val vm = crear()
            vm.importar { null }
            advanceUntilIdle()
            assertEquals(EstadoCopia.Fallo(MotivoCopiaInvalida.NO_SE_PUEDE_ABRIR), vm.estado.value)
            vm.exportar { null }
            advanceUntilIdle()
            assertEquals(EstadoCopia.Fallo(MotivoCopiaInvalida.NO_SE_PUEDE_ABRIR), vm.estado.value)
            assertEquals(0, copia.importadas + copia.exportadas)
        }

    @Test
    fun `el nombre del archivo lleva la fecha`() {
        assertEquals("rally-copilot-2026-11-06.zip", nombreArchivoCopia(LocalDate.of(2026, 11, 6)))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CopiaAjustesPantallaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `datos ofrece exportar e importar con icono`() {
        var exportar = 0
        var importar = 0
        banco.poner {
            RallyCopilotTheme {
                AjustesScreen(
                    ajustes = AjustesConduccion(),
                    acciones = AccionesAjustes(onExportarCopia = { exportar++ }, onImportarCopia = { importar++ }),
                )
            }
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_EXPORTAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AJUSTES_IMPORTAR))
        assertEquals(1, exportar)
        assertEquals(1, importar)
        val textos = banco.textos(banco.raiz())
        assertTrue("Exportar copia" in textos)
        assertTrue("Importar copia" in textos)
    }

    @Test
    fun `con una copia en curso las filas no responden`() {
        var toques = 0
        banco.poner {
            RallyCopilotTheme {
                AjustesScreen(
                    ajustes = AjustesConduccion(),
                    acciones = AccionesAjustes(onExportarCopia = { toques++ }, onImportarCopia = { toques++ }),
                    copiaEnCurso = true,
                )
            }
        }
        assertTrue(!banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_AJUSTES_EXPORTAR)))
        assertTrue(!banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_AJUSTES_IMPORTAR)))
        assertTrue(banco.hay(ETIQUETA_COPIA_PROGRESO))
        assertEquals(0, toques)
    }

    @Test
    fun `el dialogo de importar avisa de que sustituye y confirma o cancela`() {
        var confirmado = 0
        var cancelado = 0
        banco.poner {
            RallyCopilotTheme {
                DialogoImportarCopia(onConfirmar = { confirmado++ }, onCancelar = { cancelado++ })
            }
        }
        assertTrue(banco.todosLosTextos().any { "Se sustituirán tus tramos, recorridos y ajustes" in it })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COPIA_CANCELAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COPIA_CONFIRMAR))
        assertEquals(1, cancelado)
        assertEquals(1, confirmado)
    }
}
