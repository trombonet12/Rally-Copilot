package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.diagnostico.RegistroDiagnostico
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.ubicacion.PosicionEstimada
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Anota en el [registro] lo que ya publican la sesión de navegación, el cronómetro y el GPS, sin tocar a sus
 * dueños: observa [estado] y [crono] desde [alcance] (de proceso). Las posiciones ([posiciones]) solo se recogen
 * mientras hay una conducción libre o guiada, que es cuando el servicio ya las está recogiendo (no enciende el GPS por
 * su cuenta). Nunca escribe coordenadas: km, segundos, deltas, nombres de tramo, precisiones y velocidades.
 */
class ObservadorDiagnostico(
    private val estado: Flow<EstadoNavegacion>,
    private val crono: Flow<CronoEnCurso?>,
    private val posiciones: () -> Flow<PosicionEstimada>,
    private val registro: RegistroDiagnostico,
    private val alcance: CoroutineScope,
    private val relojMonotonicoMs: () -> Long,
) {
    fun iniciar() {
        alcance.launch {
            var anterior: EstadoNavegacion = EstadoNavegacion.Inactiva
            estado.collect { nuevo ->
                anotarNavegacion(anterior, nuevo)
                anterior = nuevo
            }
        }
        alcance.launch { vigilarGps() }
        val anotadorCrono = AnotadorCrono(registro)
        alcance.launch {
            var anterior: CronoEnCurso? = null
            crono.collect { nuevo ->
                anotadorCrono.anotar(anterior, nuevo)
                anterior = nuevo
            }
        }
    }

    private fun anotarNavegacion(
        antes: EstadoNavegacion,
        ahora: EstadoNavegacion,
    ) {
        if (antes::class != ahora::class) {
            anotarCambioDeModo(antes, ahora)
        } else if (antes is EstadoNavegacion.Guiando && ahora is EstadoNavegacion.Guiando) {
            anotarGuiado(antes, ahora)
        }
    }

    private fun anotarCambioDeModo(
        antes: EstadoNavegacion,
        ahora: EstadoNavegacion,
    ) {
        when (ahora) {
            is EstadoNavegacion.Inactiva -> {
                registro.info(ETIQUETA, "navegacion fin modo=${modo(antes)}${resumenFin(antes)}")
            }

            is EstadoNavegacion.Libre -> {
                registro.info(ETIQUETA, "navegacion inicio modo=${modo(ahora)}")
            }

            is EstadoNavegacion.Guiando -> {
                registro.info(ETIQUETA, "navegacion inicio modo=guiado perfil=${ahora.perfil.name}")
                registro.info(ETIQUETA, descripcionRuta("ruta calculada", ahora))
            }

            is EstadoNavegacion.Llegada -> {
                registro.info(ETIQUETA, "navegacion llegada destino=${ahora.destino.nombre}")
            }

            is EstadoNavegacion.Error -> {
                registro.error(ETIQUETA, "navegacion error motivo=${ahora.motivo}")
            }
        }
    }

    private fun anotarGuiado(
        antes: EstadoNavegacion.Guiando,
        ahora: EstadoNavegacion.Guiando,
    ) {
        if (ahora.recalculando && !antes.recalculando) registro.info(ETIQUETA, "navegacion recalculo iniciado")
        if (ahora.ruta !== antes.ruta) registro.info(ETIQUETA, descripcionRuta("ruta recalculada", ahora))
        if (ahora.paradasHechas > antes.paradasHechas) {
            registro.info(
                ETIQUETA,
                "navegacion parada alcanzada n=${ahora.paradasHechas} pendientes=${ahora.paradas.size}",
            )
        }
    }

    private fun descripcionRuta(
        titulo: String,
        guiado: EstadoNavegacion.Guiando,
    ): String =
        "navegacion $titulo km=${decimal(guiado.ruta.longitudM / METROS_POR_KM)} " +
            "previsto_s=${guiado.ruta.tiempoS.toLong()} maniobras=${guiado.ruta.maniobras.size} " +
            "paradas=${guiado.paradas.size}"

    private fun modo(estado: EstadoNavegacion): String =
        when (estado) {
            is EstadoNavegacion.Libre -> if (estado.reconocimientoTramoId != null) "reconocimiento" else "libre"
            is EstadoNavegacion.Guiando -> "guiado"
            is EstadoNavegacion.Llegada -> "llegada"
            is EstadoNavegacion.Error -> "error"
            is EstadoNavegacion.Inactiva -> "inactiva"
        }

    private fun resumenFin(estado: EstadoNavegacion): String =
        if (estado is EstadoNavegacion.Libre) " km=${decimal(estado.distanciaM / METROS_POR_KM)}" else ""

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun vigilarGps() {
        estado
            .map { it is EstadoNavegacion.Libre || it is EstadoNavegacion.Guiando }
            .distinctUntilChanged()
            .collectLatest { conduciendo -> if (conduciendo) vigilarConduccion() }
    }

    private suspend fun vigilarConduccion() =
        coroutineScope {
            val monitor = MonitorGps(registro)
            launch { posiciones().collect { monitor.fijacion(it.cruda, relojMonotonicoMs()) } }
            while (isActive) {
                delay(TICK_GPS_MS)
                monitor.tick(relojMonotonicoMs())
            }
        }

    private fun decimal(valor: Double): String = String.format(Locale.ROOT, "%.2f", valor)

    private companion object {
        const val ETIQUETA = "Diagnostico"
        const val TICK_GPS_MS = 1_000L
        const val METROS_POR_KM = 1_000.0
    }
}
