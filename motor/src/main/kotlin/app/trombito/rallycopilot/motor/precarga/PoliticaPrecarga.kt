package app.trombito.rallycopilot.motor.precarga

/** Cuándo se permite la descarga automática de detalle (la manual la lanza el usuario y no pasa por aquí). */
object PoliticaPrecarga {
    const val BATERIA_MINIMA_PORCENTAJE = 30

    /** Cargando, o con más del [BATERIA_MINIMA_PORCENTAJE] % de batería. */
    fun permiteAutomatica(
        cargando: Boolean,
        porcentajeBateria: Int,
    ): Boolean = cargando || porcentajeBateria > BATERIA_MINIMA_PORCENTAJE
}
