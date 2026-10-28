# Rally Copilot — Prompt inicial para Claude Code

## 1. Contexto

Vas a desarrollar **Rally Copilot**, una app Android de navegación cuyo núcleo es un **copiloto de rally por voz**. Lee la carretera con nomenclatura de rally (notas de pacenote) y convierte cada trayecto en una experiencia de tramo de rally, con estética de videojuego WRC.

Trabajas en una carpeta que contiene tres proyectos:
- `rally-copilot/`: este proyecto. Repo Git ya inicializado y vacío.
- `sous-chef/`: proyecto hermano. **Es la referencia principal y la más actualizada.**
- `trombito/`: proyecto hermano, más antiguo.

### Cómo usar los proyectos hermanos
- Sirven para que los tres proyectos sigan la **misma línea general** (stack, arquitectura, convenciones, estilo visual, tooling, CI), no para copiarlos a ciegas.
- Revisa `sous-chef` en profundidad: CLAUDE.md, skills, convenciones de commits, estructura de módulos, librerías, minSdk/targetSdk, workflows de GitHub Actions, configuración del emulador y tests, colores y logo.
- Extrae solo lo que sea **transferible y no específico** de ese proyecto: instrucciones generales, decisiones mías de carácter general y utilidades reutilizables.
- Ante conflicto entre `sous-chef` y `trombito`, prevalece `sous-chef`.
- No arrastres deuda técnica, código muerto ni decisiones específicas de su dominio. Si dudas si algo debe heredarse, regístralo como pregunta (ver §3).

## 2. Fase 0: Preparación del proyecto (antes de programar funcionalidad)

1. Analiza `sous-chef` (y `trombito` como apoyo). Resume en `docs/HERENCIA.md` qué heredas, qué descartas y por qué.
2. Crea `CLAUDE.md` del proyecto, con las instrucciones generales heredadas más las específicas de Rally Copilot (este documento).
3. Revisa las skills de los proyectos hermanos. Instala o adapta las que apliquen y propón nuevas si hacen falta.
4. Crea la estructura base del proyecto Android con el **mismo stack, arquitectura y minSdk que `sous-chef`**.
5. Configura los workflows de GitHub Actions siguiendo `sous-chef`: un push a `main` genera la release del APK. Prepara también la subida al **track de pruebas (testing) de Play Store**.
6. Crea `ROADMAP.md` con el plan general dividido en **hitos**. Para cada hito: objetivo, alcance, criterios de aceptación y tests. Usa como base la propuesta de §9 y refínala.
7. Crea `docs/DECISIONES.md` (registro de decisiones técnicas con su justificación) y `QUESTIONS.md` (dudas pendientes, ver §3).
8. Genera el **logo**: mismo estilo, paleta y lenguaje visual que los logos de los proyectos hermanos, con un **casco de carreras** como motivo. Entrégalo en formato vectorial más los iconos adaptativos de Android.
9. Investiga y documenta en `docs/DECISIONES.md` las opciones técnicas abiertas que se indican en este documento, antes de implementarlas.

## 3. Reglas de trabajo

- **Modo desatendido.** Avanza hito a hito con la mínima intervención. Al cerrar cada hito, actualiza `ROADMAP.md` (estado, qué se hizo, qué quedó pendiente).
- **No tomes decisiones de producto por tu cuenta.** Las decisiones técnicas menores sí, registrándolas en `docs/DECISIONES.md`. Ante cualquier duda de funcionalidad, interfaz o alcance:
  - Apúntala en `QUESTIONS.md` con contexto, opciones y tu recomendación.
  - **Sigue avanzando** con lo que no dependa de esa duda.
  - **Para y avísame** solo cuando no puedas avanzar más o al completar un hito. En ese momento presenta todas las dudas acumuladas juntas.
