package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

class CoberturaTest {
    private val grado = 111_194.9

    /** Puntos hacia el norte cada [paso] m entre [desdeM] y [hastaM], a [esteM] metros al este de la línea base. */
    private fun linea(
        desdeM: Double,
        hastaM: Double,
        esteM: Double = 0.0,
        paso: Double = 10.0,
    ): List<PuntoGeo> =
        generateSequence(desdeM) { it + paso }
            .takeWhile { it <= hastaM }
            .map { PuntoGeo(39.6 + it / grado, 2.9 + esteM / (grado * cos(Math.toRadians(39.6)))) }
            .toList()

    private val tramo = linea(0.0, 1000.0)

    @Test
    fun `un recorrido sobre el tramo lo cubre entero`() {
        assertEquals(1.0, Cobertura.fraccion(linea(0.0, 1000.0, paso = 7.0), tramo), 0.001)
    }

    @Test
    fun `un recorrido con 100 m de mas por un extremo cubre el 91 por ciento`() {
        val fraccion = Cobertura.fraccion(linea(-100.0, 1000.0), tramo)
        assertEquals(1000.0 / 1100.0, fraccion, 0.03)
    }

    @Test
    fun `un recorrido paralelo a 100 m no lo cubre y uno a 10 m si`() {
        assertEquals(0.0, Cobertura.fraccion(linea(0.0, 1000.0, esteM = 100.0), tramo), 0.0)
        assertEquals(1.0, Cobertura.fraccion(linea(0.0, 1000.0, esteM = 10.0), tramo), 0.001)
    }

    @Test
    fun `sin recorrido o sin traza no hay cobertura`() {
        assertEquals(0.0, Cobertura.fraccion(emptyList(), tramo), 0.0)
        assertEquals(0.0, Cobertura.fraccion(tramo, emptyList()), 0.0)
    }
}
