package app.trombito.rallycopilot.ui.alertas

import android.content.res.Resources
import app.trombito.rallycopilot.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SEPARADOR = " · "
private val LOCALE_ES = Locale.forLanguageTag("es-ES")
private val ZONA_MALLORCA = ZoneId.of("Europe/Madrid")
private val FORMATO_FIN = DateTimeFormatter.ofPattern("dd/MM", LOCALE_ES)

/**
 * Las líneas de detalle bajo el título del bocadillo (vacías si no hay nada que añadir):
 * - incidencia que se acerca: «Ma-13 km 56,5»;
 * - incidencia tocada ([ContenidoBocadillo.Incidencia.completo]): «Ma-13 km 56,5 · sentido Inca · afecta a carril
 *   derecho · máx. 60 · hasta 30/11», en otra línea el comentario de la DGT si lo hay y, al final, de cuándo son los
 *   datos: «DGT hace 5 min»;
 * - radares, tramos de radar y banderas: ver [detalleTocado].
 * Omite lo que falte; el rango de km sale «km 56,5–57,1» y el municipio solo si no hay km.
 */
internal fun lineasDetalleBocadillo(
    recursos: Resources,
    contenido: ContenidoBocadillo,
): List<String> =
    if (contenido is ContenidoBocadillo.Incidencia) {
        val lugar = lugarIncidencia(recursos, contenido)
        if (contenido.completo) {
            val extras = extrasIncidencia(recursos, contenido.detalle)
            listOfNotNull(
                (listOfNotNull(lugar) + extras).joinToString(SEPARADOR).takeIf { it.isNotEmpty() },
                contenido.detalle.comentario
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                contenido.antiguedadMs?.let {
                    recursos.getString(R.string.bocadillo_incidencia_dgt, textoAntiguedad(recursos, it))
                },
            )
        } else {
            listOfNotNull(lugar)
        }
    } else {
        detalleTocado(recursos, contenido)
    }

/** Sentido, carriles afectados, límite temporal y fin, en este orden y solo los que haya. */
private fun extrasIncidencia(
    recursos: Resources,
    d: DetalleIncidencia,
): List<String> =
    listOfNotNull(
        d.sentido?.textoNoVacio()?.let { recursos.getString(R.string.bocadillo_incidencia_sentido, it) },
        d.carriles?.textoNoVacio()?.let { recursos.getString(R.string.bocadillo_incidencia_carriles, it) },
        d.limiteKmh?.let { recursos.getString(R.string.bocadillo_incidencia_limite, it) },
        d.fin?.let { recursos.getString(R.string.bocadillo_incidencia_hasta, textoFin(it)) },
    )

private fun textoFin(fin: Instant): String = FORMATO_FIN.format(fin.atZone(ZONA_MALLORCA))

private fun String.textoNoVacio(): String? = takeIf { it.isNotBlank() }

/** «Ma-13 km 56,5», «Ma-13 km 56,5–57,1», «km 56,5» o, sin km, «Ma-13 · Inca» (municipio solo si no hay km). */
private fun lugarIncidencia(
    recursos: Resources,
    contenido: ContenidoBocadillo.Incidencia,
): String? {
    val d = contenido.detalle
    val km =
        d.pkDesde?.let { desde ->
            val hasta = d.pkHasta
            if (hasta == null) {
                recursos.getString(R.string.bocadillo_incidencia_km, textoKm(desde))
            } else {
                recursos.getString(R.string.bocadillo_incidencia_km_rango, textoKm(desde), textoKm(hasta))
            }
        }
    val via = listOfNotNull(contenido.carretera?.textoNoVacio(), km).joinToString(" ")
    val municipio = d.municipio?.textoNoVacio()?.takeIf { km == null }
    return listOfNotNull(via.takeIf { it.isNotEmpty() }, municipio)
        .joinToString(SEPARADOR)
        .takeIf { it.isNotEmpty() }
}

/** Punto kilométrico con coma decimal y sin «,0» de relleno: «56,5», «57». */
internal fun textoKm(pk: Double): String = String.format(LOCALE_ES, "%.1f", pk).removeSuffix(",0")
