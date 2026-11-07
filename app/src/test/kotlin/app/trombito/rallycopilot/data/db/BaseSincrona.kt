package app.trombito.rallycopilot.data.db

import android.content.Context
import androidx.room.Room
import java.util.concurrent.Executor

/**
 * Base de Room en memoria cuyas consultas y transacciones corren en el hilo que las llama, sin pasar por los hilos de
 * Room. Junto con `Dispatchers.Unconfined` y el `SynchronousExecutor` de work-testing, las pruebas que encolan trabajos
 * ven el resultado nada más volver de la llamada: no hace falta dormir ni sondear con el reloj real.
 */
fun baseSincrona(contexto: Context): RallyDatabase {
    val directo = Executor { it.run() }
    return Room
        .inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(directo)
        .setTransactionExecutor(directo)
        .build()
}
