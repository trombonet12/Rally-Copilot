package app.trombito.rallycopilot.ui.perfil

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import java.io.File
import java.io.IOException

/** Tipo de lo que se comparte: una imagen PNG del resumen, sin más datos. */
internal const val TIPO_POSTER = "image/png"

private const val SUBCARPETA = "compartir"
private const val PREFIJO_ARCHIVO = "resumen-"
private const val CALIDAD_PNG = 100

/** Nombre del archivo del póster de [resumen]: `resumen-2026-11.png`, sin ningún dato de ubicación. */
internal fun nombreArchivoPoster(resumen: ResumenMes): String =
    "$PREFIJO_ARCHIVO${resumen.mes.year}-${"%02d".format(resumen.mes.monthValue)}.png"

/**
 * Dibuja el póster del [resumen] y lo guarda en `cache/compartir/` (la única carpeta que sirve el FileProvider),
 * borrando antes los pósters de otras veces para no acumular imágenes; null si no se pudo escribir. Es trabajo de CPU
 * y de disco: se llama fuera del hilo principal.
 */
internal fun escribirPoster(
    contexto: Context,
    resumen: ResumenMes,
): File? =
    try {
        val contenido = ContenidoPoster.de(resumen, contexto.resources)
        val mascota = ContextCompat.getDrawable(contexto, R.drawable.mascota_bandera)?.mutate()
        val mapa = dibujarPoster(contenido, mascota)
        val carpeta = File(contexto.cacheDir, SUBCARPETA).apply { mkdirs() }
        carpeta.listFiles { archivo -> archivo.name.startsWith(PREFIJO_ARCHIVO) }?.forEach { it.delete() }
        File(carpeta, nombreArchivoPoster(resumen)).also { archivo ->
            archivo.outputStream().use { mapa.compress(Bitmap.CompressFormat.PNG, CALIDAD_PNG, it) }
        }
    } catch (_: IOException) {
        null
    }

/** El `content://` del FileProvider (no exportado, autoridad `<paquete>.compartir`) para el [archivo] de la caché. */
internal fun uriPoster(
    contexto: Context,
    archivo: File,
): Uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.compartir", archivo)

/**
 * El envío del [uri] por el menú Compartir: un `ACTION_SEND` de PNG con la imagen y permiso temporal de lectura. No
 * lleva texto, asunto ni extras de ningún tipo: lo único que sale de la app es la imagen.
 */
internal fun intentCompartirPoster(
    uri: Uri,
    titulo: String,
): Intent {
    val envio =
        Intent(Intent.ACTION_SEND)
            .setType(TIPO_POSTER)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    envio.clipData = ClipData.newRawUri(null, uri)
    return Intent.createChooser(envio, titulo)
}

/** Abre el menú Compartir de Android con el póster ya escrito en [archivo]; va en el hilo principal. */
fun compartirPoster(
    contexto: Context,
    archivo: File,
) {
    val titulo = contexto.getString(R.string.perfil_mes_compartir_titulo)
    contexto.startActivity(intentCompartirPoster(uriPoster(contexto, archivo), titulo))
}
