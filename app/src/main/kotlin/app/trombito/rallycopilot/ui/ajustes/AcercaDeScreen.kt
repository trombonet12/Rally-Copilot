package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Titulo

const val ETIQUETA_ATRIBUCIONES = "acerca-atribuciones"
const val ETIQUETA_ATRIBUCION_PREFIJO = "acerca-atribucion-"

private val LADO_MASCOTA = 96.dp

/** Una atribución: el título corto de la fila y el texto completo que se despliega. */
internal class Atribucion(
    @StringRes val titulo: Int,
    @StringRes val texto: Int,
)

/** Atribuciones y licencias que exigen los datos y componentes de la app, en el orden en que se muestran. */
internal val ATRIBUCIONES: List<Atribucion> =
    listOf(
        Atribucion(R.string.acerca_t_osm, R.string.acerca_osm),
        Atribucion(R.string.acerca_t_pnoa, R.string.acerca_pnoa),
        Atribucion(R.string.acerca_t_dgt, R.string.acerca_dgt),
        Atribucion(R.string.acerca_t_protomaps, R.string.acerca_protomaps),
        Atribucion(R.string.acerca_t_noto, R.string.acerca_noto),
        Atribucion(R.string.acerca_t_maplibre, R.string.acerca_maplibre),
        Atribucion(R.string.acerca_t_sherpa, R.string.acerca_sherpa),
        Atribucion(R.string.acerca_t_vosk, R.string.acerca_vosk),
        Atribucion(R.string.acerca_t_espeak, R.string.acerca_espeak),
        Atribucion(R.string.acerca_t_sharvard, R.string.acerca_voz_sharvard),
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

/** Acerca de: cabecera con la mascota, la versión y la licencia, y las atribuciones en filas que se despliegan. */
@Composable
fun AcercaDeScreen(
    version: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(stringResource(R.string.acerca_titulo), onVolver)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                Cabecera(version)
                Text(
                    text = stringResource(R.string.acerca_atribuciones_titulo),
                    style = Cuerpo,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Espacio.m).semantics { heading() },
                )
                Column(
                    modifier = Modifier.testTag(ETIQUETA_ATRIBUCIONES).padding(bottom = Espacio.l),
                    verticalArrangement = Arrangement.spacedBy(Espacio.s),
                ) {
                    ATRIBUCIONES.forEachIndexed { indice, atribucion -> FilaAtribucion(indice, atribucion) }
                }
            }
        }
    }
}

@Composable
private fun Cabecera(version: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Image(
            painter = painterResource(R.drawable.mascota_rally),
            contentDescription = stringResource(R.string.acerca_mascota),
            modifier = Modifier.size(LADO_MASCOTA),
        )
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = Titulo,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.acerca_version, version),
            style = Cuerpo,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ChipIconoWrc(
            icono = Iconos.Informacion,
            texto = stringResource(R.string.acerca_licencia_corta),
            modifier = Modifier.padding(top = Espacio.xs),
        )
        Text(
            text = stringResource(R.string.acerca_licencia_texto),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Espacio.xs),
        )
    }
}

/** Una atribución plegable: el título corto siempre visible y el texto completo al tocarla. */
@Composable
private fun FilaAtribucion(
    indice: Int,
    atribucion: Atribucion,
) {
    var abierta by rememberSaveable { mutableStateOf(false) }
    val titulo = stringResource(atribucion.titulo)
    val accion = stringResource(R.string.acerca_expandir, titulo)
    TarjetaWrc(
        modifier =
            Modifier
                .testTag(ETIQUETA_ATRIBUCION_PREFIJO + indice)
                .semantics { contentDescription = accion },
        relleno = Espacio.m,
        onClick = { abierta = !abierta },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = titulo,
                style = Cuerpo,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (abierta) Iconos.ExpandirMenos else Iconos.ExpandirMas,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(abierta) {
            Text(
                text = stringResource(atribucion.texto),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Espacio.s),
            )
        }
    }
}
