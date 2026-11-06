package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.motor.voz.TipoFrase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class LocutorFalso : Locutor {
    val estaHablando = MutableStateFlow(false)
    override val hablando: StateFlow<Boolean> = estaHablando
    val dichas = mutableListOf<String>()
    val vocesUsadas = mutableListOf<VozCopiloto>()
    val volumenes = mutableListOf<Int>()
    val preparadas = mutableListOf<List<String>>()

    override fun usarVoz(v: VozCopiloto) {
        vocesUsadas += v
    }

    override fun usarVolumen(porcentaje: Int) {
        volumenes += porcentaje
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
    fun `el volumen de los ajustes llega al locutor una sola vez`() {
        val locutor = LocutorFalso()
        val copiloto = copiloto(locutor, con(ModoCopiloto.NOTAS).copy(volumenVoz = 130))
        val ruta = ruta()

        copiloto.fijar(ruta, 920.0)
        copiloto.fijar(ruta, 921.0)

        assertEquals(listOf(130), locutor.volumenes)
    }

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
        return NotasEnCurso(HojaNotas(elementos), posicionM, 20.0, OrigenNotas.LIBRE, puntoEn = {
            PuntoGeo(39.7, 2.9 + (it + origenM) / METROS_POR_GRADO_LON)
        })
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

    @Test
    fun `una parada alcanzada se dice una vez, con prioridad, y no caduca por distancia`() {
        val locutor = LocutorFalso()
        val c = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))
        c.anunciarParada(1, "parada alcanzada, siguiente cruce, izquierda")

        c.fijar(ruta(), 100.0)
        c.fijar(ruta(), 140.0)

        assertEquals(listOf("parada alcanzada, siguiente cruce, izquierda"), locutor.dichas)
    }

    @Test
    fun `una parada alcanzada espera a que el altavoz quede libre`() {
        val locutor = LocutorFalso()
        val c = copiloto(locutor, con(ModoCopiloto.SOLO_ALERTAS))
        locutor.estaHablando.value = true
        c.anunciarParada(1, "parada alcanzada")
        c.fijar(ruta(), 100.0)
        assertTrue(locutor.dichas.isEmpty())

        locutor.estaHablando.value = false
        c.fijar(ruta(), 120.0)

        assertEquals(listOf("parada alcanzada"), locutor.dichas)
    }

    @Test
    fun `en silencio la parada alcanzada no se dice nunca`() {
        val locutor = LocutorFalso()
        val c = copiloto(locutor, con(ModoCopiloto.SILENCIO))
        c.anunciarParada(1, "parada alcanzada")

        repeat(30) { c.fijar(ruta(), 100.0 + it) }

        assertTrue(locutor.dichas.isEmpty())
    }

    private val delta = Frase("crono:t:1:sector:1", "menos dos décimas", TipoFrase.CRONO, null)

    @Test
    fun `el delta del cronometro solo suena en modo completo`() {
        for (modo in ModoCopiloto.entries.filter { it != ModoCopiloto.COMPLETO }) {
            val locutor = LocutorFalso()
            val c = copiloto(locutor, con(modo))
            c.anunciarCrono(delta)
            repeat(20) { c.fijar(null, 100.0 + it) }
            assertEquals("modo $modo", emptyList<String>(), locutor.dichas)
        }

        val locutor = LocutorFalso()
        val c = copiloto(locutor, con(ModoCopiloto.COMPLETO))
        c.anunciarCrono(delta)
        repeat(3) { c.fijar(null, 100.0 + it) }

        assertEquals(listOf("menos dos décimas"), locutor.dichas)
    }

    @Test
    fun `sin notas ni ruta el delta se dice igualmente, una sola vez`() {
        val locutor = LocutorFalso()
        val c = copiloto(locutor, con(ModoCopiloto.COMPLETO))
        c.anunciarCrono(delta)

        repeat(3) { c.alFijar(null, null, null, 20.0) }

        assertEquals(listOf("menos dos décimas"), locutor.dichas)
    }
}

/**
 * Qué indicaciones se dicen según `avisarCruces`, `avisarSalidasAutopista` y `avisarRotondas`: los avisos «cruce» y
 * «rotonda» de la hoja y las maniobras de rotonda y de salida de autopista; girar en un cruce de la ruta no se calla
 * nunca. Todo a 20 m/s desde 920 m (la curva, los avisos y las maniobras están activos a la vez).
 */
class CopilotoVozIndicacionesTest {
    private val curva = ElementoHoja.DeNota(Nota(960.0, 990.0, Direccion.DERECHA, grado = 3))
    private val hoja =
        HojaNotas(
            listOf(
                ElementoHoja.DeAviso(Aviso(940.0, TipoAviso.CRUCE)),
                ElementoHoja.DeAviso(Aviso(950.0, TipoAviso.ROTONDA)),
                curva,
            ),
        )

