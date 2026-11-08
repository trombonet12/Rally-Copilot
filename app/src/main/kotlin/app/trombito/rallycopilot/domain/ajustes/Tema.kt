package app.trombito.rallycopilot.domain.ajustes

/**
 * Tema de la interfaz. Oscuro por defecto (estética de retransmisión de rally); el claro es opcional, [SISTEMA]
 * sigue el modo del móvil y [SOL] pinta claro de día y oscuro de noche según la puesta y la salida del sol (se
 * calculan sin red; no usa el sensor de luz, así que un túnel no lo cambia). Se guarda por nombre: añadir valores
 * al final no necesita migración.
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
        val PREDETERMINADO = OSCURO
    }
}
