package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.geo.distanciaASegmento
import app.trombito.rallycopilot.motor.geo.fraccionEnSegmento
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlin.math.hypot

/**
 * Velocidad media de los tramos de radar en modo libre (sin ruta): no hay progreso sobre una ruta, así que se mide
 * la distancia realmente recorrida (suma de los segmentos entre fijaciones válidas) entre el radar de inicio y el de
 * fin, y el tiempo con el reloj de las fijaciones. Da el mismo [EstadoTramo] que [VelocidadMediaTramo] (misma media,
 * mismos avisos «media alta» con su histéresis y cupo).
 *
 * - **Arma** al pasar a [RADIO_M] (30 m) o menos del radar de inicio (el segmento entre dos fijaciones también
 *   cuenta, y la entrada se interpola en el punto más cercano), solo si venía de más lejos (un tramo que ya empezó
 *   al abrir la app no se mide) y si el radar de fin queda por delante: el rumbo de marcha, el del segmento recorrido
 *   o, si es muy corto, el de la fijación, difiere menos de [UMBRAL_FIN_GRADOS] (90°) del rumbo hacia el fin. No se
 *   usa el rumbo de la recta del radar, que en una vía con curvas no es el de la marcha.
 * - **Cierra** al pasar a 30 m o menos del radar de fin: media final con la distancia recorrida.
 * - **Abandona** (sin estado) si la distancia recorrida pasa de [FACTOR_RECORRIDO] (2) veces la recta inicio-fin más
 *   [MARGEN_RECORRIDO_M] (500 m), si se aleja del fin más que la recta más [MARGEN_ALEJARSE_M] (300 m), si pasan más de
 *   [TIEMPO_MAX_MS] (30 min), o con un salto o un hueco de fijaciones (más de [HUECO_MAX_MS] o más de [SALTO_MAX_MS]
 *   metros por segundo implícitos).
 * - [EstadoTramo.tramo] lleva `inicioM = 0` y `finM` = recta inicio-fin (no hay eje de ruta).
 *
 * No es concurrente; una sola medida a la vez (la del primer tramo que se arme). Sin dependencias de Android.
 */
