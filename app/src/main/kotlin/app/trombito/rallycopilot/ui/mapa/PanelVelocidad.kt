package app.trombito.rallycopilot.ui.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** Etiquetas de prueba del panel. */
const val ETIQUETA_VELOCIDAD = "panel-velocidad"
const val ETIQUETA_LIMITE = "panel-limite"
const val ETIQUETA_VIA = "panel-via"

private val FondoSenal = Color.White
private val TextoSenal = Color.Black

/**
 * Velocidad actual (siempre visible; «—» sin dato), señal de límite (solo si [limiteKmh] no es null) y la placa de la
 * vía. En vertical va en una fila bajo el mapa, con [botonesIzquierda] y [botonesDerecha] (columnas de botones de
 * icono) a los lados de la velocidad; en horizontal ([enColumna]) en una columna al lado, con todos los botones en
 * una fila al pie.
 */
@Composable
@Suppress("LongParameterList") // datos de conducción + los dos grupos de botones del panel
fun PanelVelocidad(
    velocidadKmh: Int?,
    limiteKmh: Int?,
    via: ViaActual?,
    enColumna: Boolean,
    modifier: Modifier = Modifier,
    botonesIzquierda: List<@Composable () -> Unit> = emptyList(),
    botonesDerecha: List<@Composable () -> Unit> = emptyList(),
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.testTag("panel"),
    ) {
        val contenido: @Composable () -> Unit = {
            Velocidad(velocidadKmh)
            if (limiteKmh != null) SenalLimite(limiteKmh)
        }
        val rotulo: @Composable () -> Unit = {
            RotuloVia(refsDePlaca(via?.ref), via?.nombre?.trim()?.takeIf { it.isNotEmpty() }, enColumna)
        }
        if (enColumna) {
            Column(
                modifier = Modifier.padding(horizontal = Espacio.s, vertical = Espacio.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
            ) {
                contenido()
                rotulo()
                // Hasta cuatro botones de 48 dp en una sola fila: no se apila otra fila bajo la velocidad.
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                    (botonesIzquierda + botonesDerecha).forEach { it() }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColumnaBotones(botonesIzquierda, Alignment.Start)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Espacio.m, Alignment.CenterHorizontally),
                    ) { contenido() }
                    rotulo()
                }
                ColumnaBotones(botonesDerecha, Alignment.End)
            }
        }
    }
}

/** Columna de botones de ancho fijo (cabe la bandera de 56 dp): la velocidad queda centrada con o sin botones. */
@Composable
private fun ColumnaBotones(
    botones: List<@Composable () -> Unit>,
    alineacion: Alignment.Horizontal,
) {
    Column(
        modifier = Modifier.width(ANCHO_COLUMNA_BOTONES),
        horizontalAlignment = alineacion,
        verticalArrangement = Arrangement.spacedBy(Espacio.s, Alignment.CenterVertically),
    ) { botones.forEach { it() } }
}

private val ANCHO_COLUMNA_BOTONES = 56.dp

/** Los `ref` de OSM separados por «;», sin vacíos ni repetidos y como mucho [MAX_PLACAS]. */
internal fun refsDePlaca(ref: String?): List<String> =
    ref
        .orEmpty()
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_PLACAS)

/**
 * Bajo la velocidad: una placa por cada `ref` y, si hay, el nombre en texto pequeño. En vertical el nombre va junto a
 * las placas y se recorta si no cabe; en horizontal (columna estrecha) va debajo. Sin `ref`, solo el nombre.
 */
@Composable
private fun RotuloVia(
    refs: List<String>,
    nombre: String?,
    enColumna: Boolean,
) {
    if (refs.isEmpty() && nombre == null) return
    val textoNombre: @Composable (Modifier, Int) -> Unit = { modificador, lineas ->
        Text(
            text = nombre.orEmpty(),
            style =
                if (refs.isEmpty()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = lineas,
            overflow = TextOverflow.Ellipsis,
            modifier = modificador.testTag(ETIQUETA_VIA),
        )
    }
    val placas: @Composable () -> Unit = {
        if (enColumna) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) { refs.forEach { PlacaCarretera(it) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) { refs.forEach { PlacaCarretera(it) } }
        }
    }
    when {
        refs.isEmpty() -> {
            textoNombre(Modifier, 2)
        }

        nombre == null -> {
            placas()
        }

        enColumna -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Espacio.xs),
            ) {
                placas()
                textoNombre(Modifier, 2)
            }
        }

        else -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Espacio.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                placas()
                textoNombre(Modifier.weight(1f, fill = false), 1)
            }
        }
    }
}

private const val MAX_PLACAS = 2

@Composable
private fun Velocidad(velocidadKmh: Int?) {
    val descripcion =
        if (velocidadKmh == null) {
            stringResource(R.string.panel_velocidad_sin_dato)
        } else {
            stringResource(R.string.panel_velocidad_descripcion, velocidadKmh)
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .testTag(ETIQUETA_VELOCIDAD)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Text(
            text = velocidadKmh?.toString() ?: stringResource(R.string.panel_velocidad_vacia),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 92.sp),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.panel_unidad),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Señal circular de límite: aro rojo, fondo blanco y cifra negra (colores de la señal real, no del tema). */
@Composable
private fun SenalLimite(limiteKmh: Int) {
    val descripcion = stringResource(R.string.panel_limite_descripcion, limiteKmh)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .testTag(ETIQUETA_LIMITE)
                .size(92.dp)
                .semantics { contentDescription = descripcion }
                .background(FondoSenal, CircleShape)
                .border(BORDE_SENAL, LocalColoresRally.current.franja, CircleShape),
    ) {
        Text(
            text = limiteKmh.toString(),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = TextoSenal,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private val BORDE_SENAL = 8.dp