    private fun maniobra(
        tipo: TipoManiobra,
        salidaRotonda: Int? = null,
    ) = Maniobra(tipo, "x", emptyList(), 0, 0, 100.0, 10.0, salidaRotonda)

    private val maniobras =
        listOf(
            maniobra(TipoManiobra.SALIDA),
            maniobra(TipoManiobra.ROTONDA_ENTRADA, salidaRotonda = 2),
            maniobra(TipoManiobra.SALIDA_AUTOVIA_DERECHA),
            maniobra(TipoManiobra.IZQUIERDA),
        )
    private val posiciones = listOf(0.0, 1000.0, 1010.0, 1020.0)

    private val avisoCruce = "cruce"
    private val avisoRotonda = "rotonda"
    private val maniobraRotonda = "rotonda, segunda salida"
    private val maniobraSalida = "salida"
    private val giro = "cruce, izquierda"
    private val nota = "derecha tres"

    private fun dicho(ajustes: AjustesConduccion): Set<String> {
        val locutor = LocutorFalso()
        val copiloto = CopilotoVoz(locutor, flowOf(ajustes)).also { runBlocking { it.seguirAjustes() } }
        val ruta = RutaVoz(AvisosManiobra(maniobras, posiciones), posiciones)
        repeat(12) {
            copiloto.alFijar(NotasEnCurso(hoja, 920.0, 20.0, OrigenNotas.GUIADO), ruta, 920.0, 20.0)
        }
        return locutor.dichas.toSet()
    }

    private fun con(
        cruces: Boolean = true,
        salidas: Boolean = true,
        rotondas: Boolean = true,
    ) = AjustesConduccion(
        modoCopiloto = ModoCopiloto.NOTAS,
        voz = VozCopiloto.DAVEFX,
        avisarCruces = cruces,
        avisarSalidasAutopista = salidas,
        avisarRotondas = rotondas,
    )

    private val todo = setOf(avisoCruce, avisoRotonda, maniobraRotonda, maniobraSalida, giro, nota)

    @Test
    fun `con todo activo se dice todo`() {
        assertEquals(todo, dicho(con()))
    }

    @Test
    fun `sin cruces solo se calla el aviso de cruce y el giro de la ruta se dice`() {
        assertEquals(todo - avisoCruce, dicho(con(cruces = false)))
        assertTrue(giro in dicho(con(cruces = false)))
    }

    @Test
    fun `sin salidas de autopista solo se calla la maniobra de salida`() {
        assertEquals(todo - maniobraSalida, dicho(con(salidas = false)))
    }

    @Test
    fun `sin rotondas se callan el aviso y la maniobra de rotonda`() {
        assertEquals(todo - avisoRotonda - maniobraRotonda, dicho(con(rotondas = false)))
    }

    @Test
    fun `con todo desactivado queda la curva y el giro`() {
        assertEquals(setOf(nota, giro), dicho(con(cruces = false, salidas = false, rotondas = false)))
    }

    @Test
    fun `lo desactivado tampoco se prepara por adelantado`() {
        val locutor = LocutorFalso()
        val ajustes = con(cruces = false, salidas = false, rotondas = false)
        val copiloto = CopilotoVoz(locutor, flowOf(ajustes)).also { runBlocking { it.seguirAjustes() } }
        val ruta = RutaVoz(AvisosManiobra(maniobras, posiciones), posiciones)
        copiloto.alFijar(NotasEnCurso(hoja, 600.0, 20.0, OrigenNotas.GUIADO), ruta, 600.0, 20.0)
        val preparadas = locutor.preparadas.last().toSet()
        assertEquals(setOf(nota, giro), preparadas)
    }

    @Test
    fun `cambiar un interruptor vale en la siguiente fijacion`() {
        val locutor = LocutorFalso()
        val ajustes = MutableStateFlow(con())
        val copiloto = CopilotoVoz(locutor, ajustes)
        val trabajo = Job()
        CoroutineScope(Dispatchers.Unconfined + trabajo).launch { copiloto.seguirAjustes() }
        ajustes.value = con(rotondas = false)
        val ruta = RutaVoz(AvisosManiobra(maniobras, posiciones), posiciones)
        repeat(12) {
            copiloto.alFijar(NotasEnCurso(hoja, 920.0, 20.0, OrigenNotas.GUIADO), ruta, 920.0, 20.0)
        }
        trabajo.cancel()
        assertEquals(todo - avisoRotonda - maniobraRotonda, locutor.dichas.toSet())
    }
}
