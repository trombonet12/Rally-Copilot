package app.trombito.rallycopilot.data.datosmapa

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Datos de Mallorca en `filesDir/mapa/` (estado en Room, descarga en WorkManager). Ver [RepositorioDatosMapa]. */
class RepositorioDatosMapaImpl(
    private val dao: DatosMapaDao,
    private val ajustesDao: AjustesMapasDao,
    private val workManager: WorkManager,
    private val config: ConfigDatosMapa,
    private val dispatcher: CoroutineDispatcher,
) : RepositorioDatosMapa {
    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private val instalador = InstaladorDatos(config)

    override val ajustes: Flow<AjustesMapas> = ajustesDao.observarAjustes().map { it?.aDominio() ?: AjustesMapas() }

    override val estado: Flow<EstadoDatosMapa> =
        combine(
            dao.observarArchivos(),
            dao.observarEstado(),
            workManager.getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO),
        ) { archivos, estado, trabajos ->
            construirEstado(archivos, estado, trabajos.firstOrNull { !it.state.isFinished })
        }

    override suspend fun guardarAjustes(ajustes: AjustesMapas) {
        val anterior = ajustesDao.ajustes()?.aDominio() ?: AjustesMapas()
        ajustesDao.guardarAjustes(ajustes.aEntidad())
        if (anterior.soloWifi != ajustes.soloWifi) reencolarPendiente(ajustes.soloWifi)
    }

    override fun comprobarActualizacion() {
        alcance.launch {
            if (dao.archivos().isNotEmpty()) encolar(automatico = true, soloWifi = soloWifiActual())
        }
    }

    override fun descargar() {
        alcance.launch {
            dao.establecerError(null)
            encolar(automatico = false, soloWifi = soloWifiActual())
        }
    }

    override fun cancelar() {
        // Los .part se conservan: la siguiente descarga reanuda.
        workManager.cancelUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO)
        alcance.launch { dao.establecerError(null) }
    }

    override suspend fun borrar() {
        workManager.cancelUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO).await()
        withContext(dispatcher) {
            instalador.borrarTodo()
            dao.borrarInstalacion()
        }
    }

    override suspend fun descartarAviso() = dao.establecerAviso(null)

    override suspend fun estilos(): EstilosMapa? =
        withContext(dispatcher) {
            val mapa = config.directorioMapa
            val vectorial = File(mapa, "vectorial.json").takeIf { it.isFile } ?: return@withContext null
            val raizUrl = "file://" + mapa.absolutePath.replace('\\', '/')
            val satelite =
                File(mapa, "satelite.json").takeIf { it.isFile && File(mapa, ARCHIVO_SATELITE).isFile }
            val estilos =
                EstilosMapa(
                    vectorial = vectorial.readText().replace(MARCADOR_DIR, raizUrl),
                    satelite = satelite?.readText()?.replace(MARCADOR_DIR, raizUrl),
                )
            val validos =
                listOfNotNull(estilos.vectorial, estilos.satelite).all { ValidadorEstilo.esValido(it, raizUrl) }
            if (validos) {
                estilos
            } else {
                Log.w(ETIQUETA_LOG, "Estilo rechazado: referencia recursos fuera de la carpeta del mapa")
                dao.establecerError(MotivoError.MANIFEST_INVALIDO.name)
                null
            }
        }

    private suspend fun soloWifiActual(): Boolean = ajustesDao.ajustes()?.soloWifi ?: AjustesMapas().soloWifi

    private fun encolar(
        automatico: Boolean,
        soloWifi: Boolean,
        politica: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val peticion =
            OneTimeWorkRequestBuilder<TrabajoDatosMapa>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(if (soloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEGUNDOS, TimeUnit.SECONDS)
                .setInputData(workDataOf(TrabajoDatosMapa.CLAVE_AUTOMATICO to automatico))
                .addTag(if (automatico) ETIQUETA_AUTOMATICO else ETIQUETA_MANUAL)
                .build()
        workManager.enqueueUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO, politica, peticion)
    }

    /** Si hay un trabajo esperando red, lo vuelve a encolar con la restricción nueva (solo WiFi o cualquier red). */
    private suspend fun reencolarPendiente(soloWifi: Boolean) {
        val pendiente =
            workManager
                .getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO)
                .first()
                .firstOrNull { it.state == WorkInfo.State.ENQUEUED } ?: return
        encolar(ETIQUETA_AUTOMATICO in pendiente.tags, soloWifi, ExistingWorkPolicy.REPLACE)
    }

    private companion object {
        const val ETIQUETA_LOG = "RepositorioDatosMapa"
        const val MARCADOR_DIR = "{{DIR}}"
        const val ARCHIVO_SATELITE = "pnoa-mallorca-z16.pmtiles"
        const val ETIQUETA_AUTOMATICO = "automatico"
        const val ETIQUETA_MANUAL = "manual"
        const val BACKOFF_SEGUNDOS = 30L
    }
}
