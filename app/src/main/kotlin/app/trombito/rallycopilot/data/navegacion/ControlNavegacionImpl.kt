package app.trombito.rallycopilot.data.navegacion

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.content.ContextCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.ruta.Ruta
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto de entrada de la conducción: guarda la solicitud, arranca [ServicioNavegacion] en primer plano y expone su
 * estado. Solo se llama tras una acción del usuario con la app visible (Android 12+ no deja arrancar un servicio en
 * primer plano desde segundo plano).
 *
 * Recuerda el destino del guiado en [guiadoPendiente]: lo guarda al empezar y lo borra al terminar, llegar o pasar a
 * libre, y la actualiza cuando cambian las paradas pendientes (se alcanza una, o se añade, quita o mueve). Si el
 * proceso muere la fila queda, y la UI la usa para ofrecer «Seguir a destino». Las escrituras van por una cola en
 * [alcance] para conservar el orden sin bloquear al llamante. Las órdenes de edición de paradas llegan a la sesión por
 * [ordenes].
 */
@Singleton
@Suppress("TooManyFunctions") // control de sesión + guiado pendiente + órdenes de paradas, todo con un cerrojo
class ControlNavegacionImpl
    internal constructor(
        @ApplicationContext private val contexto: Context,
        private val fuente: FuenteUbicacion,
        private val guiadoPendiente: RepositorioGuiadoPendiente,
        alcance: CoroutineScope,
    ) : ControlNavegacion {
        @Inject
        constructor(
            @ApplicationContext contexto: Context,
            fuente: FuenteUbicacion,
            guiadoPendiente: RepositorioGuiadoPendiente,
        ) : this(contexto, fuente, guiadoPendiente, CoroutineScope(SupervisorJob() + Dispatchers.IO))

        private val cola = Channel<suspend () -> Unit>(Channel.UNLIMITED)

        init {
            alcance.launch {
                for (tarea in cola) {
                    try {
                        tarea()
                    } catch (e: SQLiteException) {
                        Log.w(ETIQUETA, "No se pudo actualizar el guiado pendiente: ${e.javaClass.simpleName}")
                    } catch (e: IllegalStateException) {
                        Log.w(ETIQUETA, "No se pudo actualizar el guiado pendiente: ${e.javaClass.simpleName}")
                    }
                }
            }
        }

        private val _estado = MutableStateFlow<EstadoNavegacion>(EstadoNavegacion.Inactiva)
        override val estado: StateFlow<EstadoNavegacion> = _estado.asStateFlow()

        private val cerrojo = Any()
        private val ultimoId = AtomicLong(0)

        /** Id de la sesión que puede publicar; [SIN_SESION] tras [terminar]. Solo se toca con el [cerrojo]. */
        private var vigente = SIN_SESION

        @Volatile
        private var pendiente: SolicitudConId? = null

        /** Órdenes de edición de paradas hacia la sesión guiada en curso; el servicio se las pasa a la sesión. */
        internal val ordenes = Channel<OrdenParadas>(Channel.UNLIMITED)

        /** Paradas pendientes que hay en el guiado pendiente guardado. Solo se toca con el [cerrojo] o al empezar. */
        private var paradasGuardadas: List<Destino> = emptyList()

        /** Destino del guiado pendiente guardado (cambia si el usuario elige otro destino guiando). */
        private var destinoGuardado: Destino? = null
        private val huerfanosReclamados = AtomicBoolean(false)

        override fun empezarLibre() {
            if (_estado.value is EstadoNavegacion.Guiando) borrarPendiente()
            arrancar(SolicitudNavegacion.Libre, EstadoNavegacion.Libre(System.currentTimeMillis(), 0.0))
        }

        override fun empezar(
            destino: Destino,
            perfil: PerfilRuta,
            ruta: Ruta,
            paradas: List<Destino>,
        ) {
            val yaGuiando = _estado.value is EstadoNavegacion.Guiando
            arrancar(
                SolicitudNavegacion.Guiada(destino, perfil, ruta, paradas),
                EstadoNavegacion.Guiando(
                    destino,
                    perfil,
                    ruta,
                    progreso = null,
                    recalculando = false,
                    paradas = paradas.take(ruta.indicesParadas.size),
                ),
            )
            if (!yaGuiando && _estado.value is EstadoNavegacion.Guiando) {
                val pendientes = paradas.take(ruta.indicesParadas.size)
                synchronized(cerrojo) {
                    paradasGuardadas = pendientes
                    destinoGuardado = destino
                }
                cola.trySend { guiadoPendiente.guardar(destino, perfil, pendientes) }
            }
        }

        override fun editarParadas(orden: OrdenParadas) {
            if (_estado.value is EstadoNavegacion.Guiando) ordenes.trySend(orden)
        }

        override fun terminar() {
            pendiente = null
            borrarPendiente()
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
            // Una sesión activa solo se sustituye por otra de distinto tipo (Libre <-> Guiando): el servicio sigue
            // vivo, recibe otro arranque con id nuevo y cierra el recorrido anterior antes de abrir el siguiente.
            val misma =
                (actual is EstadoNavegacion.Libre && solicitud is SolicitudNavegacion.Libre) ||
                    (actual is EstadoNavegacion.Guiando && solicitud is SolicitudNavegacion.Guiada)
            if (misma) return
            if (!fuente.tienePermiso()) {
                _estado.value = EstadoNavegacion.Error(contexto.getString(R.string.navegacion_error_permiso))
                return
            }
            val id = ultimoId.incrementAndGet()
            pendiente = SolicitudConId(id, solicitud)
            descartarOrdenesViejas()
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
            borrarPendiente()
            publicar(id, EstadoNavegacion.Error(contexto.getString(R.string.navegacion_error_arranque)))
            // Si sustituía a una sesión con el servicio vivo, que no siga grabando bajo un estado de Error.
            contexto.stopService(Intent(contexto, ServicioNavegacion::class.java))
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
                if (id == vigente) {
                    _estado.value = nuevo
                    if (nuevo is EstadoNavegacion.Llegada) borrarPendiente()
                    if (nuevo is EstadoNavegacion.Guiando) guardarParadasSiCambian(nuevo)
                }
            }
        }

        /** Las órdenes de edición que quedaron de una sesión anterior no valen para la nueva. */
        private fun descartarOrdenesViejas() {
            generateSequence { ordenes.tryReceive().getOrNull() }.count()
        }

        /** Mantiene el guiado pendiente al día con las paradas que faltan. Llamar con el [cerrojo] tomado. */
        private fun guardarParadasSiCambian(guiando: EstadoNavegacion.Guiando) {
            if (guiando.paradas == paradasGuardadas && guiando.destino == destinoGuardado) return
            paradasGuardadas = guiando.paradas
            destinoGuardado = guiando.destino
            val destino = guiando.destino
            val perfil = guiando.perfil
            val paradas = guiando.paradas
            cola.trySend { guiadoPendiente.guardar(destino, perfil, paradas) }
        }

        private fun borrarPendiente() {
            cola.trySend { guiadoPendiente.borrar() }
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
