package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.TrazasDePrueba
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FantasmaTest {
    private val recta = TrazasDePrueba.recta(4000.0)
    private val tramoRecto = Tramo("recta", recta, recta.longitudMetros()) // meta a 3990 m

    /** Pasada de [tramo] con una marca cada 500 m y la meta, con el tiempo dado por [tiempoMs] (de la distancia). */
    private fun pasada(
        tramo: Tramo,
        tiempoMs: (Double) -> Long,
    ): Pasada {
        val intermedias = (0..(tramo.metaM / 500.0).toInt()).map { it * 500.0 }.filter { it < tramo.metaM }
        val marcas = (intermedias + tramo.metaM).map { MarcaTiempo(it, tiempoMs(it)) }
        return Pasada(tramo.id, marcas, marcas.last().tMs)
    }

    private val constante = pasada(tramoRecto) { Math.round(it / 20.0 * 1000.0) } // 20 m/s

    @Test
    fun `a velocidad constante la distancia crece linealmente con el tiempo`() {
        val fantasma = Fantasma(tramoRecto, constante)
        assertCerca(0.0, fantasma.distanciaEn(0)!!, 0.01)
        assertCerca(20.0, fantasma.distanciaEn(1_000)!!, 0.01)
        assertCerca(1_000.0, fantasma.distanciaEn(50_000)!!, 0.01)
        assertCerca(1_234.5, fantasma.distanciaEn(61_725)!!, 0.01) // entre marcas
    }

    @Test
    fun `con velocidad variable interpola dentro de cada tramo de marcas`() {
        // 10 m/s hasta 1000 m (100 s), luego 40 m/s.
        val variable =
            pasada(tramoRecto) {
                if (it <= 1_000.0) Math.round(it * 100.0) else Math.round(100_000 + (it - 1_000.0) * 25.0)
            }
        val fantasma = Fantasma(tramoRecto, variable)
        assertCerca(500.0, fantasma.distanciaEn(50_000)!!, 0.01)
        assertCerca(1_000.0, fantasma.distanciaEn(100_000)!!, 0.01)
        assertCerca(1_400.0, fantasma.distanciaEn(110_000)!!, 0.01)
        assertCerca(2_000.0, fantasma.distanciaEn(125_000)!!, 0.01)
    }

    @Test
    fun `es la inversa de tiempoEn de la pasada`() {
        val acelerando = pasada(tramoRecto) { Math.round(it * it / 100.0) }
        val fantasma = Fantasma(tramoRecto, acelerando)
        for (d in listOf(0.0, 137.0, 500.0, 1_999.0, 3_000.0, 3_990.0)) {
            assertCerca(d, fantasma.distanciaEn(Math.round(acelerando.tiempoEn(d)!!))!!, 5.0)
        }
    }

    @Test
    fun `antes de salir no hay fantasma y tras la meta se queda en la meta`() {
        val fantasma = Fantasma(tramoRecto, constante)
        assertNull(fantasma.distanciaEn(-1))
        assertNull(fantasma.posicionEn(-5_000))
        val total = constante.totalMs
        assertCerca(tramoRecto.metaM, fantasma.distanciaEn(total)!!, 0.001)
        assertCerca(tramoRecto.metaM, fantasma.distanciaEn(total + 60_000)!!, 0.001)
        assertCerca(tramoRecto.metaM, fantasma.posicionEn(total + 60_000)!!.distanciaM, 0.001)
    }

    @Test
    fun `parado en una marca el fantasma no avanza`() {
        val marcas =
            listOf(
                MarcaTiempo(0.0, 0),
                MarcaTiempo(100.0, 10_000),
                MarcaTiempo(100.0, 20_000),
                MarcaTiempo(3_990.0, 100_000),
            )
        val fantasma = Fantasma(tramoRecto, Pasada("recta", marcas, 100_000))
        assertCerca(100.0, fantasma.distanciaEn(15_000)!!, 0.001)
        assertCerca(100.0, fantasma.distanciaEn(20_000)!!, 0.001)
        assertCerca(100.0 + 3_890.0 * 10.0 / 80.0, fantasma.distanciaEn(30_000)!!, 0.01)
    }

    @Test
    fun `la posicion cae sobre la traza con el rumbo de la recta`() {
        val fantasma = Fantasma(tramoRecto, constante)
        val posicion = fantasma.posicionEn(50_000)!!
        assertCerca(1_000.0, posicion.distanciaM, 0.01)
        assertCerca(0.0, posicion.rumboGrados, 0.5) // hacia el norte
        assertCerca(1_000.0, recta.first().distanciaA(posicion.punto), 1.0)
        assertCerca(recta.first().lon, posicion.punto.lon, 0.0000001)
    }

    @Test
    fun `el rumbo sigue la traza en las curvas`() {
        val traza = TrazasDePrueba.enPlano(listOf(0.0 to 0.0, 0.0 to 1_000.0, 1_000.0 to 1_000.0, 1_000.0 to 0.0))
        val tramo = Tramo("ele", traza, traza.longitudMetros()) // norte, este, sur
        val fantasma = Fantasma(tramo, pasada(tramo) { Math.round(it / 10.0 * 1000.0) })
        assertCerca(0.0, fantasma.posicionEn(50_000)!!.rumboGrados, 0.5) // 500 m: norte
        assertCerca(90.0, fantasma.posicionEn(150_000)!!.rumboGrados, 0.5) // 1500 m: este
        assertCerca(180.0, fantasma.posicionEn(250_000)!!.rumboGrados, 0.5) // 2500 m: sur
        val enVertice = fantasma.posicionEn(100_000)!! // 1000 m, en el vértice: media de ambos rumbos
        assertCerca(45.0, enVertice.rumboGrados, 1.0)
        assertCerca(0.0, enVertice.punto.distanciaA(traza[1]), 1.0)
    }

    @Test
    fun `solo vale una pasada del propio tramo y con marcas`() {
        val otra = Pasada("otro", constante.tiempos, constante.totalMs)
        assertThrows(IllegalArgumentException::class.java) { Fantasma(tramoRecto, otra) }
        assertNotNull(Fantasma(tramoRecto, constante))
        assertThrows(IllegalArgumentException::class.java) { Fantasma(tramoRecto, Pasada("recta", emptyList(), 0)) }
    }
}
