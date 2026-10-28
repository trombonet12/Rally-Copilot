package app.trombito.rallycopilot.ui.comun

import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Banco mínimo para probar composables en la JVM con Robolectric sin `ui-test`: monta el contenido en una actividad,
 * deja pasar los fotogramas y lee el árbol de semántica sin fusionar.
 */
@OptIn(InternalComposeUiApi::class)
class BancoCompose {
    private val controlador = Robolectric.buildActivity(ComponentActivity::class.java).setup().also { vivos += it }

    fun poner(contenido: @Composable () -> Unit) {
        controlador.get().setContent(content = contenido)
        reposo()
    }

    /** Deja correr composición, medida y dibujo pendientes. */
    fun reposo() = dejarCorrer()

    fun raiz(): SemanticsNode {
        val vista = buscarRaiz(controlador.get().window.decorView)
        return checkNotNull(vista) { "No hay contenido Compose" }.semanticsOwner.unmergedRootSemanticsNode
    }

    fun nodos(): List<SemanticsNode> = aplanar(raiz())

    fun conEtiqueta(etiqueta: String): SemanticsNode =
        checkNotNull(nodos().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == etiqueta }) {
            "No hay nodo con etiqueta $etiqueta"
        }

    fun etiquetas(prefijo: String): List<String> =
        nodos()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            .filter { it.startsWith(prefijo) }

    /** Textos de un nodo y de todo lo que cuelga de él. */
    fun textos(nodo: SemanticsNode): List<String> =
        aplanar(nodo).flatMap { n ->
            n.config
                .getOrNull(SemanticsProperties.Text)
                ?.map { it.text }
                .orEmpty()
        }

    fun hayDesplazable(): Boolean =
        nodos().any {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
                it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) ||
                it.config.contains(SemanticsActions.ScrollBy)
        }

    fun pulsar(nodo: SemanticsNode) {
        val accion = checkNotNull(nodo.config.getOrNull(SemanticsActions.OnClick)) { "El nodo no es pulsable" }
        accion.action?.invoke()
        reposo()
    }

    /** Primer nodo pulsable con ese texto dentro del nodo dado (o en todo el árbol). */
    fun pulsableConTexto(
        texto: String,
        dentroDe: SemanticsNode = raiz(),
    ): SemanticsNode =
        checkNotNull(
            aplanar(dentroDe).firstOrNull { n ->
                n.config.contains(SemanticsActions.OnClick) && textos(n).any { it == texto }
            },
        ) { "No hay botón con el texto $texto" }

    private fun aplanar(nodo: SemanticsNode): List<SemanticsNode> = listOf(nodo) + nodo.children.flatMap(::aplanar)

    private fun buscarRaiz(vista: View): RootForTest? =
        when {
            vista is RootForTest -> vista
            vista is ViewGroup -> (0 until vista.childCount).firstNotNullOfOrNull { buscarRaiz(vista.getChildAt(it)) }
            else -> null
        }

    companion object {
        private const val FOTOGRAMAS = 4
        private const val MS_FOTOGRAMA = 20L
        private const val MAX_TAREAS = 10_000
        private val vivos = mutableListOf<ActivityController<ComponentActivity>>()

        private fun dejarCorrer() {
            repeat(FOTOGRAMAS) { avanzar(MS_FOTOGRAMA) }
        }

        /**
         * Como `idleFor`, pero tarea a tarea. Con una hoja inferior abierta, `idleFor` de Robolectric no vuelve
         * (minutos de CPU con el reloj virtual disparado); ejecutar las tareas vencidas una a una sí, y avisa si no
         * se calma.
         */
        private fun avanzar(ms: Long) {
            val bucle = shadowOf(Looper.getMainLooper())
            val fin = SystemClock.uptimeMillis() + ms
            var tareas = 0
            while (true) {
                val siguiente = bucle.nextScheduledTaskTime
                if (siguiente == Duration.ZERO || siguiente.toMillis() > fin) break
                check(++tareas <= MAX_TAREAS) { "El bucle principal no se calma: más de $MAX_TAREAS tareas en $ms ms" }
                bucle.runOneTask()
            }
            val resto = fin - SystemClock.uptimeMillis()
            if (resto > 0) ShadowSystemClock.advanceBy(Duration.ofMillis(resto))
        }

        /** Vacía lo pendiente y cierra las actividades de este test (lo llama [LimpiezaCompose]). */
        internal fun cerrarTodo() {
            dejarCorrer()
            vivos.forEach { it.pause().stop().destroy() }
            vivos.clear()
            dejarCorrer()
        }
    }
}

/**
 * Regla obligatoria en toda clase que use [BancoCompose]. Al acabar cada test deja correr el bucle
 * principal y cierra las actividades. Sin ella, un cambio de estado hecho tras el último reposo deja pendiente el
 * aviso global de Compose (`Snapshot.sendApplyNotifications`); Robolectric descarta ese mensaje al reiniciar el
 * bucle, el aviso no se vuelve a enviar y ningún test posterior de la JVM recompone tras un toque.
 */
class LimpiezaCompose : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } finally {
                    BancoCompose.cerrarTodo()
                }
            }
        }
}
