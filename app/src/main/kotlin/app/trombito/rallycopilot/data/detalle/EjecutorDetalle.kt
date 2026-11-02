package app.trombito.rallycopilot.data.detalle

import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.datosmapa.PREFIJO_SATELITE
import app.trombito.rallycopilot.data.datosmapa.aDominio
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import app.trombito.rallycopilot.motor.precarga.Tesela
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/** Si la batería y la carga permiten la descarga automática. */
fun interface EstadoEnergia {
    fun permiteDescargaAutomatica(): Boolean
}

/** Cómo acabó una ejecución del detalle. */
enum class ResultadoEjecucion {
    /** Todo lo pedido está en el MBTiles. */
    HECHO,

    /** Se llegó al límite de espacio. */
    LIMITE,

    /** La batería está baja y no carga: se reintenta más tarde. */
    ESPERA_ENERGIA,

    /** No hay satélite instalado: la descarga automática no tiene sentido y no se hace. */
    SIN_SATELITE,
}

/**
 * Lo que hace el trabajo de detalle: junta las teselas z18 de los corredores (tramos guardados y rutas pendientes) o de
 * una zona manual y se las pasa al [DescargadorDetalle] con el límite de espacio de Mapas offline. Cuenta contra el
 * límite los archivos de datos instalados más el propio MBTiles de detalle. Las teselas de mar no se guardan.
 */
@Suppress("LongParameterList") // colaboradores: tramos, Room, fuente de teselas, energia y pendientes
class EjecutorDetalle
    @Inject
    constructor(
        private val config: ConfigDatosMapa,
        private val tramos: RepositorioTramos,
        private val dao: DatosMapaDao,
        private val ajustesDao: AjustesMapasDao,
        private val fuente: FuenteTeselas,
        private val energia: EstadoEnergia,
        private val pendientes: RutasPendientes,
    ) {
        private val archivo: File get() = File(config.directorioMapa, NOMBRE_DETALLE)

        suspend fun automatico(alProgreso: suspend (Int, Int) -> Unit = { _, _ -> }): ResultadoEjecucion {
            val hayConSatelite = dao.archivos().any { it.id.startsWith(PREFIJO_SATELITE) }
            val permitido = energia.permiteDescargaAutomatica()
            return when {
                !hayConSatelite -> ResultadoEjecucion.SIN_SATELITE
                !permitido -> ResultadoEjecucion.ESPERA_ENERGIA
                else -> descargarCorredores(alProgreso)
            }
        }

        private suspend fun descargarCorredores(alProgreso: suspend (Int, Int) -> Unit): ResultadoEjecucion {
            val rutas = pendientes.leer()
            val teselas = HashSet<Tesela>()
            tramos.observarResumenes().first().forEach { resumen ->
                tramos.tramo(resumen.id)?.let {
                    teselas +=
                        TeselasPrecarga.corredor(it.traza, TeselasPrecarga.ZOOM_CORREDOR)
                }
            }
            rutas.forEach { teselas += it.second }
            val resultado = descargar(teselas, alProgreso)
            if (resultado == ResultadoEjecucion.HECHO) rutas.forEach { pendientes.borrar(it.first) }
            return resultado
        }

        suspend fun zona(
            zona: Rectangulo,
            zoom: Int,
            alProgreso: suspend (Int, Int) -> Unit = { _, _ -> },
        ): ResultadoEjecucion {
            require(zoom in TeselasPrecarga.ZOOM_MIN_ZONA..TeselasPrecarga.ZOOM_MAX_ZONA) { "Zoom fuera de 18..19" }
            return descargar(TeselasPrecarga.rectangulo(zona, zoom), alProgreso)
        }

        private suspend fun descargar(
            teselas: Collection<Tesela>,
            alProgreso: suspend (Int, Int) -> Unit,
        ): ResultadoEjecucion {
            val fuera = dao.archivos().sumOf { it.bytes }
            val limite = (ajustesDao.ajustes()?.aDominio() ?: AjustesMapas()).limiteBytes
            val ordenadas = teselas.sortedWith(compareBy({ it.z }, { it.x }, { it.y }))
            return AlmacenDetalle(archivo).use { almacen ->
                val resultado =
                    DescargadorDetalle(
                        almacen,
                        fuente,
                        { fuera },
                        { limite },
                    ).descargar(ordenadas, alProgreso)
                val limite = resultado is ResultadoDetalle.LimiteAlcanzado
                if (limite) ResultadoEjecucion.LIMITE else ResultadoEjecucion.HECHO
            }
        }
    }
