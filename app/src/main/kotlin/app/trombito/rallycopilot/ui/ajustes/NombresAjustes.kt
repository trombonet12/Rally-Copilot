package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto

/** Nombre de la voz tal como sale en el catálogo («Dave», «Sharvard»), no «Voz 1». */
@StringRes
internal fun VozCopiloto.nombreCatalogo(): Int =
    when (this) {
        VozCopiloto.DAVEFX -> R.string.ajustes_voz_davefx
        VozCopiloto.SHARVARD -> R.string.ajustes_voz_sharvard
    }

@StringRes
internal fun Tema.nombre(): Int =
    when (this) {
        Tema.OSCURO -> R.string.ajustes_tema_oscuro
        Tema.CLARO -> R.string.ajustes_tema_claro
        Tema.SISTEMA -> R.string.ajustes_tema_sistema
        Tema.SOL -> R.string.ajustes_tema_sol
    }

@StringRes
internal fun Orientacion.nombre(): Int =
    when (this) {
        Orientacion.RUMBO -> R.string.ajustes_orientacion_rumbo
        Orientacion.NORTE -> R.string.ajustes_orientacion_norte
    }

@StringRes
internal fun CapaMapa.nombreCorto(): Int =
    when (this) {
        CapaMapa.VECTORIAL -> R.string.ajustes_valor_mapa_normal
        CapaMapa.SATELITE -> R.string.ajustes_capa_satelite
    }

/** Cuántas de las cuatro indicaciones (cruces, puentes, salidas, rotondas) están activadas. */
internal fun AjustesConduccion.indicacionesActivas(): Int =
    listOf(avisarCruces, avisarPuentes, avisarSalidasAutopista, avisarRotondas).count { it }
