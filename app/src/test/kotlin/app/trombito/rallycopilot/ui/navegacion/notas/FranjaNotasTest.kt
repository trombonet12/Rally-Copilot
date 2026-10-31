package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
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

@Suppress("LongParameterList") // constructor de pruebas: todos los campos con valores por defecto
internal fun nota(
    grado: Int?,
    direccion: Direccion = Direccion.DERECHA,
    modificadores: Set<Modificador> = emptySet(),
    siguienteM: Int? = null,
    cuidado: Boolean = false,
    posicionM: Double = 200.0,
) = Nota(posicionM, posicionM + 30.0, direccion, grado, modificadores, siguienteM, false, cuidado)

internal fun deNota(
    nota: Nota,
    vararg unidos: TipoAviso,
) = ElementoHoja.DeNota(nota, unidos.map { Aviso(nota.posicionM - 10.0, it) })

internal fun deAviso(tipo: TipoAviso) = ElementoHoja.DeAviso(Aviso(300.0, tipo))

internal fun visible(
    elemento: ElementoHoja,
    distanciaM: Double = 120.0,
    activa: Boolean = false,
) = NotaVisible(elemento, distanciaM, activa)

abstract class FranjaNotasBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()

    protected fun poner(estado: NotasUiState) = banco.poner { RallyCopilotTheme { FranjaNotas(estado) } }

    protected fun fila(indice: Int): SemanticsNode = banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + indice)

    protected fun textoFila(indice: Int): String = banco.textos(fila(indice)).joinToString(" ")

    protected fun ahora(indice: Int): String? = fila(indice).config.getOrNull(SemanticsProperties.StateDescription)

    protected val tres =
        NotasUiState(
            listOf(
                visible(deNota(nota(4, modificadores = setOf(Modificador.LARGA), siguienteM = 50)), 120.0),
                visible(deNota(nota(2, Direccion.IZQUIERDA, setOf(Modificador.CIERRA), siguienteM = 30)), 250.0),
                visible(deAviso(TipoAviso.PUENTE), 400.0),
            ),
        )
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FranjaNotasTest : FranjaNotasBase() {
    @Test
    fun `sin notas no pinta nada`() {
        poner(NotasUiState())
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
        assertTrue(banco.etiquetas(ETIQUETA_NOTA_PREFIJO).isEmpty())
    }

    @Test
    fun `la primera nota va grande con su texto y distancia a la siguiente, las demas en pequeno y en orden`() {
        poner(tres)
        assertEquals("DERECHA 4 LARGA · 50", textoFila(0))
        assertEquals("IZQUIERDA 2 CIERRA · 30", textoFila(1))
        assertEquals("PUENTE", textoFila(2))
        val (grande, segunda, tercera) = (0..2).map { fila(it).boundsInRoot }
        assertTrue("la primera es más alta: $grande vs $segunda", grande.height > segunda.height * 1.2f)
        assertTrue("las pequeñas van debajo de la grande", segunda.top >= grande.bottom)
        assertEquals("las pequeñas comparten fila", segunda.top, tercera.top, 1f)
        assertTrue("la segunda va a la izquierda de la tercera", segunda.right <= tercera.left)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("la grande ocupa casi todo el ancho: $grande", grande.width > franja.width * 0.8f)
        assertTrue("las pequeñas se reparten el ancho: $segunda, $tercera", segunda.width < franja.width * 0.5f)
    }

    @Test
    fun `solo la nota que toca cantar ya va resaltada`() {
        poner(NotasUiState(tres.visibles.mapIndexed { i, v -> v.copy(activa = i == 0) }))
        assertEquals("Ahora", ahora(0))
        assertNull(ahora(1))
        assertNull(ahora(2))
    }

    @Test
    fun `una nota de cuidado lleva su aviso ambar y la frase hablada empieza por cuidado`() {
        val peligrosa = nota(2, Direccion.IZQUIERDA, setOf(Modificador.CIERRA), cuidado = true)
        poner(NotasUiState(listOf(visible(deNota(peligrosa)), visible(deNota(nota(5))))))
        assertTrue(banco.hay(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0))
        assertFalse(banco.hay(ETIQUETA_NOTA_CUIDADO_PREFIJO + 1))
        assertTrue(banco.descripcion(fila(0))!!.startsWith("cuidado, izquierda dos cierra"))
    }

    @Test
    fun `un aviso de cuidado suelto se lee cuidado`() {
        poner(NotasUiState(listOf(visible(deAviso(TipoAviso.CUIDADO)))))
        assertEquals("CUIDADO", textoFila(0))
        assertTrue(banco.descripcion(fila(0))!!.startsWith("cuidado"))
    }

    @Test
    fun `con las notas simplificadas se ve cerrada, media o suave en vez del grado`() {
        poner(
            NotasUiState(
                listOf(
                    visible(deNota(nota(2))),
                    visible(deNota(nota(4, Direccion.IZQUIERDA))),
                    visible(deNota(nota(6))),
                ),
                simplificadas = true,
            ),
        )
        assertEquals("DERECHA CERRADA", textoFila(0))
        assertEquals("IZQUIERDA MEDIA", textoFila(1))
        assertEquals("DERECHA SUAVE", textoFila(2))
        assertTrue(banco.descripcion(fila(1))!!.startsWith("izquierda media"))
    }

    @Test
    fun `la horquilla se escribe entera con su direccion`() {
        poner(NotasUiState(listOf(visible(deNota(nota(null, Direccion.IZQUIERDA, siguienteM = 40))))))
        assertEquals("HORQUILLA IZQUIERDA · 40", textoFila(0))
    }

    @Test
    fun `los avisos de la hoja se escriben en mayusculas`() {
        poner(NotasUiState(listOf(visible(deAviso(TipoAviso.CAMBIO_RASANTE)), visible(deAviso(TipoAviso.TUNEL)))))
        assertEquals("CAMBIO DE RASANTE", textoFila(0))
        assertEquals("TÚNEL", textoFila(1))
    }

    @Test
    fun `los avisos unidos a una nota van como iconos y en la frase hablada`() {
        poner(NotasUiState(listOf(visible(deNota(nota(3, Direccion.IZQUIERDA), TipoAviso.RASANTE), 80.0))))
        assertEquals("IZQUIERDA 3", textoFila(0))
        assertEquals("rasante, izquierda tres, a 80 metros", banco.descripcion(fila(0)))
    }

    @Test
    fun `talkback lee la frase de la voz y los metros redondeados a diez`() {
        poner(tres)
        assertEquals("derecha cuatro larga, cincuenta, a 120 metros", banco.descripcion(fila(0)))
        poner(NotasUiState(listOf(visible(deNota(nota(1)), distanciaM = -3.0))))
        assertTrue(banco.descripcion(fila(0))!!.endsWith("a 0 metros"))
    }

    @Test
    fun `una sola nota no deja filas pequenas`() {
        poner(NotasUiState(tres.visibles.take(1)))
        assertTrue(banco.hay(ETIQUETA_NOTA_PREFIJO + 0))
        assertFalse(banco.hay(ETIQUETA_NOTA_PREFIJO + 1))
    }

    @Test
    fun `la franja es legible y cabe en vertical`() {
        poner(tres)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("la franja cabe en el ancho: $franja", franja.right <= 411 * 3f)
        assertTrue("la nota grande alcanza 48 dp: ${fila(0).size}", fila(0).size.height >= 48 * 3)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-xxhdpi")
class FranjaNotasHorizontalTest : FranjaNotasBase() {
    @Test
    fun `en horizontal sigue el mismo orden y cabe entera`() {
        poner(tres)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertEquals("DERECHA 4 LARGA · 50", textoFila(0))
        assertTrue("la franja cabe en la pantalla: $franja", franja.right <= 891 * 3f && franja.bottom <= 411 * 3f / 2)
        assertTrue(fila(1).boundsInRoot.top >= fila(0).boundsInRoot.bottom)
        assertTrue(fila(1).boundsInRoot.right <= fila(2).boundsInRoot.left)
    }
}
