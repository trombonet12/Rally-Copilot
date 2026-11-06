package app.trombito.rallycopilot.ui.edicion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.motor.analisis.SegmentoMapa
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.analisis.ComplementoMapa
import app.trombito.rallycopilot.ui.analisis.ContenidoMapa
import app.trombito.rallycopilot.ui.analisis.MapaMapLibre
import app.trombito.rallycopilot.ui.analisis.OpcionesMapa
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Projection
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Colores (ARGB) de cada tipo de marcador: los comparten las píldoras del mapa y la leyenda en chips. */
object ColoresMarcador {
    const val NOTA = 0xFFF2EDE0.toInt()
    const val CUIDADO = 0xFFFFB300.toInt()
    const val MARCA = 0xFFD8232A.toInt()
    const val CORTE = 0xFF4FC3F7.toInt()
    const val TEXTO_OSCURO = 0xFF0E0F12.toInt()
    const val TEXTO_CLARO = 0xFFFFFFFF.toInt()

    fun fondo(tipo: TipoMarcador): Int =
        when (tipo) {
            TipoMarcador.NOTA -> NOTA
            TipoMarcador.CUIDADO -> CUIDADO
            TipoMarcador.MARCA -> MARCA
            TipoMarcador.CORTE -> CORTE
        }

    fun texto(tipo: TipoMarcador): Int = if (tipo == TipoMarcador.MARCA) TEXTO_CLARO else TEXTO_OSCURO
}

/** Centrar el mapa en [punto]; [version] distingue dos peticiones al mismo punto. */
data class CentradoMapa(
    val punto: PuntoGeo,
    val version: Int,
)

/** Lo que se dibuja en el mapa de edición. */
data class EscenaEdicion(
    val traza: List<PuntoGeo> = emptyList(),
    val marcadores: List<MarcadorEdicion> = emptyList(),
    val microsectores: List<PuntoGeo> = emptyList(),
    val seleccionId: String? = null,
    val centrado: CentradoMapa? = null,
)

/** Los gestos del mapa de edición. */
class AccionesMapaEdicion(
    val onMarcador: (MarcadorEdicion) -> Unit = {},
    val onPuntoTraza: (Double) -> Unit = {},
    val onMoverCorte: (Int, Double) -> Unit = { _, _ -> },
    val onSoltarCorte: () -> Unit = {},
)

private const val ID_FUENTE_MARCADORES = "edicion-marcadores-fuente"
private const val ID_CAPA_NOTAS = "edicion-notas"
private const val ID_CAPA_OTROS = "edicion-otros"
private const val ID_FUENTE_SELECCION = "edicion-seleccion-fuente"
private const val ID_CAPA_SELECCION = "edicion-seleccion"
private const val ID_FUENTE_MICRO = "edicion-micro-fuente"
private const val ID_CAPA_MICRO = "edicion-micro"
private const val PROPIEDAD_ICONO = "icono"
private const val PROPIEDAD_COLISION = "col"
private const val PROPIEDAD_ORDEN = "orden"
private const val RADIO_NOTA_DP = 48f
private const val RADIO_CORTE_DP = 30f
private const val RADIO_TRAZO_DP = 40f
private const val RADIO_MICRO = 4f
private const val ZOOM_AL_CENTRAR = 14.0
private const val DURACION_CENTRADO_MS = 450
private const val ORDEN_CUIDADO = 2f
private const val ORDEN_NOTA = 3f

/**
 * Mapa de edición de un tramo: el estilo offline del análisis con la traza, las notas, marcas y cortes de sector
 * como píldoras con su texto corto y color por tipo, y la nota elegida más grande y con aro blanco. Se mueve y se
 * hace zoom con los gestos del mapa; un toque elige la nota más cercana (o, sobre el trazo, pide añadir una) y un
 * corte de sector se arrastra a lo largo de la traza.
 */
