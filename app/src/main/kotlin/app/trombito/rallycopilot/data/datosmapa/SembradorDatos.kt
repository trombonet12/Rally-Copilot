package app.trombito.rallycopilot.data.datosmapa

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.di.DispatcherIo
import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import app.trombito.rallycopilot.motor.datos.ManifestDatos
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Quien necesite los datos de `mapa/` (o comparar con el remoto) espera a que termine la siembra del APK. */
fun interface EsperaDatosSembrados {
    suspend fun esperar()

    /** true cuando la siembra ha terminado (con éxito o no); mientras no, Mapas offline dice «Preparando mapas…». */
    val listo: Flow<Boolean> get() = flowOf(true)

    companion object {
        /** Sin siembra (tests o código que no la necesita). */
        val Ninguna = EsperaDatosSembrados { }
    }
}

/** Qué hizo una pasada de [SembradorDatos.sembrar]. */
data class ResultadoSiembra(
    /** Ids copiados desde el APK e instalados. */
    val sembrados: Set<String> = emptySet(),
    /** Ids cuyo asset no se pudo verificar o copiar: se quedan como estaban. */
    val fallidos: Set<String> = emptySet(),
    /** No hizo falta mirar nada: ya se sembró esta versión del APK, o el APK no trae datos. */
    val omitido: Boolean = false,
)

/**
 * Instala en `filesDir/mapa/` los datos que van dentro del APK (`assets/datos/manifest-incluido.json` y los archivos
 * que cita), por el mismo camino que una descarga: [InstaladorDatos] con verificación SHA-256, `.part`, renombrado
 * atómico y extracción de zips; después los registra en Room con su sha, así el planificador de descargas no los
 * vuelve a bajar si el remoto coincide.
 *
 * Siembra un id solo si falta o si el local es de una versión de datos más vieja que la del APK (los datos locales
 * más nuevos no se tocan). Guarda en `mapa/.sembrado` la huella del manifest incluido ya sembrado: en los arranques
 * siguientes solo se lee ese manifest (unos KB) y no se vuelve a hashear nada. Trabaja en `.sembrando/`, aparte de
 * `.nuevo/`, para no tocar una descarga en curso.
 */
