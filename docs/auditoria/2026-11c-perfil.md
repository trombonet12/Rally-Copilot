# Auditoría de Mis tramos y Perfil del piloto (2026-11c)

> Propósito: por qué Mis tramos y el Perfil se leen mal y aportan poco, y tres propuestas de rediseño para que Joan elija · Audiencia: Joan y agentes · Actualizado: 2026-11-23 · Relacionado: [2026-11b.md](2026-11b.md)

Auditoría de solo lectura (Fable) de `ui/mistramos/`, `ui/perfil/`, `ui/miscarreteras/` y `ui/analisis/`, y de los datos de `motor/analisis/` y `motor/tramos/`. Petición de Joan (2026-11-23): «toda la parte de mis tramos o mi perfil de piloto… es bastante confusa y complicada de leer y no siempre aporta mucha información».

Rutas abreviadas: `ui/…` = `app/src/main/kotlin/app/trombito/rallycopilot/ui/…`; `motor/…` = `motor/src/main/kotlin/app/trombito/rallycopilot/motor/…`; `strings_*.xml` = `app/src/main/res/values/`.

Las cifras de los ejemplos de texto son **ilustrativas** (no salen de la base de Joan); solo muestran el tono y el formato.

## 1. Diagnóstico

**Idea central.** Las pantallas enseñan *registros* (filas, tiempos, contadores) y ninguna *conclusión*. Casi todo lo que se muestra es un tiempo comparado con otro tiempo del mismo tramo, y hoy casi ningún tramo tiene dos pasadas. Lo que sí hay en abundancia (km, carreteras, curvas, velocidad en marcha por vía y sentido) apenas se enseña o se enseña sin frase que lo interprete.

### 1.1 Jerarquía: tres sitios para lo mismo, con nombres que chocan
- Hay tres entradas que se pisan: «Mis tramos» con pestañas Viajes/Carreteras (`ui/mistramos/MisTramosScreen.kt:109`), «Mis carreteras» como icono suelto en la barra (`ui/mistramos/MisTramosScreen.kt:103`, `:139-144`) y «Perfil del piloto» (`strings_inicio.xml:6`).
- La pestaña «Carreteras» y la pantalla «Mis carreteras» son cosas distintas con el mismo nombre y el mismo icono (`ui/mistramos/PestanasTramos.kt:53`, `ui/mistramos/MisTramosScreen.kt:142`; `strings_mistramos.xml:64`, `strings_miscarreteras.xml:4`).
- Dos niveles de filtro seguidos (pestañas y, debajo, fichas Todos/Repetidos/Favoritos: `ui/mistramos/MisTramosScreen.kt:109-110`). «Repetidos» sale vacío casi siempre con los datos de hoy (`strings_mistramos.xml:57-58`).
- Cinco palabras para cosas parecidas: «Viajes», «Tramos», «Pasadas», «Recorridos», «Subtramos» (`strings_mistramos.xml:63`, `strings_perfil.xml:7-8`, `strings_miscarreteras.xml:20`, `strings_mistramos.xml:97`). En Mis carreteras «pasadas» significa «viajes que pasaron por ahí» (`strings_miscarreteras.xml:36`, `motor/analisis/AgregadoCarreteras.kt:81`), y en el resto, «vuelta cronometrada».

### 1.2 Perfil: cuatro cifras sin contexto y tres secciones casi vacías
- **Totales** (`ui/perfil/PerfilScreen.kt:150-183`): cuatro números sin comparación ni periodo.
  - «Km/h medios» (`strings_perfil.xml:9`) es metros de pasadas entre tiempo de pasadas (`motor/analisis/PerfilPiloto.kt:92-99`): incluye paradas dentro del tramo y no es la media en marcha (67–77 km/h) que ya calcula `VelocidadVias`.
  - «Tramos» cuenta todo lo que devuelve `observarResumenes(TODOS)` (`ui/perfil/PerfilViewModel.kt:42`, `motor/analisis/PerfilPiloto.kt:96`). El repositorio no filtra por tipo en ese método (`data/tramos/RepositorioTramosRoom.kt:45-53`), así que parece mezclar viajes y tramos de vía, y los km de un viaje y de sus trozos de vía entrarían dos veces en la media. **Por comprobar** en el DAO antes de darlo por error.
