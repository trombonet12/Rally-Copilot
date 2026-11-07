package app.trombito.rallycopilot.ui.navegacion.notas

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.NotaVisible
import kotlin.math.roundToInt

private const val ALFA_DISTANCIA_SIGUIENTE = 0.6f
private const val PASO_DISTANCIA_M = 10
private const val PASO_DISTANCIA_LEJOS_M = 50
private const val UMBRAL_DISTANCIA_LEJOS_M = 200

/**
 * Distancia que se escribe en la tarjeta grande: de 10 en 10 metros por debajo de 200 y de 50 en 50 a partir de ahí.
 * Nunca negativa.
 */
internal fun distanciaRedondeadaM(distanciaM: Double): Int {
    val metros = distanciaM.coerceAtLeast(0.0)
    val paso = if (metros < UMBRAL_DISTANCIA_LEJOS_M) PASO_DISTANCIA_M else PASO_DISTANCIA_LEJOS_M
    return (metros / paso).roundToInt() * paso
}

/**
 * Texto visible en mayúsculas: solo la curva con el grado en cifra y, aparte y atenuada con el [color] de la nota al
 * 60 %, la distancia a la siguiente nota con su unidad («HORQUILLA DERECHA MUY LARGA · 40 m», «IZQUIERDA 3 CIERRA»):
 * solo la distancia a la curva (la cifra grande de la tarjeta) destaca. El cuidado, los avisos unidos y el puente de la
 * curva van como iconos, no en el texto. Un aviso suelto lleva su palabra («PUENTE», «CEDA EL PASO»). Con [abreviar]
 * (tarjetas pequeñas): IZQ, DER, HORQ y «MUY CERR.».
 */
internal fun textoNota(
    recursos: Resources,
    elemento: ElementoHoja,
    simplificada: Boolean,
    color: Color,
    abreviar: Boolean = false,
): AnnotatedString {
    val frase = fraseVisible(elemento, simplificada).uppercase()
    val texto = if (abreviar && elemento is ElementoHoja.DeNota) abreviaturas(frase) else frase
    val siguiente =
        (elemento as? ElementoHoja.DeNota)?.nota?.distanciaSiguienteM?.let {
            recursos.getString(R.string.notas_distancia_siguiente, it)
        }
    return buildAnnotatedString {
        append(texto)
        if (siguiente != null) {
            append(' ')
            withStyle(SpanStyle(color = color.copy(alpha = ALFA_DISTANCIA_SIGUIENTE))) { append(siguiente) }
        }
    }
}

/** Abreviaturas de roadbook para las tarjetas pequeñas. */
private fun abreviaturas(frase: String): String =
    frase
        .replace("IZQUIERDA", "IZQ")
        .replace("DERECHA", "DER")
        .replace("HORQUILLA", "HORQ")
        .replace("MUY CERRADA", "MUY CERR.")

/** La curva sin «cuidado», avisos, puente ni distancia; un aviso suelto, su palabra. */
private fun fraseVisible(
    elemento: ElementoHoja,
    simplificada: Boolean,
): String =
    when (elemento) {
        is ElementoHoja.DeAviso -> {
            elemento.frase
        }

        is ElementoHoja.DeNota -> {
            val nota = elemento.nota.copy(cuidado = false, distanciaSiguienteM = null)
            cifraEnVezDePalabra(FrasesNota.frase(nota, simplificada), nota, simplificada)
        }
    }

/**
 * En pantalla el grado va en cifra («derecha 4 larga»): cambia solo la palabra del grado de la frase del motor. La
 * escuadra no lleva número: sale «DERECHA ESCUADRA».
 */
private fun cifraEnVezDePalabra(
    frase: String,
    nota: Nota,
    simplificada: Boolean,
): String {
    val grado = nota.grado
    if (simplificada || grado == null || Modificador.ESCUADRA in nota.modificadores) return frase
    val direccion = if (nota.direccion == Direccion.IZQUIERDA) "izquierda" else "derecha"
    return frase.replace("$direccion ${FrasesNota.enPalabras(grado)}", "$direccion $grado")
}

/** Lo que lee TalkBack: la misma frase que canta la voz y a cuántos metros queda (redondeado a 10). */
internal fun descripcionNota(
    recursos: Resources,
    visible: NotaVisible,
    simplificada: Boolean,
): String {
    val hablada =
        when (val elemento = visible.elemento) {
            is ElementoHoja.DeAviso -> elemento.frase
            is ElementoHoja.DeNota -> elemento.frase(simplificada)
        }
    val metros = (visible.distanciaM.coerceAtLeast(0.0) / PASO_DISTANCIA_M).roundToInt() * PASO_DISTANCIA_M
    return recursos.getString(R.string.notas_descripcion, hablada, metros)
}
