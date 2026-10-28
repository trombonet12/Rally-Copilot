package app.trombito.rallycopilot.ui.mapa

import org.junit.Assert.assertEquals
import org.junit.Test

class EstadoPermisoUbicacionTest {
    private fun estado(
        precisa: Boolean = false,
        aproximada: Boolean = false,
        yaPedido: Boolean = false,
        rationale: Boolean = false,
    ) = estadoPermisoUbicacion(precisa, aproximada, yaPedido, rationale)

    @Test
    fun `con la ubicacion precisa esta concedido`() {
        assertEquals(EstadoPermisoUbicacion.CONCEDIDO, estado(precisa = true, aproximada = true))
    }

    @Test
    fun `solo aproximada no basta para el GPS`() {
        assertEquals(EstadoPermisoUbicacion.SOLO_APROXIMADA, estado(aproximada = true, yaPedido = true))
    }

    @Test
    fun `antes de pedir se explica`() {
        assertEquals(EstadoPermisoUbicacion.EXPLICAR, estado())
    }

    @Test
    fun `rechazado una vez con rationale se puede volver a pedir`() {
        assertEquals(EstadoPermisoUbicacion.EXPLICAR, estado(yaPedido = true, rationale = true))
    }

    @Test
    fun `rechazado sin rationale es permanente`() {
        assertEquals(EstadoPermisoUbicacion.DENEGADO_PERMANENTE, estado(yaPedido = true, rationale = false))
    }
}
