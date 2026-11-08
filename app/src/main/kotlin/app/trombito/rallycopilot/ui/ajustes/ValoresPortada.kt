package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.ui.copiloto.nombre

/** El valor actual de una sección, en una línea corta, para la tarjeta de la portada. */
@Composable
internal fun valorDeSeccion(
    seccion: SeccionAjustes,
    ajustes: AjustesConduccion,
): String =
    when (seccion) {
        SeccionAjustes.COPILOTO -> stringResource(ajustes.modoCopiloto.nombre())
        SeccionAjustes.NOTAS -> stringResource(R.string.ajustes_valor_notas, segundosEscritos(ajustes.anticipacion))
        SeccionAjustes.VOZ -> valorVoz(ajustes)
        SeccionAjustes.INDICACIONES -> valorIndicaciones(ajustes.indicacionesActivas())
        SeccionAjustes.TRAMOS -> valorTramos(ajustes)
        SeccionAjustes.MAPA -> valorMapa(ajustes)
        SeccionAjustes.ESCUCHA -> valorEscucha(ajustes.activarConCopiloto)
        SeccionAjustes.TEMA -> stringResource(ajustes.tema.nombre())
        SeccionAjustes.DATOS -> stringResource(R.string.ajustes_valor_datos)
        SeccionAjustes.AYUDA -> stringResource(R.string.ajustes_valor_ayuda)
        SeccionAjustes.ACERCA_DE -> stringResource(R.string.ajustes_valor_acerca)
    }

@Composable
private fun valorIndicaciones(activas: Int): String =
    when (activas) {
        TOTAL_INDICACIONES -> stringResource(R.string.ajustes_valor_indicaciones_todas)
        0 -> stringResource(R.string.ajustes_valor_indicaciones_ninguna)
        else -> stringResource(R.string.ajustes_valor_indicaciones_algunas, activas)
    }

@Composable
private fun valorTramos(ajustes: AjustesConduccion): String =
    stringResource(
        when {
            !ajustes.cronometrar -> R.string.ajustes_valor_tramos_sin
            ajustes.salidaManual -> R.string.ajustes_valor_tramos_manual
            else -> R.string.ajustes_valor_tramos_automatica
        },
    )

@Composable
private fun valorMapa(ajustes: AjustesConduccion): String =
    stringResource(
        R.string.ajustes_valor_mapa,
        stringResource(ajustes.orientacion.nombre()),
        stringResource(ajustes.capa.nombreCorto()),
    )

@Composable
private fun valorEscucha(activada: Boolean): String {
    val texto = if (activada) R.string.ajustes_valor_escucha_si else R.string.ajustes_valor_escucha_no
    return stringResource(texto)
}

private const val TOTAL_INDICACIONES = 4

@Composable
private fun valorVoz(ajustes: AjustesConduccion): String {
    val voz = ajustes.voz
    return if (voz == null) {
        stringResource(R.string.ajustes_valor_voz_sin_elegir, ajustes.volumenVoz)
    } else {
        stringResource(R.string.ajustes_valor_voz, stringResource(voz.nombreCatalogo()), ajustes.volumenVoz)
    }
}
