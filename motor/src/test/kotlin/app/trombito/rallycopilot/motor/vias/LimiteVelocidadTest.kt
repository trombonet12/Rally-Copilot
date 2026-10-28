package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimiteVelocidadTest {
    @Test
    fun `tabla de conversion de maxspeed a kmh`() {
        val esperado =
            mapOf(
                "50" to 50,
                " 120 " to 120,
                "5" to 5,
                "150" to 150,
                "50 mph" to 80,
                "30 mph" to 48,
                "50mph" to 80,
                "50 km/h" to 50,
                "ES:urban" to null,
                "ES:rural" to null,
                "none" to null,
                "walk" to null,
                "signals" to null,
                "variable" to null,
                "50;30" to null,
                "" to null,
                "  " to null,
                "4" to null,
                "151" to null,
                "0" to null,
                "-30" to null,
                "50.5" to null,
                "100 mph" to null,
            )
        esperado.forEach { (texto, kmh) -> assertEquals("«$texto»", kmh, LimiteVelocidad.kmh(texto)) }
        assertNull(LimiteVelocidad.kmh(null))
    }

    private fun via(
        maxspeed: String?,
        adelante: String?,
        atras: String?,
    ) = Via(
        1,
        "primary",
        maxspeed,
        adelante,
        atras,
        SentidoUnico.NO,
        null,
        null,
        listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.61, 2.9)),
    )

    @Test
    fun `aplicable usa forward o backward segun el sentido`() {
        val v = via("80", "90", "70")
        assertEquals(90, LimiteVelocidad.aplicable(v, adelante = true))
        assertEquals(70, LimiteVelocidad.aplicable(v, adelante = false))
    }

    @Test
    fun `aplicable cae a maxspeed y a null sin dato`() {
        assertEquals(80, LimiteVelocidad.aplicable(via("80", null, null), adelante = true))
        assertEquals(80, LimiteVelocidad.aplicable(via("80", "90", null), adelante = false))
        assertEquals(80, LimiteVelocidad.aplicable(via("80", null, "70"), adelante = true))
        assertNull(LimiteVelocidad.aplicable(via(null, null, null), adelante = true))
        assertNull(LimiteVelocidad.aplicable(via("ES:urban", null, null), adelante = false))
    }
}
