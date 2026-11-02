package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.motor.analisis.PerfilPiloto
import app.trombito.rallycopilot.motor.analisis.ResumenTramo as ResumenTramoPerfil

/**
 * Estado del perfil. [seleccionadoId] es el tramo cuya evolución se ve (por defecto, el de más pasadas); [mejores] son
 * los tramos con pasadas, de más a menos hechos, limitados a [TOP_TRAMOS].
 */
data class PerfilUiState(
    val cargando: Boolean = true,
    val perfil: PerfilPiloto? = null,
    val seleccionadoId: String? = null,
) {
    /** Sin kilómetros ni pasadas: el perfil empieza en el primer trayecto. */
    val vacio: Boolean get() = !cargando && (perfil == null || (perfil.numeroPasadas == 0 && perfil.kmTotales <= 0.0))

    val mejores: List<ResumenTramoPerfil>
        get() =
            perfil
                ?.tramos
                .orEmpty()
                .filter { it.numeroPasadas > 0 }
                .sortedWith(compareByDescending<ResumenTramoPerfil> { it.numeroPasadas }.thenBy { it.nombre })
                .take(TOP_TRAMOS)

    val seleccionado: ResumenTramoPerfil? get() =
        mejores.firstOrNull { it.tramoId == seleccionadoId }
            ?: mejores.firstOrNull()

    companion object {
        const val TOP_TRAMOS = 5
    }
}
