package app.trombito.rallycopilot.ui.comun

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FalsoRepositorioDatosMapa(
    estadoInicial: EstadoDatosMapa = EstadoDatosMapa(),
    var estilosDisponibles: EstilosMapa? = null,
    /** Lo que responde [tamanoSatelite] (null = sin red). */
    var bytesSatelite: Long? = null,
) : RepositorioDatosMapa {
    /** Lo que responde [tamanoPendiente] para los complementos que no son el satélite (null = sin red). */
    var bytesComplementos: Long? = null

    /** Cada descarga pedida de un complemento (la del satélite también) y cada borrado, en orden. */
    val complementosDescargados = mutableListOf<Complemento>()
    val complementosDescargadosConWifi = mutableListOf<Complemento>()
    val complementosBorrados = mutableListOf<Complemento>()

    val estadoMutable = MutableStateFlow(estadoInicial)
    val ajustesMutable = MutableStateFlow(AjustesMapas())
    override val estado: Flow<EstadoDatosMapa> = estadoMutable
    override val ajustes: Flow<AjustesMapas> = ajustesMutable

    var descargas = 0
    var cancelaciones = 0
    var borrados = 0
    var avisosDescartados = 0
    var comprobaciones = 0

    override suspend fun guardarAjustes(ajustes: AjustesMapas) {
        ajustesMutable.value = ajustes
    }

    override fun comprobarActualizacion() {
        comprobaciones++
    }

    override fun descargar(complemento: Complemento) {
        complementosDescargados += complemento
        if (complemento == Complemento.SATELITE) descargas++
    }

    override fun descargarConWifi(complemento: Complemento) {
        complementosDescargadosConWifi += complemento
    }

    override fun cancelar() {
        cancelaciones++
    }

    override suspend fun borrar(complemento: Complemento) {
        complementosBorrados += complemento
        if (complemento == Complemento.SATELITE) borrados++
    }

    var consultasTamanoSatelite = 0

    override suspend fun tamanoPendiente(complemento: Complemento): Long? {
        if (complemento != Complemento.SATELITE) return bytesComplementos
        consultasTamanoSatelite++
        return bytesSatelite
    }

    override suspend fun descartarAviso() {
        avisosDescartados++
    }

    override suspend fun estilos(): EstilosMapa? = estilosDisponibles
}

class FalsoRepositorioAjustesConduccion(
    inicial: AjustesConduccion = AjustesConduccion(),
) : RepositorioAjustesConduccion {
    val mutable = MutableStateFlow(inicial)
    override val ajustes: Flow<AjustesConduccion> = mutable

    /** Cada vez que se guarda; sirve para comprobar que algo se ha persistido. */
    val guardados = mutableListOf<AjustesConduccion>()

    override suspend fun guardar(ajustes: AjustesConduccion) {
        guardados += ajustes
        mutable.value = ajustes
    }
}

/** Una frase pedida al locutor de pruebas con su prioridad y su caducidad. */
data class FraseDicha(
    val texto: String,
    val prioridad: PrioridadVoz,
    val caducaMs: Long?,
)

/** Locutor de pruebas: no suena, solo anota qué voces se han probado. */
class FalsoLocutor : Locutor {
    override val hablando: StateFlow<Boolean> = MutableStateFlow(false)
    val probadas = mutableListOf<VozCopiloto>()

    /** Cada volumen (%) pedido al locutor, en orden. */
    val volumenes = mutableListOf<Int>()

    /** Cada frase de ejemplo pedida con su voz, en orden. */
    val ejemplos = mutableListOf<Pair<VozCopiloto, String>>()

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun usarVolumen(porcentaje: Int) {
        volumenes += porcentaje
    }

    override fun probarFrase(
        v: VozCopiloto,
        texto: String,
    ) {
        ejemplos += v to texto
    }

    override fun preparar(textos: List<String>) = Unit

    /** Cada frase dicha con [decir], en orden. */
    val dichas = mutableListOf<String>()

    /** Cada frase dicha con su prioridad y caducidad, en orden. */
    val frases = mutableListOf<FraseDicha>()

    override fun decir(
        texto: String,
        prioridad: PrioridadVoz,
        caducaMs: Long?,
    ) {
        frases += FraseDicha(texto, prioridad, caducaMs)
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String): Double = 0.0

    override fun probar(v: VozCopiloto) {
        probadas += v
    }

    override fun cerrar() = Unit
}

class FalsaFuenteUbicacion(
    var permiso: Boolean = true,
) : FuenteUbicacion {
    val flujo = MutableSharedFlow<Fijacion>(extraBufferCapacity = 16)

    override fun fijaciones(): Flow<Fijacion> = flujo

    override fun tienePermiso(): Boolean = permiso
}

class FalsoProveedorVia(
    var via: ViaActual? = null,
) : ProveedorViaActual {
    override suspend fun viaEn(fijacion: Fijacion): ViaActual? = via
}

fun fijacion(
    tiempoMs: Long,
    velocidadMs: Double? = null,
    lat: Double = 39.70,
    lon: Double = 2.90,
) = Fijacion(PuntoGeo(lat, lon), tiempoMs, velocidadMs, rumboGrados = 90.0, precisionM = 5.0)

/** Órdenes de voz de pruebas: anota cuántas veces se pidió escuchar y deja fijar si está escuchando. */
class FalsoControlOrdenes : app.trombito.rallycopilot.domain.escucha.ControlOrdenes {
    val estaEscuchando = MutableStateFlow(false)
    var peticiones = 0
    override val escuchando: StateFlow<Boolean> = estaEscuchando
    val ultimoDialogo = MutableStateFlow<app.trombito.rallycopilot.domain.escucha.DialogoVoz?>(null)
    override val dialogo: StateFlow<app.trombito.rallycopilot.domain.escucha.DialogoVoz?> = ultimoDialogo

    override fun tienePermiso(): Boolean = true

    override fun escucharOrden() {
        peticiones++
    }
}
