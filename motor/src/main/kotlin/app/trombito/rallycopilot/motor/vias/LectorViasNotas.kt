package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Etiquetas OSM de una vía relevantes para las notas (texto crudo; vacío en el archivo = null). */
data class EtiquetasVia(
    val bridge: String?,
    val tunnel: String?,
    val junction: String?,
    val lanes: Int?,
    val width: Double?,
    val hazard: String?,
    val surface: String?,
)

/** Nodo con aviso (resalto, vado, paso a nivel, stop, semáforo…); [tipo] es el texto crudo del archivo. */
data class NodoAviso(
    val punto: PuntoGeo,
    val tipo: String,
)

/**
 * Perfil de altura de una vía: muestras cada [pasoM] metros desde el primer nodo más una final en el último nodo.
 * [alturasM] son metros absolutos.
 */
class PerfilVia(
    val pasoM: Double,
    val alturasM: List<Double>,
) {
    init {
        require(pasoM > 0.0) { "Paso inválido: $pasoM" }
        require(alturasM.size >= 2) { "Un perfil necesita al menos dos muestras" }
    }

    /**
     * Altura en [distanciaM] metros desde el primer nodo, por interpolación lineal; fuera de rango se satura. La
     * última muestra cae en [longitudViaM]; sin ese dato se supone en `(n - 1) * pasoM`.
     */
    fun alturaM(
        distanciaM: Double,
        longitudViaM: Double = (alturasM.size - 1) * pasoM,
    ): Double {
        val ultima = alturasM.lastIndex
        val inicioUltimoTramo = (ultima - 1) * pasoM
        return when {
            distanciaM <= 0.0 -> {
                alturasM.first()
            }

            distanciaM >= longitudViaM || longitudViaM <= inicioUltimoTramo -> {
                alturasM.last()
            }

            distanciaM >= inicioUltimoTramo -> {
                interpolar(ultima - 1, (distanciaM - inicioUltimoTramo) / (longitudViaM - inicioUltimoTramo))
            }

            else -> {
                val i = (distanciaM / pasoM).toInt()
                interpolar(i, (distanciaM - i * pasoM) / pasoM)
            }
        }
    }

    private fun interpolar(
        i: Int,
        t: Double,
    ): Double = alturasM[i] + t * (alturasM[i + 1] - alturasM[i])
}

/** Contenido de `vias-notas`: etiquetas por id de vía, nodos de aviso, perfiles y líneas descartadas. */
data class DatosNotasVias(
    val etiquetas: Map<Long, EtiquetasVia>,
    val nodos: List<NodoAviso>,
    val perfiles: Map<Long, PerfilVia>,
    val descartadas: Int = 0,
)

/**
 * Lee el TSV `vias-notas` v1 sin tocar disco: recibe las líneas ya descomprimidas. Registros: `V` (etiquetas de
 * vía), `N` (nodo de aviso, «lat_e6,lon_e6») y `P` (perfil: paso, altura inicial en dm y deltas en dm).
 */
object LectorViasNotas {
    const val CABECERA = "#vias-notas v1"
    private const val CAMPOS_V = 9
    private const val CAMPOS_N = 3
    private const val CAMPOS_P = 4
    private const val ESCALA_COORD = 1_000_000.0
    private const val DM_POR_M = 10.0

    /** @throws IllegalArgumentException si falta la cabecera `#vias-notas v1` o es de otra versión. */
    fun leer(lineas: Sequence<String>): DatosNotasVias {
        val iterador = lineas.iterator()
        val primera = if (iterador.hasNext()) iterador.next().trimStart('\uFEFF').trimEnd() else null
        require(primera == CABECERA) { "Cabecera no válida: se esperaba «$CABECERA» y llegó «$primera»" }
        val etiquetas = HashMap<Long, EtiquetasVia>()
        val nodos = ArrayList<NodoAviso>()
        val perfiles = HashMap<Long, PerfilVia>()
        var descartadas = 0
        for (linea in iterador) {
            if (linea.isBlank()) continue
            val c = linea.trimEnd('\r', '\n').split('\t')
            val bien =
                when (c[0]) {
                    "V" -> analizarV(c)?.let { etiquetas[it.first] = it.second }
                    "N" -> analizarN(c)?.let { nodos.add(it) }
                    "P" -> analizarP(c)?.let { perfiles[it.first] = it.second }
                    else -> null
                }
            if (bien == null) descartadas++
        }
        return DatosNotasVias(etiquetas, nodos, perfiles, descartadas)
    }

    private fun analizarV(c: List<String>): Pair<Long, EtiquetasVia>? {
        val id = c.getOrNull(1)?.trim()?.toLongOrNull()
        return if (c.size != CAMPOS_V || id == null) {
            null
        } else {
            id to
                EtiquetasVia(
                    bridge = campo(c[2]),
                    tunnel = campo(c[3]),
                    junction = campo(c[4]),
                    lanes = campo(c[5])?.toIntOrNull(),
                    width = campo(c[6])?.toDoubleOrNull()?.takeIf { it.isFinite() },
                    hazard = campo(c[7]),
                    surface = campo(c[8]),
                )
        }
    }

    private fun analizarN(c: List<String>): NodoAviso? {
        val partes = c.getOrNull(1)?.split(',')
        val lat = coordenada(partes?.getOrNull(0), 90.0)
        val lon = coordenada(partes?.getOrNull(1), 180.0)
        val tipo = c.getOrNull(2)?.trim().orEmpty()
        val punto = if (lat != null && lon != null) PuntoGeo(lat, lon) else null
        if (c.size != CAMPOS_N || partes?.size != 2 || tipo.isEmpty()) return null
        return punto?.let { NodoAviso(it, tipo) }
    }

    /** Grados a partir de millonésimas enteras; null si no es entero o supera ±[limite]. */
    private fun coordenada(
        texto: String?,
        limite: Double,
    ): Double? =
        texto
            ?.trim()
            ?.toLongOrNull()
            ?.let { it / ESCALA_COORD }
            ?.takeIf { it in -limite..limite }

    private fun analizarP(c: List<String>): Pair<Long, PerfilVia>? {
        val id = c.getOrNull(1)?.trim()?.toLongOrNull()
        val paso =
            c
                .getOrNull(2)
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        val alturas = c.getOrNull(3)?.let { alturas(it) }?.takeIf { it.size >= 2 }
        if (c.size != CAMPOS_P || paso == null || alturas == null) return null
        return id?.let { it to PerfilVia(paso.toDouble(), alturas) }
    }

    /** Alturas en metros a partir de «z0_dm,d1,d2,…»; null si algún valor no es entero. */
    private fun alturas(texto: String): List<Double>? {
        var acumulado = 0
        val alturas = ArrayList<Double>()
        for (valor in texto.split(',')) {
            acumulado += valor.trim().toIntOrNull() ?: return null
            alturas.add(acumulado / DM_POR_M)
        }
        return alturas
    }

    private fun campo(texto: String): String? = texto.trim().ifEmpty { null }
}
