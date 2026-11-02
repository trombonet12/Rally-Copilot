package app.trombito.rallycopilot.ui.analisis

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.motor.analisis.SegmentoMapa
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.mapa.PuenteCicloVida
import app.trombito.rallycopilot.ui.mapa.limitesDe
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val LAT_INICIAL = 39.62
private const val LON_INICIAL = 2.98
private const val ZOOM_INICIAL = 9.0

private const val ID_FUENTE_TRAZA = "analisis-traza-fuente"
private const val ID_BORDE = "analisis-traza-borde"
private const val ID_LINEA = "analisis-traza-linea"
private const val ID_FUENTE_EXTREMOS = "analisis-extremos-fuente"
private const val ID_EXTREMOS_HALO = "analisis-extremos-halo"
private const val ID_EXTREMOS = "analisis-extremos"
private const val ID_FUENTE_MARCADOR = "analisis-marcador-fuente"
private const val ID_MARCADOR_HALO = "analisis-marcador-halo"
private const val ID_MARCADOR = "analisis-marcador"
private const val PROPIEDAD_FRACCION = "f"
private const val PROPIEDAD_TIPO = "tipo"
private const val TIPO_SALIDA = "salida"

private const val EXPONENTE_ANCHO = 1.5f
private const val ZOOM_MINIMO = 8
private const val ZOOM_MEDIO = 14
private const val ZOOM_MAXIMO = 18
private const val ANCHO_MINIMO = 4f
private const val ANCHO_MEDIO = 7f
private const val ANCHO_MAXIMO = 13f
private const val EXTRA_BORDE = 4f
private const val RADIO_EXTREMO = 7f
private const val RADIO_HALO_EXTREMO = 11f
private const val RADIO_MARCADOR = 8f
private const val RADIO_HALO_MARCADOR = 13f
private const val GROSOR_BORDE = 2.5f
private const val DURACION_ENCUADRE_MS = 600
private const val ENCUADRE_ARRIBA_DP = 72f
private const val ENCUADRE_ABAJO_DP = 150f
private const val ENCUADRE_LATERAL_DP = 36f
private const val ENCUADRE_MAXIMO_ARRIBA = 0.22f
private const val ENCUADRE_MAXIMO_ABAJO = 0.4f

private val CARBON = Color.parseColor("#0E0F12")
private val LATON = Color.parseColor("#E5AB46")
private val VERDE_SALIDA = Color.parseColor("#3DDC84")
private val ROJO_META = Color.parseColor("#D8232A")

/**
 * Mapa MapLibre del análisis (el estilo vectorial instalado, sin seguimiento ni ubicación): la traza del tramo
 * coloreada por velocidad con el degradado de [PaletaVelocidad] según la [escala], la salida en verde, la meta en
 * rojo y el marcador de la repetición en [marcador] (null = sin marcador). La cámara encuadra la traza entera.
 */
@Composable
fun MapaAnalisis(
    estiloJson: String,
    segmentos: List<SegmentoMapa>,
    escala: EscalaVelocidad,
    marcador: PuntoGeo?,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista =
        remember {
            MapLibre.getInstance(contexto)
            MapLibre.setConnected(false)
            MapView(contexto)
        }
    val controlador = remember(vista) { ControladorMapaAnalisis(vista) }
    DisposableEffect(vista, ciclo) {
        val puente = PuenteCicloVida(vista) { }
        ciclo.lifecycle.addObserver(puente)
        controlador.iniciar()
        onDispose {
            ciclo.lifecycle.removeObserver(puente)
            puente.cerrar()
        }
    }
    LaunchedEffect(controlador, estiloJson) { controlador.cambiarEstilo(estiloJson) }
    LaunchedEffect(controlador, segmentos, escala) { controlador.traza(segmentos, escala) }
    LaunchedEffect(controlador, marcador) { controlador.marcador(marcador) }
    val descripcion = stringResource(R.string.analisis_mapa_descripcion)
    AndroidView(factory = { vista }, modifier = modifier.semantics { contentDescription = descripcion })
}