@Singleton
class SembradorDatos internal constructor(
    private val dao: DatosMapaDao,
    private val config: ConfigDatosMapa,
    private val abrirAsset: (String) -> InputStream,
    private val dispatcher: CoroutineDispatcher,
) : EsperaDatosSembrados {
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
        dao: DatosMapaDao,
        config: ConfigDatosMapa,
        @DispatcherIo dispatcher: CoroutineDispatcher,
    ) : this(dao, config, { contexto.assets.open(it) }, dispatcher)

    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private val arrancado = AtomicBoolean(false)
    private val terminado = CompletableDeferred<Unit>()
    private val estadoListo = MutableStateFlow(false)
    private val instalador = InstaladorDatos(config, NOMBRE_SIEMBRA)
    private val marcador: File get() = File(config.directorioMapa, NOMBRE_MARCADOR)

    override val listo: StateFlow<Boolean> get() = estadoListo

    /** Arranca la siembra en segundo plano; llamarlo más de una vez no hace nada. */
    fun iniciar() {
        if (!arrancado.compareAndSet(false, true)) return
        alcance.launch {
            try {
                sembrar()
            } finally {
                estadoListo.value = true
                terminado.complete(Unit)
            }
        }
    }

    /** Suspende hasta que la siembra acaba (la arranca si nadie lo había hecho). Nunca lanza. */
    override suspend fun esperar() {
        iniciar()
        terminado.await()
    }

    /** Una pasada de siembra. Los fallos de un archivo no la rompen; los de E/S dejan sin marcar para reintentar. */
    suspend fun sembrar(): ResultadoSiembra =
        withContext(dispatcher) {
            try {
                sembrarSinProteger()
            } catch (e: IOException) {
                perdonar("Siembra de datos interrumpida", e)
            } catch (e: SerializationException) {
                perdonar("Manifest incluido ilegible", e)
            } catch (e: IllegalArgumentException) {
                perdonar("Manifest incluido no válido", e)
            } catch (e: ZipInseguroException) {
                perdonar("Zip incluido rechazado", e)
            } catch (e: SQLiteException) {
                perdonar("Base de datos no disponible para sembrar", e)
            }
        }

    private fun perdonar(
        mensaje: String,
        causa: Exception,
    ): ResultadoSiembra {
        Log.w(ETIQUETA_LOG, mensaje, causa)
        return ResultadoSiembra()
    }

    private suspend fun sembrarSinProteger(): ResultadoSiembra {
        val bytesManifest = leerAsset(ASSET_MANIFEST) ?: return ResultadoSiembra(omitido = true) // APK sin datos
        val huella = sha256(bytesManifest)
        val yaSembrado = marcador.takeIf { it.isFile }?.readText()?.trim() == huella
        return if (yaSembrado) ResultadoSiembra(omitido = true) else sembrarNuevo(bytesManifest, huella)
    }

    private suspend fun sembrarNuevo(
        bytesManifest: ByteArray,
        huella: String,
    ): ResultadoSiembra {
        val manifest = leerManifestIncluido(bytesManifest)
        val locales = dao.archivos().associate { it.id to LocalInstalado(it.nombre, it.sha256, it.bytes, it.version) }
        val pendientes = manifest.archivos.filter { debeSembrar(it, locales[it.id], config.directorioMapa) }
        val hayEspacio = pendientes.isEmpty() || config.bytesLibres() >= pendientes.sumOf { it.bytes }
        if (!hayEspacio) Log.w(ETIQUETA_LOG, "Sin espacio para sembrar los datos incluidos; se reintenta al arrancar")
        if (!hayEspacio) return ResultadoSiembra()
        val resultado = instalarPendientes(manifest, pendientes)
        config.directorioMapa.mkdirs()
        marcador.writeText(huella)
        return resultado
    }

    private suspend fun instalarPendientes(
        manifest: ManifestDatos,
        pendientes: List<ArchivoDatos>,
    ): ResultadoSiembra {
        File(config.directorioMapa, NOMBRE_SIEMBRA).deleteRecursively()
        val (buenos, fallidos) = pendientes.partition { copiarYVerificar(it) }
        if (buenos.isNotEmpty()) {
            instalador.instalar(buenos, emptyList(), manifest.archivos.map { it.nombre })
            val versionActual = dao.estado()?.versionInstalada
            val version =
                if (versionActual != null && esMasVieja(versionActual, manifest.version) != true) {
                    versionActual
                } else {
                    manifest.version
                }
            dao.registrarInstalacion(buenos.map { it.aEntidad() }, emptyList(), version, avisar = false)
        }
        File(config.directorioMapa, NOMBRE_SIEMBRA).deleteRecursively()
        return ResultadoSiembra(buenos.map { it.id }.toSet(), fallidos.map { it.id }.toSet())
    }

    /** Copia el asset a su `.part`, lo verifica y lo deja como `.ok`. Si no cuadra, lo borra y devuelve false. */
    private fun copiarYVerificar(archivo: ArchivoDatos): Boolean {
        val parcial = instalador.parcial(archivo)
        parcial.parentFile?.mkdirs()
        val sha = copiarAsset("$CARPETA_ASSETS/${archivo.nombre}$SUFIJO_ASSET", parcial)
        val valido = sha != null && parcial.length() == archivo.bytes && sha == archivo.sha256
        if (valido) {
            instalador.marcarVerificado(archivo)
        } else {
            Log.w(ETIQUETA_LOG, "El dato incluido '${archivo.id}' falta o no coincide con su SHA-256: no se instala")
            parcial.delete()
        }
        return valido
    }

    /** Lee un asset entero, o null si no existe. */
    private fun leerAsset(ruta: String): ByteArray? =
        try {
            abrirAsset(ruta).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }

    /**
     * Copia un asset a [destino] sin cargarlo en memoria y calcula su SHA-256 durante la copia (sin releer el archivo,
     * que son cientos de MB); null si no existe o falla la copia.
     */
    private fun copiarAsset(
        ruta: String,
        destino: File,
    ): String? =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(abrirAsset(ruta), digest).use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: IOException) {
            null
        }

    private companion object {
        const val ETIQUETA_LOG = "SembradorDatos"
        const val CARPETA_ASSETS = "datos"
        const val ASSET_MANIFEST = "$CARPETA_ASSETS/manifest-incluido.json"

        /**
         * Todos los datos viajan en el APK como `datos/<nombre>.asset` (el manifest incluido, con el nombre real).
         * Motivo: el Android Gradle Plugin descomprime los assets `*.gz` al fusionarlos y les quita la extensión,
         * con lo que `vias-*.tsv.gz` llegaría como `.tsv` y su SHA-256 no coincidiría. Un sufijo uniforme los deja
         * intactos.
         * Lo genera `GenerarAssetsDatos` en `app/build.gradle.kts`.
         */
        const val SUFIJO_ASSET = ".asset"
        const val NOMBRE_MARCADOR = ".sembrado"
        const val NOMBRE_SIEMBRA = ".sembrando"
    }
}

