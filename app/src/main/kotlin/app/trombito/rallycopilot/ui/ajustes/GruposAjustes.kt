package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion

/** Cada vista previa fija de las subpantallas; [clave] va en su etiqueta de pruebas. */
internal enum class PrevistaAjustes(
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
}

/**
 * Un grupo de controles de una subpantalla: su [previa], el subtítulo que lo encabeza cuando la subpantalla tiene
 * varios y la [huella], los valores que le pertenecen (si cambian, ese grupo pasa a ser el de la vista previa).
 */
internal class GrupoAjustes(
    val previa: PrevistaAjustes,
    @StringRes val subtitulo: Int,
    val huella: (AjustesConduccion) -> List<Any?>,
)

private val GRUPO_COPILOTO =
    GrupoAjustes(PrevistaAjustes.COPILOTO, R.string.ajustes_grupo_copiloto) { listOf(it.modoCopiloto) }
private val GRUPO_NOTAS =
    GrupoAjustes(PrevistaAjustes.NOTAS, R.string.ajustes_grupo_notas) {
        listOf(it.anticipacion, it.notasSimplificadas, it.notasGrandes)
    }
private val GRUPO_VOZ =
    GrupoAjustes(PrevistaAjustes.VOZ, R.string.ajustes_grupo_voz) {
        listOf(
            it.voz,
            it.volumenVoz,
            it.audioCasco,
            it.escuchaCascoSiempre,
            it.velocidadHabla,
            it.avisarRadares,
            it.avisarIncidencias,
            it.avisarVelocidad,
        )
    }
private val GRUPO_INDICACIONES =
    GrupoAjustes(PrevistaAjustes.INDICACIONES, R.string.ajustes_grupo_indicaciones) {
        listOf(it.avisarCruces, it.avisarPuentes, it.avisarSalidasAutopista, it.avisarRotondas)
    }

/**
 * Los grupos de controles de la subpantalla, en orden; el primero es el de la vista previa al abrirla. Datos y Ayuda
 * no tienen vista previa ni grupos.
 */
internal fun SeccionAjustes.grupos(): List<GrupoAjustes> =
    when (this) {
        SeccionAjustes.COPILOTO -> listOf(GRUPO_COPILOTO, GRUPO_NOTAS)
        SeccionAjustes.VOZ -> listOf(GRUPO_VOZ, GRUPO_INDICACIONES)
        SeccionAjustes.TRAMOS -> listOf(grupoUnico(PrevistaAjustes.TRAMOS, R.string.ajustes_seccion_tramos))
        SeccionAjustes.MAPA -> listOf(grupoUnico(PrevistaAjustes.MAPA, R.string.ajustes_seccion_mapa))
        SeccionAjustes.ESCUCHA -> listOf(grupoUnico(PrevistaAjustes.ESCUCHA, R.string.ajustes_seccion_escucha))
        SeccionAjustes.TEMA -> listOf(grupoUnico(PrevistaAjustes.TEMA, R.string.ajustes_seccion_apariencia))
        SeccionAjustes.DATOS, SeccionAjustes.AYUDA -> emptyList()
    }

private fun grupoUnico(
    previa: PrevistaAjustes,
    @StringRes titulo: Int,
) = GrupoAjustes(previa, titulo) { emptyList() }
