package app.trombito.rallycopilot.ui.mapasoffline

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.ui.comun.ChipIconoWrc
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio

private const val CAPA_APAGADA = 0.5f

/** Una capa de datos y si está en el móvil. */
private data class CapaInstalada(
    val icono: ImageVector,
    @StringRes val nombre: Int,
    val instalada: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CapasInstaladas(
    estado: MapasOfflineUiState,
    instalados: DatosInstalados,
) {
    val relieve = estado.datos.complemento(Complemento.RELIEVE).instalado
    val voz = estado.datos.complemento(Complemento.VOZ_SHARVARD).instalado
    val capas =
        listOf(
            CapaInstalada(Iconos.Mapa, R.string.mapas_capa_mapa, true),
            CapaInstalada(Iconos.Relieve, R.string.mapas_capa_relieve, relieve),
            CapaInstalada(Iconos.Satelite, R.string.mapas_capa_satelite, instalados.conSatelite),
            CapaInstalada(Iconos.Capas, R.string.mapas_capa_detalle, estado.detalle.bytes > 0),
            CapaInstalada(Iconos.Microfono, R.string.mapas_capa_voz, voz),
        )
    FlowRow(
        modifier = Modifier.testTag(ETIQUETA_CAPAS),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
        verticalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        capas.forEach { capa ->
            val estadoCapa =
                stringResource(if (capa.instalada) R.string.mapas_capa_instalada else R.string.mapas_capa_falta)
            ChipIconoWrc(
                icono = capa.icono,
                texto = stringResource(capa.nombre),
                seleccionado = capa.instalada,
                modifier =
                    Modifier
                        .alpha(if (capa.instalada) 1f else CAPA_APAGADA)
                        .semantics { stateDescription = estadoCapa },
            )
        }
    }
}
