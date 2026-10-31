package app.trombito.rallycopilot.data.navegacion

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.ruta.Ruta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto de entrada de la conducción: guarda la solicitud, arranca [ServicioNavegacion] en primer plano y expone su
 * estado. Solo se llama tras una acción del usuario con la app visible (Android 12+ no deja arrancar un servicio en
 * primer plano desde segundo plano).
 */
@Singleton
class ControlNavegacionImpl
    @Inject
    constructor(
        @ApplicationContext private val contexto: Context,
        private val fuente: FuenteUbicacion,
    ) : ControlNavegacion {
        private val _estado = MutableStateFlow<EstadoNavegacion>(EstadoNavegacion.Inactiva)
        override val estado: StateFlow<EstadoNavegacion> = _estado.asStateFlow()

        private val cerrojo = Any()
        private val ultimoId = AtomicLong(0)

        /** Id de la sesión que puede publicar; [SIN_SESION] tras [terminar]. Solo se toca con el [cerrojo]. */
        private var vigente = SIN_SESION

        @Volatile
        private var pendiente: SolicitudConId? = null
        private val huerfanosReclamados = AtomicBoolean(false)

        override fun empezarLibre() {
            arrancar(SolicitudNavegacion.Libre, EstadoNavegacion.Libre(System.currentTimeMillis(), 0.0))
        }

        override fun empezar(
            destino: Destino,
            perfil: PerfilRuta,
            ruta: Ruta,
        ) {
            arrancar(
                SolicitudNavegacion.Guiada(destino, perfil, ruta),
                EstadoNavegacion.Guiando(destino, perfil, ruta, progreso = null, recalculando = false),
            )
        }

        override fun terminar() {
            pendiente = null
            synchronized(cerrojo) {
                vigente = SIN_SESION
                _estado.value = EstadoNavegacion.Inactiva
            }
            contexto.stopService(Intent(contexto, ServicioNavegacion::class.java))
        }

        private fun arrancar(
            solicitud: SolicitudNavegacion,
            inicial: EstadoNavegacion,
        ) {
            val actual = _estado.value
            if (actual is EstadoNavegacion.Libre || actual is EstadoNavegacion.Guiando) return
            if (!fuente.tienePermiso()) {
                _estado.value = EstadoNavegacion.Error(contexto.getString(R.string.navegacion_error_permiso))
                return
            }
            val id = ultimoId.incrementAndGet()
            pendiente = SolicitudConId(id, solicitud)
            synchronized(cerrojo) {
                vigente = id
                _estado.value = inicial
            }
            try {
                ContextCompat.startForegroundService(contexto, Intent(contexto, ServicioNavegacion::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+: ForegroundServiceStartNotAllowedException si la app no está visible.
                falloAlArrancar(id, e)
            } catch (e: SecurityException) {
                // Operación de primer plano denegada (appops START_FOREGROUND o política del sistema).
                falloAlArrancar(id, e)
            }
        }

        private fun falloAlArrancar(
            id: Long,
            e: RuntimeException,
        ) {
            Log.w(ETIQUETA, "No se pudo arrancar el servicio de conducción: ${e.javaClass.simpleName}")
            pendiente = null
            publicar(id, EstadoNavegacion.Error(contexto.getString(R.string.navegacion_error_arranque)))
        }

        /** El servicio recoge la solicitud al arrancar; null si el proceso se reinició y ya no hay nada que hacer. */
        internal fun tomarSolicitud(): SolicitudConId? = pendiente.also { pendiente = null }

        /** Id de la solicitud que el servicio aún no ha recogido, para publicar su fallo de arranque. */
        internal fun idPendiente(): Long? = pendiente?.id

        /** True solo la primera vez por proceso: los recorridos huérfanos solo pueden venir de un proceso anterior. */
        internal fun reclamarCierreDeHuerfanos(): Boolean = huerfanosReclamados.compareAndSet(false, true)

        /** Publica solo si [id] sigue siendo la sesión vigente: tras [terminar] o una sesión nueva se ignora. */
        internal fun publicar(
            id: Long,
            nuevo: EstadoNavegacion,
        ) {
            synchronized(cerrojo) {
                if (id == vigente) _estado.value = nuevo
            }
        }

        private companion object {
            const val ETIQUETA = "ControlNavegacion"
            const val SIN_SESION = 0L
        }
    }

/** Una [solicitud] con el id de la sesión que la atenderá. */
internal class SolicitudConId(
    val id: Long,
    val solicitud: SolicitudNavegacion,
)
