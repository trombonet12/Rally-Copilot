package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_VOLVER
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ajustes > Ayuda: la sección, el tutorial, la tabla de notas y la chuleta de voz. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AyudaAjustesTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    @Test
    fun `la portada tiene la tarjeta de Ayuda y al tocarla se abre su subpantalla`() {
        val abiertas = mutableListOf<SeccionAjustes>()
        banco.ponerAjustes(acciones = AccionesAjustes(onSeccion = { abiertas += it }))
        val tarjeta = banco.conEtiqueta(ETIQUETA_SECCION_PREFIJO + SeccionAjustes.AYUDA.clave)
        assertTrue("Ayuda" in banco.textos(tarjeta).first())
        banco.pulsar(tarjeta)
        assertEquals(listOf(SeccionAjustes.AYUDA), abiertas)
        listOf(ETIQUETA_AYUDA_TUTORIAL, ETIQUETA_AYUDA_NOTAS, ETIQUETA_AYUDA_VOZ).forEach {
            assertTrue("Falta $it", banco.hay(it))
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertTrue(banco.hay(ETIQUETA_SECCION_PREFIJO + SeccionAjustes.AYUDA.clave))
    }

    @Test
    fun `las tres filas de ayuda navegan`() {
        var tutorial = 0
        var notas = 0
        var voz = 0
        banco.ponerAjustes(
            seccion = SeccionAjustes.AYUDA,
            acciones =
                AccionesAjustes(
                    onVerTutorial = { tutorial++ },
                    onAyudaNotas = { notas++ },
                    onAyudaVoz = { voz++ },
                ),
        )
        val textos = banco.todosLosTextos()
        listOf("Ver el tutorial otra vez", "Qué significan las notas", "Qué le puedes decir").forEach {
            assertTrue("Falta $it en $textos", it in textos)
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AYUDA_TUTORIAL))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AYUDA_NOTAS))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_AYUDA_VOZ))
        assertEquals(listOf(1, 1, 1), listOf(tutorial, notas, voz))
    }

    @Test
    fun `la tabla de notas explica los seis grados, las formas, los modificadores y los avisos`() {
        banco.poner { RallyCopilotTheme { AyudaNotasScreen(onVolver = {}) } }
        listOf(
            ETIQUETA_AYUDA_NOTAS_CURVAS,
            ETIQUETA_AYUDA_NOTAS_FORMAS,
            ETIQUETA_AYUDA_NOTAS_MODIFICADORES,
            ETIQUETA_AYUDA_NOTAS_AVISOS,
        ).forEach { assertTrue("Falta $it", banco.hay(it)) }
        val curvas = banco.textos(banco.conEtiqueta(ETIQUETA_AYUDA_NOTAS_CURVAS))
        listOf("6", "5", "4", "3", "2", "1", "Casi recta, muy suave", "La más cerrada").forEach {
            assertTrue("Falta $it en $curvas", it in curvas)
        }
        val formas = banco.textos(banco.conEtiqueta(ETIQUETA_AYUDA_NOTAS_FORMAS))
        assertTrue("Escuadra" in formas && "Horquilla" in formas)
        val modificadores = banco.textos(banco.conEtiqueta(ETIQUETA_AYUDA_NOTAS_MODIFICADORES))
        listOf("LARGA", "MUY LARGA", "CIERRA", "ABRE").forEach { assertTrue("Falta $it", it in modificadores) }
        val avisos = banco.textos(banco.conEtiqueta(ETIQUETA_AYUDA_NOTAS_AVISOS))
        listOf("Puente", "Rotonda", "Badén", "Cuidado").forEach { assertTrue("Falta $it", it in avisos) }
    }

    @Test
    fun `la chuleta de voz tiene los cinco grupos y lo que no puede hacer`() {
        banco.poner { RallyCopilotTheme { AyudaVozScreen(onVolver = {}) } }
        GrupoVoz.entries.forEach {
            assertTrue("Falta el grupo $it", banco.hay(ETIQUETA_AYUDA_VOZ_GRUPO_PREFIJO + it.name.lowercase()))
        }
        val textos = banco.todosLosTextos()
        listOf(
            "PREGUNTAS DE RUTA",
            "CONTROL DEL COPILOTO",
            "NAVEGACIÓN",
            "MAPA Y CRONO",
            "EDICIÓN DE NOTAS",
            "LO QUE NO PUEDE HACER",
            "«Llévame a Sóller»",
            "«Volver a casa»",
            "«Cancela la ruta»",
            "«Gasolinera más cercana»",
            "«Añade parada en Valldemossa»",
            "«Horquilla izquierda»",
            "«Silencio» o «Calla»",
        ).forEach { assertTrue("Falta $it en $textos", it in textos) }
        val limites = banco.textos(banco.conEtiqueta(ETIQUETA_AYUDA_VOZ_LIMITES))
        assertTrue(limites.any { it.startsWith("Dictado libre") })
        assertTrue(limites.any { it.startsWith("Direcciones que no estén") })
        assertTrue(limites.any { it.startsWith("Otros idiomas") })
    }

    @Test
    fun `las pantallas de ayuda se pueden desplazar y volver`() {
        var volver = 0
        banco.poner { RallyCopilotTheme { AyudaVozScreen(onVolver = { volver++ }) } }
        assertTrue(banco.hayDesplazable())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOLVER))
        assertEquals(1, volver)
    }
}
