package app.trombito.rallycopilot.motor.notas

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TablaGradosTest {
    private val tabla = TablaGrados()

    /** Radio al que la curva se toma justo a [kmh] con la tabla por defecto. */
    private fun radioPara(kmh: Double): Double = (kmh / 3.6) * (kmh / 3.6) / (0.6 * 9.81)

    @Test
    fun `los bordes de cada umbral separan los grados 1 a 6`() {
        listOf(45.0, 65.0, 85.0, 110.0, 140.0).forEachIndexed { i, umbral ->
            val r = radioPara(umbral)
            assertEquals("justo por debajo de $umbral km/h", i + 1, tabla.grado(r * 0.999))
            assertEquals("justo por encima de $umbral km/h", i + 2, tabla.grado(r * 1.001))
        }
    }

    @Test
    fun `radios tipicos dan el grado esperado`() {
        assertEquals(1, tabla.grado(20.0))
        assertEquals(2, tabla.grado(50.0))
        assertEquals(3, tabla.grado(70.0))
        assertEquals(4, tabla.grado(100.0))
        assertEquals(5, tabla.grado(200.0))
        assertEquals(6, tabla.grado(300.0))
    }

    @Test
    fun `la velocidad de referencia manda y pasa a grado 6`() {
        // R = 100 m es v = 87,3 km/h: grado 4 salvo que el límite sea igual o menor.
        assertEquals(4, tabla.grado(100.0, 90.0))
        assertEquals(6, tabla.grado(100.0, 80.0))
        assertEquals(6, tabla.grado(100.0, tabla.velocidadCurvaKmh(100.0)))
        assertEquals(4, tabla.grado(100.0, null))
    }

    @Test
    fun `el factor escala la velocidad de curva`() {
        assertEquals(4, TablaGrados(factor = 1.0).grado(100.0))
        assertEquals(1, TablaGrados(factor = 0.5).grado(100.0))
        assertEquals(6, TablaGrados(factor = 1.7).grado(100.0))
    }

    @Test
    fun `horquilla con angulo desde 140 grados y radio central bajo 30 m`() {
        assertTrue(tabla.esHorquilla(140.0, 29.9))
        assertTrue(tabla.esHorquilla(263.0, 10.0))
        assertFalse(tabla.esHorquilla(139.9, 10.0))
        assertFalse(tabla.esHorquilla(180.0, 30.0))
    }

    @Test
    fun `se serializa a JSON y vuelve igual`() {
        val personalizada = TablaGrados(mu = 0.7, factor = 1.1, umbralesKmh = listOf(40.0, 60.0, 80.0, 100.0, 130.0))
        val json = Json { encodeDefaults = true }
        assertEquals(personalizada, json.decodeFromString<TablaGrados>(json.encodeToString(personalizada)))
        assertEquals(tabla, Json.decodeFromString<TablaGrados>("{}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `los umbrales deben ser cinco y crecientes`() {
        TablaGrados(umbralesKmh = listOf(45.0, 40.0, 85.0, 110.0, 140.0))
    }
}