- **Mejores tiempos** (`ui/perfil/SeccionesPerfil.kt:67-81`): ordena por número de pasadas y corta a 5 (`ui/perfil/PerfilUiState.kt:18-25`, `:32`). Con una pasada por tramo, el «mejor» es el único que hay y el orden acaba siendo alfabético.
  - El nombre va a una línea y se corta («Ma-30 / Ma-15 / Ma-3220 · M…», `ui/perfil/SeccionesPerfil.kt:104-110`; se ve en `docs/capturas/h14/07-perfil-oscuro-vertical.png`).
  - La fila tiene dos gestos distintos (tocar = cambia la gráfica de más abajo, fuera de la vista; icono = abre el análisis: `ui/perfil/SeccionesPerfil.kt:77-78`, `:125-135`) y necesita una tarjeta fija de instrucciones para explicarse (`ui/perfil/SeccionesPerfil.kt:71`, `strings_perfil.xml:12`).
- **Evolución**: exige 3 pasadas (`ui/perfil/SeccionesPerfil.kt:58`, `:161`). Hoy enseña casi siempre «Aún no hay evolución» a media pantalla (`ui/perfil/SeccionesPerfil.kt:171-177`, `strings_perfil.xml:24-25`). Cuando hay gráfica, solo rotula mejor y peor tiempo y dos fechas, con el mejor arriba (`ui/perfil/GraficoEvolucion.kt:77-89`).
- **Historial reciente** (`ui/perfil/SeccionesPerfil.kt:193-204`): repite la lista de Viajes. La primera pasada de un tramo siempre es récord (`motor/analisis/PerfilPiloto.kt:109`), así que casi todas las filas llevan estrella y «Récord» (`ui/perfil/SeccionesPerfil.kt:253-264`): la marca deja de significar nada.
- «Récord» y «Mejor» conviven para lo mismo (`strings_perfil.xml:23`, `strings_mistramos.xml:12`; `ui/perfil/SeccionesPerfil.kt:261`, `:269`).

### 1.3 Mis tramos: filas densas y jerga de kilómetros
- Cada fila junta miniatura, hasta cuatro líneas de texto gris (título, rango, subtítulo, medidas: `ui/mistramos/FilaTramo.kt:188-222`), mejor tiempo, «Media …» y estrella (`ui/mistramos/FilaTramo.kt:143-157`, `:248-262`).
- Un tramo de vía se titula «km 20,1→32,4» (`ui/mistramos/FilaTramo.kt:186-187`, `strings_mistramos.xml:69`). Es un rango, no un lugar: no dice entre qué pueblos.
- Cabeceras «Ma-15 · km crecientes / km decrecientes» (`ui/mistramos/ListaCarreteras.kt:105-110`, `strings_mistramos.xml:66-67`): jerga interna cuando falta el destino.
- El dato grande de la fila es un tiempo absoluto («3:12,4»). Sin otra pasada con que compararlo no dice nada. La velocidad media, que sí se entiende sola, no sale en la fila (`ui/mistramos/FilaTramo.kt:227-232`); en el Perfil sí (`ui/perfil/SeccionesPerfil.kt:142-146`).
- Las carreteras se ordenan por número de matrícula (`ui/mistramos/GrupoCarretera.kt:32-36`), no por lo que más conduces ni por lo último. La cabecera no resume el grupo (km, tramos, ritmo).

