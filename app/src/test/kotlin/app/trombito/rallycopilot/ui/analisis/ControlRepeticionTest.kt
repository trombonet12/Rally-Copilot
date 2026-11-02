package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.analisis.PuntoSerie
import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRepeticionTest {
    private val duracion = 100_000L

    @Test
    fun `sin reproducir no avanza`() {
        val estado = EstadoRepeticion(tMs = 10_000L)
        assertEquals(estado, avanzarRepeticion(estado, 50L, duracion))
        assertNull(avanzarRepeticion(EstadoRepeticion(reproduciendo = true), 50L, duracion).tMs)
    }

    @Test
    fun `avanza el paso por el factor de la velocidad`() {
        val base = EstadoRepeticion(tMs = 10_000L, reproduciendo = true)
        assertEquals(10_050L, avanzarRepeticion(base, 50L, duracion).tMs)
        assertEquals(10_200L, avanzarRepeticion(base.copy(velocidad = VelocidadRepeticion.X4), 50L, duracion).tMs)
        assertEquals(10_800L, avanzarRepeticion(base.copy(velocidad = VelocidadRepeticion.X16), 50L, duracion).tMs)
    }

    @Test
    fun `al llegar al final se detiene en la duracion exacta`() {
        val casi = EstadoRepeticion(tMs = 99_900L, reproduciendo = true, velocidad = VelocidadRepeticion.X4)
        val fin = avanzarRepeticion(casi, 50L, duracion)
        assertEquals(duracion, fin.tMs)
        assertFalse(fin.reproduciendo)
        assertEquals(VelocidadRepeticion.X4, fin.velocidad)
    }

    @Test
    fun `alternar arranca desde la salida si no hay posicion o se esta al final`() {
        assertEquals(EstadoRepeticion(tMs = 0L, reproduciendo = true), alternarRepeticion(EstadoRepeticion(), duracion))
        val alFinal = EstadoRepeticion(tMs = duracion)
        assertEquals(0L, alternarRepeticion(alFinal, duracion).tMs)
        assertTrue(alternarRepeticion(alFinal, duracion).reproduciendo)
    }

    @Test
    fun `alternar reanuda donde se dejo y pausa conservando la posicion`() {
        val pausada = EstadoRepeticion(tMs = 40_000L, velocidad = VelocidadRepeticion.X16)
        val enMarcha = alternarRepeticion(pausada, duracion)
        assertEquals(40_000L, enMarcha.tMs)
        assertTrue(enMarcha.reproduciendo)
        val otra = alternarRepeticion(enMarcha, duracion)
        assertEquals(40_000L, otra.tMs)
        assertFalse(otra.reproduciendo)
        assertEquals(VelocidadRepeticion.X16, otra.velocidad)
    }

    @Test
    fun `las velocidades son por 1, 4 y 16`() {
        assertEquals(listOf(1, 4, 16), VelocidadRepeticion.entries.map { it.factor })
    }

    @Test
    fun `del tiempo a la distancia se interpola sobre la curva de la pasada`() {
        val marcas = listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(1_000.0, 50_000L), MarcaTiempo(3_000.0, 100_000L))
        assertEquals(0.0, distanciaEnTiempo(marcas, 0L), 1e-9)
        assertEquals(500.0, distanciaEnTiempo(marcas, 25_000L), 1e-9)
        assertEquals(2_000.0, distanciaEnTiempo(marcas, 75_000L), 1e-9)
        assertEquals(3_000.0, distanciaEnTiempo(marcas, 500_000L), 0.0)
        assertEquals(0.0, distanciaEnTiempo(marcas, -5L), 0.0)
        assertEquals(0.0, distanciaEnTiempo(emptyList(), 10L), 0.0)
    }

    @Test
    fun `parado la distancia no cambia`() {
        val marcas =
            listOf(
                MarcaTiempo(0.0, 0L),
                MarcaTiempo(500.0, 10_000L),
                MarcaTiempo(500.0, 20_000L),
                MarcaTiempo(900.0, 30_000L),
            )
        assertEquals(500.0, distanciaEnTiempo(marcas, 15_000L), 1e-9)
    }

    @Test
    fun `la lectura del cursor junta velocidad, comparada y delta en la misma distancia`() {
        val velocidad = listOf(PuntoSerie(0.0, 40.0), PuntoSerie(1_000.0, 100.0))
        val comparada = listOf(PuntoSerie(0.0, 30.0), PuntoSerie(1_000.0, 90.0))
        val delta = listOf(PuntoSerie(0.0, 0.0), PuntoSerie(1_000.0, -2_000.0))
        val lectura = lecturaCursor(500.0, velocidad, comparada, delta)!!
        assertEquals(500.0, lectura.distanciaM, 0.0)
        assertEquals(70.0, lectura.velocidadKmh!!, 1e-9)
        assertEquals(60.0, lectura.comparadaKmh!!, 1e-9)
        assertEquals(-1_000L, lectura.deltaMs)
    }

    @Test
    fun `sin cursor no hay lectura y sin comparada faltan sus valores`() {
        val velocidad = listOf(PuntoSerie(0.0, 40.0), PuntoSerie(1_000.0, 100.0))
        assertNull(lecturaCursor(null, velocidad, emptyList(), emptyList()))
        val sola = lecturaCursor(1_000.0, velocidad, emptyList(), emptyList())!!
        assertEquals(100.0, sola.velocidadKmh!!, 0.0)
        assertNull(sola.comparadaKmh)
        assertNull(sola.deltaMs)
    }
}
