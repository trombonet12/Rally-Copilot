package app.trombito.rallycopilot.data.importar

import android.content.ContentResolver
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.FileNotFoundException
import javax.inject.Inject

/** Abre el documento con el `ContentResolver`: solo lo que el sistema concede para ese Uri, sin permisos. */
class AbridorDocumentosAndroid
    @Inject
    constructor(
        private val resolver: ContentResolver,
    ) : AbridorDocumentos {
        override fun abrir(uri: Uri): DocumentoAbierto? =
            try {
                val (nombre, tamano) = metadatos(uri)
                resolver.openInputStream(uri)?.let { DocumentoAbierto(it, nombre, tamano) }
            } catch (e: FileNotFoundException) {
                Log.w(ETIQUETA, "No se pudo abrir: ${e.javaClass.simpleName}")
                null
            } catch (e: SecurityException) {
                Log.w(ETIQUETA, "Sin acceso: ${e.javaClass.simpleName}")
                null
            }

        private fun metadatos(uri: Uri): Pair<String?, Long?> =
            try {
                val columnas = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
                resolver.query(uri, columnas, null, null, null)?.use(::leerMetadatos) ?: (null to null)
            } catch (e: SQLiteException) {
                Log.w(ETIQUETA, "Sin metadatos: ${e.javaClass.simpleName}")
                null to null
            } catch (e: IllegalArgumentException) {
                Log.w(ETIQUETA, "Sin metadatos: ${e.javaClass.simpleName}")
                null to null
            }

        private fun leerMetadatos(c: Cursor): Pair<String?, Long?> {
            if (!c.moveToFirst()) return null to null
            val iNombre = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val iTamano = c.getColumnIndex(OpenableColumns.SIZE)
            val nombre = if (iNombre >= 0) c.getString(iNombre) else null
            val tamano = if (iTamano >= 0 && !c.isNull(iTamano)) c.getLong(iTamano) else null
            return nombre to tamano
        }

        private companion object {
            const val ETIQUETA = "AbridorDocumentos"
        }
    }
