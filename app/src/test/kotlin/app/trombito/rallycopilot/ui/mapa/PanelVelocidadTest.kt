package app.trombito.rallycopilot.ui.mapa

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_PLACA
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PanelVelocidadTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun poner(
        velocidad: Int?,
        limite: Int?,
        ref: String? = null,
        nombre: String? = null,
        enColumna: Boolean = false,
    ) = banco.poner {
        RallyCopilotTheme { PanelVelocidad(velocidad, limite, ViaActual(nombre, ref, limite), enColumna) }
    }

    @Test
    fun `con limite muestra velocidad y senal`() {
        poner(velocidad = 63, limite = 50)
        assertTrue("63" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
        val senal = banco.conEtiqueta(ETIQUETA_LIMITE)
        assertEquals("Límite de velocidad: 50 kilómetros por hora", banco.descripcion(senal))
        assertEquals("Velocidad: 63 kilómetros por hora", banco.descripcion(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `sin limite no se muestra la senal`() {
        poner(velocidad = 63, limite = null)
        assertFalse(banco.hay(ETIQUETA_LIMITE))
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
    }

    @Test
    fun `sin velocidad muestra una raya y lo dice a TalkBack`() {
        poner(velocidad = null, limite = null)
        val nodo = banco.conEtiqueta(ETIQUETA_VELOCIDAD)
        assertTrue("—" in banco.textos(nodo))
        assertEquals("Velocidad desconocida", banco.descripcion(nodo))
    }

    @Test
    fun `la velocidad cero se muestra como cero`() {
        poner(velocidad = 0, limite = null)
        assertTrue("0" in banco.textos(banco.conEtiqueta(ETIQUETA_VELOCIDAD)))
    }

    @Test
    fun `con ref se ve la placa con su descripcion`() {
        poner(velocidad = 40, limite = 50, ref = "Ma-19")
        val placas = banco.nodos().filter { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_PLACA }
        assertEquals(listOf("carretera Ma-19"), placas.map { banco.descripcion(it) })
        assertTrue("Ma-19" in banco.textos(placas.single()))
        assertFalse(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `con ref y nombre se ven la placa y el nombre`() {
        poner(velocidad = 40, limite = 50, ref = "Ma-2141", nombre = "Carretera de Lluc")
        assertTrue(banco.hay(ETIQUETA_PLACA))
        assertTrue("Carretera de Lluc" in banco.textos(banco.conEtiqueta(ETIQUETA_VIA)))
    }

    @Test
    fun `en columna con ref y nombre tambien se ven ambos`() {
        poner(velocidad = 40, limite = 50, ref = "Ma-19", nombre = "Carretera de Llucmajor", enColumna = true)
        assertTrue(banco.hay(ETIQUETA_PLACA))
        assertTrue(banco.hay(ETIQUETA_VIA))
    }

    @Test
    fun `varios ref dan una placa cada uno y como mucho dos`() {
        poner(velocidad = 40, limite = 50, ref = "Ma-10;Ma-2141;Ma-1")
        val descripciones =
            banco
                .nodos()
                .filter { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_PLACA }
                .map { banco.descripcion(it) }
        assertEquals(listOf("carretera Ma-10", "carretera Ma-2141"), descripciones)
    }

    @Test
    fun `sin ref se ve el nombre en texto`() {
        poner(velocidad = 40, limite = 50, nombre = "Carrer Major")
        assertFalse(banco.hay(ETIQUETA_PLACA))
        assertTrue("Carrer Major" in banco.textos(banco.conEtiqueta(ETIQUETA_VIA)))
    }

    @Test
    fun `sin via no hay rotulo de via`() {
        poner(velocidad = 40, limite = 50)
        assertFalse(banco.hay(ETIQUETA_VIA))
        assertFalse(banco.hay(ETIQUETA_PLACA))
    }

    @Test
    fun `refsDePlaca separa limpia y limita a dos`() {
        assertEquals(listOf("Ma-10", "Ma-2141"), refsDePlaca(" Ma-10 ; Ma-2141;;Ma-10;Ma-1"))
        assertEquals(emptyList<String>(), refsDePlaca(null))
        assertEquals(emptyList<String>(), refsDePlaca("  "))
    }

    @Test
    fun `en columna tambien muestra velocidad y senal`() {
        poner(velocidad = 90, limite = 90, enColumna = true)
        assertTrue(banco.hay(ETIQUETA_VELOCIDAD))
        assertTrue(banco.hay(ETIQUETA_LIMITE))
    }
}
