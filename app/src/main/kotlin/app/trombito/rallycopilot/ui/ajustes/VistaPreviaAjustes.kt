package app.trombito.rallycopilot.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.Anticipacion
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import app.trombito.rallycopilot.ui.comun.BotonIcono
import app.trombito.rallycopilot.ui.comun.Iconos
import app.trombito.rallycopilot.ui.comun.LADO_TACTIL
import app.trombito.rallycopilot.ui.navegacion.notas.FilaNota
import app.trombito.rallycopilot.ui.navegacion.notas.MEDIDAS_COMPACTA
import app.trombito.rallycopilot.ui.navegacion.notas.MEDIDAS_GRANDE
import app.trombito.rallycopilot.ui.theme.Espacio
import java.util.Locale
import kotlin.math.roundToInt

const val ETIQUETA_PREVISTA_NOTA = "prevista-nota"
const val ETIQUETA_PREVISTA_ANTICIPACION = "prevista-anticipacion"
const val ETIQUETA_PREVISTA_ESCUCHAR = "prevista-escuchar"

/** Posición de la tarjeta de ejemplo: lejos de los índices 0..2 de la franja de notas del mapa. */
private const val INDICE_EJEMPLO = 90
private const val VELOCIDAD_EJEMPLO_KMH = 90
private const val KMH_A_MS = 3.6
private const val DISTANCIA_EJEMPLO_M = 50.0
private const val GRADO_EJEMPLO = 4
private const val PASO_METROS = 10

/** La nota que se enseña y se canta de ejemplo: «derecha 4 larga, cincuenta». */
internal object NotaEjemplo {
    val nota =
        Nota(
            posicionM = 0.0,
            finM = 0.0,
            direccion = Direccion.DERECHA,
            grado = GRADO_EJEMPLO,
            modificadores = setOf(Modificador.LARGA),
            distanciaSiguienteM = DISTANCIA_EJEMPLO_M.toInt(),
        )

    /** Lo que dice la voz del ejemplo: la misma frase del motor, simplificada o no. */
    fun frase(simplificadas: Boolean): String = FrasesNota.frase(nota, simplificadas)

    fun visible() = NotaVisible(ElementoHoja.DeNota(nota), DISTANCIA_EJEMPLO_M, activa = true)
}

/** Metros a los que se canta la nota yendo a 90 km/h con [nivel] (redondeado a 10). */
internal fun metrosDeAnticipacion(nivel: NivelAnticipacion): Int {
    val metros = Anticipacion().distanciaM(VELOCIDAD_EJEMPLO_KMH / KMH_A_MS, nivel)
    return (metros / PASO_METROS).roundToInt() * PASO_METROS
}

/** «5» o «6,5»: los segundos del nivel como se escriben en español. */
internal fun segundosEscritos(nivel: NivelAnticipacion): String =
    if (nivel.segundos % 1.0 == 0.0) {
        nivel.segundos.toInt().toString()
    } else {
        String.format(Locale.forLanguageTag("es-ES"), "%.1f", nivel.segundos)
    }

/** Línea «Te la canta 5 s antes (a 90 km/h, a 150 m)» del nivel elegido. */
@Composable
internal fun TextoAnticipacion(
    nivel: NivelAnticipacion,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            stringResource(
                R.string.ajustes_prevista_anticipacion,
                segundosEscritos(nivel),
                metrosDeAnticipacion(nivel),
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.testTag(ETIQUETA_PREVISTA_ANTICIPACION),
    )
}

/** La tarjeta de nota tal como sale en el mapa, con la nota de ejemplo (simplificada o no). */
@Composable
internal fun TarjetaNotaEjemplo(
    simplificadas: Boolean,
    modifier: Modifier = Modifier,
    reducida: Boolean = false,
) {
    FilaNota(
        visible = NotaEjemplo.visible(),
        indice = INDICE_EJEMPLO,
        simplificadas = simplificadas,
        medidas = if (reducida) MEDIDAS_COMPACTA else MEDIDAS_GRANDE,
        modifier = modifier.testTag(ETIQUETA_PREVISTA_NOTA),
    )
}

/** «▶ Escuchar ejemplo»: dice la nota de ejemplo con la voz y el volumen elegidos. */
@Composable
internal fun BotonEscucharEjemplo(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.ajustes_prevista_escuchar_descripcion)
    FilledTonalButton(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = LADO_TACTIL)
                .testTag(ETIQUETA_PREVISTA_ESCUCHAR)
                .semantics { contentDescription = descripcion },
    ) {
        Icon(Iconos.Empezar, contentDescription = null)
        Text(stringResource(R.string.ajustes_prevista_escuchar), modifier = Modifier.padding(start = Espacio.s))
    }
}

/** Vista previa en pequeño para la hoja del mapa: tarjeta reducida, línea de anticipación y ▶ de icono. */
@Composable
internal fun VistaPreviaNotasReducida(
    anticipacion: NivelAnticipacion,
    simplificadas: Boolean,
    onEscuchar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val descripcion = stringResource(R.string.ajustes_prevista_escuchar_descripcion)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            TarjetaNotaEjemplo(simplificadas, Modifier.weight(1f), reducida = true)
            BotonIcono(
                descripcion = descripcion,
                onClick = onEscuchar,
                modifier = Modifier.testTag(ETIQUETA_PREVISTA_ESCUCHAR),
            ) { Icon(Iconos.Empezar, contentDescription = null) }
        }
        TextoAnticipacion(anticipacion)
    }
}

/** Dice la nota de ejemplo con la voz, el volumen y las simplificadas de [ajustes] (sin voz elegida, la primera). */
internal fun Locutor.decirEjemplo(ajustes: AjustesConduccion) {
    usarVolumen(ajustes.volumenVoz)
    probarFrase(ajustes.voz ?: VozCopiloto.DAVEFX, NotaEjemplo.frase(ajustes.notasSimplificadas))
}
