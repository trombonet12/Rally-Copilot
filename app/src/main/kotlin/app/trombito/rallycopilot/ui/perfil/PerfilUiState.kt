package app.trombito.rallycopilot.ui.perfil

import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.motor.analisis.Conclusion

/**
 * Estado del perfil del piloto: el [tablero] ya calculado (cabecera, último viaje y conclusiones). [error] si falló la
 * lectura de los viajes.
 */
data class PerfilUiState(
    val cargando: Boolean = true,
    val error: Boolean = false,
    val tablero: TableroPiloto = TableroPiloto(),
) {
    /** Sin ningún viaje terminado: el perfil empieza en el primero. */
    val vacio: Boolean get() = !cargando && !error && tablero.viajes == 0

    /** Las tarjetas de conclusión en el orden de `TipoConclusion`, que es el orden del tablero. */
    val conclusiones: List<Conclusion> get() = tablero.conclusiones.sortedBy { it.tipo.ordinal }
}
