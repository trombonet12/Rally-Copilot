package app.trombito.rallycopilot.motor.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class LectorManifestTest {
    @Test
    fun `lee un manifest valido con los cuatro archivos`() {
        val manifest = LectorManifest.leer(manifestJson())

        assertEquals(1, manifest.esquema)
        assertEquals("2026.10.28-1", manifest.version)
        assertEquals("2026-10-28T10:15:00Z", manifest.generado)
        assertEquals(listOf("vectorial", "estilo", "vias", "satelite"), manifest.archivos.map { it.id })
        val vectorial = manifest.archivo("vectorial")
        assertNotNull(vectorial)
        assertEquals("mallorca.pmtiles", vectorial?.nombre)
        assertEquals(412_345_678L, vectorial?.bytes)
        assertEquals("ODbL-1.0", vectorial?.licencia)
        assertEquals("2026.10.20-1", manifest.archivo("satelite")?.version)
    }

    @Test
    fun `archivo devuelve null si el id no existe`() {
        assertNull(LectorManifest.leer(manifestJson()).archivo("inexistente"))
    }

    @Test
    fun `ignora claves desconocidas`() {
        val json = manifestJson().replaceFirst("\"esquema\": 1,", "\"esquema\": 1, \"futuro\": {\"a\": 1},")
        assertEquals(4, LectorManifest.leer(json).archivos.size)
    }

    @Test
    fun `acepta ids adicionales a los obligatorios`() {
        val extra = archivoJson("extra", "extra.bin", sha = SHA_A)
        val json = manifestJson(archivos = TODOS + extra)
        assertEquals(5, LectorManifest.leer(json).archivos.size)
    }

    @Test
    fun `rechaza json roto`() {
        assertInvalido("{ esto no es json")
        assertInvalido("")
    }

    @Test
    fun `rechaza un json sin campos obligatorios`() {
        assertInvalido("{\"esquema\": 1}")
    }

    @Test
    fun `rechaza un esquema distinto de 1`() {
        assertInvalido(manifestJson(esquema = 2))
        assertInvalido(manifestJson(esquema = 0))
    }

    @Test
    fun `rechaza si falta alguno de los ids obligatorios`() {
        for (faltante in listOf("vectorial", "estilo", "vias", "satelite")) {
            val sin = TODOS.filterNot { it.contains("\"id\": \"$faltante\"") }
            assertInvalido(manifestJson(archivos = sin), faltante)
        }
    }

    @Test
    fun `rechaza ids repetidos`() {
        assertInvalido(manifestJson(archivos = TODOS + archivoJson("vias", "otro.tsv.gz", sha = SHA_B)), "vias")
    }

    @Test
    fun `rechaza bytes no positivos`() {
        assertInvalido(conVias(bytes = 0))
        assertInvalido(conVias(bytes = -5))
    }

    @Test
    fun `rechaza sha256 mal formado`() {
        val malos =
            listOf(
                "abc",
                SHA_A.uppercase(),
                SHA_A.dropLast(1),
                SHA_A + "0",
                SHA_A.dropLast(1) + "g",
            )
        for (sha in malos) {
            val json = conVias(sha = sha)
            assertInvalido(json, "sha256")
        }
    }

    @Test
    fun `rechaza urls que no son de github`() {
        val urls = listOf("http://github.com/x/y.bin", "https://evil.example.com/y.bin", "ftp://github.com/y", "")
        for (url in urls) {
            val json = conVias(url = url)
            assertInvalido(json, "url")
        }
    }

    @Test
    fun `rechaza nombres con separadores o puntos dobles`() {
        val nombres = listOf("a/b.bin", "a\\b.bin", "..", "../x.bin", "x..y")
        for (nombre in nombres) {
            val json = conVias(nombre = nombre)
            assertInvalido(json, "nombre")
        }
    }

    @Test
    fun `rechaza urls de otro repositorio de github`() {
        val urls =
            listOf(
                "https://github.com/otro/repo/releases/download/v1/vias-mallorca.tsv.gz",
                "https://github.com/trombonet12/otro-repo/releases/download/v1/vias-mallorca.tsv.gz",
                "https://github.com/trombonet12/rally-copilot-data/raw/main/vias-mallorca.tsv.gz",
                "https://github.com/trombonet12/rally-copilot-data/releases/latest/download/vias.bin",
                "https://github.com.evil.example/trombonet12/rally-copilot-data/releases/download/v1/x.bin",
            )
        for (url in urls) assertInvalido(conVias(url = url), "url")
    }

    @Test
    fun `rechaza nombres fuera del patron minusculas digitos punto guion`() {
        val nombres = listOf("Vias.bin", ".oculto", "-x.bin", "con espacio.bin", "ñandu.bin", "a:b.bin")
        for (nombre in nombres) assertInvalido(conVias(nombre = nombre), "nombre")
    }

    @Test
    fun `rechaza nombres repetidos entre archivos distintos`() {
        val repetido = archivoJson("extra", "mallorca.pmtiles", sha = SHA_B)
        assertInvalido(manifestJson(archivos = TODOS + repetido), "repetido")
    }

    @Test
    fun `esMasNuevoQue sin manifest previo es verdadero`() {
        assertEquals(true, LectorManifest.leer(manifestJson()).esMasNuevoQue(null))
    }

    @Test
    fun `esMasNuevoQue es falso si todos los sha256 coinciden aunque cambie la fecha o la version global`() {
        val a = LectorManifest.leer(manifestJson())
        val b = LectorManifest.leer(manifestJson(version = "2026.11.01-1", generado = "2026-11-01T00:00:00Z"))
        assertEquals(false, b.esMasNuevoQue(a))
    }

    @Test
    fun `esMasNuevoQue es verdadero si cambia un sha256`() {
        val a = LectorManifest.leer(manifestJson())
        val b = LectorManifest.leer(conVias(sha = SHA_B))
        assertEquals(true, b.esMasNuevoQue(a))
    }

    @Test
    fun `esMasNuevoQue es verdadero si hay un archivo nuevo`() {
        val a = LectorManifest.leer(manifestJson())
        val b = LectorManifest.leer(manifestJson(archivos = TODOS + archivoJson("extra", "extra.bin", sha = SHA_A)))
        assertEquals(true, b.esMasNuevoQue(a))
    }

    private fun assertInvalido(
        json: String,
        fragmentoMensaje: String? = null,
    ) {
        val e = assertFailsWith<ManifestInvalido> { LectorManifest.leer(json) }
        if (fragmentoMensaje != null) {
            assertEquals(true, e.message.orEmpty().contains(fragmentoMensaje))
        }
    }

    /** Manifest válido en el que el archivo `vias` se sustituye por una variante. */
    private fun conVias(
        nombre: String = "vias-mallorca.tsv.gz",
        bytes: Long = 1_000,
        sha: String = SHA_C,
        url: String = "$BASE/$nombre",
    ) = manifestJson(archivos = TODOS.reemplazando(archivoJson("vias", nombre, bytes, sha, url)))

    private fun List<String>.reemplazando(nuevo: String): List<String> {
        val id = Regex("\"id\": \"([^\"]+)\"").find(nuevo)!!.groupValues[1]
        return map { if (it.contains("\"id\": \"$id\"")) nuevo else it }
    }

    private companion object {
        const val SHA_A = "a3f1c2d4e5b60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        const val SHA_B = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val SHA_C = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        const val BASE = "https://github.com/trombonet12/rally-copilot-data/releases/download/datos-2026.10.28-1"

        fun archivoJson(
            id: String,
            nombre: String,
            bytes: Long = 1_000,
            sha: String = SHA_A,
            url: String = "$BASE/$nombre",
        ) = "{\"id\": \"$id\", \"nombre\": \"${nombre.replace("\\", "\\\\")}\", " +
            "\"url\": \"$url\", \"bytes\": $bytes, " +
            "\"sha256\": \"$sha\", \"version\": \"2026.10.28-1\", " +
            "\"atribucion\": \"© OpenStreetMap contributors\", \"licencia\": \"ODbL-1.0\"}"

        val TODOS =
            listOf(
                archivoJson("vectorial", "mallorca.pmtiles", bytes = 412_345_678, sha = SHA_A),
                archivoJson("estilo", "estilo-mapa.zip", bytes = 2_345_678, sha = SHA_B),
                archivoJson("vias", "vias-mallorca.tsv.gz", bytes = 9_876_543, sha = SHA_C),
                archivoJson("satelite", "pnoa-mallorca-z16.pmtiles", bytes = 900_000_000, sha = SHA_A)
                    .replace("2026.10.28-1\", \"atrib", "2026.10.20-1\", \"atrib"),
            )

        fun manifestJson(
            esquema: Int = 1,
            version: String = "2026.10.28-1",
            generado: String = "2026-10-28T10:15:00Z",
            archivos: List<String> = TODOS,
        ) = "{\"esquema\": $esquema, \"version\": \"$version\", \"generado\": \"$generado\", " +
            "\"archivos\": [${archivos.joinToString(",\n")}]}"
    }
}
