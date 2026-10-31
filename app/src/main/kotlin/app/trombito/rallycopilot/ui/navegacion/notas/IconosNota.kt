package app.trombito.rallycopilot.ui.navegacion.notas

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.GradoSimplificado
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso

private val IZQUIERDA =
    intArrayOf(
        R.drawable.ic_nota_izq_1,
        R.drawable.ic_nota_izq_2,
        R.drawable.ic_nota_izq_3,
        R.drawable.ic_nota_izq_4,
        R.drawable.ic_nota_izq_5,
        R.drawable.ic_nota_izq_6,
    )

private val DERECHA =
    intArrayOf(
        R.drawable.ic_nota_der_1,
        R.drawable.ic_nota_der_2,
        R.drawable.ic_nota_der_3,
        R.drawable.ic_nota_der_4,
        R.drawable.ic_nota_der_5,
        R.drawable.ic_nota_der_6,
    )

/** Grado que dibuja cada categoría gruesa de las notas simplificadas (la más típica de su grupo). */
private fun gradoRepresentativo(g: GradoSimplificado): Int =
    when (g) {
        GradoSimplificado.CERRADA -> 2
        GradoSimplificado.MEDIA -> 4
        GradoSimplificado.SUAVE -> 6
        GradoSimplificado.HORQUILLA -> 1
    }

/** Icono de roadbook de una nota de curva: la forma depende del grado (o de la categoría si va simplificada). */
@DrawableRes
internal fun iconoCurva(
    nota: Nota,
    simplificada: Boolean,
): Int {
    val izquierda = nota.direccion == Direccion.IZQUIERDA
    val gradoNota = nota.grado
    if (gradoNota == null) return if (izquierda) R.drawable.ic_nota_horquilla_izq else R.drawable.ic_nota_horquilla_der
    val grado = if (simplificada) gradoRepresentativo(nota.simplificada()) else gradoNota
    return (if (izquierda) IZQUIERDA else DERECHA)[grado - 1]
}

private val ICONOS_AVISO: Map<TipoAviso, Int> =
    mapOf(
        TipoAviso.PUENTE to R.drawable.ic_nota_puente,
        TipoAviso.TUNEL to R.drawable.ic_nota_tunel,
        TipoAviso.ROTONDA to R.drawable.ic_nota_rotonda,
        TipoAviso.ESTRECHA to R.drawable.ic_nota_estrecha,
        TipoAviso.BACHE to R.drawable.ic_nota_bache,
        TipoAviso.BADEN to R.drawable.ic_nota_baden,
        TipoAviso.PASO_A_NIVEL to R.drawable.ic_nota_paso_nivel,
        TipoAviso.STOP to R.drawable.ic_nota_stop,
        TipoAviso.CEDA to R.drawable.ic_nota_ceda,
        TipoAviso.SEMAFORO to R.drawable.ic_nota_semaforo,
        TipoAviso.CRUCE to R.drawable.ic_nota_cruce,
        TipoAviso.RASANTE to R.drawable.ic_nota_rasante,
        TipoAviso.CAMBIO_RASANTE to R.drawable.ic_nota_rasante,
        TipoAviso.SALTO to R.drawable.ic_nota_salto,
        TipoAviso.CUIDADO to R.drawable.ic_nota_cuidado,
    )

@DrawableRes
internal fun iconoAviso(tipo: TipoAviso): Int = ICONOS_AVISO.getValue(tipo)

@DrawableRes
internal fun iconoElemento(
    elemento: ElementoHoja,
    simplificada: Boolean,
): Int =
    when (elemento) {
        is ElementoHoja.DeNota -> iconoCurva(elemento.nota, simplificada)
        is ElementoHoja.DeAviso -> iconoAviso(elemento.aviso.tipo)
    }

private val NOMBRES_AVISO: Map<TipoAviso, Int> =
    mapOf(
        TipoAviso.PUENTE to R.string.notas_aviso_puente,
        TipoAviso.TUNEL to R.string.notas_aviso_tunel,
        TipoAviso.ROTONDA to R.string.notas_aviso_rotonda,
        TipoAviso.ESTRECHA to R.string.notas_aviso_estrecha,
        TipoAviso.BACHE to R.string.notas_aviso_bache,
        TipoAviso.BADEN to R.string.notas_aviso_baden,
        TipoAviso.PASO_A_NIVEL to R.string.notas_aviso_paso_a_nivel,
        TipoAviso.STOP to R.string.notas_aviso_stop,
        TipoAviso.CEDA to R.string.notas_aviso_ceda,
        TipoAviso.SEMAFORO to R.string.notas_aviso_semaforo,
        TipoAviso.CRUCE to R.string.notas_aviso_cruce,
        TipoAviso.RASANTE to R.string.notas_aviso_rasante,
        TipoAviso.CAMBIO_RASANTE to R.string.notas_aviso_cambio_rasante,
        TipoAviso.SALTO to R.string.notas_aviso_salto,
        TipoAviso.CUIDADO to R.string.notas_aviso_cuidado,
    )

@StringRes
internal fun nombreAviso(tipo: TipoAviso): Int = NOMBRES_AVISO.getValue(tipo)

@StringRes
internal fun nombreModificador(m: Modificador): Int =
    when (m) {
        Modificador.LARGA -> R.string.notas_larga
        Modificador.MUY_LARGA -> R.string.notas_muy_larga
        Modificador.CIERRA -> R.string.notas_cierra
        Modificador.ABRE -> R.string.notas_abre
    }

@StringRes
internal fun nombreSimplificado(g: GradoSimplificado): Int =
    when (g) {
        GradoSimplificado.CERRADA -> R.string.notas_cerrada
        GradoSimplificado.MEDIA -> R.string.notas_media
        GradoSimplificado.SUAVE -> R.string.notas_suave
        GradoSimplificado.HORQUILLA -> R.string.notas_horquilla
    }
