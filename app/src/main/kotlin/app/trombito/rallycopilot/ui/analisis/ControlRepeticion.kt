package app.trombito.rallycopilot.ui.analisis

import app.trombito.rallycopilot.motor.tramos.MarcaTiempo

/** Velocidades de la repetición: veces más rápido que el tiempo real. */
enum class VelocidadRepeticion(
    val factor: Int,
) {
    X1(1),
    X4(4),
    X16(16),
}

/**
 * Estado de la repetición del recorrido: [tMs] es el instante (ms desde la salida) del marcador sobre el mapa y del
 * cursor de los gráficos, o null si aún no se ha puesto en marcha ni se ha tocado un gráfico.
 */
data class EstadoRepeticion(
    val tMs: Long? = null,
    val reproduciendo: Boolean = false,
    val velocidad: VelocidadRepeticion = VelocidadRepeticion.X1,
)

/**
 * Avanza la repetición [pasoRealMs] ms de reloj real: el tiempo del recorrido avanza `paso × factor`. Al llegar al
 * final se detiene en la duración exacta; sin reproducir no cambia.
 */
fun avanzarRepeticion(
    estado: EstadoRepeticion,
    pasoRealMs: Long,
    duracionMs: Long,
): EstadoRepeticion {
    val actual = estado.tMs
    if (!estado.reproduciendo || actual == null) return estado
    val siguiente = actual + pasoRealMs * estado.velocidad.factor
    return if (siguiente >= duracionMs) {
        estado.copy(tMs = duracionMs, reproduciendo = false)
    } else {
        estado.copy(tMs = siguiente)
    }
}

/**
 * Pone en marcha o pausa. Al arrancar sin posición, o desde el final, la repetición empieza de nuevo desde la salida.
 */
fun alternarRepeticion(
    estado: EstadoRepeticion,
    duracionMs: Long,
): EstadoRepeticion =
    if (estado.reproduciendo) {
        estado.copy(reproduciendo = false)
    } else {
        val actual = estado.tMs
        estado.copy(tMs = if (actual == null || actual >= duracionMs) 0L else actual, reproduciendo = true)
    }

/**
 * Distancia (m desde la salida) a la que se está en el instante [tMs] según la curva distancia-tiempo de la pasada,
 * interpolada entre marcas y acotada a sus extremos. Es la inversa de `Pasada.tiempoEn`: cuando el coche iba parado
 * (varias marcas con la misma distancia) da la distancia de la parada.
 */
fun distanciaEnTiempo(
    marcas: List<MarcaTiempo>,
    tMs: Long,
): Double {
    if (marcas.isEmpty()) return 0.0
    val i = marcas.indexOfFirst { it.tMs >= tMs }
    return when {
        i < 0 -> {
            marcas.last().distanciaM
        }

        i == 0 -> {
            marcas.first().distanciaM
        }

        else -> {
            val a = marcas[i - 1]
            val b = marcas[i]
            val ancho = b.tMs - a.tMs
            if (ancho <= 0L) {
                b.distanciaM
            } else {
                a.distanciaM + (tMs - a.tMs).toDouble() / ancho * (b.distanciaM - a.distanciaM)
            }
        }
    }
}
