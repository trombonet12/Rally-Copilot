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
    fun `el grado de una curva depende solo de su angulo y no del limite de la via`() {
        // Curva de unos 90 grados: grado 4 (60 a 90) o 3 (90 a 120) segun la medida; el limite no lo cambia.
        val medida = RutaMedida(trazadoConCurva())

        fun grado(datos: ContextoNotas?) = generarHoja(medida, datos, null).notas.single().grado

        val sinVias = grado(null)
        assertEquals(4, sinVias)
        assertEquals(sinVias, grado(contextoDe(listOf(via(1, trazadoConCurva())))))
        assertEquals(sinVias, grado(contextoDe(listOf(via(1, trazadoConCurva(), maxspeed = "120")))))
    }
}
