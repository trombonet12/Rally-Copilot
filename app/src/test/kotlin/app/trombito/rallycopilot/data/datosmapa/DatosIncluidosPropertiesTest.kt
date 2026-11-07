package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.motor.datos.Complemento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** Lo que Gradle deja fuera del APK (`datos-incluidos.properties`) y lo que la app baja bajo demanda coinciden. */
class DatosIncluidosPropertiesTest {
    private val propiedades =
        Properties().apply {
            File("datos-incluidos.properties").reader(Charsets.UTF_8).use { load(it) }
        }

    @Test
    fun `los prefijos bajo demanda del APK son los de los complementos de la app`() {
        val prefijos = propiedades.getProperty("bajoDemanda").split(',').map { it.trim() }

        assertEquals(Complemento.PREFIJOS_BAJO_DEMANDA, prefijos)
    }

    @Test
    fun `la etiqueta y el sha del manifest incluido estan fijados`() {
        assertTrue(propiedades.getProperty("etiqueta").startsWith("datos-"))
        assertTrue(Regex("^[0-9a-f]{64}$").matches(propiedades.getProperty("manifestSha256")))
    }
}
