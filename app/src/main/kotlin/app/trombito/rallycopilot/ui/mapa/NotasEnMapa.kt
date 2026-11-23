package app.trombito.rallycopilot.ui.mapa

import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso

/** Metros de la hoja por delante de la posición cuyas curvas y avisos se pintan en el mapa para echar un vistazo. */
internal const val VENTANA_NOTAS_MAPA_M = 1_500.0

/** Metros por detrás de la posición que aún se pintan (lo que se acaba de pasar). */
private const val ATRAS_NOTAS_MAPA_M = 30.0

/** Los avisos unidos a una nota caen hasta 20 m antes de ella: margen para no cortar la hoja antes de tiempo. */
private const val MARGEN_CORTE_M = 50.0

/** Número de una curva en su ápice; [grado] `null` es una horquilla. */
@Immutable
data class CurvaEnMapa(
    val posicionM: Double,
    val lat: Double,
    val lon: Double,
    val grado: Int?,
    val izquierda: Boolean,
)

/** Mini icono de un aviso de la hoja en su posición. */
@Immutable
data class AvisoEnMapa(
    val posicionM: Double,
    val lat: Double,
    val lon: Double,
    val tipo: TipoAviso,
)

/** Lo que el mapa pinta de la hoja vigente: los números de las curvas y los avisos que vienen. */
@Immutable
data class NotasEnMapa(
    val curvas: List<CurvaEnMapa> = emptyList(),
    val avisos: List<AvisoEnMapa> = emptyList(),
)

/** Ápice de una curva: el punto medio entre su inicio y su final (metros de la hoja). */
internal fun apiceM(nota: Nota): Double = (nota.posicionM + nota.finM) / 2.0

/**
 * Las curvas (en su ápice) y los avisos de la hoja que caen entre [ATRAS_NOTAS_MAPA_M] por detrás de la posición y
 * [ventanaM] por delante. Vacío si no hay hoja o esta no sabe dónde cae cada posición.
 */
internal fun notasEnMapa(
    notas: NotasEnCurso?,
    ventanaM: Double = VENTANA_NOTAS_MAPA_M,
): NotasEnMapa {
    val puntoEn = notas?.puntoEn ?: return NotasEnMapa()
    val rango = (notas.posicionM - ATRAS_NOTAS_MAPA_M)..(notas.posicionM + ventanaM)
    val curvas = ArrayList<CurvaEnMapa>()
    val avisos = ArrayList<AvisoEnMapa>()

    fun anadir(aviso: Aviso) {
        if (aviso.posicionM !in rango) return
        val punto = puntoEn(aviso.posicionM)
        avisos += AvisoEnMapa(aviso.posicionM, punto.lat, punto.lon, aviso.tipo)
    }
    for (elemento in notas.hoja.elementos) {
        if (elemento.posicionM > rango.endInclusive + MARGEN_CORTE_M) break
        when (elemento) {
            is ElementoHoja.DeAviso -> {
                anadir(elemento.aviso)
            }

            is ElementoHoja.DeNota -> {
                val apice = apiceM(elemento.nota)
                if (apice in rango) {
                    val punto = puntoEn(apice)
                    curvas +=
                        CurvaEnMapa(
                            posicionM = elemento.nota.posicionM,
                            lat = punto.lat,
                            lon = punto.lon,
                            grado = elemento.nota.grado,
                            izquierda = elemento.nota.direccion == Direccion.IZQUIERDA,
                        )
                }
                (elemento.avisosUnidos + listOfNotNull(elemento.sobrePuente, elemento.sobreRasante)).forEach(::anadir)
            }
        }
    }
    return NotasEnMapa(curvas, avisos)
}
