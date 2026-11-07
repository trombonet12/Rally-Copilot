package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.comun.esperarPorFotogramas
import app.trombito.rallycopilot.ui.theme.AmbarSobreCarbon
import app.trombito.rallycopilot.ui.theme.Espacio

/** Aviso «Toca otra vez para terminar»: sale tras el primer toque a la ✕ con crono o grabación en marcha. */
const val ETIQUETA_CONFIRMAR_TERMINAR = "mapa-confirmar-terminar"

/** Lo que dura el aviso (y el segundo toque válido) tras el primer toque a la ✕. */
internal const val ESPERA_CONFIRMAR_TERMINAR_MS = 3_000L

/**
 * ¿La ✕ de la hoja necesita un segundo toque? Sí si el cronómetro corre (tramo en curso) o si el modo libre ya ha
 * grabado algo de recorrido: un roce a 90 km/h no debe tirar el tramo. Sin nada en marcha, un toque basta.
 */
internal fun terminarPideConfirmacion(
    control: EstadoNavegacion,
    crono: CronoEnCurso?,
): Boolean {
    val cronoCorriendo = crono != null && crono.enMarcha && crono.resultado == null
    val grabandoLibre = control is EstadoNavegacion.Libre && control.distanciaM > 0.0
    return cronoCorriendo || grabandoLibre
}

/** Estado del segundo toque de la ✕: «armada» durante [ESPERA_CONFIRMAR_TERMINAR_MS] tras el primer toque. */
@Stable
internal class ConfirmacionTerminar {
    var armada by mutableStateOf(false)
        private set

    /** Primer toque (si [requiere]): arma el aviso; segundo toque (o sin [requiere]): ejecuta [accion]. */
    fun pulsar(
        requiere: Boolean,
        accion: () -> Unit,
    ) {
        if (!requiere || armada) {
            armada = false
            accion()
        } else {
            armada = true
        }
    }

    fun desarmar() {
        armada = false
    }
}

/** El estado de la confirmación, que se desarma solo a los 3 s. */
@Composable
internal fun rememberConfirmacionTerminar(): ConfirmacionTerminar {
    val confirmacion = remember { ConfirmacionTerminar() }
    val armada = confirmacion.armada
    LaunchedEffect(armada) {
        if (armada) {
            esperarPorFotogramas(ESPERA_CONFIRMAR_TERMINAR_MS)
            confirmacion.desarmar()
        }
    }
    return confirmacion
}

/** Tarjeta «Toca otra vez para terminar» sobre la hoja; TalkBack la lee al salir. No pinta nada sin armar. */
@Composable
internal fun AvisoToqueOtraVez(
    confirmacion: ConfirmacionTerminar,
    modifier: Modifier = Modifier,
) {
    if (!confirmacion.armada) return
    TarjetaWrc(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(ETIQUETA_CONFIRMAR_TERMINAR)
                .semantics { liveRegion = LiveRegionMode.Polite },
        variante = VarianteTarjeta.MAPA,
        activa = true,
        relleno = Espacio.s,
    ) {
        Text(
            text = stringResource(R.string.navegacion_terminar_otra_vez),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AmbarSobreCarbon,
            modifier = Modifier.padding(horizontal = Espacio.s),
        )
    }
}
