package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.EscalaVelocidad
import app.trombito.rallycopilot.motor.analisis.SegmentoMapa
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Propiedad numérica (0..1) de cada línea de la traza que lee el color por velocidad. */
internal const val PROPIEDAD_FRACCION = "f"
internal const val SIN_SIMPLIFICAR = 0f

/** Una línea por segmento con su posición 0..1 en la [escala] como propiedad numérica `f` (la lee el color). */
internal fun featuresTraza(
    segmentos: List<SegmentoMapa>,
    escala: EscalaVelocidad,
): List<Feature> =
    segmentos.filter { it.puntos.size >= 2 }.map { s ->
        Feature
            .fromGeometry(LineString.fromLngLats(s.puntos.map { Point.fromLngLat(it.lon, it.lat) }))
            .also { it.addNumberProperty(PROPIEDAD_FRACCION, escala.fraccion(s.velocidadKmh)) }
    }

/**
 * Opciones de la fuente de la traza: sin simplificar. Con la tolerancia por defecto MapLibre descarta los segmentos
 * más cortos que un píxel a zoom bajo, y una traza de decenas de km (miles de segmentos de ~25 m) desaparecía entera.
 */
internal fun opcionesFuenteTraza(): GeoJsonOptions = GeoJsonOptions().withTolerance(SIN_SIMPLIFICAR)

/** Color de la traza según la propiedad `f` (0..1) de cada segmento, con las mismas paradas que la leyenda. */
@Suppress("SpreadOperator") // una sola vez al instalar las capas, con cuatro paradas
internal fun colorPorVelocidad(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.toNumber(Expression.get(PROPIEDAD_FRACCION)),
        *PaletaVelocidad.paradas
            .map { (posicion, _) -> Expression.stop(posicion, Expression.color(PaletaVelocidad.argb(posicion))) }
            .toTypedArray(),
    )
