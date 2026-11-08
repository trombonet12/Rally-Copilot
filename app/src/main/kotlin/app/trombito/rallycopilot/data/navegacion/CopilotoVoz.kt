package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.alertas.AlertasRadar
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.motor.notas.SelectorNotas
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import app.trombito.rallycopilot.motor.velocidad.msAKmh
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import app.trombito.rallycopilot.motor.voz.CandidatasNotas
import app.trombito.rallycopilot.motor.voz.ConfianzaPosicion
import app.trombito.rallycopilot.motor.voz.EstadoLlamadas
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.HojaFiltrada
import app.trombito.rallycopilot.motor.voz.IdsEstablesNotas
import app.trombito.rallycopilot.motor.voz.LlamadaNota
import app.trombito.rallycopilot.motor.voz.LlamadasNotas
import app.trombito.rallycopilot.motor.voz.PlanificadorFrases
import app.trombito.rallycopilot.motor.voz.TipoFrase
import app.trombito.rallycopilot.motor.voz.pendientesTrasResincronizar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Calidad de la posición de una fijación, para que el copiloto decida si se fía de ella: [precisionM] del GPS, [lateralM]
 * distancia a la ruta guiada (null en libre o sin ruta) y [tMs] reloj de la fijación. Sin esto se da por buena.
 */