### 1.4 Detalle del tramo: repite la fila y mezcla dos sistemas de kilómetros
- Los tres recuadros (longitud, pasadas, mejor: `ui/mistramos/DetalleTramoScreen.kt:307-329`) repiten lo que ya decía la fila. No hay velocidad media, curvas ni dificultad.
- El título usa el kilómetro de la carretera (`ui/mistramos/FilaTramo.kt:236-241`) y los subtramos usan kilómetros desde la salida del tramo (`ui/mistramos/ListaSubtramos.kt:66-68`, `ui/mistramos/DetalleTramoUiState.kt:16-17`). En la misma pantalla «km 1,2 → 3,6» y «km 20,1→32,4» significan cosas distintas.
- El título de un tramo de vía ocupa hasta tres líneas a 17 sp (`ui/mistramos/DetalleTramoScreen.kt:88-90`, `:200`).
- Con una sola pasada, la lista «Pasadas» es una fila con estrella y «Mejor» (`ui/mistramos/DetalleTramoScreen.kt:396-402`); los subtramos dicen «Primera vez» (`ui/mistramos/ListaSubtramos.kt:113-118`).

### 1.5 Mis carreteras: lo más interesante, sin conclusión
- «Distancia total» y «Recorridos» (`ui/miscarreteras/MisCarreterasScreen.kt:138-153`) repiten el Perfil con otro nombre y, por calcularse distinto, con otra cifra.
- «Curvas por grado» son ocho barras sin frase (`ui/miscarreteras/MisCarreterasScreen.kt:186-208`). «Grado 1» no recuerda que 1 es la más cerrada (`strings_miscarreteras.xml:26`).
- «Carreteras con más curvas» ordena por cuenta absoluta (`motor/analisis/AgregadoCarreteras.kt:229-232`): gana la más larga, no la más revirada.
- La velocidad media en marcha por vía y sentido se calcula con todo lo demás (`data/carreteras/CalculadorCarreteras.kt:83`, `motor/analisis/VelocidadVias.kt:45-60`) y solo se usa en la capa de calor del mapa. Aquí no aparece.

### 1.6 Análisis de pasada: sólido pero sin titular
- Es la pantalla mejor resuelta. Aun así apila selector, dos gráficos, tabla y óptimo (`ui/analisis/AnalisisScreen.kt:156-159`) sin una frase que diga qué mirar.
- Sin pasada comparada (lo habitual hoy), la columna de diferencia va a «—» (`ui/analisis/TablaParciales.kt:79-80`) y el óptimo no existe (`ui/analisis/AnalisisUiState.kt:60`). Queda una tabla de tiempos por sector.

## 2. Propuestas

Leyenda de cálculo: **YA** = el dato existe tal cual; **POCO** = agregación sencilla sobre lo que ya se calcula; **NUEVO** = cálculo nuevo en `:motor` (con TDD).

### Propuesta A — «Tablero del piloto» (conclusiones primero)

**Idea.** El Perfil deja de ser una lista de listas y pasa a ser una página de 5–7 tarjetas, cada una con una frase y una cifra grande. Las listas quedan a un toque.

**Pantallas**
1. **Perfil**:
   - Cabecera: «Desde el 2 nov · 10 viajes · 412 km».
   - Tarjeta «Último viaje», con acceso al resumen de viaje de H28.
   - Tarjetas de conclusión: tu ritmo, tu carretera, tus curvas, lo nuevo, récords.
   - Enlaces «Ver todos los viajes» y «Ver carreteras».
2. **Mis tramos**: sin cambios de estructura. La fila pasa a tres elementos: nombre, «12,3 km · 68 km/h» y tiempo. Se quita el filtro «Repetidos».
3. Las secciones que necesitan repeticiones (evolución, óptimo) solo aparecen cuando hay datos; si no, no ocupan sitio.

**Conclusiones**

| # | Conclusión | Datos | Cálculo |
|---|---|---|---|
| 1 | Ritmo real: media en marcha global | Suma de `Marcha` de todas las `VelocidadVia` (`motor/analisis/VelocidadVias.kt:20-28`) | POCO |
| 2 | Último viaje frente a tu media | `AnalisisMarcha` del último recorrido frente a la global | POCO |
| 3 | Carretera que más conduces | `Carretera.pasadas` y `metros` (`motor/analisis/AgregadoCarreteras.kt:93-99`), ordenado por uso | POCO |
| 4 | Carretera más revirada (curvas por km) | `Carretera.curvas / metros` | POCO |
| 5 | Tu curva tipo: reparto por grado en una frase | `CurvasPorGrado` (`motor/analisis/AgregadoCarreteras.kt:20-34`) | YA |
| 6 | Territorio nuevo: vías con una sola pasada | `ViaConducida.pasadas == 1` | POCO |
| 7 | Récords de verdad: solo si mejoran un tiempo anterior | `EntradaHistorial.esRecord` con `numeroPasadas ≥ 2` | POCO |
| 8 | Subtramos del último viaje: mejorados o no | `SubtramoGuardado.tiempoMs` frente a `mejorMs` | YA |

