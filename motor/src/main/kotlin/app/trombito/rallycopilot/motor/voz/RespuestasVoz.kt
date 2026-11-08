package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.edicion.TipoLugar
import app.trombito.rallycopilot.motor.notas.FrasesNota
import kotlin.math.roundToInt

/**
 * Frases cortas con las que el copiloto responde a las preguntas de voz y confirma las órdenes, en español y con las
 * cifras en palabras ([FormatoHablado]). Son funciones puras: de dónde salen los datos lo decide quien las llama.
 */
@Suppress("TooManyFunctions") // una función por frase que dice el copiloto
object RespuestasVoz {
    const val SIN_DESTINO = "no hay destino"
    const val NO_SE = "no lo sé"
    const val SIN_CRONO = "no hay crono"
    const val AUN_NO_HAS_SALIDO = "aún no has salido"
    const val NO_ENTENDIDO = "no te he entendido, di ayuda"

    const val SIN_CASA = "aún no tienes casa; mantén pulsado el mapa y elige casa"
    const val SIN_POSICION = "aún no tengo tu posición"
    const val SIN_DATOS_RUTA = "no tengo los datos de rutas"
    const val RUTA_FALLIDA = "no he podido calcular la ruta"
    const val SIN_OTRA_OPCION = "no hay otra opción"
    const val SIN_RUTA = "no hay ruta"
    const val SIN_RUTA_PARA_PARADA = "no hay ruta a la que añadir una parada"
    const val DEMASIADAS_PARADAS = "ya hay demasiadas paradas"
    const val PEDIR_CANCELAR_RUTA = "di sí para cancelar la ruta"
    const val RUTA_CANCELADA = "ruta cancelada"

    /** Las cinco órdenes más útiles, para «ayuda». */
    const val AYUDA = "puedes decir: cuánto falta, qué límite hay, cómo voy, repite o silencio"

    private const val HORAS_MEDIO_DIA = 12
    private const val MEDIA_HORA = 30
    private const val CUARTO = 15
    private const val MIN_POR_HORA = 60
    private val REF = Regex("^([A-Za-z]+)[- ]?(\\d+)([A-Za-z]?)$")

    /** «faltan doce kilómetros, quince minutos»; las distancias cortas van en metros redondeados. */
    fun cuantoFalta(
        distanciaM: Double,
        tiempoS: Double,
    ): String {
        val (distancia, singular) = FormatoHablado.distancia(distanciaM)
        return "${if (singular) "falta" else "faltan"} $distancia, ${FormatoHablado.duracion(tiempoS)}"
    }

    /** «llegas a las cuatro y diez», «a la una y cuarto», «a las diez menos cuarto», «a las dos en punto». */
    fun horaLlegada(
        hora: Int,
        minuto: Int,
    ): String {
        val horaDicha = if (minuto > MEDIA_HORA) hora + 1 else hora
        val reloj = horaDicha % HORAS_MEDIO_DIA
        val h = if (reloj == 0) HORAS_MEDIO_DIA else reloj
        val articulo = if (h == 1) "la" else "las"
        val palabra = if (h == 1) "una" else FrasesNota.enPalabras(h)
        val minutos =
            when {
                minuto == 0 -> "en punto"
                minuto == CUARTO -> "y cuarto"
                minuto == MEDIA_HORA -> "y media"
                minuto == MIN_POR_HORA - CUARTO -> "menos cuarto"
                minuto < MEDIA_HORA -> "y ${FrasesNota.enPalabras(minuto)}"
                else -> "menos ${FrasesNota.enPalabras(MIN_POR_HORA - minuto)}"
            }
        return "llegas a $articulo $palabra $minutos"
    }

    /** «el límite es noventa» o «no lo sé» si la vía no tiene límite conocido. */
    fun limite(kmh: Int?): String = if (kmh == null) NO_SE else "el límite es ${FrasesNota.enPalabras(kmh)}"

