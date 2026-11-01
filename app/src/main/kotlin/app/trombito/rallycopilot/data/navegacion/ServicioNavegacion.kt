package app.trombito.rallycopilot.data.navegacion

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.data.alertas.FabricaAlertasSesion
import app.trombito.rallycopilot.data.notas.FabricaNotasSesion
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.voz.Locutor
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
import kotlinx.coroutines.flow.filter
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

    @Inject lateinit var fabricaNotas: FabricaNotasSesion

    @Inject lateinit var fabricaAlertas: FabricaAlertasSesion

    @Inject lateinit var locutor: Locutor

    @Inject lateinit var ajustes: RepositorioAjustesConduccion

    private lateinit var notificacion: NotificacionNavegacion
    private lateinit var scope: CoroutineScope
    private var trabajo: Job? = null
    private var avisos: Job? = null
    private var huerfanos: Job? = null
    private var ultimoStartId = 0

    /** Id de la sesión en curso en [ControlNavegacionImpl]; sus publicaciones se ignoran si deja de ser la vigente. */
    private var idSesion: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        notificacion = NotificacionNavegacion(this)
        notificacion.crearCanal()
        val alFallar =
            CoroutineExceptionHandler { _, causa ->
                Log.w(ETIQUETA, "La conducción ha fallado: ${causa.javaClass.simpleName}")
                publicarFalloDeArranque(idSesion)
                ContextCompat.getMainExecutor(this).execute { detener() }
            }
        // Un solo hilo: SesionNavegacion no es concurrente y los recálculos no deben pisar a las posiciones.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1) + alFallar)
        if (control.reclamarCierreDeHuerfanos()) huerfanos = scope.launch { cerrarHuerfanos() }
    }

    /** Limpieza de otro proceso: si falla no debe parar la conducción que se está arrancando. */
    private suspend fun cerrarHuerfanos() {
        try {
            crearSesion(SIN_ID).cerrarHuerfanos()
        } catch (e: SQLiteException) {
            Log.w(ETIQUETA, "No se pudieron cerrar los recorridos huérfanos: ${e.javaClass.simpleName}")
        } catch (e: IllegalStateException) {
            Log.w(ETIQUETA, "No se pudieron cerrar los recorridos huérfanos: ${e.javaClass.simpleName}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ultimoStartId = startId
        if (intent?.action == ACCION_TERMINAR) {
            control.terminar()
        } else if (entrarEnPrimerPlano()) {
            val solicitud = control.tomarSolicitud()
            // Sin solicitud pero con una sesión viva: otro arranque ya recogió la solicitud (dos seguidos); no parar.
            if (solicitud != null) {
                iniciar(solicitud.id, solicitud.solicitud)
            } else if (trabajo?.isActive != true) {
                detener()
            }
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
        publicarFalloDeArranque(control.idPendiente())
        stopSelf()
        return false
    }

    private fun iniciar(
        id: Long,
        solicitud: SolicitudNavegacion,
    ) {
        idSesion = id
        val sesion = crearSesion(id)
        // En el hilo principal, igual que la cancelación, para que un `notify` no se cuele tras `onDestroy`.
        if (avisos == null) avisos = scope.launch(Dispatchers.Main.immediate) { mostrarEstado() }
        val previo = trabajo
        val nuevo =
            scope.launch {
                previo?.cancelAndJoin()
                huerfanos?.join()
                sesion.correr(solicitud)
            }
        nuevo.invokeOnCompletion { causa ->
            // Solo la sesión vigente para el servicio: si otra la sustituyó entre medias, `trabajo` ya es la nueva.
            if (causa == null) ContextCompat.getMainExecutor(this).execute { if (trabajo === nuevo) detener() }
        }
        trabajo = nuevo
    }

    private suspend fun mostrarEstado() {
        val gestor = getSystemService(NotificationManager::class.java)
        control.estado
            .filter { it !is EstadoNavegacion.Inactiva }
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
        avisos = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(ultimoStartId)
    }

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
            idSesion?.let { control.publicar(it, EstadoNavegacion.Inactiva) }
        }
        super.onDestroy()
    }

    companion object {
        const val ACCION_TERMINAR = "app.trombito.rallycopilot.TERMINAR_NAVEGACION"
        private const val ETIQUETA = "ServicioNavegacion"

        /** Id que nunca es el vigente: las publicaciones de la limpieza de huérfanos no llegan a nadie. */
        private const val SIN_ID = -1L
    }
}

private fun ServicioNavegacion.crearSesion(id: Long) =
    SesionNavegacion(
        fuente,
        enrutador,
        recorridos,
        reloj = System::currentTimeMillis,
        publicar = { control.publicar(id, it) },
        motivoSinGps = getString(R.string.navegacion_error_gps),
        fabricaNotas = fabricaNotas,
        copiloto = CopilotoVoz(locutor, ajustes.ajustes, registroDeTiempos()),
        alertas = fabricaAlertas.crear(),
    )

/** Solo en un APK depurable: una línea por frase dicha para medir la anticipación (sin coordenadas). */
private fun ServicioNavegacion.registroDeTiempos(): ((String) -> Unit)? =
    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        { Log.d("CopilotoVoz", it) }
    } else {
        null
    }

private fun ServicioNavegacion.publicarFalloDeArranque(id: Long?) {
    if (id != null) control.publicar(id, EstadoNavegacion.Error(getString(R.string.navegacion_error_arranque)))
}
