package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.navegacion.notas.iconoAviso
import app.trombito.rallycopilot.ui.theme.RallyOscuro
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val ID_FUENTE_CURVAS = "notas-mapa-curvas-fuente"
private const val ID_CAPA_CURVAS = "notas-mapa-curvas-capa"
private const val ID_FUENTE_AVISOS = "notas-mapa-avisos-fuente"
private const val ID_CAPA_AVISOS = "notas-mapa-avisos-capa"
internal const val PROPIEDAD_TEXTO = "texto"
internal const val PROPIEDAD_COLOR_TEXTO = "color"
internal const val PROPIEDAD_ORDEN = "orden"
private const val PREFIJO_IMAGEN_AVISO = "nota-aviso-"

/** Por debajo de este zoom las curvas y los avisos se ocultan (vista de zona o de isla: solo estorbarían). */
internal const val ZOOM_MIN_NOTAS_MAPA = 13f
private const val ZOOM_CERCA = 17
private const val TEXTO_LEJOS_SP = 14f
private const val TEXTO_CERCA_SP = 22f
private const val HALO_DP = 2.5f
private const val RELLENO_COLISION_DP = 4f
private const val TAMANO_ICONO_LEJOS = 0.45f
private const val TAMANO_ICONO_CERCA = 0.65f
private const val RELLENO_ICONO_COLISION_DP = 2f
private val FUENTE_TEXTO = arrayOf("Noto Sans Medium")

/** Halo carbón del número: lo mantiene legible sobre cualquier fondo del mapa (oscuro, satélite…). */
private const val COLOR_HALO = "#0E0F12"

/** Izquierda en azul claro y derecha en crema: la dirección se lee de un vistazo sin añadir signos. */
internal const val COLOR_CURVA_IZQUIERDA = "#7CC4FF"
internal const val COLOR_CURVA_DERECHA = "#FFE9A8"
private const val COLOR_BORDE_AVISO = 0xFFF2EDE0.toInt()

/** El texto de una curva: su grado (1–6) o «H» si es una horquilla. */
internal fun textoCurva(grado: Int?): String = grado?.toString() ?: "H"

/** Nombre de la imagen registrada en el estilo para el aviso [tipo]. */
internal fun imagenAviso(tipo: TipoAviso): String = PREFIJO_IMAGEN_AVISO + tipo.name

/**
 * El número de cada curva en su ápice (texto con halo oscuro, color según la dirección) y los avisos de la hoja
 * (estrecha, rasante, cruce, puente…) como mini insignias, de los próximos ~1,5 km. Son dos `SymbolLayer` sin solape
 * (lo que no cabe se oculta; las curvas, que van encima, mandan sobre los avisos y, entre ellas, la más cercana), solo
 * desde el zoom [ZOOM_MIN_NOTAS_MAPA], y por debajo de la flecha de posición. Al tocarlas sale su bocadillo (con el
 * coche parado, la edición usa [CapaNotas]). Las
 * fuentes siempre existen (vacías sin hoja) y cambiar los datos solo cambia su contenido.
 */
internal object CapaNotasMapa {
    fun instalar(
        estilo: Style,
        contexto: Context,
        notas: NotasEnMapa,
    ) {
        val ambar = RallyOscuro.aviso.toArgb()
        TipoAviso.entries.forEach { tipo ->
            val borde = if (tipo.esCuidado) ambar else COLOR_BORDE_AVISO
            estilo.addImage(imagenAviso(tipo), insignia(contexto, iconoAviso(tipo), borde))
        }
        estilo.addSource(GeoJsonSource(ID_FUENTE_AVISOS, featuresAvisos(notas.avisos)))
        estilo.addSource(GeoJsonSource(ID_FUENTE_CURVAS, featuresCurvas(notas.curvas)))
        val capas = estilo.layers.map { it.id }
        // Primero los avisos y luego las curvas, siempre bajo la flecha de posición (si ya está puesta).
        listOf(capaAvisos(), capaCurvas()).forEach { capa ->
            val bajoLaPosicion = capaBajoLaPosicion(capas)
            if (bajoLaPosicion != null) estilo.addLayerBelow(capa, bajoLaPosicion) else estilo.addLayer(capa)
        }
    }

