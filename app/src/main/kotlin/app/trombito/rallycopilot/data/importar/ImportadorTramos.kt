package app.trombito.rallycopilot.data.importar

import android.net.Uri
import app.trombito.rallycopilot.data.alertas.LectorXmlAndroid
import app.trombito.rallycopilot.data.tramos.GuardadorTramos
import app.trombito.rallycopilot.domain.importar.ImportaArchivo
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.domain.importar.ResultadoImportar
import app.trombito.rallycopilot.domain.tramos.ResultadoGuardado
import app.trombito.rallycopilot.motor.alertas.LectorXml
import app.trombito.rallycopilot.motor.importar.LectorGpx
import app.trombito.rallycopilot.motor.importar.LectorKml
import app.trombito.rallycopilot.motor.importar.MotivoErrorImportacion
import app.trombito.rallycopilot.motor.importar.ResultadoImportacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject

/** Un documento abierto desde un `content://` que ha entregado el usuario o el sistema. */
class DocumentoAbierto(
    val flujo: InputStream,
    val nombre: String?,
    val tamanoBytes: Long?,
)

/** Abre el documento de un [Uri] recibido (selector de archivos, «Abrir con» o «Compartir»); nada más que ese Uri. */
fun interface AbridorDocumentos {
    /** Null si no se puede abrir. */
    fun abrir(uri: Uri): DocumentoAbierto?
}

/**
 * Importa un GPX o un KML como tramo (sin pasadas): lee solo el [Uri] recibido, rechaza lo que no sea `content://` o
 * pase de [TOPE_BYTES] (también si el tamaño declarado miente: se corta al leer), distingue el formato por el contenido
 * (o, si no basta, por la extensión) y guarda con [GuardadorTramos]. El nombre es el `<name>` del archivo o, si no lo
 * trae, el nombre del archivo; sin ninguno queda el automático.
 */
class ImportadorTramos
    @Inject
    constructor(
        private val abridor: AbridorDocumentos,
        private val guardador: GuardadorTramos,
    ) : ImportaArchivo {
        override suspend fun importar(uri: String): ResultadoImportar =
            withContext(Dispatchers.IO) {
                val direccion = Uri.parse(uri)
                val documento = if (direccion.scheme == "content") abridor.abrir(direccion) else null
                when {
                    documento == null -> {
                        ResultadoImportar.Fallo(MotivoFalloImportar.NO_SE_PUEDE_ABRIR)
                    }

                    (documento.tamanoBytes ?: 0L) > TOPE_BYTES -> {
                        documento.flujo.close()
                        ResultadoImportar.Fallo(MotivoFalloImportar.DEMASIADO_GRANDE)
                    }

                    else -> {
                        leerYGuardar(documento)
                    }
                }
            }

        private suspend fun leerYGuardar(documento: DocumentoAbierto): ResultadoImportar {
            val limitado = FlujoLimitado(documento.flujo, TOPE_BYTES)
            val resultado = BufferedInputStream(limitado, CABECERA_BYTES).use { leerTraza(it, documento.nombre) }
            return when {
                limitado.excedido -> ResultadoImportar.Fallo(MotivoFalloImportar.DEMASIADO_GRANDE)
                resultado == null -> ResultadoImportar.Fallo(MotivoFalloImportar.FORMATO_DESCONOCIDO)
                resultado is ResultadoImportacion.Error -> ResultadoImportar.Fallo(motivo(resultado.motivo))
                resultado is ResultadoImportacion.Ok -> guardar(resultado, documento.nombre)
                else -> ResultadoImportar.Fallo(MotivoFalloImportar.FORMATO_DESCONOCIDO)
            }
        }

        /** Null si el contenido no es GPX ni KML. */
        private fun leerTraza(
            entrada: BufferedInputStream,
            nombre: String?,
        ): ResultadoImportacion? =
            try {
                formato(entrada, nombre)?.let { esGpx ->
                    val xml: LectorXml = LectorXmlAndroid(entrada)
                    if (esGpx) LectorGpx.leer(xml) else LectorKml.leer(xml)
                }
            } catch (e: IOException) {
                ResultadoImportacion.Error(MotivoErrorImportacion.ENTRADA_ILEGIBLE, e.message)
            }

        private suspend fun guardar(
            traza: ResultadoImportacion.Ok,
            archivo: String?,
        ): ResultadoImportar {
            val nombre =
                traza.nombre
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.take(MAX_NOMBRE) ?: nombreDeArchivo(archivo)
            return when (val r = guardador.guardar(traza.puntos, nombre)) {
                is ResultadoGuardado.Guardado -> ResultadoImportar.Importado(r.tramoId, r.nombre)
                ResultadoGuardado.Corto -> ResultadoImportar.Fallo(MotivoFalloImportar.CORTO)
                ResultadoGuardado.Fallo -> ResultadoImportar.Fallo(MotivoFalloImportar.NO_GUARDADO)
            }
        }

        private fun motivo(m: MotivoErrorImportacion): MotivoFalloImportar =
            when (m) {
                MotivoErrorImportacion.SIN_TRAZA -> {
                    MotivoFalloImportar.SIN_TRAZA
                }

                MotivoErrorImportacion.ENTRADA_ILEGIBLE -> {
                    MotivoFalloImportar.NO_SE_PUEDE_ABRIR
                }

                MotivoErrorImportacion.XML_MAL_FORMADO, MotivoErrorImportacion.DEMASIADOS_PUNTOS -> {
                    MotivoFalloImportar.ARCHIVO_DANADO
                }
            }

        /** true = GPX, false = KML, null = ninguno. Mira el principio del contenido y, si no basta, la extensión. */
        private fun formato(
            entrada: BufferedInputStream,
            nombre: String?,
        ): Boolean? {
            entrada.mark(CABECERA_BYTES)
            val cabecera = ByteArray(CABECERA_BYTES)
            var leidos = 0
            while (leidos < cabecera.size) {
                val n = entrada.read(cabecera, leidos, cabecera.size - leidos)
                if (n < 0) break
                leidos += n
            }
            entrada.reset()
            val texto = String(cabecera, 0, leidos, Charsets.ISO_8859_1).lowercase(Locale.ROOT)
            val extension = nombre?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            return when {
                "<gpx" in texto || ":gpx" in texto -> true
                "<kml" in texto || ":kml" in texto -> false
                extension == "gpx" && "<" in texto -> true
                extension == "kml" && "<" in texto -> false
                else -> null
            }
        }

        private fun nombreDeArchivo(archivo: String?): String? =
            archivo
                ?.substringBeforeLast('.')
                ?.replace('_', ' ')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(MAX_NOMBRE)

        /** Flujo que se corta al pasar de [tope] bytes y lo recuerda en [excedido]. */
        private class FlujoLimitado(
            entrada: InputStream,
            private val tope: Long,
        ) : FilterInputStream(entrada) {
            private var leidos = 0L
            var excedido = false
                private set

            override fun read(): Int {
                val b = super.read()
                if (b >= 0) contar(1)
                return b
            }

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
                val n = super.read(b, off, len)
                if (n > 0) contar(n)
                return n
            }

            private fun contar(n: Int) {
                leidos += n
                if (leidos > tope) {
                    excedido = true
                    throw IOException("Archivo demasiado grande")
                }
            }
        }

        companion object {
            /** Tamaño máximo de un archivo importable: 20 MB. */
            const val TOPE_BYTES = 20L * 1024 * 1024
            private const val CABECERA_BYTES = 4096
            private const val MAX_NOMBRE = 80
        }
    }
