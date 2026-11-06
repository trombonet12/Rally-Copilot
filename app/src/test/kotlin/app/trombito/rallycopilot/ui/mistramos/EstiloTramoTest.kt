package app.trombito.rallycopilot.ui.mistramos

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EstiloTramoTest {
    private val estilo =
        """{"version":8,"layers":[
            {"id":"fondo","type":"background"},
            {"id":"earth_label_islands","type":"symbol"},
            {"id":"water_label_ocean","type":"symbol"}]}"""

    @Test
    fun `quita solo la capa de la etiqueta de las islas`() {
        val capas = JSONObject(sinEtiquetaDeIslas(estilo)).getJSONArray("layers")
        val ids = (0 until capas.length()).map { capas.getJSONObject(it).getString("id") }
        assertEquals(listOf("fondo", "water_label_ocean"), ids)
    }

    @Test
    fun `un estilo ilegible se devuelve tal cual`() {
        assertEquals("{no es json", sinEtiquetaDeIslas("{no es json"))
        assertTrue(sinEtiquetaDeIslas("{\"version\":8}").isNotEmpty())
    }
}
