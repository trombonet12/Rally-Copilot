package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.DetectorSalidaRuta
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.SeguidorRuta
import app.trombito.rallycopilot.motor.velocidad.Fijacion
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
 * cambios de hora. Nunca registra coordenadas en logs.
 */
class SesionNavegacion(
    private val fuente: FuenteUbicacion,
    private val enrutador: Enrutador,
    private val recorridos: RepositorioRecorridos,
    private val reloj: () -> Long,
    private val publicar: (EstadoNavegacion) -> Unit,
    private val motivoSinGps: String,
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
        private var ultimoContado: PuntoGeo? = null
        private var distanciaM = 0.0
        private var llegado = false
        private var ultimaFijacionMs = 0L

        suspend fun ejecutar() =
            coroutineScope {
                publicar(estadoActual())
                fuente.fijaciones().takeWhile { procesar(it, this) }.collect()
                guia?.recalculo?.cancel()
                if (llegado && guia != null) {
                    publicar(EstadoNavegacion.Llegada(guia.destino))
                    delay(ESPERA_LLEGADA_MS)
                } else {
                    publicar(EstadoNavegacion.Error(motivoSinGps))
                }
            }

        /** Devuelve false cuando hay que dejar de recoger posiciones (llegada). */
        private suspend fun procesar(
            fijacion: Fijacion,
            alcance: CoroutineScope,
        ): Boolean {
            val ahora = reloj()
            ultimaFijacionMs = fijacion.tiempoMs
            registrar(fijacion, ahora)
            if (guia != null) {
                val progreso = guia.seguidor.actualizar(fijacion.punto, ahora)
                guia.progreso = progreso
                llegado = progreso.llegado
                if (!llegado) vigilarSalida(guia, fijacion, alcance)
            }
            if (!llegado) publicar(estadoActual())
            return !llegado
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
                guia.usar(resultado.ruta)
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
            val previo = ultimoContado
            if (previo == null) {
                ultimoContado = fijacion.punto
            } else if (previo.distanciaA(fijacion.punto) >= PASO_MIN_M) {
                distanciaM += previo.distanciaA(fijacion.punto)
                ultimoContado = fijacion.punto
            }
            lote += PuntoRecorrido(fijacion.punto.lat, fijacion.punto.lon, ahora, fijacion.velocidadMs)
            if (lote.size >= TAMANO_LOTE) volcar()
        }

        suspend fun volcar() {
            if (lote.isEmpty()) return
            val pendientes = lote.toList()
            lote.clear()
            recorridos.anadirPuntos(id, pendientes)
        }

        private fun estadoActual(): EstadoNavegacion = guia?.estado() ?: EstadoNavegacion.Libre(inicioMs, distanciaM)
    }

    private class Guia(
        solicitud: SolicitudNavegacion.Guiada,
    ) {
        val destino = solicitud.destino
        val perfil = solicitud.perfil
        var ruta: Ruta = solicitud.ruta
        var seguidor = SeguidorRuta(ruta)
        var detector = DetectorSalidaRuta()
        var progreso: EstadoRuta? = null
        var recalculando = false
        var reintentoMs = 0L
        var recalculo: Job? = null

        fun usar(nueva: Ruta) {
            ruta = nueva
            seguidor = SeguidorRuta(nueva)
            detector = DetectorSalidaRuta()
            progreso = null
        }

        fun estado() = EstadoNavegacion.Guiando(destino, perfil, ruta, progreso, recalculando)
    }

    companion object {
        const val TAMANO_LOTE = 10
        const val ESPERA_LLEGADA_MS = 10_000L
        const val REINTENTO_MS = 10_000L

        /** Un paso menor que esto se toma por ruido del GPS parado y no suma distancia. */
        private const val PASO_MIN_M = 5.0
    }
}
