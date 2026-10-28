package app.trombito.rallycopilot.data.datosmapa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.MotivoError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Descarga de los datos de Mallorca como trabajo único `datos-mapa`, en primer plano con notificación de progreso.
 * Un corte de red reintenta con backoff y reanuda los `.part`; los errores definitivos se guardan en Room.
 */
@HiltWorker
class TrabajoDatosMapa
    @AssistedInject
    constructor(
        @Assisted private val contexto: Context,
        @Assisted parametros: WorkerParameters,
        private val actualizador: ActualizadorDatos,
    ) : CoroutineWorker(contexto, parametros) {
        private var ultimoAviso = 0L

        override suspend fun doWork(): Result {
            val automatico = inputData.getBoolean(CLAVE_AUTOMATICO, false)
            return try {
                val resultado = actualizador.actualizar(automatico, ::publicarProgreso)
                if (resultado is ResultadoActualizacion.Instalado) Result.success() else Result.failure()
            } catch (e: IOException) {
                Log.w(ETIQUETA_LOG, "Descarga interrumpida (intento $runAttemptCount)", e)
                if (runAttemptCount >= MAX_INTENTOS) {
                    actualizador.registrarError(MotivoError.SIN_RED)
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }

        override suspend fun getForegroundInfo(): ForegroundInfo = infoPrimerPlano(0, 0)

        private suspend fun publicarProgreso(
            hechos: Long,
            totales: Long,
        ) {
            val ahora = System.currentTimeMillis()
            val terminado = totales > 0 && hechos >= totales
            if (!terminado && ahora - ultimoAviso < INTERVALO_AVISO_MS && ultimoAviso != 0L) return
            ultimoAviso = ahora
            setProgress(workDataOf(CLAVE_HECHOS to hechos, CLAVE_TOTALES to totales))
            try {
                setForeground(infoPrimerPlano(hechos, totales))
            } catch (_: IllegalStateException) {
                // Android 12+ no deja pasar a primer plano desde segundo plano: la descarga sigue sin notificación.
            }
        }

        private fun infoPrimerPlano(
            hechos: Long,
            totales: Long,
        ): ForegroundInfo {
            val gestor = contexto.getSystemService(NotificationManager::class.java)
            gestor.createNotificationChannel(
                NotificationChannel(
                    CANAL_DESCARGAS,
                    contexto.getString(R.string.descargas_canal_nombre),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = contexto.getString(R.string.descargas_canal_descripcion) },
            )
            val porcentaje =
                if (totales > 0) (hechos * PORCENTAJE_TOTAL / totales).toInt().coerceIn(0, PORCENTAJE_TOTAL) else 0
            val constructor =
                NotificationCompat
                    .Builder(contexto, CANAL_DESCARGAS)
                    .setSmallIcon(R.drawable.ic_stat_rally)
                    .setContentTitle(contexto.getString(R.string.descargas_titulo))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(PORCENTAJE_TOTAL, porcentaje, totales <= 0)
                    .addAction(
                        0,
                        contexto.getString(R.string.descargas_cancelar),
                        WorkManager.getInstance(contexto).createCancelPendingIntent(id),
                    )
            if (totales > 0) {
                constructor.setContentText(
                    contexto.getString(
                        R.string.descargas_progreso,
                        Formatter.formatFileSize(contexto, hechos),
                        Formatter.formatFileSize(contexto, totales),
                    ),
                )
            }
            contexto.packageManager.getLaunchIntentForPackage(contexto.packageName)?.let { abrir ->
                val banderas = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                constructor.setContentIntent(PendingIntent.getActivity(contexto, 0, abrir, banderas))
            }
            return ForegroundInfo(ID_NOTIFICACION, constructor.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        companion object {
            const val NOMBRE_TRABAJO = "datos-mapa"
            const val CLAVE_AUTOMATICO = "automatico"
            const val CLAVE_HECHOS = "hechos"
            const val CLAVE_TOTALES = "totales"
            const val CANAL_DESCARGAS = "descargas"
            private const val ETIQUETA_LOG = "TrabajoDatosMapa"
            private const val ID_NOTIFICACION = 4001
            private const val MAX_INTENTOS = 10
            private const val INTERVALO_AVISO_MS = 500L
            private const val PORCENTAJE_TOTAL = 100
        }
    }
