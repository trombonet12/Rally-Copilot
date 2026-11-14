package app.trombito.rallycopilot.motor.ruta

import org.junit.Assert.assertEquals
import org.junit.Test

class RitmoViajeTest {
    /** Recorre [km] km a ritmo r (real = r x previsto, previsto 60 s por km) en tramos de 1 km. */
    private fun RitmoViaje.recorrer(
        km: Int,
        r: Double,
    ) = repeat(km) { registrar(1000.0, 60.0 * r, 60.0) }

    @Test
    fun `el peso crece con los km hechos`() {
        assertEquals(0.0, RitmoViaje.peso(0.0), 1e-9)
        assertEquals(0.0, RitmoViaje.peso(-3.0), 1e-9)
        assertEquals(0.1, RitmoViaje.peso(1.0), 1e-9)
        assertEquals(0.5, RitmoViaje.peso(5.0), 1e-9)
        assertEquals(0.65, RitmoViaje.peso(12.5), 1e-9)
        assertEquals(0.8, RitmoViaje.peso(20.0), 1e-9)
        assertEquals(0.8, RitmoViaje.peso(80.0), 1e-9)
    }

    @Test
    fun `sin datos el factor es 1`() {
        val r = RitmoViaje()
        assertEquals(1.0, r.ritmo, 1e-9)
        assertEquals(1.0, r.factor, 1e-9)
        assertEquals(0.0, r.kmHechos, 1e-9)
    }

    @Test
    fun `la correccion crece con los km a un ritmo constante de 1,4`() {
        val r = RitmoViaje()
        r.recorrer(1, 1.4)
        assertEquals(1.04, r.factor, 1e-9)
        r.recorrer(4, 1.4)
        assertEquals(1.2, r.factor, 1e-9)
        r.recorrer(15, 1.4)
        assertEquals(1.32, r.factor, 1e-9)
        assertEquals(20.0, r.kmHechos, 1e-9)
    }

    @Test
    fun `el ritmo se acota a 0,7 y 1,6`() {
        val lento = RitmoViaje()
        lento.recorrer(20, 3.0)
        assertEquals(1.6, lento.ritmo, 1e-9)
        assertEquals(1.48, lento.factor, 1e-9)
        val rapido = RitmoViaje()
        rapido.recorrer(20, 0.3)
        assertEquals(0.7, rapido.ritmo, 1e-9)
        assertEquals(0.76, rapido.factor, 1e-9)
    }

    @Test
    fun `una parada corta cuenta en el ritmo`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0)
        repeat(2) { r.registrar(0.0, 30.0, 0.0) } // semaforo de 60 s
        r.registrar(1000.0, 80.0, 80.0)
        assertEquals(220.0 / 160.0, r.ritmo, 1e-9)
    }

    @Test
    fun `una parada de exactamente 2 minutos todavia cuenta`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 200.0, 200.0)
        repeat(2) { r.registrar(0.0, 60.0, 0.0) }
        r.registrar(1000.0, 200.0, 200.0)
        assertEquals(520.0 / 400.0, r.ritmo, 1e-9)
    }

    @Test
    fun `una parada de mas de 2 minutos no cuenta`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0)
        repeat(3) { r.registrar(0.0, 60.0, 0.0) } // 180 s parado
        r.registrar(1000.0, 80.0, 80.0)
        assertEquals(1.0, r.ritmo, 1e-9)
        assertEquals(2.0, r.kmHechos, 1e-9)
    }

    @Test
    fun `una parada en curso aun no cuenta ni distorsiona`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0)
        repeat(5) { r.registrar(0.0, 60.0, 0.0) }
        assertEquals(1.0, r.ritmo, 1e-9)
    }

    @Test
    fun `las paradas separadas no se acumulan entre si`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 400.0, 400.0)
        repeat(3) { r.registrar(0.0, 40.0, 0.0) } // 120 s: cuenta
        r.registrar(1000.0, 400.0, 400.0)
        repeat(3) { r.registrar(0.0, 40.0, 0.0) } // otra de 120 s: cuenta, no 240 juntas
        r.registrar(1000.0, 400.0, 400.0)
        assertEquals((1200.0 + 240.0) / 1200.0, r.ritmo, 1e-9)
    }

    @Test
    fun `un atasco que avanza a 0,3 m por s durante 6 minutos retrasa la llegada`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0) // 12,5 m/s previstos
        repeat(360) { r.registrar(0.3, 1.0, 0.3 / 12.5) }
        assertEquals(1.6, r.ritmo, 1e-9) // 440 s reales frente a ~89 previstos: sube hasta el tope
        assertEquals(1.108, r.kmHechos, 0.01) // la última racha corta aún no se resolvió
    }

    @Test
    fun `el atasco a tirones con fijaciones espaciadas tambien cuenta`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0)
        repeat(12) { r.registrar(9.0, 30.0, 0.72) } // 9 m cada 30 s durante 6 minutos
        assertEquals(1.6, r.ritmo, 1e-9) // 440 s reales frente a ~89 previstos: sube hasta el tope
    }

    @Test
    fun `quieto 5 minutos en una gasolinera no cuenta`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0)
        repeat(10) { r.registrar(0.0, 30.0, 0.0) }
        r.registrar(1000.0, 80.0, 80.0)
        assertEquals(1.0, r.ritmo, 1e-9)
        assertEquals(2.0, r.kmHechos, 1e-9)
    }

    @Test
    fun `el ruido del GPS parado no lo convierte en marcha`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 80.0, 80.0)
        repeat(5) { r.registrar(1.0, 60.0, 0.0) } // 1 m de bailoteo por minuto, 5 min
        r.registrar(1000.0, 80.0, 80.0)
        assertEquals(1.0, r.ritmo, 1e-9)
    }

    @Test
    fun `un intervalo sin tiempo se ignora`() {
        val r = RitmoViaje()
        r.registrar(1000.0, 0.0, 60.0)
        assertEquals(0.0, r.kmHechos, 1e-9)
    }
}
