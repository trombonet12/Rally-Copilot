package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.CarbonTranslucido
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

/** Etiqueta de prueba de la banda «Sin señal GPS». */
const val ETIQUETA_SIN_GPS = "mapa-sin-gps"

/**
 * Si se ha perdido el GPS. Lo pone la pantalla del mapa y lo leen las bandas de arriba (la de libre y la del guiado)
 * sin pasarlo por todas las capas.
 */
val LocalSinSenalGps = staticCompositionLocalOf { false }

/**
 * Banda ámbar bajo la parte superior del mapa: «Sin señal GPS» con su icono. Aparece al perderse el GPS y es una
 * región viva educada: TalkBack lo dice una vez, sin interrumpir al copiloto. No pinta nada con señal.
 */
@Composable
fun BandaSinGps(
    modifier: Modifier = Modifier,
    sinSenal: Boolean = LocalSinSenalGps.current,
) {
    if (!sinSenal) return
    TarjetaWrc(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_SIN_GPS).semantics { liveRegion = LiveRegionMode.Polite },
        variante = VarianteTarjeta.MAPA,
        activa = true,
        relleno = Espacio.s,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Espacio.s),
            horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sin_gps),
                contentDescription = null,
                tint = AmbarSobreCarbon,
                modifier = Modifier.size(Medidas.iconoLista),
            )
            Text(
                text = stringResource(R.string.mapa_sin_gps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AmbarSobreCarbon,
            )
        }
    }
}
