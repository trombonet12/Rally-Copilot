package app.trombito.rallycopilot.ui.copiloto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.EstadoBotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.theme.Espacio
import app.trombito.rallycopilot.ui.theme.EtiquetaIcono

const val ETIQUETA_AVISAR_RADARES = "avisos-radares"
const val ETIQUETA_AVISAR_INCIDENCIAS = "avisos-incidencias"
const val ETIQUETA_AVISAR_VELOCIDAD = "avisos-velocidad"
const val ETIQUETA_HABLA_PREFIJO = "habla-"
const val ETIQUETA_AVISOS_VOZ = "avisos-voz"

private val LADO_ICONO = 28.dp

/**
 * Los tres interruptores de avisos por voz (radares, incidencias y velocidad) como iconos con su nombre debajo; apagado
 * = esa familia no se dice (el banner y la píldora siguen igual). Los usan la hoja del copiloto y Ajustes > Voz.
 */
@Composable
internal fun InterruptoresAvisos(
    radares: Boolean,
    incidencias: Boolean,
    velocidad: Boolean,
    acciones: AccionesAvisos,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag(ETIQUETA_AVISOS_VOZ), horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
        InterruptorAviso(
            icono = { Icon(painterResource(R.drawable.ic_alerta_radar), null, Modifier.size(LADO_ICONO)) },
            descripcion = stringResource(R.string.avisos_radares_descripcion),
            etiqueta = stringResource(R.string.avisos_radares),
            activado = radares,
            onCambio = acciones.onRadares,
            prueba = ETIQUETA_AVISAR_RADARES,
        )
        InterruptorAviso(
            icono = { Icon(painterResource(R.drawable.ic_alerta_otra), null, Modifier.size(LADO_ICONO)) },
            descripcion = stringResource(R.string.avisos_incidencias_descripcion),
            etiqueta = stringResource(R.string.avisos_incidencias),
            activado = incidencias,
            onCambio = acciones.onIncidencias,
            prueba = ETIQUETA_AVISAR_INCIDENCIAS,
        )
        InterruptorAviso(
            icono = { Icon(IconosVoz.Velocimetro, null, Modifier.size(LADO_ICONO)) },
            descripcion = stringResource(R.string.avisos_velocidad_descripcion),
            etiqueta = stringResource(R.string.avisos_velocidad),
            activado = velocidad,
            onCambio = acciones.onVelocidad,
            prueba = ETIQUETA_AVISAR_VELOCIDAD,
        )
    }
}

/** Qué hacer al conmutar cada familia de avisos; los tres callbacks reciben el estado nuevo. */
internal class AccionesAvisos(
    val onRadares: (Boolean) -> Unit,
    val onIncidencias: (Boolean) -> Unit,
    val onVelocidad: (Boolean) -> Unit,
)

@Composable
@Suppress("LongParameterList") // dibujo, textos, estado y etiqueta de prueba del interruptor
private fun InterruptorAviso(
    icono: @Composable () -> Unit,
    descripcion: String,
    etiqueta: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    prueba: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BotonIcono(
            descripcion = descripcion,
            onClick = { onCambio(!activado) },
            modifier = Modifier.testTag(prueba),
            estado =
                EstadoBotonIcono(
                    seleccionado = activado,
                    descripcionEstado = stringResource(estadoAviso(activado)),
                    rol = Role.Switch,
                ),
            contenido = icono,
        )
        Text(
            text = etiqueta,
            style = EtiquetaIcono,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun estadoAviso(activado: Boolean): Int =
    if (activado) R.string.indicaciones_estado_activo else R.string.indicaciones_estado_inactivo

/**
 * «Normal» y «Rápida» como dos iconos (▶ y ▶▶) con su nombre debajo. Elegir una la guarda y dice una frase de ejemplo
 * con esa velocidad (lo hace [onElegir]): la vista previa es oírla.
 */
@Composable
internal fun SelectorVelocidadHabla(
    velocidad: VelocidadHabla,
    onElegir: (VelocidadHabla) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
        VelocidadHabla.entries.forEach { opcion ->
            val elegida = opcion == velocidad
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BotonIcono(
                    descripcion = stringResource(opcion.descripcion()),
                    onClick = { onElegir(opcion) },
                    modifier = Modifier.testTag(ETIQUETA_HABLA_PREFIJO + opcion.name.lowercase()),
                    estado =
                        EstadoBotonIcono(
                            seleccionado = elegida,
                            descripcionEstado = if (elegida) stringResource(R.string.ajustes_seleccionado) else null,
                            rol = Role.RadioButton,
                        ),
                ) { Icon(opcion.icono(), contentDescription = null, modifier = Modifier.size(LADO_ICONO)) }
                Text(
                    text = stringResource(opcion.nombre()),
                    style = EtiquetaIcono,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Para la hoja del copiloto: dos secciones con rótulo de 12 sp, «Avisos por voz» (los tres interruptores) y
 * «Velocidad de habla» (normal o rápida). Cada cambio se guarda y vale al momento, conduciendo.
 */
@Composable
fun ControlesAvisosVoz(
    estado: CopilotoUiState,
    acciones: AccionesCopiloto,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Espacio.m)) {
        SeccionVoz(stringResource(R.string.avisos_titulo)) {
            InterruptoresAvisos(
                radares = estado.avisarRadares,
                incidencias = estado.avisarIncidencias,
                velocidad = estado.avisarVelocidad,
                acciones =
                    AccionesAvisos(acciones.onAvisarRadares, acciones.onAvisarIncidencias, acciones.onAvisarVelocidad),
            )
        }
        SeccionVoz(stringResource(R.string.habla_titulo)) {
            SelectorVelocidadHabla(estado.velocidadHabla, acciones.onVelocidadHabla)
        }
    }
}

@Composable
private fun SeccionVoz(
    titulo: String,
    contenido: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
        Text(text = titulo, style = EtiquetaIcono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        contenido()
    }
}

internal fun VelocidadHabla.nombre(): Int =
    when (this) {
        VelocidadHabla.NORMAL -> R.string.habla_normal
        VelocidadHabla.RAPIDA -> R.string.habla_rapida
    }

private fun VelocidadHabla.descripcion(): Int =
    when (this) {
        VelocidadHabla.NORMAL -> R.string.habla_normal_descripcion
        VelocidadHabla.RAPIDA -> R.string.habla_rapida_descripcion
    }

private fun VelocidadHabla.icono(): ImageVector =
    when (this) {
        VelocidadHabla.NORMAL -> Iconos.Empezar
        VelocidadHabla.RAPIDA -> IconosVoz.Rapida
    }

/** Iconos de Material (Apache 2.0) como vectores propios: velocímetro y avance rápido (▶▶). */
internal object IconosVoz {
    val Velocimetro: ImageVector by lazy {
        icono(
            "Velocimetro",
            "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58H5.07A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19" +
                "a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1,10 10,0 0,0 -0.27,-10.44zM10.59,15.41a2,2 0,0 0,2.83 0" +
                "l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z",
        )
    }

    val Rapida: ImageVector by lazy { icono("Rapida", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z") }

    private fun icono(
        nombre: String,
        trazado: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = nombre,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply { addPath(pathData = addPathNodes(trazado), fill = SolidColor(Color.Black)) }
            .build()
}
