package app.trombito.rallycopilot.ui.maniobras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
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
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TamanoPlaca
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteTarjeta
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.CifraMedia
import app.trombito.rallycopilot.ui.theme.LimitarEscalaHud
import app.trombito.rallycopilot.ui.theme.TextoSecundarioSobreCarbon
import app.trombito.rallycopilot.ui.theme.TextoSobreCarbon

const val ETIQUETA_BANNER = "banner_maniobra"
const val ETIQUETA_BANNER_DISTANCIA = "banner_distancia"
const val ETIQUETA_BANNER_FRASE = "banner_frase"
const val ETIQUETA_BANNER_SIGUIENTE = "banner_siguiente"

private val RADIO_BANNER = 16.dp
private val RELLENO_BANNER = 12.dp
private val RELLENO_COMPACTO = 8.dp
private val TAMANO_ICONO = 56.dp
private val TAMANO_ICONO_COMPACTO = 36.dp
private val TAMANO_ICONO_SIGUIENTE = 28.dp
private val ALTO_FICHA_SIGUIENTE = 56.dp
private val RADIO_FICHA_SIGUIENTE = 10.dp
private val TEXTO_MINIMO_SIGUIENTE = 12.sp
private val TEXTO_MAXIMO_SIGUIENTE = 14.sp

/** Estilo único de los iconos del banner (flecha ámbar de trazo grueso): el estilo elegido en Ajustes se ignora. */
private val VARIANTE_BANNER = VarianteManiobra.CHEVRON_HUD

/** Caja inclinada tipo HUD de retransmisión: paralelogramo con las esquinas superior e inferior desplazadas [sesgo]. */
internal class ParalelogramoShape(
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
 * Banner superior de la próxima maniobra, variante única: tarjeta de carbón translúcido a todo el ancho con borde
 * ámbar, barra roja de 4 dp a la izquierda (acento, nunca relleno), flecha ámbar de 56 dp, distancia en cursiva Black
 * de 40 sp, la frase, la placa de la vía a la derecha y, si la hay, la maniobra siguiente como ficha de carbón sólido
 * de 56 dp de alto como mínimo («Luego ↰ izquierda», icono de 28 dp y texto de 12 sp o más, sin recortar).
 * El escalado de la fuente del sistema se limita a 1,3 ([LimitarEscalaHud]). [salidaRotonda] pone el número de salida
 * en el icono. [via] es el `ref` de la vía de
 * la maniobra. Con [compacto] (pantalla apaisada) va en una sola línea baja y sin maniobra siguiente.
 *
 * El antiguo estilo de iconos de Ajustes (`EstiloManiobras`: chevron, placa roja o nota) sigue guardado en la base de
 * datos pero se ignora: el banner usa siempre los iconos ámbar.
 */
@Composable
@Suppress("LongParameterList") // firma fijada por el diseño: tipo, distancia, frase y siguiente
fun BannerManiobra(
    tipo: TipoManiobra,
    distanciaM: Double,
    frase: String,
    siguiente: Pair<TipoManiobra, String>?,
    modifier: Modifier = Modifier,
    salidaRotonda: Int? = null,
    via: String? = null,
    compacto: Boolean = false,
    siguienteSalida: Int? = null,
    siguienteVia: String? = null,
) {
    LimitarEscalaHud {
        TarjetaWrc(
            modifier = modifier.fillMaxWidth().testTag(ETIQUETA_BANNER).semantics(mergeDescendants = true) {},
            variante = VarianteTarjeta.MAPA,
            activa = true,
            acentoRojo = true,
            forma = RoundedCornerShape(RADIO_BANNER),
            relleno = if (compacto) RELLENO_COMPACTO else RELLENO_BANNER,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoManiobra(
                    tipo = tipo,
                    variante = VARIANTE_BANNER,
                    tamano = if (compacto) TAMANO_ICONO_COMPACTO else TAMANO_ICONO,
                    salidaRotonda = salidaRotonda,
                    decorativo = true,
                )
                if (compacto) {
                    TextosCompactos(distanciaM, frase, Modifier.weight(1f))
                } else {
                    TextosCompletos(distanciaM, frase, siguiente, siguienteSalida to siguienteVia, Modifier.weight(1f))
                }
                if (via != null) PlacaCarretera(via, tamano = TamanoPlaca.PEQUENA)
            }
        }
    }
}

