package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.alertas.SesionAlertas
import app.trombito.rallycopilot.data.notas.FabricaNotasSesion
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.AcumuladorRecorrido
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.DetectorSalidaRuta
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.SeguidorRuta
import app.trombito.rallycopilot.motor.velocidad.EstimadorVelocidad
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.AvisosManiobra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que el usuario pidió al pulsar «Empezar»: modo libre o guiado hacia un destino con la ruta ya calculada. */
sealed interface SolicitudNavegacion {
    data object Libre : SolicitudNavegacion

    data class Guiada(
        val destino: Destino,
        val perfil: PerfilRuta,
        val ruta: Ruta,
    ) : SolicitudNavegacion
}

/**
 * Lógica de una conducción, sin Android: recoge las fijaciones, sigue la ruta, recalcula al salirse de ella y graba el
 * recorrido por lotes. Todo el estado se toca desde una sola corrutina (o un dispatcher de un hilo). [reloj] da ms
 * epoch (hora de pared: llegada prevista y marcas del recorrido) y [publicar] recibe cada cambio de estado. La
 * detección de salida de ruta y el reintento del recálculo usan el reloj monótono de la fijación, que no salta con
 * cambios de hora. Nunca registra coordenadas en logs. Si hay [fabricaNotas], genera y publica las notas de rally
 * de la conducción (guiada: hoja de la ruta; libre: hoja de la carretera prevista); sin ella, no hay notas. Ignora las
 * fijaciones con `precisionM` > [PRECISION_MAX_M] (posición aproximada de interior): ni se graban ni cuentan. Si hay
 * [copiloto], en cada fijación válida le pasa las notas vigentes y el progreso de la ruta para que hable. Si hay
 * [alertas] (radares, tramos, exceso de velocidad, incidencias), en cada fijación válida calcula las suyas, las publica
 * y se las suma al copiloto como candidatas.
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
        val destinoNombre = (solicitud as? SolicitudNavegacion.Guiada)?.destino?.nombre
        val id = recorridos.empezar(destinoNombre)
        val ejecucion = Ejecucion(id, solicitud)
        try {
            ejecucion.ejecutar()
        } finally {
            ejecucion.cerrarNotas()
            copiloto?.cerrar()
            alertas?.cerrar()
            withContext(NonCancellable) {
                ejecucion.volcar()
                recorridos.terminar(id)
            }
        }
    }

    private inner class Ejecucion(
        private val id: Long,
        solicitud: SolicitudNavegacion,
    ) {
        private val inicioMs = reloj()
        private val guia: Guia? = (solicitud as? SolicitudNavegacion.Guiada)?.let { Guia(it) }
        private val lote = ArrayList<PuntoRecorrido>(TAMANO_LOTE)
        private val acumulador = AcumuladorRecorrido()
        private val notas = fabricaNotas?.crear()
        private var llegado = false
        private var ultimaFijacionMs = 0L
        private var ultimaPosicion: PuntoGeo? = null

        /** Rumbo del movimiento para las fijaciones sin rumbo fiable (emulador, rumbo impreciso). */
        private val movimiento = EstimadorVelocidad()

        suspend fun ejecutar() =
            coroutineScope {
                if (guia != null) notas?.iniciarGuiado(guia.ruta.puntos) else notas?.iniciarLibre()
                val ajustesCopiloto = copiloto?.let { launch { it.seguirAjustes() } }
                val datosAlertas = alertas?.arrancar(this, guia?.ruta?.puntos)
                publicar(estadoActual())
                fuente.fijaciones().takeWhile { procesar(it, this) }.collect()
                guia?.recalculo?.cancel()
                ajustesCopiloto?.cancel()
                datosAlertas?.cancel()
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
            cruda: Fijacion,
            alcance: CoroutineScope,
        ): Boolean {
            // Posición aproximada (fused/red bajo techo): sirve para pintar, no para grabar, sumar ni seguir la ruta.
            if ((cruda.precisionM ?: 0.0) > PRECISION_MAX_M) return true
            // Sin rumbo fiable, el del movimiento (lo usan la predicción en libre y el sentido contrario).
            val velocidadEstimada = movimiento.actualizar(cruda)
            val fijacion = if (cruda.rumboGrados != null) cruda else cruda.copy(rumboGrados = movimiento.rumbo())
            val ahora = reloj()
            ultimaFijacionMs = fijacion.tiempoMs
            ultimaPosicion = fijacion.punto
            registrar(fijacion, ahora)
            if (guia != null) {
                val progreso = guia.seguidor.actualizar(fijacion.punto, ahora)
                guia.progreso = progreso
                llegado = progreso.llegado
                if (!llegado) {
                    notas?.posicionGuiada(progreso.progresoM, fijacion.velocidadMs)
                    vigilarSalida(guia, fijacion, alcance)
                }
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
            if (fuera && guia.recalculo?.isActive != true && fijacion.tiempoMs >= guia.reintentoMs) {
                guia.recalculo = alcance.launch { recalcular(guia, fijacion.punto) }
            }
        }

        private suspend fun recalcular(
            guia: Guia,
            origen: PuntoGeo,
        ) {
            guia.recalculando = true
            publicar(estadoActual())
            val resultado = enrutador.ruta(origen, guia.destino.punto, guia.perfil)
            if (resultado is ResultadoRuta.Ok && resultado.ruta.puntos.size >= 2) {
                // Se proyecta ya la última posición: sin progreso el banner enseñaría la salida de la ruta nueva (0 m).
                guia.usar(resultado.ruta, ultimaPosicion, reloj())
                notas?.nuevaRuta(resultado.ruta.puntos)
                alertas?.nuevaRuta(resultado.ruta.puntos)
            } else {
                guia.reintentoMs = ultimaFijacionMs + REINTENTO_MS
            }
            guia.recalculando = false
            publicar(estadoActual())
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
            guia?.estado() ?: EstadoNavegacion.Libre(inicioMs, acumulador.distanciaM)
    }

    private class Guia(
        solicitud: SolicitudNavegacion.Guiada,
    ) {
        val destino = solicitud.destino
        val perfil = solicitud.perfil
        var ruta: Ruta = solicitud.ruta
        var seguidor = SeguidorRuta(ruta)
        var rutaVoz = rutaVoz(ruta, seguidor)
        var detector = DetectorSalidaRuta()
        var progreso: EstadoRuta? = null
        var recalculando = false
        var reintentoMs = 0L
        var recalculo: Job? = null

        fun usar(
            nueva: Ruta,
            posicion: PuntoGeo?,
            ahoraMs: Long,
        ) {
            ruta = nueva
            seguidor = SeguidorRuta(nueva)
            rutaVoz = rutaVoz(nueva, seguidor)
            detector = DetectorSalidaRuta()
            progreso = posicion?.let { seguidor.actualizar(it, ahoraMs) }
        }

        fun estado() = EstadoNavegacion.Guiando(destino, perfil, ruta, progreso, recalculando)

        private fun rutaVoz(
            ruta: Ruta,
            seguidor: SeguidorRuta,
        ) = RutaVoz(AvisosManiobra(ruta.maniobras, seguidor.posicionesManiobrasM), seguidor.posicionesManiobrasM)
    }

    companion object {
        const val TAMANO_LOTE = 10

        /** Las fijaciones con peor precisión (m) se ignoran al grabar, acumular distancia, seguir la ruta y anotar. */
        const val PRECISION_MAX_M = 30.0
        const val ESPERA_LLEGADA_MS = 10_000L
        const val REINTENTO_MS = 10_000L
    }
}
