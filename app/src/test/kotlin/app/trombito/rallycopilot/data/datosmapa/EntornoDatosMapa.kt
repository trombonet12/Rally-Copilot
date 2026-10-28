package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Archivos de un paquete de prueba (id -> contenido). Los ids y nombres son los reales del repo de datos. */
object PaqueteDePrueba {
    val NOMBRES =
        mapOf(
            "vectorial" to "mallorca.pmtiles",
            "estilo" to "estilo-mapa.zip",
            "vias" to "vias-mallorca.bin",
            "satelite" to "pnoa-mallorca-z16.pmtiles",
        )

    fun aleatorio(
        semilla: Long,
        tamano: Int = 150_000,
    ): ByteArray = ByteArray(tamano).also { Random(semilla).nextBytes(it) }

    fun zip(entradas: Map<String, String>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            entradas.forEach { (nombre, texto) ->
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(texto.toByteArray())
                zip.closeEntry()
            }
        }
        return salida.toByteArray()
    }

    fun estiloZip(marca: String = "v1"): ByteArray =
        zip(
            mapOf(
                "vectorial.json" to """{"marca":"$marca","url":"pmtiles://{{DIR}}/mallorca.pmtiles"}""",
                "satelite.json" to """{"marca":"$marca","url":"pmtiles://{{DIR}}/pnoa-mallorca-z16.pmtiles"}""",
                "glyphs/Noto/0-255.pbf" to "glifos $marca",
            ),
        )

    /** Contenido base de cada id; [marca] cambia los bytes para simular otra versión. */
    fun contenidos(marca: String = "v1"): Map<String, ByteArray> {
        val desplazamiento = marca.hashCode().toLong()
        return mapOf(
            "vectorial" to aleatorio(1 + desplazamiento),
            "estilo" to estiloZip(marca),
            "vias" to aleatorio(2 + desplazamiento, 40_000),
            "satelite" to aleatorio(3 + desplazamiento),
        )
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/**
 * Entorno de pruebas: servidor local que se hace pasar por GitHub, carpeta de datos y [ConfigDatosMapa] cuyas
 * conexiones se desvían al servidor local (el manifest sigue pasando por la validación real de `LectorManifest`).
 */
class EntornoDatosMapa(
    raiz: File,
) : AutoCloseable {
    val servidor = ServidorFalso()
    val mapa = File(raiz, "mapa")
    var bytesLibres = Long.MAX_VALUE / 2

    val config: ConfigDatosMapa =
        ConfigDatosMapa(
            urlManifest = URL_MANIFEST,
            directorioMapa = mapa,
            userAgent = "RallyCopilot/test",
            bytesLibres = { bytesLibres },
            abrirConexion = { url ->
                URL("http://127.0.0.1:${servidor.puerto}${url.path}").openConnection() as HttpURLConnection
            },
        )

    private val rutaManifest = URL(URL_MANIFEST).path

    fun archivo(
        id: String,
        bytes: ByteArray,
        version: String,
    ): ArchivoDatos {
        val nombre = PaqueteDePrueba.NOMBRES[id] ?: "$id.bin"
        return ArchivoDatos(
            id = id,
            nombre = nombre,
            url = "https://github.com/trombonet12/rally-copilot-data/releases/download/$version/$nombre",
            bytes = bytes.size.toLong(),
            sha256 = PaqueteDePrueba.sha256(bytes),
            version = version,
            atribucion = "© OpenStreetMap contributors",
            licencia = "ODbL",
        )
    }

    /**
     * Publica un manifest y sirve sus archivos. Los ids de [corruptos] se sirven con el primer byte cambiado: el
     * manifest anuncia el hash bueno pero la descarga trae otra cosa.
     */
    fun publicar(
        version: String,
        contenidos: Map<String, ByteArray> = PaqueteDePrueba.contenidos(version),
        corruptos: Set<String> = emptySet(),
    ): List<ArchivoDatos> {
        val archivos = contenidos.map { (id, bytes) -> archivo(id, bytes, version) }
        archivos.forEach { a ->
            val original = contenidos.getValue(a.id)
            val servido = if (a.id in corruptos) original.copyOf().also { it[0] = (it[0] + 1).toByte() } else original
            servidor.servir(URL(a.url).path, servido)
        }
        val json =
            buildString {
                append("""{"esquema":1,"version":"$version","generado":"2026-10-28T00:00:00Z","archivos":[""")
                append(archivos.joinToString(",") { a -> manifestDe(a) })
                append("]}")
            }
        servidor.servir(rutaManifest, json.toByteArray())
        return archivos
    }

    private fun manifestDe(a: ArchivoDatos) =
        """{"id":"${a.id}","nombre":"${a.nombre}","url":"${a.url}","bytes":${a.bytes},"sha256":"${a.sha256}",""" +
            """"version":"${a.version}","atribucion":"${a.atribucion}","licencia":"${a.licencia}"}"""

    fun publicarManifestCrudo(json: String) {
        servidor.servir(rutaManifest, json.toByteArray())
    }

    fun rutaDe(archivo: ArchivoDatos): String = URL(archivo.url).path

    override fun close() = servidor.close()
}
