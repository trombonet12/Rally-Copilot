package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.alertas.AlertasRadar
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import app.trombito.rallycopilot.motor.velocidad.msAKmh
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.CandidatasNotas
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.HojaFiltrada
import app.trombito.rallycopilot.motor.voz.IdsEstablesNotas
import app.trombito.rallycopilot.motor.voz.PlanificadorFrases
import app.trombito.rallycopilot.motor.voz.TipoFrase
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maniobras de la ruta guiada vigente y sus posiciones (m, mismo sistema que el progreso del seguidor de ruta). Se crea
 * una vez por ruta: un recálculo da una instancia nueva.
 */
class RutaVoz(
    val avisos: AvisosManiobra,
    val posicionesM: List<Double>,
)

/**
 * El copiloto de una conducción: en cada fijación decide, con las notas visibles y las maniobras del guiado, qué frase
 * toca decir y se la pasa al [locutor]; además le pide sintetizar por adelantado las próximas. Un
 * [PlanificadorFrases] por sesión: no repite lo dicho ni dice lo que llegaría tarde. Con el modo SILENCIO, o sin voz
 * elegida (`voz == null`), no habla (ni sintetiza). Las [ajustes] se siguen con [seguirAjustes]; [registro] recibe
 * una línea por frase dicha (id, metros que faltaban y velocidad: nunca coordenadas) para medir la anticipación y
 * [alDecir] el texto de cada frase dicha (la orden de voz «repite» vuelve a decir la última) y [alDecirNota] cada nota
 * dicha con su posición (la orden «esta nota estaba mal» marca la última). Las familias de avisos
 * que los ajustes apagan (radares, incidencias, velocidad) no se dicen, pero las notas y maniobras no se tocan.
 *
 * El [locutor] es de la aplicación y sobrevive a la sesión: [cerrar] solo deja de pedirle trabajo. No es concurrente:
 * [alFijar] y [cerrar] se llaman desde la corrutina de la sesión.
 */
