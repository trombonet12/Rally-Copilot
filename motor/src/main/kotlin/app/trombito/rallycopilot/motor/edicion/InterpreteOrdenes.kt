package app.trombito.rallycopilot.motor.edicion

import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.FrasesNota
import app.trombito.rallycopilot.motor.notas.Modificador
import java.text.Normalizer

/**
 * Interpreta el texto que devuelve un reconocedor de voz (ASR) y lo convierte en una [Orden]. Gramática del glosario:
 * `[cuidado] dirección grado [larga|muy larga] [cierra|abre] [distancia]`, con el grado en palabra («tres»), en cifra
 * («3») o simplificado («media»); `[cuidado] horquilla dirección`; y las órdenes sueltas (quita, cuidado, repite, más
 * pronto, más tarde, silencio, habla, marca aquí).
 *
 * Es tolerante a mayúsculas, tildes, puntuación y palabras de relleno («el», «a la», «por favor», «copiloto»). Devuelve
 * `null` si no entiende: sin dirección o sin grado, órdenes contradictorias o más de [RUIDO_MAX] palabras desconocidas.
 */
object InterpreteOrdenes {
    /** Palabras desconocidas que se toleran antes de rendirse. */
    const val RUIDO_MAX = 2

    private const val GRADO_MAX = 6
    private const val DISTANCIA_MIN_M = 10
    private const val DISTANCIA_MAX_M = 999

    // Copia de las distancias del generador de notas (GeneradorNotas.DISTANCIAS_M, privada).
    private val DISTANCIAS_M =
        listOf(10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 150, 200, 250, 300, 400, 500, 600, 700, 800)

    private val FRASES =
        listOf(
            " no hables " to " silencio ",
            " mas pronto " to " maspronto ",
            " mas temprano " to " maspronto ",
            " avisa antes " to " maspronto ",
            " mas tarde " to " mastarde ",
            " muy larga " to " muylarga ",
            " muy largo " to " muylarga ",
            " muy cerrada " to " muycerrada ",
            " muy cerrado " to " muycerrada ",
            " otra vez " to " repite ",
        )

    private val RELLENO =
        (
            "el la los las a al de del en por favor porfa venga vale oye copiloto eh ah ee mmm hm pues ok entonces " +
                "ahora esa ese esta este eso esto nota curva metros metro me se y es una un lo te puedes puede " +
                "podrias pon pone ponme ponla ponle gracias hola que con vuelve ya ultima ultimo punto aqui ahi " +
                "mira para avisa uh um"
        ).split(' ').toSet()

    private val COMANDOS: Map<String, Orden> =
        mapOf(
            "quita" to Orden.Quitar,
            "quitala" to Orden.Quitar,
            "quitalo" to Orden.Quitar,
            "borra" to Orden.Quitar,
            "borrala" to Orden.Quitar,
            "borralo" to Orden.Quitar,
            "elimina" to Orden.Quitar,
            "eliminala" to Orden.Quitar,
            "repite" to Orden.Repite,
            "repitelo" to Orden.Repite,
            "repetir" to Orden.Repite,
            "maspronto" to Orden.MasPronto,
            "adelanta" to Orden.MasPronto,
            "adelantalo" to Orden.MasPronto,
            "mastarde" to Orden.MasTarde,
            "retrasa" to Orden.MasTarde,
            "retrasalo" to Orden.MasTarde,
            "silencio" to Orden.Silencio,
            "calla" to Orden.Silencio,
            "callate" to Orden.Silencio,
            "mute" to Orden.Silencio,
            "habla" to Orden.Habla,
            "hablar" to Orden.Habla,
            "hables" to Orden.Habla,
            "marca" to Orden.MarcaAqui,
            "marcar" to Orden.MarcaAqui,
        )

    private val MODIFICADORES =
        mapOf(
            "larga" to Modificador.LARGA,
            "largo" to Modificador.LARGA,
            "muylarga" to Modificador.MUY_LARGA,
            "cierra" to Modificador.CIERRA,
            "cierre" to Modificador.CIERRA,
            "abre" to Modificador.ABRE,
        )

    private val GRADOS_SIMPLIFICADOS =
        mapOf("muycerrada" to 1, "cerrada" to 2, "cerrado" to 2, "media" to 3, "suave" to 5)

    private val NUMEROS: Map<String, Int> =
        buildMap {
            val valores = (1..29) + (30..90 step 10) + (200..900 step 100) + 100
            valores.forEach { put(normalizar(FrasesNota.enPalabras(it)), it) }
            put("ciento", 100)
        }

    private sealed interface Ficha {
        data class Giro(
            val direccion: Direccion,
        ) : Ficha

        data object Horquilla : Ficha

        data object Aviso : Ficha

        data class Grado(
            val valor: Int,
        ) : Ficha

        data class Matiz(
            val modificador: Modificador,
        ) : Ficha

        data class Numero(
            val valor: Int,
        ) : Ficha

        data class Comando(
            val orden: Orden,
        ) : Ficha

        data object Relleno : Ficha

        data object Desconocida : Ficha
    }

