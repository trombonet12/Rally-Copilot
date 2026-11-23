package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.tramos.TramosPorDelante
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.TramoEnCamino

/**
 * Qué tramos conocidos hay por delante en el camino de las notas en curso ([NotasEnCurso.traza] medida desde su inicio,
 * con el coche en [NotasEnCurso.posicionM]): en guiado la ruta restante (hasta [VENTANA_GUIADO_M]) y en libre los
 * próximos [VENTANA_LIBRE_M] de la carretera prevista. [sobre] es la búsqueda (con `TramosEnCamino` en producción);
 * solo se vuelve a llamar al avanzar [PASO_RECALCULO_M] sobre lo calculado, al cambiar el camino o al retroceder más de
 * [RETROCESO_M]; en medio se reutiliza la misma lista y solo se actualiza la distancia a la meta. Una instancia por
 * recogida (tiene estado) y no es concurrente.
 */
internal class CalculadorTramosPorDelante(
    private val sobre: (List<PuntoGeo>) -> List<TramoEnCamino>,
) {
    private var trazaCalculada: List<PuntoGeo>? = null
    private var origenM = 0.0
    private var resultado: List<TramoEnCamino> = emptyList()

    /** Cuántas veces se ha buscado de verdad (para comprobar que no se recalcula con cada fijación). */
    var busquedas = 0
        private set

    fun calcular(notas: NotasEnCurso?): TramosPorDelante {
        val traza = notas?.takeIf { it.tramoReconocimiento == null }?.traza?.takeIf { it.size >= 2 }
        if (notas == null || traza == null) {
            trazaCalculada = null
            resultado = emptyList()
            return TramosPorDelante()
        }
        val posicionM = notas.posicionM.coerceAtLeast(0.0)
        val hecho = posicionM - origenM
        if (traza !== trazaCalculada || hecho >= PASO_RECALCULO_M || hecho < -RETROCESO_M) {
            recalcular(traza, posicionM, if (notas.origen == OrigenNotas.LIBRE) VENTANA_LIBRE_M else VENTANA_GUIADO_M)
        }
        return TramosPorDelante(resultado, finDeTramoM(posicionM))
    }

    private fun recalcular(
        traza: List<PuntoGeo>,
        posicionM: Double,
        ventanaM: Double,
    ) {
        busquedas++
        trazaCalculada = traza
        origenM = posicionM
        val camino = recortarTraza(traza, posicionM, posicionM + ventanaM)
        resultado = if (camino.size < 2) emptyList() else sobre(camino)
    }

    /** Metros hasta la meta más cercana por delante entre los trozos que acaban en una meta real; null si no hay. */
    private fun finDeTramoM(posicionM: Double): Double? =
        resultado
            .filter { it.acabaEnMeta }
            .map { origenM + it.hastaM - posicionM }
            .filter { it >= 0.0 }
            .minOrNull()

    companion object {
        /** Metros recorridos sobre lo calculado que piden volver a buscar. */
        const val PASO_RECALCULO_M = 200.0

        /** Retroceso (m) sobre el origen calculado a partir del cual se vuelve a buscar. */
        const val RETROCESO_M = 30.0

        /** En libre se mira hasta aquí por delante (la carretera prevista llega a unos 2 km). */
        const val VENTANA_LIBRE_M = 3_000.0

        /** En guiado, el tope de ruta restante que se examina (más allá no se vería el mapa de conducción). */
        const val VENTANA_GUIADO_M = 20_000.0
    }
}

/** Trozo de [traza] entre [desdeM] y [hastaM] metros desde su inicio (con los puntos intermedios); vacío si no cabe. */
internal fun recortarTraza(
    traza: List<PuntoGeo>,
    desdeM: Double,
    hastaM: Double,
): List<PuntoGeo> {
    val salida = ArrayList<PuntoGeo>()
    var acumuladoM = 0.0
    for (i in 0 until traza.size - 1) {
        val a = traza[i]
        val b = traza[i + 1]
        val largoM = a.distanciaA(b)
        val finM = acumuladoM + largoM
        if (largoM > 0.0 && finM > desdeM && acumuladoM < hastaM) {
            if (salida.isEmpty()) salida += entre(a, b, (desdeM - acumuladoM) / largoM)
            salida += if (finM > hastaM) entre(a, b, (hastaM - acumuladoM) / largoM) else b
        }
        acumuladoM = finM
        if (acumuladoM >= hastaM) break
    }
    return if (salida.size >= 2) salida else emptyList()
}

private fun entre(
    a: PuntoGeo,
    b: PuntoGeo,
    fraccion: Double,
): PuntoGeo {
    val f = fraccion.coerceIn(0.0, 1.0)
    return PuntoGeo(a.lat + f * (b.lat - a.lat), a.lon + f * (b.lon - a.lon))
}
