package app.trombito.rallycopilot.ui.analisis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.EstadoVacio
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_ANALISIS_PLACEHOLDER = "analisis-placeholder"

/** Marcador de la pantalla de análisis de una pasada: la sustituye el lote que la implementa. */
@Composable
fun AnalisisPlaceholder(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BarraSuperior(titulo = stringResource(R.string.analisis_titulo), onVolver = onVolver)
        EstadoVacio(
            icono = Iconos.Grafico,
            titulo = stringResource(R.string.analisis_placeholder_titulo),
            texto = stringResource(R.string.analisis_placeholder_texto),
            etiqueta = ETIQUETA_ANALISIS_PLACEHOLDER,
        )
    }
}
