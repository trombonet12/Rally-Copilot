package app.trombito.rallycopilot.data.copia

import android.content.Context
import android.content.Intent
import android.os.Process
import app.trombito.rallycopilot.domain.copia.ReiniciadorApp

/**
 * Relanza la actividad principal en una tarea nueva y mata el proceso: así Hilt, Room y los servicios arrancan limpios
 * con la base importada (la Room anterior se cerró al importar).
 */
class ReiniciadorAppAndroid(
    private val contexto: Context,
) : ReiniciadorApp {
    override fun reiniciar() {
        val intent =
            contexto.packageManager
                .getLaunchIntentForPackage(contexto.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) contexto.startActivity(intent)
        Process.killProcess(Process.myPid())
    }
}
