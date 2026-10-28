package app.trombito.rallycopilot.ui.mapasoffline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.ui.comun.BancoCompose
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapasOfflineScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val instalados = DatosInstalados("2026.10.1", 1_500_000_000, conSatelite = true)

    private fun poner(
        datos: EstadoDatosMapa = EstadoDatosMapa(),
        ajustes: AjustesMapas = AjustesMapas(),
        cargando: Boolean = false,
        acciones: AccionesMapasOffline = AccionesMapasOffline(),
    ) = banco.poner {
        RallyCopilotTheme { MapasOfflineScreen(MapasOfflineUiState(cargando, datos, ajustes), acciones) }
    }

    private fun textos() = banco.textos(banco.raiz())

    @Test
    fun `cargando solo muestra un indicador`() {
        poner(cargando = true)
        assertTrue(banco.hay("mapas-fase-cargando"))
        assertFalse(banco.hay(ETIQUETA_DESCARGAR))
    }

    @Test
    fun `sin datos ofrece descargar y no borrar`() {
        var descargas = 0
        poner(acciones = AccionesMapasOffline(onDescargar = { descargas++ }))
        assertTrue(banco.hay("mapas-fase-sin_datos"))
        assertTrue("Todavía no hay mapas de Mallorca en este móvil." in textos())
        assertFalse(banco.hay(ETIQUETA_BORRAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DESCARGAR))
        assertEquals(1, descargas)
    }

    @Test
    fun `instalado muestra version y tamano y permite borrar`() {
        var borrados = 0
        poner(EstadoDatosMapa(instalados = instalados), acciones = AccionesMapasOffline(onBorrar = { borrados++ }))
        assertTrue(banco.hay("mapas-fase-instalado"))
        assertTrue("Versión 2026.10.1" in textos())
        assertTrue("Incluye satélite" in textos())
        assertTrue(textos().any { it.startsWith("Ocupan ") })
        assertFalse(banco.hay(ETIQUETA_DESCARGAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_BORRAR))
        assertEquals("el borrado pide confirmación antes de ejecutarse", 0, borrados)
    }

    @Test
    fun `descargando muestra el progreso y cancelar`() {
        var cancelaciones = 0
        val progreso = ProgresoDescarga(bytesHechos = 500, bytesTotales = 1_000, esperandoRed = false)
        poner(EstadoDatosMapa(descarga = progreso), acciones = AccionesMapasOffline(onCancelar = { cancelaciones++ }))
        assertTrue(banco.hay("mapas-fase-descargando"))
        assertTrue(textos().any { it.endsWith("· 50 %") })
        assertFalse(banco.hay(ETIQUETA_ESPERA))
        assertFalse(banco.hay(ETIQUETA_DESCARGAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_CANCELAR))
        assertEquals(1, cancelaciones)
    }

    @Test
    fun `esperando red con solo WiFi lo dice`() {
        val progreso = ProgresoDescarga(0, 1_000, esperandoRed = true)
        poner(EstadoDatosMapa(descarga = progreso), AjustesMapas(soloWifi = true))
        assertTrue("Esperando WiFi" in textos())
    }

    @Test
    fun `esperando red sin la restriccion de WiFi habla de conexion`() {
        val progreso = ProgresoDescarga(0, 1_000, esperandoRed = true)
        poner(EstadoDatosMapa(descarga = progreso), AjustesMapas(soloWifi = false))
        assertTrue("Esperando conexión" in textos())
    }

    @Test
    fun `descarga sin total conocido muestra progreso indeterminado`() {
        poner(EstadoDatosMapa(descarga = ProgresoDescarga(0, 0, esperandoRed = false)))
        assertTrue("Descargando…" in textos())
    }

    @Test
    fun `actualizando con datos instalados lo explica y conserva los datos`() {
        val progreso = ProgresoDescarga(10, 100, esperandoRed = false)
        poner(EstadoDatosMapa(instalados = instalados, descarga = progreso))
        assertTrue("Versión 2026.10.1" in textos())
        assertTrue(textos().any { it.startsWith("Hay una versión nueva en descarga") })
        assertFalse(banco.hay(ETIQUETA_BORRAR))
    }

    @Test
    fun `cada motivo de error tiene su mensaje`() {
        val esperados =
            mapOf(
                MotivoError.SIN_RED to "No hay conexión. Inténtalo de nuevo cuando tengas red.",
                MotivoError.SUPERA_LIMITE to "Los mapas no caben en el límite de espacio elegido. Sube el límite.",
                MotivoError.SIN_ESPACIO to "No queda espacio libre en el móvil. Libera espacio e inténtalo de nuevo.",
                MotivoError.HASH_INCORRECTO to "Un archivo descargado está dañado. Inténtalo de nuevo.",
                MotivoError.MANIFEST_INVALIDO to "No se pudo leer la lista de mapas. Inténtalo más tarde.",
                MotivoError.DESCONOCIDO to "La descarga ha fallado. Inténtalo de nuevo.",
            )
        assertEquals(MotivoError.entries.toSet(), esperados.keys)
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        esperados.forEach { (motivo, mensaje) ->
            assertEquals(mensaje, contexto.getString(textoError(motivo)))
        }
    }

    @Test
    fun `un error ofrece reintentar`() {
        var descargas = 0
        poner(
            EstadoDatosMapa(error = MotivoError.SIN_RED),
            acciones = AccionesMapasOffline(onDescargar = { descargas++ }),
        )
        val error = banco.textos(banco.conEtiqueta(ETIQUETA_ERROR))
        assertTrue("No hay conexión. Inténtalo de nuevo cuando tengas red." in error)
        assertTrue("Reintentar" in banco.textos(banco.conEtiqueta(ETIQUETA_DESCARGAR)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DESCARGAR))
        assertEquals(1, descargas)
    }

    @Test
    fun `el interruptor de solo WiFi invierte el ajuste`() {
        val cambios = mutableListOf<Boolean>()
        poner(ajustes = AjustesMapas(soloWifi = true), acciones = AccionesMapasOffline(onSoloWifi = { cambios += it }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_SOLO_WIFI))
        assertEquals(listOf(false), cambios)
    }

    @Test
    fun `el limite elegido es el de los ajustes y se puede cambiar`() {
        val elegidos = mutableListOf<Int>()
        poner(acciones = AccionesMapasOffline(onLimiteGb = { elegidos += it }))
        val ofrecidos =
            banco.etiquetas(ETIQUETA_LIMITE_PREFIJO).map { it.removePrefix(ETIQUETA_LIMITE_PREFIJO).toInt() }
        assertEquals(LIMITES_GB, ofrecidos.sorted())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_LIMITE_PREFIJO + 4))
        assertEquals(listOf(4), elegidos)
    }

    @Test
    fun `volver llama a la accion`() {
        var volver = 0
        poner(acciones = AccionesMapasOffline(onVolver = { volver++ }))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }

    @Test
    fun `el porcentaje se calcula sin pasarse de 100 ni dividir por cero`() {
        assertEquals(null, ProgresoDescarga(5, 0, false).porcentaje())
        assertEquals(50, ProgresoDescarga(500, 1_000, false).porcentaje())
        assertEquals(100, ProgresoDescarga(2_000, 1_000, false).porcentaje())
        assertEquals(0, ProgresoDescarga(-5, 1_000, false).porcentaje())
    }
}
