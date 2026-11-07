package app.trombito.rallycopilot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Factor de calibración de las notas ya calculado (una sola fila, `id` = [FILA_UNICA]). [clave] son los ids de los
 * recorridos con que se calculó (los terminados más recientes, separados por comas): si cambia el conjunto, la fila
 * ya no vale. [factor] es null cuando no había datos bastantes (también es un resultado que no hay que recalcular).
 * Se lee y se escribe desde `RecorridosDao`.
 */
@Entity(tableName = "calibracion")
data class CalibracionEntity(
    @PrimaryKey val id: Int = FILA_UNICA,
    val clave: String,
    val factor: Double?,
)
