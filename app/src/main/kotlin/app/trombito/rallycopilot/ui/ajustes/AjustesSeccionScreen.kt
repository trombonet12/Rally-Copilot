package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.theme.Espacio

private val MARGEN_VERTICAL_APAISADO = 8.dp

/**
 * Subpantalla de una sección: arriba la vista previa fija (no se desplaza, 160 a 200 dp) que cambia en vivo al tocar,
 * y debajo los controles con su desplazamiento. En apaisado, la vista previa a la izquierda y los controles a la
 * derecha, para que ambos se vean.
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
) {
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(stringResource(seccion.titulo()), acciones.onVolver)
            val alto = altoPrevista(seccion)
            BoxWithConstraints(Modifier.weight(1f)) {
                val altoDisponible = maxHeight
                if (alto != null && maxWidth > altoDisponible) {
                    Row(
                        modifier = Modifier.padding(horizontal = Espacio.l).padding(bottom = MARGEN_VERTICAL_APAISADO),
                        horizontalArrangement = Arrangement.spacedBy(Espacio.l),
                    ) {
                        PrevistaDeSeccion(
                            seccion,
                            ajustes,
                            acciones,
                            alto = minOf(alto, altoDisponible - MARGEN_VERTICAL_APAISADO),
                            modifier = Modifier.weight(1f),
                        )
                        Controles(seccion, ajustes, acciones, copiaEnCurso, vozSharvard, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = Espacio.l),
                        verticalArrangement = Arrangement.spacedBy(Espacio.l),
                    ) {
                        if (alto != null) PrevistaDeSeccion(seccion, ajustes, acciones, alto)
                        Controles(seccion, ajustes, acciones, copiaEnCurso, vozSharvard, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Los controles de la sección, con su propio desplazamiento (la vista previa no se mueve). */
@Composable
@Suppress("LongParameterList") // lo mismo que la pantalla, más el modificador de cada columna
private fun Controles(
    seccion: SeccionAjustes,
    ajustes: AjustesConduccion,
    acciones: AccionesAjustes,
    copiaEnCurso: Boolean,
    vozSharvard: EstadoVozDescarga,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.l),
    ) {
        when (seccion) {
            SeccionAjustes.COPILOTO -> ControlesCopiloto(ajustes, acciones)
            SeccionAjustes.NOTAS -> ControlesNotas(ajustes, acciones)
            SeccionAjustes.VOZ -> ControlesVoz(ajustes, acciones, vozSharvard)
            SeccionAjustes.INDICACIONES -> ControlesIndicaciones(ajustes, acciones)
            SeccionAjustes.TRAMOS -> ControlesTramos(ajustes, acciones)
            SeccionAjustes.MAPA -> ControlesMapa(ajustes, acciones)
            SeccionAjustes.ESCUCHA -> ControlesEscucha(ajustes, acciones)
            SeccionAjustes.TEMA -> ControlesTema(ajustes.tema, acciones.onTema)
            SeccionAjustes.DATOS -> ControlesDatos(acciones, copiaEnCurso)
            SeccionAjustes.AYUDA -> ControlesAyuda(acciones)
            SeccionAjustes.ACERCA_DE -> Unit
        }
    }
}
