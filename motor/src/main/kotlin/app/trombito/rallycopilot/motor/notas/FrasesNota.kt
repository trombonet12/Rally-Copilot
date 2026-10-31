package app.trombito.rallycopilot.motor.notas

/** Frases de las notas de rally en español, sin depender del `Locale` del sistema. */
object FrasesNota {
    private val UNIDADES =
        (
            "cero uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince dieciséis " +
                "diecisiete dieciocho diecinueve veinte veintiuno veintidós veintitrés veinticuatro veinticinco " +
                "veintiséis veintisiete veintiocho veintinueve"
        ).split(' ')
    private val DECENAS = "treinta cuarenta cincuenta sesenta setenta ochenta noventa".split(' ')
    private val CENTENAS =
        (
            "ciento doscientos trescientos cuatrocientos quinientos seiscientos setecientos ochocientos novecientos"
        ).split(' ')

    /**
     * «derecha cuatro larga, cincuenta»; la distancia (si hay) va después. Con [simplificada], «derecha media». Si la
     * nota pide precaución empieza por «cuidado,».
     */
    fun frase(
        nota: Nota,
        simplificada: Boolean = false,
    ): String {
        val base = base(nota, simplificada)
        return nota.distanciaSiguienteM?.let { "$base, ${enPalabras(it)}" } ?: base
    }

    /**
     * Frase de una secuencia de notas: las unidas van con «y» («izquierda tres y derecha cuatro, cincuenta») y los
     * grupos se separan con un punto para que la voz haga pausa.
     */
    fun secuencia(
        notas: List<Nota>,
        simplificada: Boolean = false,
    ): String {
        val sb = StringBuilder()
        notas.forEachIndexed { i, nota ->
            sb.append(frase(nota, simplificada))
            if (i < notas.lastIndex) sb.append(if (nota.unidaConSiguiente) " y " else ". ")
        }
        return sb.toString()
    }

    /** Número entero de 0 a 999 en palabras («ciento veinte», «doscientos cincuenta»). */
    fun enPalabras(n: Int): String {
        require(n in 0..999) { "Número fuera de 0..999: $n" }
        return when {
            n < 30 -> UNIDADES[n]
            n < 100 -> DECENAS[n / 10 - 3] + if (n % 10 > 0) " y ${UNIDADES[n % 10]}" else ""
            n == 100 -> "cien"
            else -> CENTENAS[n / 100 - 1] + if (n % 100 > 0) " ${enPalabras(n % 100)}" else ""
        }
    }

    private fun base(
        nota: Nota,
        simplificada: Boolean,
    ): String = (if (nota.cuidado) "cuidado, " else "") + giro(nota, simplificada)

    private fun giro(
        nota: Nota,
        simplificada: Boolean,
    ): String {
        val direccion = if (nota.direccion == Direccion.IZQUIERDA) "izquierda" else "derecha"
        val modificadores = nota.modificadores.sorted().joinToString("") { " " + palabra(it) }
        if (nota.grado == null) return "horquilla $direccion$modificadores"
        val grado = if (simplificada) palabra(nota.simplificada()) else enPalabras(nota.grado)
        return "$direccion $grado$modificadores"
    }

    private fun palabra(g: GradoSimplificado): String =
        when (g) {
            GradoSimplificado.HORQUILLA -> "horquilla"
            GradoSimplificado.CERRADA -> "cerrada"
            GradoSimplificado.MEDIA -> "media"
            GradoSimplificado.SUAVE -> "suave"
        }

    private fun palabra(m: Modificador): String =
        when (m) {
            Modificador.LARGA -> "larga"
            Modificador.MUY_LARGA -> "muy larga"
            Modificador.CIERRA -> "cierra"
            Modificador.ABRE -> "abre"
        }
}
