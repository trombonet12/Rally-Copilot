package app.trombito.rallycopilot.motor.ruta

/** Frases del copiloto al llegar a una parada intermedia. */
object FrasesParada {
    /** Texto de una parada intermedia que aún no se ha alcanzado, en vez de «destino» (que solo es el final). */
    const val PARADA = "parada"

    /**
     * Frase al alcanzar la parada [n] (0 = la primera) de [ruta]: «parada alcanzada, siguiente cruce, derecha, Ma-10».
     * Si lo siguiente es otra parada o el destino lo dice así; sin maniobras por delante, solo «parada alcanzada».
     */
    fun alcanzada(
        ruta: Ruta,
        n: Int,
    ): String {
        val siguiente = ruta.maniobraTrasParada(n) ?: return "parada alcanzada"
        val texto =
            when {
                siguiente.tipo != TipoManiobra.DESTINO -> FrasesManiobra.frase(siguiente)
                siguiente === ruta.maniobras.lastOrNull() -> FrasesManiobra.frase(siguiente)
                else -> PARADA
            }
        return "parada alcanzada, siguiente $texto"
    }
}
