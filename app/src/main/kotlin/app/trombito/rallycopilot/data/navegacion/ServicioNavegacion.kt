package app.trombito.rallycopilot.data.navegacion

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.data.alertas.FabricaAlertasSesion
import app.trombito.rallycopilot.data.escucha.EscuchaContinua
import app.trombito.rallycopilot.data.escucha.MemoriaFrases
import app.trombito.rallycopilot.data.escucha.NotaDicha
import app.trombito.rallycopilot.data.notas.FabricaNotasSesion
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.data.tramos.FabricaCronoSesion
import app.trombito.rallycopilot.data.voz.LiberadorModelos
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.voz.Locutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    @Inject lateinit var fabricaCrono: FabricaCronoSesion

    @Inject lateinit var locutor: Locutor

    @Inject lateinit var ajustes: RepositorioAjustesConduccion

    @Inject lateinit var memoriaFrases: MemoriaFrases

    @Inject lateinit var escuchaContinua: EscuchaContinua

    @Inject lateinit var ediciones: RepositorioEdiciones

    @Inject lateinit var tramos: RepositorioTramos

    @Inject lateinit var liberadorModelos: LiberadorModelos

    @Inject @DispatcherDefault
    lateinit var dispatcherDefault: CoroutineDispatcher

    @Inject @DispatcherIo
    lateinit var dispatcherIo: CoroutineDispatcher

    private lateinit var notificacion: NotificacionNavegacion
    private lateinit var scope: CoroutineScope
    private var trabajo: Job? = null
    private var avisos: Job? = null
    private var huerfanos: Job? = null
    private var vigilanteTipo: Job? = null
    private var ultimoStartId = 0

    /** Tipos con los que se declaró el primer plano por última vez (0 = aún no está en primer plano). */
    private var tipoActual = 0

    /** Id de la sesión en curso en [ControlNavegacionImpl]; sus publicaciones se ignoran si deja de ser la vigente. */
    private var idSesion: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        notificacion = NotificacionNavegacion(this)
        notificacion.crearCanal()
        // Otra conducción empieza: los modelos de voz que iban a soltarse al acabar la anterior se quedan.
        liberadorModelos.cancelar()
        val alFallar =
            CoroutineExceptionHandler { _, causa ->
                Log.w(ETIQUETA, "La conducción ha fallado: ${causa.javaClass.simpleName}")
                publicarFalloDeArranque(idSesion)
                ContextCompat.getMainExecutor(this).execute { detener() }
            }
        // Un solo hilo: SesionNavegacion no es concurrente y los recálculos no deben pisar a las posiciones.
        scope = CoroutineScope(SupervisorJob() + dispatcherDefault.limitedParallelism(1) + alFallar)
        if (control.reclamarCierreDeHuerfanos()) huerfanos = scope.launch { cerrarHuerfanos() }
        // «Copiloto»: solo hace algo con el ajuste activado y el permiso de micrófono; vive lo que dure el servicio.
        // La puerta de voz se cierra mientras habla el copiloto, para que no se escuche a sí mismo.
        scope.launch(dispatcherDefault) {
            escuchaContinua.correr(hablando = { locutor.hablando.value }, registro = registroDeTiempos())
        }
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

    /**
     * Debe ocurrir en los primeros segundos tras `startForegroundService`, aunque luego no haya nada que hacer. El tipo
     * `microphone` (la escucha de «Copiloto» con la pantalla apagada) solo se pide con el permiso concedido **y** el
     * ajuste «Copiloto» activo; si el sistema lo rechaza, sigue solo con `location`. Si el ajuste cambia en marcha,
     * [vigilarAjusteCopiloto] vuelve a declarar el primer plano con el tipo nuevo.
     */
    private fun entrarEnPrimerPlano(): Boolean {
        val deseados = tiposDeseados(copilotoActivoAhora())
        val tipos =
            listOf(deseados, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION).distinct().firstOrNull {
                declararPrimerPlano(notificacion.construir(control.estado.value), it)
            } ?: return fallarAlArrancar()
        tipoActual = tipos
        vigilarAjusteCopiloto()
        return true
    }

    /** Mientras el servicio está en primer plano, ajusta el tipo (micrófono sí/no) a lo que diga «Copiloto». */
    private fun vigilarAjusteCopiloto() {
        if (vigilanteTipo != null) return
        vigilanteTipo =
            scope.launch(Dispatchers.Main.immediate) {
                ajustes.ajustes.map { it.activarConCopiloto }.distinctUntilChanged().collect { copiloto ->
                    val tipos = tiposDeseados(copiloto)
                    val ok =
                        tipos == tipoActual || declararPrimerPlano(notificacion.construir(control.estado.value), tipos)
                    if (ok) tipoActual = tipos
                }
            }
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

    /**
     * Actualiza la notificación solo si cambia el texto, y como mucho cada [LimitadorNotificacion.PERIODO_MS] salvo que
     * cambie de maniobra o de estado: con la pantalla apagada cada `notify` despierta al sistema.
     */
    private suspend fun mostrarEstado() {
        val gestor = getSystemService(NotificationManager::class.java)
        val limitador = LimitadorNotificacion(SystemClock::elapsedRealtime)
        control.estado
            .filter { it !is EstadoNavegacion.Inactiva }
            .map { it to notificacion.textos(it) }
            .distinctUntilChanged { a, b -> a.second == b.second }
            .collectLatest { (estado, _) ->
                val clave = claveDeNotificacion(estado)
                val espera = limitador.esperaMs(clave)
                if (espera > 0) delay(espera)
                limitador.publicada(clave)
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
        vigilanteTipo?.cancel()
        vigilanteTipo = null
        tipoActual = 0
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(ultimoStartId)
    }

    override fun onDestroy() {
        // Acabada la conducción, Piper y Vosk no deben quedarse en memoria (con un margen por la última frase).
        liberadorModelos.programar()
        avisos?.cancel()
        vigilanteTipo?.cancel()
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

        /** Id que nunca es el vigente: las publicaciones de la limpieza de huérfanos no llegan a nadie. */
        private const val SIN_ID = -1L
    }
}

private const val ETIQUETA = "ServicioNavegacion"

/** Tiempo máximo para leer «Copiloto» al entrar en primer plano (hilo principal; en Room son unos ms). */
private const val PLAZO_AJUSTE_MS = 500L

private fun ServicioNavegacion.crearSesion(id: Long) =
    SesionNavegacion(
        fuente,
        enrutador,
        recorridos,
        reloj = System::currentTimeMillis,
        publicar = { control.publicar(id, it) },
        motivoSinGps = getString(R.string.navegacion_error_gps),
        fabricaNotas = fabricaNotas,
        copiloto =
            CopilotoVoz(
                locutor,
                ajustes.ajustes,
                registroDeTiempos(),
                memoriaFrases::recordar,
                alDecirNota = { frase ->
                    frase.puntoM?.let {
                        memoriaFrases.recordarNota(NotaDicha(frase.texto, it, System.currentTimeMillis()))
                    }
                },
            ),
        alertas = fabricaAlertas.crear(),
        ordenes = control.ordenes,
        crono = fabricaCrono.crear(),
        cronometrar = { ajustes.ajustes.first().cronometrar },
        ediciones = ediciones,
        tramos = tramos,
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

private fun ServicioNavegacion.conPermisoDeMicrofono(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Declara el primer plano con [tipos]; `false` si el sistema lo rechaza (sin permiso del tipo o estado no válido). */
private fun Service.declararPrimerPlano(
    notificacion: Notification,
    tipos: Int,
): Boolean =
    try {
        ServiceCompat.startForeground(this, NotificacionNavegacion.ID_NOTIFICACION, notificacion, tipos)
        true
    } catch (e: SecurityException) {
        Log.w(ETIQUETA, "Sin primer plano con los tipos $tipos: ${e.javaClass.simpleName}")
        false
    } catch (e: IllegalStateException) {
        Log.w(ETIQUETA, "Sin primer plano con los tipos $tipos: ${e.javaClass.simpleName}")
        false
    }

private fun ServicioNavegacion.fallarAlArrancar(): Boolean {
    Log.w(ETIQUETA, "No se pudo pasar a primer plano")
    publicarFalloDeArranque(control.idPendiente())
    stopSelf()
    return false
}

/** Ubicación siempre; micrófono solo con el permiso concedido y «Copiloto» activo (mínimo privilegio). */
private fun ServicioNavegacion.tiposDeseados(copiloto: Boolean): Int =
    if (copiloto && conPermisoDeMicrofono()) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    }

/**
 * Lectura breve del ajuste en el arranque; si no llega a tiempo, empieza sin micrófono y el vigilante lo corrige.
 *
 * Es el único `runBlocking` de producción y se queda: se llama desde `onStartCommand` (hilo principal), que debe
 * declarar el primer plano con sus tipos antes de volver al sistema. El tipo `microphone` solo se concede si se pide
 * al entrar en primer plano mientras la app es visible; declararlo luego desde el vigilante (tras volver de
 * `onStartCommand`) puede rechazarse y cambiaría el comportamiento. El bloqueo está acotado a [PLAZO_AJUSTE_MS] y va
 * fuera del hilo principal (dispatcher de E/S inyectado).
 */
private fun ServicioNavegacion.copilotoActivoAhora(): Boolean =
    runBlocking(dispatcherIo) {
        withTimeoutOrNull(PLAZO_AJUSTE_MS) { ajustes.ajustes.first().activarConCopiloto } ?: false
    }
