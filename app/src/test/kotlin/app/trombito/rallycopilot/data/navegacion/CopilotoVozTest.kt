package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class LocutorFalso : Locutor {
    val estaHablando = MutableStateFlow(false)
    override val hablando: StateFlow<Boolean> = estaHablando
    val dichas = mutableListOf<String>()
    val vocesUsadas = mutableListOf<VozCopiloto>()
    val preparadas = mutableListOf<List<String>>()

    override fun usarVoz(v: VozCopiloto) {
        vocesUsadas += v
    }

    override fun preparar(textos: List<String>) {
        preparadas += textos
    }

    override fun decir(texto: String) {
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String) = 1.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
}

class CopilotoVozTest {
    // Curva a 960 m y cruce a la izquierda a 1000 m; a 920 m y 20 m/s ambas están activas.
    private val hoja = HojaNotas(listOf(ElementoHoja.DeNota(Nota(960.0, 990.0, Direccion.DERECHA, grado = 3))))
    private val maniobra = Maniobra(TipoManiobra.IZQUIERDA, "izquierda", emptyList(), 0, 0, 100.0, 10.0, null)
    private val salida = Maniobra(TipoManiobra.SALIDA, "salida", emptyList(), 0, 0, 100.0, 10.0, null)

    private fun ruta() = RutaVoz(AvisosManiobra(listOf(salida, maniobra), listOf(0.0, 1000.0)), listOf(0.0, 1000.0))

    private fun notas(posicionM: Double) = NotasEnCurso(hoja, posicionM, 20.0, OrigenNotas.GUIADO)

    private fun copiloto(
        locutor: LocutorFalso,
        ajustes: AjustesConduccion,
        registro: ((String) -> Unit)? = null,
    ) = CopilotoVoz(locutor, flowOf(ajustes), registro).also { runBlocking { it.seguirAjustes() } }

    private fun CopilotoVoz.fijar(
        ruta: RutaVoz?,
        posicionM: Double,
    ) = alFijar(notas(posicionM), ruta, posicionM, 20.0)

    private fun con(
        modo: ModoCopiloto,
        voz: VozCopiloto? = VozCopiloto.DAVEFX,
    ) = AjustesConduccion(modoCopiloto = modo, voz = voz)

    @Test
    fun `en modo notas dice primero la maniobra y luego la nota, una sola vez cada una`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))
        val ruta = ruta()

        repeat(4) { copiloto.fijar(ruta, 920.0) }

        assertEquals(listOf("cruce, izquierda", "derecha tres"), locutor.dichas)
    }

    @Test
    fun `en modo completo tambien dice notas y maniobras`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.COMPLETO))
        val ruta = ruta()

        repeat(3) { copiloto.fijar(ruta, 920.0) }

        assertEquals(listOf("cruce, izquierda", "derecha tres"), locutor.dichas)
    }

    @Test
    fun `en silencio no dice nada ni prepara nada`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SILENCIO))
        val ruta = ruta()

        repeat(3) { copiloto.fijar(ruta, 920.0) }

        assertEquals(emptyList<String>(), locutor.dichas)
        assertTrue(locutor.preparadas.all { it.isEmpty() })
    }

    @Test
    fun `solo alertas dice las maniobras y calla las notas`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))
        val ruta = ruta()

        repeat(3) { copiloto.fijar(ruta, 920.0) }

        assertEquals(listOf("cruce, izquierda"), locutor.dichas)
    }

    @Test
    fun `en conduccion libre dice las notas sin maniobras`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))

        repeat(2) { copiloto.alFijar(notas(920.0), null, null, 20.0) }

        assertEquals(listOf("derecha tres"), locutor.dichas)
    }

    @Test
    fun `no habla mientras el locutor esta hablando y lo dice cuando acaba`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))
        val ruta = ruta()

        locutor.estaHablando.value = true
        copiloto.fijar(ruta, 920.0)
        assertEquals(emptyList<String>(), locutor.dichas)

        locutor.estaHablando.value = false
        copiloto.fijar(ruta, 921.0)
        assertEquals(listOf("cruce, izquierda"), locutor.dichas)
    }

    @Test
    fun `sin voz elegida no habla ni prepara ni fija voz`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.COMPLETO, voz = null))
        val ruta = ruta()

        repeat(3) { copiloto.fijar(ruta, 920.0) }

        assertEquals(emptyList<String>(), locutor.dichas)
        assertEquals(emptyList<List<String>>(), locutor.preparadas)
        assertEquals(emptyList<VozCopiloto>(), locutor.vocesUsadas)
    }

    @Test
    fun `fija la voz elegida una sola vez`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS, VozCopiloto.SHARVARD))
        val ruta = ruta()

        repeat(3) { copiloto.fijar(ruta, 920.0) }

        assertEquals(listOf(VozCopiloto.SHARVARD), locutor.vocesUsadas)
    }

    @Test
    fun `prepara las proximas frases, tambien el aviso inminente que aun no esta vigente`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))

        // A 600 m la curva y el cruce aun quedan lejos (ni activas ni vigentes), pero ya se sintetizan.
        copiloto.fijar(ruta(), 600.0)

        assertEquals(listOf("derecha tres", "cruce, izquierda"), locutor.preparadas.last())
        assertEquals(emptyList<String>(), locutor.dichas)
    }

    @Test
    fun `tras recalcular la ruta suenan los avisos de la ruta nueva aunque repitan indice`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))

        val primera = ruta()
        copiloto.fijar(primera, 920.0)
        copiloto.fijar(primera, 921.0)
        assertEquals(listOf("cruce, izquierda"), locutor.dichas)

        copiloto.fijar(primera, 922.0)
        assertEquals(listOf("cruce, izquierda"), locutor.dichas)

        val nueva = ruta()
        copiloto.fijar(nueva, 923.0)
        copiloto.fijar(nueva, 924.0)
        assertEquals(listOf("cruce, izquierda", "cruce, izquierda"), locutor.dichas)
    }

    @Test
    fun `registra id metros y velocidad de la frase dicha, sin coordenadas`() {
        val locutor = LocutorFalso()
        val registro = mutableListOf<String>()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS), registro::add)

        copiloto.fijar(ruta(), 920.0)

        assertEquals(listOf("dice id=r1-maniobra-1-inminente faltan=80m v=72km/h"), registro)
    }

    @Test
    fun `al cerrar vacia lo que habia por sintetizar`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))
        copiloto.fijar(ruta(), 600.0)

        copiloto.cerrar()

        assertEquals(emptyList<String>(), locutor.preparadas.last())
    }
}
