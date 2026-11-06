package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.ui.comun.BarraSuperior
import app.trombito.rallycopilot.ui.comun.FondoWrc
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.Radios
import app.trombito.rallycopilot.ui.theme.Rotulo

const val ETIQUETA_AYUDA_NOTAS_CURVAS = "ayuda-notas-curvas"
const val ETIQUETA_AYUDA_NOTAS_FORMAS = "ayuda-notas-formas"
const val ETIQUETA_AYUDA_NOTAS_MODIFICADORES = "ayuda-notas-modificadores"
const val ETIQUETA_AYUDA_NOTAS_AVISOS = "ayuda-notas-avisos"

private val ICONO_NOTA = 40.dp
private val ICONO_AVISO = 32.dp
private val ANCHO_GRADO = 28.dp

private class FilaNota(
    @DrawableRes val icono: Int,
    @StringRes val titulo: Int,
    @StringRes val detalle: Int,
    val grado: Int? = null,
)

private val CURVAS =
    listOf(
        FilaNota(R.drawable.ic_nota_der_6, R.string.ayuda_grado_6, R.string.ayuda_grado_6, 6),
        FilaNota(R.drawable.ic_nota_der_5, R.string.ayuda_grado_5, R.string.ayuda_grado_5, 5),
        FilaNota(R.drawable.ic_nota_der_4, R.string.ayuda_grado_4, R.string.ayuda_grado_4, 4),
        FilaNota(R.drawable.ic_nota_der_3, R.string.ayuda_grado_3, R.string.ayuda_grado_3, 3),
        FilaNota(R.drawable.ic_nota_der_2, R.string.ayuda_grado_2, R.string.ayuda_grado_2, 2),
        FilaNota(R.drawable.ic_nota_der_1, R.string.ayuda_grado_1, R.string.ayuda_grado_1, 1),
    )

private val FORMAS =
    listOf(
        FilaNota(R.drawable.ic_nota_der_3, R.string.ayuda_escuadra, R.string.ayuda_escuadra_detalle),
        FilaNota(R.drawable.ic_nota_horquilla_der, R.string.ayuda_horquilla, R.string.ayuda_horquilla_detalle),
    )

private val MODIFICADORES =
    listOf(
        R.string.ayuda_larga to R.string.ayuda_larga_detalle,
        R.string.ayuda_muy_larga to R.string.ayuda_muy_larga_detalle,
        R.string.ayuda_cierra to R.string.ayuda_cierra_detalle,
        R.string.ayuda_abre to R.string.ayuda_abre_detalle,
    )

private val AVISOS =
    listOf(
        R.drawable.ic_nota_puente to R.string.ayuda_aviso_puente,
        R.drawable.ic_nota_tunel to R.string.ayuda_aviso_tunel,
        R.drawable.ic_nota_rotonda to R.string.ayuda_aviso_rotonda,
        R.drawable.ic_nota_estrecha to R.string.ayuda_aviso_estrecha,
        R.drawable.ic_nota_bache to R.string.ayuda_aviso_bache,
        R.drawable.ic_nota_baden to R.string.ayuda_aviso_baden,
        R.drawable.ic_nota_paso_nivel to R.string.ayuda_aviso_paso_nivel,
        R.drawable.ic_nota_stop to R.string.ayuda_aviso_stop,
        R.drawable.ic_nota_ceda to R.string.ayuda_aviso_ceda,
        R.drawable.ic_nota_semaforo to R.string.ayuda_aviso_semaforo,
        R.drawable.ic_nota_cruce to R.string.ayuda_aviso_cruce,
        R.drawable.ic_nota_rasante to R.string.ayuda_aviso_rasante,
        R.drawable.ic_nota_salto to R.string.ayuda_aviso_salto,
        R.drawable.ic_nota_compresion to R.string.ayuda_aviso_compresion,
        R.drawable.ic_nota_cuidado to R.string.ayuda_aviso_cuidado,
    )

/**
 * «Qué significan las notas»: la tabla con el icono de cada grado del 6 al 1, la escuadra, la horquilla, los
 * modificadores (larga, muy larga, cierra, abre) y los avisos del camino.
 */
@Composable
fun AyudaNotasScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FondoWrc(modifier) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BarraSuperior(stringResource(R.string.ayuda_notas_titulo), onVolver)
            Column(
                modifier =
                    Modifier
                        .verticalScroll(
                            rememberScrollState(),
                        ).padding(horizontal = Espacio.l)
                        .padding(bottom = Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                TarjetaAyuda(stringResource(R.string.ayuda_notas_curvas), ETIQUETA_AYUDA_NOTAS_CURVAS) {
                    CURVAS.forEach { FilaDeNota(it) }
                }
                TarjetaAyuda(stringResource(R.string.ayuda_notas_formas), ETIQUETA_AYUDA_NOTAS_FORMAS) {
                    FORMAS.forEach { FilaDeNota(it) }
                }
                TarjetaAyuda(stringResource(R.string.ayuda_notas_modificadores), ETIQUETA_AYUDA_NOTAS_MODIFICADORES) {
                    MODIFICADORES.forEach { (nombre, detalle) -> FilaModificador(nombre, detalle) }
                }
                TarjetaAyuda(stringResource(R.string.ayuda_notas_avisos), ETIQUETA_AYUDA_NOTAS_AVISOS) {
                    AVISOS.chunked(2).forEach { par ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
                            par.forEach { (icono, nombre) -> Aviso(icono, nombre, Modifier.weight(1f)) }
                            if (par.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Icono de la nota, su grado (si lo tiene) y qué significa; TalkBack lo lee de una vez. */
@Composable
private fun FilaDeNota(fila: FilaNota) {
    val titulo = stringResource(fila.titulo)
    val detalle = stringResource(fila.detalle)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Icon(
            painterResource(fila.icono),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICONO_NOTA),
        )
        if (fila.grado != null) {
            Text(
                text = fila.grado.toString(),
                style = Rotulo,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.defaultMinSize(minWidth = ANCHO_GRADO),
            )
            Text(detalle, style = Cuerpo, color = MaterialTheme.colorScheme.onSurface)
        } else {
            Column {
                Text(titulo, style = Cuerpo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(detalle, style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** El modificador como una píldora con borde ámbar y su explicación al lado. */
@Composable
private fun FilaModificador(
    @StringRes nombre: Int,
    @StringRes detalle: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Surface(
            shape = Radios.formaPildora,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.defaultMinSize(minWidth = 104.dp),
        ) {
            Text(
                text = stringResource(nombre).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Espacio.m, vertical = Espacio.xs),
            )
        }
        Text(stringResource(detalle), style = Ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Aviso(
    @DrawableRes icono: Int,
    @StringRes nombre: Int,
    modifier: Modifier = Modifier,
) {
    val texto = stringResource(nombre)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Icon(
            painterResource(icono),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICONO_AVISO),
        )
        Text(texto, style = Ayuda, color = MaterialTheme.colorScheme.onSurface)
    }
}
