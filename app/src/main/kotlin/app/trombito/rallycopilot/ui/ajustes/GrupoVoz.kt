package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import app.trombito.rallycopilot.R

/**
 * Los grupos de la chuleta de voz, en el orden en que se enseñan. Las frases de cada grupo son los elementos de su
 * `string-array` en `strings_tutorial.xml`.
 *
 * Qué existe y qué llega:
 * - [EDICION] y las órdenes sueltas de [CONTROL] («silencio», «habla», «repite», «más pronto», «más tarde») y de
 *   [MAPA] («marca aquí») son las que entiende hoy `InterpreteOrdenes` (motor/edicion), con la gramática de
 *   `GramaticaOrdenes`.
 * - [RUTA], las órdenes de modo de [CONTROL] («modo notas», «modo completo») y de mapa de [MAPA] («satélite», «mapa»)
 *   llegan con la ampliación de la voz; el orquestador debe confirmar las frases exactas.
 * - [NAVEGACION] llega en H14d parte 2: «llévame a <lugar>», «volver a casa», «cancela la ruta», «gasolinera más
 *   cercana» y «añade parada en <lugar>».
 */
internal enum class GrupoVoz(
    @StringRes val titulo: Int,
    @ArrayRes val frases: Int,
) {
    RUTA(R.string.ayuda_voz_grupo_ruta, R.array.ayuda_voz_ruta),
    CONTROL(R.string.ayuda_voz_grupo_control, R.array.ayuda_voz_control),
    NAVEGACION(R.string.ayuda_voz_grupo_navegacion, R.array.ayuda_voz_navegacion),
    MAPA(R.string.ayuda_voz_grupo_mapa, R.array.ayuda_voz_mapa),
    EDICION(R.string.ayuda_voz_grupo_edicion, R.array.ayuda_voz_edicion),
}

/** Lo que el copiloto no puede hacer, una línea cada uno. */
internal val LIMITES_VOZ =
    listOf(
        R.string.ayuda_voz_limite_dictado,
        R.string.ayuda_voz_limite_direcciones,
        R.string.ayuda_voz_limite_idiomas,
    )
