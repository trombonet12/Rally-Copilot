package app.trombito.rallycopilot.ui.mapa

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.comun.ConLadoBotonMapa
import app.trombito.rallycopilot.ui.copiloto.BotonMicrofono
import app.trombito.rallycopilot.ui.copiloto.BotonModoCopiloto
import app.trombito.rallycopilot.ui.copiloto.BotonSilencio
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.modoAlAlternarSilencio
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.tutorial.ConPista
import app.trombito.rallycopilot.ui.tutorial.LadoPista
import app.trombito.rallycopilot.ui.tutorial.Pista

const val ETIQUETA_COLUMNA_BOTONES = "mapa-columna-botones"

/**
 * Los botones redondos de la columna derecha, de arriba abajo: primero los secundarios y luego los de conducción. Los
 * fijos ocupan siempre el mismo hueco (uno por botón, `null` = hueco reservado vacío) para que ninguno se desplace.
 */
internal class BotonesMapa(
    /** Capas y notas grandes: se esconden al ir deprisa (más de 20 km/h) y vuelven al parar. */
    val tactiles: List<@Composable () -> Unit> = emptyList(),
    /** Micrófono, modo del copiloto, silencio y brújula (o recentrar), con `null` donde falta el botón. */
    val fijos: List<(@Composable () -> Unit)?> = emptyList(),
)

/**
 * Los botones de la columna derecha, todos de 56 dp. De abajo arriba y siempre en su hueco: brújula (o «mi ubicación»
 * si el mapa se movió; se ve también en marcha), silenciar (alterna Silencio y el modo anterior), modo del copiloto y
 * micrófono (solo guiando o en libre; sin él su hueco queda reservado). Encima, solo parado: capas y notas grandes, que
 * al esconderse no mueven a los demás. Sin mapa (modo de notas grandes) no hay capas ni brújula, pero su hueco se
 * respeta. [modoAnterior] es el último modo del copiloto con voz.
 */
@Suppress("LongParameterList") // estado, vista, acciones, navegación, copiloto y menú son los bloques de la pantalla
internal fun botonesDeLaColumna(
    estado: MapaUiState,
    vista: EstadoVistaMapa,
    acciones: AccionesMapa,
    navegacion: NavegacionEnMapa,
    copiloto: CopilotoEnMapa,
    menu: MenuAbierto,
    modoAnterior: ModoCopiloto,
): BotonesMapa {
    val control = navegacion.estado.control
    val grandes = copiloto.estado.notasGrandes
    val hablar = control is EstadoNavegacion.Guiando || control is EstadoNavegacion.Libre
    val modo = copiloto.estado.modo
    val sigueAlCoche = vista.siguiendo || estado.posicion == null
    val tactiles: List<(@Composable () -> Unit)?> =
        listOf(
            if (grandes) null else ({ BotonCapas(menu.actual == MenuMapa.CAPAS, { menu.alternar(MenuMapa.CAPAS) }) }),
            { BotonNotasGrandes(grandes, { copiloto.acciones.onNotasGrandes(!grandes) }) },
        )
    val fijos: List<(@Composable () -> Unit)?> =
        listOf(
            if (hablar) {
                { BotonMicrofono(copiloto.estado.escuchando, copiloto.acciones.onEscuchar) }
            } else {
                null
            },
            {
                ConPista(Pista.MAPA, stringResource(R.string.pista_mapa)) {
                    BotonModoCopiloto(modo, menu.actual == MenuMapa.COPILOTO, { menu.alternar(MenuMapa.COPILOTO) })
                }
            },
            {
                BotonSilencio(
                    silenciado = modo == ModoCopiloto.SILENCIO,
                    onClick = { copiloto.acciones.onModo(modoAlAlternarSilencio(modo, modoAnterior)) },
                )
            },
            // Brújula (siguiendo al coche) o recentrar (mapa movido): siempre a la vista, también en marcha.
            if (grandes) {
                null
            } else {
                {
                    BotonOrientacion(
                        estado.orientacion,
                        acciones.onOrientacion,
                        siguiendo = sigueAlCoche,
                        onRecentrar = acciones.onRecentrar,
                    )
                }
            },
        )
    return BotonesMapa(tactiles.filterNotNull(), fijos)
}

/**
 * Columna de botones redondos de 56 dp con 12 dp de hueco, anclada abajo a la derecha sobre la hoja. Los fijos quedan
 * abajo, cada uno en su hueco (si falta uno se reserva el espacio), y los secundarios aparecen encima con
 * [tactilesVisibles] (nunca al ir deprisa): al aparecer o esconderse no desplazan a los fijos. En horizontal, con poca
 * altura, los táctiles van en una segunda columna a la izquierda de los fijos.
 */
@Composable
internal fun ColumnaBotonesMapa(
    botones: BotonesMapa,
    tactilesVisibles: Boolean,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val hueco = Arrangement.spacedBy(Espacio.m)
    val tactiles: @Composable () -> Unit = { botones.tactiles.forEach { it() } }
    val fijos: @Composable () -> Unit = {
        botones.fijos.forEach { boton -> boton?.invoke() ?: Spacer(Modifier.size(Medidas.botonMapa)) }
    }
    ConLadoBotonMapa(Medidas.botonMapa) {
        if (horizontal) {
            Row(
                modifier.testTag(ETIQUETA_COLUMNA_BOTONES),
                horizontalArrangement = hueco,
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedVisibility(
                    visible = tactilesVisibles,
                    enter = fadeIn(tweenCorta()) + expandHorizontally(tweenCorta()),
                    exit = fadeOut(tweenCorta()) + shrinkHorizontally(tweenCorta()),
                ) { Column(verticalArrangement = hueco, horizontalAlignment = Alignment.End) { tactiles() } }
                Column(verticalArrangement = hueco, horizontalAlignment = Alignment.End) { fijos() }
            }
        } else {
            Column(
                modifier.testTag(ETIQUETA_COLUMNA_BOTONES),
                verticalArrangement = hueco,
                horizontalAlignment = Alignment.End,
            ) {
                AnimatedVisibility(
                    visible = tactilesVisibles,
                    enter = fadeIn(tweenCorta()) + expandVertically(tweenCorta()),
                    exit = fadeOut(tweenCorta()) + shrinkVertically(tweenCorta()),
                ) { Column(verticalArrangement = hueco, horizontalAlignment = Alignment.End) { tactiles() } }
                fijos()
            }
        }
    }
}

private fun <T> tweenCorta() =
    androidx.compose.animation.core
        .tween<T>(Movimiento.CORTA)