private const val ESQUEMA_SOPORTADO = 1
private val FORMATO = Json { ignoreUnknownKeys = true }
private val SHA256 = Regex("^[0-9a-f]{64}$")
private val NOMBRE = Regex("^[a-z0-9][a-z0-9._-]*$")
private val DIGITOS = Regex("\\d+")

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private class LocalInstalado(
    val nombre: String,
    val sha256: String,
    val bytes: Long,
    val version: String,
)

/** Sembrar si falta (en Room o en disco) o si el local es de una versión estrictamente más vieja que la del APK. */
private fun debeSembrar(
    incluido: ArchivoDatos,
    local: LocalInstalado?,
    mapa: File,
): Boolean =
    when {
        local == null -> true
        !estaEnDisco(local, mapa) -> true
        local.sha256 == incluido.sha256 -> false
        else -> esMasVieja(local.version, incluido.version) == true
    }

/** Los zips se descomprimen al instalar: no hay un archivo con su nombre, vale el registro de Room. */
private fun estaEnDisco(
    local: LocalInstalado,
    mapa: File,
): Boolean =
    local.nombre.endsWith(".zip", ignoreCase = true) ||
        File(mapa, local.nombre).let { it.isFile && it.length() == local.bytes }

private fun leerManifestIncluido(bytes: ByteArray): ManifestDatos {
    val manifest = FORMATO.decodeFromString<ManifestDatos>(bytes.decodeToString())
    require(manifest.esquema == ESQUEMA_SOPORTADO) { "Esquema no soportado: ${manifest.esquema}" }
    manifest.archivos.forEach {
        require(it.bytes > 0 && SHA256.matches(it.sha256) && NOMBRE.matches(it.nombre) && ".." !in it.nombre) {
            "Entrada no válida en el manifest incluido: '${it.id}'"
        }
    }
    val ids = manifest.archivos.map { it.id }
    require(ids.toSet().size == ids.size) { "Ids repetidos" }
    return manifest
}

/**
 * Compara dos versiones de datos (`datos-2026.10.31-5`, `2026.10.31-5`...) por sus grupos de dígitos, de izquierda a
 * derecha. true si [a] es estrictamente más vieja que [b]; null si no se pueden comparar (alguna sin dígitos).
 */
internal fun esMasVieja(
    a: String,
    b: String,
): Boolean? {
    val na = numeros(a)
    val nb = numeros(b)
    if (na.isEmpty() || nb.isEmpty()) return null
    val primeraDiferencia =
        (0 until maxOf(na.size, nb.size)).firstNotNullOfOrNull { i ->
            val x = na.getOrElse(i) { 0L }
            val y = nb.getOrElse(i) { 0L }
            (x < y).takeIf { x != y }
        }
    return primeraDiferencia ?: false
}

private fun numeros(version: String): List<Long> =
    DIGITOS.findAll(version).map { it.value.toLongOrNull() ?: Long.MAX_VALUE }.toList()
