package app.trombito.rallycopilot.motor.notas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PerfilVelocidadTest {
    private val tabla = TablaGrados()
    private val radioM = 100.0

    /** Velocidad teórica (m/s) de una curva de [radioM] con la tabla por defecto. */
    private val vTeorica = sqrt(tabla.mu * tabla.g * radioM)

    /**
     * Recorrido de arcos de 90° de [radioM] (alternando izquierda y derecha) separados por rectas de 100 m, con una
     * muestra cada 5 m. Cada elemento de [cocientes] es el cociente v real / teórica de un arco; en las rectas
     * se va a 30 m/s. Un cociente negativo deja el arco sin velocidad.
     */
    private fun recorrido(cocientes: List<Double>): List<MuestraVelocidad> {
        val metros = mutableListOf<Pair<Double, Double>>()
        val velocidades = mutableListOf<Double?>()
        var x = 0.0
        var y = 0.0
        var rumbo = Math.PI / 2

        fun punto(v: Double?) {
            metros += x to y
            velocidades += v
        }

        punto(RECTA_MS)
        cocientes.forEachIndexed { i, c ->
            repeat(RECTA_PASOS) {
                x += PASO * cos(rumbo)
                y += PASO * sin(rumbo)
                punto(RECTA_MS)
            }
            val signo = if (i % 2 == 0) 1.0 else -1.0
            val pasosArco = (radioM * Math.PI / 2 / PASO).toInt()
            val dRumbo = signo * (Math.PI / 2) / pasosArco
            val v = if (c < 0.0) null else c * vTeorica
            repeat(pasosArco) {
                x += PASO * cos(rumbo + dRumbo / 2)
                y += PASO * sin(rumbo + dRumbo / 2)
                rumbo += dRumbo
                punto(v)
            }
        }
        repeat(RECTA_PASOS) {
            x += PASO * cos(rumbo)
            y += PASO * sin(rumbo)
            punto(RECTA_MS)
        }
        val puntos = TrazasDePrueba.enPlano(metros)
        return puntos.mapIndexed { i, p -> MuestraVelocidad(p, velocidades[i]) }
    }

    @Test
    fun `arcos de radio y velocidad conocidos dan el factor esperado`() {
        val r = PerfilVelocidad.calibrar(listOf(recorrido(List(60) { 0.8 })), tabla)
        assertEquals(60, r.curvas)
        assertNotNull(r.factor)
        assertEquals(0.8, r.factor!!, 0.05)
    }

    @Test
    fun `el factor es el percentil 85 de los cocientes`() {
        // 51 curvas a 0,8 y 9 a 1,2: el percentil 85 de 60 es el cociente número 51.
        val cocientes = List(51) { 0.8 } + List(9) { 1.2 }
        val r = PerfilVelocidad.calibrar(listOf(recorrido(cocientes.shuffled(java.util.Random(7)))), tabla)
        assertEquals(60, r.curvas)
        assertEquals(0.8, r.factor!!, 0.05)
        val sinAltos = PerfilVelocidad.calibrar(listOf(recorrido(List(50) { 0.8 } + List(10) { 1.2 })), tabla)
        assertEquals(1.2, sinAltos.factor!!, 0.07)
    }

    @Test
    fun `junta las curvas de varios recorridos`() {
        val r = PerfilVelocidad.calibrar(listOf(recorrido(List(30) { 1.0 }), recorrido(List(30) { 1.0 })), tabla)
        assertEquals(60, r.curvas)
        assertEquals(1.0, r.factor!!, 0.05)
    }

    @Test
    fun `con menos de 50 curvas no hay factor`() {
        val r = PerfilVelocidad.calibrar(listOf(recorrido(List(49) { 0.9 })), tabla)
        assertNull(r.factor)
        assertEquals(49, r.curvas)
        assertNull(PerfilVelocidad.calibrar(emptyList(), tabla).factor)
    }

    @Test
    fun `con justo 50 curvas ya calibra`() {
        assertNotNull(PerfilVelocidad.calibrar(listOf(recorrido(List(50) { 0.9 })), tabla).factor)
    }

    @Test
    fun `las paradas no cuentan como curvas`() {
        // 20 arcos a 1 m/s (parado) y 49 buenos: solo valen 49.
        val r = PerfilVelocidad.calibrar(listOf(recorrido(List(49) { 0.9 } + List(20) { 1.0 / vTeorica })), tabla)
        assertEquals(49, r.curvas)
        assertNull(r.factor)
        val con50 = PerfilVelocidad.calibrar(listOf(recorrido(List(50) { 0.9 } + List(20) { 1.0 / vTeorica })), tabla)
        assertEquals(50, con50.curvas)
        assertEquals(0.9, con50.factor!!, 0.05)
    }

    @Test
    fun `las curvas sin velocidad se ignoran`() {
        val r = PerfilVelocidad.calibrar(listOf(recorrido(List(50) { 0.9 } + List(10) { -1.0 })), tabla)
        assertEquals(50, r.curvas)
        assertEquals(0.9, r.factor!!, 0.05)
    }

    @Test
    fun `el factor se acota entre 0,6 y 1,6`() {
        assertEquals(1.6, PerfilVelocidad.calibrar(listOf(recorrido(List(60) { 3.0 })), tabla).factor!!, 1e-9)
        assertEquals(0.6, PerfilVelocidad.calibrar(listOf(recorrido(List(60) { 0.3 })), tabla).factor!!, 1e-9)
    }

    @Test
    fun `es determinista`() {
        val datos = listOf(recorrido(List(60) { 0.7 + (it % 5) * 0.1 }))
        assertEquals(PerfilVelocidad.calibrar(datos, tabla), PerfilVelocidad.calibrar(datos, tabla))
    }

    @Test
    fun `usa mu y g de la tabla pero no su factor`() {
        val datos = listOf(recorrido(List(60) { 0.8 }))
        val base = PerfilVelocidad.calibrar(datos, tabla).factor!!
        assertEquals(base, PerfilVelocidad.calibrar(datos, tabla.copy(factor = 1.3)).factor!!, 1e-12)
        // Con la mitad de agarre la velocidad teórica baja en √2 y el cociente sube en √2.
        val menosAgarre = PerfilVelocidad.calibrar(datos, tabla.copy(mu = tabla.mu / 2.0)).factor!!
        assertEquals(base * sqrt(2.0), menosAgarre, 1e-9)
    }

    @Test
    fun `un recorrido sin curvas o casi vacio no falla`() {
        assertEquals(0, PerfilVelocidad.calibrar(listOf(recorrido(emptyList())), tabla).curvas)
        assertEquals(0, PerfilVelocidad.calibrar(listOf(emptyList()), tabla).curvas)
    }

    private companion object {
        const val PASO = 5.0
        const val RECTA_PASOS = 20
        const val RECTA_MS = 30.0
    }
}
