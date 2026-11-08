package app.trombito.rallycopilot.data.ajustes

import app.trombito.rallycopilot.data.db.AjustesConduccionDao
import app.trombito.rallycopilot.data.db.AjustesConduccionEntity
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.EstiloManiobras
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Tema
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VelocidadHabla
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Ajustes de conducción en Room (una fila). Sin fila, o con valores desconocidos, valen los por defecto. */
class RepositorioAjustesConduccionImpl
    @Inject
    constructor(
        private val dao: AjustesConduccionDao,
    ) : RepositorioAjustesConduccion {
        override val ajustes: Flow<AjustesConduccion> =
            dao.observar().map { it?.aDominio() ?: AjustesConduccion() }.distinctUntilChanged()

        override suspend fun guardar(ajustes: AjustesConduccion) {
            dao.guardar(ajustes.aEntidad())
        }
    }

internal fun AjustesConduccionEntity.aDominio(): AjustesConduccion {
    val defecto = AjustesConduccion()
    return AjustesConduccion(
        nivelZoom = nivelZoom.coerceIn(AjustesConduccion.NIVEL_MIN, AjustesConduccion.NIVEL_MAX),
        icono = enumOrNull<IconoPosicion>(icono) ?: defecto.icono,
        orientacion = enumOrNull<Orientacion>(orientacion) ?: defecto.orientacion,
        capa = enumOrNull<CapaMapa>(capa) ?: defecto.capa,
        relieve = relieve,
        estiloManiobras = enumOrNull<EstiloManiobras>(estiloManiobras) ?: defecto.estiloManiobras,
        anticipacion = enumOrNull<NivelAnticipacion>(anticipacion) ?: defecto.anticipacion,
        notasSimplificadas = notasSimplificadas,
        modoCopiloto = enumOrNull<ModoCopiloto>(modoCopiloto) ?: defecto.modoCopiloto,
        voz = voz?.let { enumOrNull<VozCopiloto>(it) },
        activarConCopiloto = activarConCopiloto,
        volumenVoz = volumenVoz.coerceIn(AjustesConduccion.VOLUMEN_VOZ_MIN, AjustesConduccion.VOLUMEN_VOZ_MAX),
        tema = enumOrNull<Tema>(tema) ?: defecto.tema,
        cronometrar = cronometrar,
        notasGrandes = notasGrandes,
        avisarCruces = avisarCruces,
        avisarSalidasAutopista = avisarSalidasAutopista,
        avisarRotondas = avisarRotondas,
        salidaManual = salidaManual,
        casa = casaValida(),
        avisarRadares = avisarRadares,
        avisarIncidencias = avisarIncidencias,
        avisarVelocidad = avisarVelocidad,
        velocidadHabla = enumOrNull<VelocidadHabla>(velocidadHabla) ?: defecto.velocidadHabla,
        avisarPuentes = avisarPuentes,
    )
}

/** La casa guardada, o `null` si falta una de las dos coordenadas o están fuera de rango (copia dañada). */
private fun AjustesConduccionEntity.casaValida(): PuntoGeo? {
    val lat = casaLat?.takeIf { it in LAT_RANGO }
    val lon = casaLon?.takeIf { it in LON_RANGO }
    return if (lat != null && lon != null) PuntoGeo(lat, lon) else null
}

private val LAT_RANGO = -90.0..90.0
private val LON_RANGO = -180.0..180.0

internal fun AjustesConduccion.aEntidad() =
    AjustesConduccionEntity(
        nivelZoom = nivelZoom,
        icono = icono.name,
        orientacion = orientacion.name,
        capa = capa.name,
        relieve = relieve,
        estiloManiobras = estiloManiobras.name,
        anticipacion = anticipacion.name,
        notasSimplificadas = notasSimplificadas,
        modoCopiloto = modoCopiloto.name,
        voz = voz?.name,
        activarConCopiloto = activarConCopiloto,
        volumenVoz = volumenVoz,
        tema = tema.name,
        cronometrar = cronometrar,
        notasGrandes = notasGrandes,
        avisarCruces = avisarCruces,
        avisarSalidasAutopista = avisarSalidasAutopista,
        avisarRotondas = avisarRotondas,
        salidaManual = salidaManual,
        casaLat = casa?.lat,
        casaLon = casa?.lon,
        avisarRadares = avisarRadares,
        avisarIncidencias = avisarIncidencias,
        avisarVelocidad = avisarVelocidad,
        velocidadHabla = velocidadHabla.name,
        avisarPuentes = avisarPuentes,
    )

private inline fun <reified E : Enum<E>> enumOrNull(nombre: String): E? =
    enumValues<E>().firstOrNull {
        it.name ==
            nombre
    }
