package app.trombito.rallycopilot.ui.maniobras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.ruta.TipoManiobra

const val ETIQUETA_BANNER = "banner_maniobra"
const val ETIQUETA_BANNER_DISTANCIA = "banner_distancia"
const val ETIQUETA_BANNER_FRASE = "banner_frase"
const val ETIQUETA_BANNER_SIGUIENTE = "banner_siguiente"

private val SESGO = 14.dp
private val TAMANO_ICONO = 84.dp
private val TAMANO_ICONO_SIGUIENTE = 26.dp

/** Colores fijos de cada variante (el banner va sobre el mapa y no cambia con el tema). Todos cumplen AA. */
internal data class PaletaBanner(
    val fondo: Color,
    val borde: Color,
    val distancia: Color,
    val frase: Color,
    val secundario: Color,
)

internal fun paletaBanner(variante: VarianteManiobra): PaletaBanner =
    when (variante) {
        VarianteManiobra.CHEVRON_HUD -> {
            PaletaBanner(
                fondo = Color(0xFF0E0F12),
                borde = Color(0xFFE5AB46),
                distancia = Color(0xFFE5AB46),
                frase = Color(0xFFECEDEF),
                secundario = Color(0xFFBFC3C9),
            )
        }

        VarianteManiobra.PLACA_INCLINADA -> {
            PaletaBanner(
                fondo = Color(0xFFD8232A),
                borde = Color.White,
                distancia = Color.White,
                frase = Color.White,
                secundario = Color.White,
            )
        }

        VarianteManiobra.NOTA_RALLY -> {
            PaletaBanner(
                fondo = Color(0xFF22252A),
                borde = Color(0xFF80858D),
                distancia = Color.White,
                frase = Color(0xFFECEDEF),
                secundario = Color(0xFFBFC3C9),
            )
        }
    }

/** Caja inclinada tipo HUD de retransmisión: paralelogramo con las esquinas superior e inferior desplazadas [sesgo]. */
private class ParalelogramoShape(
    private val sesgo: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val s = with(density) { sesgo.toPx() }.coerceAtMost(size.width / 2)
        return Outline.Generic(
            Path().apply {
                moveTo(s, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - s, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/**
 * Banner superior de la próxima maniobra: icono grande, distancia en negrita cursiva, frase y, si la hay, la
 * maniobra siguiente en pequeño («Luego ↰ izquierda»). [salidaRotonda] pone el número de salida en el icono.
 */
@Composable
@Suppress("LongParameterList") // firma fijada por el diseño: variante, tipo, distancia, frase y siguiente
fun BannerManiobra(
    variante: VarianteManiobra,
    tipo: TipoManiobra,
    distanciaM: Double,
    frase: String,
    siguiente: Pair<TipoManiobra, String>?,
    modifier: Modifier = Modifier,
    salidaRotonda: Int? = null,
) {
    val paleta = paletaBanner(variante)
    val forma = ParalelogramoShape(SESGO)
    Row(
        modifier =
            modifier
                .testTag(ETIQUETA_BANNER)
                .background(paleta.fondo, forma)
                .border(2.dp, paleta.borde, forma)
                .padding(start = SESGO + 10.dp, end = SESGO + 6.dp, top = 8.dp, bottom = 8.dp)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoManiobra(
            tipo = tipo,
            variante = variante,
            tamano = TAMANO_ICONO,
            salidaRotonda = salidaRotonda,
            decorativo = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = formatearDistanciaManiobra(distanciaM),
                modifier = Modifier.testTag(ETIQUETA_BANNER_DISTANCIA),
                color = paleta.distancia,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Black),
                maxLines = 1,
            )
            Text(
                text = frase,
                modifier = Modifier.testTag(ETIQUETA_BANNER_FRASE),
                color = paleta.frase,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (siguiente != null) FilaSiguiente(siguiente, variante, paleta)
        }
    }
}

@Composable
private fun FilaSiguiente(
    siguiente: Pair<TipoManiobra, String>,
    variante: VarianteManiobra,
    paleta: PaletaBanner,
) {
    Row(
        modifier = Modifier.testTag(ETIQUETA_BANNER_SIGUIENTE).padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.maniobra_luego),
            color = paleta.secundario,
            style = MaterialTheme.typography.bodySmall,
        )
        IconoManiobra(
            tipo = siguiente.first,
            variante = variante,
            modifier = Modifier.size(TAMANO_ICONO_SIGUIENTE),
            tamano = TAMANO_ICONO_SIGUIENTE,
            decorativo = true,
        )
        Text(
            text = siguiente.second,
            color = paleta.secundario,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
