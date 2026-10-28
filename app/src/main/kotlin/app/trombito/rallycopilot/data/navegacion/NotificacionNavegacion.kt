package app.trombito.rallycopilot.data.navegacion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import java.util.Date
import java.util.Locale

/** Construye el canal «Conducción» y la notificación persistente del servicio según el [EstadoNavegacion]. */
internal class NotificacionNavegacion(
    private val contexto: Context,
) {
    fun crearCanal() {
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CANAL,
                contexto.getString(R.string.navegacion_canal_nombre),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = contexto.getString(R.string.navegacion_canal_descripcion) },
        )
    }

    fun construir(estado: EstadoNavegacion): Notification {
        val (titulo, texto) = textos(estado)
        val constructor =
            NotificationCompat
                .Builder(contexto, CANAL)
                .setSmallIcon(R.drawable.ic_stat_rally)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, contexto.getString(R.string.navegacion_terminar), intentTerminar())
        contexto.packageManager.getLaunchIntentForPackage(contexto.packageName)?.let { abrir ->
            val banderas = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            constructor.setContentIntent(PendingIntent.getActivity(contexto, 0, abrir, banderas))
        }
        return constructor.build()
    }

    private fun intentTerminar(): PendingIntent {
        val intent = Intent(contexto, ServicioNavegacion::class.java).setAction(ServicioNavegacion.ACCION_TERMINAR)
        return PendingIntent.getService(
            contexto,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Título y texto de la notificación para ese estado. */
    internal fun textos(estado: EstadoNavegacion): Pair<String, String> =
        when (estado) {
            EstadoNavegacion.Inactiva -> {
                contexto.getString(R.string.navegacion_titulo_libre) to
                    contexto.getString(R.string.navegacion_iniciando)
            }

            is EstadoNavegacion.Libre -> {
                contexto.getString(R.string.navegacion_titulo_libre) to
                    contexto.getString(R.string.navegacion_libre_texto, formatearDistancia(estado.distanciaM))
            }

            is EstadoNavegacion.Guiando -> {
                contexto.getString(R.string.navegacion_titulo_guiando, estado.destino.nombre) to textoGuiando(estado)
            }

            is EstadoNavegacion.Llegada -> {
                contexto.getString(R.string.navegacion_titulo_llegada) to estado.destino.nombre
            }

            is EstadoNavegacion.Error -> {
                contexto.getString(R.string.navegacion_titulo_error) to estado.motivo
            }
        }

    private fun textoGuiando(estado: EstadoNavegacion.Guiando): String {
        val progreso = estado.progreso
        return when {
            estado.recalculando -> {
                contexto.getString(R.string.navegacion_recalculando)
            }

            progreso == null -> {
                contexto.getString(R.string.navegacion_guiando_sin_progreso)
            }

            else -> {
                val maniobra = progreso.maniobraActual
                val frase = maniobra?.let { FrasesManiobra.frase(it) }.orEmpty().replaceFirstChar { it.uppercase() }
                val llegada = DateFormat.getTimeFormat(contexto).format(Date(progreso.llegadaMs))
                contexto.getString(
                    R.string.navegacion_guiando_texto,
                    formatearDistancia(progreso.distanciaAManiobraM),
                    frase,
                    llegada,
                )
            }
        }
    }

    companion object {
        const val CANAL = "conduccion"
        const val ID_NOTIFICACION = 4002
    }
}

private const val UN_KM = 1000.0
private const val REDONDEO_M = 50.0
private val ES = Locale.forLanguageTag("es-ES")

/** «300 m» (redondeado a 50 m para no refrescar la notificación a cada metro) o «1,2 km». */
internal fun formatearDistancia(metros: Double): String {
    val redondeado = Math.round(metros.coerceAtLeast(0.0) / REDONDEO_M) * REDONDEO_M
    return if (redondeado < UN_KM) "${redondeado.toInt()} m" else String.format(ES, "%.1f km", metros / UN_KM)
}
