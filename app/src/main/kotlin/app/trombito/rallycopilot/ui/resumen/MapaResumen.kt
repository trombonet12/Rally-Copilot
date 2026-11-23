package app.trombito.rallycopilot.ui.resumen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.ui.analisis.ContenidoMapa
import app.trombito.rallycopilot.ui.analisis.MapaMapLibre
import app.trombito.rallycopilot.ui.analisis.OpcionesMapa

/** El mapa del resumen: recibe el estilo offline y el resumen (segmentos y escala de velocidad). */
typealias MapaDelResumen = @Composable (estiloJson: String, resumen: ResumenViaje, modifier: Modifier) -> Unit

/**
 * El mapa real: MapLibre con la traza del viaje coloreada por velocidad (el mismo motor y degradado que el análisis de
 * una pasada). Sin gestos: va dentro de una lista que se desplaza y no debe robarle el arrastre.
 */
val MapaResumenReal: MapaDelResumen = { estiloJson, resumen, modificador ->
    val contenido = remember(resumen) { ContenidoMapa(resumen.segmentos, resumen.escala, null) }
    val opciones =
        remember { OpcionesMapa(interactivo = false, descripcion = R.string.resumen_mapa_descripcion) }
    MapaMapLibre(estiloJson, contenido, opciones, modificador)
}
