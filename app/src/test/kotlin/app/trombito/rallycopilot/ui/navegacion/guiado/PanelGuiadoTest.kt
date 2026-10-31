package app.trombito.rallycopilot.ui.navegacion.guiado

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER_DISTANCIA
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER_FRASE
import app.trombito.rallycopilot.ui.maniobras.ETIQUETA_BANNER_SIGUIENTE
import app.trombito.rallycopilot.ui.maniobras.VarianteManiobra
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PanelGuiadoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val destino = Destino("Cala Figuera", PuntoGeo(39.3, 3.1))

    private fun maniobra(
        tipo: TipoManiobra,
        longitudM: Double,
        vias: List<String> = emptyList(),
        salidaRotonda: Int? = null,
    ) = Maniobra(tipo, "instrucción", vias, 0, 1, longitudM, 30.0, salidaRotonda)

    private val salida = maniobra(TipoManiobra.SALIDA, 120.0)
    private val izquierda = maniobra(TipoManiobra.IZQUIERDA, 800.0, vias = listOf("Ma-10"))
    private val recto = maniobra(TipoManiobra.RECTO, 500.0)
    private val ruta =
        Ruta(
            puntos = listOf(PuntoGeo(39.0, 3.0), PuntoGeo(39.1, 3.1)),
            maniobras = listOf(salida, izquierda, recto),
            longitudM = 1420.0,
            tiempoS = 120.0,
        )

    private fun progreso(
        actual: Maniobra? = izquierda,
        siguiente: Maniobra? = recto,
    ) = EstadoRuta(
        progresoM = 100.0,
        distanciaRestanteM = 12_400.0,
        tiempoRestanteS = 3900.0,
        llegadaMs = 1_700_000_000_000L,
        maniobraActual = actual,
        distanciaAManiobraM = 352.0,
        maniobraSiguiente = siguiente,
        distanciaALaRutaM = 3.0,
        llegado = false,
    )

    private fun estado(
        progreso: EstadoRuta?,
        recalculando: Boolean = false,
    ) = EstadoNavegacion.Guiando(destino, PerfilRuta.MAS_RAPIDA, ruta, progreso, recalculando)

    private fun poner(
        estado: EstadoNavegacion.Guiando,
        variante: VarianteManiobra = VarianteManiobra.CHEVRON_HUD,
    ) = banco.poner {
        RallyCopilotTheme { PanelGuiado(estado, variante, Modifier.fillMaxSize(), ZoneId.of("UTC")) }
    }

    private fun textosDe(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta))

    @Test
    fun `muestra distancia y frase de la maniobra actual`() {
        poner(estado(progreso()))
        assertEquals(listOf("350 m"), textosDe(ETIQUETA_BANNER_DISTANCIA))
        assertEquals(listOf("cruce, izquierda, Ma-10"), textosDe(ETIQUETA_BANNER_FRASE))
    }

    @Test
    fun `muestra la maniobra siguiente`() {
        poner(estado(progreso()))
        assertEquals(listOf("Luego", "recto"), textosDe(ETIQUETA_BANNER_SIGUIENTE))
    }

    @Test
    fun `sin siguiente no hay fila de luego`() {
        poner(estado(progreso(siguiente = null)))
        assertFalse(banco.hay(ETIQUETA_BANNER_SIGUIENTE))
    }

    @Test
    fun `el pie muestra llegada, tiempo y kilometros formateados`() {
        poner(estado(progreso()))
        assertEquals(listOf("22:13"), textosDe(ETIQUETA_PIE_HORA))
        assertEquals(listOf("1 h 05 min"), textosDe(ETIQUETA_PIE_TIEMPO))
        assertEquals(listOf("12,4 km"), textosDe(ETIQUETA_PIE_DISTANCIA))
        assertEquals(
            "Llegada a las 22:13. Quedan 1 h 05 min y 12,4 km.",
            banco.descripcion(banco.conEtiqueta(ETIQUETA_PIE_GUIADO)),
        )
    }

    @Test
    fun `sin progreso el banner enseña la primera maniobra y su longitud, y no hay pie`() {
        poner(estado(null))
        assertEquals(listOf("120 m"), textosDe(ETIQUETA_BANNER_DISTANCIA))
        assertEquals(listOf("en marcha"), textosDe(ETIQUETA_BANNER_FRASE))
        assertEquals(listOf("Luego", "cruce, izquierda, Ma-10"), textosDe(ETIQUETA_BANNER_SIGUIENTE))
        assertFalse(banco.hay(ETIQUETA_PIE_GUIADO))
    }

    @Test
    fun `sin maniobras por delante enseña el destino a la distancia restante`() {
        poner(estado(progreso(actual = null, siguiente = null)))
        assertEquals(listOf("destino"), textosDe(ETIQUETA_BANNER_FRASE))
        assertEquals(listOf("12,4 km"), textosDe(ETIQUETA_BANNER_DISTANCIA))
    }

    @Test
    fun `la rotonda lleva el numero de salida`() {
        val rotonda = maniobra(TipoManiobra.ROTONDA_ENTRADA, 300.0, salidaRotonda = 2)
        poner(estado(progreso(actual = rotonda, siguiente = null)))
        assertEquals(listOf("rotonda, segunda salida"), textosDe(ETIQUETA_BANNER_FRASE))
        assertTrue("2" in banco.textos(banco.conEtiqueta(ETIQUETA_BANNER)))
    }

    @Test
    fun `el banner se describe en español para TalkBack`() {
        poner(estado(progreso()))
        val descripciones = banco.nodos().mapNotNull { banco.descripcion(it) }
        assertTrue(descripciones.toString(), "cruce, izquierda, Ma-10, dentro de 350 m" in descripciones)
    }

    @Test
    fun `sin recalcular no hay aviso`() {
        poner(estado(progreso(), recalculando = false))
        assertFalse(banco.hay(ETIQUETA_AVISO_RECALCULO))
    }

    @Test
    fun `el aviso de recalculo aparece al recalcular`() {
        poner(estado(progreso(), recalculando = true))
        assertTrue(banco.hay(ETIQUETA_AVISO_RECALCULO))
        assertEquals(listOf("Recalculando"), textosDe(ETIQUETA_AVISO_RECALCULO))
    }

    @Test
    fun `la tarjeta de llegada muestra destino y se cierra`() {
        var cerrada = false
        banco.poner { RallyCopilotTheme { TarjetaLlegada(destino, onCerrar = { cerrada = true }) } }
        assertEquals(listOf("Has llegado"), textosDe(ETIQUETA_LLEGADA_TITULO))
        assertEquals(listOf("Cala Figuera"), textosDe(ETIQUETA_LLEGADA_DESTINO))
        val boton = banco.conEtiqueta(ETIQUETA_LLEGADA_CERRAR)
        assertEquals("Cerrar", banco.descripcion(boton))
        banco.pulsar(boton)
        assertTrue(cerrada)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun `en horizontal el banner queda a la izquierda y el pie abajo sin tapar el centro`() {
        poner(estado(progreso()))
        val banner = banco.conEtiqueta(ETIQUETA_BANNER).boundsInRoot
        val pie = banco.conEtiqueta(ETIQUETA_PIE_GUIADO).boundsInRoot
        val raiz = banco.raiz().boundsInRoot
        val centroX = raiz.width / 2
        assertTrue("banner a la izquierda: $banner", banner.right < centroX)
        assertTrue("banner arriba: $banner", banner.top < raiz.height / 4)
        assertTrue("pie abajo: $pie", pie.bottom > raiz.height * 3 / 4)
        assertTrue("pie sin tapar el centro: $pie", pie.right < centroX)
    }

    @Test
    fun `en vertical el banner ocupa el ancho y el pie va centrado abajo`() {
        poner(estado(progreso()))
        val banner = banco.conEtiqueta(ETIQUETA_BANNER).boundsInRoot
        val pie = banco.conEtiqueta(ETIQUETA_PIE_GUIADO).boundsInRoot
        val raiz = banco.raiz().boundsInRoot
        assertTrue("banner $banner raiz $raiz", banner.width > raiz.width * 0.8f)
        assertTrue(pie.bottom > raiz.height * 3 / 4)
        assertEquals(raiz.center.x, pie.center.x, 2f)
    }

    @Test
    fun `los textos del guiado cumplen contraste AA sobre carbon`() {
        for (texto in listOf(ColoresGuiado.Laton, ColoresGuiado.Texto)) {
            val c = contraste(texto, ColoresGuiado.Carbon)
            assertTrue("${texto.value}: $c", c >= 4.5)
        }
    }

    private fun contraste(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
