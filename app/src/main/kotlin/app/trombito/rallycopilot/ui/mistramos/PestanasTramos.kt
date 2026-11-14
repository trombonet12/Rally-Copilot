package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.Iconos

const val ETIQUETA_PESTANA_PREFIJO = "tramos-pestana-"

/** Pestañas «Viajes» y «Carreteras»: icono y texto corto; la elegida lleva el indicador en latón. */
@Composable
internal fun PestanasTramos(
    actual: PestanaTramos,
    onPestana: (PestanaTramos) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = actual.ordinal,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        PestanaTramos.entries.forEach { pestana ->
            LeadingIconTab(
                selected = pestana == actual,
                onClick = { onPestana(pestana) },
                text = { Text(stringResource(textoPestana(pestana)), maxLines = 1) },
                icon = { Icon(iconoPestana(pestana), contentDescription = null) },
                modifier = Modifier.testTag(ETIQUETA_PESTANA_PREFIJO + pestana.name.lowercase()),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun textoPestana(pestana: PestanaTramos): Int =
    when (pestana) {
        PestanaTramos.VIAJES -> R.string.mistramos_pestana_viajes
        PestanaTramos.CARRETERAS -> R.string.mistramos_pestana_carreteras
    }

private fun iconoPestana(pestana: PestanaTramos): ImageVector =
    when (pestana) {
        PestanaTramos.VIAJES -> Iconos.Bandera
        PestanaTramos.CARRETERAS -> Iconos.Curvas
    }
