package app.trombito.rallycopilot.domain.ajustes

/**
 * Tema de la interfaz. Por defecto [SOL] («Día y noche»): pinta claro de día y oscuro de noche según la puesta y la
 * salida del sol (se calculan sin red; no usa el sensor de luz, así que un túnel no lo cambia). [OSCURO] (estética de
 * retransmisión de rally) y [CLARO] son fijos y [SISTEMA] sigue el modo del móvil. Se guarda por nombre: añadir
 * valores al final no necesita migración.
 */
enum class Tema {
    OSCURO,
    CLARO,
    SISTEMA,
    SOL,
    ;

    /** Si la interfaz se pinta en oscuro; [sistemaOscuro] solo cuenta con [SISTEMA] y [solOscuro] con [SOL]. */
    fun esOscuro(
        sistemaOscuro: Boolean,
        solOscuro: Boolean = true,
    ): Boolean =
        when (this) {
            OSCURO -> true
            CLARO -> false
            SISTEMA -> sistemaOscuro
            SOL -> solOscuro
        }

    companion object {
        val PREDETERMINADO = SOL
    }
}
