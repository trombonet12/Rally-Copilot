package app.trombito.rallycopilot.ui.mistramos

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.trombito.rallycopilot.domain.tramos.TramoExportado
import java.io.File
import java.io.IOException

/** Tipo del archivo que se comparte. */
const val TIPO_GPX = "application/gpx+xml"

private const val SUBCARPETA = "compartir"

/** Autoridad del FileProvider declarado en el manifiesto (no exportado). */
internal fun autoridadCompartir(contexto: Context): String = "${contexto.packageName}.compartir"

/**
 * Escribe el [exportado] en `cache/compartir/`, borrando antes lo que se compartió la vez anterior para no acumular
 * recorridos en la caché; null si no se pudo escribir.
 */
internal fun escribirArchivoParaCompartir(
    contexto: Context,
    exportado: TramoExportado,
): File? =
    try {
        val carpeta = File(contexto.cacheDir, SUBCARPETA)
        carpeta.mkdirs()
        carpeta.listFiles()?.forEach { it.delete() }
        File(carpeta, exportado.nombreArchivo).also { it.writeText(exportado.contenido, Charsets.UTF_8) }
    } catch (_: IOException) {
        null
    }

/** El `content://` del FileProvider (no exportado) para el [archivo] ya escrito en la caché. */
internal fun uriParaCompartir(
    contexto: Context,
    archivo: File,
): Uri = FileProvider.getUriForFile(contexto, autoridadCompartir(contexto), archivo)

/** El envío del [uri] por el menú Compartir, con permiso temporal de lectura para la app elegida. */
internal fun intentCompartir(
    uri: Uri,
    nombreArchivo: String,
    titulo: String,
): Intent {
    val envio =
        Intent(Intent.ACTION_SEND)
            .setType(TIPO_GPX)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, nombreArchivo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    envio.clipData = ClipData.newRawUri(nombreArchivo, uri)
    return Intent.createChooser(envio, titulo)
}

/** Escribe el tramo y abre el menú Compartir de Android; false si no se pudo escribir el archivo. */
fun compartirTramo(
    contexto: Context,
    exportado: TramoExportado,
    titulo: String,
): Boolean {
    val archivo = escribirArchivoParaCompartir(contexto, exportado) ?: return false
    contexto.startActivity(intentCompartir(uriParaCompartir(contexto, archivo), exportado.nombreArchivo, titulo))
    return true
}
