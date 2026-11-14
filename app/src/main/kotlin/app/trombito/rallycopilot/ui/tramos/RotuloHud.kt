package app.trombito.rallycopilot.ui.tramos

/** Líneas del rótulo en vertical: el nombre de un tramo de vía («Ma-15 · km ~20,1→~32,4 · hacia Manacor») se parte. */
internal const val LINEAS_ROTULO_HUD = 2
private const val SEPARADOR_DESTINO = " · hacia "

/**
 * El rótulo del HUD: en apaisado ([compacto], una sola línea) el nombre de un tramo de vía pierde el destino y se queda
 * en la carretera y el rango de kilómetros («Ma-15 · km ~20,1→~32,4»); el resto de nombres no cambia.
 */
internal fun rotuloCompacto(
    nombre: String,
    compacto: Boolean,
): String = if (compacto) nombre.substringBefore(SEPARADOR_DESTINO) else nombre
