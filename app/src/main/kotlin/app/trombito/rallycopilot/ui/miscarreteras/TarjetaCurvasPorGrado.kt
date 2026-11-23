package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.RojoAcento

const val ETIQUETA_CARRETERAS_CURVAS = "carreteras-curvas"
const val ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO = "carreteras-curvas-"

private val ALTO_BARRA = 12.dp
private val ANCHO_ETIQUETA_CURVAS = 84.dp
private val ANCHO_CUENTA_CURVAS = 40.dp
private const val FRACCION_MIN_BARRA = 0.03f
private const val GRADOS = 6

/** Una barra de la tarjeta de curvas: texto del grado, cuántas curvas y qué fracción del máximo es. */
private class FilaCurvas(
    val etiqueta: String,
    val clave: String,
    val cuenta: Int,
)

/**
 * Curvas por grado en barras, de la más cerrada (grado 1) a la más suave (grado 6) y, al final, escuadras y horquillas.
 * Dice en una línea que 1 es la más cerrada. [etiqueta] es la etiqueta de prueba de la tarjeta; las filas son
 * `[prefijoFila]g1`… `[prefijoFila]horquilla`.
 */
@Composable
fun TarjetaCurvasPorGrado(
    curvas: CurvasPorGrado,
    modifier: Modifier = Modifier,
    etiqueta: String = ETIQUETA_CARRETERAS_CURVAS,
    prefijoFila: String = ETIQUETA_CARRETERAS_CURVAS_FILA_PREFIJO,
) {
    val filas =
        (1..GRADOS).map { FilaCurvas(stringResource(R.string.carreteras_grado, it), "g$it", curvas.deGrado(it)) } +
            FilaCurvas(stringResource(R.string.carreteras_escuadra), "escuadra", curvas.escuadras) +
            FilaCurvas(stringResource(R.string.carreteras_horquilla), "horquilla", curvas.horquillas)
    val maximo = filas.maxOf { it.cuenta }.coerceAtLeast(1)
    TarjetaWrc(modifier.testTag(etiqueta)) {
        Text(
            stringResource(R.string.carreteras_curvas_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pluralStringResource(R.plurals.carreteras_curvas_total, curvas.total, curvas.total),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.carreteras_curvas_escala),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Espacio.s),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            filas.forEach { FilaBarra(it, maximo, prefijoFila) }
        }
    }
}

@Composable
private fun FilaBarra(
    fila: FilaCurvas,
    maximo: Int,
    prefijoFila: String,
) {
    val descripcion =
        stringResource(
            R.string.carreteras_curvas_fila,
            fila.etiqueta,
            pluralStringResource(R.plurals.carreteras_curvas_cuenta, fila.cuenta, fila.cuenta),
        )
    val fraccion = if (fila.cuenta == 0) 0f else (fila.cuenta.toFloat() / maximo).coerceAtLeast(FRACCION_MIN_BARRA)
    val color = if (fila.clave == "horquilla") RojoAcento else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(prefijoFila + fila.clave)
            .clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Text(
            fila.etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(ANCHO_ETIQUETA_CURVAS),
            maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .height(ALTO_BARRA)
                .clip(RoundedCornerShape(ALTO_BARRA / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion)
                    .clip(RoundedCornerShape(ALTO_BARRA / 2))
                    .background(color),
            )
        }
        Text(
            fila.cuenta.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(ANCHO_CUENTA_CURVAS),
            maxLines = 1,
        )
    }
}
