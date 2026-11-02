package app.trombito.rallycopilot.ui.inicio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/**
 * Pantalla de inicio tipo menú de juego. Las entradas de hitos pendientes salen desactivadas. En vertical la mascota y
 * el título van arriba y el menú debajo; en apaisado, la mascota a la izquierda y el menú a la derecha, para que
 * quepan las cuatro entradas.
 */
@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    onEntrada: (EntradaInicio) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = Espacio.xl, vertical = Espacio.m),
                horizontalArrangement = Arrangement.spacedBy(Espacio.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { CabeceraInicio() }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Espacio.s),
                ) { MenuInicio(onEntrada) }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Espacio.xl, vertical = Espacio.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CabeceraInicio()
                Column(
                    modifier = Modifier.padding(top = Espacio.xxl).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Espacio.s),
                ) { MenuInicio(onEntrada) }
            }
        }
    }
}

@Composable
private fun MenuInicio(onEntrada: (EntradaInicio) -> Unit) {
    EntradaInicio.entries.forEach { FilaMenu(it) { onEntrada(it) } }
}

/** La mascota, el nombre en cursiva, la franja roja y el lema. */
@Composable
private fun ColumnScope.CabeceraInicio() {
    Image(
        painter = painterResource(R.drawable.mascota_rally),
        contentDescription = stringResource(R.string.inicio_mascota),
        modifier = Modifier.size(160.dp),
    )
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Box(
        modifier =
            Modifier
                .padding(vertical = Espacio.s)
                .width(96.dp)
                .height(4.dp)
                .background(LocalColoresRally.current.franja),
    )
    Text(
        text = stringResource(R.string.inicio_lema),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FilaMenu(
    entrada: EntradaInicio,
    onClick: () -> Unit,
) {
    val modificador = Modifier.fillMaxWidth().testTag("inicio-${entrada.name.lowercase()}")
    if (entrada.activa) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = modificador,
        ) { ContenidoFila(entrada, proximamente = false) }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = modificador.semantics { disabled() },
        ) { ContenidoFila(entrada, proximamente = true) }
    }
}

private fun EntradaInicio.icono(): ImageVector =
    when (this) {
        EntradaInicio.CONDUCIR -> Iconos.Rumbo
        EntradaInicio.MIS_TRAMOS -> Iconos.Bandera
        EntradaInicio.PERFIL -> Iconos.Piloto
        EntradaInicio.AJUSTES -> Iconos.Deslizadores
    }

/** Una entrada del menú: franja roja de la marca, icono latón, título en cursiva y flecha, como un menú de juego. */
@Composable
private fun ContenidoFila(
    entrada: EntradaInicio,
    proximamente: Boolean,
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min).heightIn(min = ALTO_FILA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(LocalColoresRally.current.franja))
        Icon(
            imageVector = entrada.icono(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Espacio.l).size(28.dp),
        )
        Text(
            text = stringResource(entrada.titulo).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (proximamente) {
            Text(
                text = stringResource(R.string.inicio_proximamente),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Espacio.l),
            )
        } else {
            Icon(
                imageVector = Iconos.Siguiente,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Espacio.m),
            )
        }
    }
}

private val ALTO_FILA = 72.dp
