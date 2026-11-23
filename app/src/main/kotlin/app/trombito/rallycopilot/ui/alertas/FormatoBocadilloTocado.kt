package app.trombito.rallycopilot.ui.alertas

import android.content.res.Resources
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FormatoEs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val SEPARADOR = " · "
private const val VUELTA_GRADOS = 360.0
private const val RUMBOS = 8
private const val MS_POR_SEGUNDO = 1_000L
private const val SEGUNDOS_POR_HORA_KMH = 3.6
private const val TOLERANCIA_REDONDEO = 1e-6

/** El rumbo en grados (0 = norte, en sentido horario) como uno de los ocho de la brújula: «noreste». */
internal fun nombreRumbo(
    recursos: Resources,
    grados: Double,
): String {
    val rumbos = recursos.getStringArray(R.array.bocadillo_rumbos)
    val indice = ((grados % VUELTA_GRADOS + VUELTA_GRADOS) % VUELTA_GRADOS / (VUELTA_GRADOS / RUMBOS)).roundToInt()
    return rumbos[indice % RUMBOS]
}

/** Tiempo de crono sin cero a la izquierda en los minutos: «3:59,2» (con horas, «1:02:03,4»). */
internal fun tiempoCorto(ms: Long): String = FormatoEs.tiempo(ms).removePrefix("0")

/**
 * Lo más rápido que se puede hacer un tramo de [longitudM] sin pasar de [limiteKmh] de media: «2:24» para 3,2 km a 80.
 * Redondea hacia arriba al segundo (a 1 s menos ya se pasaría de la media).
 */
internal fun tiempoMinimo(
    longitudM: Double,
    limiteKmh: Int,
): String {
    val exactos = longitudM * SEGUNDOS_POR_HORA_KMH / limiteKmh
    val segundos = ceil(exactos - TOLERANCIA_REDONDEO).toLong().coerceAtLeast(0L)
    return FormatoEs.tiempo(segundos * MS_POR_SEGUNDO, conDecimas = false).removePrefix("0")
}

/** Título de los bocadillos de lo tocado (radar, tramo de radar, nota, bandera); null si es de otro tipo. */
internal fun tituloTocado(
    recursos: Resources,
    contenido: ContenidoBocadillo,
): String? =
    when (contenido) {
        is ContenidoBocadillo.RadarTocado -> {
            conLimite(recursos, recursos.getString(tituloRadar(contenido.clase)), contenido.limiteKmh)
        }

        is ContenidoBocadillo.TramoRadarTocado -> {
            conLimite(recursos, recursos.getString(R.string.alerta_radar_tramo), contenido.limiteKmh)
        }

        is ContenidoBocadillo.NotaDelMapa -> {
            contenido.frase
        }

        is ContenidoBocadillo.Bandera -> {
            tituloBandera(recursos, contenido)
        }

        else -> {
            null
        }
    }

private fun conLimite(
    recursos: Resources,
    titulo: String,
    limiteKmh: Int?,
): String = limiteKmh?.let { recursos.getString(R.string.bocadillo_radar_limite, titulo, it) } ?: titulo

private fun tituloBandera(
    recursos: Resources,
    bandera: ContenidoBocadillo.Bandera,
): String {
    val nombre = bandera.nombre?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        nombre != null && bandera.esMeta -> recursos.getString(R.string.bocadillo_bandera_meta, nombre)
        nombre != null -> recursos.getString(R.string.bocadillo_bandera_salida, nombre)
        bandera.esMeta -> recursos.getString(R.string.bocadillo_bandera_meta_sin_nombre)
        else -> recursos.getString(R.string.bocadillo_bandera_salida_sin_nombre)
    }
}

/**
 * Líneas de detalle de los bocadillos de lo tocado, omitiendo lo que no se sabe:
 * - radar fijo: «Sentido noreste» (o «Ambos sentidos»); móvil: «Ubicación aproximada · sentido noreste»;
 * - radar de tramo: «3,2 km · mínimo 2:24 para ir a 80» (sin límite, solo la longitud);
 * - bandera: «Tu mejor: 3:59,2» si hay pasadas; nota: nada (su frase ya es el título).
 * Vacía si [contenido] es de otro tipo.
 */
internal fun detalleTocado(
    recursos: Resources,
    contenido: ContenidoBocadillo,
): List<String> =
    when (contenido) {
        is ContenidoBocadillo.RadarTocado -> {
            listOf(lineaRadar(recursos, contenido))
        }

        is ContenidoBocadillo.TramoRadarTocado -> {
            listOfNotNull(lineaTramoRadar(recursos, contenido))
        }

        is ContenidoBocadillo.Bandera -> {
            val mejor = contenido.mejorMs?.let { recursos.getString(R.string.bocadillo_bandera_mejor, tiempoCorto(it)) }
            listOfNotNull(mejor)
        }

        else -> {
            emptyList()
        }
    }

private fun lineaRadar(
    recursos: Resources,
    radar: ContenidoBocadillo.RadarTocado,
): String {
    val sentido =
        radar.rumboGrados?.let { recursos.getString(R.string.bocadillo_incidencia_sentido, nombreRumbo(recursos, it)) }
            ?: recursos.getString(R.string.bocadillo_radar_ambos_sentidos)
    val partes =
        if (radar.clase == ClaseRadar.MOVIL) {
            listOf(recursos.getString(R.string.bocadillo_radar_zona_aproximada), sentido)
        } else {
            listOf(sentido)
        }
    return partes.joinToString(SEPARADOR).replaceFirstChar { it.uppercase() }
}

private fun lineaTramoRadar(
    recursos: Resources,
    tramo: ContenidoBocadillo.TramoRadarTocado,
): String? {
    val longitud = tramo.longitudM.takeIf { it > 0.0 }?.let { FormatoEs.distancia(it) }
    val limite = tramo.limiteKmh
    return when {
        longitud == null -> {
            null
        }

        limite == null -> {
            longitud
        }

        else -> {
            val minimo = tiempoMinimo(tramo.longitudM, limite)
            recursos.getString(R.string.bocadillo_tramo_radar_minimo, longitud, minimo, limite)
        }
    }
}