**Ejemplo de texto**
- «Vas a **71 km/h** en marcha. En tu último viaje, 74: tres por encima de lo tuyo.»
- «Tu carretera es la **Ma-10**: has pasado en 6 de tus 10 viajes y llevas 86 km en ella.»
- «La más revirada que has hecho: **Ma-2141**, 9 curvas por kilómetro.»
- «De cada 10 curvas, 5 son rápidas (grados 5 y 6) y 1 es horquilla.»
- «Este mes has estrenado **14 km** de carretera.»

**Esfuerzo**: M. Un agregado nuevo en `:motor` (p. ej. `ConclusionesPiloto`) sobre `PerfilPiloto`, `ResumenCarreteras` y `VelocidadVia`, y rehacer `PerfilScreen`.

**Riesgo**: bajo. El principal es redactar frases que suenen bien con pocos datos: cada tarjeta necesita un umbral mínimo y desaparecer si no lo cumple.

### Propuesta B — «Carreteras como protagonistas»

**Idea.** La unidad que Joan reconoce es la carretera (Ma-10, Ma-2141), no el «tramo km 20,1→32,4». Se fusionan la pestaña Carreteras y «Mis carreteras» en un sitio, y cada carretera tiene su ficha.

**Pantallas**
1. **Mis carreteras** (sustituye a la pestaña y al icono):
   - Mapa de calor arriba, sobre el mapa base, con conmutador uso/velocidad.
   - Debajo, una tarjeta por carretera con placa, km conducidos, veces y ritmo, ordenable por uso, curvas o reciente.
2. **Ficha de carretera** (nueva):
   - Mapa con lo conducido.
   - Dos columnas por sentido («hacia Sóller» / «hacia Andratx») con ritmo y veces.
   - Barra de curvas por grado de esa carretera.
   - Sus tramos de vía como franjas sobre una regla de kilómetros, en vez de filas sueltas.
   - Pasadas y subtramos de esos tramos.
3. **Viajes**: lista cronológica aparte (enlaza con el resumen de viaje de H28).
4. **Perfil**: se reduce a totales y accesos, o se adopta el tablero de A.

**Conclusiones**

| # | Conclusión | Datos | Cálculo |
|---|---|---|---|
| 1 | Ritmo por carretera | `VelocidadVia` sumada por `ref` | POCO |
| 2 | Ritmo por sentido | `adelante` / `atras` (`motor/analisis/VelocidadVias.kt:45-60`); hay que orientar las vías de una misma carretera de forma coherente | POCO–NUEVO |
| 3 | Dificultad de la carretera: curvas por km y reparto por grado | `UsoVia.curvas` existe por vía; el reparto por grado por carretera no se guarda hoy | NUEVO (pequeño) |
| 4 | Qué trozo conoces: rangos de km conducidos | `DatosVia.pkDesde` / `pkHasta` de sus tramos | YA |
| 5 | Veces y última vez | `Carretera.pasadas` YA; fecha del último recorrido por carretera | POCO |
| 6 | Mejor tiempo y media de cada tramo de vía | `ResumenTramo.mejorTotalMs`, `mediaMs` | YA |
| 7 | Tramo más rápido y más lento de la carretera | velocidad de cada tramo: longitud entre mejor tiempo | POCO |
| 8 | Cuánto de la carretera has hecho | Necesita la longitud total por `ref` en los datos de vías; **no verificado que exista** | NUEVO |

**Ejemplo de texto**
- Tarjeta: «**Ma-10** · 86 km en 6 viajes · 58 km/h · 7 curvas/km».
- Ficha: «Hacia Sóller vas a **61 km/h**; hacia Andratx, a 55.»
- Ficha: «Conoces del km 62 al 108. Tu trozo más lento: km 71→76 (44 km/h, 11 curvas/km).»

