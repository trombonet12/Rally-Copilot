package app.trombito.rallycopilot.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.perfil.TemporadaPiloto
import app.trombito.rallycopilot.motor.analisis.KmSemana
import app.trombito.rallycopilot.motor.analisis.NivelProgreso
import app.trombito.rallycopilot.ui.comun.EstadoVacioWrc
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.ListaEsqueletoWrc
import app.trombito.rallycopilot.ui.comun.MascotaPose
import app.trombito.rallycopilot.ui.comun.PoseMascota
import app.trombito.rallycopilot.ui.comun.TarjetaWrc
import app.trombito.rallycopilot.ui.comun.VarianteVacio
import app.trombito.rallycopilot.ui.mistramos.formatearFechaCorta
import app.trombito.rallycopilot.ui.theme.Ayuda
import app.trombito.rallycopilot.ui.theme.CifraPequena
import app.trombito.rallycopilot.ui.theme.Cuerpo
import app.trombito.rallycopilot.ui.theme.Espacio
import java.time.ZoneId

const val ETIQUETA_TEMPORADA_CARGANDO = "temporada-cargando"
const val ETIQUETA_TEMPORADA_ERROR = "temporada-error"
const val ETIQUETA_TEMPORADA_FICHA = "temporada-ficha"
const val ETIQUETA_TEMPORADA_NIVEL = "temporada-nivel"
const val ETIQUETA_TEMPORADA_BARRA = "temporada-barra-nivel"
const val ETIQUETA_TEMPORADA_RACHA = "temporada-racha"
const val ETIQUETA_TEMPORADA_SEMANAS = "temporada-semanas"
const val ETIQUETA_TEMPORADA_BARRAS_SEMANA = "temporada-barras-semana"

/** Semanas que dibuja el gráfico de kilómetros por semana: unos tres meses. */
internal const val SEMANAS_VISIBLES = 12

private val ALTO_GRAFICO = 96.dp
private val ALTO_MINIMO_BARRA = 4.dp
private val LADO_MASCOTA_FICHA = 88.dp

/** La temporada del piloto: ficha, racha, kilómetros por semana, hitos, carné, récords y resumen del mes. */
@Composable
internal fun TemporadaPerfil(
    estado: PerfilUiState,
    acciones: AccionesPerfil,
    falloCompartir: Boolean,
) {
    val datos = estado.temporada
    when {
        datos != null -> {
            ContenidoTemporada(datos, acciones, falloCompartir)
        }

        estado.temporadaError -> {
            EstadoVacioWrc(
                icono = Iconos.Cronometro,
                titulo = stringResource(R.string.perfil_error_titulo),
                texto = stringResource(R.string.perfil_temporada_error),
                etiqueta = ETIQUETA_TEMPORADA_ERROR,
                variante = VarianteVacio.ERROR,
                modifier = Modifier.fillMaxSize(),
            )
        }

        else -> {
            Box(Modifier.fillMaxSize().testTag(ETIQUETA_TEMPORADA_CARGANDO)) { ListaEsqueletoWrc(filas = 3) }
        }
    }
}

@Composable
private fun ContenidoTemporada(
    datos: TemporadaPiloto,
    acciones: AccionesPerfil,
    falloCompartir: Boolean,
) {
    val temporada = datos.temporada
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Espacio.l),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item { FichaPiloto(temporada.nivel, temporada.kmTotales, temporada.viajes) }
        item { TarjetaRacha(temporada.racha.actual, temporada.racha.mejor) }
        if (temporada.kmPorSemana.isNotEmpty()) {
            item { TarjetaKmSemana(temporada.kmPorSemana.takeLast(SEMANAS_VISIBLES)) }
        }
        item { TarjetaHitos(temporada.hitos, temporada.siguienteHito) }
        item { TarjetaCarne(datos.carne, temporada.carreterasDistintas) }
        item { TarjetaRecords(temporada.records) }
        item { TarjetaResumenMes(temporada.resumenMes, acciones.onCompartirMes, falloCompartir) }
    }
}

/**
 * Ficha de piloto: Trombito con la bandera, el nivel con su barra hacia el siguiente («Piloto de club · 620/1200»), los
 * kilómetros y los viajes de la temporada y la nota de que la velocidad nunca puntúa.
 */
