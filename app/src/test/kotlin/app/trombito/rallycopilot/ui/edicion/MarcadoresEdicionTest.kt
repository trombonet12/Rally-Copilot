package app.trombito.rallycopilot.ui.edicion

import androidx.compose.ui.geometry.Offset
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.tramos.Tramo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarcadoresEdicionTest {
    private val traza = List(21) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
    private val tramo = Tramo("t1", traza, traza.zipWithNext { a, b -> a.distanciaA(b) }.sum())
    private val metros = TrazaMetros(traza)

    @Test
    fun `el texto corto lleva el lado y el grado y H en las horquillas`() {
        assertEquals("D4", textoCortoNota(Nota(0.0, 10.0, Direccion.DERECHA, 4)))
        assertEquals("I1", textoCortoNota(Nota(0.0, 10.0, Direccion.IZQUIERDA, 1)))
        assertEquals("IH", textoCortoNota(Nota(0.0, 10.0, Direccion.IZQUIERDA, null)))
        assertEquals("DH", textoCortoNota(Nota(0.0, 10.0, Direccion.DERECHA, null)))
    }

    @Test
    fun `los marcadores son notas, cuidados, marcas y cortes, cada uno con su tipo y texto`() {
        val normal = Nota(300.0, 340.0, Direccion.DERECHA, 3)
        val cuidado = Nota(600.0, 640.0, Direccion.IZQUIERDA, null, cuidado = true)
        val hoja = HojaNotas(listOf(ElementoHoja.DeNota(normal), ElementoHoja.DeNota(cuidado)))
        val marca = MarcaConPosicion(Marca(900.0, 1_000L), null)
        val datos = TramoEditable(tramo, hoja, listOf(marca), 0)

        val marcadores = marcadoresDe(datos, listOf(500.0, 1_200.0), metros)

        assertEquals(
            listOf(
                TipoMarcador.NOTA to "D3",
                TipoMarcador.CUIDADO to "IH",
                TipoMarcador.MARCA to "M",
                TipoMarcador.CORTE to "C1",
                TipoMarcador.CORTE to "C2",
            ),
            marcadores.map { it.tipo to it.texto },
        )
        // La nota va a mitad de su curva
        assertEquals(320.0, marcadores.first().posicionM, 1e-9)
        assertEquals(idMarcadorNota(normal), marcadores.first().id)
        assertEquals(marcadores.size, marcadores.map { it.id }.toSet().size)
    }

    @Test
    fun `una marca con etiqueta radar es un marcador de radar`() {
        val radar = MarcaConPosicion(Marca(900.0, 1_000L, etiqueta = Marca.ETIQUETA_RADAR), null)
        val datos = TramoEditable(tramo, HojaNotas(emptyList()), listOf(radar), 0)

        val marcadores = marcadoresDe(datos, emptyList(), metros)

        assertEquals(listOf(TipoMarcador.RADAR to "R"), marcadores.map { it.tipo to it.texto })
    }

    @Test
    fun `los metros se convierten en posicion y la posicion en metros`() {
        val medio = metros.puntoEn(metros.longitudM / 2)
        val proyeccion = checkNotNull(metros.proyectar(PuntoGeo(medio.lat, medio.lon + 0.00003)))

        assertEquals(metros.longitudM / 2, proyeccion.metros, 1.0)
        assertTrue("a unos 2,6 m del trazo: ${proyeccion.distanciaM}", proyeccion.distanciaM in 2.0..3.5)
        assertEquals(traza.first(), metros.puntoEn(-50.0))
        assertEquals(traza.last(), metros.puntoEn(1e9))
        assertNull(TrazaMetros(listOf(traza.first())).proyectar(medio))
    }

    @Test
    fun `una proyeccion mas alla de los extremos se queda en el extremo`() {
        val antes = checkNotNull(metros.proyectar(PuntoGeo(traza.first().lat - 0.01, traza.first().lon)))
        val despues = checkNotNull(metros.proyectar(PuntoGeo(traza.last().lat + 0.01, traza.last().lon)))

        assertEquals(0.0, antes.metros, 1e-6)
        assertEquals(metros.longitudM, despues.metros, 1e-6)
        assertTrue(antes.distanciaM > 1_000.0)
    }

    @Test
    fun `el marcador mas cercano dentro del radio es el que se elige`() {
        fun marcador(id: String) = MarcadorEdicion(id, TipoMarcador.NOTA, 0.0, "D1", traza.first())
        val lejos = marcador("a") to Offset(100f, 0f)
        val cerca = marcador("b") to Offset(10f, 0f)
        val pegado = marcador("c") to Offset(4f, 3f)

        assertEquals("c", marcadorCercano(listOf(lejos, cerca, pegado), Offset.Zero, 20f)?.id)
        assertEquals("b", marcadorCercano(listOf(lejos, cerca), Offset.Zero, 20f)?.id)
        assertNull(marcadorCercano(listOf(lejos), Offset.Zero, 20f))
        assertNotNull(marcadorCercano(listOf(lejos), Offset(95f, 0f), 20f))
    }
}