    /** La orden que dice [texto], o `null` si no se entiende. */
    fun interpretar(texto: String): Orden? {
        val fichas = fichas(texto)
        val limpio = fichas.count { it == Ficha.Desconocida } <= RUIDO_MAX && fichas.any { it !is Ficha.Relleno }
        return if (limpio) resolver(fichas) else null
    }

    private fun resolver(fichas: List<Ficha>): Orden? {
        val giros = fichas.filterIsInstance<Ficha.Giro>().map { it.direccion }.distinct()
        val comandos = fichas.filterIsInstance<Ficha.Comando>().map { it.orden }.distinct()
        val aviso = fichas.contains(Ficha.Aviso)
        return when {
            giros.size > 1 -> null
            giros.size == 1 -> if (comandos.isEmpty()) nota(fichas, giros.single(), aviso) else null
            fichas.any { it is Ficha.Horquilla || it is Ficha.Grado || it is Ficha.Matiz || it is Ficha.Numero } -> null
            aviso -> if (comandos.all { it == Orden.MarcaAqui }) Orden.Cuidado else null
            else -> comandos.singleOrNull()
        }
    }

    private fun nota(
        fichas: List<Ficha>,
        direccion: Direccion,
        cuidado: Boolean,
    ): Orden.Nota? {
        val horquilla = fichas.contains(Ficha.Horquilla)
        val inicio = fichas.indexOfLast { it is Ficha.Giro || it == Ficha.Horquilla }
        val numeros = fichas.withIndex().filter { it.value is Ficha.Numero }
        val grados = fichas.filterIsInstance<Ficha.Grado>().map { it.valor }.distinct()
        val matices = fichas.filterIsInstance<Ficha.Matiz>().map { it.modificador }.toSet()
        val valida =
            numeros.all { it.index > inicio } &&
                grados.size <= 1 &&
                !(horquilla && grados.isNotEmpty()) &&
                !(Modificador.CIERRA in matices && Modificador.ABRE in matices)
        val valores = numeros.map { (it.value as Ficha.Numero).valor }
        val porPalabra = grados.singleOrNull()
        val porNumero = valores.firstOrNull()?.takeIf { it in 1..GRADO_MAX }
        val grado = if (horquilla) null else porPalabra ?: porNumero
        val dictado = if (horquilla || porPalabra != null) valores else valores.drop(1)
        val completa = valida && (horquilla || grado != null)
        return if (completa) {
            Orden.Nota(direccion, grado, modificadores(matices, horquilla), distancia(dictado), cuidado)
        } else {
            null
        }
    }

    private fun modificadores(
        matices: Set<Modificador>,
        horquilla: Boolean,
    ): Set<Modificador> {
        val largura =
            when {
                Modificador.MUY_LARGA in matices -> setOf(Modificador.MUY_LARGA)
                Modificador.LARGA in matices -> setOf(Modificador.LARGA)
                else -> emptySet()
            }
        val tendencia = if (horquilla) emptySet() else matices.intersect(setOf(Modificador.CIERRA, Modificador.ABRE))
        return largura + tendencia
    }

    /** Suma las cifras dictadas («ciento» «veinte» = 120) y la ajusta hacia abajo a la lista de distancias. */
    private fun distancia(valores: List<Int>): Int? {
        val total = valores.sum()
        return if (valores.isEmpty() || total < DISTANCIA_MIN_M || total > DISTANCIA_MAX_M) {
            null
        } else {
            DISTANCIAS_M.last { it <= total }
        }
    }

    private fun fichas(texto: String): List<Ficha> {
        var tira = " ${normalizar(texto)} "
        FRASES.forEach { (frase, sustituto) -> tira = tira.replace(frase, sustituto) }
        val palabras = tira.trim().split(' ').filter { it.isNotEmpty() }
        return palabras.map(::ficha)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun ficha(palabra: String): Ficha =
        when {
            palabra.startsWith("izquierd") -> Ficha.Giro(Direccion.IZQUIERDA)
            palabra == "derecha" || palabra == "derechas" -> Ficha.Giro(Direccion.DERECHA)
            palabra.startsWith("horquilla") -> Ficha.Horquilla
            palabra == "cuidado" || palabra == "ojo" -> Ficha.Aviso
            palabra in MODIFICADORES -> Ficha.Matiz(MODIFICADORES.getValue(palabra))
            palabra in GRADOS_SIMPLIFICADOS -> Ficha.Grado(GRADOS_SIMPLIFICADOS.getValue(palabra))
            palabra in NUMEROS -> Ficha.Numero(NUMEROS.getValue(palabra))
            palabra.length <= CIFRAS_MAX && palabra.all { it.isDigit() } -> Ficha.Numero(palabra.toInt())
            palabra in COMANDOS -> Ficha.Comando(COMANDOS.getValue(palabra))
            palabra in RELLENO -> Ficha.Relleno
            else -> Ficha.Desconocida
        }

    private const val CIFRAS_MAX = 3

    /** Minúsculas, sin tildes ni signos: solo letras y cifras separadas por un espacio. */
    private fun normalizar(texto: String): String =
        Normalizer
            .normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