@Composable
fun MapaEdicionTramo(
    estiloJson: String,
    escena: EscenaEdicion,
    acciones: AccionesMapaEdicion,
    modifier: Modifier = Modifier,
) {
    val densidad = LocalDensity.current.density
    val complemento = remember { ComplementoEdicion(densidad) }
    SideEffect {
        complemento.acciones = acciones
        complemento.actualizar(escena)
    }
    val contenido =
        remember(escena.traza) {
            ContenidoMapa(listOf(SegmentoMapa(escena.traza, 0.0, 0.0, 0.0)), EscalaVelocidad.de(listOf(0.0)), null)
        }
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val opciones =
        remember(color) { OpcionesMapa(colorUnico = color, descripcion = R.string.edicion_mapa_descripcion) }
    MapaMapLibre(estiloJson, contenido, opciones, modifier, complemento)
}

/** Capas y gestos propios de la edición, enganchados al mapa del análisis. */
internal class ComplementoEdicion(
    private val densidad: Float,
) : ComplementoMapa {
    var acciones = AccionesMapaEdicion()

    private var mapa: MapLibreMap? = null
    private var estilo: Style? = null
    private val registradas = mutableSetOf<String>()
    private var escena = EscenaEdicion()
    private var traza = TrazaMetros(emptyList())
    private var arrastrando: Int? = null
    private var centradoAplicado: CentradoMapa? = null

    override fun alIniciar(
        mapa: MapLibreMap,
        vista: MapView,
    ) {
        this.mapa = mapa
        mapa.addOnMapClickListener { alToque(it) }
        vista.setOnTouchListener { _, evento -> alTocar(evento) }
    }

    override fun alCargarEstilo(estilo: Style) {
        this.estilo = estilo
        registradas.clear()
        instalarCapas(estilo)
        pintar(estilo)
        centrar()
    }

    fun actualizar(nueva: EscenaEdicion) {
        if (nueva == escena) return
        if (nueva.traza != escena.traza) traza = TrazaMetros(nueva.traza)
        escena = nueva
        estilo?.let(::pintar)
        centrar()
    }

    private fun instalarCapas(estilo: Style) {
        estilo.addSource(GeoJsonSource(ID_FUENTE_MICRO, vacio()))
        estilo.addSource(GeoJsonSource(ID_FUENTE_MARCADORES, vacio()))
        estilo.addSource(GeoJsonSource(ID_FUENTE_SELECCION, vacio()))
        estilo.addLayer(
            CircleLayer(ID_CAPA_MICRO, ID_FUENTE_MICRO).withProperties(
                PropertyFactory.circleRadius(RADIO_MICRO),
                PropertyFactory.circleColor(ColoresMarcador.CORTE),
                PropertyFactory.circleStrokeColor(ColoresMarcador.TEXTO_OSCURO),
                PropertyFactory.circleStrokeWidth(1.5f),
            ),
        )
        // Las notas ceden el sitio unas a otras (al alejar el zoom no se amontonan); marcas y cortes siempre se ven.
        estilo.addLayer(capaDePildoras(ID_CAPA_NOTAS, ID_FUENTE_MARCADORES, solapar = false, colision = true))
        estilo.addLayer(capaDePildoras(ID_CAPA_OTROS, ID_FUENTE_MARCADORES, solapar = true, colision = false))
        estilo.addLayer(
            SymbolLayer(ID_CAPA_SELECCION, ID_FUENTE_SELECCION).withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_ICONO)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    private fun pintar(estilo: Style) {
        val propias =
            escena.marcadores.map { m ->
                Feature
                    .fromGeometry(Point.fromLngLat(m.punto.lon, m.punto.lat))
                    .also {
                        it.addStringProperty(PROPIEDAD_ICONO, icono(estilo, m, seleccionado = false))
                        it.addBooleanProperty(
                            PROPIEDAD_COLISION,
                            m.tipo == TipoMarcador.NOTA || m.tipo == TipoMarcador.CUIDADO,
                        )
                        it.addNumberProperty(
                            PROPIEDAD_ORDEN,
                            if (m.tipo == TipoMarcador.CUIDADO) ORDEN_CUIDADO else ORDEN_NOTA,
                        )
                    }
            }
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_MARCADORES)?.setGeoJson(FeatureCollection.fromFeatures(propias))
        val elegido = escena.marcadores.firstOrNull { it.id == escena.seleccionId }
        val seleccion =
            listOfNotNull(
                elegido?.let { m ->
                    Feature.fromGeometry(Point.fromLngLat(m.punto.lon, m.punto.lat)).also {
                        it.addStringProperty(PROPIEDAD_ICONO, icono(estilo, m, seleccionado = true))
                    }
                },
            )
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_SELECCION)?.setGeoJson(FeatureCollection.fromFeatures(seleccion))
        val micros =
            escena.microsectores.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_MICRO)?.setGeoJson(FeatureCollection.fromFeatures(micros))
    }

    /** Nombre de la imagen de la píldora de [m]; la dibuja y la registra en el estilo la primera vez. */
    private fun icono(
        estilo: Style,
        m: MarcadorEdicion,
        seleccionado: Boolean,
    ): String {
        val id = "${m.tipo}|${m.texto}|${if (seleccionado) 1 else 0}"
        if (registradas.add(id)) estilo.addImage(id, dibujarPildora(m.texto, m.tipo, seleccionado, densidad))
        return id
    }

    private fun centrar() {
        val pedido = escena.centrado?.takeIf { it != centradoAplicado } ?: return
        val m = mapa
        if (m == null || estilo == null) return
        centradoAplicado = pedido
        val zoom = maxOf(m.cameraPosition.zoom, ZOOM_AL_CENTRAR)
        m.easeCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(pedido.punto.lat, pedido.punto.lon), zoom),
            DURACION_CENTRADO_MS,
        )
    }

    private fun alToque(posicion: LatLng): Boolean {
        val m = mapa ?: return false
        val toque = m.projection.toScreenLocation(posicion).let { Offset(it.x, it.y) }
        val notas =
            escena.marcadores
                .filter { it.tipo == TipoMarcador.NOTA || it.tipo == TipoMarcador.CUIDADO }
                .map { it to pantallaDe(it, m.projection) }
        val cercana = marcadorCercano(notas, toque, RADIO_NOTA_DP * densidad)
        if (cercana != null) acciones.onMarcador(cercana) else tocarTrazo(m, posicion)
        return true
    }

    /** Un toque sin nota cerca pide añadir una si cae a menos de [RADIO_TRAZO_DP] del trazo. */
    private fun tocarTrazo(
        m: MapLibreMap,
        posicion: LatLng,
    ) {
        val proyeccion = traza.proyectar(PuntoGeo(posicion.latitude, posicion.longitude))
        val metrosPorPixel = m.projection.getMetersPerPixelAtLatitude(posicion.latitude)
        if (proyeccion != null && metrosPorPixel > 0.0 &&
            proyeccion.distanciaM / metrosPorPixel <= RADIO_TRAZO_DP * densidad
        ) {
            acciones.onPuntoTraza(proyeccion.metros)
        }
    }

    /** Un corte se arrastra si el dedo cae sobre su píldora; si no, el gesto es del mapa (mover, zoom). */
    private fun alTocar(evento: MotionEvent): Boolean {
        val m = mapa ?: return false
        return when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cortes =
                    escena.marcadores.filter { it.tipo == TipoMarcador.CORTE }.map {
                        it to
                            pantallaDe(it, m.projection)
                    }
                val corte = marcadorCercano(cortes, Offset(evento.x, evento.y), RADIO_CORTE_DP * densidad)
                arrastrando = corte?.id?.removePrefix("c-")?.toIntOrNull()
                arrastrando != null
            }

            MotionEvent.ACTION_MOVE -> {
                val indice = arrastrando
                if (indice == null) {
                    false
                } else {
                    val posicion = m.projection.fromScreenLocation(PointF(evento.x, evento.y))
                    traza
                        .proyectar(PuntoGeo(posicion.latitude, posicion.longitude))
                        ?.let { acciones.onMoverCorte(indice, it.metros) }
                    true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val arrastrado = arrastrando != null
                if (arrastrado) acciones.onSoltarCorte()
                arrastrando = null
                arrastrado
            }

            else -> {
                arrastrando != null
            }
        }
    }
}

