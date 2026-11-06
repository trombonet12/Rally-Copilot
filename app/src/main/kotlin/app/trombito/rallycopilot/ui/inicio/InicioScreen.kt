package app.trombito.rallycopilot.ui.inicio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.mistramos.MiniaturaTraza
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono
import app.trombito.rallycopilot.ui.theme.Medidas
import app.trombito.rallycopilot.ui.theme.Movimiento
import app.trombito.rallycopilot.ui.theme.RojoAcento
import kotlinx.coroutines.delay

/** Etiqueta de prueba de la línea «Última salida». */
const val ETIQUETA_ULTIMA_SALIDA = "ultima-salida-inicio"

/** Etiqueta de prueba de la frase de bienvenida (aún sin salidas). */
const val ETIQUETA_BIENVENIDA = "bienvenida-inicio"

private val ALTO_CONDUCIR = 120.dp
private val ALTO_TARJETA_MENU = 96.dp
private val ICONO_CONDUCIR = 56.dp
private val ICONO_MENU = 32.dp
private val LADO_MASCOTA = 120.dp
private val LADO_MINIATURA = 48.dp
private val ANCHO_FRANJA = 96.dp
private const val RETARDO_ESCALON_MS = 70L
private const val DESPLAZAMIENTO_ENTRADA_DP = 24f
private const val ESCALONES_MAX = 6

/**
 * Pantalla de inicio sobre el fondo WRC: la mascota y el nombre, CONDUCIR como tarjeta protagonista con la última
 * salida debajo y el resto del menú en una rejilla. En apaisado, la mascota a la izquierda y el menú a la derecha.
 * Los elementos entran uno tras otro (fundido y subida corta).
 */
@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    estado: InicioUiState = InicioUiState(),
    onEntrada: (EntradaInicio) -> Unit = {},
) {
    FondoWrc(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (maxWidth > maxHeight) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Espacio.xl, vertical = Espacio.m),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Aparece(0) { CabeceraInicio() }
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Espacio.m),
                    ) { MenuInicio(estado, onEntrada) }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Espacio.xl, vertical = Espacio.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Espacio.l),
                ) {
                    Aparece(0) { CabeceraInicio() }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Espacio.m),
                    ) { MenuInicio(estado, onEntrada) }
                }
            }
        }
    }
}

/** Entrada escalonada: aparece con fundido y una subida corta, [indice] × 70 ms después de componerse. */
@Composable
private fun Aparece(
    indice: Int,
    contenido: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(indice.coerceAtMost(ESCALONES_MAX) * RETARDO_ESCALON_MS)
        visible = true
    }
    val avance by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(Movimiento.MEDIA),
        label = "entrada-inicio",
    )
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = avance
            translationY = (1f - avance) * DESPLAZAMIENTO_ENTRADA_DP * density
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { contenido() }
}

@Composable
private fun MenuInicio(
    estado: InicioUiState,
    onEntrada: (EntradaInicio) -> Unit,
) {
    Aparece(1) {
        Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            TarjetaConducir { onEntrada(EntradaInicio.CONDUCIR) }
            UltimaSalidaInicio(estado.ultimaSalida)
        }
    }
    Aparece(2) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
            EntradaInicio.entries
                .filter { it != EntradaInicio.CONDUCIR }
                .forEach { TarjetaMenu(it, Modifier.weight(1f)) { onEntrada(it) } }
        }
    }
}

/** La mascota, el nombre en cursiva, una franja roja de acento y el lema. */
@Composable
private fun ColumnScope.CabeceraInicio() {
    Image(
        painter = painterResource(R.drawable.mascota_rally),
        contentDescription = stringResource(R.string.inicio_mascota),
        modifier = Modifier.size(LADO_MASCOTA),
    )
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = MaterialTheme.typography.headlineMedium,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
    )
    Box(
        modifier =
            Modifier
                .padding(vertical = Espacio.s)
                .width(ANCHO_FRANJA)
                .height(Medidas.barraAcento)
                .background(RojoAcento),
    )
    Text(
        text = stringResource(R.string.inicio_lema),
        style = Ayuda,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** CONDUCIR: tarjeta de 120 dp con el icono de navegación grande, en ámbar y cursiva. */
@Composable
private fun TarjetaConducir(onClick: () -> Unit) {
    TarjetaWrc(
        modifier = Modifier.fillMaxWidth().height(ALTO_CONDUCIR).testTag("inicio-conducir"),
        activa = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ALTO_CONDUCIR - Espacio.xxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacio.l),
        ) {
            Icon(
                imageVector = Iconos.Rumbo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICONO_CONDUCIR),
            )
            Text(
                text = stringResource(EntradaInicio.CONDUCIR.titulo).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Iconos.Siguiente,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Última salida: tramo · tiempo · mejor tiempo» con la silueta del tramo; sin salidas, una frase de bienvenida. */
@Composable
private fun UltimaSalidaInicio(salida: UltimaSalida?) {
    if (salida == null) {
        Text(
            text = stringResource(R.string.inicio_bienvenida),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.s).testTag(ETIQUETA_BIENVENIDA),
        )
        return
    }
    val texto =
        stringResource(
            R.string.inicio_ultima_salida,
            salida.nombre,
            FormatoEs.tiempo(salida.tiempoMs),
            FormatoEs.tiempo(salida.mejorMs),
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacio.xs).testTag(ETIQUETA_ULTIMA_SALIDA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        MiniaturaTraza(salida.traza, LADO_MINIATURA)
        Text(
            text = texto,
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun EntradaInicio.icono(): ImageVector =
    when (this) {
        EntradaInicio.CONDUCIR -> Iconos.Rumbo
        EntradaInicio.MIS_TRAMOS -> Iconos.Bandera
        EntradaInicio.PERFIL -> Iconos.Piloto
        EntradaInicio.AJUSTES -> Iconos.Deslizadores
    }

/** Tarjeta de 96 dp del menú: icono de 32 dp en ámbar y etiqueta de 14 sp debajo. */
@Composable
private fun TarjetaMenu(
    entrada: EntradaInicio,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TarjetaWrc(
        modifier = modifier.height(ALTO_TARJETA_MENU).testTag("inicio-${entrada.name.lowercase()}"),
        relleno = Espacio.s,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(ALTO_TARJETA_MENU - Espacio.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = entrada.icono(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICONO_MENU),
            )
            Text(
                text = stringResource(entrada.titulo),
                style = EtiquetaIcono.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = Espacio.xs),
            )
        }
    }
}
