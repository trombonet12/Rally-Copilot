package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import kotlinx.serialization.Serializable

const val ETIQUETA_SECCION_PREFIJO = "ajustes-seccion-"
const val ETIQUETA_SECCION_PREVISTA_PREFIJO = "ajustes-prevista-de-"

/**
 * Las categorías de la portada de Ajustes, una fila cada una. Cada una abre su subpantalla (vista previa fija arriba,
 * controles debajo); [AYUDA] reúne Ayuda y el acceso a Acerca de. [clave] identifica la fila en las pruebas.
 */
@Keep
@Serializable
enum class SeccionAjustes(
    val clave: String,
) {
    COPILOTO("copiloto"),
    VOZ("voz"),
    TRAMOS("tramos"),
    MAPA("mapa"),
    ESCUCHA("escucha"),
    TEMA("tema"),
    DATOS("datos"),
    AYUDA("ayuda"),
}

@StringRes
internal fun SeccionAjustes.titulo(): Int =
    when (this) {
        SeccionAjustes.COPILOTO -> R.string.ajustes_seccion_copiloto
        SeccionAjustes.VOZ -> R.string.ajustes_seccion_voz
        SeccionAjustes.TRAMOS -> R.string.ajustes_seccion_tramos
        SeccionAjustes.MAPA -> R.string.ajustes_seccion_mapa
        SeccionAjustes.ESCUCHA -> R.string.ajustes_seccion_escucha
        SeccionAjustes.TEMA -> R.string.ajustes_seccion_apariencia
        SeccionAjustes.DATOS -> R.string.ajustes_seccion_datos
        SeccionAjustes.AYUDA -> R.string.ajustes_seccion_ayuda
    }

/** El icono de casco del copiloto (el mismo que el botón de la hoja del mapa), si la sección usa un recurso propio. */
@DrawableRes
internal fun SeccionAjustes.iconoRecurso(): Int? =
    if (this ==
        SeccionAjustes.COPILOTO
    ) {
        R.drawable.ic_modo_completo
    } else {
        null
    }

internal fun SeccionAjustes.icono(): ImageVector =
    when (this) {
        SeccionAjustes.COPILOTO -> Iconos.Piloto
        SeccionAjustes.VOZ -> Iconos.Volumen
        SeccionAjustes.TRAMOS -> Iconos.Cronometro
        SeccionAjustes.MAPA -> Iconos.Mapa
        SeccionAjustes.ESCUCHA -> Iconos.Microfono
        SeccionAjustes.TEMA -> Iconos.TemaOscuro
        SeccionAjustes.DATOS -> Iconos.Descarga
        SeccionAjustes.AYUDA -> Iconos.Ayuda
    }
