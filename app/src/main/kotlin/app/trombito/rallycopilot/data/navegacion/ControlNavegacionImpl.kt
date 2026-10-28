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

        @Volatile
        private var pendiente: SolicitudNavegacion? = null
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
            contexto.stopService(Intent(contexto, ServicioNavegacion::class.java))
            _estado.value = EstadoNavegacion.Inactiva
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
            pendiente = solicitud
            _estado.value = inicial
            try {
                ContextCompat.startForegroundService(contexto, Intent(contexto, ServicioNavegacion::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+: ForegroundServiceStartNotAllowedException si la app no está visible.
                Log.w(ETIQUETA, "No se pudo arrancar el servicio de conducción: ${e.javaClass.simpleName}")
                pendiente = null
                _estado.value = EstadoNavegacion.Error(contexto.getString(R.string.navegacion_error_arranque))
            }
        }

        /** El servicio recoge la solicitud al arrancar; null si el proceso se reinició y ya no hay nada que hacer. */
        internal fun tomarSolicitud(): SolicitudNavegacion? = pendiente.also { pendiente = null }

        /** True solo la primera vez por proceso: los recorridos huérfanos solo pueden venir de un proceso anterior. */
        internal fun reclamarCierreDeHuerfanos(): Boolean = huerfanosReclamados.compareAndSet(false, true)

        internal fun publicar(nuevo: EstadoNavegacion) {
            _estado.value = nuevo
        }

        private companion object {
            const val ETIQUETA = "ControlNavegacion"
        }
    }
