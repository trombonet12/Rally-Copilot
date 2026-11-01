package app.trombito.rallycopilot.ui.navegacion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.Espacio

const val ETIQUETA_PARADAS = "nav-paradas"
const val ETIQUETA_PARADA = "nav-parada-"
const val ETIQUETA_PARADA_QUITAR = "nav-parada-quitar-"
const val ETIQUETA_PARADA_SUBIR = "nav-parada-subir-"
const val ETIQUETA_PARADA_BAJAR = "nav-parada-bajar-"
const val ETIQUETA_PARADA_FINAL = "nav-parada-final"
const val ETIQUETA_BANNER_PARADA = "nav-banner-parada"

private const val OPACIDAD_SOBRE_MAPA = 0.94f
private const val ALFA_DESHABILITADO = 0.38f
private val LADO_NUMERO = 28.dp
private val ALTO_FILA_PARADA = 48.dp
private val ALTO_FILA_FINAL = 40.dp
private val ALTO_MAXIMO_VERTICAL = ALTO_FILA_PARADA * 3 + ALTO_FILA_FINAL
private val ALTO_MAXIMO_HORIZONTAL = ALTO_FILA_PARADA * 2
private val FORMA_BANNER = RoundedCornerShape(10.dp)
private val ANCHO_MAXIMO = 520.dp

/**
 * Lista compacta de las paradas de la ruta, numeradas desde [primerNumero] (al guiar, las ya alcanzadas cuentan), y
 * debajo el final. Cada parada se sube, se baja o se quita con su botón: las flechas sustituyen al arrastre, que es
 * frágil en un móvil que va en el coche. Con muchas paradas la lista se desplaza.
 */
@Composable
@Suppress("LongParameterList") // lista con su numeración, orientación y fondo
fun ListaParadas(
    paradas: List<Destino>,
    destino: Destino,
    acciones: AccionesNavegacion,
    modifier: Modifier = Modifier,
    primerNumero: Int = 1,
    horizontal: Boolean = false,
    sobreMapa: Boolean = true,
) {
    val alto = if (horizontal) ALTO_MAXIMO_HORIZONTAL else ALTO_MAXIMO_VERTICAL
    val descripcion = stringResource(R.string.navegacion_ui_paradas)
    val contenido: @Composable () -> Unit = {
        Column(
            modifier =
                Modifier
                    .heightIn(max = alto)
                    .verticalScroll(rememberScrollState()),
        ) {
            paradas.forEachIndexed { indice, parada ->
                FilaParada(
                    indice = indice,
                    numero = primerNumero + indice,
                    parada = parada,
                    ultima = indice == paradas.lastIndex,
                    acciones = acciones,
                )
            }
            FilaFinal(destino)
        }
    }
    if (sobreMapa) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(OPACIDAD_SOBRE_MAPA),
            modifier =
                modifier
                    .widthIn(max = ANCHO_MAXIMO)
                    .fillMaxWidth()
                    .testTag(ETIQUETA_PARADAS)
                    .semantics { contentDescription = descripcion },
        ) { contenido() }
    } else {
        Box(modifier.fillMaxWidth().testTag(ETIQUETA_PARADAS).semantics { contentDescription = descripcion }) {
            contenido()
        }
    }
}

@Composable
private fun FilaParada(
    indice: Int,
    numero: Int,
    parada: Destino,
    ultima: Boolean,
    acciones: AccionesNavegacion,
) {
    val colores = MaterialTheme.colorScheme
    val hablado = stringResource(R.string.navegacion_ui_parada_numero, numero, parada.nombre)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_PARADA)
                .padding(start = Espacio.m)
                .testTag(ETIQUETA_PARADA + indice),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        NumeroParada(numero)
        Text(
            text = parada.nombre,
            style = MaterialTheme.typography.bodyLarge,
            color = colores.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { contentDescription = hablado },
        )
        BotonFila(
            Iconos.FlechaArriba,
            stringResource(R.string.navegacion_ui_parada_subir, numero),
            ETIQUETA_PARADA_SUBIR + indice,
            habilitado = indice > 0,
        ) { acciones.onMoverParada(indice, indice - 1) }
        BotonFila(
            Iconos.FlechaAbajo,
            stringResource(R.string.navegacion_ui_parada_bajar, numero),
            ETIQUETA_PARADA_BAJAR + indice,
            habilitado = !ultima,
        ) { acciones.onMoverParada(indice, indice + 1) }
        BotonFila(
            Iconos.Cerrar,
            stringResource(R.string.navegacion_ui_parada_quitar, numero),
            ETIQUETA_PARADA_QUITAR + indice,
        ) { acciones.onQuitarParada(indice) }
    }
}

