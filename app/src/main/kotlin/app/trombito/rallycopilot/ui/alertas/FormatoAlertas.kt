package app.trombito.rallycopilot.ui.alertas

import android.content.res.Resources
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.maniobras.formatearDistanciaManiobra

private const val MINUTO_MS = 60_000L
private const val MINUTOS_POR_HORA = 60L
private const val HORAS_POR_DIA = 24L

/** «hace 25 min», «hace 2 h», «hace 3 d»: la antigüedad redondeada hacia abajo a la unidad mayor (mínimo 1 min). */
internal fun textoAntiguedad(
    recursos: Resources,
    antiguedadMs: Long,
): String {
    val minutos = (antiguedadMs / MINUTO_MS).coerceAtLeast(1L)
    val horas = minutos / MINUTOS_POR_HORA
    return when {
        horas < 1L -> recursos.getString(R.string.alerta_hace_minutos, minutos.toInt())
        horas < HORAS_POR_DIA -> recursos.getString(R.string.alerta_hace_horas, horas.toInt())
        else -> recursos.getString(R.string.alerta_hace_dias, (horas / HORAS_POR_DIA).toInt())
    }
}

/** Frase para TalkBack sobre la antigüedad: con error de conexión o solo antigua. */
internal fun descripcionFrescura(
    recursos: Resources,
    frescura: FrescuraIncidencias,
): String {
    val hace = textoAntiguedad(recursos, frescura.antiguedadMs)
    val recurso = if (frescura.conError) R.string.alerta_datos_sin_conexion else R.string.alerta_datos_antiguos
    return recursos.getString(recurso, hace)
}

/** Título visible del aviso (en mayúsculas en el banner). */
internal fun tituloAviso(
    recursos: Resources,
    aviso: AvisoAlerta,
): String =
    when (aviso) {
        is AvisoAlerta.Radar -> {
            if (aviso.sentidoContrario) {
                recursos.getString(R.string.alerta_radar_contrario)
            } else {
                recursos.getString(tituloRadar(aviso.clase))
            }
        }

        is AvisoAlerta.Incidencia -> {
            recursos.getString(tituloIncidencia(aviso.tipo))
        }
    }

/** Texto corto del bocadillo del mapa: «Radar fijo 80», «Radar · sentido contrario», «Pasaste a 87», «Obras»... */
internal fun textoBocadillo(
    recursos: Resources,
    contenido: ContenidoBocadillo,
): String =
    when (contenido) {
        is ContenidoBocadillo.Radar -> {
            textoBocadilloRadar(recursos, contenido)
        }

        is ContenidoBocadillo.Tramo -> {
            val titulo = recursos.getString(R.string.alerta_radar_tramo)
            val limite = contenido.limiteKmh
            when {
                limite == null -> titulo
                contenido.mediaKmh == null -> recursos.getString(R.string.bocadillo_tramo_sin_media, titulo, limite)
                else -> recursos.getString(R.string.bocadillo_tramo_media, titulo, limite, contenido.mediaKmh)
            }
        }

        is ContenidoBocadillo.Paso -> {
            recursos.getString(R.string.bocadillo_paso, contenido.velocidadKmh)
        }

        is ContenidoBocadillo.Incidencia -> {
            recursos.getString(tituloIncidencia(contenido.tipo))
        }
    }

private fun textoBocadilloRadar(
    recursos: Resources,
    contenido: ContenidoBocadillo.Radar,
): String {
    val limite = contenido.limiteKmh
    val titulo = recursos.getString(tituloRadar(contenido.clase))
    return when {
        contenido.sentidoContrario -> recursos.getString(R.string.alerta_radar_contrario)
        limite == null -> titulo
        else -> recursos.getString(R.string.bocadillo_radar_limite, titulo, limite)
    }
}

/** Distancia del banner: «350 m», «1,2 km»; null si el aviso es de una incidencia en la que ya se está. */
internal fun distanciaAviso(aviso: AvisoAlerta): String? {
    val dentro = aviso is AvisoAlerta.Incidencia && aviso.distanciaM <= 0
    return if (dentro) null else formatearDistanciaManiobra(aviso.distanciaM.toDouble())
}

/** Lo que lee TalkBack del banner: «Radar fijo, a 500 m. Límite de velocidad: 50 kilómetros por hora». */
internal fun descripcionAviso(
    recursos: Resources,
    aviso: AvisoAlerta,
    frescura: FrescuraIncidencias?,
): String {
    val titulo = tituloAviso(recursos, aviso)
    val carretera = (aviso as? AvisoAlerta.Incidencia)?.carretera
    val lugar = carretera?.let { recursos.getString(R.string.alerta_descripcion_en_carretera, titulo, it) } ?: titulo
    val distancia = distanciaAviso(aviso)
    val base =
        if (distancia == null) {
            recursos.getString(R.string.alerta_descripcion_aqui, lugar)
        } else {
            recursos.getString(R.string.alerta_descripcion_a_distancia, lugar, distancia)
        }
    val partes = mutableListOf(base)
    if (aviso is AvisoAlerta.Radar && aviso.limiteKmh != null) {
        partes += recursos.getString(R.string.panel_limite_descripcion, aviso.limiteKmh)
    }
    if (aviso is AvisoAlerta.Incidencia && frescura != null) partes += descripcionFrescura(recursos, frescura)
    return partes.joinToString(". ")
}
