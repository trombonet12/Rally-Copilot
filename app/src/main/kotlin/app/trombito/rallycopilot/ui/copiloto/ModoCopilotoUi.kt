package app.trombito.rallycopilot.ui.copiloto

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.voz.ModoCopiloto

/** Icono vectorial del modo (mismo trazo que los `ic_nota_*`): altavoz tachado, alerta, nota de roadbook y casco. */
@DrawableRes
fun ModoCopiloto.icono(): Int =
    when (this) {
        ModoCopiloto.SILENCIO -> R.drawable.ic_modo_silencio
        ModoCopiloto.SOLO_ALERTAS -> R.drawable.ic_modo_alertas
        ModoCopiloto.NOTAS -> R.drawable.ic_modo_notas
        ModoCopiloto.COMPLETO -> R.drawable.ic_modo_completo
    }

@StringRes
fun ModoCopiloto.nombre(): Int =
    when (this) {
        ModoCopiloto.SILENCIO -> R.string.copiloto_modo_silencio
        ModoCopiloto.SOLO_ALERTAS -> R.string.copiloto_modo_alertas
        ModoCopiloto.NOTAS -> R.string.copiloto_modo_notas
        ModoCopiloto.COMPLETO -> R.string.copiloto_modo_completo
    }

@StringRes
fun ModoCopiloto.detalle(): Int =
    when (this) {
        ModoCopiloto.SILENCIO -> R.string.copiloto_modo_silencio_detalle
        ModoCopiloto.SOLO_ALERTAS -> R.string.copiloto_modo_alertas_detalle
        ModoCopiloto.NOTAS -> R.string.copiloto_modo_notas_detalle
        ModoCopiloto.COMPLETO -> R.string.copiloto_modo_completo_detalle
    }

/** Nombre de la voz como en el catálogo de Ajustes: «Dave» y «Sharvard». */
@StringRes
fun VozCopiloto.nombre(): Int =
    when (this) {
        VozCopiloto.DAVEFX -> R.string.ajustes_voz_davefx
        VozCopiloto.SHARVARD -> R.string.ajustes_voz_sharvard
    }
