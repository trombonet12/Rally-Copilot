package app.trombito.rallycopilot.ui.mapa

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.navegacion.notas.iconoCurva

/** Metros de la hoja por delante de la posición cuyas curvas y avisos se pintan en el mapa para echar un vistazo. */
internal const val VENTANA_NOTAS_MAPA_M = 1_500.0

/** Metros por detrás de la posición que aún se pintan (lo que se acaba de pasar). */
private const val ATRAS_NOTAS_MAPA_M = 30.0

/** Los avisos unidos a una nota caen hasta 20 m antes de ella: margen para no cortar la hoja antes de tiempo. */
private const val MARGEN_CORTE_M = 50.0

/**
 * Número de una curva en su ápice; [grado] `null` es una horquilla. [frase] es lo que dice la nota («Izquierda 3
 * larga · cuidado») y [icono] su icono de roadbook: lo que enseña su bocadillo al tocarla.
 */
@Immutable
data class CurvaEnMapa(
    val posicionM: Double,
    val lat: Double,
    val lon: Double,
    val grado: Int?,
    val izquierda: Boolean,
    val frase: String = "",
    @DrawableRes val icono: Int = 0,
)

/** Mini icono de un aviso de la hoja en su posición; [frase] es su nombre («Estrecha», «Rasante»). */
@Immutable
data class AvisoEnMapa(
    val posicionM: Double,
    val lat: Double,
    val lon: Double,
    val tipo: TipoAviso,
    val frase: String = fraseAviso(tipo),
)

/** El nombre de un aviso con la primera en mayúscula: «Estrecha», «Cuidado, resalto». */
internal fun fraseAviso(tipo: TipoAviso): String = tipo.frase.replaceFirstChar { it.uppercase() }

/**
 * La frase de una curva para su bocadillo: el giro con el grado en cifra («Izquierda 3 larga») y, tras « · », lo que
 * lleva: «cuidado», los avisos unidos («rasante») y «sobre puente» / «sobre rasante». Con texto libre de Joan, ese
 * texto tal cual.
 */
internal fun fraseCurva(elemento: ElementoHoja.DeNota): String {
    val nota = elemento.nota
    val propio = nota.textoPropio
    val base = propio ?: giroConCifra(nota)
    val cuidado = (nota.cuidado && propio == null) || elemento.avisosUnidos.any { it.tipo.esCuidado }
    val extras =
        listOfNotNull(if (cuidado) "cuidado" else null) +
            elemento.avisosUnidos.map { it.tipo.palabra }.filter { it.isNotEmpty() } +
            listOfNotNull(
                if (elemento.sobrePuente != null) "sobre puente" else null,
                if (elemento.sobreRasante != null) "sobre rasante" else null,
            )
    return (listOf(base.replaceFirstChar { it.uppercase() }) + extras.distinct()).joinToString(" · ")
}

/** «izquierda 3 larga»: la frase del motor sin «cuidado» ni distancia, con el grado en cifra (no la escuadra). */
private fun giroConCifra(nota: Nota): String {
    val frase = FrasesNota.frase(nota.copy(cuidado = false, distanciaSiguienteM = null))
    val grado = nota.grado
    if (grado == null || Modificador.ESCUADRA in nota.modificadores) return frase
    val direccion = if (nota.direccion == Direccion.IZQUIERDA) "izquierda" else "derecha"
    return frase.replace("$direccion ${FrasesNota.enPalabras(grado)}", "$direccion $grado")
}

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
                            frase = fraseCurva(elemento),
                            icono = iconoCurva(elemento.nota, simplificada = false),
                        )
                }
                (elemento.avisosUnidos + listOfNotNull(elemento.sobrePuente, elemento.sobreRasante)).forEach(::anadir)
            }
        }
    }
    return NotasEnMapa(curvas, avisos)
}
