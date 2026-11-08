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
     * cercano (sin punto, la primera). La elegida queda marcada como dicha, junto con las notas que [Frase.cubre].
     *
     * Los avisos de velocidad ([TipoFrase.AVISO_VELOCIDAD]) se difieren (sin descartarlos) mientras haya una nota o
     * maniobra pendiente a menos de [HORIZONTE_AVISO_S] segundos de camino, sea candidata o esté en [venideras] (las
     * notas y maniobras que aún no están activas pero ya vienen): primero se canta la carretera.
     */
    fun siguiente(
        candidatas: List<Frase>,
        posicionM: Double,
        vMs: Double?,
        modo: ModoCopiloto,
        venideras: List<Frase> = emptyList(),
    ): Frase? {
        val vigentes =
            candidatas
                .filter { it.id !in resueltas && modo.permite(it.tipo) }
                .filter { vigente(it, posicionM, vMs) }
        val cercanas =
            if (vigentes.any { it.tipo == TipoFrase.AVISO_VELOCIDAD }) {
                (candidatas + venideras).filter { esCercana(it, posicionM, vMs, modo) }
            } else {
                emptyList()
            }
        val elegida =
            vigentes
                .filter { cercanas.isEmpty() || it.tipo != TipoFrase.AVISO_VELOCIDAD }
                .minWithOrNull(ORDEN_URGENCIA)
        if (elegida != null) {
            resueltas.add(elegida.id)
            resueltas.addAll(elegida.cubre)
        }
        return elegida
    }

    /** `true` si la [id] ya se dijo o se descartó (la voz no la repetirá). */
    fun resuelta(id: String): Boolean = id in resueltas

    /** Da [id] por dicha sin decirla (una maniobra equivalente de la ruta anterior ya se dijo). */
    fun marcarResuelta(id: String) {
        resueltas.add(id)
    }

    /** Nota o maniobra aún por cantar, que el modo permite, a menos de [HORIZONTE_AVISO_S] s por delante. */
    private fun esCercana(
        frase: Frase,
        posicionM: Double,
        vMs: Double?,
        modo: ModoCopiloto,
    ): Boolean {
        val punto = frase.puntoM
        val porCantar = frase.id !in resueltas && modo.permite(frase.tipo)
        val deCarretera = frase.tipo == TipoFrase.NOTA || frase.tipo == TipoFrase.MANIOBRA
        if (punto == null || !porCantar || !deCarretera) return false
        val restanteM = punto - posicionM
        return restanteM > 0.0 && vMs != null && vMs > VELOCIDAD_MIN_MS && restanteM / vMs <= HORIZONTE_AVISO_S
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

        /** Un aviso de velocidad espera si una nota o maniobra queda a esta distancia en segundos o menos. */
        const val HORIZONTE_AVISO_S = 8.0

        val ORDEN_URGENCIA =
            compareBy<Frase> { it.tipo.prioridad }.thenBy { it.puntoM ?: Double.NEGATIVE_INFINITY }
    }
}
