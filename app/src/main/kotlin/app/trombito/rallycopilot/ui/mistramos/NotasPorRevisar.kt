package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Titulo

const val ETIQUETA_NOTAS_REVISAR_TITULO = "detalle-notas-revisar-titulo"

/** Fila de una nota por revisar: `detalle-nota-revisar-0`, `detalle-nota-revisar-1`... */
const val ETIQUETA_NOTA_REVISAR_FILA_PREFIJO = "detalle-nota-revisar-"

private const val METROS_KM = 1000.0
private val ALTO_FILA_NOTA = 56.dp

/**
 * La sección «Notas por revisar» del detalle: las notas que Joan marcó como mal cantadas («esta nota estaba mal»), cada
 * una con su kilómetro y, si se conoce, lo que decía; con la lista vacía no añade nada.
 */
fun LazyListScope.seccionNotasPorRevisar(notas: List<FilaNotaPorRevisar>) {
    if (notas.isEmpty()) return
    item(key = "notas-revisar-titulo") {
        Text(
            text = stringResource(R.string.detalle_notas_revisar_titulo),
            style = Titulo,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.padding(top = Espacio.s).testTag(ETIQUETA_NOTAS_REVISAR_TITULO).semantics { heading() },
        )
    }
    itemsIndexed(notas, key = { i, _ -> "nota-revisar-$i" }) { i, fila -> TarjetaNotaPorRevisar(i, fila) }
}

/** «km 1,2» y, debajo, el texto de la nota («DERECHA 4 LARGA») si se pudo reconstruir. */
@Composable
private fun TarjetaNotaPorRevisar(
    indice: Int,
    fila: FilaNotaPorRevisar,
) {
    val km = FormatoEs.decimal(fila.posicionM / METROS_KM)
    val etiqueta = stringResource(R.string.detalle_nota_revisar_km, km)
    val descripcion =
        fila.texto?.let { stringResource(R.string.detalle_nota_revisar_descripcion_texto, km, it) }
            ?: stringResource(R.string.detalle_nota_revisar_descripcion, km)
    TarjetaWrc(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_NOTA)
                .testTag(ETIQUETA_NOTA_REVISAR_FILA_PREFIJO + indice)
                .clearAndSetSemantics { contentDescription = descripcion },
        relleno = Espacio.m,
    ) {
        Column {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            fila.texto?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
