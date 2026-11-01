package app.trombito.rallycopilot.motor.alertas

/**
 * Tramo de radar medido sobre la ruta: [inicioM] y [finM] son metros desde el inicio de la ruta (el mismo eje que la
 * posición del conductor) y [limiteKmh] el límite que controla, si se conoce.
 */
data class TramoRadar(
    val id: String,
    val inicioM: Double,
    val finM: Double,
    val limiteKmh: Int?,
)
