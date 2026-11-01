package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.motor.voz.TipoFrase
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

    /** Hoja con una derecha (m en su propio origen), cuyo origen está [origenM] al este del de la primera hoja. */
    private fun hojaRegenerada(
        derecha: Double,
        origenM: Double,
        izquierda: Double? = null,
        posicionM: Double,
    ): NotasEnCurso {
        val elementos =
            listOfNotNull(
                ElementoHoja.DeNota(Nota(derecha, derecha + 30.0, Direccion.DERECHA, grado = 3)),
                izquierda?.let { ElementoHoja.DeNota(Nota(it, it + 30.0, Direccion.IZQUIERDA, grado = 3)) },
            )
        return NotasEnCurso(HojaNotas(elementos), posicionM, 20.0, OrigenNotas.LIBRE) {
            PuntoGeo(39.7, 2.9 + (it + origenM) / METROS_POR_GRADO_LON)
        }
    }

    private fun CopilotoVoz.fijarLibre(notas: NotasEnCurso) = alFijar(notas, null, null, 20.0)

    @Test
    fun `una hoja regenerada con otro origen no repite la misma curva y si canta la siguiente`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))

        // Primera hoja: derecha a 960 m e izquierda a 1100 m.
        copiloto.fijarLibre(hojaRegenerada(960.0, 0.0, 1100.0, posicionM = 920.0))
        copiloto.fijarLibre(hojaRegenerada(960.0, 0.0, 1100.0, posicionM = 921.0))
        assertEquals(listOf("derecha tres"), locutor.dichas)

        // Regenerada 500 m más adelante: la misma curva ahora está a 460 m, la izquierda a 600 m.
        copiloto.fijarLibre(hojaRegenerada(460.0, 500.0, 600.0, posicionM = 425.0))
        copiloto.fijarLibre(hojaRegenerada(460.0, 500.0, 600.0, posicionM = 426.0))
        assertEquals(listOf("derecha tres"), locutor.dichas)

        copiloto.fijarLibre(hojaRegenerada(460.0, 500.0, 600.0, posicionM = 570.0))
        copiloto.fijarLibre(hojaRegenerada(460.0, 500.0, 600.0, posicionM = 571.0))
        assertEquals(listOf("derecha tres", "izquierda tres"), locutor.dichas)
    }

    @Test
    fun `curvas distintas en hojas con otro origen suenan las dos`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))

        copiloto.fijarLibre(hojaRegenerada(960.0, 0.0, posicionM = 920.0))
        copiloto.fijarLibre(hojaRegenerada(960.0, 0.0, posicionM = 921.0))
        // La hoja nueva tiene una derecha a 1020 m absolutos (60 m más allá): otra curva.
        copiloto.fijarLibre(hojaRegenerada(520.0, 500.0, posicionM = 480.0))
        copiloto.fijarLibre(hojaRegenerada(520.0, 500.0, posicionM = 481.0))

        assertEquals(listOf("derecha tres", "derecha tres"), locutor.dichas)
    }

    @Test
    fun `tras recalcular la ruta guiada no repite la nota ya dicha`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))
        val guiada = { n: NotasEnCurso -> n.copy(origen = OrigenNotas.GUIADO) }

        val primera = ruta()
        copiloto.alFijar(guiada(hojaRegenerada(960.0, 0.0, posicionM = 920.0)), primera, 920.0, 20.0)
        copiloto.alFijar(guiada(hojaRegenerada(960.0, 0.0, posicionM = 921.0)), primera, 921.0, 20.0)
        assertEquals(1, locutor.dichas.count { it == "derecha tres" })

        // Recálculo: ruta nueva, hoja nueva con otro origen; la curva sigue 38 m por delante.
        val nueva = ruta()
        copiloto.alFijar(guiada(hojaRegenerada(40.0, 922.0, posicionM = 2.0)), nueva, 2.0, 20.0)
        copiloto.alFijar(guiada(hojaRegenerada(40.0, 922.0, posicionM = 3.0)), nueva, 3.0, 20.0)
        assertEquals(1, locutor.dichas.count { it == "derecha tres" })
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

    private fun radar(puntoM: Double? = 1000.0) =
        Frase("radar-f1", "radar a quinientos, ochenta", TipoFrase.ALERTA, puntoM)

    private val exceso = Frase("exceso-1", "exceso de velocidad, límite ochenta", TipoFrase.ALERTA, null)

    @Test
    fun `en solo alertas dice las alertas aunque callen las notas`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))

        copiloto.alFijar(notas(920.0), null, null, 20.0, listOf(radar()))

        assertEquals(listOf("radar a quinientos, ochenta"), locutor.dichas)
    }

    @Test
    fun `en silencio no dice las alertas ni las prepara`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SILENCIO))

        repeat(3) { copiloto.alFijar(notas(920.0), ruta(), 920.0, 20.0, listOf(radar(), exceso)) }

        assertEquals(emptyList<String>(), locutor.dichas)
        assertTrue(locutor.preparadas.all { it.isEmpty() })
    }

    @Test
    fun `una alerta manda sobre la maniobra y la nota cuando coinciden`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS))

        val ruta = ruta()

        repeat(4) { copiloto.alFijar(notas(920.0), ruta, 920.0, 20.0, listOf(radar(puntoM = 1000.0))) }

        assertEquals(listOf("radar a quinientos, ochenta", "cruce, izquierda", "derecha tres"), locutor.dichas)
    }

    @Test
    fun `el exceso de velocidad se dice una vez aunque llegue en cada fijacion`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))

        repeat(5) { copiloto.alFijar(null, null, 500.0 + it, 25.0, listOf(exceso)) }

        assertEquals(listOf("exceso de velocidad, límite ochenta"), locutor.dichas)
    }

    @Test
    fun `una alerta ya dicha no se repite y una nueva sin notas en libre suena igual`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))

        // Libre sin notas ni ruta: el eje arranca en 0 y el radar queda a 300 m.
        copiloto.alFijar(null, null, null, 20.0, listOf(radar(puntoM = 300.0)))
        copiloto.alFijar(null, null, null, 20.0, listOf(radar(puntoM = 280.0)))

        assertEquals(listOf("radar a quinientos, ochenta"), locutor.dichas)
    }

    @Test
    fun `una alerta que ya no llega a tiempo se descarta`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))

        // A 20 m/s quedan 10 m (0,5 s) y la frase dura 1 s: llegaría tarde.
        copiloto.alFijar(null, null, 990.0, 20.0, listOf(radar(puntoM = 1000.0)))

        assertEquals(emptyList<String>(), locutor.dichas)
    }

    @Test
    fun `sin alertas ni posicion no hace nada`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.COMPLETO))

        copiloto.alFijar(null, null, null, 20.0)

        assertEquals(emptyList<String>(), locutor.dichas)
        assertEquals(emptyList<VozCopiloto>(), locutor.vocesUsadas)
    }

    private companion object {
        const val METROS_POR_GRADO_LON = 85_500.0
    }
}
