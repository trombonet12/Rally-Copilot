package app.trombito.rallycopilot.ui.maniobras

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.ruta.TipoManiobra

private val ICONOS_CHEVRON: Map<TipoManiobra, Int> =
    mapOf(
        TipoManiobra.SALIDA to R.drawable.ic_man_a_salida,
        TipoManiobra.DESTINO to R.drawable.ic_man_a_destino,
        TipoManiobra.RECTO to R.drawable.ic_man_a_recto,
        TipoManiobra.LIGERA_IZQUIERDA to R.drawable.ic_man_a_ligera_izq,
        TipoManiobra.LIGERA_DERECHA to R.drawable.ic_man_a_ligera_der,
        TipoManiobra.IZQUIERDA to R.drawable.ic_man_a_izq,
        TipoManiobra.DERECHA to R.drawable.ic_man_a_der,
        TipoManiobra.CERRADA_IZQUIERDA to R.drawable.ic_man_a_cerrada_izq,
        TipoManiobra.CERRADA_DERECHA to R.drawable.ic_man_a_cerrada_der,
        TipoManiobra.CAMBIO_SENTIDO to R.drawable.ic_man_a_cambio_sentido,
        TipoManiobra.ROTONDA_ENTRADA to R.drawable.ic_man_a_rotonda,
        TipoManiobra.ROTONDA_SALIDA to R.drawable.ic_man_a_rotonda,
        TipoManiobra.INCORPORACION to R.drawable.ic_man_a_incorporacion,
        TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA to R.drawable.ic_man_a_salida_autovia_izq,
        TipoManiobra.SALIDA_AUTOVIA_DERECHA to R.drawable.ic_man_a_salida_autovia_der,
        TipoManiobra.BIFURCACION_IZQUIERDA to R.drawable.ic_man_a_bifurcacion_izq,
        TipoManiobra.BIFURCACION_DERECHA to R.drawable.ic_man_a_bifurcacion_der,
        TipoManiobra.FERRY to R.drawable.ic_man_a_ferry,
        TipoManiobra.OTRA to R.drawable.ic_man_a_otra,
    )

private val ICONOS_PLACA: Map<TipoManiobra, Int> =
    mapOf(
        TipoManiobra.SALIDA to R.drawable.ic_man_b_salida,
        TipoManiobra.DESTINO to R.drawable.ic_man_b_destino,
        TipoManiobra.RECTO to R.drawable.ic_man_b_recto,
        TipoManiobra.LIGERA_IZQUIERDA to R.drawable.ic_man_b_ligera_izq,
        TipoManiobra.LIGERA_DERECHA to R.drawable.ic_man_b_ligera_der,
        TipoManiobra.IZQUIERDA to R.drawable.ic_man_b_izq,
        TipoManiobra.DERECHA to R.drawable.ic_man_b_der,
        TipoManiobra.CERRADA_IZQUIERDA to R.drawable.ic_man_b_cerrada_izq,
        TipoManiobra.CERRADA_DERECHA to R.drawable.ic_man_b_cerrada_der,
        TipoManiobra.CAMBIO_SENTIDO to R.drawable.ic_man_b_cambio_sentido,
        TipoManiobra.ROTONDA_ENTRADA to R.drawable.ic_man_b_rotonda,
        TipoManiobra.ROTONDA_SALIDA to R.drawable.ic_man_b_rotonda,
        TipoManiobra.INCORPORACION to R.drawable.ic_man_b_incorporacion,
        TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA to R.drawable.ic_man_b_salida_autovia_izq,
        TipoManiobra.SALIDA_AUTOVIA_DERECHA to R.drawable.ic_man_b_salida_autovia_der,
        TipoManiobra.BIFURCACION_IZQUIERDA to R.drawable.ic_man_b_bifurcacion_izq,
        TipoManiobra.BIFURCACION_DERECHA to R.drawable.ic_man_b_bifurcacion_der,
        TipoManiobra.FERRY to R.drawable.ic_man_b_ferry,
        TipoManiobra.OTRA to R.drawable.ic_man_b_otra,
    )

private val ICONOS_NOTA: Map<TipoManiobra, Int> =
    mapOf(
        TipoManiobra.SALIDA to R.drawable.ic_man_c_salida,
        TipoManiobra.DESTINO to R.drawable.ic_man_c_destino,
        TipoManiobra.RECTO to R.drawable.ic_man_c_recto,
        TipoManiobra.LIGERA_IZQUIERDA to R.drawable.ic_man_c_ligera_izq,
        TipoManiobra.LIGERA_DERECHA to R.drawable.ic_man_c_ligera_der,
        TipoManiobra.IZQUIERDA to R.drawable.ic_man_c_izq,
        TipoManiobra.DERECHA to R.drawable.ic_man_c_der,
        TipoManiobra.CERRADA_IZQUIERDA to R.drawable.ic_man_c_cerrada_izq,
        TipoManiobra.CERRADA_DERECHA to R.drawable.ic_man_c_cerrada_der,
        TipoManiobra.CAMBIO_SENTIDO to R.drawable.ic_man_c_cambio_sentido,
        TipoManiobra.ROTONDA_ENTRADA to R.drawable.ic_man_c_rotonda,
        TipoManiobra.ROTONDA_SALIDA to R.drawable.ic_man_c_rotonda,
        TipoManiobra.INCORPORACION to R.drawable.ic_man_c_incorporacion,
        TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA to R.drawable.ic_man_c_salida_autovia_izq,
        TipoManiobra.SALIDA_AUTOVIA_DERECHA to R.drawable.ic_man_c_salida_autovia_der,
        TipoManiobra.BIFURCACION_IZQUIERDA to R.drawable.ic_man_c_bifurcacion_izq,
        TipoManiobra.BIFURCACION_DERECHA to R.drawable.ic_man_c_bifurcacion_der,
        TipoManiobra.FERRY to R.drawable.ic_man_c_ferry,
        TipoManiobra.OTRA to R.drawable.ic_man_c_otra,
    )

