package app.trombito.rallycopilot.ui.mapa

import android.graphics.RectF
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

private const val PROPIEDAD_NOMBRE = "name"
private const val TOLERANCIA_DP = 18f

/** Un elemento dibujado bajo el dedo: su nombre (si lo tiene) y si es un punto (lugar, POI) y no una línea (vía). */
internal data class CandidatoNombre(
    val nombre: String?,
    val esPunto: Boolean,
)

/**
 * El nombre que se da al punto mantenido: primero un lugar o POI con nombre (restaurante, playa, pueblo), después la
 * vía con nombre más cercana y, si no hay, el primer elemento con nombre; null si nada lo tiene. Sale de las teselas
 * ya dibujadas, así que funciona sin red.
 */
internal fun elegirNombre(candidatos: List<CandidatoNombre>): String? {
    val conNombre =
        candidatos.mapNotNull { c ->
            c.nombre
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { it to c.esPunto }
        }
    return (conNombre.firstOrNull { it.second } ?: conNombre.firstOrNull())?.first
}

internal object NombreDelPunto {
    /** El nombre bajo [x], [y] (píxeles) con un margen para el dedo; null si no hay nada con nombre. */
    fun en(
        mapa: MapLibreMap,
        x: Float,
        y: Float,
        densidad: Float,
    ): String? {
        val margen = TOLERANCIA_DP * densidad
        val zona = RectF(x - margen, y - margen, x + margen, y + margen)
        return elegirNombre(mapa.queryRenderedFeatures(zona).map(::candidato))
    }

    private fun candidato(f: Feature): CandidatoNombre =
        CandidatoNombre(
            nombre = if (f.hasProperty(PROPIEDAD_NOMBRE)) f.getStringProperty(PROPIEDAD_NOMBRE) else null,
            esPunto = f.geometry() is Point,
        )
}
