package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
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
import app.trombito.rallycopilot.ui.theme.Movimiento
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

internal fun deNotaSobrePuente(nota: Nota) =
    ElementoHoja.DeNota(nota, sobrePuente = Aviso(nota.posicionM + 10.0, TipoAviso.PUENTE))

internal fun deAviso(tipo: TipoAviso) = ElementoHoja.DeAviso(Aviso(300.0, tipo))

internal fun visible(
    elemento: ElementoHoja,
    distanciaM: Double = 120.0,
    activa: Boolean = false,
) = NotaVisible(elemento, distanciaM, activa)

abstract class CarreteNotasBase {
    @get:Rule val limpieza = LimpiezaCompose()

    protected val banco = BancoCompose()

    protected fun poner(estado: NotasUiState) = banco.poner { RallyCopilotTheme { CarreteNotas(estado) } }

    protected fun fila(indice: Int): SemanticsNode = banco.conEtiqueta(ETIQUETA_NOTA_PREFIJO + indice)

    /** Texto de la nota sin la distancia hasta ella, que va aparte («120 m»). */
    protected fun textoFila(indice: Int): String =
        fila(indice)
            .children
            .filter {
                val etiqueta = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                etiqueta != ETIQUETA_NOTA_DISTANCIA_PREFIJO + indice &&
                    !etiqueta.startsWith(ETIQUETA_NOTA_CUIDADO_PREFIJO)
            }.flatMap { banco.textos(it) }
            .joinToString(" ")

    protected fun distanciaFila(indice: Int): String? =
        banco
            .etiquetas(ETIQUETA_NOTA_DISTANCIA_PREFIJO + indice)
            .firstOrNull()
            ?.let { banco.textos(banco.conEtiqueta(it)).single() }

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
class CarreteNotasTest : CarreteNotasBase() {
    @Test
    fun `sin notas no pinta nada`() {
        poner(NotasUiState())
        assertFalse(banco.hay(ETIQUETA_FRANJA_NOTAS))
        assertTrue(banco.etiquetas(ETIQUETA_NOTA_PREFIJO).isEmpty())
    }

    @Test
    fun `la actual va grande con texto y distancia y la siguiente va a la derecha mas pequena`() {
        poner(tres)
        assertEquals("DERECHA 4 LARGA · 50 m", textoFila(0))
        assertEquals("IZQ 2 CIERRA · 30 m", textoFila(1))
        assertEquals("120 m", distanciaFila(0))
        assertNull(distanciaFila(1))
        assertFalse("solo dos notas en el carrete", banco.hay(ETIQUETA_NOTA_PREFIJO + 2))
        val (actual, siguiente) = (0..1).map { fila(it).boundsInRoot }
        assertTrue("la actual es más alta: $actual vs $siguiente", actual.height > siguiente.height * 1.3f)
        assertTrue("la siguiente va a la derecha de la actual", siguiente.left >= actual.right)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("la actual sigue siendo la más ancha: $actual", actual.width > franja.width * 0.55f)
        assertTrue("la actual mide unos 72 dp: ${actual.height}", actual.height in 64 * 3f..80 * 3f)
    }

    @Test
    fun `la siguiente cabe entera dentro de la franja, sin cortarse contra el borde`() {
        poner(tres)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        val siguiente = fila(1).boundsInRoot
        assertTrue("la siguiente no se sale: $siguiente vs $franja", siguiente.right <= franja.right + 1f)
        assertTrue("la siguiente mide su ancho entero: ${siguiente.width}", siguiente.width >= 120 * 3f)
        assertTrue("la siguiente es legible: ${siguiente.height}", siguiente.height >= 44 * 3f)
    }

    @Test
    fun `solo la nota que toca cantar ya va resaltada`() {
        poner(NotasUiState(tres.visibles.mapIndexed { i, v -> v.copy(activa = i == 0) }))
        assertEquals("Ahora", ahora(0))
        assertNull(ahora(1))
    }

