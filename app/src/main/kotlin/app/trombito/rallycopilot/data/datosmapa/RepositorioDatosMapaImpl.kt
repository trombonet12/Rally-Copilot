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
import app.trombito.rallycopilot.data.detalle.NOMBRE_DETALLE
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.motor.datos.ManifestInvalido
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
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Datos de Mallorca en `filesDir/mapa/` (estado en Room, descarga en WorkManager). Ver [RepositorioDatosMapa]. */
@Suppress("LongParameterList") // colaboradores: Room, WorkManager, configuracion, siembra y precarga
class RepositorioDatosMapaImpl(
    private val dao: DatosMapaDao,
    private val ajustesDao: AjustesMapasDao,
    private val workManager: WorkManager,
    private val config: ConfigDatosMapa,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
    /** Al abrir la app encola la descarga automática del detalle de los tramos que aún no lo tengan. */
    private val precarga: PrecargaDetalle = PrecargaDetalle.Ninguna,
) : RepositorioDatosMapa {
    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private val instalador = InstaladorDatos(config)

    override val ajustes: Flow<AjustesMapas> = ajustesDao.observarAjustes().map { it?.aDominio() ?: AjustesMapas() }

    override val estado: Flow<EstadoDatosMapa> =
        combine(
            dao.observarArchivos(),
            dao.observarEstado(),
            workManager.getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO),
            siembra.listo,
        ) { archivos, estado, trabajos, lista ->
            construirEstado(archivos, estado, trabajos.firstOrNull { !it.state.isFinished }).copy(preparando = !lista)
        }

    override suspend fun guardarAjustes(ajustes: AjustesMapas) {
        val anterior = ajustesDao.ajustes()?.aDominio() ?: AjustesMapas()
        ajustesDao.guardarAjustes(ajustes.aEntidad())
        if (anterior.soloWifi != ajustes.soloWifi) reencolarPendiente(ajustes.soloWifi)
    }

    override fun comprobarActualizacion() {
        alcance.launch {
            siembra.esperar()
            if (dao.archivos().isNotEmpty()) encolar(automatico = true, soloWifi = ajustesDao.soloWifiActual())
            precarga.precargarTramos()
        }
    }

    override fun descargar() {
        alcance.launch {
            siembra.esperar()
            dao.establecerError(null)
            encolar(automatico = false, soloWifi = ajustesDao.soloWifiActual())
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
            siembra.esperar()
            val satelite = dao.archivos().filter { it.id.startsWith(PREFIJO_SATELITE) }
            // Primero el registro: si se corta a medias, un archivo huérfano es mejor que un registro sin archivo.
            if (satelite.isNotEmpty()) dao.borrarArchivos(satelite.map { it.id })
            instalador.borrarSatelite(satelite.map { it.nombre })
            // Sin satélite el detalle z18-19 no sirve: se borran el MBTiles, su cola y las rutas pendientes.
            precarga.borrar()
            dao.establecerError(null)
        }
    }

    override suspend fun tamanoSatelite(): Long? =
        withContext(dispatcher) {
            try {
                val instalados = dao.archivos().map { it.id }.toSet()
                ClienteHttp(config)
                    .leerManifestRemoto(config)
                    .manifest
                    .archivos
                    .filter { it.id.startsWith(PREFIJO_SATELITE) && it.id !in instalados }
                    .sumOf { it.bytes }
            } catch (e: IOException) {
                Log.w(ETIQUETA_LOG, "Sin tamaño del satélite: ${e.javaClass.simpleName}")
                null
            } catch (e: ManifestInvalido) {
                Log.w(ETIQUETA_LOG, "Manifest remoto no válido al pedir el tamaño del satélite", e)
                null
            }
        }

    override suspend fun descartarAviso() = dao.establecerAviso(null)

    override suspend fun estilos(): EstilosMapa? =
        withContext(dispatcher) {
            siembra.esperar()
            val mapa = config.directorioMapa
            val vectorial = File(mapa, "vectorial.json").takeIf { it.isFile } ?: return@withContext null
            val raizUrl = "file://" + mapa.absolutePath.replace('\\', '/')
            val satelite = File(mapa, "satelite.json").takeIf { it.isFile && hayTeselasSatelite(mapa) }
            val hayZ17 = File(mapa, NOMBRE_TESELAS_Z17).isFile
            val detalle = File(mapa, NOMBRE_DETALLE).takeIf { it.isFile }
            val estilos =
                EstilosMapa(
                    vectorial = vectorial.readText().replace(MARCADOR_DIR, raizUrl),
                    satelite =
                        satelite
                            ?.readText()
                            ?.let { if (hayZ17) EstiloSatelite.conZ17(it) else it }
                            ?.let { if (detalle != null) EstiloSatelite.conDetalle(it, detalle.rutaAbsoluta()) else it }
                            ?.replace(MARCADOR_DIR, raizUrl),
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
        const val ETIQUETA_AUTOMATICO = "automatico"
        const val ETIQUETA_MANUAL = "manual"
        const val BACKOFF_SEGUNDOS = 30L
    }
}

private fun File.rutaAbsoluta() = absolutePath.replace(File.separatorChar, '/')

/** Cualquier `pnoa-mallorca*.pmtiles` instalado vale (z16 o z17): el nombre puede cambiar con el paquete. */
private suspend fun AjustesMapasDao.soloWifiActual(): Boolean = ajustes()?.soloWifi ?: AjustesMapas().soloWifi

private fun hayTeselasSatelite(mapa: File): Boolean {
    val archivos = mapa.listFiles() ?: return false
    return archivos.any { it.isFile && it.name.startsWith(PREFIJO_TESELAS_SATELITE) && it.name.endsWith(".pmtiles") }
}
