package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.GRADO_MAX
import app.trombito.rallycopilot.motor.notas.GRADO_MIN
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.motor.notas.limpiarTextoLibre

/**
 * Texto con que se guarda una [AccionEdicion] (tipo + nota codificada). La nota va como
 * `direccion;grado;modificadores;distancia;cuidado;largo[;texto]`: grado `-` para una horquilla, modificadores
 * separados por comas, distancia vacía si no se dictó, `largo` los metros entre el inicio y el fin de la curva y
 * `texto` el texto libre (solo si lo hay; `%` y `;` van escapados), de modo que lo guardado antes, sin texto, se
 * sigue leyendo. Las posiciones de la nota no se guardan: las pone la edición ([NotasEditadas.posicionM]).
 * [AccionEdicion.AnadirAviso] guarda como «nota» el nombre de su [TipoAviso].
 */
object CodecEdiciones {
    const val SUSTITUIR = "SUSTITUIR"
    const val QUITAR = "QUITAR"
    const val ANADIR = "ANADIR"
    const val CUIDADO = "CUIDADO"
    const val FUSIONAR = "FUSIONAR"
    const val DIVIDIR = "DIVIDIR"
    const val ANADIR_AVISO = "ANADIR_AVISO"
    const val QUITAR_AVISO = "QUITAR_AVISO"

    /** Tipo de la acción, para la columna del mismo nombre. */
    fun tipo(accion: AccionEdicion): String =
        when (accion) {
            is AccionEdicion.Sustituir -> SUSTITUIR
            AccionEdicion.Quitar -> QUITAR
            is AccionEdicion.Anadir -> ANADIR
            AccionEdicion.Cuidado -> CUIDADO
            AccionEdicion.Fusionar -> FUSIONAR
            AccionEdicion.Dividir -> DIVIDIR
            is AccionEdicion.AnadirAviso -> ANADIR_AVISO
            AccionEdicion.QuitarAviso -> QUITAR_AVISO
        }

    /** La nota de la acción codificada, o null si la acción no lleva nota. */
    fun nota(accion: AccionEdicion): String? =
        when (accion) {
            is AccionEdicion.Sustituir -> codificarNota(accion.nota)

            is AccionEdicion.Anadir -> codificarNota(accion.nota)

            is AccionEdicion.AnadirAviso -> accion.tipo.name

            AccionEdicion.Quitar,
            AccionEdicion.Cuidado,
            AccionEdicion.Fusionar,
            AccionEdicion.Dividir,
            AccionEdicion.QuitarAviso,
            -> null
        }

    /** Reconstruye la acción; null si el tipo es desconocido o la nota está dañada. */
    fun accion(
        tipo: String,
        nota: String?,
    ): AccionEdicion? =
        when (tipo) {
            QUITAR -> {
                AccionEdicion.Quitar
            }

            CUIDADO -> {
                AccionEdicion.Cuidado
            }

            FUSIONAR -> {
                AccionEdicion.Fusionar
            }

            DIVIDIR -> {
                AccionEdicion.Dividir
            }

            QUITAR_AVISO -> {
                AccionEdicion.QuitarAviso
            }

            ANADIR_AVISO -> {
                TipoAviso.entries
                    .firstOrNull { it.name == nota }
                    ?.let { AccionEdicion.AnadirAviso(it) }
            }

            SUSTITUIR -> {
                nota?.let(::decodificarNota)?.let { AccionEdicion.Sustituir(it) }
            }

            ANADIR -> {
                nota?.let(::decodificarNota)?.let { AccionEdicion.Anadir(it) }
            }

            else -> {
                null
            }
        }

    fun codificarNota(nota: Nota): String {
        val texto = limpiarTextoLibre(nota.textoLibre)
        return listOfNotNull(
            nota.direccion.name,
            nota.grado?.toString() ?: "-",
            nota.modificadores.sorted().joinToString(",") { it.name },
            nota.distanciaSiguienteM?.toString().orEmpty(),
            if (nota.cuidado) "1" else "0",
            (nota.finM - nota.posicionM).toString(),
            texto?.let(::escapar),
        ).joinToString(";")
    }

    @Suppress("ReturnCount") // un campo dañado descarta la nota: se valida campo a campo
    fun decodificarNota(texto: String): Nota? {
        val campos = texto.split(";")
        if (campos.size != CAMPOS && campos.size != CAMPOS_CON_TEXTO) return null
        val direccion = Direccion.entries.firstOrNull { it.name == campos[0] } ?: return null
        val grado = if (campos[1] == "-") null else campos[1].toIntOrNull() ?: return null
        if (grado != null && grado !in GRADO_MIN..GRADO_MAX) return null
        val modificadores = modificadores(campos[2]) ?: return null
        val textoDistancia = campos[COL_DISTANCIA]
        val distancia = if (textoDistancia.isEmpty()) null else textoDistancia.toIntOrNull() ?: return null
        val largo = campos[COL_LARGO].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        return Nota(
            posicionM = 0.0,
            finM = largo,
            direccion = direccion,
            grado = grado,
            modificadores = modificadores,
            distanciaSiguienteM = distancia,
            cuidado = campos[COL_CUIDADO] == "1",
            textoLibre = campos.getOrNull(COL_TEXTO)?.let { limpiarTextoLibre(desescapar(it)) },
        )
    }

    /** Los modificadores separados por comas; null si alguno no existe. */
    private fun modificadores(texto: String): Set<Modificador>? =
        if (texto.isEmpty()) {
            emptySet()
        } else {
            texto.split(",").map { n -> Modificador.entries.firstOrNull { it.name == n } ?: return null }.toSet()
        }

    // El texto libre puede llevar el separador de campos: se escapa con % como en una URL.
    private fun escapar(texto: String): String = texto.replace("%", "%25").replace(";", "%3B")

    private fun desescapar(texto: String): String = texto.replace("%3B", ";").replace("%25", "%")

    private const val CAMPOS = 6
    private const val CAMPOS_CON_TEXTO = 7
    private const val COL_TEXTO = 6
    private const val COL_DISTANCIA = 3
    private const val COL_CUIDADO = 4
    private const val COL_LARGO = 5
}
