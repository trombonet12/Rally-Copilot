package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa

/** Lo que hay descargado, para la fila de Mapas offline de Ajustes > Datos. */
data class ResumenMapas(
    val instalado: Boolean,
    val satelite: Boolean = false,
    val relieve: Boolean = false,
    val vozSharvard: Boolean = false,
) {
    companion object {
        /** El resumen de los datos; null mientras se siembran los del APK (aún no se sabe qué hay). */
        fun de(datos: EstadoDatosMapa): ResumenMapas? {
            val instalados = datos.instalados
            return when {
                instalados != null -> {
                    ResumenMapas(true, instalados.conSatelite, instalados.conRelieve, instalados.conVozSharvard)
                }

                datos.preparando -> {
                    null
                }

                else -> {
                    ResumenMapas(instalado = false)
                }
            }
        }
    }
}

/** El detalle de la fila de Mapas offline según lo que haya: base, con o sin satélite y los complementos. */
@Composable
internal fun textoMapasOffline(resumen: ResumenMapas): String {
    val sinDatos = stringResource(R.string.ajustes_mapas_offline_sin_datos)
    val base =
        stringResource(
            if (resumen.satelite) {
                R.string.ajustes_mapas_offline_con_satelite
            } else {
                R.string.ajustes_mapas_offline_sin_satelite
            },
        )
    val extras =
        listOfNotNull(
            stringResource(R.string.ajustes_mapas_offline_relieve).takeIf { resumen.relieve },
            stringResource(R.string.ajustes_mapas_offline_voz).takeIf { resumen.vozSharvard },
        )
    val unidos = extras.joinToString(stringResource(R.string.ajustes_mapas_offline_y))
    val ademas = stringResource(R.string.ajustes_mapas_offline_ademas, unidos)
    return when {
        !resumen.instalado -> sinDatos
        extras.isEmpty() -> base
        else -> "$base $ademas"
    }
}
