package app.trombito.rallycopilot.ui.maniobras

/**
 * Estilo gráfico de los iconos de maniobra (propuestas del diseño original). Desde H14c el banner usa siempre
 * [CHEVRON_HUD] y el estilo de Ajustes (`EstiloManiobras`) se conserva guardado pero se ignora; la galería de
 * depuración y los tests siguen usando las tres:
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
