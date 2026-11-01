package app.trombito.rallycopilot.ui.alertas

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.ui.theme.RallyOscuro

private val GRAVES = setOf(TipoIncidencia.CORTE, TipoIncidencia.ACCIDENTE)

/** Icono de un radar según su clase. */
@DrawableRes
internal fun iconoRadar(clase: ClaseRadar): Int =
    when (clase) {
        ClaseRadar.FIJO -> R.drawable.ic_alerta_radar
        ClaseRadar.MOVIL -> R.drawable.ic_alerta_radar_movil
        ClaseRadar.TRAMO -> R.drawable.ic_alerta_tramo
    }

@StringRes
internal fun tituloRadar(clase: ClaseRadar): Int =
    when (clase) {
        ClaseRadar.FIJO -> R.string.alerta_radar_fijo
        ClaseRadar.MOVIL -> R.string.alerta_radar_movil
        ClaseRadar.TRAMO -> R.string.alerta_radar_tramo
    }

/** Icono de una incidencia según su tipo (el mismo en el banner, la tarjeta y los marcadores del mapa). */
@DrawableRes
internal fun iconoIncidencia(tipo: TipoIncidencia): Int =
    when (tipo) {
        TipoIncidencia.OBRAS -> R.drawable.ic_alerta_obras
        TipoIncidencia.ACCIDENTE -> R.drawable.ic_alerta_accidente
        TipoIncidencia.RETENCION -> R.drawable.ic_alerta_retencion
        TipoIncidencia.CORTE -> R.drawable.ic_alerta_corte
        TipoIncidencia.METEO -> R.drawable.ic_alerta_meteo
        TipoIncidencia.OTRA -> R.drawable.ic_alerta_otra
    }

@StringRes
internal fun tituloIncidencia(tipo: TipoIncidencia): Int =
    when (tipo) {
        TipoIncidencia.OBRAS -> R.string.alerta_incidencia_obras
        TipoIncidencia.ACCIDENTE -> R.string.alerta_incidencia_accidente
        TipoIncidencia.RETENCION -> R.string.alerta_incidencia_retencion
        TipoIncidencia.CORTE -> R.string.alerta_incidencia_corte
        TipoIncidencia.METEO -> R.string.alerta_incidencia_meteo
        TipoIncidencia.OTRA -> R.string.alerta_incidencia_otra
    }

/** Nombre del icono del marcador en el estilo del mapa, uno por tipo. */
internal fun imagenMarcador(tipo: TipoIncidencia): String = "incidencia-${tipo.name.lowercase()}"

@DrawableRes
internal fun iconoDeAviso(aviso: AvisoAlerta): Int =
    when (aviso) {
        is AvisoAlerta.Radar -> iconoRadar(aviso.clase)
        is AvisoAlerta.Incidencia -> iconoIncidencia(aviso.tipo)
    }

/** `FIJO`, `MOVIL`, `TRAMO` o el tipo de la incidencia: sufijo del tag del icono. */
internal fun claveDeAviso(aviso: AvisoAlerta): String =
    when (aviso) {
        is AvisoAlerta.Radar -> aviso.clase.name
        is AvisoAlerta.Incidencia -> aviso.tipo.name
    }

/** Ámbar para los radares y los avisos corrientes; rojo para los cortes y los accidentes. */
internal fun colorAviso(aviso: AvisoAlerta): Color =
    if (aviso is AvisoAlerta.Incidencia) colorIncidencia(aviso.tipo) else RallyOscuro.aviso

internal fun colorIncidencia(tipo: TipoIncidencia): Color =
    if (tipo in GRAVES) RallyOscuro.deltaPeor else RallyOscuro.aviso
