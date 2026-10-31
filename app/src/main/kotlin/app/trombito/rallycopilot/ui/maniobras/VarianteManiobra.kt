package app.trombito.rallycopilot.ui.maniobras

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras

/**
 * Estilo gráfico de los iconos de maniobra (propuestas para elegir):
 * - [CHEVRON_HUD]: flechas de trazo grueso latón con borde oscuro y punta en V abierta.
 * - [PLACA_INCLINADA]: flecha blanca sobre placa roja inclinada (paralelogramo).
 * - [NOTA_RALLY]: flecha curva de grosor variable que sigue la forma del giro, estilo roadbook.
 */
enum class VarianteManiobra(
    val etiqueta: String,
) {
    CHEVRON_HUD("A · Chevron HUD"),
    PLACA_INCLINADA("B · Placa inclinada"),
    NOTA_RALLY("C · Nota de rally"),
}

/** Variante de UI del estilo elegido en Ajustes. */
fun EstiloManiobras.variante(): VarianteManiobra =
    when (this) {
        EstiloManiobras.CHEVRON -> VarianteManiobra.CHEVRON_HUD
        EstiloManiobras.PLACA -> VarianteManiobra.PLACA_INCLINADA
        EstiloManiobras.NOTA -> VarianteManiobra.NOTA_RALLY
    }

@StringRes
fun EstiloManiobras.nombre(): Int =
    when (this) {
        EstiloManiobras.CHEVRON -> R.string.ajustes_maniobras_chevron
        EstiloManiobras.PLACA -> R.string.ajustes_maniobras_placa
        EstiloManiobras.NOTA -> R.string.ajustes_maniobras_nota
    }
