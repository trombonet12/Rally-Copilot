package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.alertas.SesionAlertas
import app.trombito.rallycopilot.data.notas.EdicionEnMapa
import app.trombito.rallycopilot.data.notas.FabricaNotasSesion
import app.trombito.rallycopilot.data.notas.NotasSesion
import app.trombito.rallycopilot.data.notas.RutaMedida
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.tramos.SesionCrono
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.navegacion.aplicar
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.PosicionEstimada
import app.trombito.rallycopilot.motor.geo.AcumuladorRecorrido
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.DetectorSalidaRuta
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.FrasesParada
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.SeguidorRuta
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.PrecisionGps
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que el usuario pidió al pulsar «Empezar»: modo libre o guiado hacia un destino con la ruta ya calculada. En el
 * guiado, [Guiada.paradas] son las intermedias en el orden en que se añadieron y la [Guiada.ruta] las lleva en
 * `indicesParadas` (una por parada; las que la ruta no incluya se ignoran).
 */
sealed interface SolicitudNavegacion {
    data object Libre : SolicitudNavegacion

    /** Pasada de reconocimiento del tramo [tramoId]: lee sus notas fusionadas y no cuenta para tiempos. */
    data class Reconocimiento(
        val tramoId: String,
    ) : SolicitudNavegacion

    data class Guiada(
        val destino: Destino,
        val perfil: PerfilRuta,
        val ruta: Ruta,
        val paradas: List<Destino> = emptyList(),
    ) : SolicitudNavegacion
}

/**
 * Lógica de una conducción, sin Android: recoge las fijaciones, sigue la ruta, recalcula al salirse de ella y graba el
 * recorrido por lotes. Todo el estado se toca desde una sola corrutina (o un dispatcher de un hilo). [reloj] da ms
 * epoch (hora de pared: llegada prevista y marcas del recorrido) y [publicar] recibe cada cambio de estado. La
 * detección de salida de ruta y el reintento del recálculo usan el reloj monótono de la fijación, que no salta con
 * cambios de hora. Nunca registra coordenadas en logs. Si hay [fabricaNotas], genera y publica las notas de rally
 * de la conducción (guiada: hoja de la ruta; libre: hoja de la carretera prevista); sin ella, no hay notas. Ignora las
 * fijaciones con `precisionM` > [PrecisionGps.FIJACION_UTIL_M] (posición aproximada de interior): ni se graban ni
 * cuentan. Recibe de la fuente compartida las posiciones con velocidad y rumbo ya estimados. Si hay [copiloto],
 * en cada fijación válida le pasa las notas vigentes y el progreso de la ruta para que hable. Si hay
 * [alertas] (radares, tramos, exceso de velocidad, incidencias), en cada fijación válida calcula las suyas, las publica
 * y se las suma al copiloto como candidatas. Con [ediciones] aplica a la hoja las notas editadas del tramo activo (y
 * las
 * dictadas sin tramo); en [SolicitudNavegacion.Reconocimiento] la hoja es la del tramo (de [tramos]) y no hay crono.
 * Si hay [crono] y [cronometrar] da verdadero al empezar, cronometra la conducción: detecta los tramos guardados,
 * publica el cronómetro, le pasa al copiloto el delta de sectores y meta y, al cerrar, guarda las pasadas y el tramo.
 *
 * Paradas: al alcanzar la próxima (a ≤ 30 m o al pasarla) la siguiente pasa a ser el objetivo y el copiloto lo dice
 * («parada alcanzada, siguiente …»); un recálculo conserva solo las paradas pendientes. Las [ordenes] de edición
 * (añadir, quitar, mover) de la UI recalculan la ruta desde la posición actual.
 */
