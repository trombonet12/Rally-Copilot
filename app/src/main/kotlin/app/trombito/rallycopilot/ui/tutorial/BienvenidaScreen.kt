package app.trombito.rallycopilot.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Titulo
import kotlinx.coroutines.launch

const val ETIQUETA_BIENVENIDA_SALTAR = "bienvenida-saltar"
const val ETIQUETA_BIENVENIDA_SIGUIENTE = "bienvenida-siguiente"
const val ETIQUETA_BIENVENIDA_INDICADOR = "bienvenida-indicador"
const val ETIQUETA_BIENVENIDA_PAGINA_PREFIJO = "bienvenida-pagina-"

/** Las páginas de la bienvenida, en orden; cada una con su título y sus dos líneas. */
enum class PaginaBienvenida(
    @StringRes val titulo: Int,
    @StringRes val lineaA: Int,
    @StringRes val lineaB: Int,
) {
    NOTAS(R.string.tutorial_notas_titulo, R.string.tutorial_notas_a, R.string.tutorial_notas_b),
    VOZ(R.string.tutorial_voz_titulo, R.string.tutorial_voz_a, R.string.tutorial_voz_b),
    CRONO(R.string.tutorial_crono_titulo, R.string.tutorial_crono_a, R.string.tutorial_crono_b),
    RADAR(R.string.tutorial_radar_titulo, R.string.tutorial_radar_a, R.string.tutorial_radar_b),
    OFFLINE(R.string.tutorial_offline_titulo, R.string.tutorial_offline_a, R.string.tutorial_offline_b),
    PERMISOS(R.string.tutorial_permisos_titulo, R.string.tutorial_permisos_a, R.string.tutorial_permisos_b),
}

private val ALTO_BARRA_SUPERIOR = 56.dp
private val PUNTO = 8.dp
private val PUNTO_ACTIVO = 24.dp
private const val OPACIDAD_PUNTO = 0.4f

/**
 * Bienvenida del primer arranque: [PaginaBienvenida] deslizables con ilustración animada, indicador de página,
 * «Saltar» arriba y «Siguiente» (en la última, «Empezar») abajo. Saltar y Empezar llaman a [onTerminar]; quien la usa
 * decide qué pasa después (marcarla como vista, ir a Inicio o volver a Ajustes).
 */
@Composable
fun BienvenidaScreen(
    onTerminar: () -> Unit,
    permisos: List<PermisoBienvenida>,
    modifier: Modifier = Modifier,
    paginaInicial: Int = 0,
) {
    val paginas = PaginaBienvenida.entries
    val pager = rememberPagerState(initialPage = paginaInicial) { paginas.size }
    val alcance = rememberCoroutineScope()
    val ultima = pager.currentPage == paginas.lastIndex
    BackHandler(enabled = pager.currentPage > 0) { alcance.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(ALTO_BARRA_SUPERIOR), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!ultima) {
                    TextButton(
                        onClick = onTerminar,
                        modifier =
                            Modifier
                                .heightIn(
                                    min = Medidas.zonaTactil,
                                ).padding(end = Espacio.s)
                                .testTag(ETIQUETA_BIENVENIDA_SALTAR),
                    ) {
                        Text(
                            stringResource(R.string.tutorial_saltar),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { indice ->
                Box(Modifier.fillMaxSize().testTag(ETIQUETA_BIENVENIDA_PAGINA_PREFIJO + indice)) {
                    PaginaDeBienvenida(paginas[indice], permisos)
                }
            }
            PieBienvenida(pager, ultima) {
                if (ultima) onTerminar() else alcance.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

/** Indicador de página y botón de avance. */
@Composable
private fun PieBienvenida(
    pager: PagerState,
    ultima: Boolean,
    onAvanzar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.l, vertical = Espacio.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IndicadorPagina(pager.currentPage, pager.pageCount, Modifier.weight(1f))
        Button(
            onClick = onAvanzar,
            modifier = Modifier.heightIn(min = Medidas.accionPrincipal).testTag(ETIQUETA_BIENVENIDA_SIGUIENTE),
        ) {
            Text(
                stringResource(if (ultima) R.string.tutorial_empezar else R.string.tutorial_siguiente),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Puntos de página: la actual, alargada y en ámbar. Lo lee TalkBack como «Página x de n». */
@Composable
private fun IndicadorPagina(
    actual: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.tutorial_pagina, actual + 1, total)
    Row(
        modifier = modifier.testTag(ETIQUETA_BIENVENIDA_INDICADOR).semantics { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { indice ->
            val activo = indice == actual
            val color =
                if (activo) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OPACIDAD_PUNTO)
                }
            Box(
                Modifier
                    .size(
                        width = if (activo) PUNTO_ACTIVO else PUNTO,
                        height = PUNTO,
                    ).background(color, CircleShape),
            )
        }
    }
}

/** Una página: ilustración y texto, en columna en vertical y lado a lado en horizontal. */
@Composable
private fun PaginaDeBienvenida(
    pagina: PaginaBienvenida,
    permisos: List<PermisoBienvenida>,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = Espacio.l),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Ilustracion(pagina, permisos) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { TextoDePagina(pagina) }
            }
        } else {
            // Centrada en vertical: las páginas cortas no se quedan pegadas arriba con un hueco debajo.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.xl),
            ) {
                Ilustracion(pagina, permisos)
                TextoDePagina(pagina)
            }
        }
    }
}

@Composable
private fun Ilustracion(
    pagina: PaginaBienvenida,
    permisos: List<PermisoBienvenida>,
) {
    when (pagina) {
        PaginaBienvenida.NOTAS -> IlustracionNotas()
        PaginaBienvenida.VOZ -> IlustracionVoz()
        PaginaBienvenida.CRONO -> IlustracionCrono()
        PaginaBienvenida.RADAR -> IlustracionRadar()
        PaginaBienvenida.OFFLINE -> IlustracionOffline()
        PaginaBienvenida.PERMISOS -> ListaPermisos(permisos)
    }
}

@Composable
private fun TextoDePagina(pagina: PaginaBienvenida) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(
            text = stringResource(pagina.titulo),
            style = Titulo,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(pagina.lineaA), style = Cuerpo, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(pagina.lineaB), style = Cuerpo, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Une la bienvenida con el flujo de permisos del sistema. */
@Composable
fun BienvenidaRoute(
    onTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BienvenidaScreen(onTerminar = onTerminar, permisos = rememberPermisosBienvenida(), modifier = modifier)
}
