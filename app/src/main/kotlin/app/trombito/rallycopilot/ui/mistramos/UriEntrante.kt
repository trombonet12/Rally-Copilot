package app.trombito.rallycopilot.ui.mistramos

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * El documento que otra app entrega a Rally Copilot: el dato de un «Abrir con» (`ACTION_VIEW`) o el `EXTRA_STREAM` de
 * un «Compartir» (`ACTION_SEND`). Solo se aceptan `content://`: un `file://` podría apuntar a los datos privados de la
 * propia app. Null si el intent no trae ninguno (por ejemplo, el arranque normal desde el lanzador).
 */
fun Intent.uriDeImportacion(): Uri? {
    val uri =
        when (action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
    return uri?.takeIf { it.scheme == "content" }
}
