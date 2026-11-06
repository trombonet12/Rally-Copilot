package app.trombito.rallycopilot.ui.tramos

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.maniobras.ParalelogramoShape
import app.trombito.rallycopilot.ui.navegacion.guiado.ColoresGuiado
import app.trombito.rallycopilot.ui.theme.LocalColoresRally

/** El conjunto de la cuenta atrás: la superposición que cubre la pantalla y que se cancela al tocar fuera. */
const val ETIQUETA_CUENTA_ATRAS = "cuenta-atras"

/** Fondo tocable que cancela la cuenta atrás. */
const val ETIQUETA_CUENTA_ATRAS_FONDO = "cuenta-atras-fondo"

/** Cada luz: `cuenta-atras-luz-1` … `cuenta-atras-luz-5`. */
const val ETIQUETA_CUENTA_ATRAS_LUZ_PREFIJO = "cuenta-atras-luz-"

/** El «¡YA!» final. */
const val ETIQUETA_CUENTA_ATRAS_YA = "cuenta-atras-ya"

/** El número que se está contando. */
const val ETIQUETA_CUENTA_ATRAS_NUMERO = "cuenta-atras-numero"

/** Cuántas luces tiene la cuenta atrás (y segundos hasta el «ya»). */
const val LUCES_CUENTA_ATRAS = 5

/** Tiempo entre una luz y la siguiente. */
const val PASO_CUENTA_ATRAS_MS = 1_000L

/** Cuánto se queda el «¡YA!» en verde antes de retirarse la superposición. */
const val DURACION_YA_MS = 900L

private const val OPACIDAD_FONDO = 0.55f
private const val OPACIDAD_PANEL = 0.96f
private const val OPACIDAD_BORDE_PANEL = 0.7f
private const val OPACIDAD_BORDE_LUZ_ENCENDIDA = 0.35f
private const val OPACIDAD_BORDE_LUZ_APAGADA = 0.15f
private const val ESCALA_APAGADA = 0.8f
private const val DURACION_COLOR_MS = 150
private val ROJO_LUZ = Color(0xFFE02A32)
private val ROJO_APAGADO = Color(0xFF3B1517)
private val VERDE_LUZ = Color(0xFF3DDC84)
private val LADO_LUZ = 48.dp

/**
 * Cuenta atrás de la salida manual, centrada sobre toda la pantalla: cinco luces rojas que se encienden una por
 * segundo (cinco, cuatro, tres, dos, uno) y, un segundo después de la última, pasan todas a verde con «¡YA!». Cada
 * número se pasa a [onDecir] (el copiloto lo dice) y el «ya» llama además a [onYa], que da la salida. Tocar fuera del
 * panel la cancela sin dar la salida. [onCerrar] se llama al cancelar y, tras [DURACION_YA_MS], al acabar: quien la
 * muestra la retira. TalkBack recibe cada paso como región viva; el fondo es un botón «Cancelar la cuenta atrás».
 */
@Composable
fun CuentaAtras(
    onDecir: (String) -> Unit,
    onYa: () -> Unit,
    onCerrar: () -> Unit,
) {
    val numeros = LocalContext.current.resources.getStringArray(R.array.cuenta_atras_voz)
    val palabraYa = stringResource(R.string.cuenta_atras_voz_ya)
    val decir by rememberUpdatedState(onDecir)
    val salir by rememberUpdatedState(onYa)
    val cerrar by rememberUpdatedState(onCerrar)
    var encendidas by remember { mutableIntStateOf(1) }
    var ya by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        for (paso in 1..LUCES_CUENTA_ATRAS) {
            encendidas = paso
            decir(numeros[paso - 1])
            esperarEnPrincipal(PASO_CUENTA_ATRAS_MS)
        }
        ya = true
        decir(palabraYa)
        salir()
        esperarEnPrincipal(DURACION_YA_MS)
        cerrar()
    }
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Escena(encendidas, ya, onCerrar)
    }
}

