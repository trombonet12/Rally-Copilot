package app.trombito.rallycopilot.data.navegacion

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio en primer plano de ubicación (tipo `location`) que vive mientras dura la conducción: sigue recibiendo
 * posiciones con la pantalla apagada. Solo delega en [SesionNavegacion] y muestra la notificación persistente. Se
 * arranca desde [ControlNavegacionImpl] tras una acción del usuario; no pide ubicación en segundo plano ni wake lock.
 */
@AndroidEntryPoint
class ServicioNavegacion : Service() {
    @Inject lateinit var control: ControlNavegacionImpl

    @Inject lateinit var fuente: FuenteUbicacion

    @Inject lateinit var enrutador: Enrutador

    @Inject lateinit var recorridos: RepositorioRecorridos

    private lateinit var notificacion: NotificacionNavegacion
    private lateinit var sesion: SesionNavegacion
    private lateinit var scope: CoroutineScope
    private var trabajo: Job? = null
    private var avisos: Job? = null
    private var huerfanos: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        notificacion = NotificacionNavegacion(this)
        notificacion.crearCanal()
        sesion =
            SesionNavegacion(
                fuente,
                enrutador,
                recorridos,
                reloj = System::currentTimeMillis,
                publicar = control::publicar,
                motivoSinGps = getString(R.string.navegacion_error_gps),
            )
        val alFallar =
            CoroutineExceptionHandler { _, causa ->
                Log.w(ETIQUETA, "La conducción ha fallado: ${causa.javaClass.simpleName}")
                control.publicar(EstadoNavegacion.Error(getString(R.string.navegacion_error_arranque)))
                enHiloPrincipal { detener() }
            }
        // Un solo hilo: SesionNavegacion no es concurrente y los recálculos no deben pisar a las posiciones.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + alFallar)
        if (control.reclamarCierreDeHuerfanos()) huerfanos = scope.launch { sesion.cerrarHuerfanos() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACCION_TERMINAR) {
            control.terminar()
        } else if (entrarEnPrimerPlano()) {
            val solicitud = control.tomarSolicitud()
            if (solicitud == null) detener() else iniciar(solicitud)
        }
        return START_NOT_STICKY
    }

    /** Debe ocurrir en los primeros segundos tras `startForegroundService`, aunque luego no haya nada que hacer. */
    private fun entrarEnPrimerPlano(): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                NotificacionNavegacion.ID_NOTIFICACION,
                notificacion.construir(control.estado.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (e: SecurityException) {
            fallarAlArrancar(e)
        } catch (e: IllegalStateException) {
            fallarAlArrancar(e)
        }

    private fun fallarAlArrancar(e: RuntimeException): Boolean {
        Log.w(ETIQUETA, "No se pudo pasar a primer plano: ${e.javaClass.simpleName}")
        control.publicar(EstadoNavegacion.Error(getString(R.string.navegacion_error_arranque)))
        stopSelf()
        return false
    }

    private fun iniciar(solicitud: SolicitudNavegacion) {
        if (avisos == null) avisos = scope.launch { mostrarEstado() }
        val previo = trabajo
        val nuevo =
            scope.launch {
                previo?.cancelAndJoin()
                huerfanos?.join()
                sesion.correr(solicitud)
            }
        nuevo.invokeOnCompletion { causa -> if (causa == null) enHiloPrincipal { detener() } }
        trabajo = nuevo
    }

    private suspend fun mostrarEstado() {
        val gestor = getSystemService(NotificationManager::class.java)
        control.estado
            .map { it to notificacion.textos(it) }
            .distinctUntilChanged { a, b -> a.second == b.second }
            .collect { (estado, _) ->
                try {
                    gestor.notify(NotificacionNavegacion.ID_NOTIFICACION, notificacion.construir(estado))
                } catch (_: SecurityException) {
                    // Sin POST_NOTIFICATIONS el servicio sigue funcionando sin actualizar el aviso.
                }
            }
    }

    private fun detener() {
        avisos?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enHiloPrincipal(accion: () -> Unit) = ContextCompat.getMainExecutor(this).execute(accion)

    override fun onDestroy() {
        avisos?.cancel()
        huerfanos?.cancel()
        val sesionActiva = trabajo
        if (sesionActiva == null) {
            scope.cancel()
        } else {
            // La sesión vuelca los puntos pendientes y cierra el recorrido en su `finally` antes de soltar el scope.
            sesionActiva.invokeOnCompletion { scope.cancel() }
            sesionActiva.cancel()
        }
        val estado = control.estado.value
        if (estado is EstadoNavegacion.Libre || estado is EstadoNavegacion.Guiando) {
            control.publicar(EstadoNavegacion.Inactiva)
        }
        super.onDestroy()
    }

    companion object {
        const val ACCION_TERMINAR = "app.trombito.rallycopilot.TERMINAR_NAVEGACION"
        private const val ETIQUETA = "ServicioNavegacion"
    }
}
