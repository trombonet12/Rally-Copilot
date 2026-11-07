package app.trombito.rallycopilot.ui.mapa

import android.os.SystemClock
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.PosicionEstimada
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.DetectorSenalGps
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.velocidad.kmh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lo que el mapa sabe de la posición en un instante. */
data class LecturaMapa(
    val posicion: Fijacion?,
    val velocidadKmh: Int?,
    val via: ViaActual?,
    /** [posicion] ajustada al camino; es la que pinta el mapa. */
    val ajustada: Fijacion? = posicion,
    /** Se perdió el GPS (más de 10 s sin fijación útil tras haberla tenido). */
    val sinSenal: Boolean = false,
    /** [via] es el camino al que se ajustó la posición: si no, la placa del panel no es fiable. */
    val viaCoincide: Boolean = true,
)

/**
 * Canalización GPS del mapa (auditoría de noviembre de 2026, ARQ-10; antes dentro de `MapaViewModel`): toma las
 * posiciones compartidas de la app ([FuenteUbicacion.posiciones], con velocidad y rumbo ya estimados), anota la señal
 * ([DetectorSenalGps] y el aviso de voz), busca la vía, ajusta la posición al camino, caduca la velocidad por el reloj
 * y compone la estela del recorrido. Una instancia por ViewModel (tiene estado). [reloj] es el reloj monótono (ms),
 * el mismo de `Fijacion.tiempoMs`.
 *
 * Emite una [LecturaMapa] por fijación (la velocidad primero, sin esperar a la vía, que puede tardar si se carga
 * entonces el archivo) y una segunda solo si la vía cambió (REN-9). Solo recoge mientras alguien recoge [lecturas].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CanalizacionGps internal constructor(
    private val ubicacion: FuenteUbicacion,
    private val vias: ProveedorViaActual,
    private val navegacion: ControlNavegacion,
    ajustes: RepositorioAjustesConduccion,
    locutor: Locutor,
    private val reloj: () -> Long,
) {
    @Inject
    constructor(
        ubicacion: FuenteUbicacion,
        vias: ProveedorViaActual,
        navegacion: ControlNavegacion,
        ajustes: RepositorioAjustesConduccion,
        locutor: Locutor,
    ) : this(ubicacion, vias, navegacion, ajustes, locutor, SystemClock::elapsedRealtime)

    private val avisador = AvisadorSenalGps(navegacion, ajustes, locutor)
    private val recorrido = EstelaRecorrido()
    private val estelaMutable = MutableStateFlow<List<List<PuntoGeo>>>(emptyList())
    private val permiso = MutableStateFlow(ubicacion.tienePermiso())

    /**
     * Estela de por dónde se ha pasado en el recorrido en curso (las posiciones que pinta el mapa, simplificadas a
     * pasos de 5 m y con tope de 5.000 puntos); se publica como mucho una vez por segundo.
     */
    val estela: StateFlow<List<List<PuntoGeo>>> = estelaMutable

    /** Borra la estela (al terminar la conducción o al cambiar de modo). */
    fun limpiarEstela() {
        recorrido.limpiar()
        estelaMutable.value = emptyList()
    }

    /** La UI avisa de si tiene la ubicación precisa (el GPS la exige); al concederse empieza a recoger el GPS. */
    fun permisoConcedido(precisa: Boolean) {
        permiso.value = precisa
    }

    val lecturas: Flow<LecturaMapa> =
        permiso.flatMapLatest { concedido -> if (concedido) canalizar() else flowOf(LECTURA_VACIA) }

    private fun canalizar(): Flow<LecturaMapa> {
        avisador.reiniciar()
        val canal = Canal()
        // Sin fijaciones (túnel, GPS perdido) la velocidad caduca por el reloj, no por una fijación nueva.
        // Ambas corrutinas corren en el hilo del colector; solo se recoge con la pantalla visible.
        return channelFlow {
            send(LECTURA_VACIA)
            launch { ubicacion.posiciones().collect { canal.alRecibir(it) { lectura -> send(lectura) } } }
            launch {
                while (true) {
                    delay(TICK_CADUCIDAD_MS)
                    canal.alTick { lectura -> send(lectura) }
                }
            }
        }
    }

    /** El estado de una recogida de posiciones (una por [canalizar]); lo tocan las dos corrutinas del colector. */
    private inner class Canal {
        private val sobreCamino = PosicionSobreCamino(navegacion)
        private val detector = DetectorSenalGps()
        private var sinSenal = false
        private var viaPrevia: ViaActual? = null
        private var ultima = LECTURA_VACIA

        // Cuándo (reloj de las fijaciones) se actualizó por última vez la velocidad con una fijación precisa.
        private var velocidadDesdeMs: Long? = null

        // Devuelve si cambió el estado de la señal; el avisador decide cuándo habla.
        private suspend fun refrescarSenal(ahoraMs: Long): Boolean {
            val nueva = detector.sinSenal(ahoraMs)
            avisador.actualizar(nueva, ahoraMs, detector.ultimaUtilMs)
            if (nueva == sinSenal) return false
            sinSenal = nueva
            return true
        }

        suspend fun alRecibir(
            posicion: PosicionEstimada,
            enviar: suspend (LecturaMapa) -> Unit,
        ) {
            val fijacion = posicion.cruda
            detector.fijacion(fijacion, reloj())
            refrescarSenal(reloj())
            // La posición aproximada de interior (fused/red) salta decenas de metros: se pinta, pero no alimenta la
            // velocidad derivada de la posición (la fuente compartida ya lo tiene en cuenta).
            val ms = posicion.velocidadMs
            if (posicion.precisa && ms != null) velocidadDesdeMs = fijacion.tiempoMs
            val velocidad = ms?.let(::kmh)
            // Al ajuste va la velocidad estimada (la del receptor puede faltar). El rumbo mostrado sale del camino
            // (ruta o vía), no del bruto; la cruda sigue en `posicion`. Sin rumbo del receptor (emulador, fused), el
            // del movimiento: si no, en una vía de doble sentido el ajuste solo tendría su propio rumbo previo y se
            // quedaba al revés.
            val conVelocidad =
                fijacion.copy(velocidadMs = ms ?: fijacion.velocidadMs, rumboGrados = posicion.rumboGrados)

            fun lectura(via: ViaActual?): LecturaMapa {
                val ajustada = sobreCamino.ajustar(conVelocidad, via)
                return LecturaMapa(fijacion, velocidad, via, ajustada, sinSenal, viaCoincide(ajustada.punto, via))
            }
            // Primero la velocidad: la vía puede tardar (carga perezosa del archivo de vías).
            ultima = lectura(viaPrevia)
            enviar(ultima)
            val via = vias.viaEn(fijacion)
            if (via != viaPrevia) {
                viaPrevia = via
                ultima = lectura(via)
                enviar(ultima)
            }
            if (posicion.precisa) {
                recorrido.registrar((ultima.ajustada ?: fijacion).punto, reloj())?.let { estelaMutable.value = it }
            }
        }

        suspend fun alTick(enviar: suspend (LecturaMapa) -> Unit) {
            val cambioSenal = refrescarSenal(reloj())
            val desde = velocidadDesdeMs
            val caduca = ultima.velocidadKmh != null && (desde == null || reloj() - desde > CADUCIDAD_MS)
            if (cambioSenal || caduca) {
                ultima =
                    ultima.copy(
                        velocidadKmh = if (caduca) null else ultima.velocidadKmh,
                        sinSenal = sinSenal,
                    )
                enviar(ultima)
            }
        }
    }

    private companion object {
        const val TICK_CADUCIDAD_MS = 1_000L

        /** Antigüedad máxima de la velocidad para seguir mostrándola (la del `EstimadorVelocidad`). */
        const val CADUCIDAD_MS = 5_000L
        val LECTURA_VACIA = LecturaMapa(null, null, null)
    }
}
