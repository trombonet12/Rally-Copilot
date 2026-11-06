package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import kotlinx.serialization.Serializable

const val ETIQUETA_SECCION_PREFIJO = "ajustes-seccion-"
const val ETIQUETA_SECCION_PREVISTA_PREFIJO = "ajustes-prevista-de-"

/**
 * Las secciones de la portada de Ajustes. Cada una abre su subpantalla (vista previa fija arriba, controles debajo);
 * [ACERCA_DE] lleva a su pantalla propia. [clave] identifica la tarjeta y la vista previa en las pruebas.
 */
@Keep
@Serializable
enum class SeccionAjustes(
    val clave: String,
) {
    COPILOTO("copiloto"),
    NOTAS("notas"),
    VOZ("voz"),
    INDICACIONES("indicaciones"),
    TRAMOS("tramos"),
    MAPA("mapa"),
    ESCUCHA("escucha"),
    TEMA("tema"),
    DATOS("datos"),
    ACERCA_DE("acerca"),
}

@StringRes
internal fun SeccionAjustes.titulo(): Int =
    when (this) {
        SeccionAjustes.COPILOTO -> R.string.ajustes_seccion_copiloto
        SeccionAjustes.NOTAS -> R.string.ajustes_seccion_notas
        SeccionAjustes.VOZ -> R.string.ajustes_seccion_voz
        SeccionAjustes.INDICACIONES -> R.string.ajustes_seccion_indicaciones
        SeccionAjustes.TRAMOS -> R.string.ajustes_seccion_tramos
        SeccionAjustes.MAPA -> R.string.ajustes_seccion_mapa
        SeccionAjustes.ESCUCHA -> R.string.ajustes_seccion_escucha
        SeccionAjustes.TEMA -> R.string.ajustes_seccion_tema
        SeccionAjustes.DATOS -> R.string.ajustes_seccion_datos
        SeccionAjustes.ACERCA_DE -> R.string.ajustes_seccion_acerca
    }

internal fun SeccionAjustes.icono(): ImageVector =
    when (this) {
        SeccionAjustes.COPILOTO -> Iconos.Piloto
        SeccionAjustes.NOTAS -> Iconos.Notas
        SeccionAjustes.VOZ -> Iconos.Volumen
        SeccionAjustes.INDICACIONES -> Iconos.Direcciones
        SeccionAjustes.TRAMOS -> Iconos.Cronometro
        SeccionAjustes.MAPA -> Iconos.Mapa
        SeccionAjustes.ESCUCHA -> Iconos.Microfono
        SeccionAjustes.TEMA -> Iconos.TemaOscuro
        SeccionAjustes.DATOS -> Iconos.Descarga
        SeccionAjustes.ACERCA_DE -> Iconos.Informacion
    }
