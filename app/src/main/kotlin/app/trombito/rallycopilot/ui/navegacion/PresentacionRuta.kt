package app.trombito.rallycopilot.ui.navegacion

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.ui.comun.Iconos

/** El título corto bajo el icono de cada ruta. */
@StringRes
internal fun tituloRuta(perfil: PerfilRuta): Int =
    when (perfil) {
        PerfilRuta.MAS_RAPIDA -> R.string.navegacion_ui_ruta_rapida_titulo
        PerfilRuta.EQUILIBRADA -> R.string.navegacion_ui_ruta_equilibrada_titulo
        PerfilRuta.SINUOSA -> R.string.navegacion_ui_ruta_sinuosa_titulo
    }

/** El nombre completo que lee TalkBack. */
@StringRes
internal fun nombreRuta(perfil: PerfilRuta): Int =
    when (perfil) {
        PerfilRuta.MAS_RAPIDA -> R.string.navegacion_ui_ruta_rapida
        PerfilRuta.EQUILIBRADA -> R.string.navegacion_ui_ruta_equilibrada
        PerfilRuta.SINUOSA -> R.string.navegacion_ui_ruta_sinuosa
    }

internal fun iconoRuta(perfil: PerfilRuta): ImageVector =
    when (perfil) {
        PerfilRuta.MAS_RAPIDA -> Iconos.Rayo
        PerfilRuta.EQUILIBRADA -> Iconos.CurvasSuaves
        PerfilRuta.SINUOSA -> Iconos.Curvas
    }

internal fun etiquetaRuta(perfil: PerfilRuta): String =
    when (perfil) {
        PerfilRuta.MAS_RAPIDA -> ETIQUETA_RUTA_RAPIDA
        PerfilRuta.EQUILIBRADA -> ETIQUETA_RUTA_EQUILIBRADA
        PerfilRuta.SINUOSA -> ETIQUETA_RUTA_SINUOSA
    }
