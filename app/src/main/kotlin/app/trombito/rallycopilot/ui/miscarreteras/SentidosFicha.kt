package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.FichaCarretera
import app.trombito.rallycopilot.motor.analisis.SentidoCarretera
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.CifraPequena
import app.trombito.rallycopilot.ui.theme.Espacio
import kotlin.math.roundToInt

/** Prefijo de la etiqueta de prueba de cada columna: `ficha-sentido-<CRECIENTE|DECRECIENTE|DESCONOCIDO>`. */
const val ETIQUETA_FICHA_SENTIDO_PREFIJO = "ficha-sentido-"
const val ETIQUETA_FICHA_SENTIDOS = "ficha-sentidos"

/**
 * El ritmo en cada sentido, en dos columnas («Hacia Sóller» / «Hacia Andratx»): la velocidad media en marcha y en
 * cuántos viajes se fue así. Si ningún viaje se pudo orientar, una sola línea con el ritmo de la carretera entera.
 */
@Composable
internal fun SentidosFicha(
    ficha: FichaCarretera,
    destinos: Map<SentidoVia, String>,
    modifier: Modifier = Modifier,
) {
    val orientada = ficha.creciente.viajes > 0 || ficha.decreciente.viajes > 0 || ficha.sinSentido.viajes == 0
    TarjetaWrc(modifier.testTag(ETIQUETA_FICHA_SENTIDOS)) {
        Text(
            stringResource(R.string.ficha_sentidos_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (orientada) {
            Row(
                Modifier.padding(top = Espacio.s),
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                val columna = Modifier.weight(1f)
                ColumnaSentido(SentidoVia.CRECIENTE, ficha.creciente, destinos[SentidoVia.CRECIENTE], columna)
                ColumnaSentido(SentidoVia.DECRECIENTE, ficha.decreciente, destinos[SentidoVia.DECRECIENTE], columna)
            }
        } else {
            ColumnaSentido(SentidoVia.DESCONOCIDO, ficha.sinSentido, null, Modifier.padding(top = Espacio.s))
        }
    }
}

@Composable
private fun ColumnaSentido(
    sentido: SentidoVia,
    datos: SentidoCarretera,
    destino: String?,
    modifier: Modifier = Modifier,
) {
    val etiqueta = etiquetaSentido(sentido, destino)
    val kmh = datos.velMediaKmh?.roundToInt()
    val ritmo = kmh?.let { stringResource(R.string.carreteras_ritmo, it) }
    val viajes = pluralStringResource(R.plurals.carreteras_viajes, datos.viajes, datos.viajes)
    val resumen = if (ritmo != null) "$ritmo, $viajes" else stringResource(R.string.ficha_sentido_sin_datos)
    val descripcion = stringResource(R.string.ficha_sentido_descripcion, etiqueta, resumen)
    Column(
        modifier
            .testTag(ETIQUETA_FICHA_SENTIDO_PREFIJO + sentido.name)
            .clearAndSetSemantics { contentDescription = descripcion },
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Text(etiqueta, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ritmo != null) {
            Text(ritmo, style = CifraPequena, color = MaterialTheme.colorScheme.onSurface)
            Text(viajes, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                stringResource(R.string.ficha_sentido_sin_datos),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
