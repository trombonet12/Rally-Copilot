package app.trombito.rallycopilot.ui.inicio

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R

/** Entradas del menú de inicio, en el orden del producto. Cada una se activa en el hito que la implementa. */
enum class EntradaInicio(
    @param:StringRes val titulo: Int,
    /** Falsa mientras el hito que la implementa no está hecho: se muestra desactivada. */
    val activa: Boolean = false,
) {
    CONDUCIR(R.string.inicio_conducir, activa = true),
    MIS_TRAMOS(R.string.inicio_mis_tramos, activa = true),
    RECONOCIMIENTO(R.string.inicio_reconocimiento, activa = true),
    MAPAS_OFFLINE(R.string.inicio_mapas_offline, activa = true),
    PERFIL(R.string.inicio_perfil, activa = true),
    AJUSTES(R.string.inicio_ajustes, activa = true),
}
