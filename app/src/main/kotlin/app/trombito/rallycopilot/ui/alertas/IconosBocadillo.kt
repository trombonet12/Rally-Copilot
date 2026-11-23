package app.trombito.rallycopilot.ui.alertas

import androidx.annotation.DrawableRes
import app.trombito.rallycopilot.R

/** Icono del bocadillo del mapa: el del radar, el del tramo, el de la incidencia, el de la bandera o el de la nota. */
@DrawableRes
internal fun iconoBocadillo(contenido: ContenidoBocadillo): Int =
    when (contenido) {
        is ContenidoBocadillo.Radar -> iconoRadar(contenido.clase)
        is ContenidoBocadillo.Tramo -> iconoRadar(ClaseRadar.TRAMO)
        is ContenidoBocadillo.Paso -> iconoRadar(ClaseRadar.FIJO)
        is ContenidoBocadillo.Incidencia -> iconoIncidencia(contenido.tipo)
        is ContenidoBocadillo.RadarTocado -> iconoRadar(contenido.clase)
        is ContenidoBocadillo.TramoRadarTocado -> iconoRadar(ClaseRadar.TRAMO)
        is ContenidoBocadillo.NotaDelMapa -> contenido.icono
        is ContenidoBocadillo.Bandera -> R.drawable.ic_alerta_bandera
    }