**Esfuerzo**: L. Pantalla nueva, navegación nueva, agregación por carretera y sentido, y retirar una pestaña y una pantalla.

**Riesgo**: medio. El sentido estable sigue pendiente en [2026-11b.md](2026-11b.md) y el kilómetro es estimado («~»): la ficha lo haría muy visible. Tocar la navegación obliga a rehacer bastantes tests de interfaz.

### Propuesta C — «Temporada» (progresión tipo juego WRC)

**Idea.** El perfil como modo carrera: temporada en curso, hitos desbloqueados y carné de carreteras. Es lo más vistoso y lo más alineado con la estética de juego.

**Pantallas**
1. **Perfil = Temporada**:
   - Cabecera tipo ficha de piloto (Trombito con casco, km de la temporada, viajes).
   - Calendario por semanas con barras de km.
   - Hitos conseguidos y el siguiente.
2. **Carné de carreteras**: rejilla de placas (Ma-10, Ma-15…), con las conducidas en color y las repetidas con contador.
3. **Vitrina de récords**: solo tiempos que mejoran uno anterior, con fecha y diferencia.
4. **Resumen del mes**: una tarjeta que se puede compartir como imagen.

**Conclusiones**

| # | Conclusión | Datos | Cálculo |
|---|---|---|---|
| 1 | Km por semana o mes, y racha | Hoy solo hay total, primero y último (`domain/tramos/RepositorioTramos.kt:76-83`); hace falta una consulta por fecha | NUEVO (pequeño, en datos) |
| 2 | Carreteras coleccionadas | `ResumenCarreteras.carreteras` sin el corte a 10 ni el filtro de curvas (`motor/analisis/AgregadoCarreteras.kt:229-232`) | POCO |
| 3 | Hitos de kilómetros (100, 250, 500…) | `kmTotales` | YA |
| 4 | Hitos de curvas | `CurvasPorGrado.total` y `horquillas` | YA |
| 5 | Mejor semana | Sale de la serie del n.º 1 | POCO |
| 6 | Récords batidos en la temporada | `PuntoEvolucion.esRecord` sin contar la primera pasada | POCO |
| 7 | Viaje más largo y viaje más revirado | Distancia por recorrido (en la base) y `AnalisisRecorrido.curvas` por recorrido (`motor/analisis/AgregadoCarreteras.kt:75-79`) | POCO |
| 8 | Nivel de piloto | Fórmula inventada sobre km y curvas | NUEVO y decisión de producto |

**Ejemplo de texto**
- «Temporada 2026 · **412 km** · 10 viajes · 3 semanas seguidas saliendo.»
- «Hito: **100 horquillas**. Siguiente: 500 km (te faltan 88).»
- «Carné: 12 carreteras. Nuevas este mes: Ma-2141 y Ma-1101.»

**Esfuerzo**: M–L. Consulta por fechas, catálogo de hitos, rejilla de placas y bastante diseño visual.

**Riesgo**: medio-alto.
- Mucha decisión de producto (qué hitos, si hay niveles).
- Con diez viajes el calendario y la vitrina salen medio vacíos.
- Los hitos nunca deben premiar velocidad en carretera abierta: solo km, curvas, carreteras y constancia.

## 3. Recomendación

**Hacer A ahora y tomar de B la ficha de carretera como segundo paso. De C, solo dos piezas baratas.**

1. **Paso 1 (M): Tablero del piloto (A).** Responde literalmente a «sacar datos o conclusiones». Usa datos que ya existen y no dependen de repetir tramos. No toca la navegación.
2. **Paso 2 (L, cuando el sentido estable de 2026-11b esté resuelto): ficha de carretera (B).** Sustituye a la pestaña «Carreteras» y a «Mis carreteras».
3. **De C**: «carreteras estrenadas» e hitos de km y curvas, como una tarjeta más del tablero. Sin niveles ni calendario hasta que haya más viajes.