- **Git:**
  - Commits locales frecuentes siguiendo el estándar de commits de `sous-chef`.
  - **Nunca añadas `Co-authored-by`** ni ninguna atribución de coautoría.
  - **Nunca hagas push.** El push lo decido yo, porque dispara los workflows de release.
- **Tests:**
  - Tests pequeños en cada hito, centrados en la lógica crítica: generación de notas, cronometraje, detección de tramos, cálculo de deltas, parsing de datos.
  - Al terminar todo, una batería de tests end-to-end.
- **Pruebas en dispositivo:**
  - Usa el emulador del PC, como en `sous-chef`.
  - Simula el GPS con rutas reales de Mallorca (reproducción de trazas).
  - Cuando la app esté suficientemente avanzada, puedes pedirme que conecte mi teléfono por USB para pruebas reales.
- Idioma de la app: **solo español**.

## 4. Producto: alcance funcional

### 4.1 Cobertura y modo offline
- Zona inicial: **solo la isla de Mallorca**.
- **Offline por defecto.** Rutas, notas, voz y mapas funcionan sin conexión. Internet solo se usa para:
  - Descargar mapas (vectorial y satélite).
  - Refrescar incidencias **al abrir la app y al iniciar una ruta**.
- **Sin backend propio.** Todos los datos del usuario se quedan en el móvil y cada usuario es independiente. No hay cuentas ni reportes comunitarios.

### 4.2 Mapas
- Vista **vectorial** y vista **satélite**.
- Fuentes propuestas, que puedes contrastar con alternativas antes de decidir:
  - Vectorial y datos de carretera: **OpenStreetMap**.
  - Satélite: **PNOA del IGN**.
  - Elevación: **MDT del IGN**.
  - Límites de velocidad: OSM.
  - Respeta las atribuciones de licencia.
- Estrategia de descarga del satélite:
  - **Base:** toda la isla hasta zoom 16 (~600 MB estimados), descargada en el primer uso.
  - **Alta calidad automática:** zoom 17–18 en un corredor de ~300 m alrededor de rutas planificadas, rutas recorridas y tramos guardados.
  - **Manual:** el usuario selecciona una zona y la descarga a zoom 18–19.
  - **Ajustes:** límite de espacio, solo WiFi (opcional) y gestión o borrado de caché.
- Los mapas se descargan desde la app; no van empaquetados en el APK.

### 4.3 Navegación
- **Navegación completa giro a giro, offline**, con cálculo y recálculo de rutas en el dispositivo. El motor está por decidir: investiga GraphHopper, BRouter u otros.
- **Modo libre** sin destino: el copiloto lee la carretera por la que vas y registra el recorrido.
- Siempre visibles: **límite de velocidad de la vía y velocidad actual**.
- Debe seguir funcionando con **pantalla apagada o la app en segundo plano** (servicio en primer plano), igual que Google Maps.
- Orientación vertical y horizontal según la posición del móvil.
- Android Auto: fuera de alcance.

### 4.4 Notas de rally (núcleo)
- **Escala 1–6**: 1 es la curva más cerrada y lenta; 6, casi recta a fondo.
- Generación **automática** a partir de la geometría de la vía (curvatura de OSM) y la elevación. Debe incluir **todos los elementos posibles**:
  - Dirección y grado.
  - Horquillas.
  - Modificadores: abre, cierra, larga, "y"/encadenadas.
  - Distancias entre notas.
  - Rasantes, cambios de rasante, badenes.
  - Cruces, estrechamientos, puentes.
  - Avisos de precaución ("cuidado", "no cortes", etc.).
  - Cualquier otro elemento que se pueda derivar de los datos.
- **Vocabulario:** propón un glosario en estilo copiloto español. Más adelante se refinará con transcripciones de audios y vídeos de tramos reales (§5.2).
- **Anticipación de lectura** adaptativa según la velocidad, más un ajuste de usuario de 5 niveles (de "muy pronto" a "muy tarde").
- Opción de **notas simplificadas** (suave / media / cerrada).
- **Edición de notas**, de dos formas:
  - Tocando la nota sobre el mapa.
  - Por voz.
  
  Las notas editadas se guardan ligadas al tramo y **siempre prevalecen** sobre las automáticas.
