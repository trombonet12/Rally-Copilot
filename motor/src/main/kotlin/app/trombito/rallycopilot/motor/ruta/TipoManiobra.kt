package app.trombito.rallycopilot.motor.ruta

/** Tipo de maniobra simplificado para el copiloto; se deriva del `type` numérico de Valhalla (0–39). */
enum class TipoManiobra {
    SALIDA,
    DESTINO,
    RECTO,
    LIGERA_DERECHA,
    LIGERA_IZQUIERDA,
    DERECHA,
    IZQUIERDA,
    CERRADA_DERECHA,
    CERRADA_IZQUIERDA,
    CAMBIO_SENTIDO,
    ROTONDA_ENTRADA,
    ROTONDA_SALIDA,
    INCORPORACION,
    SALIDA_AUTOVIA_DERECHA,
    SALIDA_AUTOVIA_IZQUIERDA,
    BIFURCACION_DERECHA,
    BIFURCACION_IZQUIERDA,
    FERRY,
    OTRA,
    ;

    companion object {
        private val TABLA: Map<Int, TipoManiobra> =
            mapOf(
                1 to SALIDA,
                2 to SALIDA,
                3 to SALIDA,
                4 to DESTINO,
                5 to DESTINO,
                6 to DESTINO,
                7 to RECTO,
                8 to RECTO,
                17 to RECTO,
                22 to RECTO,
                9 to LIGERA_DERECHA,
                10 to DERECHA,
                11 to CERRADA_DERECHA,
                12 to CAMBIO_SENTIDO,
                13 to CAMBIO_SENTIDO,
                14 to CERRADA_IZQUIERDA,
                15 to IZQUIERDA,
                16 to LIGERA_IZQUIERDA,
                18 to SALIDA_AUTOVIA_DERECHA,
                20 to SALIDA_AUTOVIA_DERECHA,
                19 to SALIDA_AUTOVIA_IZQUIERDA,
                21 to SALIDA_AUTOVIA_IZQUIERDA,
                23 to BIFURCACION_DERECHA,
                24 to BIFURCACION_IZQUIERDA,
                25 to INCORPORACION,
                37 to INCORPORACION,
                38 to INCORPORACION,
                26 to ROTONDA_ENTRADA,
                27 to ROTONDA_SALIDA,
                28 to FERRY,
                29 to FERRY,
            )

        /** Tabla de `type` de Valhalla: transporte público, ascensores y desconocidos pasan a [OTRA]. */
        fun desdeValhalla(type: Int): TipoManiobra = TABLA[type] ?: OTRA
    }
}
