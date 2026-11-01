package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Test

class FrasesParadaTest {
    private fun m(
        tipo: TipoManiobra,
        inicio: Int,
        vias: List<String> = emptyList(),
    ) = Maniobra(tipo, tipo.name, vias, inicio, inicio, 100.0, 10.0, null)

    private fun ruta(
        maniobras: List<Maniobra>,
        paradas: List<Int>,
    ) = Ruta(List(8) { PuntoGeo(39.6, 2.9 + it * 0.001) }, maniobras, 700.0, 70.0, paradas)

    @Test
    fun `dice la siguiente maniobra tras la parada, sin contar la salida del tramo`() {
        val r =
            ruta(
                listOf(
                    m(TipoManiobra.SALIDA, 0),
                    m(TipoManiobra.DESTINO, 3),
                    m(TipoManiobra.SALIDA, 3),
                    m(TipoManiobra.DERECHA, 5, listOf("Ma-10")),
                    m(TipoManiobra.DESTINO, 7),
                ),
                listOf(3),
            )
        assertEquals("parada alcanzada, siguiente cruce, derecha, Ma-10", FrasesParada.alcanzada(r, 0))
    }

    @Test
    fun `si lo siguiente es el destino lo dice, y si es otra parada dice parada`() {
        val r =
            ruta(
                listOf(
                    m(TipoManiobra.SALIDA, 0),
                    m(TipoManiobra.DESTINO, 2),
                    m(TipoManiobra.SALIDA, 2),
                    m(TipoManiobra.DESTINO, 4),
                    m(TipoManiobra.SALIDA, 4),
                    m(TipoManiobra.DESTINO, 7),
                ),
                listOf(2, 4),
            )
        assertEquals("parada alcanzada, siguiente parada", FrasesParada.alcanzada(r, 0))
        assertEquals("parada alcanzada, siguiente destino", FrasesParada.alcanzada(r, 1))
    }

    @Test
    fun `sin parada o sin maniobras por delante solo confirma la parada`() {
        val r = ruta(listOf(m(TipoManiobra.SALIDA, 0)), listOf(3))
        assertEquals("parada alcanzada", FrasesParada.alcanzada(r, 0))
        assertEquals("parada alcanzada", FrasesParada.alcanzada(r, 5))
    }
}
