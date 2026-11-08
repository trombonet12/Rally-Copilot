package app.trombito.rallycopilot.ui.widget

import android.content.Context
import android.content.Intent
import app.trombito.rallycopilot.MainActivity
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos

/** Los atajos del icono («Conducir libre», «Repetir último tramo») y el botón del widget entran por aquí. */
enum class Atajo(
    /** Valor del extra [EXTRA_ATAJO]; es el mismo que lleva `res/xml/shortcuts.xml`. */
    val clave: String,
) {
    CONDUCIR_LIBRE("conducir_libre"),
    REPETIR_ULTIMO("repetir_ultimo"),
}

/** Acción de los intents de atajo (la misma que `res/xml/shortcuts.xml`). */
const val ACCION_ATAJO = "app.trombito.rallycopilot.action.ATAJO"

/** Extra con la [Atajo.clave] del atajo pedido. */
const val EXTRA_ATAJO = "atajo"

/** El atajo que pide este intent, o null si no es de atajo o la clave no se conoce. */
fun Intent.atajo(): Atajo? {
    if (action != ACCION_ATAJO) return null
    val clave = getStringExtra(EXTRA_ATAJO)
    return Atajo.entries.firstOrNull { it.clave == clave }
}

/** Intent explícito a [MainActivity] que pide este atajo (para el botón del widget). */
fun Atajo.intent(contexto: Context): Intent =
    Intent(contexto, MainActivity::class.java)
        .setAction(ACCION_ATAJO)
        .putExtra(EXTRA_ATAJO, clave)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** A dónde lleva un atajo, ya resuelto con los datos de los tramos. */
sealed interface DestinoAtajo {
    /** El mapa en modo libre, siguiendo al coche. */
    data object Libre : DestinoAtajo

    /** El mapa con la ruta a la salida de [destino] (la del último tramo) abierta en la vista previa. */
    data class AlTramo(
        val destino: Destino,
    ) : DestinoAtajo

    /** «Repetir último tramo» sin ningún tramo cronometrado: se abre el mapa en libre y se explica. */
    data object SinTramos : DestinoAtajo
}

/** Resuelve [atajo] con el último tramo cronometrado de [repositorio] (el mismo que muestra Inicio). */
suspend fun resolverAtajo(
    atajo: Atajo,
    repositorio: RepositorioTramos,
): DestinoAtajo =
    when (atajo) {
        Atajo.CONDUCIR_LIBRE -> {
            DestinoAtajo.Libre
        }

        Atajo.REPETIR_ULTIMO -> {
            repositorio.ultimaSalidaReciente()?.let { DestinoAtajo.AlTramo(Destino(it.nombre, it.salida)) }
                ?: DestinoAtajo.SinTramos
        }
    }

/** Lo que hace la app ante un [DestinoAtajo] según si ya está en el mapa. */
data class PasoAtajo(
    /** Abrir el mapa (false si ya se está en él). */
    val abrirMapa: Boolean,
    /** Destino que se pasa al mapa para abrir su vista previa de ruta, si lo hay. */
    val destinoEnMapa: Destino?,
    /** Avisar (discretamente) de que no hay último tramo que repetir. */
    val avisarSinTramos: Boolean,
)

/** Traduce [destino] en lo que hay que hacer; [enMapa] es si la pantalla actual ya es el mapa. */
fun pasoDeAtajo(
    destino: DestinoAtajo,
    enMapa: Boolean,
): PasoAtajo =
    when (destino) {
        DestinoAtajo.Libre -> {
            PasoAtajo(abrirMapa = !enMapa, destinoEnMapa = null, avisarSinTramos = false)
        }

        is DestinoAtajo.AlTramo -> {
            PasoAtajo(abrirMapa = !enMapa, destinoEnMapa = destino.destino, avisarSinTramos = false)
        }

        DestinoAtajo.SinTramos -> {
            PasoAtajo(abrirMapa = !enMapa, destinoEnMapa = null, avisarSinTramos = true)
        }
    }
