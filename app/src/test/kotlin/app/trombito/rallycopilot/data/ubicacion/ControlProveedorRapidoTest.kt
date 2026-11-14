package app.trombito.rallycopilot.data.ubicacion

import app.trombito.rallycopilot.data.ubicacion.ControlProveedorRapido.Orden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REN-3: el proveedor rápido se quita con el GPS fijado y se vuelve a poner si el GPS se calla. */
class ControlProveedorRapidoTest {
    private val control = ControlProveedorRapido()

    private fun assertNo(orden: Orden) = assertEquals(Orden.NADA, orden)

    private fun assertQuitar(orden: Orden) = assertEquals(Orden.QUITAR, orden)

    @Test
    fun `tres fijaciones precisas seguidas del gps quitan el rapido`() {
        assertNo(control.alGps(5.0, 1_000))
        assertNo(control.alGps(5.0, 2_000))
        assertQuitar(control.alGps(5.0, 3_000))
        // Ya quitado: no se pide quitarlo otra vez.
        assertNo(control.alGps(5.0, 4_000))
    }

    @Test
    fun `una fijacion imprecisa o sin precision corta la racha`() {
        assertNo(control.alGps(5.0, 1_000))
        assertNo(control.alGps(5.0, 2_000))
        assertNo(control.alGps(25.0, 3_000))
        assertNo(control.alGps(5.0, 4_000))
        assertNo(control.alGps(null, 5_000))
        assertNo(control.alGps(5.0, 6_000))
        assertNo(control.alGps(5.0, 7_000))
        assertQuitar(control.alGps(5.0, 8_000))
    }

    @Test
    fun `20 m justos todavia cuentan y 20,1 no`() {
        assertNo(control.alGps(20.0, 1_000))
        assertNo(control.alGps(20.0, 2_000))
        assertQuitar(control.alGps(20.0, 3_000))
        val otro = ControlProveedorRapido()
        repeat(3) { assertNo(otro.alGps(20.1, it * 1_000L)) }
    }

    @Test
    fun `con el rapido puesto no hay nada que volver a poner`() {
        assertFalse(control.alVigilar(60_000))
        control.alGps(5.0, 1_000)
        assertFalse(control.alVigilar(60_000))
    }

    @Test
    fun `tras mas de 5 s sin gps se vuelve a poner el rapido, una sola vez`() {
        repeat(3) { control.alGps(5.0, 1_000L + it * 1_000) }
        assertFalse("a los 5 s justos aun no", control.alVigilar(3_000 + 5_000))
        assertTrue(control.alVigilar(3_000 + 5_001))
        assertFalse(control.alVigilar(3_000 + 9_000))
    }

    @Test
    fun `tras volver a ponerlo hacen falta otras tres para quitarlo`() {
        repeat(3) { control.alGps(5.0, 1_000L + it * 1_000) }
        assertTrue(control.alVigilar(20_000))
        assertNo(control.alGps(5.0, 21_000))
        assertNo(control.alGps(5.0, 22_000))
        assertQuitar(control.alGps(5.0, 23_000))
    }

    @Test
    fun `un gps que sigue entregando pero degradado vuelve a pedir el rapido tras tres fijaciones malas`() {
        repeat(3) { control.alGps(5.0, 1_000L + it * 1_000) }
        assertEquals(Orden.NADA, control.alGps(60.0, 4_000))
        assertEquals(Orden.NADA, control.alGps(null, 5_000))
        assertEquals(Orden.PONER, control.alGps(80.0, 6_000))
        // Ya puesto: no se repite la orden y hacen falta otras tres buenas para quitarlo.
        assertEquals(Orden.NADA, control.alGps(80.0, 7_000))
        assertFalse(control.alVigilar(60_000))
        assertNo(control.alGps(5.0, 8_000))
        assertNo(control.alGps(5.0, 9_000))
        assertQuitar(control.alGps(5.0, 10_000))
    }

    @Test
    fun `una fijacion buena corta la racha de degradadas`() {
        repeat(3) { control.alGps(5.0, 1_000L + it * 1_000) }
        assertEquals(Orden.NADA, control.alGps(60.0, 4_000))
        assertEquals(Orden.NADA, control.alGps(60.0, 5_000))
        assertEquals(Orden.NADA, control.alGps(5.0, 6_000))
        assertEquals(Orden.NADA, control.alGps(60.0, 7_000))
        assertEquals(Orden.NADA, control.alGps(60.0, 8_000))
        assertEquals(Orden.PONER, control.alGps(60.0, 9_000))
    }
}
