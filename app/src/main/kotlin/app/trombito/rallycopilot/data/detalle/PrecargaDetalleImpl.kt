package app.trombito.rallycopilot.data.detalle

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.datosmapa.aDominio
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDetalle
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.domain.datos.ProgresoDetalle
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.PoliticaPrecarga
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Batería y carga del dispositivo según el sistema (la intención fija `ACTION_BATTERY_CHANGED`). */
class EnergiaAndroid(
    private val contexto: Context,
) : EstadoEnergia {
    override fun permiteDescargaAutomatica(): Boolean {
        val intento = contexto.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
        val enchufado = intento.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val nivel = intento.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val escala = intento.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val porcentaje = if (nivel >= 0 && escala > 0) nivel * PORCENTAJE_TOTAL / escala else PORCENTAJE_TOTAL
        return PoliticaPrecarga.permiteAutomatica(enchufado, porcentaje)
    }

    private companion object {
        const val PORCENTAJE_TOTAL = 100
    }
}

/**
 * Detalle z18–19 con WorkManager: un trabajo único `detalle-satelite` encadenado (las peticiones se ejecutan una tras
 * otra, así solo escribe el MBTiles un trabajo a la vez). El automático pide WiFi (red sin tarifa) y el manual respeta
 * «Solo con WiFi» de Mapas offline. Ver [PrecargaDetalle].
 */
class PrecargaDetalleImpl(
    private val workManager: WorkManager,
    private val config: ConfigDatosMapa,
    private val ajustesDao: AjustesMapasDao,
    private val pendientes: RutasPendientes,
    private val dispatcher: CoroutineDispatcher,
) : PrecargaDetalle {
    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private val archivo: File get() = File(config.directorioMapa, NOMBRE_DETALLE)

    override val estado: Flow<EstadoDetalle> =
        workManager
            .getWorkInfosByTagFlow(TrabajoDetalleSatelite.ETIQUETA_DETALLE)
            .map { trabajos ->
                EstadoDetalle(
                    bytes = archivo.takeIf { it.isFile }?.length() ?: 0,
                    teselas = AlmacenDetalle.contarEn(archivo),
                    descarga =
                        trabajos
                            .filter { !it.state.isFinished }
                            .maxByOrNull { it.state == WorkInfo.State.RUNNING }
                            ?.aProgreso(),
                )
            }.flowOn(dispatcher)

    override fun precargarTramos() {
        alcance.launch { encolarAutomatico() }
    }

    override fun precargarRuta(puntos: List<PuntoGeo>) {
        alcance.launch {
            val corredor = TeselasPrecarga.corredor(puntos, TeselasPrecarga.ZOOM_CORREDOR)
            if (pendientes.guardar(corredor) != null) encolarAutomatico()
        }
    }

    override fun descargarZona(
        zona: Rectangulo,
        zoom: Int,
    ) {
        alcance.launch {
            val soloWifi = (ajustesDao.ajustes()?.aDominio() ?: AjustesMapas()).soloWifi
            val peticion =
                OneTimeWorkRequestBuilder<TrabajoDetalleSatelite>()
                    .setConstraints(redRequerida(soloWifi))
                    .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTOS, TimeUnit.MINUTES)
                    .setInputData(TrabajoDetalleSatelite.datosDeZona(zona, zoom))
                    .addTag(TrabajoDetalleSatelite.ETIQUETA_DETALLE)
                    .build()
            // Cola aparte de la automática: una zona manual no espera detrás de un corredor que aguarda WiFi.
            workManager.enqueueUniqueWork(
                TrabajoDetalleSatelite.NOMBRE_ZONA,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                peticion,
            )
        }
    }

    override fun cancelar() {
        workManager.cancelAllWorkByTag(TrabajoDetalleSatelite.ETIQUETA_DETALLE)
    }

    override suspend fun borrar() {
        workManager.cancelAllWorkByTag(TrabajoDetalleSatelite.ETIQUETA_DETALLE).await()
        withContext(dispatcher) {
            pendientes.borrarTodas()
            archivo.delete()
            File(archivo.path + "-journal").delete()
        }
    }

    /**
     * Encola el trabajo automático salvo que ya haya uno esperando (leerá los tramos al ejecutarse, así que ya incluirá
     * lo nuevo). Si hay uno en marcha, el nuevo va detrás.
     */
    private suspend fun encolarAutomatico() {
        val existentes = workManager.getWorkInfosForUniqueWorkFlow(TrabajoDetalleSatelite.NOMBRE_TRABAJO).first()
        val esperando =
            existentes.any {
                it.state == WorkInfo.State.ENQUEUED &&
                    TrabajoDetalleSatelite.ETIQUETA_AUTOMATICO in it.tags
            }
        if (esperando) return
        val peticion =
            OneTimeWorkRequestBuilder<TrabajoDetalleSatelite>()
                .setConstraints(redRequerida(soloWifi = true, bateriaNoBaja = true))
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTOS, TimeUnit.MINUTES)
                .addTag(TrabajoDetalleSatelite.ETIQUETA_AUTOMATICO)
                .addTag(TrabajoDetalleSatelite.ETIQUETA_DETALLE)
                .build()
        workManager.enqueueUniqueWork(
            TrabajoDetalleSatelite.NOMBRE_TRABAJO,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            peticion,
        )
    }

    /** El automático además espera a que la batería no esté baja; el manual (lo pidió Joan) no. */
    private fun redRequerida(
        soloWifi: Boolean,
        bateriaNoBaja: Boolean = false,
    ) = Constraints
        .Builder()
        .setRequiredNetworkType(if (soloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(bateriaNoBaja)
        .build()

    private fun WorkInfo.aProgreso() =
        ProgresoDetalle(
            hechas = progress.getInt(TrabajoDetalleSatelite.CLAVE_HECHAS, 0),
            totales = progress.getInt(TrabajoDetalleSatelite.CLAVE_TOTALES, 0),
            esperando = state != WorkInfo.State.RUNNING,
        )

    private companion object {
        const val BACKOFF_MINUTOS = 15L
    }
}
