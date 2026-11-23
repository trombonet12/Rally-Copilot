package app.trombito.rallycopilot.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.ui.inicio.EntradaInicio
import app.trombito.rallycopilot.ui.inicio.InicioRoute
import app.trombito.rallycopilot.ui.resumen.ResumenPendienteViewModel
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.widget.Atajo
import app.trombito.rallycopilot.ui.widget.AtajosViewModel

/**
 * Inicio y mapa. Inicio abre el detalle de su última salida y pide el atajo «Repetir último tramo» (el mismo del
 * icono y el widget). El mapa, que es el lienzo y no una página más, entra y sale con un fundido simple en vez del
 * deslizamiento del resto de pantallas.
 */
@Suppress("LongParameterList") // reparte los ViewModels y callbacks de la raíz de navegación, como `destinos`
internal fun NavGraphBuilder.destinosInicioMapa(
    nav: NavHostController,
    atajos: AtajosViewModel,
    atajoPendiente: Destino?,
    resumenes: ResumenPendienteViewModel,
    reducirMovimiento: Boolean,
    onAtajoAplicado: () -> Unit,
) {
    composable<RutaInicio> {
        InicioRoute(
            onEntrada = { abrirEntrada(nav, it) },
            onAbrirUltima = { nav.navigate(RutaTramo(it)) },
            onRepetirUltima = { atajos.pedir(Atajo.REPETIR_ULTIMO) },
        )
    }
    composable<RutaMapa>(
        enterTransition = { fundidoEntrada(reducirMovimiento) },
        popEnterTransition = { fundidoEntrada(reducirMovimiento) },
        exitTransition = { fundidoSalida(reducirMovimiento) },
        popExitTransition = { fundidoSalida(reducirMovimiento) },
    ) { DestinoMapa(nav, atajoPendiente, onAtajoAplicado, resumenes) }
}

private fun fundidoEntrada(reducirMovimiento: Boolean): EnterTransition =
    if (reducirMovimiento) EnterTransition.None else fadeIn(tween(Movimiento.MEDIA))

private fun fundidoSalida(reducirMovimiento: Boolean): ExitTransition =
    if (reducirMovimiento) ExitTransition.None else fadeOut(tween(Movimiento.MEDIA))

/** Las entradas desactivadas no llegan aquí (la fila no es pulsable). */
private fun abrirEntrada(
    nav: NavHostController,
    entrada: EntradaInicio,
) {
    when (entrada) {
        EntradaInicio.CONDUCIR -> nav.navigate(RutaMapa)
        EntradaInicio.AJUSTES -> nav.navigate(RutaAjustes)
        EntradaInicio.MIS_TRAMOS -> nav.navigate(RutaMisTramos)
        EntradaInicio.MIS_CARRETERAS -> nav.navigate(RutaMisCarreteras)
        EntradaInicio.PERFIL -> nav.navigate(RutaPerfil)
    }
}
