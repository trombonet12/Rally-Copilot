package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Titulo

private val MARGEN_VERTICAL_APAISADO = 8.dp

/**
 * Subpantalla de una categoría: arriba la vista previa fija (no se desplaza, 160 a 200 dp) que cambia en vivo al tocar,
 * y debajo los controles con su desplazamiento, agrupados con subtítulos si la categoría reúne varias secciones. La
 * vista previa es la del último grupo tocado (al abrir, la del primero). En apaisado, la vista previa a la izquierda
 * y los controles a la derecha, para que ambos se vean.
 */
@Composable
@Suppress("LongParameterList") // sección, ajustes, acciones y los dos estados extra (copia y voz Sharvard)
fun AjustesSeccionScreen(
    seccion: SeccionAjustes,
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    modifier: Modifier = Modifier,
    copiaEnCurso: Boolean = false,
    vozSharvard: EstadoVozDescarga = EstadoVozDescarga(),
    mapas: ResumenMapas? = null,
    copiaAutomatica: Boolean = true,
) {
    val grupos = seccion.grupos()
    var activo by rememberSaveable(seccion) { mutableIntStateOf(0) }
    SeguirGrupoCambiado(grupos, ajustes) { activo = it }
    val previa = grupos.getOrNull(activo)?.previa
    val extra = EstadosExtra(copiaEnCurso, vozSharvard, mapas, copiaAutomatica)
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(stringResource(seccion.titulo()), acciones.onVolver)
            val alto = altoPrevista(seccion)
            BoxWithConstraints(Modifier.weight(1f)) {
                val altoDisponible = maxHeight
                if (alto != null && previa != null && maxWidth > altoDisponible) {
                    Row(
                        modifier = Modifier.padding(horizontal = Espacio.l).padding(bottom = MARGEN_VERTICAL_APAISADO),
                        horizontalArrangement = Arrangement.spacedBy(Espacio.l),
                    ) {
                        PrevistaDeSeccion(
                            previa,
                            ajustes,
                            acciones,
                            alto = minOf(alto, altoDisponible - MARGEN_VERTICAL_APAISADO),
                            modifier = Modifier.weight(1f),
                        )
                        Controles(seccion, ajustes, acciones, extra, { activo = it }, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = Espacio.l),
                        verticalArrangement = Arrangement.spacedBy(Espacio.l),
                    ) {
                        if (alto != null && previa != null) PrevistaDeSeccion(previa, ajustes, acciones, alto)
                        Controles(seccion, ajustes, acciones, extra, { activo = it }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Lo que llega a la subpantalla aparte de [AjustesConduccion]: copia, voz Sharvard, mapas y copia automática. */
private class EstadosExtra(
    val copiaEnCurso: Boolean,
    val vozSharvard: EstadoVozDescarga,
    val mapas: ResumenMapas?,
    val copiaAutomatica: Boolean,
)

/** Cuando cambia un valor de un grupo (por toque, teclado o TalkBack), ese grupo pasa a mandar la vista previa. */
@Composable
private fun SeguirGrupoCambiado(
    grupos: List<GrupoAjustes>,
    ajustes: AjustesConduccion,
    onActivo: (Int) -> Unit,
) {
    var anterior by remember { mutableStateOf(ajustes) }
    LaunchedEffect(ajustes) {
        grupos.indexOfLast { it.huella(ajustes) != it.huella(anterior) }.takeIf { it >= 0 }?.let(onActivo)
        anterior = ajustes
    }
}

/** Los controles de la sección, con su propio desplazamiento (la vista previa no se mueve). */
@Composable
@Suppress("LongParameterList") // lo mismo que la pantalla, más el aviso de grupo tocado y el modificador
private fun Controles(
    seccion: SeccionAjustes,
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    extra: EstadosExtra,
    onActivo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grupos = seccion.grupos()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        when (seccion) {
            SeccionAjustes.DATOS -> {
                ControlesDatos(acciones, extra.copiaEnCurso, extra.mapas, extra.copiaAutomatica)
            }

            SeccionAjustes.AYUDA -> {
                ControlesAyuda(acciones)
            }

            else -> {
                grupos.forEachIndexed { indice, grupo ->
                    Column(
                        modifier = Modifier.alTocar(indice, onActivo),
                        verticalArrangement = Arrangement.spacedBy(Espacio.l),
                    ) {
                        if (grupos.size > 1) SubtituloGrupo(grupo.subtitulo)
                        ControlesDe(grupo.previa, ajustes, acciones, extra.vozSharvard)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtituloGrupo(
    @StringRes titulo: Int,
) {
    Text(
        text = stringResource(titulo),
        style = Titulo,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ControlesDe(
    previa: PrevistaAjustes,
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    vozSharvard: EstadoVozDescarga,
) {
    when (previa) {
        PrevistaAjustes.COPILOTO -> ControlesCopiloto(ajustes, acciones)
        PrevistaAjustes.NOTAS -> ControlesNotas(ajustes, acciones)
        PrevistaAjustes.VOZ -> ControlesVoz(ajustes, acciones, vozSharvard)
        PrevistaAjustes.INDICACIONES -> ControlesIndicaciones(ajustes, acciones)
        PrevistaAjustes.TRAMOS -> ControlesTramos(ajustes, acciones)
        PrevistaAjustes.MAPA -> ControlesMapa(ajustes, acciones)
        PrevistaAjustes.ESCUCHA -> ControlesEscucha(ajustes, acciones)
        PrevistaAjustes.TEMA -> ControlesTema(ajustes.tema, acciones.onTema)
    }
}

/** Avisa de que se ha tocado (o enfocado) algo del grupo, sin consumir el gesto, para que mande su vista previa. */
private fun Modifier.alTocar(
    indice: Int,
    onActivo: (Int) -> Unit,
): Modifier =
    pointerInput(indice) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onActivo(indice)
        }
    }.onFocusChanged { if (it.hasFocus) onActivo(indice) }
