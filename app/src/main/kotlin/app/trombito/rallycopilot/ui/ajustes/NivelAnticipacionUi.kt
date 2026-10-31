package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion

/** Nombre hablado del nivel, para TalkBack (los botones no llevan texto). */
@StringRes
fun NivelAnticipacion.nombre(): Int =
    when (this) {
        NivelAnticipacion.MUY_PRONTO -> R.string.ajustes_anticipacion_muy_pronto
        NivelAnticipacion.PRONTO -> R.string.ajustes_anticipacion_pronto
        NivelAnticipacion.NORMAL -> R.string.ajustes_anticipacion_normal
        NivelAnticipacion.TARDE -> R.string.ajustes_anticipacion_tarde
        NivelAnticipacion.MUY_TARDE -> R.string.ajustes_anticipacion_muy_tarde
    }

/** Barras de señal: cuantas más llenas, antes se canta la nota. */
@DrawableRes
fun NivelAnticipacion.icono(): Int =
    when (this) {
        NivelAnticipacion.MUY_PRONTO -> R.drawable.ic_nota_anticipacion_5
        NivelAnticipacion.PRONTO -> R.drawable.ic_nota_anticipacion_4
        NivelAnticipacion.NORMAL -> R.drawable.ic_nota_anticipacion_3
        NivelAnticipacion.TARDE -> R.drawable.ic_nota_anticipacion_2
        NivelAnticipacion.MUY_TARDE -> R.drawable.ic_nota_anticipacion_1
    }