class MediaTramoLibre(
    radares: List<Radar>,
) {
    private class Par(
        val inicio: Radar,
        val fin: Radar,
        val rectaM: Double,
        val rumboFin: Double,
        val limiteKmh: Int?,
    )

    private class Activo(
        val par: Par,
        val medicion: MedicionTramo,
        val inicioMs: Double,
        var recorridoM: Double,
    )

    private class Previa(
        val punto: PuntoGeo,
        val tiempoMs: Long,
    )

    private val pares: List<Par> = emparejar(radares)
    private var previa: Previa? = null
    private var activo: Activo? = null

    /** Estado tras la fijación [fijacion]; `null` si no se mide ningún tramo. */
    fun actualizar(fijacion: Fijacion): EstadoTramo? {
        val anterior = previa
        previa = Previa(fijacion.punto, fijacion.tiempoMs)
        val dt = anterior?.let { fijacion.tiempoMs - it.tiempoMs }
        return when {
            anterior == null || dt == null || dt <= 0L -> {
                // Primera fijación o reloj que no avanza: nada que medir; un reloj atrás invalida la medida.
                if (dt != null && dt < 0L) activo = null
                null
            }

            esSalto(anterior, fijacion, dt) -> {
                activo = null
                null
            }

            else -> {
                activo?.let { medir(it, anterior, fijacion) } ?: armar(anterior, fijacion)
            }
        }
    }

    private fun esSalto(
        anterior: Previa,
        fijacion: Fijacion,
        dt: Long,
    ): Boolean = dt > HUECO_MAX_MS || anterior.punto.distanciaA(fijacion.punto) > SALTO_MAX_MS * dt / MS_POR_S

    private fun armar(
        anterior: Previa,
        fijacion: Fijacion,
    ): EstadoTramo? {
        val plano = ProyeccionPlana(anterior.punto)
        val bx = plano.x(fijacion.punto)
        val by = plano.y(fijacion.punto)
        val largoM = hypot(bx, by)
        val rumbo = if (largoM >= SEGMENTO_MIN_M) Rumbo.deVector(bx, by) else fijacion.rumboGrados
        val candidato =
            pares
                .mapNotNull { par -> entrada(par, plano, bx, by)?.let { par to it } }
                .filter { (par, _) -> rumbo != null && Rumbo.diferencia(rumbo, par.rumboFin) < UMBRAL_FIN_GRADOS }
                .minByOrNull { (_, fraccion) -> fraccion }
        return candidato?.let { (par, fraccion) ->
            val tramo = TramoRadar(par.inicio.tramoId.orEmpty(), 0.0, par.rectaM, par.limiteKmh)
            val inicioMs = anterior.tiempoMs + fraccion * (fijacion.tiempoMs - anterior.tiempoMs)
            val nuevo = Activo(par, MedicionTramo(tramo, inicioMs), inicioMs, (1.0 - fraccion) * largoM)
            activo = nuevo
            nuevo.medicion.medir(nuevo.recorridoM, fijacion.tiempoMs)
        }
    }

    /** Fracción del segmento anterior→actual donde se entra en el radar de inicio; `null` si no se entra ahora. */
    private fun entrada(
        par: Par,
        plano: ProyeccionPlana,
        bx: Double,
        by: Double,
    ): Double? {
        val px = plano.x(par.inicio.punto)
        val py = plano.y(par.inicio.punto)
        val vieneDeLejos = hypot(px, py) > RADIO_M
        val pasaCerca = distanciaASegmento(px, py, 0.0, 0.0, bx, by) <= RADIO_M
        return if (vieneDeLejos && pasaCerca) fraccionEnSegmento(px, py, 0.0, 0.0, bx, by) else null
    }

    private fun medir(
        actual: Activo,
        anterior: Previa,
        fijacion: Fijacion,
    ): EstadoTramo? {
        val plano = ProyeccionPlana(anterior.punto)
        val bx = plano.x(fijacion.punto)
        val by = plano.y(fijacion.punto)
        val largoM = hypot(bx, by)
        val fin = actual.par.fin.punto
        val fx = plano.x(fin)
        val fy = plano.y(fin)
        val llega = distanciaASegmento(fx, fy, 0.0, 0.0, bx, by) <= RADIO_M
        val par = actual.par
        val enMarcha = actual.recorridoM + largoM
        val tiempoTotal = fijacion.tiempoMs - actual.inicioMs
        val distanciaFin = fijacion.punto.distanciaA(fin)
        return when {
            llega -> {
                val fraccion = fraccionEnSegmento(fx, fy, 0.0, 0.0, bx, by)
                activo = null
                val finMs = anterior.tiempoMs + fraccion * (fijacion.tiempoMs - anterior.tiempoMs)
                actual.medicion.cerrar(actual.recorridoM + fraccion * largoM, finMs)
            }

            enMarcha > FACTOR_RECORRIDO * par.rectaM + MARGEN_RECORRIDO_M ||
                distanciaFin > par.rectaM + MARGEN_ALEJARSE_M ||
                tiempoTotal > TIEMPO_MAX_MS -> {
                activo = null
                null
            }

            else -> {
                actual.recorridoM = enMarcha
                actual.medicion.medir(enMarcha, fijacion.tiempoMs)
            }
        }
    }

    private fun emparejar(radares: List<Radar>): List<Par> {
        val fines = radares.filter { it.tipo == TipoRadar.TRAMO_FIN && it.tramoId != null }.associateBy { it.tramoId }
        return radares
            .filter { it.tipo == TipoRadar.TRAMO_INICIO && it.tramoId != null }
            .mapNotNull { inicio ->
                fines[inicio.tramoId]?.let { fin ->
                    val recta = inicio.punto.distanciaA(fin.punto)
                    if (recta <= 0.0) {
                        null
                    } else {
                        Par(inicio, fin, recta, Rumbo.entre(inicio.punto, fin.punto), inicio.limiteKmh ?: fin.limiteKmh)
                    }
                }
            }
    }

    companion object {
        /** Distancia a la que se pasa por un radar de inicio o de fin. */
        const val RADIO_M = 30.0

        /** El radar de fin está «por delante» si el rumbo de marcha difiere menos de esto del rumbo hacia él. */
        const val UMBRAL_FIN_GRADOS = 90.0

        /** Se abandona si lo recorrido pasa de este múltiplo de la recta inicio-fin (más el margen). */
        const val FACTOR_RECORRIDO = 2.0

        /** Margen que se suma al límite de recorrido. */
        const val MARGEN_RECORRIDO_M = 500.0

        /** Margen de alejamiento del fin respecto a la recta inicio-fin. */
        const val MARGEN_ALEJARSE_M = 300.0

        /** Tiempo máximo de una medida. */
        const val TIEMPO_MAX_MS = 30 * 60_000L

        /** Hueco máximo entre fijaciones para fiarse del recorrido. */
        const val HUECO_MAX_MS = 120_000L

        /** Velocidad implícita (m/s) por encima de la cual una fijación es un salto (la del estimador de velocidad). */
        const val SALTO_MAX_MS = 70.0

        private const val SEGMENTO_MIN_M = 3.0
        private const val MS_POR_S = 1000.0
    }
}
