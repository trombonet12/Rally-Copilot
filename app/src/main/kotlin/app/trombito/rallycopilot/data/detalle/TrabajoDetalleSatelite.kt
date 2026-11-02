package app.trombito.rallycopilot.data.detalle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import androidx.work.workDataOf
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.data.datosmapa.TrabajoDatosMapa
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Descarga del satélite de detalle (z18–19) del IGN como trabajo único `detalle-satelite`. Modo automático (corredor
 * z18 de los tramos y rutas pendientes, solo con WiFi y con carga o batería > 30 %) o zona manual (en primer plano con
 * notificación). Un corte de red o la batería baja reintentan más tarde; lo ya bajado no se repite.
 */
@HiltWorker
class TrabajoDetalleSatelite
    @AssistedInject
    constructor(
        @Assisted private val contexto: Context,
        @Assisted parametros: WorkerParameters,
        private val ejecutor: EjecutorDetalle,
    ) : CoroutineWorker(contexto, parametros) {
        private var ultimoAviso = 0L

        override suspend fun doWork(): Result {
            val zona = inputData.zona()
            return try {
                val resultado =
                    if (zona == null) {
                        ejecutor.automatico(::publicarProgreso)
                    } else {
                        ejecutor.zona(zona.first, zona.second, ::publicarProgreso)
                    }
                if (resultado == ResultadoEjecucion.ESPERA_ENERGIA) Result.retry() else Result.success()
            } catch (e: IOException) {
                Log.w(ETIQUETA_LOG, "Descarga de detalle interrumpida (intento $runAttemptCount)", e)
                if (runAttemptCount >= MAX_INTENTOS) Result.failure() else Result.retry()
            }
        }

        override suspend fun getForegroundInfo(): ForegroundInfo = infoPrimerPlano(0, 0)

        private suspend fun publicarProgreso(
            hechas: Int,
            totales: Int,
        ) {
            val ahora = System.currentTimeMillis()
            val terminado = totales > 0 && hechas >= totales
            if (!terminado && ahora - ultimoAviso < INTERVALO_AVISO_MS && ultimoAviso != 0L) return
            ultimoAviso = ahora
            setProgress(workDataOf(CLAVE_HECHAS to hechas, CLAVE_TOTALES to totales))
            if (inputData.zona() != null) {
                try {
                    setForeground(infoPrimerPlano(hechas, totales))
                } catch (_: IllegalStateException) {
                    // Android 12+ no deja pasar a primer plano desde segundo plano: la descarga sigue sin notificación.
                }
            }
        }

        private fun infoPrimerPlano(
            hechas: Int,
            totales: Int,
        ): ForegroundInfo {
            val gestor = contexto.getSystemService(NotificationManager::class.java)
            gestor.createNotificationChannel(
                NotificationChannel(
                    TrabajoDatosMapa.CANAL_DESCARGAS,
                    contexto.getString(R.string.descargas_canal_nombre),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val porcentaje = if (totales > 0) (hechas * PORCENTAJE_TOTAL / totales).coerceIn(0, PORCENTAJE_TOTAL) else 0
            val notificacion =
                NotificationCompat
                    .Builder(contexto, TrabajoDatosMapa.CANAL_DESCARGAS)
                    .setSmallIcon(R.drawable.ic_stat_rally)
                    .setContentTitle(contexto.getString(R.string.detalle_titulo))
                    .setContentText(contexto.getString(R.string.detalle_progreso, hechas, totales))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(PORCENTAJE_TOTAL, porcentaje, totales <= 0)
                    .addAction(
                        0,
                        contexto.getString(R.string.descargas_cancelar),
                        WorkManager.getInstance(contexto).createCancelPendingIntent(id),
                    ).build()
            return ForegroundInfo(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        companion object {
            const val NOMBRE_TRABAJO = "detalle-satelite"
            const val NOMBRE_ZONA = "detalle-satelite-zona"
            const val ETIQUETA_DETALLE = "detalle"
            const val ETIQUETA_AUTOMATICO = "detalle-automatico"
            const val CLAVE_HECHAS = "hechas"
            const val CLAVE_TOTALES = "totales"
            private const val CLAVE_OESTE = "oeste"
            private const val CLAVE_SUR = "sur"
            private const val CLAVE_ESTE = "este"
            private const val CLAVE_NORTE = "norte"
            private const val CLAVE_ZOOM = "zoom"
            private const val ETIQUETA_LOG = "TrabajoDetalle"
            private const val ID_NOTIFICACION = 4002
            private const val MAX_INTENTOS = 10
            private const val INTERVALO_AVISO_MS = 1_000L
            private const val PORCENTAJE_TOTAL = 100

            /** Datos de entrada de una descarga manual de [zona] a [zoom]. */
            fun datosDeZona(
                zona: Rectangulo,
                zoom: Int,
            ): Data =
                workDataOf(
                    CLAVE_OESTE to zona.oeste,
                    CLAVE_SUR to zona.sur,
                    CLAVE_ESTE to zona.este,
                    CLAVE_NORTE to zona.norte,
                    CLAVE_ZOOM to zoom,
                )

            private fun Data.zona(): Pair<Rectangulo, Int>? {
                if (!hasKeyWithValueOfType<Int>(CLAVE_ZOOM)) return null
                return Rectangulo(
                    getDouble(CLAVE_OESTE, 0.0),
                    getDouble(CLAVE_SUR, 0.0),
                    getDouble(CLAVE_ESTE, 0.0),
                    getDouble(CLAVE_NORTE, 0.0),
                ) to getInt(CLAVE_ZOOM, 0)
            }
        }
    }
