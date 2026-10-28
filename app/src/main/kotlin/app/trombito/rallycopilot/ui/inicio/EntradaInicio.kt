package app.trombito.rallycopilot.ui.inicio

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R

/** Entradas del menú de inicio, en el orden del producto. Cada una se activa en el hito que la implementa. */
enum class EntradaInicio(
    @param:StringRes val titulo: Int,
) {
    CONDUCIR(R.string.inicio_conducir),
    MIS_TRAMOS(R.string.inicio_mis_tramos),
    RECONOCIMIENTO(R.string.inicio_reconocimiento),
    MAPAS_OFFLINE(R.string.inicio_mapas_offline),
    PERFIL(R.string.inicio_perfil),
    AJUSTES(R.string.inicio_ajustes),
}
