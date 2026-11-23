package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.PlacaCarretera
import app.trombito.rallycopilot.ui.comun.TamanoPlaca
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.CifraPequena
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Medidas
import java.time.format.TextStyle
import java.util.Locale

const val ETIQUETA_TEMPORADA_MES = "temporada-mes"
const val ETIQUETA_TEMPORADA_COMPARTIR = "temporada-compartir"
const val ETIQUETA_TEMPORADA_COMPARTIR_FALLO = "temporada-compartir-fallo"

private val ES: Locale = Locale.forLanguageTag("es-ES")

/** «noviembre 2026»: el mes en minúsculas y el año. */
private fun nombreMes(resumen: ResumenMes): String =
    "${resumen.mes.month.getDisplayName(TextStyle.FULL, ES)} ${resumen.mes.year}"

/**
 * Resumen del mes: kilómetros, viajes, carreteras estrenadas (solo con referencia de placa), hitos, récords y nivel. El
 * botón «Compartir» ([onCompartir]) lo convierte en una imagen sin mapa ni lugares; solo sale con algún viaje en el
 * mes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TarjetaResumenMes(
    resumen: ResumenMes,
    onCompartir: (ResumenMes) -> Unit,
    falloCompartir: Boolean,
) {
    val mes = nombreMes(resumen)
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_MES), relleno = Espacio.m, biselada = true) {
        EtiquetaConIcono(Iconos.Cronometro, stringResource(R.string.perfil_mes_titulo, mes))
        if (resumen.viajes == 0) {
            Text(
                text = stringResource(R.string.perfil_mes_vacio),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CifrasDelMes(resumen)
            val refs = resumen.refsNuevas()
            if (refs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Espacio.s),
                    verticalArrangement = Arrangement.spacedBy(Espacio.s),
                ) {
                    refs.forEach { PlacaCarretera(it, tamano = TamanoPlaca.PEQUENA) }
                }
            }
            BotonCompartir(mes) { onCompartir(resumen) }
            Text(
                text = stringResource(R.string.perfil_mes_privacidad),
                style = Ayuda,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (falloCompartir) {
                Text(
                    text = stringResource(R.string.perfil_mes_compartir_fallo),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(ETIQUETA_TEMPORADA_COMPARTIR_FALLO),
                )
            }
        }
    }
}

@Composable
private fun CifrasDelMes(resumen: ResumenMes) {
    val nuevas = resumen.carreterasNuevas.size
    val hitos = resumen.hitosConseguidos.size
    val records = resumen.records.size
    val cuentas =
        listOf(
            pluralStringResource(R.plurals.perfil_mes_carreteras_nuevas, nuevas, nuevas),
            pluralStringResource(R.plurals.perfil_mes_hitos, hitos, hitos),
            pluralStringResource(R.plurals.perfil_mes_records, records, records),
        ).joinToString(" · ")
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            text =
                stringResource(
                    R.string.perfil_mes_cifras,
                    FormatoEs.kilometros(resumen.km),
                    pluralStringResource(R.plurals.perfil_viajes, resumen.viajes, resumen.viajes),
                ),
            style = CifraPequena,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = cuentas, style = Cuerpo, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = stringResource(R.string.perfil_mes_nivel, resumen.nivel.nivel.nombre),
            style = Cuerpo.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BotonCompartir(
    mes: String,
    onClick: () -> Unit,
) {
    val descripcion = stringResource(R.string.perfil_mes_compartir_accion, mes)
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Medidas.zonaTactil)
                .testTag(ETIQUETA_TEMPORADA_COMPARTIR)
                .semantics { contentDescription = descripcion },
    ) {
        Icon(IconoCompartir, contentDescription = null)
        Text(
            text = stringResource(R.string.perfil_mes_compartir),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Espacio.s),
        )
    }
}
