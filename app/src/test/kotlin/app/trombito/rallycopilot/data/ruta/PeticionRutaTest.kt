package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeticionRutaTest {
    private val palma = PuntoGeo(39.5696, 2.6502)
    private val soller = PuntoGeo(39.7667, 2.7150)

    private fun json(
        perfil: PerfilRuta,
        excluirTuneles: Boolean = false,
    ): JsonObject = Json.parseToJsonElement(PeticionRuta.json(palma, soller, perfil, excluirTuneles)).jsonObject

    private fun auto(peticion: JsonObject): JsonObject =
        peticion
            .getValue("costing_options")
            .jsonObject
            .getValue("auto")
            .jsonObject

    @Test
    fun `petición base en español y kilómetros con instrucciones`() {
        val p = json(PerfilRuta.MAS_RAPIDA)
        assertEquals("auto", p.getValue("costing").jsonPrimitive.content)
        assertEquals("es-ES", p.getValue("language").jsonPrimitive.content)
        assertEquals("kilometers", p.getValue("units").jsonPrimitive.content)
        assertEquals("instructions", p.getValue("directions_type").jsonPrimitive.content)
    }

    @Test
    fun `origen y destino van por orden`() {
        val puntos = json(PerfilRuta.MAS_RAPIDA).getValue("locations").jsonArray.map { it.jsonObject }
        assertEquals(2, puntos.size)
        assertEquals(39.5696, puntos[0].getValue("lat").jsonPrimitive.double, 1e-9)
        assertEquals(2.6502, puntos[0].getValue("lon").jsonPrimitive.double, 1e-9)
        assertEquals(39.7667, puntos[1].getValue("lat").jsonPrimitive.double, 1e-9)
        assertEquals(2.7150, puntos[1].getValue("lon").jsonPrimitive.double, 1e-9)
    }

    @Test
    fun `la más rápida no añade opciones de costing`() {
        assertNull(json(PerfilRuta.MAS_RAPIDA)["costing_options"])
    }

    @Test
    fun `la sinuosa evita autopistas y peajes y limita la velocidad`() {
        val auto = auto(json(PerfilRuta.SINUOSA))
        assertEquals(0.0, auto.getValue("use_highways").jsonPrimitive.double, 0.0)
        assertEquals(0.0, auto.getValue("use_tolls").jsonPrimitive.double, 0.0)
        assertEquals(70.0, auto.getValue("top_speed").jsonPrimitive.double, 0.0)
        assertFalse("exclude_tunnels" in auto)
    }

    @Test
    fun `excluir túneles se añade solo si se pide`() {
        val sinuosa = auto(json(PerfilRuta.SINUOSA, excluirTuneles = true))
        assertTrue(
            sinuosa
                .getValue("exclude_tunnels")
                .jsonPrimitive.content
                .toBoolean(),
        )
        val rapida = auto(json(PerfilRuta.MAS_RAPIDA, excluirTuneles = true))
        assertTrue(
            rapida
                .getValue("exclude_tunnels")
                .jsonPrimitive.content
                .toBoolean(),
        )
        assertFalse("use_highways" in rapida)
    }
}
