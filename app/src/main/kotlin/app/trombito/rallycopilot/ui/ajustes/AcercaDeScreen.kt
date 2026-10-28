package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_ATRIBUCIONES = "acerca-atribuciones"

/** Atribuciones y licencias que exigen los datos y componentes de la app, en el orden en que se muestran. */
internal val ATRIBUCIONES: List<Int> =
    listOf(
        R.string.acerca_osm,
        R.string.acerca_pnoa,
        R.string.acerca_protomaps,
        R.string.acerca_noto,
        R.string.acerca_maplibre,
    )

/** Lee la versión instalada; se resuelve fuera de la pantalla para poder probarla con cualquier texto. */
@Composable
fun AcercaDeRoute(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val version =
        runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    AcercaDeScreen(version = version, onVolver = onVolver, modifier = modifier)
}

@Composable
fun AcercaDeScreen(
    version: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        BarraSuperior(stringResource(R.string.acerca_titulo), onVolver)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.acerca_version, version), style = MaterialTheme.typography.bodyLarge)
            Titulo(R.string.acerca_licencia_titulo)
            Text(stringResource(R.string.acerca_licencia_texto), style = MaterialTheme.typography.bodyMedium)
            Titulo(R.string.acerca_atribuciones_titulo)
            Column(Modifier.testTag(ETIQUETA_ATRIBUCIONES), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                ATRIBUCIONES.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun Titulo(
    @StringRes texto: Int,
) {
    Text(
        text = stringResource(texto),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = Espacio.m).semantics { heading() },
    )
}
