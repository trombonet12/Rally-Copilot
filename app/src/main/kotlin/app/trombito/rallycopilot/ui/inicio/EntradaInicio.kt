package app.trombito.rallycopilot.ui.inicio

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R

/**
 * Entradas del menú de inicio, en el orden del producto. «Reconocer» está en el detalle de cada tramo y «Mapas
 * offline» en Ajustes › Datos.
 */
enum class EntradaInicio(
    @param:StringRes val titulo: Int,
) {
    CONDUCIR(R.string.inicio_conducir),
    MIS_TRAMOS(R.string.inicio_mis_tramos),
    PERFIL(R.string.inicio_perfil),
    AJUSTES(R.string.inicio_ajustes),
}