    @Test
    fun `una nota de cuidado lleva su insignia y la frase hablada empieza por cuidado`() {
        val peligrosa = nota(2, Direccion.IZQUIERDA, setOf(Modificador.CIERRA), cuidado = true)
        poner(NotasUiState(listOf(visible(deNota(peligrosa)), visible(deNota(nota(5))))))
        assertTrue(banco.hay(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0))
        assertEquals(listOf("!"), banco.textos(banco.conEtiqueta(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0)))
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
    fun `cuidado va primero aunque lo pida un aviso unido y el texto sigue la frase del motor`() {
        poner(NotasUiState(listOf(visible(deNota(nota(3, siguienteM = 50), TipoAviso.BACHE), 60.0))))
        assertEquals("DERECHA 3 · 50 m", textoFila(0))
        assertTrue(banco.hay(ETIQUETA_NOTA_AVISO_PREFIJO + "0-BACHE"))
        assertEquals("cuidado, resalto, derecha tres, cincuenta, a 60 metros", banco.descripcion(fila(0)))
        assertTrue(banco.hay(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0))
    }

    @Test
    fun `la nota de cuidado con un aviso unido no repite cuidado`() {
        val peligrosa = nota(1, Direccion.IZQUIERDA, cuidado = true)
        poner(NotasUiState(listOf(visible(deNota(peligrosa, TipoAviso.RASANTE, TipoAviso.CUIDADO), 90.0))))
        assertEquals("IZQUIERDA 1", textoFila(0))
        assertTrue(banco.hay(ETIQUETA_NOTA_AVISO_PREFIJO + "0-RASANTE"))
        assertTrue(banco.hay(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0))
        assertEquals("cuidado, rasante, izquierda uno, a 90 metros", banco.descripcion(fila(0)))
    }

    @Test
    fun `una nota con puente dentro de la curva dice sobre puente`() {
        poner(NotasUiState(listOf(visible(deNotaSobrePuente(nota(3, siguienteM = 50))))))
        assertEquals("DERECHA 3 · 50 m", textoFila(0))
        assertTrue(banco.hay(ETIQUETA_NOTA_AVISO_PREFIJO + "0-PUENTE"))
        assertEquals("derecha tres sobre puente, cincuenta, a 120 metros", banco.descripcion(fila(0)))
    }

    @Test
    fun `la horquilla muy larga y el ceda el paso salen como los canta el motor`() {
        poner(
            NotasUiState(
                listOf(
                    visible(deNota(nota(null, Direccion.DERECHA, setOf(Modificador.MUY_LARGA), siguienteM = 40))),
                    visible(deAviso(TipoAviso.CEDA)),
                ),
            ),
        )
        assertEquals("HORQUILLA DERECHA MUY LARGA · 40 m", textoFila(0))
        assertEquals("CEDA EL PASO", textoFila(1))
    }

    @Test
    fun `un aviso suelto de cuidado va como nota del carrete`() {
        poner(NotasUiState(listOf(visible(deNota(nota(3))), visible(deAviso(TipoAviso.BACHE)))))
        assertEquals("CUIDADO, RESALTO", textoFila(1))
        assertTrue(banco.descripcion(fila(1))!!.startsWith("cuidado, resalto"))
    }

    @Test
    fun `con las notas simplificadas se ve cerrada, media o suave en vez del grado`() {
        poner(NotasUiState(listOf(visible(deNota(nota(2))), visible(deNota(nota(4, Direccion.IZQUIERDA)))), true))
        assertEquals("DERECHA CERRADA", textoFila(0))
        assertEquals("IZQ MEDIA", textoFila(1))
        assertTrue(banco.descripcion(fila(1))!!.startsWith("izquierda media"))
    }

    @Test
    fun `con las notas simplificadas el grado 1 se ve muy cerrada y abreviada en la siguiente`() {
        poner(NotasUiState(listOf(visible(deNota(nota(1))), visible(deNota(nota(1, Direccion.IZQUIERDA)))), true))
        assertEquals("DERECHA MUY CERRADA", textoFila(0))
        assertEquals("IZQ MUY CERR.", textoFila(1))
        assertTrue(banco.descripcion(fila(0))!!.startsWith("derecha muy cerrada"))
    }

    @Test
    fun `una sola nota no deja siguiente`() {
        poner(NotasUiState(tres.visibles.take(1)))
        assertTrue(banco.hay(ETIQUETA_NOTA_PREFIJO + 0))
        assertFalse(banco.hay(ETIQUETA_NOTA_PREFIJO + 1))
    }

    @Test
    fun `al cantarse la actual el carrete desliza y la siguiente pasa a ser la actual`() {
        var estado by mutableStateOf(tres)
        banco.poner { RallyCopilotTheme { CarreteNotas(estado) } }
        assertEquals("DERECHA 4 LARGA · 50 m", textoFila(0))
        estado = NotasUiState(tres.visibles.drop(1))
        banco.esperar(Movimiento.MEDIA + 200L)
        banco.reposo()
        assertEquals("IZQUIERDA 2 CIERRA · 30 m", textoFila(0))
        assertEquals("250 m", distanciaFila(0))
        assertEquals("PUENTE", textoFila(1))
        assertEquals("solo queda un carrete", 1, banco.etiquetas(ETIQUETA_FRANJA_NOTAS).size)
    }

    @Test
    fun `si solo cambia la distancia no hay transicion y el texto se actualiza`() {
        var estado by mutableStateOf(tres)
        banco.poner { RallyCopilotTheme { CarreteNotas(estado) } }
        estado = NotasUiState(tres.visibles.mapIndexed { i, v -> if (i == 0) v.copy(distanciaM = 80.0) else v })
        banco.reposo()
        assertEquals("80 m", distanciaFila(0))
        assertEquals(1, banco.etiquetas(ETIQUETA_NOTA_PREFIJO + "0").size)
    }

    @Test
    fun `una nota larga con avisos y cuidado lleva todo su texto, sus iconos y la insignia`() {
        // El ajuste de la fuente se comprueba en el emulador: Robolectric no mide tipografías reales.
        val larga = nota(null, Direccion.DERECHA, setOf(Modificador.MUY_LARGA), siguienteM = 50, cuidado = true)
        poner(NotasUiState(listOf(visible(deNota(larga, TipoAviso.CAMBIO_RASANTE, TipoAviso.RASANTE)))))
        assertEquals("HORQUILLA DERECHA MUY LARGA · 50 m", textoFila(0))
        assertTrue(banco.hay(ETIQUETA_NOTA_AVISO_PREFIJO + "0-CAMBIO_RASANTE"))
        assertTrue(banco.hay(ETIQUETA_NOTA_AVISO_PREFIJO + "0-RASANTE"))
        assertTrue(banco.hay(ETIQUETA_NOTA_CUIDADO_PREFIJO + 0))
        assertEquals("120 m", distanciaFila(0))
    }

    @Test
    fun `sin pausa no hay rotulo de notas en pausa`() {
        poner(tres)
        assertFalse(banco.hay(ETIQUETA_NOTAS_EN_PAUSA))
    }

    @Test
    fun `con las notas en pausa sale un rotulo discreto, region viva educada, y la franja sigue`() {
        poner(tres.copy(enPausa = true))
        assertTrue(banco.hay(ETIQUETA_NOTAS_EN_PAUSA))
        assertEquals(listOf("Notas en pausa"), banco.textos(banco.conEtiqueta(ETIQUETA_NOTAS_EN_PAUSA)))
        assertEquals(
            LiveRegionMode.Polite,
            banco.conEtiqueta(ETIQUETA_NOTAS_EN_PAUSA).config.getOrNull(SemanticsProperties.LiveRegion),
        )
        assertTrue(textoFila(0).startsWith("DERECHA 4 LARGA"))
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        val rotulo = banco.conEtiqueta(ETIQUETA_NOTAS_EN_PAUSA).boundsInRoot
        assertTrue("el rótulo va bajo el carrete: $rotulo vs $franja", rotulo.top >= franja.bottom - 1f)
        assertTrue("el rótulo es pequeño: ${rotulo.height}", rotulo.height < 28 * 3f)
    }

    @Test
    fun `el rotulo de pausa aparece y desaparece solo al cambiar el estado`() {
        var estado by mutableStateOf(tres)
        banco.poner { RallyCopilotTheme { CarreteNotas(estado) } }
        assertFalse(banco.hay(ETIQUETA_NOTAS_EN_PAUSA))
        estado = tres.copy(enPausa = true)
        banco.reposo()
        assertTrue(banco.hay(ETIQUETA_NOTAS_EN_PAUSA))
        estado = tres
        banco.reposo()
        assertFalse(banco.hay(ETIQUETA_NOTAS_EN_PAUSA))
        assertTrue(textoFila(0).startsWith("DERECHA 4 LARGA"))
    }

    @Test
    fun `el carrete es legible y cabe en vertical`() {
        poner(tres)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("el carrete cabe en el ancho: $franja", franja.right <= 411 * 3f)
        assertTrue("la nota actual alcanza 48 dp: ${fila(0).size}", fila(0).size.height >= 48 * 3)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w891dp-h411dp-xxhdpi")
class CarreteNotasHorizontalTest : CarreteNotasBase() {
    @Test
    fun `en horizontal compacta es mas bajo y la siguiente cabe a la derecha`() {
        banco.poner { RallyCopilotTheme { CarreteNotas(tres, compacta = true) } }
        assertEquals("DERECHA 4 LARGA · 50 m", textoFila(0))
        assertEquals("IZQ 2 CIERRA · 30 m", textoFila(1))
        assertEquals("120 m", distanciaFila(0))
        assertNull(distanciaFila(1))
        assertFalse("sin tercera nota", banco.hay(ETIQUETA_NOTA_PREFIJO + 2))
        val (primera, segunda) = (0..1).map { fila(it).boundsInRoot }
        assertTrue("la siguiente a la derecha: $primera vs $segunda", segunda.left >= primera.right)
        val franja = banco.conEtiqueta(ETIQUETA_FRANJA_NOTAS).boundsInRoot
        assertTrue("la siguiente cabe entera: $segunda vs $franja", segunda.right <= franja.right + 1f)
        assertTrue("la siguiente mide su ancho entero: ${segunda.width}", segunda.width >= 120 * 3f)
        assertTrue("alineadas en vertical", kotlin.math.abs(primera.center.y - segunda.center.y) < 8 * 3f)
        assertTrue("más baja que la vertical: $primera", primera.height < 64 * 3f)
    }
}