/** Distancia, frase (hasta dos líneas) y la maniobra siguiente. */
@Composable
private fun TextosCompletos(
    distanciaM: Double,
    frase: String,
    siguiente: Pair<TipoManiobra, String>?,
    detalleSiguiente: Pair<Int?, String?>,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = formatearDistanciaManiobra(distanciaM),
            modifier = Modifier.testTag(ETIQUETA_BANNER_DISTANCIA),
            color = TextoSobreCarbon,
            style = CifraMedia,
            maxLines = 1,
        )
        Text(
            text = frase,
            modifier = Modifier.testTag(ETIQUETA_BANNER_FRASE),
            color = TextoSobreCarbon,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (siguiente != null) FilaSiguiente(siguiente, detalleSiguiente.first, detalleSiguiente.second)
    }
}

/** Distancia y frase en una sola línea. */
@Composable
private fun TextosCompactos(
    distanciaM: Double,
    frase: String,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatearDistanciaManiobra(distanciaM),
            modifier = Modifier.testTag(ETIQUETA_BANNER_DISTANCIA),
            color = TextoSobreCarbon,
            style = CifraMedia.copy(fontSize = 28.sp, lineHeight = 30.sp),
            maxLines = 1,
        )
        Text(
            text = frase,
            modifier = Modifier.weight(1f).testTag(ETIQUETA_BANNER_FRASE),
            color = TextoSobreCarbon,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Ficha de la maniobra siguiente: «Luego» + icono de 28 dp + texto, sobre carbón sólido, de 56 dp de alto como mínimo y
 * ocupando el ancho de la columna (no se pega al borde ni se recorta). El texto va de 12 a 14 sp (nunca por debajo de
 * 12) y, si no cabe en una línea, pasa a una segunda. Las rotondas van compactas: «Rotonda · 2.ª salida» o «Rotonda ·
 * 1.ª · Carrer X».
 */
@Composable
private fun FilaSiguiente(
    siguiente: Pair<TipoManiobra, String>,
    salidaRotonda: Int?,
    via: String?,
) {
    val compactaRotonda = siguiente.first == TipoManiobra.ROTONDA_ENTRADA && salidaRotonda != null
    val texto =
        when {
            !compactaRotonda -> siguiente.second
            via != null -> stringResource(R.string.maniobra_luego_rotonda_via, salidaRotonda ?: 0, via)
            else -> stringResource(R.string.maniobra_luego_rotonda, salidaRotonda ?: 0)
        }
    Row(
        modifier =
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .testTag(ETIQUETA_BANNER_SIGUIENTE)
                .background(CarbonOpaco, RoundedCornerShape(RADIO_FICHA_SIGUIENTE))
                .defaultMinSize(minHeight = ALTO_FICHA_SIGUIENTE)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.maniobra_luego),
            color = TextoSecundarioSobreCarbon,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = TEXTO_MINIMO_SIGUIENTE),
            maxLines = 1,
        )
        IconoManiobra(
            tipo = siguiente.first,
            variante = VARIANTE_BANNER,
            modifier = Modifier.size(TAMANO_ICONO_SIGUIENTE),
            tamano = TAMANO_ICONO_SIGUIENTE,
            salidaRotonda = salidaRotonda,
            decorativo = true,
            mostrarNumero = false,
        )
        Text(
            text = texto,
            modifier = Modifier.weight(1f),
            color = TextoSobreCarbon,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = TEXTO_MINIMO_SIGUIENTE,
                    maxFontSize = TEXTO_MAXIMO_SIGUIENTE,
                    stepSize = 0.5.sp,
                ),
        )
    }
}
