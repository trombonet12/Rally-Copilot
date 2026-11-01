package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvisosManiobraTest {
    private fun maniobra(
        tipo: TipoManiobra = TipoManiobra.DERECHA,
        vias: List<String> = listOf("Ma-10"),
    ) = Maniobra(tipo, "instruccion", vias, 0, 0, 100.0, 10.0, null)

    private val avisos =
        AvisosManiobra(
            maniobras =
                listOf(
                    maniobra(TipoManiobra.SALIDA, emptyList()),
                    maniobra(),
                    maniobra(TipoManiobra.IZQUIERDA, emptyList()),
                ),
            posicionesM = listOf(0.0, 2000.0, 5000.0),
        )

    private fun candidatas(
        posicionM: Double,
        vMs: Double,
    ) = avisos.candidatas(posicionM, vMs)

    @Test
    fun `lejos de la maniobra no hay nada`() {
        assertEquals(emptyList<Frase>(), candidatas(1000.0, 20.0))
    }

    @Test
    fun `dentro de la ventana de preaviso suena el preaviso con la distancia redondeada a cien`() {
        // 20 m/s: preaviso a 300 m. A 280 m se redondea a trescientos.
        val frase = candidatas(1720.0, 20.0).single()
        assertEquals("a trescientos, cruce, derecha, Ma-10", frase.texto)
        assertEquals("maniobra-1-preaviso", frase.id)
        assertEquals(TipoFrase.MANIOBRA, frase.tipo)
        assertEquals(2000.0, frase.puntoM!!, 1e-9)
    }

    @Test
    fun `la ventana de preaviso crece con la velocidad`() {
        // 40 m/s * 15 s = 600 m: a 520 m ya suena, a quinientos.
        assertEquals("a quinientos, cruce, derecha, Ma-10", candidatas(1480.0, 40.0).single().texto)
        // Y a esa misma distancia a 20 m/s todavia no.
        assertEquals(emptyList<Frase>(), candidatas(1480.0, 20.0))
    }

    @Test
    fun `la ventana de preaviso se acota entre trescientos y mil metros`() {
        // 1 m/s daria 15 m: se sube a 300.
        assertEquals(1, candidatas(1710.0, 1.0).size)
        assertEquals(emptyList<Frase>(), candidatas(1690.0, 1.0))
        // 100 m/s daria 1500 m: se baja a 1000, que se canta «mil».
        assertEquals("a mil, cruce, derecha, Ma-10", candidatas(1000.0, 100.0).single().texto)
        assertEquals(emptyList<Frase>(), candidatas(990.0, 100.0))
    }

    @Test
    fun `el aviso inminente usa la frase de la maniobra a cinco segundos`() {
        // 20 m/s * 5 s = 100 m.
        val frase = candidatas(1920.0, 20.0).single()
        assertEquals("cruce, derecha, Ma-10", frase.texto)
        assertEquals("maniobra-1-inminente", frase.id)
        assertEquals(TipoFrase.MANIOBRA, frase.tipo)
        assertEquals(2000.0, frase.puntoM!!, 1e-9)
    }

    @Test
    fun `justo en el limite inminente solo suena el inminente y justo fuera el preaviso`() {
        assertEquals("maniobra-1-inminente", candidatas(1900.0, 20.0).single().id)
        assertEquals("maniobra-1-preaviso", candidatas(1899.0, 20.0).single().id)
    }

    @Test
    fun `el aviso inminente se acota entre cuarenta y doscientos metros`() {
        // 2 m/s daria 10 m: se sube a 40.
        assertEquals("maniobra-1-inminente", candidatas(1960.0, 2.0).single().id)
        assertEquals("maniobra-1-preaviso", candidatas(1950.0, 2.0).single().id)
        // 60 m/s daria 300 m: se baja a 200.
        assertEquals("maniobra-1-inminente", candidatas(1800.0, 60.0).single().id)
        assertEquals("maniobra-1-preaviso", candidatas(1799.0, 60.0).single().id)
    }

    @Test
    fun `un preaviso que se redondearia a menos de cien metros no suena`() {
        // 44 m con minimo inminente 40: fuera del inminente pero 44 m redondea a cero.
        assertEquals(emptyList<Frase>(), candidatas(1956.0, 1.0))
    }

    @Test
    fun `la maniobra ya pasada no genera frases`() {
        assertEquals(emptyList<Frase>(), candidatas(2000.5, 20.0).filter { it.puntoM == 2000.0 })
    }

    @Test
    fun `la salida nunca se anuncia`() {
        assertTrue(candidatas(0.0, 20.0).none { it.id.startsWith("maniobra-0-") })
    }

    @Test
    fun `cada maniobra tiene sus propias ids estables`() {
        // A 20 m/s en 4700 m: la segunda maniobra esta a 300 m (preaviso) y la primera ya pasó.
        val frase = candidatas(4700.0, 20.0).single()
        assertEquals("maniobra-2-preaviso", frase.id)
        assertEquals("a trescientos, cruce, izquierda", frase.texto)
        assertEquals(frase.id, candidatas(4710.0, 20.0).single().id)
    }

    @Test
    fun `las maniobras sin tipo conocido cantan la instruccion de Valhalla`() {
        val otras = AvisosManiobra(listOf(maniobra(TipoManiobra.OTRA, emptyList())), listOf(1000.0))
        assertEquals("instruccion", otras.candidatas(950.0, 20.0).single().texto)
    }

    @Test
    fun `maniobras y posiciones deben tener el mismo tamano`() {
        val resultado = runCatching { AvisosManiobra(listOf(maniobra()), emptyList()) }
        assertTrue(resultado.exceptionOrNull() is IllegalArgumentException)
    }
}
