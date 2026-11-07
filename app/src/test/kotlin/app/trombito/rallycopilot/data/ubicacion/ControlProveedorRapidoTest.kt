package app.trombito.rallycopilot.data.ubicacion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REN-3: el proveedor rápido se quita con el GPS fijado y se vuelve a poner si el GPS se calla. */
class ControlProveedorRapidoTest {
    private val control = ControlProveedorRapido()

    @Test
    fun `tres fijaciones precisas seguidas del gps quitan el rapido`() {
        assertFalse(control.alGps(5.0, 1_000))
        assertFalse(control.alGps(5.0, 2_000))
        assertTrue(control.alGps(5.0, 3_000))
        // Ya quitado: no se pide quitarlo otra vez.
        assertFalse(control.alGps(5.0, 4_000))
    }

    @Test
    fun `una fijacion imprecisa o sin precision corta la racha`() {
        assertFalse(control.alGps(5.0, 1_000))
        assertFalse(control.alGps(5.0, 2_000))
        assertFalse(control.alGps(25.0, 3_000))
        assertFalse(control.alGps(5.0, 4_000))
        assertFalse(control.alGps(null, 5_000))
        assertFalse(control.alGps(5.0, 6_000))
        assertFalse(control.alGps(5.0, 7_000))
        assertTrue(control.alGps(5.0, 8_000))
    }

    @Test
    fun `20 m justos todavia cuentan y 20,1 no`() {
        assertFalse(control.alGps(20.0, 1_000))
        assertFalse(control.alGps(20.0, 2_000))
        assertTrue(control.alGps(20.0, 3_000))
        val otro = ControlProveedorRapido()
        repeat(3) { assertFalse(otro.alGps(20.1, it * 1_000L)) }
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
        assertFalse(control.alGps(5.0, 21_000))
        assertFalse(control.alGps(5.0, 22_000))
        assertTrue(control.alGps(5.0, 23_000))
    }
}