- **Modo reconocimiento:** pasada en la que dictas o corriges notas. Las notas se calibran respecto a la **velocidad máxima de la vía**.

### 4.5 Modos del copiloto
1. **Silencio**: nada de voz.
2. **Solo alertas**: radares, incidencias y límites.
3. **Notas**: notas de rally y alertas.
4. **Copiloto completo**: lo anterior más **delta y parciales** por voz. No incluye otros comentarios no viales.

- La voz baja el volumen de la música mientras habla (**audio ducking**).
- **Avisos visuales** además de la voz: icono de nota grande tipo roadbook en pantalla y banners de radar/incidencia que entran y salen.

### 4.6 Alertas e incidencias
- **Todo tipo de radares**: fijos, de tramo y móviles si hay fuente.
- **Obras, accidentes, retenciones y cortes**: toda la información posible de fuentes abiertas, principalmente la **DGT**: datos abiertos y punto de acceso nacional DATEX II. Investiga qué cubre Baleares.
- Los radares fijos y de tramo se guardan offline con los mapas. Las incidencias dinámicas se refrescan al abrir la app y al iniciar una ruta.
- No hay API pública de Waze ni de Google Maps para esto: no las uses.

### 4.7 Tramos, tiempos y análisis
- Un **tramo** empieza al iniciar la navegación y termina al llegar o parar.
- **Grabación automática** de los trayectos, activable o desactivable en Ajustes.
- División por defecto en **4 sectores grandes** (como en rally) y **microsectores** equitativos dentro de cada uno. Todo **configurable**: número de cortes y posición, arrastrando sobre el mapa.
- **Detección de tramo conocido:** si un recorrido coincide con un tramo guardado (aunque no se haya arrancado desde él), la app lo detecta y compara.
- **Delta en vivo** contra el mejor tiempo, en pantalla (verde/rojo) y por voz en el modo copiloto completo. Parciales en cada sector y microsector.
- **Precargar rutas:**
  - Importar GPX/KML.
  - Dibujarlas en el mapa.
  - Reutilizar rutas ya recorridas.
- **Análisis post-tramo:**
  - Tiempo total y por sector/microsector.
  - Delta contra el mejor.
  - Velocidad media y máxima por sector.
  - Gráfico velocidad/distancia.
  - Mapa coloreado por velocidad.
  - Repetición del recorrido sobre el mapa.
- Exportar o compartir tramos: **fuera de alcance por ahora**.

### 4.8 Perfil del piloto
Estadísticas agregadas de todos los tramos: kilómetros, número de tramos, mejores tiempos, evolución y velocidades medias. Incluye el historial de análisis de cada tramo.

## 5. Voz

### 5.1 Orden de implementación
1. **Primero:** voz **sintética en el dispositivo**, offline. Ofrece un par de opciones de voz masculina. Investiga motores TTS on-device viables (por ejemplo, Piper o sherpa-onnx) y documenta la elección. Es aceptable que cada voz ocupe entre 50 y 150 MB.
2. **Después**, en un hito posterior: **voz clonada de Luis Moya** (con autorización).

### 5.2 Pipeline de la voz de Moya (en el PC, GPU RTX 4080)
- Prepara una carpeta de trabajo, por ejemplo `voice-lab/`, con instrucciones claras de **dónde dejo yo los audios y vídeos** de Moya y de tramos reales. Todavía no los tengo todos.
- El pipeline debe:
  1. Transcribir localmente (Whisper o similar).
  2. Extraer del material real el vocabulario y las expresiones de copiloto, para refinar el glosario de §4.4.
  3. Entrenar o clonar el modelo de voz.
  4. **Pregenerar** los audios de las notas y frases más comunes (números, direcciones, modificadores, distancias, alertas) para empaquetarlos en la app.
