package app.trombito.rallycopilot.data.resumen

import app.trombito.rallycopilot.domain.resumen.ViajeTerminado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViajesTerminadosEnMemoriaTest {
    private val viajes = ViajesTerminadosEnMemoria()

    @Test
    fun `empieza sin ningun viaje pendiente`() {
        assertNull(viajes.ultimo.value)
    }

    @Test
    fun `publica el ultimo viaje y lo quita al consumirlo`() {
        viajes.publicar(ViajeTerminado(5L, 100L))
        assertEquals(ViajeTerminado(5L, 100L), viajes.ultimo.value)
        viajes.consumir(5L)
        assertNull(viajes.ultimo.value)
    }

    @Test
    fun `un viaje nuevo sustituye al anterior y consumir el viejo no lo quita`() {
        viajes.publicar(ViajeTerminado(5L, 100L))
        viajes.publicar(ViajeTerminado(6L, 200L))
        viajes.consumir(5L)
        assertEquals(ViajeTerminado(6L, 200L), viajes.ultimo.value)
    }
}