    fun actualizar(
        estilo: Style,
        notas: NotasEnMapa,
    ) {
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_AVISOS)?.setGeoJson(featuresAvisos(notas.avisos))
        estilo.getSourceAs<GeoJsonSource>(ID_FUENTE_CURVAS)?.setGeoJson(featuresCurvas(notas.curvas))
    }

    /** La posición en la hoja de la curva (antes) o el aviso pintados bajo el toque en [x], [y] (píxeles). */
    fun posicionEn(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): Double? {
        val zona = zonaDeToque(x, y, densidad)
        return listOf(ID_CAPA_CURVAS, ID_CAPA_AVISOS).firstNotNullOfOrNull { capa ->
            mapa
                .queryRenderedFeatures(zona, capa)
                .firstOrNull()
                ?.getNumberProperty(PROPIEDAD_ORDEN)
                ?.toDouble()
        }
    }

    /**
     * Como [posicionEn] pero midiendo en pantalla la distancia a las [notas] (sin depender de lo que la capa tenga
     * colocado en ese instante, que con la cámara en movimiento es nada); solo si el zoom ya las muestra.
     */
    fun posicionCercana(
        mapa: MapLibreMap,
        notas: NotasEnMapa,
        x: Float,
        y: Float,
        densidad: Float,
    ): Double? =
        if (mapa.cameraPosition.zoom < ZOOM_MIN_NOTAS_MAPA) {
            null
        } else {
            val puntos =
                notas.curvas.map { Triple(it.posicionM, it.lat, it.lon) } +
                    notas.avisos.map { Triple(it.posicionM, it.lat, it.lon) }
            masCercano(puntos, x, y, MARGEN_TOQUE_DP * densidad) { (_, lat, lon) ->
                mapa.projection.toScreenLocation(LatLng(lat, lon)).let { p -> p.x to p.y }
            }?.first
        }

    private fun capaCurvas(): SymbolLayer =
        SymbolLayer(ID_CAPA_CURVAS, ID_FUENTE_CURVAS)
            .withProperties(
                PropertyFactory.textField(Expression.get(PROPIEDAD_TEXTO)),
                PropertyFactory.textFont(FUENTE_TEXTO),
                PropertyFactory.textColor(Expression.toColor(Expression.get(PROPIEDAD_COLOR_TEXTO))),
                PropertyFactory.textHaloColor(COLOR_HALO),
                PropertyFactory.textHaloWidth(HALO_DP),
                PropertyFactory.textSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(ZOOM_MIN_NOTAS_MAPA.toInt(), TEXTO_LEJOS_SP),
                        Expression.stop(ZOOM_CERCA, TEXTO_CERCA_SP),
                    ),
                ),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(false),
                PropertyFactory.textPadding(RELLENO_COLISION_DP),
                PropertyFactory.symbolSortKey(Expression.get(PROPIEDAD_ORDEN)),
            ).also { it.minZoom = ZOOM_MIN_NOTAS_MAPA }

    private fun capaAvisos(): SymbolLayer =
        SymbolLayer(ID_CAPA_AVISOS, ID_FUENTE_AVISOS)
            .withProperties(
                PropertyFactory.iconImage(Expression.get(PROPIEDAD_ICONO)),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconIgnorePlacement(false),
                PropertyFactory.iconPadding(RELLENO_ICONO_COLISION_DP),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(ZOOM_MIN_NOTAS_MAPA.toInt(), TAMANO_ICONO_LEJOS),
                        Expression.stop(ZOOM_CERCA, TAMANO_ICONO_CERCA),
                    ),
                ),
                PropertyFactory.symbolSortKey(Expression.get(PROPIEDAD_ORDEN)),
            ).also { it.minZoom = ZOOM_MIN_NOTAS_MAPA }
}

/** Un punto por curva, en su ápice, con su texto, su color y la posición en la hoja como orden de prioridad. */
internal fun featuresCurvas(curvas: List<CurvaEnMapa>): FeatureCollection =
    FeatureCollection.fromFeatures(
        curvas.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).also {
                it.addStringProperty(PROPIEDAD_TEXTO, textoCurva(c.grado))
                it.addStringProperty(
                    PROPIEDAD_COLOR_TEXTO,
                    if (c.izquierda) COLOR_CURVA_IZQUIERDA else COLOR_CURVA_DERECHA,
                )
                it.addNumberProperty(PROPIEDAD_ORDEN, c.posicionM)
            }
        },
    )

/** Un punto por aviso con el nombre de su imagen y la posición en la hoja como orden de prioridad. */
internal fun featuresAvisos(avisos: List<AvisoEnMapa>): FeatureCollection =
    FeatureCollection.fromFeatures(
        avisos.map { a ->
            Feature.fromGeometry(Point.fromLngLat(a.lon, a.lat)).also {
                it.addStringProperty(PROPIEDAD_ICONO, imagenAviso(a.tipo))
                it.addNumberProperty(PROPIEDAD_ORDEN, a.posicionM)
            }
        },
    )
