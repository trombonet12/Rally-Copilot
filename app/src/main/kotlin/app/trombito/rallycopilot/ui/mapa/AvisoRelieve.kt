package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.ui.comun.BotonRedondoMapa
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

const val ETIQUETA_AVISO_RELIEVE = "mapa-aviso-relieve"
const val ETIQUETA_AVISO_RELIEVE_CERRAR = "mapa-aviso-relieve-cerrar"

private const val PORCIENTO = 100L
private val ANCHO_MAXIMO_AVISO = 320.dp

/** Fracción 0..1 de la descarga, o null si aún no se sabe el total o está esperando red. */
internal fun fraccionDescarga(progreso: ProgresoDescarga): Float? =
    progreso
        .takeIf { !it.esperandoRed && it.bytesTotales > 0 }
        ?.let { it.bytesHechos.coerceIn(0, it.bytesTotales).toFloat() / it.bytesTotales }

/**
 * Aviso discreto de que el relieve y las curvas de nivel se están descargando tras activarlos: una píldora de carbón
 * con el texto, una barra de progreso (indeterminada mientras no se sabe el total) y una ✕ para cerrarla. Solo se
 * pinta con la descarga en marcha: una que espera red o WiFi no bloquea nada (los datos del APK ya funcionan) y se
 * ve en Mapas offline, no en el mapa. Sin descarga ([progreso] null) no se pinta nada. No es rojo: filo ámbar.
 */
@Composable
internal fun AvisoRelieveDescargando(
    progreso: ProgresoDescarga?,
    modifier: Modifier = Modifier,
) {
    if (progreso == null || progreso.esperandoRed) return
    var cerrado by rememberSaveable { mutableStateOf(false) }
    if (cerrado) return
    val fraccion = fraccionDescarga(progreso)
    val estado = fraccion?.let { stringResource(R.string.mapas_progreso_descripcion, (it * PORCIENTO).toInt()) }
    TarjetaWrc(
        modifier = modifier.widthIn(max = ANCHO_MAXIMO_AVISO).testTag(ETIQUETA_AVISO_RELIEVE),
        variante = VarianteTarjeta.MAPA,
        relleno = Espacio.s,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mapa_relieve_descargando),
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
            BotonRedondoMapa(
                descripcion = stringResource(R.string.mapa_aviso_cerrar),
                onClick = { cerrado = true },
                modifier = Modifier.testTag(ETIQUETA_AVISO_RELIEVE_CERRAR),
            ) { Icon(Iconos.Cerrar, contentDescription = null) }
        }
    }
}
