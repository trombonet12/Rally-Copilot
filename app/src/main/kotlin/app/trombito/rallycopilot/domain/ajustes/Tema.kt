package app.trombito.rallycopilot.domain.ajustes

/**
 * Tema de la interfaz. Oscuro por defecto (estética de retransmisión de rally); el claro es opcional y [SISTEMA]
 * sigue el modo del móvil.
 */
enum class Tema {
    OSCURO,
    CLARO,
    SISTEMA,
    ;

    /** Si la interfaz se pinta en oscuro; [sistemaOscuro] solo cuenta con [SISTEMA]. */
    fun esOscuro(sistemaOscuro: Boolean): Boolean =
        when (this) {
            OSCURO -> true
            CLARO -> false
            SISTEMA -> sistemaOscuro
        }

    companion object {
        val PREDETERMINADO = OSCURO
    }
}