@Composable
private fun FichaPiloto(
    nivel: NivelProgreso,
    km: Double,
    viajes: Int,
) {
    val nombre = nivel.nivel.nombre
    val umbral = nivel.umbralSiguiente
    val cifra =
        if (umbral != null) {
            stringResource(R.string.perfil_temporada_progreso, nivel.puntos, umbral)
        } else {
            stringResource(R.string.perfil_temporada_puntos, nivel.puntos)
        }
    val porcentaje = (nivel.progreso * PORCIENTO).toInt()
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_FICHA), acentoRojo = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m), verticalAlignment = Alignment.CenterVertically) {
            MascotaPose(PoseMascota.BANDERA, lado = LADO_MASCOTA_FICHA)
            Column(verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
                EtiquetaConIcono(Iconos.Piloto, stringResource(R.string.perfil_temporada_ficha))
                Text(
                    text = stringResource(R.string.perfil_temporada_nivel, nombre, cifra),
                    style = CifraPequena,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(ETIQUETA_TEMPORADA_NIVEL),
                )
                Text(
                    text =
                        stringResource(
                            R.string.perfil_temporada_resumen,
                            FormatoEs.kilometros(km),
                            pluralStringResource(R.plurals.perfil_viajes, viajes, viajes),
                        ),
                    style = Ayuda.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BarraProgresoWrc(
            progreso = nivel.progreso.toFloat(),
            descripcion = stringResource(R.string.perfil_temporada_barra_descripcion, nombre, porcentaje),
            modifier = Modifier.testTag(ETIQUETA_TEMPORADA_BARRA),
        )
        Text(
            text =
                nivel.siguiente?.let { stringResource(R.string.perfil_temporada_siguiente_nivel, it.nombre) }
                    ?: stringResource(R.string.perfil_temporada_nivel_maximo),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.perfil_temporada_puntos_nota),
            style = Ayuda,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val PORCIENTO = 100

/** La racha de semanas seguidas con salida («3 semanas seguidas») y la mejor que has tenido. */
@Composable
private fun TarjetaRacha(
    actual: Int,
    mejor: Int,
) {
    TarjetaWrc(Modifier.fillMaxWidth().testTag(ETIQUETA_TEMPORADA_RACHA), relleno = Espacio.m) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            EtiquetaConIcono(Iconos.Rayo, stringResource(R.string.perfil_racha_titulo))
            if (actual > 0) {
                Text(
                    text = pluralStringResource(R.plurals.perfil_racha_semanas, actual, actual),
                    style = CifraPequena,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        stringResource(
                            R.string.perfil_racha_mejor,
                            pluralStringResource(R.plurals.perfil_semanas, mejor, mejor),
                        ),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.perfil_racha_ninguna),
                    style = Cuerpo,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Barras simples de los kilómetros de las últimas semanas; la barra baja y gris es una semana sin salir. */
@Composable
private fun TarjetaKmSemana(semanas: List<KmSemana>) {
    val maximo = semanas.maxOf { it.km }
    val total = semanas.sumOf { it.km }
    val descripcion =
        stringResource(
            R.string.perfil_semanas_descripcion,
            semanas.size,
            FormatoEs.kilometros(total),
            FormatoEs.kilometros(maximo),
        )
    TarjetaWrc(Modifier.fillMaxWidth(), relleno = Espacio.m) {
        EtiquetaConIcono(Iconos.Grafico, stringResource(R.string.perfil_semanas_titulo))
        Column(
            Modifier.testTag(ETIQUETA_TEMPORADA_SEMANAS).clearAndSetSemantics { contentDescription = descripcion },
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            Text(
                text = stringResource(R.string.perfil_semanas_mejor, FormatoEs.kilometros(maximo)),
                style = Ayuda.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BarrasSemana(semanas, maximo)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatearFechaCorta(semanas.first().lunes.inicioDelDiaMs()),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.perfil_semanas_esta),
                    style = Ayuda,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun java.time.LocalDate.inicioDelDiaMs(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun BarrasSemana(
    semanas: List<KmSemana>,
    maximo: Double,
) {
    val color = MaterialTheme.colorScheme.primary
    val vacio = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(ALTO_GRAFICO).testTag(ETIQUETA_TEMPORADA_BARRAS_SEMANA),
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        semanas.forEach { semana ->
            val proporcion = if (maximo > 0.0) (semana.km / maximo).toFloat() else 0f
            Box(
                Modifier
                    .weight(1f)
                    .height(maxOf(ALTO_MINIMO_BARRA, ALTO_GRAFICO * proporcion))
                    .background(if (semana.km > 0.0) color else vacio, MaterialTheme.shapes.extraSmall),
            )
        }
    }
}
