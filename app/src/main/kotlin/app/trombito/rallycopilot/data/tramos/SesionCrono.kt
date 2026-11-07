package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.ParcialCrono
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.Cobertura
import app.trombito.rallycopilot.motor.tramos.Cronometro
import app.trombito.rallycopilot.motor.tramos.Delta
import app.trombito.rallycopilot.motor.tramos.DetectorSalida
import app.trombito.rallycopilot.motor.tramos.DetectorTramo
import app.trombito.rallycopilot.motor.tramos.EventoCrono
import app.trombito.rallycopilot.motor.tramos.EventoTramo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.Frase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * El cronometraje de una conducción (libre o guiada). Al [arrancar] carga una vez los tramos guardados con su mejor
 * pasada (en segundo plano: hasta entonces no se detecta nada) y en cada fijación ([alFijar]) alimenta al
 * [DetectorTramo], publica un [CronoEnCurso] y devuelve las frases CRONO que tocan (delta acumulado al cerrar cada
 * sector y en meta, solo del tramo más reciente en que se entró y solo si tiene mejor pasada con la que comparar).
 * Sin tramo conocido publica el cronómetro de la conducción desde la salida (primer movimiento, ver [DetectorSalida]);
 * hasta entonces publica el estado «listo» ([CronoEnCurso.enMarcha] false, sin tiempo) y el tramo que se crea del
 * recorrido tampoco incluye el rato parado. Con [salidaManual] verdadero (se sigue en vivo desde [arrancar]) el
 * detector no cuenta: la salida la fija [darSalida] y mientras tanto se publica [CronoEnCurso.esperaSalidaManual]. No
 * es concurrente: se usa desde la corrutina de la sesión. Nunca registra coordenadas.
 *
 * Cada pasada completada se guarda en cuanto se cruza la meta (en segundo plano, ligada al recorrido de la
 * conducción en curso y a su intervalo de puntos) y el resultado se republica con el `tramoId` y el `pasadaId` para
 * abrir su análisis; si el guardado falla se queda sin ids y la conducción sigue (al [cerrar] se reintenta). Al
 * [cerrar] espera a esos guardados y, si el recorrido mide un kilómetro o más (regla de los recorridos), crea con él un
 * tramo nuevo y su primera pasada, salvo que ya coincida más de [UMBRAL_COINCIDENCIA] con un tramo completado durante
 * la conducción (su pasada ya queda guardada: no se duplica el tramo).
 */
