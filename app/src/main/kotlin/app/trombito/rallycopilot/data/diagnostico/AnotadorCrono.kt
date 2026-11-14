package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import java.util.Locale

/** Anota en el registro los cambios del cronómetro (tramo iniciado, sectores, tramo y subtramos completados). */
class AnotadorCrono(
    private val registro: RegistroDiagnostico,
) {
    fun anotar(
        antes: CronoEnCurso?,
        ahora: CronoEnCurso?,
    ) {
        if (ahora == null) return
        val nombre = ahora.tramo?.nombre ?: "sin nombre"
        anotarInicio(antes, ahora, nombre)
        anotarMarca(antes, ahora)
        anotarResultado(antes, ahora, nombre)
        anotarSubtramo(antes, ahora)
    }

    private fun anotarInicio(
        antes: CronoEnCurso?,
        ahora: CronoEnCurso,
        nombre: String,
    ) {
        val tramo = ahora.tramo ?: return
        if (tramo.id != antes?.tramo?.id) {
            registro.info(ETIQUETA, "tramo iniciado nombre=$nombre longitud_m=${tramo.longitudM.toLong()}")
        }
    }

    private fun anotarMarca(
        antes: CronoEnCurso?,
        ahora: CronoEnCurso,
    ) {
        val marca = ahora.ultimaMarca ?: return
        if (marca == antes?.ultimaMarca) return
        registro.info(
            ETIQUETA,
            "tramo ${marca.tipo.name.lowercase(Locale.ROOT)} n=${marca.numero} tiempo_ms=${marca.tiempoMs} " +
                "delta_acumulado_ms=${marca.deltaAcumuladoMs ?: "n/d"}",
        )
    }

    private fun anotarResultado(
        antes: CronoEnCurso?,
        ahora: CronoEnCurso,
        nombre: String,
    ) {
        val resultado = ahora.resultado ?: return
        if (resultado == antes?.resultado) return
        registro.info(
            ETIQUETA,
            "tramo completado nombre=$nombre tiempo_ms=${resultado.totalMs} " +
                "delta_ms=${resultado.deltaMs ?: "n/d"} nuevo_mejor=${resultado.nuevoMejor}",
        )
    }

    private fun anotarSubtramo(
        antes: CronoEnCurso?,
        ahora: CronoEnCurso,
    ) {
        val subtramo = ahora.ultimoSubtramo ?: return
        if (subtramo == antes?.ultimoSubtramo) return
        val delta = subtramo.mejorMs?.let { subtramo.tiempoMs - it }
        registro.info(
            ETIQUETA,
            "subtramo completado longitud_m=${subtramo.longitudM.toLong()} tiempo_ms=${subtramo.tiempoMs} " +
                "delta_ms=${delta ?: "n/d"}",
        )
    }

    private companion object {
        const val ETIQUETA = "Diagnostico"
    }
}
