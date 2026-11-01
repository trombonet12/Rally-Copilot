package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.velocidad.Fijacion

/** Conducción simulada y determinista sobre una polilínea: sin azar ni reloj real. */
object Conduccion {
    /** Punto de la polilínea a [distanciaM] metros del inicio (acotado a los extremos). */
    fun puntoA(
        traza: List<PuntoGeo>,
        distanciaM: Double,
    ): PuntoGeo {
        var resto = distanciaM.coerceAtLeast(0.0)
        var resultado = traza.last()
        for (i in 0 until traza.lastIndex) {
            val largo = traza[i].distanciaA(traza[i + 1])
            if (resto <= largo) {
                val t = if (largo == 0.0) 0.0 else resto / largo
                resultado =
                    PuntoGeo(
                        traza[i].lat + t * (traza[i + 1].lat - traza[i].lat),
                        traza[i].lon + t * (traza[i + 1].lon - traza[i].lon),
                    )
                break
            }
            resto -= largo
        }
        return resultado
    }

    /** Fijación en [punto] sin velocidad ni rumbo (el detector los deduce de las posiciones). */
    fun fijacion(
        punto: PuntoGeo,
        tMs: Long,
    ) = Fijacion(punto, tMs, null, null, null)

    /**
     * Fijaciones a velocidad constante [kmh] entre [desdeM] y [hastaM] de la polilínea, una cada [periodoMs] y una
     * última exacta en [hastaM]. El instante [t0Ms] es el de la primera.
     */
    @Suppress("LongParameterList")
    fun muestrear(
        traza: List<PuntoGeo>,
        kmh: Double,
        t0Ms: Long = 0,
        periodoMs: Long = 1000,
        desdeM: Double = 0.0,
        hastaM: Double = traza.longitudMetros(),
    ): List<Fijacion> {
        val v = kmh / 3.6
        val fijaciones = mutableListOf<Fijacion>()
        var k = 0
        while (desdeM + v * k * periodoMs / 1000.0 < hastaM) {
            val d = desdeM + v * k * periodoMs / 1000.0
            fijaciones += fijacion(puntoA(traza, d), t0Ms + k * periodoMs)
            k++
        }
        val tFinal = t0Ms + Math.round((hastaM - desdeM) / v * 1000.0)
        fijaciones += fijacion(puntoA(traza, hastaM), tFinal)
        return fijaciones
    }

    /** Subpolilínea entre [desdeM] y [hastaM] metros (con los vértices intermedios). */
    fun subtraza(
        traza: List<PuntoGeo>,
        desdeM: Double,
        hastaM: Double,
    ): List<PuntoGeo> {
        val resultado = mutableListOf(puntoA(traza, desdeM))
        var acumulado = 0.0
        for (i in 0 until traza.lastIndex) {
            acumulado += traza[i].distanciaA(traza[i + 1])
            if (acumulado > desdeM && acumulado < hastaM) resultado += traza[i + 1]
        }
        resultado += puntoA(traza, hastaM)
        return resultado
    }

    /** Desplaza [punto] [norteM] y [esteM] metros (plano local; de sobra para pocos cientos de metros). */
    fun desplazar(
        punto: PuntoGeo,
        norteM: Double,
        esteM: Double,
    ): PuntoGeo {
        val porGrado = Math.toRadians(PuntoGeo.RADIO_TIERRA_M)
        return PuntoGeo(
            punto.lat + norteM / porGrado,
            punto.lon + esteM / (porGrado * Math.cos(Math.toRadians(punto.lat))),
        )
    }
}
