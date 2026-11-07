package app.trombito.rallycopilot.motor.datos

/**
 * Datos que NO van dentro del APK: se descargan al elegirlos y luego funcionan sin red. La regla de qué va en el APK
 * se deduce de los ids del manifest: todo lo que no empiece por un prefijo de aquí se empaqueta (la lista
 * [PREFIJOS_BAJO_DEMANDA] debe coincidir con `bajoDemanda` de `app/datos-incluidos.properties`, que lee Gradle; un test
 * lo comprueba).
 *
 * @property prefijosIds prefijos de los ids del manifest que pertenecen al complemento.
 * @property idsRequeridos ids que deben estar instalados para considerarlo instalado.
 * @property bytesAproximados tamaño orientativo para avisar antes de descargar si no se ha podido leer el manifest.
 */
enum class Complemento(
    val prefijosIds: List<String>,
    val idsRequeridos: List<String>,
    val bytesAproximados: Long,
) {
    /** Satélite PNOA z16 (y z17 si lo hay). */
    SATELITE(listOf("satelite"), listOf("satelite"), 1_300_000_000L),

    /** Relieve (hillshade MDT05) y curvas de nivel. */
    RELIEVE(listOf("relieve", "curvas"), listOf("relieve", "curvas"), 69_331_524L),

    /** Voz Sharvard (la voz Dave va en el APK). */
    VOZ_SHARVARD(listOf("voz-sharvard"), listOf("voz-sharvard", "voz-sharvard-tokens"), 21_993_140L),
    ;

    fun incluye(id: String): Boolean = prefijosIds.any { id.startsWith(it) }

    /** true si [instalados] contiene todos los ids requeridos. */
    fun estaCompleto(instalados: Set<String>): Boolean = idsRequeridos.all { it in instalados }

    companion object {
        /** Prefijos de todos los complementos, en el orden del enum. */
        val PREFIJOS_BAJO_DEMANDA: List<String> = entries.flatMap { it.prefijosIds }

        /** El complemento al que pertenece el id, o null si va en el APK. */
        fun de(id: String): Complemento? = entries.firstOrNull { it.incluye(id) }

        fun esBajoDemanda(id: String): Boolean = de(id) != null
    }
}
