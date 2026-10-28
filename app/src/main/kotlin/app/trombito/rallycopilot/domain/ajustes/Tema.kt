package app.trombito.rallycopilot.domain.ajustes

/** Tema de la interfaz. Oscuro por defecto (estética de retransmisión de rally); el claro es opcional. */
enum class Tema(
    val oscuro: Boolean,
) {
    OSCURO(oscuro = true),
    CLARO(oscuro = false),
    ;

    companion object {
        val PREDETERMINADO = OSCURO
    }
}
