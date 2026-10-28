package app.trombito.rallycopilot.motor.busqueda

import java.text.Normalizer

/**
 * Normalización de nombres para buscar; replica exacta de `nombre_norm` de `scripts/busqueda.py` (repo de datos), que
 * es quien rellena la columna `nombre_norm` del índice. Si cambia uno, cambia el otro (mismos casos de test).
 *
 * Pasos: minúsculas; `l·l` y `ŀl` -> `ll`; NFD sin marcas combinantes; apóstrofos, guiones y `·` -> espacio; espacios
 * colapsados y recortados. «Sóller» -> «soller», «Cala d'Or» -> «cala d or», «Col·legi» -> «collegi».
 */
object NormalizadorNombre {
    private const val ELE_GEMINADA_PUNTO_MEDIO = "l·l"
    private const val ELE_GEMINADA_LIGADA = "ŀl"

    /** Apóstrofos (' ’ ʼ ` ´), guiones (- ‐ ‑ – —) y punto medio (·). */
    private const val SEPARADORES = "'’ʼ`´-‐‑–—·"

    fun normalizar(texto: String): String {
        val minusculas =
            texto
                .lowercase()
                .replace(ELE_GEMINADA_PUNTO_MEDIO, "ll")
                .replace(ELE_GEMINADA_LIGADA, "ll")
        val salida = StringBuilder(minusculas.length)
        var pendienteEspacio = false
        for (c in Normalizer.normalize(minusculas, Normalizer.Form.NFD)) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            if (c in SEPARADORES || esBlanco(c)) {
                pendienteEspacio = salida.isNotEmpty()
            } else {
                if (pendienteEspacio) salida.append(' ')
                pendienteEspacio = false
                salida.append(c)
            }
        }
        return salida.toString()
    }

    private fun esBlanco(c: Char): Boolean = Character.isWhitespace(c) || Character.isSpaceChar(c)
}
