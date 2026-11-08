package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.ruta.TipoManiobra

/**
 * Qué indicaciones quiere oír (y ver) el piloto; todo activo es el comportamiento de siempre.
 *
 * - [avisarCruces]: el aviso «cruce» de la hoja de notas. Girar en un cruce de la ruta es una maniobra de navegación y
 *   se dice siempre.
 * - [avisarPuentes]: el aviso «puente» de la hoja de notas (los puentes y los pasos sobre estructuras).
 * - [avisarSalidasAutopista]: las maniobras de salida de autovía/autopista y de incorporación.
 * - [avisarRotondas]: el aviso «rotonda» de la hoja y las maniobras de entrada y salida de rotonda.
 */
data class FiltroIndicaciones(
    val avisarCruces: Boolean = true,
    val avisarSalidasAutopista: Boolean = true,
    val avisarRotondas: Boolean = true,
    val avisarPuentes: Boolean = true,
) {
    /** `true` si no se calla nada. */
    val todoActivo: Boolean get() = avisarCruces && avisarSalidasAutopista && avisarRotondas && avisarPuentes

    /** `false` si el aviso de la hoja está desactivado. */
    fun permite(tipo: TipoAviso): Boolean =
        when (tipo) {
            TipoAviso.CRUCE -> avisarCruces
            TipoAviso.ROTONDA -> avisarRotondas
            TipoAviso.PUENTE -> avisarPuentes
            else -> true
        }

    /** `false` si la maniobra de navegación pertenece a una familia desactivada. */
    fun permite(tipo: TipoManiobra): Boolean =
        when (tipo) {
            TipoManiobra.ROTONDA_ENTRADA, TipoManiobra.ROTONDA_SALIDA -> {
                avisarRotondas
            }

            TipoManiobra.SALIDA_AUTOVIA_DERECHA,
            TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA,
            TipoManiobra.INCORPORACION,
            -> {
                avisarSalidasAutopista
            }

            else -> {
                true
            }
        }

    /**
     * La [hoja] sin los avisos desactivados, sueltos o unidos a una curva (la curva y el resto de su frase se
     * conservan). Con todo activo (o sin nada que quitar) devuelve la misma instancia.
     */
    fun aplicar(hoja: HojaNotas): HojaNotas {
        if (todoActivo || hoja.elementos.none { afectado(it) }) return hoja
        return HojaNotas(
            hoja.elementos.mapNotNull { elemento ->
                when (elemento) {
                    is ElementoHoja.DeAviso -> {
                        elemento.takeIf { permite(it.aviso.tipo) }
                    }

                    is ElementoHoja.DeNota -> {
                        if (afectado(elemento)) {
                            elemento.copy(avisosUnidos = elemento.avisosUnidos.filter { permite(it.tipo) })
                        } else {
                            elemento
                        }
                    }
                }
            },
        )
    }

    private fun afectado(elemento: ElementoHoja): Boolean =
        when (elemento) {
            is ElementoHoja.DeAviso -> !permite(elemento.aviso.tipo)
            is ElementoHoja.DeNota -> elemento.avisosUnidos.any { !permite(it.tipo) }
        }
}

/**
 * Recuerda la última hoja filtrada: la hoja y el filtro cambian muy rara vez y se consulta una vez por fijación, así
 * que no se recorre la hoja entera cada segundo. Uno por consumidor; no es concurrente.
 */
class HojaFiltrada {
    private var origen: HojaNotas? = null
    private var filtroUsado: FiltroIndicaciones? = null
    private var resultado: HojaNotas? = null

    /** [hoja] sin lo que calla [filtro]; la misma instancia mientras no cambien ninguno de los dos. */
    fun de(
        hoja: HojaNotas,
        filtro: FiltroIndicaciones,
    ): HojaNotas {
        val guardada = resultado
        if (guardada != null && origen === hoja && filtroUsado == filtro) return guardada
        return filtro.aplicar(hoja).also {
            origen = hoja
            filtroUsado = filtro
            resultado = it
        }
    }
}
