package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.navegacion.coordenadasCortas
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios

const val ETIQUETA_AJUSTES_CASA = "ajustes-casa"
const val ETIQUETA_AJUSTES_CASA_BORRAR = "ajustes-casa-borrar"

private val TITULO = Cuerpo.copy(fontWeight = FontWeight.SemiBold)

/**
 * La casa de «Ir a casa» y «volver a casa»: sus coordenadas con «Borrar»; sin casa explica cómo fijarla (mantener
 * pulsado el mapa y tocar «Casa»).
 */
@Composable
internal fun FilaCasa(
    casa: PuntoGeo?,
    onBorrar: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = Radios.formaTarjeta,
        modifier = Modifier.fillMaxWidth().testTag(ETIQUETA_AJUSTES_CASA),
    ) {
        Row(
            modifier = Modifier.heightIn(min = Medidas.accionPrincipal).padding(start = Espacio.l, end = Espacio.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Icon(Iconos.Casa, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(vertical = Espacio.m)) {
                Text(
                    stringResource(R.string.ajustes_casa_titulo),
                    style = TITULO,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = casa?.let(::coordenadasCortas) ?: stringResource(R.string.ajustes_casa_sin_fijar),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (casa != null) {
                TextButton(
                    onClick = onBorrar,
                    modifier = Modifier.heightIn(min = Medidas.zonaTactil).testTag(ETIQUETA_AJUSTES_CASA_BORRAR),
                ) { Text(stringResource(R.string.ajustes_casa_borrar)) }
            }
        }
    }
}
