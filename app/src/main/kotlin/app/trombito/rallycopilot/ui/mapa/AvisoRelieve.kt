package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

const val ETIQUETA_AVISO_RELIEVE = "mapa-aviso-relieve"

private const val PORCIENTO = 100L

/** Fracción 0..1 de la descarga, o null si aún no se sabe el total o está esperando red. */
internal fun fraccionDescarga(progreso: ProgresoDescarga): Float? =
    progreso
        .takeIf { !it.esperandoRed && it.bytesTotales > 0 }
        ?.let { it.bytesHechos.coerceIn(0, it.bytesTotales).toFloat() / it.bytesTotales }

/**
 * Aviso discreto de que el relieve y las curvas de nivel se están descargando tras activarlos: una tarjeta de carbón
 * con el texto y una barra de progreso (indeterminada mientras no se sabe el total o espera red). Sin descarga
 * ([progreso] null) no se pinta nada. No es rojo: la barra y el filo son ámbar.
 */
@Composable
internal fun AvisoRelieveDescargando(
    progreso: ProgresoDescarga?,
    modifier: Modifier = Modifier,
) {
    if (progreso == null) return
    val fraccion = fraccionDescarga(progreso)
    val texto =
        stringResource(
            if (progreso.esperandoRed) R.string.complementos_esperando else R.string.mapa_relieve_descargando,
        )
    val estado = fraccion?.let { stringResource(R.string.mapas_progreso_descripcion, (it * PORCIENTO).toInt()) }
    TarjetaWrc(
        modifier = modifier.fillMaxWidth().testTag(ETIQUETA_AVISO_RELIEVE),
        variante = VarianteTarjeta.MAPA,
        relleno = Espacio.s,
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSobreCarbon,
            modifier = Modifier.padding(horizontal = Espacio.s),
        )
        val barra = Modifier.fillMaxWidth().padding(horizontal = Espacio.s, vertical = Espacio.xs)
        if (fraccion != null) {
            LinearProgressIndicator(
                progress = { fraccion },
                modifier = barra.semantics { stateDescription = estado.orEmpty() },
            )
        } else {
            LinearProgressIndicator(modifier = barra)
        }
    }
}
