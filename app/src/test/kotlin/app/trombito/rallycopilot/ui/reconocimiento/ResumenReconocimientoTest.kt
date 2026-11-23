package app.trombito.rallycopilot.ui.reconocimiento

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.edicion.CambioNota
import app.trombito.rallycopilot.ui.comun.BancoCompose
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
class ResumenReconocimientoTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val pedidas = mutableListOf<String>()

    private val cambios =
        listOf(
            CambioNota.Cambiada(1_240.0, "derecha cuatro", "derecha tres"),
            CambioNota.Anadida(850.0, "cuidado, rasante"),
            CambioNota.Quitada(300.0, "izquierda cinco"),
        )

    private fun poner(resumen: ResumenPasada?) {
        banco.poner {
            RallyCopilotTheme {
                Box(Modifier.fillMaxSize()) {
                    ResumenReconocimiento(
                        resumen = resumen,
                        acciones =
                            AccionesResumenReconocimiento(
                                onSegundaPasada = { pedidas += "segunda" },
                                onTerminar = { pedidas += "terminar" },
                            ),
                    )
                }
            }
        }
    }

    @Test
    fun `sin resumen no se pinta nada`() {
        poner(null)
        assertFalse(banco.hay(ETIQUETA_RESUMEN_RECONOCIMIENTO))
    }

    @Test
    fun `tras la primera pasada lista los cambios con km y ofrece segunda pasada y terminar`() {
        poner(ResumenPasada("t1", 1, cambios, terminada = false))

        val textos = banco.todosLosTextos()
        assertTrue("Resumen de la pasada 1" in textos)
        assertTrue("3 cambios" in textos)
        // Cada cambio es una fila con el km y los iconos (la frase completa va de descripción para TalkBack).
        assertTrue("1,2 km" in textos)
        assertTrue("850 m" in textos)
        assertTrue("300 m" in textos)
        assertFalse(textos.any { it.startsWith("A 1,2 km") })
        assertEquals(3, banco.etiquetas(ETIQUETA_RESUMEN_CAMBIO).size)
        val filas = banco.nodos().filter { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_RESUMEN_CAMBIO }
        assertEquals(
            listOf(
                "A 1,2 km: «derecha cuatro» pasa a «derecha tres»",
                "A 850 m: nota añadida «cuidado, rasante»",
                "A 300 m: nota quitada «izquierda cinco»",
            ),
            filas.map { banco.descripcion(it) },
        )

        val minimo = 48 * 3f
        listOf(ETIQUETA_RESUMEN_SEGUNDA_PASADA, ETIQUETA_RESUMEN_TERMINAR).forEach {
            val nodo = banco.conEtiqueta(it)
            assertTrue("$it no es pulsable", banco.esPulsable(nodo))
            assertTrue("$it mide ${nodo.size.height}", nodo.size.height >= minimo)
        }
        assertTrue(banco.pulsablesSinNombre().isEmpty())

        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESUMEN_SEGUNDA_PASADA))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_RESUMEN_TERMINAR))
        assertEquals(listOf("segunda", "terminar"), pedidas)
    }

    @Test
    fun `cada cambio se pinta con los iconos de antes y despues en vez de la frase`() {
        val iconos = mapOf("derecha cuatro" to R.drawable.ic_nota_der_4, "derecha tres" to R.drawable.ic_nota_der_3)
        poner(ResumenPasada("t1", 1, cambios.take(1), terminada = false, iconos = iconos))

        val fila = banco.conEtiqueta(ETIQUETA_RESUMEN_CAMBIO)
        // Con icono conocido no se escribe la frase de la nota: solo el km.
        assertEquals(listOf("1,2 km"), banco.textos(fila))
        assertFalse(banco.todosLosTextos().any { it.contains("derecha") })
    }

    @Test
    fun `tras la segunda pasada solo se ofrece terminar`() {
        poner(ResumenPasada("t1", 2, cambios.take(1), terminada = false))

        assertTrue("Resumen de la pasada 2" in banco.todosLosTextos())
        assertTrue("1 cambio" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_RESUMEN_SEGUNDA_PASADA))
        assertTrue(banco.hay(ETIQUETA_RESUMEN_TERMINAR))
    }

    @Test
    fun `sin cambios lo dice`() {
        poner(ResumenPasada("t1", 1, emptyList(), terminada = true))

        assertTrue("No has cambiado ninguna nota en esta pasada." in banco.todosLosTextos())
        assertEquals(0, banco.etiquetas(ETIQUETA_RESUMEN_CAMBIO).size)
    }
}
