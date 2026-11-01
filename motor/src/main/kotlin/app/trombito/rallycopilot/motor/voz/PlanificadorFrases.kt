package app.trombito.rallycopilot.motor.voz

/**
 * Decide qué frase decir y cuándo. Recuerda lo dicho y lo descartado por id: no se repite nunca, y una frase que ya
 * no cabe se pierde para siempre («una nota nunca llega tarde»). [duracionS] da lo que dura la voz en cantar un texto.
 * Se consulta solo cuando el altavoz está libre.
 */
class PlanificadorFrases(
    private val duracionS: (String) -> Double,
) {
    private val resueltas = HashSet<String>()

    /**
     * La frase que toca decir ahora, o `null`. Filtra por [modo] (lo que el modo calla no se descarta), tira la que
     * ya pasó (`posicionM >= puntoM`) o no acabaría antes de llegar (a más de 1 m/s, `(puntoM − posicionM) / vMs`
     * menor que la duración), y entre el resto elige la de más prioridad y, a igualdad, el [Frase.puntoM] más
     * cercano (sin punto, la primera). La elegida queda marcada como dicha.
     */
    fun siguiente(
        candidatas: List<Frase>,
        posicionM: Double,
        vMs: Double?,
        modo: ModoCopiloto,
    ): Frase? {
        val elegida =
            candidatas
                .filter { it.id !in resueltas && modo.permite(it.tipo) }
                .filter { vigente(it, posicionM, vMs) }
                .minWithOrNull(ORDEN_URGENCIA)
        if (elegida != null) resueltas.add(elegida.id)
        return elegida
    }

    /**
     * Hasta [n] frases aún no dichas ni pasadas que el [modo] permite, en orden de [Frase.puntoM] (las sin punto
     * primero), para sintetizarlas por adelantado. No marca nada.
     */
    fun proximas(
        candidatas: List<Frase>,
        posicionM: Double,
        modo: ModoCopiloto,
        n: Int,
    ): List<Frase> =
        candidatas
            .filter { it.id !in resueltas && modo.permite(it.tipo) && (it.puntoM == null || it.puntoM > posicionM) }
            .sortedBy { it.puntoM ?: Double.NEGATIVE_INFINITY }
            .take(n.coerceAtLeast(0))

    /** `true` si la frase aún llega a tiempo; si no, la descarta para siempre. */
    private fun vigente(
        frase: Frase,
        posicionM: Double,
        vMs: Double?,
    ): Boolean {
        val punto = frase.puntoM ?: return true
        val restanteM = punto - posicionM
        val tarde =
            restanteM <= 0.0 || (vMs != null && vMs > VELOCIDAD_MIN_MS && restanteM / vMs < duracionS(frase.texto))
        if (tarde) resueltas.add(frase.id)
        return !tarde
    }

    private companion object {
        /** Por debajo de esta velocidad no se aplica el corte por tiempo (parado o GPS dudoso). */
        const val VELOCIDAD_MIN_MS = 1.0

        val ORDEN_URGENCIA =
            compareBy<Frase> { it.tipo.prioridad }.thenBy { it.puntoM ?: Double.NEGATIVE_INFINITY }
    }
}
