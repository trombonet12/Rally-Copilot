package app.trombito.rallycopilot.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainDependencyRuleTest {
    private val root: File = File("src/main/kotlin/app/trombito/rallycopilot")

    private data class Source(
        val relPath: String,
        val imports: List<String>,
    )

    private fun scan(): List<Source> {
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
        return files
            .map { f ->
                val lines = f.readLines().map { it.trim() }
                Source(
                    relPath = f.relativeTo(root).invariantSeparatorsPath,
                    imports = lines.filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() },
                )
            }.toList()
    }

    private fun violations(
        under: String?,
        forbidden: List<String>,
        exceptUnder: List<String> = emptyList(),
        allowed: Set<Pair<String, String>> = emptySet(),
    ): List<String> =
        scan()
            .filter { under == null || it.relPath.startsWith("$under/") }
            .filter { s -> exceptUnder.none { s.relPath.startsWith("$it/") } }
            .flatMap { s ->
                s.imports
                    .filter { imp -> forbidden.any { imp.startsWith(it) } }
                    .filter { imp -> (s.relPath to imp) !in allowed }
                    .map { imp -> "${s.relPath}: import $imp" }
            }

    private fun assertNone(violations: List<String>) {
        assertTrue("Violaciones de dependencia:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun scanFindsSources() {
        val all = scan()
        assertTrue("El escaneo no encontró archivos en ${root.absolutePath}", all.isNotEmpty())
        assertTrue("No hay archivos en domain/", all.any { it.relPath.startsWith("domain/") })
    }

    @Test
    fun domainHasNoFrameworkImports() {
        val forbidden =
            listOf(
                "android.",
                "androidx.",
                "dagger",
                "javax.inject",
            )
        assertNone(violations("domain", forbidden))
    }

    @Test
    fun domainDoesNotImportDataNorUi() {
        assertNone(violations("domain", listOf("app.trombito.rallycopilot.data", "app.trombito.rallycopilot.ui")))
    }

    @Test
    fun uiDoesNotImportData() {
        assertNone(violations("ui", listOf("app.trombito.rallycopilot.data")))
    }

    @Test
    fun dataDoesNotImportUi() {
        assertNone(violations("data", listOf("app.trombito.rallycopilot.ui"), allowed = DATA_A_UI_PERMITIDO))
    }

    @Test
    fun theDataToUiAllowListHasNoStaleEntries() {
        val porArchivo = scan().associate { it.relPath to it.imports }
        val obsoletas = DATA_A_UI_PERMITIDO.filter { (archivo, import) -> import !in porArchivo[archivo].orEmpty() }
        assertTrue("Entradas de la lista blanca que ya no existen: $obsoletas", obsoletas.isEmpty())
    }

    private companion object {
        /**
         * Lista blanca data -> ui. Solo Activities que el sistema lanza a través de un PendingIntent (p. ej. desde la
         * notificación del servicio de navegación) y que por tanto la capa de datos debe nombrar; cualquier otra
         * entrada es una violación de la frontera.
         */
        val DATA_A_UI_PERMITIDO: Set<Pair<String, String>> = emptySet()
    }
}
