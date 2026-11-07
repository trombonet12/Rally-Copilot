package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.GRADO_MAX
import app.trombito.rallycopilot.motor.notas.GRADO_MIN
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota

/**
 * Texto con que se guarda una [AccionEdicion] (tipo + nota codificada). La nota va como
 * `direccion;grado;modificadores;distancia;cuidado;largo`: grado `-` para una horquilla, modificadores separados por
 * comas, distancia vacía si no se dictó y `largo` los metros entre el inicio y el fin de la curva. Las posiciones de la
 * nota no se guardan: las pone la edición ([NotasEditadas.posicionM]).
 */
object CodecEdiciones {
    const val SUSTITUIR = "SUSTITUIR"
    const val QUITAR = "QUITAR"
    const val ANADIR = "ANADIR"
    const val CUIDADO = "CUIDADO"

    /** Tipo de la acción, para la columna del mismo nombre. */
    fun tipo(accion: AccionEdicion): String =
        when (accion) {
            is AccionEdicion.Sustituir -> SUSTITUIR
            AccionEdicion.Quitar -> QUITAR
            is AccionEdicion.Anadir -> ANADIR
            AccionEdicion.Cuidado -> CUIDADO
        }

    /** La nota de la acción codificada, o null si la acción no lleva nota. */
    fun nota(accion: AccionEdicion): String? =
        when (accion) {
            is AccionEdicion.Sustituir -> codificarNota(accion.nota)
            is AccionEdicion.Anadir -> codificarNota(accion.nota)
            AccionEdicion.Quitar, AccionEdicion.Cuidado -> null
        }

    /** Reconstruye la acción; null si el tipo es desconocido o la nota está dañada. */
    fun accion(
        tipo: String,
        nota: String?,
    ): AccionEdicion? =
        when (tipo) {
            QUITAR -> AccionEdicion.Quitar
            CUIDADO -> AccionEdicion.Cuidado
            SUSTITUIR -> nota?.let(::decodificarNota)?.let { AccionEdicion.Sustituir(it) }
            ANADIR -> nota?.let(::decodificarNota)?.let { AccionEdicion.Anadir(it) }
            else -> null
        }

    fun codificarNota(nota: Nota): String =
        listOf(
            nota.direccion.name,
            nota.grado?.toString() ?: "-",
            nota.modificadores.sorted().joinToString(",") { it.name },
            nota.distanciaSiguienteM?.toString().orEmpty(),
            if (nota.cuidado) "1" else "0",
            (nota.finM - nota.posicionM).toString(),
        ).joinToString(";")

    @Suppress("ReturnCount") // un campo dañado descarta la nota: se valida campo a campo
    fun decodificarNota(texto: String): Nota? {
        val campos = texto.split(";")
        if (campos.size != CAMPOS) return null
        val direccion = Direccion.entries.firstOrNull { it.name == campos[0] } ?: return null
        val grado = if (campos[1] == "-") null else campos[1].toIntOrNull() ?: return null
        if (grado != null && grado !in GRADO_MIN..GRADO_MAX) return null
        val modificadores =
            if (campos[2].isEmpty()) {
                emptySet()
            } else {
                campos[2]
                    .split(",")
                    .map { n ->
                        Modificador.entries.firstOrNull { it.name == n } ?: return null
                    }.toSet()
            }
        val distancia =
            if (campos[COL_DISTANCIA].isEmpty()) {
                null
            } else {
                campos[COL_DISTANCIA].toIntOrNull()
                    ?: return null
            }
        val largo = campos[COL_LARGO].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        return Nota(
            posicionM = 0.0,
            finM = largo,
            direccion = direccion,
            grado = grado,
            modificadores = modificadores,
            distanciaSiguienteM = distancia,
            cuidado = campos[COL_CUIDADO] == "1",
        )
    }

    private const val CAMPOS = 6
    private const val COL_DISTANCIA = 3
    private const val COL_CUIDADO = 4
    private const val COL_LARGO = 5
}
