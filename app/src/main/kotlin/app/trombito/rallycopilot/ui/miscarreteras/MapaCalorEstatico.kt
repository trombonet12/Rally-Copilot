package app.trombito.rallycopilot.ui.miscarreteras

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.analisis.ComplementoMapa
import app.trombito.rallycopilot.ui.analisis.ContenidoMapa
import app.trombito.rallycopilot.ui.analisis.MapaMapLibre
import app.trombito.rallycopilot.ui.analisis.OpcionesMapa
import app.trombito.rallycopilot.ui.mapa.CALOR_GEOJSON_VACIO
import app.trombito.rallycopilot.ui.mapa.CapaCalor
import app.trombito.rallycopilot.ui.mapa.DatosCalor
import app.trombito.rallycopilot.ui.mapa.limitesDe
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * El mapa de calor de las carreteras: recibe el estilo offline, el GeoJSON ya construido (ver `calorGeoJsonTexto`)
 * y las dos esquinas que encuadran lo conducido. Se inyecta en la pantalla para poder sustituirlo en los tests,
 * como el mapa del resumen de un viaje.
 */
typealias MapaDelCalor =
    @Composable (estiloJson: String, geoJson: String, esquinas: List<PuntoGeo>, modifier: Modifier) -> Unit

/** El mapa real: MapLibre sobre el estilo instalado con la capa de calor de «Mis carreteras». */
val MapaCalorReal: MapaDelCalor = { estiloJson, geoJson, esquinas, modificador ->
    MapaCalorEstatico(estiloJson, geoJson, esquinas, R.string.carreteras_mapa_descripcion, modificador)
}

private const val MARGEN_ENCUADRE_DP = 28f

/**
 * Mapa estático (sin gestos: va dentro de una lista que se desplaza y no debe robarle el arrastre) con la capa de calor
 * de [CapaCalor] sobre el mismo estilo offline del análisis. La cámara encuadra las [esquinas] una vez medido el mapa y
 * otra vez cuando cambian; un cambio de [geoJson] (otro modo) solo repinta.
 */
@Composable
fun MapaCalorEstatico(
    estiloJson: String,
    geoJson: String,
    esquinas: List<PuntoGeo>,
    @StringRes descripcion: Int,
    modifier: Modifier = Modifier,
) {
    val complemento = remember { ComplementoCalor() }
    SideEffect { complemento.actualizar(geoJson, esquinas) }
    val contenido = remember { ContenidoMapa(emptyList(), EscalaVelocidad.de(listOf(0.0)), null) }
    val opciones = remember(descripcion) { OpcionesMapa(interactivo = false, descripcion = descripcion) }
    MapaMapLibre(estiloJson, contenido, opciones, modifier, complemento)
}

/** Instala la capa de calor en el mapa del análisis (sin su traza) y encuadra lo conducido. */
internal class ComplementoCalor : ComplementoMapa {
    private val capa = CapaCalor()
    private var mapa: MapLibreMap? = null
    private var vista: MapView? = null
    private var estilo: Style? = null
    private var geoJson = CALOR_GEOJSON_VACIO
    private var esquinas: List<PuntoGeo> = emptyList()
    private var encuadrePendiente = false

    override fun alIniciar(
        mapa: MapLibreMap,
        vista: MapView,
    ) {
        this.mapa = mapa
        this.vista = vista
        // El mapa se mide después de crearse: el encuadre pendiente se hace en cuanto tenga tamaño.
        vista.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> encuadrarSiToca() }
    }

    override fun alCargarEstilo(estilo: Style) {
        this.estilo = estilo
        capa.instalar(estilo, DatosCalor(geoJson))
        encuadrePendiente = esquinas.size >= 2
        encuadrarSiToca()
    }

    fun actualizar(
        nuevoGeoJson: String,
        nuevasEsquinas: List<PuntoGeo>,
    ) {
        if (nuevasEsquinas != esquinas) {
            esquinas = nuevasEsquinas
            encuadrePendiente = nuevasEsquinas.size >= 2
        }
        if (nuevoGeoJson != geoJson) {
            geoJson = nuevoGeoJson
            estilo?.let { capa.actualizar(it, DatosCalor(nuevoGeoJson)) }
        }
        encuadrarSiToca()
    }

    private fun listoParaEncuadrar() = estilo != null && encuadrePendiente && vista?.sinTamano() == false

    private fun encuadrarSiToca() {
        val m = mapa
        val v = vista
        val limites = if (listoParaEncuadrar()) limitesDe(esquinas) else null
        if (m != null && v != null && limites != null) {
            encuadrePendiente = false
            val margen = (MARGEN_ENCUADRE_DP * v.resources.displayMetrics.density).toInt()
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, margen, margen, margen, margen))
        }
    }
}

private fun MapView.sinTamano() = width <= 0 || height <= 0
