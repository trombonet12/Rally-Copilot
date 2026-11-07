package app.trombito.rallycopilot.motor.velocidad

/** Milisegundos por segundo. */
const val MS_POR_S = 1000.0

private const val KMH_POR_MS = 3.6

/** Esta velocidad en m/s, pasada a km/h. */
fun Double.msAKmh(): Double = this * KMH_POR_MS

/** Esta velocidad en km/h, pasada a m/s. */
fun Double.kmhAMs(): Double = this / KMH_POR_MS
