package app.trombito.rallycopilot.data.ajustes

import app.trombito.rallycopilot.data.db.AjustesConduccionDao
import app.trombito.rallycopilot.data.db.AjustesConduccionEntity
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.IconoPosicion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.datos.CapaMapa
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
    )
}

internal fun AjustesConduccion.aEntidad() =
    AjustesConduccionEntity(
        nivelZoom = nivelZoom,
        icono = icono.name,
        orientacion = orientacion.name,
        capa = capa.name,
        relieve = relieve,
    )

private inline fun <reified E : Enum<E>> enumOrNull(nombre: String): E? =
    enumValues<E>().firstOrNull {
        it.name ==
            nombre
    }
