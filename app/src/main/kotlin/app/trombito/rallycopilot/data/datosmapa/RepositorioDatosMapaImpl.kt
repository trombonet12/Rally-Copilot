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
import app.trombito.rallycopilot.motor.datos.Complemento
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
@Suppress("LongParameterList", "TooManyFunctions") // colaboradores de Room y WorkManager; una función por orden
class RepositorioDatosMapaImpl(
    private val dao: DatosMapaDao,
    private val ajustesDao: AjustesMapasDao,
    private val workManager: WorkManager,
    private val config: ConfigDatosMapa,
    private val dispatcher: CoroutineDispatcher,
    private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
    /** Al abrir la app encola la descarga automática del detalle de los tramos que aún no lo tengan. */
    private val precarga: PrecargaDetalle = PrecargaDetalle.Ninguna,
    /**
     * Complementos que el usuario ya eligió en Ajustes (voz Sharvard, relieve activado): si no están instalados se
     * bajan solos con WiFi al abrir la app (p. ej. tras actualizar desde una versión que los traía en el APK).
     */
    private val complementosElegidos: suspend () -> Set<Complemento> = { emptySet() },
) : RepositorioDatosMapa {
    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private val instalador = InstaladorDatos(config)

    override val ajustes: Flow<AjustesMapas> = ajustesDao.observarAjustes().map { it?.aDominio() ?: AjustesMapas() }

    override val estado: Flow<EstadoDatosMapa> =
        combine(
            dao.observarArchivos(),
            dao.observarEstado(),
            workManager.getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO),
            workManager.getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO_COMPLEMENTOS),
            siembra.listo,
        ) { archivos, estado, trabajos, complementos, lista ->
            construirEstado(
                archivos,
                estado,
                trabajos.firstOrNull { !it.state.isFinished },
                complementos.filter { !it.state.isFinished },
            ).copy(preparando = !lista)
        }

    override suspend fun guardarAjustes(ajustes: AjustesMapas) {
        val anterior = ajustesDao.ajustes()?.aDominio() ?: AjustesMapas()
        ajustesDao.guardarAjustes(ajustes.aEntidad())
        if (anterior.soloWifi != ajustes.soloWifi) reencolarPendiente(ajustes.soloWifi)
    }

    override fun comprobarActualizacion() {
        alcance.launch {
            siembra.esperar()
            if (dao.archivos().isNotEmpty()) {
                encolar(automatico = true, soloWifi = ajustesDao.soloWifiActual())
                pedirElegidosSinInstalar()
            }
            precarga.precargarTramos()
        }
    }

    /** Lo elegido en Ajustes que ya no viene en el APK y falta en el móvil: se baja solo con WiFi. */
    private suspend fun pedirElegidosSinInstalar() {
        val ids = dao.archivos().map { it.id }.toSet()
        complementosElegidos()
            .filter { it != Complemento.SATELITE && !it.estaCompleto(ids) }
            .forEach { encolarComplemento(it, soloWifi = true, manual = false) }
    }

    override fun descargar(complemento: Complemento) {
        alcance.launch {
            siembra.esperar()
            dao.establecerError(null)
            if (complemento == Complemento.SATELITE) {
                encolar(automatico = false, soloWifi = ajustesDao.soloWifiActual(), complementos = setOf(complemento))
            } else {
                encolarComplemento(complemento, soloWifi = false, manual = true)
            }
        }
    }

    override fun descargarConWifi(complemento: Complemento) {
        alcance.launch {
            siembra.esperar()
            val ids = dao.archivos().map { it.id }.toSet()
            if (complemento.estaCompleto(ids)) return@launch
            if (complemento == Complemento.SATELITE) {
                encolar(automatico = false, soloWifi = true, complementos = setOf(complemento))
            } else {
                encolarComplemento(complemento, soloWifi = true, manual = false)
            }
        }
    }

    override fun cancelar() {
        // Los .part se conservan: la siguiente descarga reanuda.
        workManager.cancelUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO)
        workManager.cancelUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO_COMPLEMENTOS)
        alcance.launch { dao.establecerError(null) }
    }

    override suspend fun borrar(complemento: Complemento) {
        if (complemento == Complemento.SATELITE) {
            borrarSatelite()
        } else {
            workManager.cancelAllWorkByTag(PREFIJO_ETIQUETA_COMPLEMENTO + complemento.name).await()
            withContext(dispatcher) {
                siembra.esperar()
                val archivos = dao.archivos().filter { complemento.incluye(it.id) }
                // Primero el registro: si se corta a medias, un archivo huérfano es mejor que un registro sin archivo.
                if (archivos.isNotEmpty()) dao.borrarArchivos(archivos.map { it.id })
                instalador.borrarArchivos(archivos.map { it.nombre })
                dao.establecerError(null)
            }
        }
    }

    private suspend fun borrarSatelite() {
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

    override suspend fun tamanoPendiente(complemento: Complemento): Long? =
        withContext(dispatcher) {
            try {
                val instalados = dao.archivos().map { it.id }.toSet()
                ClienteHttp(config)
                    .leerManifestRemoto(config)
                    .manifest
                    .archivos
                    .filter { complemento.incluye(it.id) && it.id !in instalados }
                    .sumOf { it.bytes }
            } catch (e: IOException) {
                Log.w(ETIQUETA_LOG, "Sin tamaño de ${complemento.name}: ${e.javaClass.simpleName}")
                null
            } catch (e: ManifestInvalido) {
                Log.w(ETIQUETA_LOG, "Manifest remoto no válido al pedir el tamaño de ${complemento.name}", e)
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
            val claro = File(mapa, "vectorial-claro.json").takeIf { it.isFile }
            val hayZ17 = File(mapa, NOMBRE_TESELAS_Z17).isFile
            val detalle = File(mapa, NOMBRE_DETALLE).takeIf { it.isFile }
            // Relieve y curvas se bajan al elegirlos: sin sus archivos el estilo no debe apuntar a ellos.
            val conRelieve = { texto: String -> EstiloRelieve.sinAusentes(texto) { File(mapa, it).isFile } }
            val estilos =
                EstilosMapa(
                    vectorial = conRelieve(vectorial.readText()).replace(MARCADOR_DIR, raizUrl),
                    vectorialClaro = claro?.readText()?.let(conRelieve)?.replace(MARCADOR_DIR, raizUrl),
                    satelite =
                        satelite
                            ?.readText()
                            ?.let(conRelieve)
                            ?.let { if (hayZ17) EstiloSatelite.conZ17(it) else it }
                            ?.let { if (detalle != null) EstiloSatelite.conDetalle(it, detalle.rutaAbsoluta()) else it }
                            ?.replace(MARCADOR_DIR, raizUrl),
                )
            val validos =
                listOfNotNull(
                    estilos.vectorial,
                    estilos.vectorialClaro,
                    estilos.satelite,
                ).all { ValidadorEstilo.esValido(it, raizUrl) }
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
        complementos: Set<Complemento> = emptySet(),
    ) {
        val peticion =
            OneTimeWorkRequestBuilder<TrabajoDatosMapa>()
                .setConstraints(red(soloWifi))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEGUNDOS, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        TrabajoDatosMapa.CLAVE_AUTOMATICO to automatico,
                        TrabajoDatosMapa.CLAVE_COMPLEMENTOS to complementos.map { it.name }.toTypedArray(),
                    ),
                ).addTag(if (automatico) ETIQUETA_AUTOMATICO else ETIQUETA_MANUAL)
                .also { b -> complementos.forEach { b.addTag(PREFIJO_ETIQUETA_COMPLEMENTO + it.name) } }
                .build()
        workManager.enqueueUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO, politica, peticion)
    }

    private fun red(soloWifi: Boolean) =
        Constraints
            .Builder()
            .setRequiredNetworkType(if (soloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

    /**
     * Encola la descarga de un complemento (relieve o voz) en su propia cola: no espera al satélite, que puede estar
     * parado a la espera de WiFi, y baja solo sus archivos. Los trabajos de esta cola van uno detrás de otro. Si ya hay
     * uno de este complemento: en marcha, se deja; esperando red, una petición del usuario ([manual]) lo sustituye
     * (puede que fuera una descarga solo con WiFi y el usuario ya aceptó la red que tiene).
     */
    private suspend fun encolarComplemento(
        complemento: Complemento,
        soloWifi: Boolean,
        manual: Boolean,
    ) {
        val etiqueta = PREFIJO_ETIQUETA_COMPLEMENTO + complemento.name
        val vigente =
            workManager
                .getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO_COMPLEMENTOS)
                .first()
                .firstOrNull { !it.state.isFinished && etiqueta in it.tags }
        if (vigente != null && (vigente.state == WorkInfo.State.RUNNING || !manual)) return
        val peticion =
            OneTimeWorkRequestBuilder<TrabajoDatosMapa>()
                .setConstraints(red(soloWifi))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEGUNDOS, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        TrabajoDatosMapa.CLAVE_AUTOMATICO to false,
                        TrabajoDatosMapa.CLAVE_COMPLEMENTOS to arrayOf(complemento.name),
                        TrabajoDatosMapa.CLAVE_SOLO_COMPLEMENTOS to true,
                    ),
                ).addTag(ETIQUETA_COMPLEMENTO_FIJO)
                .addTag(etiqueta)
                .build()
        val politica = if (vigente != null) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        workManager.enqueueUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO_COMPLEMENTOS, politica, peticion)
    }

    /** Si hay un trabajo esperando red, lo vuelve a encolar con la restricción nueva (solo WiFi o cualquier red). */
    private suspend fun reencolarPendiente(soloWifi: Boolean) {
        val pendiente =
            workManager
                .getWorkInfosForUniqueWorkFlow(TrabajoDatosMapa.NOMBRE_TRABAJO)
                .first()
                .firstOrNull { it.state == WorkInfo.State.ENQUEUED } ?: return
        val complementos =
            Complemento.entries.filter { PREFIJO_ETIQUETA_COMPLEMENTO + it.name in pendiente.tags }.toSet()
        encolar(ETIQUETA_AUTOMATICO in pendiente.tags, soloWifi, ExistingWorkPolicy.REPLACE, complementos)
    }

    private companion object {
        const val ETIQUETA_LOG = "RepositorioDatosMapa"
        const val MARCADOR_DIR = "{{DIR}}"
        const val ETIQUETA_AUTOMATICO = "automatico"
        const val ETIQUETA_MANUAL = "manual"

        /** La red de las descargas de complementos no sigue «Solo con WiFi» (la elige quien las pide). */
        const val ETIQUETA_COMPLEMENTO_FIJO = "complemento-fijo"
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
