package app.trombito.rallycopilot.data.copia

import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.domain.copia.CopiaDatos
import app.trombito.rallycopilot.domain.copia.CopiaInvalidaException
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import app.trombito.rallycopilot.domain.copia.ResumenCopia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Versión de la app que se anota en el manifiesto de la copia. */
data class InfoApp(
    val versionName: String,
    val versionCode: Long,
)

/**
 * Copia de seguridad: un zip con `rally.db` (checkpoint del WAL hecho) y `manifest.json`. Nunca lleva mapas, voces ni
 * modelos (grandes y se regeneran). Al importar valida el zip (solo esas dos entradas, tamaño máximo, sin rutas),
 * rechaza esquemas más nuevos que el actual y comprueba la base con SQLite (`integrity_check`, `user_version` e
 * identidad de Room) antes de sustituir la real; si el esquema es menor, Room migra al volver a abrir. Tras importar
 * la Room queda cerrada: hay que reiniciar el proceso.
 */
class CopiaSeguridad(
    private val base: RallyDatabase,
    private val archivoBase: File,
    private val info: InfoApp,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = MAX_BYTES,
) : CopiaDatos {
    // Nombres cortos: SQLite les añade -journal/-wal y en Windows (tests) la ruta total tiene un límite.
    private val temporalExportar get() = File(archivoBase.parentFile, "exp.tmp")
    private val temporalImportar get() = File(archivoBase.parentFile, "imp.tmp")

    override suspend fun exportar(salida: OutputStream): ResumenCopia =
        withContext(dispatcher) {
            val copia = temporalExportar
            try {
                copiarBaseConsistente(copia)
                val manifiesto = manifiestoDe(copia)
                escribirZip(salida, copia, manifiesto)
                manifiesto.resumen()
            } finally {
                borrarConAcompanantes(copia)
            }
        }

    /**
     * Checkpoint del WAL y copia del archivo principal mientras una transacción de escritura impide que otro hilo
     * escriba (y dispare un checkpoint automático) a mitad de la copia.
     */
    private fun copiarBaseConsistente(destino: File) {
        val db = base.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        db.beginTransaction()
        try {
            Files.copy(archivoBase.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            db.endTransaction()
        }
    }

    /** El manifiesto sale de la propia copia, así los recuentos casan con lo que se guarda. */
    private fun manifiestoDe(copia: File): ManifiestoCopia {
        val ahoraMs = ahora()
        return InspectorBase.abrir(copia).use { db ->
            ManifiestoCopia(
                esquema = db.version,
                versionName = info.versionName,
                versionCode = info.versionCode,
                fechaMs = ahoraMs,
                fecha = Instant.ofEpochMilli(ahoraMs).toString(),
                recorridos = InspectorBase.contar(db, "recorrido"),
                tramos = InspectorBase.contar(db, "tramo"),
                pasadas = InspectorBase.contar(db, "pasada"),
            )
        }
    }

    private fun escribirZip(
        salida: OutputStream,
        copia: File,
        manifiesto: ManifiestoCopia,
    ) {
        val zip = ZipOutputStream(salida)
        zip.putNextEntry(ZipEntry(ENTRADA_MANIFIESTO))
        zip.write(jsonCopia.encodeToString(ManifiestoCopia.serializer(), manifiesto).toByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(ENTRADA_BASE))
        copia.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    override suspend fun importar(entrada: InputStream): ResumenCopia =
        withContext(dispatcher) {
            val temporal = temporalImportar
            try {
                val manifiesto = LectorZipCopia(maxBytes).leer(entrada, temporal)
                validar(manifiesto, temporal)
                sustituirBase(temporal)
                manifiesto.resumen()
            } finally {
                borrarConAcompanantes(temporal)
            }
        }

    private fun validar(
        manifiesto: ManifiestoCopia,
        copia: File,
    ) {
        val actual = base.openHelper.writableDatabase
        comprobarNoFuturo(manifiesto.esquema, actual.version)
        val identidad = InspectorBase.inspeccionar(copia)
        comprobarNoFuturo(identidad.version, actual.version)
        val compatible =
            identidad.hash != null &&
                (identidad.version != actual.version || identidad.hash == InspectorBase.hashDeRoom(actual))
        if (!compatible) {
            throw CopiaInvalidaException(MotivoCopiaInvalida.INCOMPATIBLE, "no es una base de Room de esta versión")
        }
        if (identidad.version != manifiesto.esquema) invalida("el manifiesto no casa con la base")
    }

    private fun comprobarNoFuturo(
        recibida: Int,
        actual: Int,
    ) {
        if (recibida > actual) {
            throw CopiaInvalidaException(MotivoCopiaInvalida.ESQUEMA_FUTURO, "esquema $recibida > $actual")
        }
    }

    private fun sustituirBase(validada: File) {
        base.close()
        borrarAcompanantes(archivoBase)
        Files.move(validada.toPath(), archivoBase.toPath(), StandardCopyOption.REPLACE_EXISTING)
        borrarAcompanantes(archivoBase)
    }

    private fun borrarAcompanantes(archivo: File) {
        for (sufijo in SUFIJOS) File(archivo.path + sufijo).delete()
    }

    private fun borrarConAcompanantes(archivo: File) {
        archivo.delete()
        borrarAcompanantes(archivo)
    }

    companion object {
        /** 200 MB: las bases reales pesan unos pocos MB; esto solo frena un zip desmedido. */
        const val MAX_BYTES = 200L * 1024 * 1024
        private val SUFIJOS = listOf("-wal", "-shm", "-journal")
    }
}
