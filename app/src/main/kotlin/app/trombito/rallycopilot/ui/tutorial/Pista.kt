package app.trombito.rallycopilot.ui.tutorial

/**
 * Las pantallas con una pista en contexto; cada una se enseña una vez. [clave] es lo que se guarda como vista y [lado]
 * de qué lado del elemento señalado va el globo.
 */
enum class Pista(
    val clave: String,
    val lado: LadoPista,
) {
    MAPA("mapa", LadoPista.IZQUIERDA),
    MIS_TRAMOS("mis_tramos", LadoPista.ABAJO),
    ANALISIS("analisis", LadoPista.ARRIBA),
    EDITAR_TRAMO("editar_tramo", LadoPista.ABAJO),
}