/** Estado imperativo del mapa del análisis: estilo, capas de la traza y del marcador y encuadre. */
private class ControladorMapaAnalisis(
    private val vista: MapView,
) {
    private var mapa: MapLibreMap? = null
    private var estilo: Style? = null
    private var estiloPedido: String? = null
    private var estiloAplicado: String? = null
    private var segmentos: List<SegmentoMapa> = emptyList()
    private var escala: EscalaVelocidad? = null
    private var marcador: PuntoGeo? = null
    private var encuadrePendiente = false

    fun iniciar() {
        vista.getMapAsync { m ->
            mapa = m
            m.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
                isTiltGesturesEnabled = false
            }
            m.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition
                        .Builder()
                        .target(LatLng(LAT_INICIAL, LON_INICIAL))
                        .zoom(ZOOM_INICIAL)
                        .build(),
                ),
            )
            // El mapa se mide después de crearse: el encuadre pendiente se hace en cuanto tenga tamaño.
            vista.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> encuadrarSiToca() }
            estiloPedido?.let(::aplicarEstilo)
        }
    }

    fun cambiarEstilo(json: String) {
        estiloPedido = json
        if (mapa != null) aplicarEstilo(json)
    }

    fun traza(
        nuevos: List<SegmentoMapa>,
        nuevaEscala: EscalaVelocidad,
    ) {
        if (nuevos == segmentos && nuevaEscala == escala) return
        segmentos = nuevos
        escala = nuevaEscala
        estilo?.let(::pintarTraza)
        encuadrePendiente = true
        encuadrarSiToca()
    }

    fun marcador(nuevo: PuntoGeo?) {
        marcador = nuevo
        estilo?.getSourceAs<GeoJsonSource>(ID_FUENTE_MARCADOR)?.setGeoJson(puntoGeoJson(nuevo))
    }

    private fun aplicarEstilo(json: String) {
        val m = mapa ?: return
        if (json == estiloAplicado) return
        estiloAplicado = json
        estilo = null
        m.setStyle(Style.Builder().fromJson(json)) { cargado ->
            instalarCapas(cargado)
            estilo = cargado
            pintarTraza(cargado)
            marcador(marcador)
            encuadrePendiente = segmentos.isNotEmpty()
            encuadrarSiToca()
        }
    }

    private fun instalarCapas(cargado: Style) {
        cargado.addSource(GeoJsonSource(ID_FUENTE_TRAZA, vacio()))
        cargado.addSource(GeoJsonSource(ID_FUENTE_EXTREMOS, vacio()))
        cargado.addSource(GeoJsonSource(ID_FUENTE_MARCADOR, vacio()))
        // Debajo de las etiquetas del estilo: la traza se ve sobre las vías sin tapar los nombres.
        val etiquetas = cargado.layers.firstOrNull { it is SymbolLayer }?.id
        val capas =
            listOf(
                linea(ID_BORDE, EXTRA_BORDE).withProperties(PropertyFactory.lineColor(CARBON)),
                linea(ID_LINEA, 0f).withProperties(PropertyFactory.lineColor(colorPorVelocidad())),
                circulo(ID_EXTREMOS_HALO, ID_FUENTE_EXTREMOS, RADIO_HALO_EXTREMO, Expression.color(Color.WHITE)),
                circulo(ID_EXTREMOS, ID_FUENTE_EXTREMOS, RADIO_EXTREMO, colorExtremo()),
            )
        capas.forEach { if (etiquetas != null) cargado.addLayerBelow(it, etiquetas) else cargado.addLayer(it) }
        cargado.addLayer(
            circulo(ID_MARCADOR_HALO, ID_FUENTE_MARCADOR, RADIO_HALO_MARCADOR, Expression.color(Color.WHITE)),
        )
        cargado.addLayer(
            circulo(ID_MARCADOR, ID_FUENTE_MARCADOR, RADIO_MARCADOR, Expression.color(LATON)).withProperties(
                PropertyFactory.circleStrokeColor(CARBON),
                PropertyFactory.circleStrokeWidth(GROSOR_BORDE),
            ),
        )
    }

    private fun pintarTraza(cargado: Style) {
        val actual = escala ?: return
        val lineas =
            segmentos.filter { it.puntos.size >= 2 }.map { s ->
                Feature
                    .fromGeometry(LineString.fromLngLats(s.puntos.map { Point.fromLngLat(it.lon, it.lat) }))
                    .also { it.addNumberProperty(PROPIEDAD_FRACCION, actual.fraccion(s.velocidadKmh)) }
            }
        cargado.getSourceAs<GeoJsonSource>(ID_FUENTE_TRAZA)?.setGeoJson(FeatureCollection.fromFeatures(lineas))
        val extremos =
            listOfNotNull(
                segmentos
                    .firstOrNull()
                    ?.puntos
                    ?.firstOrNull()
                    ?.let { extremo(it, TIPO_SALIDA) },
                segmentos
                    .lastOrNull()
                    ?.puntos
                    ?.lastOrNull()
                    ?.let { extremo(it, "meta") },
            )
        cargado.getSourceAs<GeoJsonSource>(ID_FUENTE_EXTREMOS)?.setGeoJson(FeatureCollection.fromFeatures(extremos))
    }

    private fun mapaMedido(): Boolean = estilo != null && vista.width > 0 && vista.height > 0

    private fun encuadrarSiToca() {
        val m = mapa
        if (m == null || !encuadrePendiente || !mapaMedido()) return
        val limites = limitesDe(segmentos.flatMap { it.puntos }) ?: return
        encuadrePendiente = false
        val densidad = vista.resources.displayMetrics.density
        val alto = vista.height.toFloat()
        val arriba = minOf(ENCUADRE_ARRIBA_DP * densidad, alto * ENCUADRE_MAXIMO_ARRIBA).toInt()
        val abajo = minOf(ENCUADRE_ABAJO_DP * densidad, alto * ENCUADRE_MAXIMO_ABAJO).toInt()
        val lado = (ENCUADRE_LATERAL_DP * densidad).toInt()
        m.easeCamera(
            CameraUpdateFactory.newLatLngBounds(limites, lado, arriba, lado, abajo),
            DURACION_ENCUADRE_MS,
        )
    }
}

