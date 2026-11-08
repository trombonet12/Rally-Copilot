package app.trombito.rallycopilot.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import app.trombito.rallycopilot.MainActivity
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.ui.comun.FormatoEs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Lo que enseña el widget: la última salida con sus tiempos ya formateados, o nada si aún no hay salidas. */
sealed interface ContenidoWidget {
    /** Sin ninguna salida cronometrada: «Conduce tu primer tramo». */
    data object Vacio : ContenidoWidget

    data class ConSalida(
        val nombre: String,
        val tiempo: String,
        val mejor: String,
    ) : ContenidoWidget
}

/** El contenido del widget con la última salida de [repositorio]. */
suspend fun contenidoWidget(repositorio: RepositorioTramos): ContenidoWidget =
    repositorio.ultimaSalidaReciente()?.let {
        ContenidoWidget.ConSalida(it.nombre, FormatoEs.tiempo(it.tiempoMs), FormatoEs.tiempo(it.mejorMs))
    } ?: ContenidoWidget.Vacio

/**
 * Las vistas del widget para [contenido]. Tocar el widget abre la app; el botón «Conducir» lanza el atajo de conducir
 * libre.
 */
fun construirVistas(
    contexto: Context,
    contenido: ContenidoWidget,
): RemoteViews {
    val vistas = RemoteViews(contexto.packageName, R.layout.widget_ultima_salida)
    when (contenido) {
        ContenidoWidget.Vacio -> {
            vistas.setTextViewText(R.id.widget_nombre, contexto.getString(R.string.widget_vacio))
            vistas.setViewVisibility(R.id.widget_tiempos, View.GONE)
        }

        is ContenidoWidget.ConSalida -> {
            vistas.setTextViewText(R.id.widget_nombre, contenido.nombre)
            vistas.setTextViewText(R.id.widget_tiempo, contenido.tiempo)
            vistas.setTextViewText(R.id.widget_mejor, contexto.getString(R.string.widget_mejor, contenido.mejor))
            vistas.setViewVisibility(R.id.widget_tiempos, View.VISIBLE)
        }
    }
    vistas.setOnClickPendingIntent(R.id.widget_raiz, pendiente(contexto, CODIGO_ABRIR, abrirApp(contexto)))
    vistas.setOnClickPendingIntent(
        R.id.widget_conducir,
        pendiente(contexto, CODIGO_CONDUCIR, Atajo.CONDUCIR_LIBRE.intent(contexto)),
    )
    return vistas
}

private fun abrirApp(contexto: Context) =
    Intent(contexto, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

private fun pendiente(
    contexto: Context,
    codigo: Int,
    intent: Intent,
): PendingIntent =
    PendingIntent.getActivity(
        contexto,
        codigo,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private const val CODIGO_ABRIR = 1
private const val CODIGO_CONDUCIR = 2
private const val ETIQUETA = "WidgetUltimaSalida"

/** Widget «Última salida» de la pantalla de inicio: último tramo, su tiempo y el mejor, y un botón para conducir. */
class WidgetUltimaSalida : AppWidgetProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Acceso {
        fun repositorioTramos(): RepositorioTramos
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val repositorio =
            EntryPointAccessors.fromApplication(context.applicationContext, Acceso::class.java).repositorioTramos()
        val pendiente = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val vistas = construirVistas(context, contenidoWidget(repositorio))
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, vistas) }
            } catch (e: SQLiteException) {
                Log.w(ETIQUETA, "No se pudo leer la última salida: ${e.javaClass.simpleName}")
            } catch (e: IllegalStateException) {
                Log.w(ETIQUETA, "No se pudo leer la última salida: ${e.javaClass.simpleName}")
            } finally {
                pendiente.finish()
            }
        }
    }
}

/** Pide a los widgets «Última salida» puestos en la pantalla de inicio que se vuelvan a pintar. */
object ActualizadorWidget {
    /** No hace nada si no hay ninguno puesto. */
    fun pedir(contexto: Context) {
        val aplicacion = contexto.applicationContext
        val ids =
            AppWidgetManager
                .getInstance(aplicacion)
                .getAppWidgetIds(ComponentName(aplicacion, WidgetUltimaSalida::class.java))
        if (ids.isEmpty()) return
        aplicacion.sendBroadcast(
            Intent(aplicacion, WidgetUltimaSalida::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }
}
