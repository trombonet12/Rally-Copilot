package app.trombito.rallycopilot.ui.mapasoffline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.EstimacionZona
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_DATO_GRANDE
import app.trombito.rallycopilot.ui.comun.ETIQUETA_MAPA_PANEL
import app.trombito.rallycopilot.ui.comun.ETIQUETA_MAPA_PANEL_CABECERA
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Descargar zona sobre la plantilla «mapa con panel»: dato grande, chips de zoom y acciones de icono. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ZonaDetalleScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private var volver = 0
    private var limpiar = 0
    private var confirmar = 0
    private val zooms = mutableListOf<Int>()

    private fun poner(
        estado: ZonaDetalleUiState,
        oscuro: Boolean = true,
    ) = banco.poner {
        RallyCopilotTheme(darkTheme = oscuro) {
            ZonaDetalleScreen(
                estado = estado,
                acciones =
                    AccionesZonaDetalle(
                        onVolver = { volver++ },
                        onZoom = { zooms += it },
                        onLimpiar = { limpiar++ },
                        onConfirmar = { confirmar++ },
                    ),
                mapa = { modificador: Modifier -> Box(modificador.fillMaxSize().testTag("mapa-falso")) },
            )
        }
    }

    @Test
    fun `agrupa los miles con espacio duro y parte el tamano en cifra y unidad`() {
        assertEquals("1 240", agruparMiles(1_240))
        assertEquals("12 345 678", agruparMiles(12_345_678))
        assertEquals("999", agruparMiles(999))
        assertEquals("0", agruparMiles(-4))
        val partes = partesEstimacion(1_240, 86L * 1024 * 1024, "teselas")
        assertEquals("1 240", partes[0].valor)
        assertEquals("teselas", partes[0].unidad)
        assertEquals("86", partes[1].valor)
        assertEquals("MB", partes[1].unidad)
    }

    @Test
    fun `el panel enseña el dato grande con teselas y tamano`() {
        poner(ZonaDetalleUiState(estimacion = EstimacionZona(1_240, 86L * 1024 * 1024)))
        assertTrue(banco.hay(ETIQUETA_MAPA_PANEL))
        assertTrue(banco.hay(ETIQUETA_MAPA_PANEL_CABECERA))
        assertTrue(banco.hay(ETIQUETA_ZONA_ESTIMACION))
        assertEquals(
            listOf("1 240 teselas · 86 MB"),
            banco.textos(banco.conEtiqueta(ETIQUETA_DATO_GRANDE)),
        )
        assertTrue("DESCARGAR ZONA" in banco.todosLosTextos())
    }

    @Test
    fun `sin estimacion no hay dato grande y no se puede descargar`() {
        poner(ZonaDetalleUiState())
        assertFalse(banco.hay(ETIQUETA_DATO_GRANDE))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_ZONA_CONFIRMAR)))
    }

    @Test
    fun `los chips de zoom, limpiar, confirmar y volver llaman a las acciones`() {
        poner(
            ZonaDetalleUiState(
                esquinas = listOf(PuntoGeo(39.6, 2.7)),
                estimacion = EstimacionZona(10, 1_000_000),
            ),
        )
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ZONA_ZOOM_PREFIJO + 19))
        assertEquals(listOf(19), zooms)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ZONA_LIMPIAR))
        assertEquals(1, limpiar)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ZONA_CONFIRMAR))
        assertEquals(1, confirmar)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }

    @Test
    fun `limpiar solo esta habilitado con esquinas y los iconos tienen descripcion`() {
        poner(ZonaDetalleUiState(estimacion = EstimacionZona(10, 1_000_000)), oscuro = false)
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_ZONA_LIMPIAR)))
        assertEquals(
            "Quitar las esquinas y usar lo que se ve",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_ZONA_LIMPIAR)),
        )
        assertEquals("Descargar esta zona", banco.descripcion(banco.conEtiqueta(ETIQUETA_ZONA_CONFIRMAR)))
        assertTrue(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_ZONA_CONFIRMAR)))
    }
}
