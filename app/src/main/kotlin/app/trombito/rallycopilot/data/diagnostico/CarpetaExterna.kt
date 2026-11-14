package app.trombito.rallycopilot.data.diagnostico

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Carpeta externa de la app (`getExternalFilesDir(null)`) solo donde otras apps no pueden leerla: con almacenamiento
 * con ámbito (API 30+). Por debajo, una app con permiso de almacenamiento podría leer la copia y el registro (rutas,
 * viajes), así que no se escribe nada ahí (la exportación manual por SAF sigue disponible).
 */
internal fun carpetaExternaPrivada(
    contexto: Context,
    sdk: Int = Build.VERSION.SDK_INT,
): File? = if (sdk >= Build.VERSION_CODES.R) contexto.getExternalFilesDir(null) else null
