package app.trombito.rallycopilot.ui.comun

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion

/** Dibujo del icono de posición (sin círculo de fondo; apunta hacia arriba). Lo usan el mapa y Ajustes. */
@DrawableRes
fun IconoPosicion.recurso(): Int =
    when (this) {
        IconoPosicion.FLECHA -> R.drawable.ic_posicion_flecha
        IconoPosicion.FLECHA_WRC -> R.drawable.ic_posicion_flecha_wrc
        IconoPosicion.COCHE_RALLY -> R.drawable.ic_posicion_coche
        IconoPosicion.CASCO -> R.drawable.ic_posicion_casco
    }

@StringRes
fun IconoPosicion.nombre(): Int =
    when (this) {
        IconoPosicion.FLECHA -> R.string.ajustes_icono_flecha
        IconoPosicion.FLECHA_WRC -> R.string.ajustes_icono_flecha_wrc
        IconoPosicion.COCHE_RALLY -> R.string.ajustes_icono_coche
        IconoPosicion.CASCO -> R.string.ajustes_icono_casco
    }