    /**
     * «estás en la Ma once, carretera de Sóller, kilómetro veintitrés»; con solo uno de los dos, ese; sin ninguno,
     * «no lo sé». El [km] (punto kilométrico de la carretera, redondeado al entero) solo se dice si hay carretera.
     */
    fun dondeEstoy(
        nombre: String?,
        ref: String?,
        km: Double? = null,
    ): String {
        val carretera = ref?.let(::refHablada)
        val calle = nombre?.trim()?.takeIf { it.isNotEmpty() }
        val punto = km?.takeIf { it.isFinite() && it >= 0.0 }?.let { "kilómetro ${kilometroHablado(it)}" }
        return when {
            carretera != null -> listOfNotNull("estás en la $carretera", calle, punto).joinToString(", ")
            calle != null -> "estás en $calle"
            else -> NO_SE
        }
    }

    private fun kilometroHablado(km: Double): String = FormatoHablado.numero(km.roundToInt())

    /** `Ma-2141` -> «Ma dos mil ciento cuarenta y uno»; solo la primera de varias (`Ma-13;Ma-13A`). */
    private fun refHablada(ref: String): String? {
        val primera = ref.substringBefore(';').trim()
        val partes = REF.matchEntire(primera) ?: return primera.takeIf { it.isNotEmpty() }
        val (letras, cifras, sufijo) = partes.destructured
        val numero = cifras.toIntOrNull()?.let(FormatoHablado::numero) ?: cifras
        return listOf(letras, numero, sufijo.lowercase()).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * «uno veinte, tres segundos por delante»: el tiempo del tramo y la diferencia con el mejor ([deltaMs] negativo =
     * más rápido; `null` si no hay con qué comparar).
     */
    fun comoVoy(
        tiempoMs: Long,
        deltaMs: Long?,
    ): String {
        val tiempo = FormatoHablado.tiempo(tiempoMs)
        return if (deltaMs == null) tiempo else "$tiempo, ${FormatoHablado.diferencia(deltaMs)}"
    }

    /** «modo notas», «solo avisos», «modo completo»; `null` en silencio (no se dice nada). */
    fun modo(modo: ModoCopiloto): String? =
        when (modo) {
            ModoCopiloto.SILENCIO -> null
            ModoCopiloto.SOLO_ALERTAS -> "solo avisos"
            ModoCopiloto.NOTAS -> "modo notas"
            ModoCopiloto.COMPLETO -> "modo completo"
        }

    /** «volumen ciento diez»; en el tope, «volumen máximo» o «volumen mínimo». */
    fun volumen(
        porcentaje: Int,
        subir: Boolean,
        alLimite: Boolean,
    ): String =
        when {
            alLimite -> if (subir) "volumen máximo" else "volumen mínimo"
            else -> "volumen ${FormatoHablado.numero(porcentaje)}"
        }

    /** «zoom más cerca» o «zoom más lejos»; en el tope, «zoom máximo» o «zoom mínimo». */
    fun zoom(
        acercar: Boolean,
        alLimite: Boolean,
    ): String =
        when {
            alLimite -> if (acercar) "zoom máximo" else "zoom mínimo"
            acercar -> "zoom más cerca"
            else -> "zoom más lejos"
        }

    /** «llevándote a Sóller, veinticinco minutos». */
    fun llevandoteA(
        nombre: String,
        tiempoS: Double,
    ): String = "llevándote a $nombre, ${FormatoHablado.duracion(tiempoS)}"

    /** «no encuentro Sóller». */
    fun lugarNoEncontrado(lugar: String): String = "no encuentro $lugar"

    /** «parada añadida: Inca». */
    fun paradaAnadida(nombre: String): String = "parada añadida: $nombre"

    /** «no encuentro ninguna gasolinera cerca» / «ningún hospital». */
    fun sinCercano(tipo: TipoLugar): String =
        "no encuentro ${if (tipo.femenino) "ninguna" else "ningún"} ${tipo.nombre} cerca"

    /**
     * «la gasolinera más cercana está a tres kilómetros, en Sóller. ¿Vamos?»: [distanciaM] en línea recta y
     * [municipio] si se conoce.
     */
    fun masCercano(
        tipo: TipoLugar,
        distanciaM: Double,
        municipio: String?,
    ): String {
        val (distancia, _) = FormatoHablado.distancia(distanciaM)
        val articulo = if (tipo.femenino) "la" else "el"
        val donde =
            municipio
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { ", en $it" }
                .orEmpty()
        return "$articulo ${tipo.nombre} más ${tipo.cercano} está a $distancia$donde. ¿Vamos?"
    }
}