@Composable
private fun Escena(
    encendidas: Int,
    ya: Boolean,
    onCancelar: () -> Unit,
) {
    val cancelar = stringResource(R.string.cuenta_atras_cancelar)
    Box(Modifier.fillMaxSize().testTag(ETIQUETA_CUENTA_ATRAS), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(OPACIDAD_FONDO))
                .testTag(ETIQUETA_CUENTA_ATRAS_FONDO)
                .clickable(onClickLabel = cancelar, role = Role.Button, onClick = onCancelar)
                .semantics { contentDescription = cancelar },
        )
        Panel(encendidas, ya)
    }
}

@Composable
private fun Panel(
    encendidas: Int,
    ya: Boolean,
) {
    val recursos = LocalContext.current.resources
    val descripcion =
        if (ya) {
            recursos.getString(R.string.cuenta_atras_ya_descripcion)
        } else {
            recursos.getString(R.string.cuenta_atras_descripcion, LUCES_CUENTA_ATRAS - encendidas + 1)
        }
    val forma = ParalelogramoShape(10.dp)
    Column(
        modifier =
            Modifier
                .background(ColoresGuiado.Carbon.copy(alpha = OPACIDAD_PANEL), forma)
                .border(2.dp, ColoresGuiado.Laton.copy(alpha = OPACIDAD_BORDE_PANEL), forma)
                // El panel se traga los toques: solo el fondo cancela.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = descripcion
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (luz in 1..LUCES_CUENTA_ATRAS) Luz(luz, luz <= encendidas, ya)
        }
        Mensaje(encendidas, ya)
    }
}

@Composable
private fun Luz(
    numero: Int,
    encendida: Boolean,
    ya: Boolean,
) {
    val objetivo =
        when {
            ya -> VERDE_LUZ
            encendida -> ROJO_LUZ
            else -> ROJO_APAGADO
        }
    val color by animateColorAsState(objetivo, tween(DURACION_COLOR_MS), label = "luz-color")
    val escala by animateFloatAsState(
        if (encendida) 1f else ESCALA_APAGADA,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "luz-escala",
    )
    val estado =
        when {
            ya -> stringResource(R.string.cuenta_atras_luces_verdes)
            encendida -> stringResource(R.string.cuenta_atras_luz_encendida, numero)
            else -> stringResource(R.string.cuenta_atras_luz_apagada, numero)
        }
    val borde = if (encendida) OPACIDAD_BORDE_LUZ_ENCENDIDA else OPACIDAD_BORDE_LUZ_APAGADA
    Box(
        Modifier
            .size(LADO_LUZ)
            .graphicsLayer {
                scaleX = escala
                scaleY = escala
            }.background(color, CircleShape)
            .border(2.dp, Color.White.copy(alpha = borde), CircleShape)
            .testTag(ETIQUETA_CUENTA_ATRAS_LUZ_PREFIJO + numero)
            .clearAndSetSemantics { contentDescription = estado },
    )
}

@Composable
private fun Mensaje(
    encendidas: Int,
    ya: Boolean,
) {
    val escalaYa by animateFloatAsState(
        if (ya) 1f else ESCALA_APAGADA,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ya-escala",
    )
    val estilo = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
    if (ya) {
        Text(
            text = stringResource(R.string.cuenta_atras_ya),
            style = estilo,
            color = LocalColoresRally.current.deltaMejor,
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = escalaYa
                        scaleY = escalaYa
                    }.testTag(ETIQUETA_CUENTA_ATRAS_YA),
        )
    } else {
        Text(
            text = (LUCES_CUENTA_ATRAS - encendidas + 1).toString(),
            style = estilo.copy(fontFeatureSettings = "tnum"),
            color = ColoresGuiado.Texto,
            modifier = Modifier.testTag(ETIQUETA_CUENTA_ATRAS_NUMERO),
        )
    }
}
