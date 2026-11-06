package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.motor.edicion.FrasesOrdenes
import app.trombito.rallycopilot.motor.notas.FrasesNota
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Gramáticas cerradas para el reconocedor (Vosk): una lista de frases posibles y `[unk]` para lo que no sea
 * ninguna (sin él el reconocedor forzaría una orden falsa con cualquier ruido). Se generan desde el vocabulario del
 * motor (`FrasesNota` y la lista de distancias de las notas), de modo que lo que el copiloto canta es lo que entiende;
 * el texto reconocido pasa luego por `InterpreteOrdenes`, que es quien da el significado.
 *
 * Las órdenes de frase fija (preguntas, control del copiloto, mapa y cronómetro, «sí»/«no», ayuda) salen de
 * `FrasesOrdenes` del motor, que es también lo que `InterpreteOrdenes` reconoce.
 *
 * Los destinos («llévame a Sóller», «vamos a Sóller», «añade parada en Sóller») salen de los nombres de lugares que se
 * le pasan ([LugaresPronunciables]: municipios y localidades cuyas palabras conoce el modelo). Cada nombre añade tres
 * frases y la gramática no pasa de [MAX_FRASES]: si no caben todos, se quedan los primeros (los más importantes).
 *
 * Cobertura de las notas dictadas: dirección + grado (cifra en palabra o simplificado) + `larga`/`muy larga` +
 * `cierra`/`abre` + distancia, `horquilla` + dirección + distancia y dirección + `escuadra` + distancia; con `cuidado`
 * delante solo sin distancia.
 */
object GramaticaOrdenes {
    /** Lo que el reconocedor devuelve para lo que no está en la gramática. */
    const val DESCONOCIDO = "[unk]"

    /** Palabra de activación. */
    const val PALABRA_ACTIVACION = "copiloto"

    private val DIRECCIONES = listOf("izquierda", "derecha")
    private val GRADOS = (1..GRADO_MAX).map { FrasesNota.enPalabras(it) }
    private val SIMPLIFICADOS = listOf("muy cerrada", "cerrada", "media", "suave")
    private val LARGURAS = listOf("", "larga", "muy larga")
    private val TENDENCIAS = listOf("", "cierra", "abre")
    private val ORDENES =
        listOf("repite", "más pronto", "más tarde", "silencio", "habla", "marca aquí", "quita", "cuidado")

    // Copia de las distancias del generador de notas (privada en el motor); un test comprueba que el
    // intérprete las entiende.
    internal val DISTANCIAS_M =
        listOf(10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 150, 200, 250, 300, 400, 500, 600, 700, 800)
    private val DISTANCIAS = DISTANCIAS_M.map { FrasesNota.enPalabras(it) }

    private const val GRADO_MAX = 6

    /** Tope de frases de la gramática de las órdenes (compilarla en el móvil cuesta más cuanto más grande). */
    const val MAX_FRASES = 3_900

    private val FIJAS: List<String> by lazy {
        (ORDENES + FrasesOrdenes.frases() + DIRECCIONES.flatMap(::frasesDe)).distinct()
    }

    /**
     * Frases de las órdenes del botón y de «Copiloto» con su orden, sin `[unk]`: las fijas y, para cada nombre de
     * [lugares] (normalizado, con tildes), las que llevan destino hasta llenar [MAX_FRASES].
     */
    fun frasesOrdenes(lugares: List<String> = emptyList()): List<String> {
        val prefijos = FrasesOrdenes.PREFIJOS_IR + FrasesOrdenes.PREFIJO_PARADA
        val caben = ((MAX_FRASES - FIJAS.size) / prefijos.size).coerceAtLeast(0)
        val conDestino = lugares.distinct().take(caben).flatMap { lugar -> prefijos.map { "$it $lugar" } }
        return FIJAS + conDestino
    }

    private fun frasesDe(direccion: String): List<String> {
        val numericas = GRADOS.flatMap { notaCon("$direccion $it", matices()) }
        val simplificadas = SIMPLIFICADOS.flatMap { notaCon("$direccion $it", listOf("")) }
        return numericas + simplificadas + horquilla(direccion) + escuadra(direccion)
    }

    /** La nota de [giro] con cada matiz: sola, con «cuidado» delante y con cada distancia detrás. */
    private fun notaCon(
        giro: String,
        matices: List<String>,
    ): List<String> =
        matices.flatMap { matiz ->
            val nota = listOf(giro, matiz).filter(String::isNotEmpty).joinToString(" ")
            listOf(nota, "cuidado $nota") + DISTANCIAS.map { "$nota $it" }
        }

    private fun horquilla(direccion: String): List<String> =
        listOf("horquilla $direccion", "cuidado horquilla $direccion") +
            LARGURAS.drop(1).map { "horquilla $direccion $it" } +
            DISTANCIAS.map { "horquilla $direccion $it" }

    private fun escuadra(direccion: String): List<String> =
        listOf("$direccion escuadra", "cuidado $direccion escuadra") + DISTANCIAS.map { "$direccion escuadra $it" }

    private fun matices(): List<String> =
        LARGURAS.flatMap { largura ->
            TENDENCIAS.map { listOf(largura, it).filter(String::isNotEmpty).joinToString(" ") }
        }

    /** Gramática JSON de las órdenes: `["frase", ..., "[unk]"]`, con los destinos de [lugares] si los hay. */
    fun jsonOrdenes(lugares: List<String> = emptyList()): String = json(frasesOrdenes(lugares))

    /** Gramática JSON mínima de la palabra de activación: `["copiloto", "[unk]"]`. */
    fun jsonActivacion(): String = json(listOf(PALABRA_ACTIVACION))

    /** Todas las palabras distintas que usa la gramática de las órdenes (para comprobarlas contra el modelo). */
    fun palabras(lugares: List<String> = emptyList()): Set<String> =
        (frasesOrdenes(lugares) + PALABRA_ACTIVACION).flatMap { it.split(' ') }.toSet()

    private fun json(frases: List<String>): String =
        Json.encodeToString(JsonArray.serializer(), JsonArray((frases + DESCONOCIDO).map(::JsonPrimitive)))
}
