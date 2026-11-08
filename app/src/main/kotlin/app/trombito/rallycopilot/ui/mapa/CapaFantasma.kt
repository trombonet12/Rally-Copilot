package app.trombito.rallycopilot.ui.mapa

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.PosicionFantasma
import app.trombito.rallycopilot.ui.comun.recurso
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val ID_FUENTE = "fantasma-fuente"
private const val ID_CAPA = "fantasma-capa"
private const val ID_IMAGEN = "fantasma-icono"
internal const val PROPIEDAD_RUMBO_FANTASMA = "rumbo"
private const val LADO_ICONO_DP = 40

/** Opacidad del coche fantasma: translúcido para que se lea el mapa por debajo y no se confunda con el coche real. */
internal const val OPACIDAD_FANTASMA = 0.45f

/** Gris casi blanco de la silueta del fantasma (sin opacidad; la capa lo pinta al [OPACIDAD_FANTASMA]). */
private const val COLOR_FANTASMA = 0xFFE8EAED.toInt()

/**
 * Coche fantasma: dónde iba el coche de la mejor pasada a este mismo instante del tramo. Un solo punto en una
 * `SymbolLayer` con la silueta del icono de posición elegido (gris claro al 45 %), girada con el rumbo de la traza.
 * Se instala justo antes de activar la ubicación, así queda por debajo del coche real. Sin [PosicionFantasma] (sin
 * tramo vivo o sin mejor pasada) la fuente queda vacía y no se ve nada. Una instancia por mapa: recuerda el icono
 * dibujado.
 */
internal class CapaFantasma {
    private var iconoDibujado: IconoPosicion? = null

    /** Dónde está pintado ahora (a mitad de una animación, la posición intermedia). */
    private var mostrada: PosicionFantasma? = null
    private var animador: ValueAnimator? = null

    fun instalar(
        estilo: Style,
        contexto: Context,
        icono: IconoPosicion,
        fantasma: PosicionFantasma?,
    ) {
        iconoDibujado = null
        detener()
        mostrada = fantasma
        estilo.addSource(GeoJsonSource(ID_FUENTE, fantasmaGeoJson(fantasma)))
        estilo.addLayer(
            SymbolLayer(ID_CAPA, ID_FUENTE).withProperties(
                PropertyFactory.iconImage(ID_IMAGEN),
                PropertyFactory.iconRotate(Expression.get(PROPIEDAD_RUMBO_FANTASMA)),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconOpacity(OPACIDAD_FANTASMA),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        dibujarIcono(estilo, contexto, icono)
    }

    fun actualizar(
        estilo: Style,
        contexto: Context,
        icono: IconoPosicion,
        fantasma: PosicionFantasma?,
    ) {
        dibujarIcono(estilo, contexto, icono)
        val fuente = estilo.getSourceAs<GeoJsonSource>(ID_FUENTE) ?: return
        val origen = mostrada
        detener()
        if (origen == null || fantasma == null || !debeAnimarFantasma(origen, fantasma)) {
            // Primer punto, fin del fantasma o salto grande: se coloca de golpe.
            mostrada = fantasma
            fuente.setGeoJson(fantasmaGeoJson(fantasma))
        } else if (origen != fantasma) {
            animarHacia(fuente, origen, fantasma)
        }
    }

    /** Corta la animación en marcha (al cambiar de estilo o cerrar el mapa) sin tocar la fuente. */
    fun detener() {
        animador?.cancel()
        animador = null
    }

    /** Desliza el fantasma de [origen] a [destino] en [DURACION_ANIMACION_MS] cambiando solo su fuente GeoJSON. */
    private fun animarHacia(
        fuente: GeoJsonSource,
        origen: PosicionFantasma,
        destino: PosicionFantasma,
    ) {
        animador =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DURACION_ANIMACION_MS
                interpolator = LinearInterpolator()
                addUpdateListener { a ->
                    val actual = interpolarFantasma(origen, destino, a.animatedFraction.toDouble())
                    mostrada = actual
                    fuente.setGeoJson(fantasmaGeoJson(actual))
                }
                start()
            }
    }

    /** La silueta se vuelve a dibujar solo si cambia el icono de posición. */
    private fun dibujarIcono(
        estilo: Style,
        contexto: Context,
        icono: IconoPosicion,
    ) {
        if (icono == iconoDibujado) return
        iconoDibujado = icono
        estilo.addImage(ID_IMAGEN, siluetaDe(contexto, icono))
    }
}

/** Lo que dura el deslizamiento entre dos posiciones: el intervalo del cronómetro (1 Hz), para que sea continuo. */
internal const val DURACION_ANIMACION_MS = 1_000L

/** Más allá de esta distancia entre dos posiciones (un salto, no un avance) el fantasma se coloca sin animar. */
internal const val SALTO_MAXIMO_ANIMADO_M = 200.0

/** Si [hasta] se alcanza deslizando desde [desde]: un avance normal, no un salto de más de [SALTO_MAXIMO_ANIMADO_M]. */
internal fun debeAnimarFantasma(
    desde: PosicionFantasma,
    hasta: PosicionFantasma,
): Boolean = desde.punto.distanciaA(hasta.punto) <= SALTO_MAXIMO_ANIMADO_M

/**
 * Posición a la [fraccion] (0 = [desde], 1 = [hasta]; se acota a 0..1) del deslizamiento: coordenadas y distancia en
 * línea recta (el tramo es de metros) y rumbo por el camino corto del círculo (350° → 10° pasa por 0°, no por 180°).
 */
internal fun interpolarFantasma(
    desde: PosicionFantasma,
    hasta: PosicionFantasma,
    fraccion: Double,
): PosicionFantasma {
    val f = fraccion.coerceIn(0.0, 1.0)
    val giro = ((hasta.rumboGrados - desde.rumboGrados + 540.0) % 360.0) - 180.0
    return PosicionFantasma(
        punto =
            PuntoGeo(
                desde.punto.lat + (hasta.punto.lat - desde.punto.lat) * f,
                desde.punto.lon + (hasta.punto.lon - desde.punto.lon) * f,
            ),
        rumboGrados = ((desde.rumboGrados + giro * f) % 360.0 + 360.0) % 360.0,
        distanciaM = desde.distanciaM + (hasta.distanciaM - desde.distanciaM) * f,
    )
}

/** El fantasma como un punto con su rumbo (grados desde el norte); sin fantasma, ninguno. */
internal fun fantasmaGeoJson(fantasma: PosicionFantasma?): FeatureCollection =
    FeatureCollection.fromFeatures(
        listOfNotNull(
            fantasma?.let {
                Feature.fromGeometry(Point.fromLngLat(it.punto.lon, it.punto.lat)).also { f ->
                    f.addNumberProperty(PROPIEDAD_RUMBO_FANTASMA, it.rumboGrados)
                }
            },
        ),
    )

/** Dibuja el icono de posición como silueta gris clara (el icono apunta hacia arriba: rumbo 0 = norte). */
private fun siluetaDe(
    contexto: Context,
    icono: IconoPosicion,
): Bitmap {
    val lado = (LADO_ICONO_DP * contexto.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    ContextCompat.getDrawable(contexto, icono.recurso())?.mutate()?.let { dibujo ->
        dibujo.setBounds(0, 0, lado, lado)
        dibujo.setTint(COLOR_FANTASMA)
        dibujo.draw(Canvas(bitmap))
    }
    return bitmap
}