private fun vacio(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

private fun puntoGeoJson(punto: PuntoGeo?): FeatureCollection =
    if (punto == null) {
        vacio()
    } else {
        FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(punto.lon, punto.lat)))
    }

private fun extremo(
    punto: PuntoGeo,
    tipo: String,
): Feature =
    Feature.fromGeometry(Point.fromLngLat(punto.lon, punto.lat)).also { it.addStringProperty(PROPIEDAD_TIPO, tipo) }

/** Verde en la salida y rojo en la meta. */
private fun colorExtremo(): Expression =
    Expression.match(
        Expression.get(PROPIEDAD_TIPO),
        Expression.color(ROJO_META),
        Expression.stop(TIPO_SALIDA, Expression.color(VERDE_SALIDA)),
    )

/** Color de la traza según la propiedad `f` (0..1) de cada segmento, con las mismas paradas que la leyenda. */
@Suppress("SpreadOperator") // una sola vez al instalar las capas, con cuatro paradas
private fun colorPorVelocidad(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.get(PROPIEDAD_FRACCION),
        *PaletaVelocidad.paradas
            .map { (posicion, _) -> Expression.stop(posicion, Expression.color(PaletaVelocidad.argb(posicion))) }
            .toTypedArray(),
    )

private fun linea(
    id: String,
    extra: Float,
): LineLayer =
    LineLayer(id, ID_FUENTE_TRAZA).withProperties(
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineWidth(
            Expression.interpolate(
                Expression.exponential(EXPONENTE_ANCHO),
                Expression.zoom(),
                Expression.stop(ZOOM_MINIMO, ANCHO_MINIMO + extra),
                Expression.stop(ZOOM_MEDIO, ANCHO_MEDIO + extra),
                Expression.stop(ZOOM_MAXIMO, ANCHO_MAXIMO + extra),
            ),
        ),
    )

private fun circulo(
    id: String,
    fuente: String,
    radio: Float,
    color: Expression,
): CircleLayer =
    CircleLayer(id, fuente).withProperties(
        PropertyFactory.circleColor(color),
        PropertyFactory.circleRadius(radio),
    )
