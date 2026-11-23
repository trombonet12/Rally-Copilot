package app.trombito.rallycopilot.ui.miscarreteras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.OpcionSegmento
import app.trombito.rallycopilot.ui.comun.SelectorSegmentadoWrc
import app.trombito.rallycopilot.ui.mapa.ModoCalor
import app.trombito.rallycopilot.ui.theme.CarbonOpaco
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios

/** Etiqueta del recuadro del mapa de MapLibre (no el dibujo sin base, que lleva [ETIQUETA_CARRETERAS_MAPA]). */
const val ETIQUETA_CARRETERAS_MAPA_BASE = "carreteras-mapa-base"

/** Etiqueta del conmutador uso / velocidad del mapa de calor. */
const val ETIQUETA_CARRETERAS_MODO = "carreteras-modo"

/** Lo que enseña [ZonaMapaCalor]: el mapa de calor con su conmutador y su leyenda. */
internal class DatosZonaMapa(
    val modo: ModoCalor,
    val trazos: List<TrazoCarretera>,
    val geoJson: String,
    val esquinas: List<PuntoGeo>,
    val estiloMapaJson: String?,
    val hayVias: Boolean,
)

/**
 * El mapa de calor de las carreteras conducidas con el conmutador uso / velocidad debajo y la leyenda del modo. Con
 * estilo de mapa instalado y vías conducidas se pinta con [mapa] (MapLibre sobre el mapa offline); si no, con el dibujo
 * sin mapa base, que además avisa de por qué no hay nada que enseñar.
 */
@Composable
internal fun ZonaMapaCalor(
    datos: DatosZonaMapa,
    onModo: (ModoCalor) -> Unit,
    mapa: MapaDelCalor,
    modifier: Modifier = Modifier,
) {
    val estilo = datos.estiloMapaJson
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        if (estilo != null && datos.trazos.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ALTO_MAPA_CALOR)
                    .clip(RoundedCornerShape(Radios.tarjeta))
                    .background(CarbonOpaco)
                    .testTag(ETIQUETA_CARRETERAS_MAPA_BASE),
            ) {
                mapa(estilo, datos.geoJson, datos.esquinas, Modifier.fillMaxSize())
            }
        } else {
            val aviso =
                if (datos.hayVias) R.string.carreteras_mapa_sin_coincidencias else R.string.carreteras_mapa_sin_vias
            MapaCalorCarreteras(datos.trazos, stringResource(aviso), modo = datos.modo)
        }
        if (datos.trazos.isNotEmpty()) {
            SelectorModoCalor(datos.modo, onModo)
            LeyendaCalor(datos.modo)
        }
    }
}

@Composable
private fun SelectorModoCalor(
    modo: ModoCalor,
    onModo: (ModoCalor) -> Unit,
) {
    val opciones =
        listOf(
            OpcionSegmento(ModoCalor.USO, stringResource(R.string.carreteras_modo_uso), Iconos.Calor),
            OpcionSegmento(ModoCalor.VELOCIDAD, stringResource(R.string.carreteras_modo_velocidad), Iconos.Velocimetro),
        )
    SelectorSegmentadoWrc(
        opciones = opciones,
        seleccionada = modo,
        onSeleccion = onModo,
        modifier = Modifier.testTag(ETIQUETA_CARRETERAS_MODO),
    )
}
