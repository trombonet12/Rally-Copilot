package app.trombito.rallycopilot.ui.tutorial

/** Las pantallas con una pista en contexto; cada una se enseña una vez. [clave] es lo que se guarda como vista. */
enum class Pista(
    val clave: String,
) {
    MAPA("mapa"),
    MIS_TRAMOS("mis_tramos"),
    ANALISIS("analisis"),
    EDITAR_TRAMO("editar_tramo"),
}