**Qué eliminar o fusionar (vale para cualquier propuesta)**
- **Fusionar** el icono «Mis carreteras» y la pestaña «Carreteras» en una sola entrada con un solo nombre (`ui/mistramos/MisTramosScreen.kt:103`, `ui/mistramos/PestanasTramos.kt:44-54`).
- **Eliminar** el filtro «Repetidos» mientras casi no hay repetidos; convertirlo en orden o recuperarlo más adelante (`ui/mistramos/MisTramosScreen.kt:204-209`).
- **Eliminar** la tarjeta fija de instrucciones del Perfil (`ui/perfil/SeccionesPerfil.kt:71`) y el doble gesto de la fila: un toque abre el tramo.
- **Ocultar** «Evolución» con menos de 3 pasadas en vez de enseñar el estado vacío (`ui/perfil/SeccionesPerfil.kt:161-162`), y llevar la gráfica al detalle del tramo, que es donde tiene contexto.
- **Fusionar** «Historial reciente» con la lista de Viajes; en el Perfil, solo «Último viaje».
- **Corregir** el significado de «Récord»: la primera pasada no lo es (hoy sí: `motor/analisis/PerfilPiloto.kt:109`). Unificar «Récord» y «Mejor» en una sola palabra.
- **Cambiar** «Km/h medios» por la media en marcha de `VelocidadVias` y decirlo («en marcha»). Comprobar si «Tramos» mezcla viajes y tramos de vía (`ui/perfil/PerfilViewModel.kt:42`).
- **Quitar** los tres recuadros del detalle que repiten la fila (`ui/mistramos/DetalleTramoScreen.kt:307-329`) o cambiarlos por velocidad media, curvas y curvas por km.
- **Unificar** los kilómetros de los subtramos con los de la carretera cuando el tramo es de vía (`ui/mistramos/ListaSubtramos.kt:66-68`).
- **Añadir la velocidad media a la fila** de Mis tramos y bajar el tiempo absoluto a segundo plano cuando solo hay una pasada (`ui/mistramos/FilaTramo.kt:227-232`).
- **Ordenar «más curvas» por curvas/km**, no por total (`motor/analisis/AgregadoCarreteras.kt:231`), y poner «1 = la más cerrada» junto a «Grado 1» (`strings_miscarreteras.xml:26`).
- **Vocabulario único**: «viaje» (trayecto entero), «tramo» (trozo de carretera), «pasada» (una vez cronometrada por un tramo). Dejar de usar «recorridos» y «pasadas» para decir viajes en Mis carreteras (`strings_miscarreteras.xml:20`, `:36`).

## 4. Dudas de producto para QUESTIONS.md
1. **Qué propuesta**: A (tablero), B (carreteras), C (temporada) o la combinación recomendada (A ahora, ficha de carretera después, hitos de km/curvas de C). Recomendación: la combinación.
2. **Velocidad en el perfil**: ¿se enseña la media en marcha y por carretera/sentido como dato protagonista? Y la velocidad máxima, ¿se enseña fuera del análisis de una pasada? Recomendación: media sí, máxima solo en el análisis.
3. **Nombre único** para la entrada fusionada: «Mis carreteras» (recomendado) o «Carreteras» dentro de Mis tramos.
4. **Filtro «Repetidos»**: quitarlo (recomendado), dejarlo o convertirlo en un orden «más repetidos».
5. **Hitos y niveles** (si se toma algo de C): ¿solo hitos de km, curvas y carreteras (recomendado) o también nivel de piloto?
6. **Tramos de vía sin destino**: ¿qué texto sustituye a «km crecientes/decrecientes»? Opciones: pueblo más cercano a la meta (cálculo nuevo), rumbo («hacia el norte»), o solo los km. Recomendación: pueblo más cercano si los datos lo permiten; si no, rumbo.
7. **Velocidad por grado de curva** («en las de grado 3 pasas a 48 km/h»): es el dato más «de copiloto» y no necesita repeticiones, pero exige un cálculo nuevo (cruzar la posición de cada nota con la velocidad de los puntos grabados; M). ¿Interesa para una segunda vuelta?