@Composable
private fun BotonFila(
    icono: ImageVector,
    descripcion: String,
    etiqueta: String,
    habilitado: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier.size(LADO_TACTIL).testTag(etiqueta),
    ) {
        val color = MaterialTheme.colorScheme.onSurface
        Icon(
            icono,
            contentDescription = descripcion,
            tint = if (habilitado) color else color.copy(alpha = ALFA_DESHABILITADO),
        )
    }
}

/** El final: bandera y nombre del destino, sin controles. */
@Composable
private fun FilaFinal(destino: Destino) {
    val colores = MaterialTheme.colorScheme
    val hablado = stringResource(R.string.navegacion_ui_parada_final, destino.nombre)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA_FINAL)
                .padding(horizontal = Espacio.m)
                .testTag(ETIQUETA_PARADA_FINAL)
                .semantics(mergeDescendants = true) { contentDescription = hablado },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Box(Modifier.size(LADO_NUMERO), contentAlignment = Alignment.Center) {
            Icon(Iconos.Bandera, contentDescription = null, tint = colores.tertiary)
        }
        Text(
            text = destino.nombre,
            style = MaterialTheme.typography.bodyMedium,
            color = colores.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Círculo latón con el número de la parada, como el marcador del mapa. */
@Composable
internal fun NumeroParada(
    numero: Int,
    modifier: Modifier = Modifier,
    lado: Dp = LADO_NUMERO,
) {
    Box(
        modifier =
            modifier
                .size(lado)
                .background(ColoresGuiado.Carbon, CircleShape)
                .border(2.dp, ColoresGuiado.Laton, CircleShape)
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = numero.toString(),
            color = ColoresGuiado.Laton,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            maxLines = 1,
        )
    }
}

/**
 * Banner «Parada 1 de 3 · nombre» del guiado, con el carbón y el latón de la retransmisión. Tocarlo abre o cierra la
 * lista de paradas ([abierta]).
 */
@Composable
@Suppress("LongParameterList") // progreso de la parada (número, total, nombre) y su estado abierto
fun BannerParada(
    numero: Int,
    total: Int,
    nombre: String,
    abierta: Boolean,
    onAlternar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.navegacion_ui_paradas_banner_descripcion, numero, total, nombre)
    val estado = stringResource(if (abierta) R.string.navegacion_ui_paradas_cerrar else R.string.navegacion_ui_paradas)
    Row(
        modifier =
            modifier
                .testTag(ETIQUETA_BANNER_PARADA)
                .background(ColoresGuiado.Carbon, FORMA_BANNER)
                .border(2.dp, ColoresGuiado.Laton, FORMA_BANNER)
                .clickable(role = Role.Button, onClick = onAlternar)
                .heightIn(min = LADO_TACTIL)
                .padding(horizontal = Espacio.m)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    stateDescription = estado
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Icon(Iconos.Lugar, contentDescription = null, tint = ColoresGuiado.Laton, modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(R.string.navegacion_ui_paradas_banner, numero, total),
            color = ColoresGuiado.Laton,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                ),
            maxLines = 1,
        )
        Text(
            text = nombre,
            color = ColoresGuiado.Texto,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            if (abierta) Iconos.ExpandirMas else Iconos.ExpandirMenos,
            contentDescription = null,
            tint = Color.White,
        )
    }
}
