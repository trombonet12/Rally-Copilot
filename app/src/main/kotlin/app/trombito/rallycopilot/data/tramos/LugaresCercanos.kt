package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.DestinoTramoVia
import app.trombito.rallycopilot.motor.vias.LugarNombrado
import app.trombito.rallycopilot.motor.vias.TipoLugar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hacia dónde va un tramo de vía ([DestinoTramoVia]): el pueblo más cercano a su meta o, si no hay ninguno a 3 km, el
 * rumbo. Los lugares salen del índice de búsqueda offline y se cargan una sola vez (las ciudades, pueblos y aldeas de
 * Mallorca son unos cientos); si el índice aún no está instalado no se guarda nada y se vuelve a pedir la próxima vez.
 * Nunca falla: sin lugares, el destino es el rumbo.
 */
@Singleton
class LugaresCercanos
    @Inject
    constructor(
        private val buscador: Buscador,
    ) {
        private val candado = Mutex()
        private var lugares: List<LugarNombrado>? = null

        /** «Manacor» o «el sureste» para un tramo que va de [salida] a [meta]. */
        suspend fun destinoDe(
            salida: PuntoGeo,
            meta: PuntoGeo,
        ): String = DestinoTramoVia.de(salida, meta, lugares())

        private suspend fun lugares(): List<LugarNombrado> =
            candado.withLock {
                lugares ?: cargar().also { if (it.isNotEmpty()) lugares = it }
            }

        private suspend fun cargar(): List<LugarNombrado> = buscador.lugaresPoblados().mapNotNull { it.aLugar() }

        private fun ResultadoBusqueda.aLugar(): LugarNombrado? {
            val tipo =
                when (categoria) {
                    "ciudad", "municipio" -> TipoLugar.MUNICIPIO
                    "pueblo" -> TipoLugar.PUEBLO
                    "aldea" -> TipoLugar.ALDEA
                    else -> return null
                }
            return LugarNombrado(nombre, punto, tipo)
        }
    }
