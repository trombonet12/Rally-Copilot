package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneracionHojaTest {
    private fun viaDeClase(
        clase: String,
        maxspeed: String? = null,
    ) = Via(1, clase, maxspeed, null, null, SentidoUnico.NO, null, null, emptyList())

    @Test
    fun `la velocidad por clase sigue el reglamento`() {
        val esperado =
            mapOf(
                "motorway" to 120.0,
                "motorway_link" to 120.0,
                "trunk" to 90.0,
                "primary" to 90.0,
                "secondary" to 90.0,
                "tertiary" to 90.0,
                "tertiary_link" to 90.0,
                "unclassified" to 70.0,
                "residential" to 50.0,
                "living_street" to 20.0,
            )
        esperado.forEach { (clase, kmh) -> assertEquals(clase, kmh, VelocidadReferencia.porClase(clase)) }
        assertNull(VelocidadReferencia.porClase("track"))
        assertNull(VelocidadReferencia.porClase("service"))
    }

    @Test
    fun `el maxspeed de la via manda sobre la clase`() {
        assertEquals(60.0, VelocidadReferencia.de(viaDeClase("primary", "60"), adelante = true))
        assertEquals(90.0, VelocidadReferencia.de(viaDeClase("primary"), adelante = true))
        assertEquals(90.0, VelocidadReferencia.de(viaDeClase("primary", "ES:urban"), adelante = true))
        assertNull(VelocidadReferencia.de(viaDeClase("track"), adelante = true))
    }

    @Test
    fun `el grado de una curva es relativo al limite de la via o al de su clase`() {
        // 90 grados de radio 60 m: v = 67,6 km/h.
        val medida = RutaMedida(trazadoConCurva())

        fun grado(datos: ContextoNotas?) = generarHoja(medida, datos, null).notas.single().grado

        // Sin vías, umbrales absolutos: 67,6 km/h es grado 3.
        assertEquals(3, grado(null))
        // Primary sin maxspeed: 90 km/h por defecto, v / vRef = 0,75: grado 4.
        assertEquals(4, grado(contextoDe(listOf(via(1, trazadoConCurva())))))
        // Con maxspeed 120: v / vRef = 0,56: grado 2.
        assertEquals(2, grado(contextoDe(listOf(via(1, trazadoConCurva(), maxspeed = "120")))))
    }
}
