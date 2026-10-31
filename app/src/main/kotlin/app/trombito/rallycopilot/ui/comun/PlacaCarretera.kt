package app.trombito.rallycopilot.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R

/** Etiqueta de prueba de cada placa. */
const val ETIQUETA_PLACA = "placa-carretera"

/** Clase de placa según el `ref` de la vía (color de la señal real de Mallorca). */
enum class TipoPlaca { NARANJA, AMARILLA, AZUL, NEUTRA }

// Colores de la señal real, no del tema.
internal val NaranjaPlaca = Color(0xFFF8931D)
internal val AmarilloPlaca = Color(0xFFFFDD00)
internal val AzulPlaca = Color(0xFF024DA1)
internal val GrisPlaca = Color(0xFF545B63)

private val RegexMallorca = Regex("""^MA-?(\d{1,4})[A-Z]?$""")
private val RegexAutovia = Regex("""^(?:AP|A)-?\d{1,4}[A-Z]?$""")

/**
 * Tipo de placa de un `ref`, sin distinguir mayúsculas ni espacios: Ma- con 1-2 cifras = naranja, con 3-4 = amarilla;
 * AP- y A- = azul; cualquier otro (o sin cifras) = neutra.
 */
internal fun tipoPlaca(ref: String): TipoPlaca {
    val limpio = ref.filterNot { it.isWhitespace() }.uppercase()
    val cifras = RegexMallorca.matchEntire(limpio)?.groupValues?.get(1)
    return when {
        cifras != null -> if (cifras.length <= 2) TipoPlaca.NARANJA else TipoPlaca.AMARILLA
        RegexAutovia.matches(limpio) -> TipoPlaca.AZUL
        else -> TipoPlaca.NEUTRA
    }
}

/** Colores de una placa: relleno, borde y texto. */
internal data class ColoresPlaca(
    val fondo: Color,
    val borde: Color,
    val texto: Color,
)

internal fun TipoPlaca.colores(): ColoresPlaca =
    when (this) {
        TipoPlaca.NARANJA -> ColoresPlaca(NaranjaPlaca, Color.Black, Color.Black)
        TipoPlaca.AMARILLA -> ColoresPlaca(AmarilloPlaca, Color.Black, Color.Black)
        TipoPlaca.AZUL -> ColoresPlaca(AzulPlaca, Color.White, Color.White)
        TipoPlaca.NEUTRA -> ColoresPlaca(GrisPlaca, Color.White, Color.White)
    }

/** Tamaños de la placa: [PEQUENA] para el banner de maniobras, [NORMAL] para el panel de velocidad. */
enum class TamanoPlaca(
    val fuente: TextUnit,
    val relleno: Dp,
    val borde: Dp,
    val minimo: Dp,
) {
    PEQUENA(16.sp, 6.dp, 2.dp, 32.dp),
    NORMAL(24.sp, 10.dp, 3.dp, 48.dp),
    GRANDE(32.sp, 14.dp, 4.dp, 64.dp),
}

/** Placa de carretera con el color de su tipo, esquinas pequeñas y texto en negrita. */
@Composable
fun PlacaCarretera(
    ref: String,
    modifier: Modifier = Modifier,
    tamano: TamanoPlaca = TamanoPlaca.NORMAL,
) {
    val texto = ref.trim()
    val colores = tipoPlaca(texto).colores()
    val forma = RoundedCornerShape(6.dp)
    val descripcion = stringResource(R.string.placa_carretera_descripcion, texto)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .testTag(ETIQUETA_PLACA)
                .clearAndSetSemantics { contentDescription = descripcion }
                .defaultMinSize(minWidth = tamano.minimo, minHeight = tamano.minimo * 0.7f)
                .background(colores.fondo, forma)
                .border(tamano.borde, colores.borde, forma)
                .padding(horizontal = tamano.relleno, vertical = tamano.relleno / 3),
    ) {
        Text(
            text = texto,
            color = colores.texto,
            fontSize = tamano.fuente,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
