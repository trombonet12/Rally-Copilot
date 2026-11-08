package app.trombito.rallycopilot.ui.edicion

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.LONGITUD_MAX_TEXTO_LIBRE
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class EditorNotaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val curva = Nota(300.0, 340.0, Direccion.DERECHA, 4)

    private val pedidas = mutableListOf<String>()
    private val notasGuardadas = mutableListOf<Nota>()
    private val avisos = mutableListOf<TipoAviso>()

    private fun poner(
        inicial: Nota?,
        extras: AccionesExtraEditor,
    ) {
        banco.poner {
            RallyCopilotTheme {
                EditorNota(
                    inicial = inicial,
                    simplificadas = false,
                    onAplicar = { notasGuardadas += it },
                    onQuitar = { pedidas += "quitar" }.takeIf { inicial != null },
                    onCerrar = { pedidas += "cerrar" },
                    extras = extras,
                )
            }
        }
    }

    private val extrasDeNota
        get() =
            AccionesExtraEditor(
                onFusionar = { pedidas += "fusionar" },
                onDividir = { pedidas += "dividir" },
            )

    private val extrasDePunto
        get() =
            AccionesExtraEditor(
                onDividir = { pedidas += "dividir" },
                dividirEnElPunto = true,
                onAnadirAviso = { avisos += it },
            )

    private fun escribir(texto: String) {
        val accion = checkNotNull(banco.conEtiqueta(ETIQUETA_EDITOR_TEXTO).config.getOrNull(SemanticsActions.SetText))
        accion.action?.invoke(AnnotatedString(texto))
        banco.reposo()
    }

    @Test
    fun `una nota existente ofrece fusionar y dividir, accesibles y de 48 dp`() {
        poner(curva, extrasDeNota)
        val minimo = 48 * 3f
        listOf(ETIQUETA_EDITOR_FUSIONAR, ETIQUETA_EDITOR_DIVIDIR)
            .forEach {
                val nodo = banco.conEtiqueta(it)
                assertTrue("$it no es pulsable", banco.esPulsable(nodo))
                assertTrue("$it mide ${nodo.size.height}", nodo.size.height >= minimo)
            }
        assertTrue("Fusionar con la siguiente" in banco.todosLosTextos())
        assertTrue("Dividir en dos" in banco.todosLosTextos())
        assertTrue(banco.pulsablesSinNombre().isEmpty())

        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_FUSIONAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_DIVIDIR))
        assertEquals(listOf("fusionar", "dividir"), pedidas)
    }

    @Test
    fun `sin acciones extra no se ofrece fusionar ni dividir ni avisos`() {
        poner(curva, AccionesExtraEditor())
        assertFalse(banco.hay(ETIQUETA_EDITOR_FUSIONAR))
        assertFalse(banco.hay(ETIQUETA_EDITOR_DIVIDIR))
        assertFalse(banco.hay(ETIQUETA_EDITOR_MODO_AVISO))
        assertTrue(banco.hay(ETIQUETA_EDITOR_TEXTO))
    }

    @Test
    fun `el texto libre sale en el dira y se guarda en la nota`() {
        poner(curva, extrasDeNota)
        assertTrue("Dirá: «derecha cuatro»" in banco.todosLosTextos())

        escribir("cuidado gravilla, derecha cuatro")
        assertTrue("Dirá: «cuidado gravilla, derecha cuatro»" in banco.todosLosTextos())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_GUARDAR))

        assertEquals("cuidado gravilla, derecha cuatro", notasGuardadas.single().textoLibre)
    }

    @Test
    fun `una nota con texto lo trae al abrirla y vaciarlo vuelve a la frase generada`() {
        poner(curva.copy(textoLibre = "gravilla"), extrasDeNota)
        assertTrue("Dirá: «gravilla»" in banco.todosLosTextos())

        escribir("")
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_GUARDAR))

        assertNull(notasGuardadas.single().textoLibre)
        assertTrue(banco.todosLosTextos().contains("Dirá: «derecha cuatro»"))
    }

    @Test
    fun `el texto libre tiene un largo maximo y no admite saltos de linea`() {
        val borrador = BorradorNota().conTexto("a\nb" + "x".repeat(LONGITUD_MAX_TEXTO_LIBRE * 2))
        assertEquals(LONGITUD_MAX_TEXTO_LIBRE, borrador.texto.length)
        assertFalse('\n' in borrador.texto)
        assertEquals(borrador.texto.trim(), borrador.aNota().textoLibre)
        assertNull(BorradorNota().conTexto("   ").aNota().textoLibre)

        poner(curva, extrasDeNota)
        escribir("y".repeat(LONGITUD_MAX_TEXTO_LIBRE + 20))
        assertTrue(banco.todosLosTextos().any { it.endsWith("$LONGITUD_MAX_TEXTO_LIBRE/$LONGITUD_MAX_TEXTO_LIBRE") })
    }

    @Test
    fun `en un punto nuevo se elige entre nota y aviso y cada aviso tiene su icono con nombre`() {
        poner(null, extrasDePunto)
        assertTrue("Añadir nota" in banco.todosLosTextos())
        assertFalse(banco.hay(ETIQUETA_EDITOR_AVISO_PREFIJO + TipoAviso.STOP.name))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_MODO_AVISO))
        assertTrue("Añadir aviso" in banco.todosLosTextos())
        assertEquals(TipoAviso.entries.size, banco.etiquetas(ETIQUETA_EDITOR_AVISO_PREFIJO).size)
        val stop = banco.conEtiqueta(ETIQUETA_EDITOR_AVISO_PREFIJO + TipoAviso.STOP.name)
        assertEquals("Añadir aviso: Stop", banco.descripcion(stop))
        assertTrue(banco.esPulsable(stop))
        assertTrue(banco.pulsablesSinNombre().isEmpty())
    }

    @Test
    fun `elegir un aviso lo anade y volver a nota recupera el formulario`() {
        poner(null, extrasDePunto)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_MODO_AVISO))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_AVISO_PREFIJO + TipoAviso.BADEN.name))
        assertEquals(listOf(TipoAviso.BADEN), avisos)
        assertTrue(notasGuardadas.isEmpty())

        banco.pulsar(banco.conEtiqueta(ETIQUETA_EDITOR_MODO_NOTA))
        assertTrue(banco.hay(ETIQUETA_EDITOR_GUARDAR))
        assertTrue(banco.hay(ETIQUETA_EDITOR_DIVIDIR))
    }

    @Test
    fun `una nota existente no ofrece cambiar a aviso`() {
        poner(curva, extrasDePunto)
        assertFalse(banco.hay(ETIQUETA_EDITOR_MODO_AVISO))
    }
}