@Suppress("LongParameterList") // colaboradores de la sesión: fuente, enrutador, grabación, reloj, salidas y notas
class SesionNavegacion(
    private val fuente: FuenteUbicacion,
    private val enrutador: Enrutador,
    private val recorridos: RepositorioRecorridos,
    private val reloj: () -> Long,
    private val publicar: (EstadoNavegacion) -> Unit,
    private val motivoSinGps: String,
    private val fabricaNotas: FabricaNotasSesion? = null,
    private val copiloto: CopilotoVoz? = null,
    private val alertas: SesionAlertas? = null,
    private val ordenes: ReceiveChannel<OrdenParadas>? = null,
    private val crono: SesionCrono? = null,
    private val ediciones: RepositorioEdiciones? = null,
    private val tramos: RepositorioTramos? = null,
    private val cronometrar: suspend () -> Boolean = { true },
) {
    /** Cierra con la regla de siempre (descarta < 1 km) los recorridos que quedaron abiertos al cerrarse la app. */
    suspend fun cerrarHuerfanos() {
        for (id in recorridos.idsEnCurso()) recorridos.terminar(id)
    }

    /**
     * Conduce hasta que se cancele, llegue al destino (y pasen [ESPERA_LLEGADA_MS]) o el GPS deje de dar posiciones.
     * Al salir, por cualquier motivo, vuelca los puntos pendientes y cierra el recorrido.
     */
    suspend fun correr(solicitud: SolicitudNavegacion) {
        val destinoNombre =
            (solicitud as? SolicitudNavegacion.Guiada)?.destino?.nombre
                ?: NOMBRE_RECONOCIMIENTO.takeIf { solicitud is SolicitudNavegacion.Reconocimiento }
        val id = recorridos.empezar(destinoNombre)
        // «Cronometrar trayectos» se lee una vez al empezar: sin él, esta conducción no crea ni detecta tramos.
        val cronometra = crono != null && solicitud !is SolicitudNavegacion.Reconocimiento && cronometrar()
        val ejecucion = Ejecucion(id, solicitud, crono.takeIf { cronometra })
        try {
            ejecucion.ejecutar()
        } finally {
            ejecucion.cerrarNotas()
            copiloto?.cerrar()
            alertas?.cerrar()
            withContext(NonCancellable) {
                ejecucion.volcar()
                val recorrido = recorridos.terminar(id)
                if (cronometra) {
                    crono?.cerrar(recorrido) { recorridos.puntos(id) }
                } else if (crono != null && solicitud !is SolicitudNavegacion.Reconocimiento) {
                    // Sin cronometraje no hay tramo al que anclar lo dictado: se descarta.
                    ediciones?.anclarPendientes(null)
                }
            }
        }
    }

    @Suppress("TooManyFunctions") // el estado de la conducción es uno: fijaciones, recálculo y paradas lo comparten
    private inner class Ejecucion(
        private val id: Long,
        solicitud: SolicitudNavegacion,
        private val cronometro: SesionCrono?,
    ) {
        private val inicioMs = reloj()
        private val guia: Guia? = (solicitud as? SolicitudNavegacion.Guiada)?.let { Guia(it) }
        private val lote = ArrayList<PuntoRecorrido>(TAMANO_LOTE)
        private val acumulador = AcumuladorRecorrido()
        private val notas = fabricaNotas?.crear()
        private val reconocimiento = solicitud as? SolicitudNavegacion.Reconocimiento

        /** Tramo reconocido (cargado al empezar) o, conduciendo, el tramo actual: ahí se anclan las ediciones. */
        private val tramoActivo = MutableStateFlow<Tramo?>(null)
        private var tramoReconocido: Tramo? = null
        private var llegado = false
        private var ultimaFijacionMs = 0L
        private var ultimaPosicion: PuntoGeo? = null

        suspend fun ejecutar() =
            coroutineScope {
                tramoReconocido = reconocimiento?.let { tramos?.tramo(it.tramoId) }
                tramoActivo.value = tramoReconocido
                val reconocido = tramoReconocido
                when {
                    guia != null -> notas?.iniciarGuiado(guia.ruta.puntos)
                    reconocido != null -> notas?.iniciarReconocimiento(reconocido.id, reconocido.traza)
                    else -> notas?.iniciarLibre()
                }
                val vigiladas =
                    if (notas != null && ediciones != null) launch { vigilarEdiciones(notas, ediciones) } else null
                val ajustesCopiloto = copiloto?.let { launch { it.seguirAjustes() } }
                val datosAlertas = alertas?.arrancar(this, guia?.ruta?.puntos)
                val cargaTramos = cronometro?.arrancar(this, id)
                val edicion =
                    if (guia != null && ordenes != null) {
                        launch { for (orden in ordenes) editar(guia, orden, this@coroutineScope) }
                    } else {
                        null
                    }
                publicar(estadoActual())
                fuente.posiciones().takeWhile { procesar(it, this) }.collect()
                edicion?.cancel()
                vigiladas?.cancel()
                guia?.recalculo?.cancel()
                ajustesCopiloto?.cancel()
                datosAlertas?.cancel()
                cargaTramos?.cancel()
                cerrarNotas()
                copiloto?.cerrar()
                alertas?.cerrar()
                if (llegado && guia != null) {
                    publicar(EstadoNavegacion.Llegada(guia.destino))
                    delay(ESPERA_LLEGADA_MS)
                } else {
                    publicar(EstadoNavegacion.Error(motivoSinGps))
                }
            }

        /** Devuelve false cuando hay que dejar de recoger posiciones (llegada). */
        private suspend fun procesar(
            posicion: PosicionEstimada,
            alcance: CoroutineScope,
        ): Boolean {
            val cruda = posicion.cruda
            // Posición aproximada (fused/red bajo techo): sirve para pintar, no para grabar, sumar ni seguir la ruta.
            if (!posicion.precisa) return true
            // Velocidad y rumbo ya estimados por la fuente compartida; sin rumbo fiable, el del movimiento (lo usan
            // la predicción en libre y el sentido contrario).
            val velocidadEstimada = posicion.velocidadMs
            val fijacion = cruda.copy(rumboGrados = posicion.rumboGrados)
            val ahora = reloj()
            ultimaFijacionMs = fijacion.tiempoMs
            ultimaPosicion = fijacion.punto
            registrar(fijacion, ahora)
            cronometro?.alFijar(fijacion)?.forEach { copiloto?.anunciarCrono(it) }
            cronometro?.tramoActivo().let {
                if (cronometro != null &&
                    it?.id != tramoActivo.value?.id
                ) {
                    tramoActivo.value = it
                }
            }
            if (guia != null) {
                val progreso = guia.seguidor.actualizar(fijacion.punto, ahora)
                guia.progreso = progreso
                paradasAlcanzadas(guia, progreso, alcance)
                llegado = progreso.llegado
                if (!llegado) {
                    notas?.posicionGuiada(progreso.progresoM, fijacion.velocidadMs)
                    vigilarSalida(guia, fijacion, alcance)
                }
            } else if (tramoReconocido != null) {
                notas?.posicionReconocimiento(fijacion)
            } else {
                notas?.posicionLibre(fijacion)
            }
            if (!llegado) {
                hablar(fijacion, velocidadEstimada)
                publicar(estadoActual())
            }
            return !llegado
        }

        /** Calcula las alertas de la fijación y le pasa al copiloto, con ellas, lo que toca decir. */
        private suspend fun hablar(
            fijacion: Fijacion,
            velocidadEstimada: Double?,
        ) {
            val vigentes = notas?.instantanea
            val progresoM = guia?.progreso?.progresoM
            val frases =
                alertas
                    ?.alFijar(
                        fijacion,
                        velocidadEstimada ?: fijacion.velocidadMs,
                        progresoM,
                        vigentes?.posicionM,
                        vigentes?.takeIf { it.origen == OrigenNotas.LIBRE }?.traza,
                    ).orEmpty()
            copiloto?.alFijar(vigentes, guia?.rutaVoz, progresoM, fijacion.velocidadMs, frases)
        }

        private fun vigilarSalida(
            guia: Guia,
            fijacion: Fijacion,
            alcance: CoroutineScope,
        ) {
            val progreso = checkNotNull(guia.progreso)
            val fuera =
                guia.detector.actualizar(
                    progreso.distanciaALaRutaM,
                    fijacion.tiempoMs,
                    progreso.sentidoContrario,
                )
            // «Recalculando» por voz solo si la salida de la ruta dura más de 5 s (lo mide el copiloto).
            copiloto?.alRecalcular(fuera, fijacion.tiempoMs)
            if (fuera && guia.recalculo?.isActive != true && fijacion.tiempoMs >= guia.reintentoMs) {
                guia.recalculo = alcance.launch { recalcular(guia, fijacion.punto) }
            }
        }

        /**
         * Recalcula desde [origen] hacia el destino pasando por las paradas que [guia] tiene pendientes. Si se cancela
         * (otra edición o una parada alcanzada mientras tanto) no toca el estado: quien cancela relanza.
         */
        private suspend fun recalcular(
            guia: Guia,
            origen: PuntoGeo,
        ) {
            guia.recalculando = true
            publicar(estadoActual())
            val paradas = guia.paradas
            val destino = guia.destino
            val resultado = enrutador.rutaConParadas(origen, paradas.map { it.punto }, destino.punto, guia.perfil)
            if (resultado is ResultadoRuta.Ok && resultado.ruta.puntos.size >= 2) {
                // Se proyecta ya la última posición: sin progreso el banner enseñaría la salida de la ruta nueva (0 m).
                guia.usar(resultado.ruta, paradas, destino, ultimaPosicion, reloj())
                notas?.nuevaRuta(resultado.ruta.puntos)
                alertas?.nuevaRuta(resultado.ruta.puntos)
            } else {
                guia.reintentoMs = ultimaFijacionMs + REINTENTO_MS
                // La ruta vigente sigue sin las paradas ni el destino pedidos: vuelven a ser los de esa ruta.
                guia.paradas = guia.paradasEnRuta
                guia.destino = guia.destinoEnRuta
            }
            guia.recalculando = false
            guia.progreso?.let { paradasAlcanzadas(guia, it, null) }
            publicar(estadoActual())
        }

        /** Aplica una orden de la UI sobre las paradas o el destino y recalcula desde la posición actual. */
        private fun editar(
            guia: Guia,
            orden: OrdenParadas,
            alcance: CoroutineScope,
        ) {
            val cambio =
                if (orden is OrdenParadas.CambiarDestino) {
                    (orden.destino != guia.destino).also { if (it) guia.destino = orden.destino }
                } else {
                    val nuevas = orden.aplicar(guia.paradas)
                    (nuevas != guia.paradas).also { if (it) guia.paradas = nuevas }
                }
            if (cambio) relanzarRecalculo(guia, alcance)
        }

        private fun relanzarRecalculo(
            guia: Guia,
            alcance: CoroutineScope,
        ) {
            guia.recalculo?.cancel()
            val origen = ultimaPosicion ?: guia.ruta.puntos.first()
            // Ya, sin esperar al enrutador: la UI enseña la lista nueva y «recalculando».
            guia.recalculando = true
            publicar(estadoActual())
            guia.recalculo = alcance.launch { recalcular(guia, origen) }
        }

        /**
         * Registra las paradas que [progreso] da por alcanzadas desde la última vez: las quita de las pendientes, se lo
         * dice al copiloto y, si había un recálculo en marcha (con esa parada dentro), lo relanza. [alcance] es null
         * cuando se llama desde el propio recálculo (la ruta nueva ya parte de las pendientes).
         */
        private fun paradasAlcanzadas(
            guia: Guia,
            progreso: EstadoRuta,
            alcance: CoroutineScope?,
        ) {
            val nuevas = progreso.paradasAlcanzadas - guia.alcanzadasEnRuta
            if (nuevas <= 0) return
            repeat(nuevas) {
                val numero = guia.alcanzadasEnRuta
                guia.alcanzadasEnRuta++
                guia.paradaAlcanzada()
                copiloto?.anunciarParada(guia.paradasHechas, FrasesParada.alcanzada(guia.ruta, numero))
            }
            if (alcance != null && guia.recalculo?.isActive == true) relanzarRecalculo(guia, alcance)
        }

        private suspend fun registrar(
            fijacion: Fijacion,
            ahora: Long,
        ) {
            if (!acumulador.anadir(fijacion.punto)) return
            lote += PuntoRecorrido(fijacion.punto.lat, fijacion.punto.lon, ahora, fijacion.velocidadMs)
            if (lote.size >= TAMANO_LOTE) volcar()
        }

        /** Publica «sin notas» y cancela su trabajo en segundo plano; se puede llamar más de una vez. */
        fun cerrarNotas() {
            notas?.terminar()
        }

        suspend fun volcar() {
            if (lote.isEmpty()) return
            val pendientes = lote.toList()
            lote.clear()
            recorridos.anadirPuntos(id, pendientes)
        }

        private fun estadoActual(): EstadoNavegacion =
            guia?.estado() ?: EstadoNavegacion.Libre(inicioMs, acumulador.distanciaM, tramoReconocido?.id)

        /**
         * Lleva a las notas las ediciones que valen ahora: las del tramo activo (en metros de su traza, convertidas a
         * coordenada) y las dictadas sin tramo (ya con la suya), en el orden en que se hicieron.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private suspend fun vigilarEdiciones(
            notas: NotasSesion,
            ediciones: RepositorioEdiciones,
        ) {
            tramoActivo
                .flatMapLatest { tramo ->
                    val trazado = tramo?.let { RutaMedida(it.traza) }
                    val delTramo = tramo?.let { ediciones.observarDeTramo(it.id) } ?: flowOf(emptyList())
                    combine(delTramo, ediciones.observarPendientes()) { guardadas, sinTramo ->
                        val ancladas =
                            guardadas.mapNotNull { e ->
                                trazado?.let { EdicionEnMapa(it.puntoEn(e.posicionM), e.accion, e.marcaTiempoMs) }
                            }
                        ancladas +
                            sinTramo.mapNotNull { e ->
                                e.posicion?.let { EdicionEnMapa(it, e.accion, e.marcaTiempoMs) }
                            }
                    }
                }.collect { notas.fijarEdiciones(it) }
        }
    }

    private class Guia(
        solicitud: SolicitudNavegacion.Guiada,
    ) {
        /** Destino final que se quiere; con un cambio en marcha difiere del de la ruta hasta que se recalcula. */
        var destino = solicitud.destino

        /** Destino final al que lleva [ruta]. */
        var destinoEnRuta = solicitud.destino
        val perfil = solicitud.perfil
        var ruta: Ruta = solicitud.ruta

        /** Paradas pendientes que se quieren (las que enseña la UI); con una edición en marcha difieren de la ruta. */
        var paradas: List<Destino> = solicitud.paradas.take(ruta.indicesParadas.size)

        /** Paradas pendientes que [ruta] realmente incluye. */
        var paradasEnRuta: List<Destino> = paradas
        var paradasHechas = 0
        var alcanzadasEnRuta = 0
        var seguidor = SeguidorRuta(ruta)
        var rutaVoz = rutaVoz(ruta, seguidor)
        var detector = DetectorSalidaRuta()
        var progreso: EstadoRuta? = null
        var recalculando = false
        var reintentoMs = 0L
        var recalculo: Job? = null

        /** La próxima parada de la ruta se alcanzó: deja de estar pendiente. */
        fun paradaAlcanzada() {
            val llegada = paradasEnRuta.firstOrNull() ?: return
            paradasEnRuta = paradasEnRuta.drop(1)
            if (paradas.firstOrNull() == llegada) paradas = paradas.drop(1)
            paradasHechas++
        }

        /** [nueva] se calculó con las [pendientes] (las paradas de entonces, todas aún por alcanzar). */
        fun usar(
            nueva: Ruta,
            pendientes: List<Destino>,
            destinoNuevo: Destino,
            posicion: PuntoGeo?,
            ahoraMs: Long,
        ) {
            paradasEnRuta = pendientes.take(nueva.indicesParadas.size)
            destinoEnRuta = destinoNuevo
            alcanzadasEnRuta = 0
            ruta = nueva
            seguidor = SeguidorRuta(nueva)
            rutaVoz = rutaVoz(nueva, seguidor)
            detector = DetectorSalidaRuta()
            progreso = posicion?.let { seguidor.actualizar(it, ahoraMs) }
        }

        fun estado() = EstadoNavegacion.Guiando(destino, perfil, ruta, progreso, recalculando, paradas, paradasHechas)

        private fun rutaVoz(
            ruta: Ruta,
            seguidor: SeguidorRuta,
        ) = RutaVoz(AvisosManiobra(ruta.maniobras, seguidor.posicionesManiobrasM), seguidor.posicionesManiobrasM)
    }

    companion object {
        /** Puntos por escritura en Room (~30 s): menos transacciones con la pantalla apagada. */
        const val TAMANO_LOTE = 30

        /** Nombre con que se graba el recorrido de una pasada de reconocimiento. */
        const val NOMBRE_RECONOCIMIENTO = "Reconocimiento"

        const val ESPERA_LLEGADA_MS = 10_000L
        const val REINTENTO_MS = 10_000L
    }
}
