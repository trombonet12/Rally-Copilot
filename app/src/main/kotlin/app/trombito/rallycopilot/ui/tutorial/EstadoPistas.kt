package app.trombito.rallycopilot.ui.tutorial

import androidx.compose.runtime.compositionLocalOf

/**
 * Qué pistas en contexto faltan por enseñar y cómo se marcan como entendidas. Lo provee la navegación (con el
 * ViewModel); sin proveedor (pruebas de otras pantallas) no hay pistas pendientes.
 */
class EstadoPistas(
    private val vistas: Set<Pista>,
    private val onEntendida: (Pista) -> Unit,
) {
    fun pendiente(pista: Pista): Boolean = pista !in vistas

    fun entendida(pista: Pista) = onEntendida(pista)

    companion object {
        /** Todas vistas: nunca sale ninguna pista. */
        val Ninguna = EstadoPistas(Pista.entries.toSet()) {}
    }
}

val LocalPistas = compositionLocalOf { EstadoPistas.Ninguna }