/**
 * Recurso del icono de una maniobra. [salidaRotonda] no cambia el dibujo: el número de salida lo pinta
 * [IconoManiobra] encima del anillo (el recurso es el mismo para todas las salidas).
 */
@DrawableRes
@Suppress("UNUSED_PARAMETER")
fun iconoManiobra(
    tipo: TipoManiobra,
    variante: VarianteManiobra,
    salidaRotonda: Int? = null,
): Int {
    val tabla =
        when (variante) {
            VarianteManiobra.CHEVRON_HUD -> ICONOS_CHEVRON
            VarianteManiobra.PLACA_INCLINADA -> ICONOS_PLACA
            VarianteManiobra.NOTA_RALLY -> ICONOS_NOTA
        }
    return tabla.getValue(tipo)
}

private val NOMBRES: Map<TipoManiobra, Int> =
    mapOf(
        TipoManiobra.SALIDA to R.string.maniobra_salida,
        TipoManiobra.DESTINO to R.string.maniobra_destino,
        TipoManiobra.RECTO to R.string.maniobra_recto,
        TipoManiobra.LIGERA_IZQUIERDA to R.string.maniobra_ligera_izquierda,
        TipoManiobra.LIGERA_DERECHA to R.string.maniobra_ligera_derecha,
        TipoManiobra.IZQUIERDA to R.string.maniobra_izquierda,
        TipoManiobra.DERECHA to R.string.maniobra_derecha,
        TipoManiobra.CERRADA_IZQUIERDA to R.string.maniobra_cerrada_izquierda,
        TipoManiobra.CERRADA_DERECHA to R.string.maniobra_cerrada_derecha,
        TipoManiobra.CAMBIO_SENTIDO to R.string.maniobra_cambio_sentido,
        TipoManiobra.ROTONDA_ENTRADA to R.string.maniobra_rotonda,
        TipoManiobra.ROTONDA_SALIDA to R.string.maniobra_rotonda,
        TipoManiobra.INCORPORACION to R.string.maniobra_incorporacion,
        TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA to R.string.maniobra_salida_autovia_izquierda,
        TipoManiobra.SALIDA_AUTOVIA_DERECHA to R.string.maniobra_salida_autovia_derecha,
        TipoManiobra.BIFURCACION_IZQUIERDA to R.string.maniobra_bifurcacion_izquierda,
        TipoManiobra.BIFURCACION_DERECHA to R.string.maniobra_bifurcacion_derecha,
        TipoManiobra.FERRY to R.string.maniobra_ferry,
        TipoManiobra.OTRA to R.string.maniobra_otra,
    )

/** Nombre hablado de la maniobra, para TalkBack. */
@StringRes
fun nombreManiobra(tipo: TipoManiobra): Int = NOMBRES.getValue(tipo)

internal fun esRotonda(tipo: TipoManiobra): Boolean =
    tipo == TipoManiobra.ROTONDA_ENTRADA || tipo == TipoManiobra.ROTONDA_SALIDA

/** Proporción del tamaño del icono que ocupa el número de la salida, según el hueco del anillo de cada variante. */
private fun escalaNumero(variante: VarianteManiobra): Float =
    when (variante) {
        VarianteManiobra.CHEVRON_HUD -> 0.30f
        VarianteManiobra.PLACA_INCLINADA -> 0.22f
        VarianteManiobra.NOTA_RALLY -> 0.30f
    }

private fun colorNumero(variante: VarianteManiobra): Color =
    when (variante) {
        VarianteManiobra.CHEVRON_HUD -> Color(0xFFE5AB46)
        VarianteManiobra.PLACA_INCLINADA, VarianteManiobra.NOTA_RALLY -> Color.White
    }

/**
 * Icono de maniobra. En las rotondas con [salidaRotonda] dibuja el número de salida dentro del anillo.
 * [decorativo] oculta el icono a TalkBack cuando el texto de al lado ya dice lo mismo.
 */
@Composable
@Suppress("LongParameterList") // API de composable con valores por defecto
fun IconoManiobra(
    tipo: TipoManiobra,
    variante: VarianteManiobra,
    modifier: Modifier = Modifier,
    tamano: Dp = 72.dp,
    salidaRotonda: Int? = null,
    decorativo: Boolean = false,
) {
    val descripcion =
        when {
            decorativo -> null
            esRotonda(tipo) && salidaRotonda != null -> stringResource(R.string.maniobra_rotonda_salida, salidaRotonda)
            else -> stringResource(nombreManiobra(tipo))
        }
    Box(
        modifier = modifier.size(tamano).semantics { descripcion?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconoManiobra(tipo, variante, salidaRotonda)),
            contentDescription = null,
            modifier = Modifier.size(tamano),
        )
        if (esRotonda(tipo) && salidaRotonda != null) {
            Text(
                text = salidaRotonda.toString(),
                color = colorNumero(variante),
                style =
                    TextStyle(
                        fontSize = (tamano.value * escalaNumero(variante)).sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        shadow = Shadow(Color(0xFF0E0F12), Offset(0f, 0f), blurRadius = 6f),
                    ),
            )
        }
    }
}
