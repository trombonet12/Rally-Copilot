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
    private fun radioPara(
        kmh: Double,
        mu: Double = 0.6,
    ): Double = (kmh / 3.6) * (kmh / 3.6) / (mu * 9.81)

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
    fun `con velocidad de referencia el grado es relativo a ella`() {
        // Radio al que la velocidad de curva es r veces la de referencia (factor 1).
        val vRef = 90.0
        listOf(0.45, 0.60, 0.72, 0.86, 1.0).forEachIndexed { i, banda ->
            val r = radioPara(vRef * banda, 0.6)
            assertEquals("justo por debajo de $banda", i + 1, tabla.grado(r * 0.99, vRef))
            // El 6 exige además el radio de confort: a 90 km/h son 182 m, y la banda 1,0 cae en 106 m.
            val esperado = if (i == 4) 5 else i + 2
            assertEquals("justo por encima de $banda", esperado, tabla.grado(r * 1.01, vRef))
        }
    }

    @Test
    fun `la misma curva es mas cerrada cuanto menor es la velocidad de referencia`() {
        // R = 100 m es v = 87,3 km/h.
        assertEquals(5, tabla.grado(100.0, 90.0))
        assertEquals(4, tabla.grado(100.0, 110.0))
        assertEquals(3, tabla.grado(100.0, 130.0))
        assertEquals(2, tabla.grado(100.0, 180.0))
        assertEquals(1, tabla.grado(100.0, 250.0))
        assertEquals(4, tabla.grado(100.0, null))
    }

    @Test
    fun `un seis exige el radio de confort y si no es cinco`() {
        // BLOCKER: a 50 km/h, R = 40 m (v = 55 km/h > vRef) no es seis; el radio de confort son 56,2 m.
        assertEquals(5, tabla.grado(40.0, 50.0))
        assertEquals(56.2, tabla.radioMinimoSeisM(50.0), 0.05)
        assertEquals(5, tabla.grado(56.0, 50.0))
        assertEquals(6, tabla.grado(56.5, 50.0))
        assertEquals(6, tabla.grado(300.0, 50.0))
        // A 90 km/h: 182 m.
        assertEquals(5, tabla.grado(150.0, 90.0))
        assertEquals(6, tabla.grado(300.0, 90.0))
    }

    @Test
    fun `el factor se aplica a la velocidad de curva tambien con referencia`() {
        // BLOCKER: con factor 0,7 una R = 60 m (v = 47,4 km/h) no es grado uno en ningun caso.
        val lenta = TablaGrados(factor = 0.7)
        assertEquals(2, lenta.grado(60.0))
        assertEquals(2, lenta.grado(60.0, 90.0))
        assertEquals(5, lenta.grado(60.0, 50.0))
        assertEquals(4, lenta.grado(60.0, 60.0))
        assertEquals(1, TablaGrados(factor = 0.7).grado(60.0, 150.0))
    }

    @Test
    fun `una velocidad de referencia no positiva se ignora`() {
        assertEquals(tabla.grado(100.0), tabla.grado(100.0, 0.0))
        assertEquals(tabla.grado(100.0), tabla.grado(100.0, -5.0))
    }

    @Test
    fun `el factor escala la velocidad de curva`() {
        assertEquals(4, TablaGrados(factor = 1.0).grado(100.0))
        assertEquals(1, TablaGrados(factor = 0.5).grado(100.0))
        assertEquals(6, TablaGrados(factor = 1.7).grado(100.0))
    }

    @Test
    fun `el grado depende solo del angulo y en el limite exacto gana el numero mayor`() {
        val esperado =
            mapOf(
                9.0 to null,
                9.99 to null,
                10.0 to 6,
                29.0 to 6,
                30.0 to 6,
                31.0 to 5,
                59.0 to 5,
                60.0 to 5,
                61.0 to 4,
                90.0 to 4,
                91.0 to 3,
                120.0 to 3,
                121.0 to 2,
                150.0 to 2,
                151.0 to 1,
                180.0 to 1,
            )
        esperado.forEach { (angulo, grado) -> assertEquals("$angulo grados", grado, tabla.gradoPorAngulo(angulo)) }
    }

    @Test
    fun `mas de 180 grados es horquilla y 180 todavia es un uno`() {
        assertTrue(tabla.esHorquilla(181.0))
        assertTrue(tabla.esHorquilla(263.0))
        assertFalse(tabla.esHorquilla(180.0))
        assertFalse(tabla.esHorquilla(90.0))
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

    @Test(expected = IllegalArgumentException::class)
    fun `los limites de angulo deben ser cinco y crecientes`() {
        TablaGrados(limitesAnguloGrados = listOf(30.0, 60.0, 60.0, 120.0, 150.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `las bandas relativas deben ser cinco y crecientes`() {
        TablaGrados(bandasRelativas = listOf(0.45, 0.60, 0.60, 0.86, 1.0))
    }
}