class CalidadPosicion(
    val precisionM: Double?,
    val lateralM: Double?,
    val tMs: Long,
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
    private val confianza = ConfianzaPosicion()
    private val pausa = MutableStateFlow(false)
    private var fijaciones = 0L
    private var ultimaCantadaHastaM: Double? = null
    private var hojaLibreAnterior: HojaNotas? = null
    private var relecturas = 0
    private val entradas = HashMap<String, Double>()
    private val cantadas = HashSet<String>()

    /** `true` mientras el copiloto no se fia de la posicion y no canta notas nuevas (maniobras y alertas siguen). */
    val notasEnPausa: StateFlow<Boolean> = pausa

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
     * ([TipoFrase.AVISO_VELOCIDAD]) esperan a las notas y maniobras que queden a menos de ~8 s. Las notas se cantan
     * como llamadas (curvas encadenadas en una frase; la siguiente espera a la entrada de la anterior) y, con una
     * [calidad] de posicion mala, se pausan ([notasEnPausa]) hasta recuperarla: entonces se re-sincroniza sin cantar
     * las pasadas.
     */
    @Suppress("LongParameterList") // la fijacion trae lo que ve cada frente (notas, guiado, alertas, calidad)
    fun alFijar(
        notas: NotasEnCurso?,
        ruta: RutaVoz?,
        progresoM: Double?,
        vMs: Double?,
        alertas: List<Frase> = emptyList(),
        calidad: CalidadPosicion? = null,
    ) {
        val a = actuales
        val idDe = idsDe(notas)
        val enPausa = evaluarConfianza(notas, calidad, vMs ?: 0.0, a, idDe)
        val habladas = alertas.filterNot { AlertasRadar.esContrario(it.id) }.filter(a.filtroAvisos::permite)
        val progresoPrevio = ultimoProgresoM
        if (progresoM != null) ultimoProgresoM = progresoM
        val sinPosicion = habladas.isEmpty() && porDecir.isEmpty()
        val posicionM = progresoM ?: notas?.posicionM ?: if (sinPosicion) null else 0.0
        val voz = a.voz
        if (voz == null || posicionM == null) return
        sincronizarLocutor(a, voz)
        if (ruta !== rutaAnterior) cambiarRuta(ruta, desplazamiento(progresoPrevio, progresoM))
        val v = vMs ?: 0.0
        val visibles = visibles(notas, a, v)
        val maniobras = maniobras(ruta, posicionM, v, a)
        val pendientes = pendientesVigentes().filterNot { enPausa && it.tipo == TipoFrase.NOTA }
        val activas = llamadasActivas(notas, visibles, a, idDe, enPausa) + maniobras + habladas + pendientes
        val previstas =
            llamadas(notas, visibles.map { it.copy(activa = true) }, a, idDe, conEspera = false).map { it.frase } +
                maniobras + inminentesPrevistos(ruta, posicionM, a)
        if (!locutor.hablando.value) decirSiguiente(activas, previstas, posicionM, vMs, a)
        preparar(previstas, posicionM, a)
    }

    /** Frases de las llamadas que ya se pueden cantar (ninguna en pausa); recuerda la entrada de cada una. */
    private fun llamadasActivas(
        notas: NotasEnCurso?,
        visibles: List<NotaVisible>,
        a: AjustesConduccion,
        idDe: (ElementoHoja) -> String,
        enPausa: Boolean,
    ): List<Frase> {
        val llamadas = if (enPausa) emptyList() else llamadas(notas, visibles, a, idDe, conEspera = true)
        llamadas.forEach { entradas[it.frase.id] = it.entradaM }
        return llamadas.map { it.frase }
    }

    private fun decirSiguiente(
        activas: List<Frase>,
        previstas: List<Frase>,
        posicionM: Double,
        vMs: Double?,
        a: AjustesConduccion,
    ) {
        planificador.siguiente(activas, posicionM, vMs, a.modoCopiloto, venideras = previstas)?.let {
            porDecir.removeAll { p -> p.frase.id == it.id }
            decir(it, posicionM, vMs)
        }
    }

    /** La ruta guiada cambia ([ruta] nueva o null): otro eje de posiciones; hereda lo dicho de la anterior. */
    private fun cambiarRuta(
        ruta: RutaVoz?,
        desplazamientoM: Double?,
    ) {
        val anterior = rutaAnterior
        val generacionAnterior = generacionRuta
        rutaAnterior = ruta
        generacionRuta++
        ultimaCantadaHastaM = null
        heredarDichas(anterior, generacionAnterior, ruta, desplazamientoM)
    }

    /**
     * Evalua la confianza en la posicion sobre la hoja y devuelve `true` si las notas estan en pausa. Al volver de una
     * pausa re-sincroniza ([resincronizar]). Sin notas no hay nada que evaluar: se mantiene el ultimo estado.
     */
    private fun evaluarConfianza(
        notas: NotasEnCurso?,
        calidad: CalidadPosicion?,
        vMs: Double,
        a: AjustesConduccion,
        idDe: (ElementoHoja) -> String,
    ): Boolean {
        if (notas == null) return pausa.value
        val tMs = calidad?.tMs ?: (fijaciones * MS_POR_FIJACION)
        fijaciones++
        val evaluacion = confianza.evaluar(tMs, calidad?.precisionM, calidad?.lateralM, notas.posicionM)
        val enPausa = evaluacion.estado == ConfianzaPosicion.Estado.PAUSA
        pausa.value = enPausa
        if (notas.origen == OrigenNotas.LIBRE && notas.hoja !== hojaLibreAnterior) {
            hojaLibreAnterior = notas.hoja
            ultimaCantadaHastaM = null // la hoja regenerada tiene otro origen
        }
        evaluacion.resincronizar?.let { resincronizar(notas, it, vMs, a, idDe) }
        return enPausa
    }

    /**
     * Tras una pausa: las notas que quedaron detras de [posicionM] se dan por dichas (no se cantan tarde) y, si la
     * siguiente ya cae dentro de la anticipacion y se dio por resuelta sin llegar a sonar, se relee una vez.
     */
    private fun resincronizar(
        notas: NotasEnCurso,
        posicionM: Double,
        vMs: Double,
        a: AjustesConduccion,
        idDe: (ElementoHoja) -> String,
    ) {
        val hoja = hojaFiltrada.de(notas.hoja, a.filtroIndicaciones)
        hoja.elementos.filter { it.posicionM < posicionM }.forEach { planificador.marcarResuelta(idDe(it)) }
        val siguiente = pendientesTrasResincronizar(hoja, posicionM, vMs, a.anticipacion)
        val llamada = siguiente?.let { llamadaSuelta(hoja, it, posicionM, a, idDe) }
        // Si no esta resuelta, el flujo normal la canta; solo se relee la que se descarto sin sonar.
        if (llamada != null && llamada.frase.id !in cantadas && planificador.resuelta(llamada.frase.id)) {
            relecturas++
            val relectura = llamada.frase.copy(id = "relectura-$relecturas-${llamada.frase.id}")
            entradas[relectura.id] = llamada.entradaM
            porDecir += FrasePorDecir(relectura, VIGENCIA_PARADA_FIJACIONES)
        }
    }

    /** La llamada que abre [elemento] como si estuviera activa; si no abre ninguna (2.a de cadena), solo su frase. */
    private fun llamadaSuelta(
        hoja: HojaNotas,
        elemento: ElementoHoja,
        posicionM: Double,
        a: AjustesConduccion,
        idDe: (ElementoHoja) -> String,
    ): LlamadaNota? {
        val visible = NotaVisible(elemento, elemento.posicionM - posicionM, activa = true)
        return LlamadasNotas
            .de(
                hoja,
                listOf(visible),
                EstadoLlamadas(posicionM, a.notasSimplificadas),
                idDe,
            ).firstOrNull()
            ?: CandidatasNotas
                .de(listOf(visible), a.notasSimplificadas, idDe)
                .firstOrNull()
                ?.let { LlamadaNota(it, elemento.posicionM) }
    }

    /** Llamadas de notas de [visibles] (con la espera a la entrada de la ultima cantada si [conEspera]). */
    private fun llamadas(
        notas: NotasEnCurso?,
        visibles: List<NotaVisible>,
        a: AjustesConduccion,
        idDe: (ElementoHoja) -> String,
        conEspera: Boolean,
    ): List<LlamadaNota> {
        if (notas == null || visibles.isEmpty()) return emptyList()
        val hoja = hojaFiltrada.de(notas.hoja, a.filtroIndicaciones)
        val estado =
            EstadoLlamadas(notas.posicionM, a.notasSimplificadas, if (conEspera) ultimaCantadaHastaM else null)
        return LlamadasNotas.de(hoja, visibles, estado, idDe)
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
        if (frase.tipo == TipoFrase.NOTA) {
            entradas[frase.id]?.let { ultimaCantadaHastaM = it }
            cantadas += frase.id
            cantadas += frase.cubre
        }
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

        /** Reloj de una fijacion sin [CalidadPosicion] (aprox. 1 Hz). */
        const val MS_POR_FIJACION = 1_000L

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
