package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.ui.mapa.NotasEnMapa
import app.trombito.rallycopilot.ui.navegacion.notas.iconoAviso

private const val MINUTO_MS = 60_000L

/**
 * Los elementos del mapa que se pueden tocar y que el presentador necesita para montar el bocadillo de una
 * [SeleccionMapa]: los que llegan de fuera de [AlertasViewModel] (curvas y avisos de la hoja, banderas de los tramos)
 * y los suyos, con los que se compone el resto.
 */
internal data class ElementosTocables(
    val incidencias: List<MarcadorIncidencia> = emptyList(),
    val radares: RadaresUi = RadaresUi(),
    val banderas: List<BanderaMapa> = emptyList(),
    val notas: NotasEnMapa = NotasEnMapa(),
    /** Cuánto hace que se descargaron las incidencias; null si no hay copia. */
    val antiguedadIncidenciasMs: Long? = null,
)

/**
 * Bocadillo anclado al elemento que el usuario ha tocado. null si el elemento ya no existe (se quitó del mapa, se salió
 * de la ventana de notas…): la selección sobrevive al refresco de los marcadores mientras siga ahí. La antigüedad de
 * las incidencias va al minuto: el bocadillo (y su imagen) no cambia con cada fijación.
 */
internal fun bocadilloDeSeleccion(
    seleccion: SeleccionMapa,
    elementos: ElementosTocables,
): BocadilloMapa? =
    when (seleccion) {
        is SeleccionMapa.Incidencia -> {
            val minutos = elementos.antiguedadIncidenciasMs?.let { it / MINUTO_MS * MINUTO_MS }
            elementos.incidencias
                .firstOrNull { it.id == seleccion.id }
                ?.let { bocadilloDeIncidencia(it, completo = true, antiguedadMs = minutos) }
        }

        is SeleccionMapa.Radar -> {
            elementos.radares.marcadores
                .firstOrNull { it.id == seleccion.id }
                ?.let { bocadilloDeRadarTocado(it) }
        }

        is SeleccionMapa.TramoRadar -> {
            bocadilloDeTramoRadarTocado(seleccion, elementos.radares)
        }

        is SeleccionMapa.Bandera -> {
            elementos.banderas
                .firstOrNull { it.tramoId == seleccion.tramoId && it.esMeta == seleccion.esMeta }
                ?.let {
                    val contenido = ContenidoBocadillo.Bandera(it.esMeta, it.nombre, it.mejorMs)
                    BocadilloMapa("seleccion-${seleccion.clave}", it.lat, it.lon, contenido)
                }
        }

        is SeleccionMapa.Nota -> {
            bocadilloDeNota(seleccion, elementos.notas)
        }
    }

private fun bocadilloDeRadarTocado(marcador: MarcadorRadar): BocadilloMapa {
    val contenido = ContenidoBocadillo.RadarTocado(marcador.clase, marcador.limiteKmh, marcador.rumboGrados)
    return BocadilloMapa("seleccion-${SeleccionMapa.Radar(marcador.id).clave}", marcador.lat, marcador.lon, contenido)
}

private fun bocadilloDeTramoRadarTocado(
    seleccion: SeleccionMapa.TramoRadar,
    radares: RadaresUi,
): BocadilloMapa? {
    val tramo = radares.tramos.firstOrNull { it.id == seleccion.id }
    val ancla = seleccion.ancla ?: tramo?.puntos?.firstOrNull()
    return if (tramo == null || ancla == null) {
        null
    } else {
        val contenido = ContenidoBocadillo.TramoRadarTocado(tramo.limiteKmh, tramo.longitudM)
        BocadilloMapa("seleccion-${seleccion.clave}", ancla.lat, ancla.lon, contenido)
    }
}

/** La curva (antes) o el aviso de la hoja en esa posición; sus frases y el icono de roadbook. */
private fun bocadilloDeNota(
    seleccion: SeleccionMapa.Nota,
    notas: NotasEnMapa,
): BocadilloMapa? {
    val id = "seleccion-${seleccion.clave}"
    val curva = notas.curvas.firstOrNull { it.posicionM == seleccion.posicionM }
    val aviso = notas.avisos.firstOrNull { it.posicionM == seleccion.posicionM }
    return when {
        curva != null -> {
            BocadilloMapa(id, curva.lat, curva.lon, ContenidoBocadillo.NotaDelMapa(curva.frase, curva.icono))
        }

        aviso != null -> {
            val contenido = ContenidoBocadillo.NotaDelMapa(aviso.frase, iconoAviso(aviso.tipo))
            BocadilloMapa(id, aviso.lat, aviso.lon, contenido)
        }

        else -> {
            null
        }
    }
}
