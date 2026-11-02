package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
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

/** Vista previa en vivo de cada sección de Ajustes: cambia al tocar la opción. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesVistaPreviaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    /** Pantalla con estado propio: lo que se toca se aplica, como con el ViewModel. */
    private fun ponerConEstado(
        inicial: AjustesConduccion = AjustesConduccion(),
        alEscuchar: () -> Unit = {},
    ) {
        banco.poner {
            var ajustes by remember { mutableStateOf(inicial) }
            RallyCopilotTheme {
                AjustesScreen(
                    ajustes = ajustes,
                    acciones =
                        AccionesAjustes(
                            onAnticipacion = { ajustes = ajustes.copy(anticipacion = it) },
                            onNotasSimplificadas = { ajustes = ajustes.copy(notasSimplificadas = it) },
                            onIcono = { ajustes = ajustes.copy(icono = it) },
                            onEstiloManiobras = { ajustes = ajustes.copy(estiloManiobras = it) },
                            onTema = { ajustes = ajustes.copy(tema = it) },
                            onVolumenVoz = { ajustes = ajustes.copy(volumenVoz = it) },
                            onEscucharEjemplo = alEscuchar,
                        ),
                )
            }
        }
    }

    private fun textoDe(etiqueta: String) = banco.textos(banco.conEtiqueta(etiqueta)).joinToString(" ")

    private fun ponerProgreso(
        nodo: SemanticsNode,
        valor: Float,
    ) {
        val accion = checkNotNull(nodo.config.getOrNull(SemanticsActions.SetProgress)) { "No es un deslizador" }
        accion.action?.invoke(valor)
        banco.reposo()
    }

    @Test
    fun `las secciones salen en orden, con cabecera, y con vista previa las que la tienen`() {
        ponerConEstado()
        val claves = listOf("copiloto", "notas", "voz", "mapa", "escucha", "tema", "datos", "acerca")
        claves.forEach { assertTrue("falta la seccion $it", banco.hay(ETIQUETA_SECCION_PREFIJO + it)) }
        listOf("copiloto", "notas", "voz", "mapa", "tema").forEach {
            assertTrue("falta la vista previa de $it", banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + it))
        }
        listOf("escucha", "datos", "acerca").forEach {
            assertFalse(banco.hay(ETIQUETA_SECCION_PREVISTA_PREFIJO + it))
        }
        val titulos = banco.todosLosTextos()
        listOf("Copiloto", "Notas", "Volumen de la voz", "Mapa", "Escucha", "Tema", "Datos", "Acerca de").forEach {
            assertTrue("falta el titulo $it", it in titulos)
        }
    }

    @Test
    fun `la tarjeta de nota de ejemplo cambia entre derecha 4 larga y derecha media al tocar simplificadas`() {
        ponerConEstado()
        val completa = textoDe(ETIQUETA_PREVISTA_NOTA)
        assertTrue(completa, completa.startsWith("DERECHA 4 LARGA"))
        assertTrue(completa, "50" in completa)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_NOTAS_SIMPLIFICADAS).children.first { banco.esPulsable(it) })
        val simplificada = textoDe(ETIQUETA_PREVISTA_NOTA)
        assertTrue(simplificada, "MEDIA" in simplificada)
        assertFalse(simplificada, "DERECHA 4" in simplificada)
    }

    @Test
    fun `te la canta N s antes cambia al tocar cada nivel de anticipacion`() {
        ponerConEstado()
        assertEquals("Te la canta 5 s antes (a 90 km/h, a 150 m)", textoDe(ETIQUETA_PREVISTA_ANTICIPACION))
        val esperados =
            mapOf(
                NivelAnticipacion.MUY_PRONTO to "Te la canta 8 s antes (a 90 km/h, a 230 m)",
                NivelAnticipacion.PRONTO to "Te la canta 6,5 s antes (a 90 km/h, a 190 m)",
                NivelAnticipacion.TARDE to "Te la canta 3,5 s antes (a 90 km/h, a 110 m)",
                NivelAnticipacion.MUY_TARDE to "Te la canta 2,5 s antes (a 90 km/h, a 90 m)",
            )
        esperados.forEach { (nivel, texto) ->
            banco.pulsar(banco.conEtiqueta(ETIQUETA_ANTICIPACION_PREFIJO + nivel.name.lowercase()))
            assertEquals(texto, textoDe(ETIQUETA_PREVISTA_ANTICIPACION))
        }
    }

    @Test
    fun `escuchar ejemplo se pide desde la vista previa de notas, de voz y de copiloto`() {
        var pedidos = 0
        ponerConEstado(alEscuchar = { pedidos++ })
        val botones =
            banco.nodos().filter { it.config.getOrNull(SemanticsProperties.TestTag) == ETIQUETA_PREVISTA_ESCUCHAR }
        assertEquals(3, botones.size)
        botones.forEach { banco.pulsar(it) }
        assertEquals(3, pedidos)
        assertTrue("Escuchar ejemplo" in banco.todosLosTextos())
    }

    @Test
    fun `la miniatura del mapa muestra el icono de posicion y la maniobra elegidos`() {
        ponerConEstado()
        assertTrue(banco.hay(ETIQUETA_PREVISTA_MAPA_ICONO + IconoPosicion.FLECHA_WRC.name.lowercase()))
        assertTrue(banco.hay(ETIQUETA_PREVISTA_MAPA_MANIOBRA + EstiloManiobras.PLACA.name.lowercase()))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_ICONO_PREFIJO + IconoPosicion.CASCO.name.lowercase()))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_MANIOBRAS_PREFIJO + EstiloManiobras.CHEVRON.name.lowercase()))
        assertTrue(banco.hay(ETIQUETA_PREVISTA_MAPA_ICONO + "casco"))
        assertTrue(banco.hay(ETIQUETA_PREVISTA_MAPA_MANIOBRA + "chevron"))
        assertFalse(banco.hay(ETIQUETA_PREVISTA_MAPA_ICONO + "flecha_wrc"))
    }

    @Test
    fun `la muestra del tema cambia al tocar oscuro claro o como el movil`() {
        ponerConEstado()
        assertEquals("Muestra del tema Oscuro", banco.descripcion(banco.conEtiqueta(ETIQUETA_TEMA_MUESTRA)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + Tema.CLARO.name.lowercase()))
        assertEquals("Muestra del tema Claro", banco.descripcion(banco.conEtiqueta(ETIQUETA_TEMA_MUESTRA)))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + Tema.SISTEMA.name.lowercase()))
        assertEquals("Muestra del tema Como el móvil", banco.descripcion(banco.conEtiqueta(ETIQUETA_TEMA_MUESTRA)))
        Tema.entries.forEach {
            assertTrue(banco.esPulsable(banco.conEtiqueta(ETIQUETA_TEMA_PREFIJO + it.name.lowercase())))
        }
    }

    @Test
    fun `el deslizador de volumen va de 50 a 150 de diez en diez y muestra la cifra`() {
        ponerConEstado()
        assertEquals("100 %", textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
        val nodo = banco.conEtiqueta(ETIQUETA_AJUSTES_VOLUMEN)
        ponerProgreso(nodo, 130f)
        assertEquals("130 %", textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
        ponerProgreso(banco.conEtiqueta(ETIQUETA_AJUSTES_VOLUMEN), 50f)
        assertEquals("50 %", textoDe(ETIQUETA_AJUSTES_VOLUMEN_VALOR))
    }
}
