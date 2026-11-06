package app.trombito.rallycopilot.ui.alertas

import androidx.annotation.DrawableRes

/** Icono del bocadillo del mapa: el del radar, el del tramo o el de la incidencia. */
@DrawableRes
internal fun iconoBocadillo(contenido: ContenidoBocadillo): Int =
    when (contenido) {
        is ContenidoBocadillo.Radar -> iconoRadar(contenido.clase)
        is ContenidoBocadillo.Tramo -> iconoRadar(ClaseRadar.TRAMO)
        is ContenidoBocadillo.Paso -> iconoRadar(ClaseRadar.FIJO)
        is ContenidoBocadillo.Incidencia -> iconoIncidencia(contenido.tipo)
    }