private fun capaDePildoras(
    id: String,
    fuente: String,
    solapar: Boolean,
    colision: Boolean,
): SymbolLayer =
    SymbolLayer(id, fuente)
        .withFilter(Expression.eq(Expression.get(PROPIEDAD_COLISION), Expression.literal(colision)))
        .withProperties(
            PropertyFactory.iconImage(Expression.get(PROPIEDAD_ICONO)),
            PropertyFactory.iconAllowOverlap(solapar),
            PropertyFactory.iconIgnorePlacement(solapar),
            PropertyFactory.iconPadding(2f),
            PropertyFactory.symbolSortKey(Expression.get(PROPIEDAD_ORDEN)),
        )

private fun pantallaDe(
    m: MarcadorEdicion,
    proyeccion: Projection,
): Offset = proyeccion.toScreenLocation(LatLng(m.punto.lat, m.punto.lon)).let { Offset(it.x, it.y) }

private fun vacio(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

private const val ALTO_PILDORA_DP = 24f
private const val ALTO_PILDORA_ELEGIDA_DP = 32f
private const val TEXTO_PILDORA_DP = 13f
private const val TEXTO_PILDORA_ELEGIDA_DP = 17f
private const val RELLENO_PILDORA_DP = 8f
private const val BORDE_PILDORA_DP = 2f
private const val AURO_ELEGIDA_DP = 3f

/** Píldora redondeada con [texto] en el color de [tipo]; la [elegida] es mayor y lleva un aro blanco. */
internal fun dibujarPildora(
    texto: String,
    tipo: TipoMarcador,
    elegida: Boolean,
    densidad: Float,
): Bitmap {
    val alto = (if (elegida) ALTO_PILDORA_ELEGIDA_DP else ALTO_PILDORA_DP) * densidad
    val letra =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (if (elegida) TEXTO_PILDORA_ELEGIDA_DP else TEXTO_PILDORA_DP) * densidad
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = ColoresMarcador.texto(tipo)
            textAlign = Paint.Align.CENTER
        }
    val margen = (if (elegida) AURO_ELEGIDA_DP else BORDE_PILDORA_DP) * densidad
    val ancho = maxOf(alto, letra.measureText(texto) + 2 * RELLENO_PILDORA_DP * densidad) + 2 * margen
    val imagen = Bitmap.createBitmap(ancho.toInt(), (alto + 2 * margen).toInt(), Bitmap.Config.ARGB_8888)
    imagen.density = (densidad * android.util.DisplayMetrics.DENSITY_DEFAULT).toInt()
    val lienzo = Canvas(imagen)
    val caja = RectF(margen, margen, imagen.width - margen, imagen.height - margen)
    val radio = alto / 2f
    val borde =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = margen
            color = if (elegida) ColoresMarcador.TEXTO_CLARO else ColoresMarcador.TEXTO_OSCURO
        }
    val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColoresMarcador.fondo(tipo) }
    lienzo.drawRoundRect(caja, radio, radio, relleno)
    lienzo.drawRoundRect(caja, radio, radio, borde)
    val centroY = caja.centerY() - (letra.descent() + letra.ascent()) / 2f
    lienzo.drawText(texto, caja.centerX(), centroY, letra)
    return imagen
}
