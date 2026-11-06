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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.ui.copiloto.BotonMicrofono
import app.trombito.rallycopilot.ui.copiloto.BotonModoCopiloto
import app.trombito.rallycopilot.ui.copiloto.BotonSilencio
import app.trombito.rallycopilot.ui.copiloto.CopilotoEnMapa
import app.trombito.rallycopilot.ui.copiloto.modoAlAlternarSilencio
import app.trombito.rallycopilot.ui.navegacion.NavegacionEnMapa
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.tutorial.ConPista
import app.trombito.rallycopilot.ui.tutorial.LadoPista
import app.trombito.rallycopilot.ui.tutorial.Pista

const val ETIQUETA_COLUMNA_BOTONES = "mapa-columna-botones"

/** Segundos que los botones táctiles siguen a la vista tras el último toque al mapa, en marcha. */
internal const val RETARDO_OCULTAR_BOTONES_MS = 6_000L

/** Por encima de esta velocidad (km/h) el coche va «en marcha»: los botones secundarios se esconden solos. */
internal const val VELOCIDAD_EN_MARCHA_KMH = 8

/** En marcha solo van los botones fijos; parado (o con el mapa tocado hace menos de 6 s) van todos. */
internal fun botonesTactilesVisibles(
    enMarcha: Boolean,
    tocado: Boolean,
): Boolean = !enMarcha || tocado

/** Los botones redondos de la columna derecha, de arriba abajo: primero los táctiles y luego los fijos. */
internal class BotonesMapa(
    /** Capas, notas grandes y micrófono: aparecen al tocar el mapa y se esconden a los 6 s en marcha. */
    val tactiles: List<@Composable () -> Unit> = emptyList(),
    /** Copiloto, silenciar y brújula (o recentrar): siempre a la vista. */
    val fijos: List<@Composable () -> Unit> = emptyList(),
)

/**
 * Los botones de la columna derecha. Fijos: hoja del copiloto, silenciar (alterna Silencio y el modo anterior) y
 * brújula, que si el mapa se movió es el de «mi ubicación». Táctiles: capas, notas grandes y micrófono. Sin mapa
 * (modo de notas grandes) no hay capas ni brújula. [modoAnterior] es el último modo del copiloto con voz.
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
            if (hablar) ({ BotonMicrofono(copiloto.estado.escuchando, copiloto.acciones.onEscuchar) }) else null,
        )
    val fijos: List<(@Composable () -> Unit)?> =
        listOf(
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
    return BotonesMapa(tactiles.filterNotNull(), fijos.filterNotNull())
}

/**
 * Columna de botones redondos de 48 dp con 12 dp de hueco, anclada a la derecha sobre la hoja. Los fijos quedan abajo
 * y los táctiles aparecen encima con [tactilesVisibles]. En horizontal, con poca altura, los táctiles van en una
 * segunda columna a la izquierda de los fijos.
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
            Column(verticalArrangement = hueco, horizontalAlignment = Alignment.End) {
                botones.fijos.forEach { it() }
            }
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
            botones.fijos.forEach { it() }
        }
    }
}

private fun <T> tweenCorta() =
    androidx.compose.animation.core
        .tween<T>(Movimiento.CORTA)