- En el móvil:
  - Audios pregenerados para lo común.
  - Motor TTS con la voz clonada para frases no previstas.
  - Todas las voces van **dentro de la app**, sin consultas a internet.
- Documenta el pipeline en `docs/VOZ.md` para poder repetirlo.

## 6. Interfaz

- **Estética de videojuego WRC y retransmisión de rally:**
  - Cronómetro, barra de progreso del tramo, parciales y delta verde/rojo.
  - Iconos de notas tipo roadbook.
  - Banners animados.
- Paleta y lenguaje visual **coherentes con los proyectos hermanos**.
- **Modo oscuro por defecto**, con opción de modo claro.
- **Pantalla de inicio tipo menú de juego:** Conducir · Mis tramos · Reconocimiento · Mapas offline · Perfil del piloto · Ajustes.
- **Pantalla de conducción:**
  - El mapa es protagonista.
  - Hay un modo alternativo centrado en las notas, que van pasando en grande.
  - Siempre visibles: velocidad actual y límite.
- Otras pantallas:
  - Buscar destino.
  - Mis tramos (listado, detalle, edición de sectores y notas).
  - Análisis de tramo.
  - Mapas offline (zonas, calidades, espacio).
  - Ajustes: voz, modo del copiloto, anticipación, notas simplificadas, grabación automática, mapas, tema.

## 7. Fuera de alcance (hitos futuros sin definir)
- Modo circuito / tramo cerrado.
- Android Auto.
- Exportar o compartir tramos.
- Otras zonas fuera de Mallorca.
- Backend, cuentas y funciones comunitarias.

## 8. Investigaciones previas a documentar
- Motor de enrutado offline.
- Librería de mapas (vector + raster satélite offline).
- Formato de almacenamiento de teselas.
- Algoritmo de generación de notas a partir de curvatura y elevación, con calibración de grados.
- Fuentes DGT disponibles para Baleares.
- Motor TTS on-device y opciones de clonado de voz.

Para cada una, documenta en `docs/DECISIONES.md` las alternativas y la recomendación. Si la elección afecta al producto, pásala a `QUESTIONS.md`.

## 9. Propuesta inicial de hitos (refínala en `ROADMAP.md`)
- **H0:** Preparación (§2): herencia, CLAUDE.md, skills, estructura, CI, logo, roadmap.
- **H1:** Mapa base. Vectorial y satélite de Mallorca, descarga offline por niveles, posición GPS, velocidad y límite.
- **H2:** Navegación offline. Búsqueda de destino, cálculo de ruta, giro a giro, recálculo, servicio en segundo plano.
- **H3:** Motor de notas. Generación automática desde geometría y elevación, glosario, anticipación adaptativa y ajustable.
- **H4:** Voz sintética on-device, modos del copiloto, ducking y avisos visuales.
- **H5:** Alertas. Radares offline e incidencias DGT con refresco.
- **H6:** Tramos y cronometraje. Grabación, sectores y microsectores, detección de tramo conocido, delta en vivo.
- **H7:** Análisis post-tramo y Perfil del piloto.
- **H8:** Edición de notas (táctil y voz) y modo reconocimiento.
- **H9:** Precarga de rutas (GPX/KML, dibujo, recorridas).
- **H10:** UI completa estilo WRC, menú de inicio y pulido visual.
- **H11:** Pipeline de voz de Moya e integración en la app.
- **H12:** Tests end-to-end, pruebas en dispositivo físico y preparación del track de pruebas de Play Store.

## 10. Primer paso
Empieza por la **Fase 0** (§2). Al terminarla, para y preséntame:
- El resumen de herencia.
- El `ROADMAP.md` propuesto.
- Las decisiones técnicas tomadas.
- Las dudas acumuladas en `QUESTIONS.md`.