@Suppress("TooManyFunctions") // un solo planificador por sesión; los ayudantes comparten su estado
class CopilotoVoz(
    private val locutor: Locutor,
    private val ajustes: Flow<AjustesConduccion>,
    private val registro: ((String) -> Unit)? = null,
    private val alDecir: ((String) -> Unit)? = null,
    private val alDecirNota: ((Frase) -> Unit)? = null,
) {
    private val selector = SelectorNotas()
    private val hojaFiltrada = HojaFiltrada()
    private val planificador = PlanificadorFrases(locutor::duracionEstimadaS)
    private val ids = IdsEstablesNotas()

    @Volatile private var actuales = AjustesConduccion()
    private var vozEnLocutor: VozCopiloto? = null
    private var volumenEnLocutor: Int? = null
    private var velocidadEnLocutor: VelocidadHabla? = null
    private var ultimaPreparacion: List<String>? = null
    private var rutaAnterior: RutaVoz? = null
    private var generacionRuta = 0
    private var ultimoProgresoM: Double? = null
    private val porDecir = ArrayList<FrasePorDecir>()
    private var recalculandoDesdeMs: Long? = null
    private var recalculoAnunciado = false
    private var recalculosAnunciados = 0

    /**
     * Una parada intermedia alcanzada ([numero]: cuántas van, desde 1): se dice [texto] en cuanto el altavoz esté libre
     * (como una maniobra, sin posición: no caduca por distancia) y, si no cabe en [VIGENCIA_PARADA_FIJACIONES]
     * fijaciones, se pierde.
     */
    fun anunciarParada(
        numero: Int,
        texto: String,
    ) {
        porDecir += FrasePorDecir(Frase("parada-$numero", texto, TipoFrase.MANIOBRA, null), VIGENCIA_PARADA_FIJACIONES)
    }

    /**
     * Una frase de cronometraje (delta de un sector o de la meta): se dice si el modo la permite (solo COMPLETO) en
     * cuanto el altavoz esté libre y, si no cabe en [VIGENCIA_PARADA_FIJACIONES] fijaciones, se pierde.
     */
    fun anunciarCrono(frase: Frase) {
        porDecir += FrasePorDecir(frase, VIGENCIA_PARADA_FIJACIONES)
    }

    /**
     * Estado de «recalculando» en cada fijación ([recalculando]: la sesión está saliendo de la ruta y recalculando; [ahoraMs]
     * el reloj de la fijación). La voz solo dice «recalculando» una vez por episodio y solo si dura más de
     * [RECALCULO_MIN_MS] (5 s): un recálculo rápido es solo visual. Como una maniobra: se dice en cuanto el altavoz
     * esté libre y se pierde si no cabe en [VIGENCIA_RECALCULO_FIJACIONES] fijaciones.
     */
    fun alRecalcular(
        recalculando: Boolean,
        ahoraMs: Long,
    ) {
        if (!recalculando) {
            recalculandoDesdeMs = null
            recalculoAnunciado = false
            return
        }
        val desde = recalculandoDesdeMs ?: ahoraMs.also { recalculandoDesdeMs = it }
        if (recalculoAnunciado || ahoraMs - desde <= RECALCULO_MIN_MS) return
        recalculoAnunciado = true
        recalculosAnunciados++
        val frase = Frase("recalculando-$recalculosAnunciados", FrasesManiobra.RECALCULANDO, TipoFrase.MANIOBRA, null)
        porDecir += FrasePorDecir(frase, VIGENCIA_RECALCULO_FIJACIONES)
    }

    /** Mantiene al día los ajustes (modo, voz, notas, indicaciones que se callan) hasta cancelar. */
    suspend fun seguirAjustes() {
        ajustes.collect { actuales = it }
    }

    /**
     * Una fijación: [notas] es lo último publicado por la sesión de notas (null si no hay hoja vigente), [ruta] las
     * maniobras del guiado (null en modo libre), [progresoM] el progreso sobre la ruta guiada y [vMs] la velocidad.
     * [alertas] son las candidatas ALERTA de la fijación (radares, tramos, incidencias, exceso), en el mismo eje que
     * `progresoM` o, en libre, que la posición de las notas (o 0 si no hay notas): se suman a las activas, no se
     * sintetizan por adelantado (su texto depende de la distancia) y los modos las filtran como al resto. Un fijo del
     * sentido contrario se muestra en el banner pero no se dice. El exceso y la media del tramo
     * ([TipoFrase.AVISO_VELOCIDAD]) esperan a las notas y maniobras que queden a menos de ~8 s.
     */
    fun alFijar(
        notas: NotasEnCurso?,
        ruta: RutaVoz?,
        progresoM: Double?,
        vMs: Double?,
        alertas: List<Frase> = emptyList(),
    ) {
        val a = actuales
        val habladas = alertas.filterNot { AlertasRadar.esContrario(it.id) }.filter(a.filtroAvisos::permite)
        val progresoPrevio = ultimoProgresoM
        if (progresoM != null) ultimoProgresoM = progresoM
        val sinPosicion = habladas.isEmpty() && porDecir.isEmpty()
        val posicionM = progresoM ?: notas?.posicionM ?: if (sinPosicion) null else 0.0
        val voz = a.voz
        if (voz == null || posicionM == null) return
        sincronizarLocutor(a, voz)
        if (ruta !== rutaAnterior) {
            val anterior = rutaAnterior
            val generacionAnterior = generacionRuta
            rutaAnterior = ruta
            generacionRuta++
            heredarDichas(anterior, generacionAnterior, ruta, desplazamiento(progresoPrevio, progresoM))
        }
        val v = vMs ?: 0.0
        val visibles = visibles(notas, a, v)
        val maniobras = maniobras(ruta, posicionM, v, a)
        val idDe = idsDe(notas)
        val pendientes = pendientesVigentes()
        val activas = CandidatasNotas.de(visibles, a.notasSimplificadas, idDe) + maniobras + habladas + pendientes
        val previstas =
            CandidatasNotas.de(visibles.map { it.copy(activa = true) }, a.notasSimplificadas, idDe) +
                maniobras + inminentesPrevistos(ruta, posicionM, a)
        if (!locutor.hablando.value) {
            planificador.siguiente(activas, posicionM, vMs, a.modoCopiloto, venideras = previstas)?.let {
                porDecir.removeAll { p -> p.frase.id == it.id }
                decir(it, posicionM, vMs)
            }
        }
        preparar(previstas, posicionM, a)
    }

    /** Pasa al locutor la voz, el volumen y la velocidad de habla de los ajustes cuando cambian. */
    private fun sincronizarLocutor(
        a: AjustesConduccion,
        voz: VozCopiloto,
    ) {
        if (voz != vozEnLocutor) {
            locutor.usarVoz(voz)
            vozEnLocutor = voz
            ultimaPreparacion = null
        }
        if (a.volumenVoz != volumenEnLocutor) {
            locutor.usarVolumen(a.volumenVoz)
            volumenEnLocutor = a.volumenVoz
        }
        if (a.velocidadHabla != velocidadEnLocutor) {
            locutor.usarVelocidad(a.velocidadHabla)
            velocidadEnLocutor = a.velocidadHabla
            ultimaPreparacion = null // lo preparado a otra velocidad no vale: se vuelve a pedir
        }
    }

    /**
     * Dónde cae el origen de la ruta nueva en el eje de la anterior (m): lo que avanzaba la anterior menos lo que la
     * nueva da ya de progreso. Un recálculo parte de la posición del coche, así que su progreso empieza casi en 0.
     */
    private fun desplazamiento(
        progresoPrevio: Double?,
        progresoActual: Double?,
    ): Double? = if (progresoPrevio != null && progresoActual != null) progresoPrevio - progresoActual else null

    /**
     * Tras un recálculo, una maniobra de [nueva] equivalente a una de [anterior] (mismo tipo, a ± [TOLERANCIA_RECALCULO_M]
     * una vez puesta la nueva en el eje de la anterior con [desplazamientoM]) hereda su estado «dicho»: no se vuelven a
     * cantar preaviso ni inminente. Sin desplazamiento fiable no se hereda nada.
     */
    private fun heredarDichas(
        anterior: RutaVoz?,
        generacionAnterior: Int,
        nueva: RutaVoz?,
        desplazamientoM: Double?,
    ) {
        if (anterior == null || nueva == null || desplazamientoM == null) return
        val viejas = anterior.avisos.maniobras
        nueva.avisos.maniobras.forEachIndexed { j, maniobra ->
            val enEjeAnterior = nueva.posicionesM[j] + desplazamientoM
            val i =
                viejas.indices
                    .filter { viejas[it].tipo == maniobra.tipo }
                    .minByOrNull { abs(anterior.posicionesM[it] - enEjeAnterior) }
                    ?.takeIf { abs(anterior.posicionesM[it] - enEjeAnterior) <= TOLERANCIA_RECALCULO_M }
            if (i != null) {
                for (fase in FASES_MANIOBRA) {
                    val dicha = planificador.resuelta("r$generacionAnterior-maniobra-$i-$fase")
                    if (dicha) planificador.marcarResuelta("r$generacionRuta-maniobra-$j-$fase")
                }
            }
        }
    }

    /** Las paradas y frases de cronometraje por decir aún vigentes; cada fijación gasta una de su vigencia. */
    private fun pendientesVigentes(): List<Frase> {
        porDecir.removeAll { it.fijacionesRestantes <= 0 }
        val frases = porDecir.map { it.frase }
        porDecir.forEach { it.fijacionesRestantes-- }
        return frases
    }

    /** Deja de pedir trabajo al locutor (vacía lo que había por sintetizar); se puede llamar más de una vez. */
    fun cerrar() {
        if (ultimaPreparacion != null) locutor.preparar(emptyList())
        ultimaPreparacion = null
    }

    private fun visibles(
        notas: NotasEnCurso?,
        a: AjustesConduccion,
        vMs: Double,
    ): List<NotaVisible> =
        notas
            ?.let {
                val hoja = hojaFiltrada.de(it.hoja, a.filtroIndicaciones)
                selector.siguientes(hoja, it.posicionM, vMs, a.anticipacion, NOTAS_CONSIDERADAS)
            }.orEmpty()

    /**
     * Id de cada elemento de la hoja, independiente del origen de la hoja: si se conoce la traza, la misma curva en una
     * hoja regenerada (modo libre cada ~500 m, o tras un recálculo) conserva su id y no se canta dos veces.
     */
    private fun idsDe(notas: NotasEnCurso?): (ElementoHoja) -> String {
        val puntoEn = notas?.puntoEn ?: return { "nota-${it.posicionM}" }
        return { ids.id(it, puntoEn(it.posicionM)) }
    }

    /**
     * Frases de maniobra vigentes ahora (sin las familias que los ajustes callan); el id lleva la generación de la ruta
     * para que un recálculo no las calle.
     */
    private fun maniobras(
        ruta: RutaVoz?,
        posicionM: Double,
        vMs: Double,
        a: AjustesConduccion,
    ): List<Frase> =
        ruta
            ?.avisos
            ?.candidatas(posicionM, vMs, a.filtroIndicaciones)
            .orEmpty()
            .map(::conRuta)

    /**
     * El aviso inminente de las próximas maniobras, que aún no está vigente pero sí se puede sintetizar ya (su texto
     * no depende de la distancia).
     */
    private fun inminentesPrevistos(
        ruta: RutaVoz?,
        posicionM: Double,
        a: AjustesConduccion,
    ): List<Frase> =
        ruta
            ?.posicionesM
            ?.filter { it > posicionM }
            ?.take(MANIOBRAS_PREPARADAS)
            ?.flatMap { punto ->
                ruta.avisos.candidatas(punto - 1.0, 0.0, a.filtroIndicaciones).filter {
                    it.puntoM == punto && it.id.endsWith(SUFIJO_INMINENTE)
                }
            }.orEmpty()
            .map(::conRuta)

    private fun conRuta(frase: Frase) = frase.copy(id = "r$generacionRuta-${frase.id}")

    private fun decir(
        frase: Frase,
        posicionM: Double,
        vMs: Double?,
    ) {
        locutor.decir(frase.texto)
        alDecir?.invoke(frase.texto)
        if (frase.tipo == TipoFrase.NOTA && frase.puntoM != null) alDecirNota?.invoke(frase)
        registro?.invoke(
            "dice id=${frase.id} faltan=${frase.puntoM?.let { (it - posicionM).roundToInt().toString() } ?: "-"}m " +
                "v=${vMs?.let { it.msAKmh().roundToInt().toString() } ?: "-"}km/h",
        )
    }

    private fun preparar(
        previstas: List<Frase>,
        posicionM: Double,
        a: AjustesConduccion,
    ) {
        val textos =
            planificador
                .proximas(previstas.distinctBy { it.id }, posicionM, a.modoCopiloto, PREPARADAS)
                .map { it.texto }
        if (textos != ultimaPreparacion) {
            ultimaPreparacion = textos
            locutor.preparar(textos)
        }
    }

    private companion object {
        /** Elementos de la hoja que se miran por delante: cantar las activas y preparar las siguientes. */
        const val NOTAS_CONSIDERADAS = 8
        const val MANIOBRAS_PREPARADAS = 3
        const val PREPARADAS = 6
        const val SUFIJO_INMINENTE = "-inminente"

        /** Fijaciones (≈ s) que una parada alcanzada espera a que el altavoz esté libre antes de perderse. */
        const val VIGENCIA_PARADA_FIJACIONES = 15

        /** «Recalculando» solo se dice si la salida de ruta dura más de esto, y caduca pronto. */
        const val RECALCULO_MIN_MS = 5_000L
        const val VIGENCIA_RECALCULO_FIJACIONES = 5

        /** Una maniobra de la ruta nueva equivale a la anterior si es del mismo tipo y cae a esta distancia o menos. */
        const val TOLERANCIA_RECALCULO_M = 30.0
        val FASES_MANIOBRA = listOf("preaviso", "inminente")
    }
}

private class FrasePorDecir(
    val frase: Frase,
    var fijacionesRestantes: Int,
)
