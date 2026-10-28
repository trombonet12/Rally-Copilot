package app.trombito.rallycopilot.data.ruta

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.Closeable
import java.io.File
import javax.inject.Inject

/** Motor de rutas abierto sobre un archivo de datos; separa las nativas de Valhalla para poder probar el resto. */
interface MotorRutas : Closeable {
    /** Respuesta JSON de Valhalla a una petición `route`. @throws com.valhalla.valhalla.ValhallaException si falla. */
    fun rutaJson(peticion: String): String
}

fun interface FabricaMotorRutas {
    fun crear(tar: File): MotorRutas
}

/**
 * Abre Valhalla (nativas) sobre el extracto de teselas `tar`. Una instancia mantiene el tar mapeado en memoria.
 *
 * La configuración parte de `default.json` (los valores por defecto de Valhalla que empaqueta valhalla-mobile, el mismo
 * recurso que usa `ValhallaConfigFactory`) con `mjolnir.tile_extract` apuntando al tar. Se escribe el JSON a mano y se
 * abre con `Valhalla(configPath)` porque el módulo de modelos de configuración no está en el classpath de compilación.
 */
class FabricaMotorValhalla
    @Inject
    constructor(
        @ApplicationContext private val contexto: Context,
    ) : FabricaMotorRutas {
        override fun crear(tar: File): MotorRutas {
            val archivoConfig = File(contexto.filesDir, NOMBRE_CONFIG)
            archivoConfig.writeText(configJson(leerDefecto(), tar).toString())
            val valhalla = Valhalla(archivoConfig.absolutePath)
            return object : MotorRutas {
                override fun rutaJson(peticion: String): String = valhalla.routeRaw(peticion)

                override fun close() = valhalla.close()
            }
        }

        private fun leerDefecto(): JsonObject {
            val texto =
                ValhallaConfigFactory::class.java
                    .getResourceAsStream(RECURSO_DEFECTO)
                    ?.use { it.readBytes().decodeToString() }
                    ?: error("Falta $RECURSO_DEFECTO en valhalla-mobile")
            return Json.parseToJsonElement(texto).jsonObject
        }

        companion object {
            private const val NOMBRE_CONFIG = "valhalla-ruta.json"
            private const val RECURSO_DEFECTO = "/com/valhalla/valhalla/default.json"

            /**
             * [defecto] con el tar de teselas y `service_limits.allow_hard_exclusions` (necesario para
             * `exclude_tunnels`; sin él Valhalla ignora la exclusión con un aviso).
             */
            internal fun configJson(
                defecto: JsonObject,
                tar: File,
            ): JsonObject {
                val mjolnir = defecto["mjolnir"]?.jsonObject ?: JsonObject(emptyMap())
                val limites = defecto["service_limits"]?.jsonObject ?: JsonObject(emptyMap())
                return JsonObject(
                    defecto +
                        mapOf(
                            "mjolnir" to JsonObject(mjolnir + ("tile_extract" to JsonPrimitive(tar.absolutePath))),
                            "service_limits" to JsonObject(limites + ("allow_hard_exclusions" to JsonPrimitive(true))),
                        ),
                )
            }
        }
    }
