package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Polyline6Test {
    // Tramo de Palma (Mallorca); la cadena la generó una implementación independiente de la especificación.
    private val tramo =
        listOf(
            PuntoGeo(39.569815, 2.650123),
            PuntoGeo(39.570501, 2.651987),
            PuntoGeo(39.571244, 2.653310),
            PuntoGeo(39.572001, 2.652004),
        )
    private val cadena = "mxcnjAu_w`D{i@osBmm@uqAin@rpA"

    @Test
    fun `codifica un tramo real igual que la referencia`() {
        assertEquals(cadena, Polyline6.codificar(tramo))
    }

    @Test
    fun `decodifica un tramo real con precision de micrograd`() {
        val puntos = Polyline6.decodificar(cadena)
        assertEquals(tramo.size, puntos.size)
        puntos.zip(tramo).forEach { (a, b) ->
            assertEquals(b.lat, a.lat, 1e-9)
            assertEquals(b.lon, a.lon, 1e-9)
        }
    }

    @Test
    fun `ida y vuelta con coordenadas negativas y saltos grandes`() {
        val original =
            listOf(
                PuntoGeo(-33.868820, -151.209296),
                PuntoGeo(39.5, 2.9),
                PuntoGeo(39.5, 2.9),
                PuntoGeo(-0.000001, 0.000001),
            )
        val vuelta = Polyline6.decodificar(Polyline6.codificar(original))
        assertEquals(original.size, vuelta.size)
        original.zip(vuelta).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-9)
            assertEquals(a.lon, b.lon, 1e-9)
        }
    }

    @Test
    fun `cadena vacia da lista vacia`() {
        assertTrue(Polyline6.decodificar("").isEmpty())
        assertEquals("", Polyline6.codificar(emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cadena truncada es un error`() {
        Polyline6.decodificar(cadena.dropLast(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `caracter fuera de rango es un error`() {
        Polyline6.decodificar("mxcn jA")
    }
}
