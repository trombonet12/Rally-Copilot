package app.trombito.rallycopilot.motor.datos

/** Límite de espacio por defecto para los datos descargados (decisión de Joan: 2 GB). */
const val LIMITE_POR_DEFECTO: Long = 2L * 1024 * 1024 * 1024

/** Archivo ya presente en el dispositivo. */
data class ArchivoLocal(
    val id: String,
    val sha256: String,
    val bytes: Long,
)

/** Por qué no se puede llevar a cabo la actualización. */
enum class ProblemaEspacio {
    /** Los datos resultantes superarían el límite configurado. */
    SUPERA_LIMITE,

    /** No hay espacio libre para bajar lo que falta (el archivo viejo convive con el `.part` hasta el final). */
    SIN_ESPACIO_LIBRE,
}

/** Resultado de [PlanificadorDescarga.planificar]. */
data class PlanDescarga(
    val aDescargar: List<ArchivoDatos>,
    /** Ids locales que ya no están en el manifest remoto. */
    val aBorrar: List<String>,
    val bytesADescargar: Long,
    val bytesTrasActualizar: Long,
    val problema: ProblemaEspacio?,
)

/** Decide qué bajar y qué borrar comparando el manifest remoto con lo que hay en disco. */
object PlanificadorDescarga {
    fun planificar(
        remoto: ManifestDatos,
        locales: List<ArchivoLocal>,
        bytesLibres: Long,
        limiteBytes: Long,
    ): PlanDescarga {
        val localPorId = locales.associateBy { it.id }
        val (vigentes, aDescargar) = remoto.archivos.partition { localPorId[it.id]?.sha256 == it.sha256 }
        val idsRemotos = remoto.archivos.map { it.id }.toSet()
        val aBorrar = locales.map { it.id }.filter { it !in idsRemotos }

        val bytesADescargar = aDescargar.sumOf { it.bytes }
        val bytesTrasActualizar = vigentes.sumOf { localPorId.getValue(it.id).bytes } + bytesADescargar
        val problema =
            when {
                bytesTrasActualizar > limiteBytes -> ProblemaEspacio.SUPERA_LIMITE
                bytesADescargar > bytesLibres -> ProblemaEspacio.SIN_ESPACIO_LIBRE
                else -> null
            }
        return PlanDescarga(aDescargar, aBorrar, bytesADescargar, bytesTrasActualizar, problema)
    }
}