@Suppress("TooManyFunctions", "LongParameterList") // un estado por conducción; colaboradores de guardado y cierre
class SesionCrono(
    private val repositorio: RepositorioTramos,
    private val publicar: (CronoEnCurso?) -> Unit,
    private val ahoraMs: () -> Long,
    private val nuevoId: () -> String = { UUID.randomUUID().toString() },
    private val alFallar: (Throwable) -> Unit = {},
    private val nombrar: suspend (List<PuntoGeo>) -> String? = { null },
    private val anclarEdiciones: suspend (Tramo?) -> Unit = {},
    private val salidaManual: Flow<Boolean> = flowOf(false),
) {
    /** Un tramo en que se ha entrado y aún no se ha completado ni abandonado. */
    private class Vivo(
        val tramo: Tramo,
        val inicioFijacionMs: Long,
        val inicioMs: Long,
        val mejor: Pasada?,
    ) {
        val parciales = ArrayList<ParcialCrono>()
        val idPasada: String get() = inicioFijacionMs.toString()
    }

    /** Una pasada hecha: [inicioMs] epoch de su salida y [origen] su intervalo en el reloj de las fijaciones. */
    private class Completada(
        val pasada: Pasada,
        val inicioMs: Long,
        val desdeMs: Long,
    ) {
        val hastaMs: Long get() = desdeMs + pasada.totalMs

        /** Id de la pasada en la base una vez guardada; null mientras no (o si el guardado falló). */
        @Volatile var idGuardada: Long? = null
        var guardado: Job? = null
    }

    private class Terminado(
        val completada: Completada?,
        val tramo: Tramo,
        val resultado: ResultadoCrono,
        val parciales: List<ParcialCrono>,
    )

    private val salida = DetectorSalida()

    /** Epoch de la salida (cuando el coche empieza a moverse); null mientras siga parado. */
    private var salidaEpochMs: Long? = null

    /** La salida la da Joan con [darSalida] y no el movimiento del coche. */
    @Volatile private var manual = false
    private var ultimaFijacion: Fijacion? = null
    private var detector: DetectorTramo? = null
    private var catalogo: Map<String, Tramo> = emptyMap()
    private val mejores = HashMap<String, Pasada>()
    private val vivos = LinkedHashMap<String, Vivo>()
    private val completadas = ArrayList<Completada>()
    private val cerrojo = Any()
    private var terminado: Terminado? = null
    private var ultimaFijacionMs = 0L
    private var alcance: CoroutineScope? = null
    private var recorridoId: Long? = null

    /**
     * Carga los tramos guardados en [alcance] sin bloquear las fijaciones; devuelve el trabajo para cancelarlo.
     * [recorridoId] es el recorrido de la conducción en curso (ya abierto en la base): lo usan las pasadas que se
     * guardan al cruzar la meta.
     */
    fun arrancar(
        alcance: CoroutineScope,
        recorridoId: Long? = null,
    ): Job {
        this.alcance = alcance
        this.recorridoId = recorridoId
        return alcance.launch {
            launch { salidaManual.collect { manual = it } }
            try {
                val lista = repositorio.cronometrables()
                catalogo = lista.associate { it.tramo.id to it.tramo }
                lista.forEach { c -> c.mejor?.let { mejores[c.tramo.id] = it } }
                detector = DetectorTramo(lista.map { it.tramo }, reloj = { ultimaFijacionMs })
            } catch (e: SQLiteException) {
                alFallar(e)
            } catch (e: IllegalArgumentException) {
                alFallar(e)
            }
        }
    }

    /** Una fijación válida (en orden temporal). Devuelve las frases CRONO nuevas. */
    fun alFijar(f: Fijacion): List<Frase> =
        synchronized(cerrojo) {
            ultimaFijacionMs = f.tiempoMs
            ultimaFijacion = f
            if (salidaEpochMs == null && !manual) {
                salida.alFijar(f)?.let { salidaEpochMs = ahoraMs() - (f.tiempoMs - it) }
            }
            val frases = ArrayList<Frase>()
            detector?.alFijar(f)?.forEach { procesar(it, f, frases) }
            publicar(estado(f))
            frases
        }

    /**
     * Fija la salida en [epochMs] (por defecto ahora) si aún no la hay: el cronómetro de la conducción y el tramo
     * que se crea del recorrido cuentan desde ahí. Republica el estado al momento, sin esperar a otra fijación.
     */
    fun darSalida(epochMs: Long = ahoraMs()) {
        synchronized(cerrojo) {
            if (salidaEpochMs != null) return
            salidaEpochMs = epochMs
            ultimaFijacion?.let { publicar(estado(it)) }
        }
    }

    /** El tramo en que se está ahora (entrado y sin completar ni abandonar); null si no hay ninguno. */
    fun tramoActivo(): Tramo? = synchronized(cerrojo) { vivos.values.lastOrNull()?.tramo }

    private fun procesar(
        evento: EventoTramo,
        f: Fijacion,
        frases: MutableList<Frase>,
    ) {
        when (evento) {
            is EventoTramo.Entrada -> entrar(evento, f)
            is EventoTramo.Corte -> cortar(evento, frases)
            is EventoTramo.Completado -> completar(evento, frases)
            is EventoTramo.Abandono -> vivos.remove(evento.tramoId)
        }
    }

    private fun entrar(
        evento: EventoTramo.Entrada,
        f: Fijacion,
    ) {
        val tramo = catalogo[evento.tramoId] ?: return
        val inicioMs = ahoraMs() - (f.tiempoMs - evento.tInicioMs)
        vivos.remove(tramo.id)
        vivos[tramo.id] = Vivo(tramo, evento.tInicioMs, inicioMs, mejores[tramo.id])
        terminado = null
    }

    private fun cortar(
        evento: EventoTramo.Corte,
        frases: MutableList<Frase>,
    ) {
        val vivo = vivos[evento.tramoId] ?: return
        val corte = evento.corte
        val parcial =
            when (corte) {
                is EventoCrono.CorteSector -> {
                    parcial(vivo, TipoCorte.SECTOR, corte.numero, corte.distanciaM, corte.tMs)
                }

                is EventoCrono.CorteMicro -> {
                    parcial(vivo, TipoCorte.MICROSECTOR, corte.numero, corte.distanciaM, corte.tMs)
                }

                else -> {
                    return
                }
            }
        vivo.parciales += parcial
        val delta = parcial.deltaAcumuladoMs
        if (parcial.tipo == TipoCorte.SECTOR && delta != null && esReciente(vivo)) {
            frases += Delta.fraseSector(vivo.tramo.id, vivo.idPasada, parcial.numero, delta)
        }
    }

    private fun completar(
        evento: EventoTramo.Completado,
        frases: MutableList<Frase>,
    ) {
        val id = evento.tramoId
        val pasada = evento.pasada
        val vivo = vivos[id]
        if (vivo != null) {
            val tramo = vivo.tramo
            vivo.parciales += parcial(vivo, TipoCorte.SECTOR, tramo.sectores.size + 1, tramo.metaM, pasada.totalMs)
            vivo.parciales +=
                parcial(vivo, TipoCorte.MICROSECTOR, tramo.microsectores.size + 1, tramo.metaM, pasada.totalMs)
            val mejor = vivo.mejor
            val delta = mejor?.let { pasada.totalMs - it.totalMs }
            val nuevoMejor = mejor == null || pasada.totalMs < mejor.totalMs
            if (delta != null && esReciente(vivo)) frases += Delta.fraseMeta(id, vivo.idPasada, delta)
            val resultado = ResultadoCrono(pasada.totalMs, delta, nuevoMejor, mejor?.totalMs)
            val hecha = Completada(pasada, vivo.inicioMs, vivo.inicioFijacionMs)
            terminado = Terminado(hecha, tramo, resultado, vivo.parciales.toList())
            vivos.remove(id)
            completadas += hecha
            guardarYa(hecha)
        }
        if (mejores[id].let { it == null || pasada.totalMs < it.totalMs }) mejores[id] = pasada
    }

    /** Guarda la pasada ya, sin bloquear las fijaciones, y republica el resultado con sus ids si sale bien. */
    private fun guardarYa(hecha: Completada) {
        val alcance = alcance ?: return
        // Fuera de la cancelación de la conducción: el cierre espera a este trabajo (join) y no se pierde la pasada.
        hecha.guardado =
            alcance.launch(NonCancellable) {
                guardar {
                    val id = repositorio.guardarPasada(hecha.pasada, hecha.origen(recorridoId))
                    hecha.idGuardada = id
                    publicarIds(hecha, id)
                }
            }
    }

    private fun publicarIds(
        hecha: Completada,
        id: Long,
    ) = synchronized(cerrojo) {
        val actual = terminado?.takeIf { it.completada === hecha } ?: return@synchronized
        val resultado = actual.resultado.copy(tramoId = hecha.pasada.tramoId, pasadaId = id)
        val nuevo = Terminado(hecha, actual.tramo, resultado, actual.parciales)
        terminado = nuevo
        if (vivos.isEmpty()) publicar(estadoTerminado(nuevo, ahoraMs()))
    }

    private fun esReciente(vivo: Vivo): Boolean = vivos.values.lastOrNull() === vivo

    @Suppress("LongParameterList") // los datos de un corte: tipo, número, dónde y cuándo
    private fun parcial(
        vivo: Vivo,
        tipo: TipoCorte,
        numero: Int,
        hastaM: Double,
        tMs: Long,
    ): ParcialCrono {
        val previo = vivo.parciales.lastOrNull { it.tipo == tipo }
        val desdeM = previo?.distanciaM ?: 0.0
        val tDesdeMs = previo?.tiempoMs ?: 0L
        val mejorHasta = vivo.mejor?.let { tiempoEn(it, hastaM) }
        val mejorDesde = vivo.mejor?.let { tiempoEn(it, desdeM) }
        val diferencia =
            if (mejorHasta != null && mejorDesde != null) {
                Math.round((tMs - tDesdeMs) - (mejorHasta - mejorDesde))
            } else {
                null
            }
        return ParcialCrono(
            tipo,
            numero,
            hastaM,
            tMs,
            tMs - tDesdeMs,
            diferencia,
            mejorHasta?.let { Math.round(tMs - it) },
        )
    }

    /** Tiempo de [pasada] en [distanciaM], acotado a lo que cubre (las marcas guardadas redondean al milímetro). */
    private fun tiempoEn(
        pasada: Pasada,
        distanciaM: Double,
    ): Double? {
        val primera = pasada.tiempos.firstOrNull() ?: return null
        val ultima = pasada.tiempos.last()
        return pasada.tiempoEn(distanciaM.coerceIn(primera.distanciaM, ultima.distanciaM))
    }

    private fun estado(f: Fijacion): CronoEnCurso? {
        val ahora = ahoraMs()
        val activo = vivos.values.lastOrNull()
        val cerrado = terminado
        return when {
            activo != null -> {
                estadoVivo(activo, f, ahora)
            }

            cerrado != null -> {
                estadoTerminado(cerrado, ahora)
            }

            else -> {
                val salida = salidaEpochMs
                CronoEnCurso(
                    tramo = null,
                    transcurridoMs = if (salida != null) ahora - salida else 0L,
                    actualizadoEnMs = ahora,
                    enMarcha = salida != null,
                    esperaSalidaManual = salida == null && manual,
                )
            }
        }
    }

    private fun estadoTerminado(
        cerrado: Terminado,
        ahora: Long,
    ) = CronoEnCurso(
        tramo = descripcion(cerrado.tramo),
        transcurridoMs = cerrado.resultado.totalMs,
        actualizadoEnMs = ahora,
        progresoM = cerrado.tramo.metaM,
        deltaMs = cerrado.resultado.deltaMs,
        parciales = cerrado.parciales,
        ultimaMarca = cerrado.parciales.lastOrNull(),
        hayMejorPasada = cerrado.resultado.mejorAnteriorMs != null,
        resultado = cerrado.resultado,
    )

    private fun estadoVivo(
        vivo: Vivo,
        f: Fijacion,
        ahora: Long,
    ): CronoEnCurso {
        val cronometro = detector?.cronometro(vivo.tramo.id)
        val transcurrido = cronometro?.transcurridoMs() ?: (f.tiempoMs - vivo.inicioFijacionMs)
        val progreso = cronometro?.progresoM ?: 0.0
        val delta = vivo.mejor?.let { m -> tiempoEn(m, progreso)?.let { Math.round(transcurrido - it) } }
        return CronoEnCurso(
            tramo = descripcion(vivo.tramo),
            transcurridoMs = transcurrido,
            actualizadoEnMs = ahora,
            progresoM = progreso,
            deltaMs = delta,
            parciales = vivo.parciales.toList(),
            ultimaMarca = vivo.parciales.lastOrNull(),
            hayMejorPasada = vivo.mejor != null,
        )
    }

    private fun descripcion(tramo: Tramo) =
        TramoCronometrado(tramo.id, null, tramo.longitudM, tramo.metaM, tramo.sectores, tramo.microsectores)

    /**
     * Fin de la conducción: espera los guardados en curso, deja de publicar, reintenta las pasadas sin guardar y crea
     * el tramo del [recorrido] (null si no llegó a un kilómetro; [puntos] lo lee solo si hace falta). Un fallo de la
     * base no se propaga.
     */
    suspend fun cerrar(
        recorrido: Recorrido?,
        puntos: suspend () -> List<PuntoRecorrido>,
    ) {
        completadas.forEach { it.guardado?.join() }
        publicar(null)
        val recorridoId = recorrido?.id
        // Las que no se pudieron guardar al cruzar la meta se reintentan; las ya guardadas no se repiten.
        for (c in completadas.filter { it.idGuardada == null }) {
            guardar { repositorio.guardarPasada(c.pasada, c.origen(recorridoId)) }
        }
        var creado: Tramo? = null
        if (recorrido != null) guardar { creado = crearTramoNuevo(recorrido.id, puntos()) }
        // Las ediciones dictadas sin tramo activo pasan al tramo recién creado; sin él se descartan.
        guardar { anclarEdiciones(creado) }
    }

    private suspend fun guardar(accion: suspend () -> Unit) {
        try {
            accion()
        } catch (e: SQLiteException) {
            alFallar(e)
        } catch (e: IllegalStateException) {
            alFallar(e)
        }
    }

    private fun Completada.origen(recorridoId: Long?) =
        OrigenPasada(inicioMs, recorridoId, recorridoId?.let { desdeMs }, recorridoId?.let { hastaMs })

    private suspend fun crearTramoNuevo(
        recorridoId: Long,
        todos: List<PuntoRecorrido>,
    ): Tramo? {
        // El tramo empieza en la salida, no en el rato parado al entrar al mapa; sin salida (solo deriva) no hay tramo.
        val salida = salidaEpochMs
        val puntos = if (salida == null) emptyList() else todos.filter { it.tiempoMs >= salida }
        val traza = puntos.map { PuntoGeo(it.lat, it.lon) }
        val repetido =
            completadas.any { c ->
                val guardado = catalogo[c.pasada.tramoId]
                guardado != null && Cobertura.fraccion(traza, guardado.traza) > UMBRAL_COINCIDENCIA
            }
        val tramo = if (repetido || puntos.isEmpty()) null else TramoDesdeRecorrido.crear(nuevoId(), traza)
        val primera = tramo?.let { pasadaDelRecorrido(it, puntos) }
        if (tramo != null && primera != null) {
            repositorio.crear(tramo, null, nombrar(tramo.traza), ahoraMs(), primera.pasada, primera.origen(recorridoId))
            return tramo
        }
        return null
    }

    /** Repite el recorrido por el cronómetro del [tramo] recién creado: los tiempos salen de los puntos grabados. */
    private fun pasadaDelRecorrido(
        tramo: Tramo,
        puntos: List<PuntoRecorrido>,
    ): Completada? {
        var ahora = 0L
        val cronometro = Cronometro(tramo, reloj = { ahora })
        var desdeMs = 0L
        for (p in puntos) {
            ahora = p.tiempoMs
            val eventos = cronometro.alFijar(Fijacion(PuntoGeo(p.lat, p.lon), p.tiempoMs, p.velocidadMs, null, null))
            eventos.filterIsInstance<EventoCrono.Salida>().forEach { desdeMs = it.tInicioMs }
            if (cronometro.estado == Cronometro.Estado.TERMINADO) break
        }
        val ultimaMs = puntos.lastOrNull()?.tiempoMs ?: return null
        // Los puntos van en el reloj de la sesión (el de ahoraMs): la salida epoch es ahora menos lo que falta
        // hasta el último.
        return cronometro.pasada()?.let { Completada(it, ahoraMs() - (ultimaMs - desdeMs), desdeMs) }
    }

    companion object {
        /** Fracción del recorrido sobre un tramo completado a partir de la cual no se crea otro tramo. */
        const val UMBRAL_COINCIDENCIA = 0.9
    }
}
