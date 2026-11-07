# Auditoría 7 · Producto: benchmark y roadmap futuro de Rally Copilot

> Fecha: 2026-11-07 · Autor: docs-researcher (sonnet) · Estado analizado: v1.2.0 (H0–H14 cerrados; H11 y parte de H12 pendientes) · Las propuestas NO son decisiones de producto: las decide Joan.

## Resumen
- El nicho «notas de rally automáticas en carretera abierta» ya tiene competidores directos: Tornante (iOS) y RallyTrax (Android, GitHub). Ninguno junta nuestro paquete: navegación offline completa + tramos cronometrados con sectores/deltas + órdenes de voz offline + radares DGT + estética WRC.
- Lo que ofrecen y nosotros no: CarPlay/Android Auto, notas combinadas por velocidad (agrupar curvas cercanas en una llamada), editor con fusionar/dividir, meteo, modo circuito, antelación de llamadas ajustable (EA WRC, 5 niveles) y garaje/combustible (RallyTrax).
- La queja típica en juegos y apps son las llamadas tardías (EA WRC) y el exceso de detalle: calibrar el ritmo con la velocidad real es el diferenciador más valioso.
- Lo más rentable para Joan: calibración real en coche, pasada contra el fantasma/mejor tiempo con delta visual, y exportar/importar tramos y notas como archivo (hoy «Fuera» en H7/H8).
- Descartadas por romper restricciones: ranking/comunidad, cuentas y nube, multi-región/idioma, tráfico en vivo, meteo en vivo. Android Auto es viable pero caro y con riesgo de política de Google/Play.

## Benchmark

| App | Qué hace bien | Idea aplicable |
|---|---|---|
| **RallyTrax** (Android, GitHub, gratis) | Graba el trayecto y genera notas 1–6 con modificadores («Left 3, tightens»); replay por posición GPS con TTS, tono previo y llamadas temporizadas a la velocidad; biblioteca con filtros, capas de velocidad/curvatura/aceleración, import/export GPX; garaje (combustible, mantenimiento). | Capas de curvatura/aceleración en Mis tramos (PRO-15). Es el rival directo en Android; nuestra ventaja es crono, navegación y voz. El garaje no encaja (PRO-28). |
| **Tornante: Rally Pacenotes** (iOS) | Notas 1–6 con horquilla, escuadra y rotonda; **agrupa curvas cercanas y espera a que entres antes de cantar la siguiente**; editor (cambiar, fusionar, dividir, añadir); **CarPlay** con próxima curva y distancia; HUD; previsión 12 h; modo circuito con cronómetro; aviso de límite al inicio; informe de llamada errónea; ducking de música. | Notas combinadas (PRO-1), editor completo (PRO-9), reportar llamada errónea local (PRO-4), límite al inicio (PRO-13), Android Auto (PRO-12). |
| **Pacenote** (iOS, AlgoTrader) | **Recce Mode**: notas propias para una ruta, reproducidas en la pasada; grados; posicionada para circuito cerrado. | Ya tenemos reconocimiento (H8); falta un flujo guiado en pasadas (PRO-3). Aviso de uso responsable en Acerca de. |
| **RallyPacenotes** (iOS) | Modos piloto y copiloto; controles horarios, tiempos de entrada, trip meter GPS y consumo para regularidad. | Trip parcial en el HUD (PRO-16). La regularidad no es del producto. |
| **iNotebook / iCodriver** (iPad) | Copilotos profesionales escriben notas a mano en el recce y las leen y cronometran en el tramo. | Editor con texto libre (PRO-9). |
| **EA Sports WRC / Dirt Rally 2.0** | Antelación en 5 niveles (Earliest…Latest), llamadas Normal/Simplificado, iconos HUD con distancia. Foros (no verificado): quejas de llamadas tardías incluso en «Earliest»; piden antelación proporcional a la velocidad. | Ajuste de antelación + auto-proporcional (PRO-2); modo Simplificado (PRO-6). |
| **RaceChrono / TrackAddict / Harry's LapTimer** | Tiempo predictivo, gráfico de delta, vuelta óptima teórica (mejor sector de cada pasada), sectores, vídeo con overlay, GPS + G + OBD-II. Auto Express los recomienda; los usuarios valoran el predictivo en solitario. | Tramo óptimo teórico (PRO-7), delta continuo y fantasma (PRO-8). Vídeo (PRO-24) y OBD-II (PRO-19) descartados. |
| **calimoto / Kurviger** | Rutas «curvy»: calimoto más simple, Kurviger con slider de sinuosidad y control fino; planificador web con sync; mapas offline según plan de pago (fuentes contradictorias). | **Ruta divertida** con Valhalla por coste de curvatura (PRO-5): hueco claro para la Tramuntana y la Ma-10. |
| **Waze** | Reportes comunitarios (policía, peligros, tráfico en vivo); necesita conexión y cuenta. | Descartado (PRO-26). Ya cubrimos radares/incidencias DGT. |
| **OsmAnd** | Muy completo offline, avisos de radares/pasos/stop; Android Auto con plantilla simplificada (de pago, solo build de Play); curva de aprendizaje alta; batería criticada. | Android Auto como plantilla simplificada (PRO-12); cuidar batería con pantalla apagada. |
| **Organic Maps** | Simple, sin cuentas ni rastreo, offline, ligero; sin tráfico en vivo. | Referencia de simplicidad: mantener Ajustes claros (MEMORIA 2026-11-01). |

## Mejores prácticas de copilotos reales aplicables
1. **Antelación suficiente, no excesiva**: el copiloto lee con tiempo para actuar, sin adelantarse tanto que se olvide; relee cuando nota que el piloto se pierde (Wikipedia, Pacenotes). Aplicación: antelación proporcional a la velocidad y relectura (PRO-2). Ya existe la orden «repite».
2. **Distancias como números sueltos, sin unidad** (Wikipedia). Revisar el glosario con copiloto-reviewer.
3. **Recce en dos pasadas** (Reeves, Team O'Neil): primera suave dictando curvas, distancias y rasantes; segunda más rápida añadiendo carácter; tercera opcional de limpieza. Aplicación: recce guiado (PRO-3).
4. **Detalle limitado**: suficiente para describir la carretera sin saturar. Aplicación: Simplificado y combinar curvas (PRO-1, PRO-6).
5. **Una nota incorrecta es peor que ninguna** y perder el sitio es peligroso. Aplicación: silenciar o avisar con baja confianza GPS o fuera del recorrido y re-sincronizar (PRO-14).
6. **Notas personales por tramo con prioridad sobre las automáticas**: ya hecho en H8 (`FusionNotas`).
7. Las ventanas de 2–4 s de llamada y 3–5 s de antelación salen de una calculadora web, no de un estándar: VERIFICAR con pruebas en coche; solo como punto de partida.

## Funcionalidades
Esfuerzo: S ≤ 1 día de agente, M 2–4 días, L > 1 semana. Riesgo: técnico/producto.

| id | Funcionalidad | Valor | Esf. | Riesgo | Encaja restricciones | Recomendación |
|---|---|---|---|---|---|---|
| PRO-1 | Notas combinadas por velocidad (agrupar curvas cercanas, esperar a la entrada para cantar la siguiente) | Alto | M | Medio (goldens y voz) | Sí | Hacer tras pruebas en coche; separa un copiloto de un lector de curvas |
| PRO-2 | Antelación en 5 niveles + auto-proporcional a la velocidad + relectura si se pierde | Muy alto | S–M | Bajo | Sí | **Hacer primero**: queja nº 1 en EA WRC y «canta poco» de Joan |
| PRO-3 | Recce guiado en 2–3 pasadas con resumen de diferencias frente a las notas automáticas | Alto | M | Medio | Sí | Hacer; amplía H8 |
| PRO-4 | «Reportar llamada errónea» local (marca + lista de revisión) | Medio | S | Bajo | Sí (local, sin backend) | Hacer; alimenta goldens y copiloto-reviewer |
| PRO-5 | Ruta divertida (maximizar curvas con coste de curvatura en Valhalla, slider de sinuosidad) | Alto | M–L | Medio (config Valhalla offline, tiempos) | Sí | Hacer tras PRO-1/2; hueco frente a calimoto/Kurviger |
| PRO-6 | Modo «Simplificado» (solo curvas ≥ grado X) | Medio | S | Bajo | Sí | Hacer con PRO-2 (un ajuste de nivel de detalle) |
| PRO-7 | Tramo óptimo teórico (mejor sector de cada pasada) | Medio-alto | S | Bajo | Sí | Hacer: ya hay sectores y deltas |
| PRO-8 | Delta continuo y coche fantasma en el mapa contra la mejor pasada | Alto | M | Medio (carga de UI al conducir) | Sí | Hacer; HUD simple |
| PRO-9 | Editor completo de notas: fusionar/dividir, texto libre, añadir aviso | Medio-alto | M | Bajo | Sí | Hacer; hoy solo se corrige tocando |
| PRO-10 | Exportar/importar tramos y notas como archivo (GPX + JSON, compartir de Android) | Alto | S–M | Bajo | Sí (archivo local; H7/H8 lo dejaron «Fuera»: requiere que Joan lo active) | Hacer; complementa la copia de H14b |
| PRO-11 | Modo nocturno automático (tema y mapa oscuro por hora/sol/sensor de luz) | Medio | S | Bajo | Sí | Hacer, barato |
| PRO-12 | Android Auto (CarAppService, categoría NAVIGATION, plantillas) | Medio (¿lo usa Joan?) | L | Alto: plantillas limitadas (pila de 5), reglas de distracción, revisión de Google/Play | Sí en sí; implica Play | **Preguntar a Joan**; si no hay pantalla compatible, aplazar |
| PRO-13 | Aviso de límite de velocidad al inicio del tramo y de radares | Medio | S | Bajo (límites OSM incompletos) | Sí | Hacer si hay dato en OSM |
| PRO-14 | Confianza de posición: avisar «notas en pausa» con GPS pobre o fuera del recorrido y re-sincronizar | Alto (seguridad) | M | Medio | Sí | **Hacer**; práctica real de copilotos |
| PRO-15 | Estadísticas ampliadas: curvatura/aceleración por tramo, mapa de calor de carreteras recorridas, km por carretera | Medio | M | Bajo | Sí | Opcional (efecto «videojuego») |
| PRO-16 | Trip parcial y distancia restante del tramo en el HUD | Bajo-medio | S | Bajo | Sí | Opcional |
| PRO-17 | Widgets y atajos (CONDUCIR, último tramo) | Bajo-medio | S–M | Bajo (Glance exige dependency-add) | Sí | Opcional tras PRO-11 |
| PRO-18 | Audio e intercom Bluetooth de casco (ruteo SCO/A2DP, micro del casco para órdenes) | Alto si Joan usa casco | M | Medio-alto (varía por dispositivo, Vosk con micro BT) | Sí | **Preguntar a Joan**; crítico si va en moto |
| PRO-19 | Lectura OBD-II (velocidad, RPM, marcha) | Bajo | L | Alto (hardware, permisos Bluetooth) | Sí | Descartar de momento: el GPS cubre el crono |
| PRO-20 | Avisos de accidentalidad DGT (tramos de concentración de accidentes) | Medio | M | Alto de datos: la DGT no publica puntos negros actualizados (VERIFICAR en datos.gob.es); no hay listado de la Ma-10 | Sí si el dato entra en el repo de datos | Investigar disponibilidad; si no hay dato, descartar |
| PRO-21 | Meteo | Bajo | M–L | Alto | **Parcial**: offline no hay previsión; solo caché del último refresco con internet | Descartar salvo caché opcional |
| PRO-22 | Modo circuito (vueltas, circuitos precargados) | Bajo | M | Medio | Sí, pero fuera de foco (solo Mallorca) | Descartar |
| PRO-23 | Voz de Luis Moya (H11) | Alto | L | Medio-alto (autorización, sin material) | Sí | Mantener pendiente según Joan |
| PRO-24 | Vídeo con overlay de datos | Medio | L | Alto (cámara, almacenamiento, batería) | Sí, pero fuera de foco | Descartar |
| PRO-25 | Ranking/comunidad, cuentas, nube (Drive, cuentas de Tornante 1.8), compartir con otros usuarios | — | — | — | **No**: sin backend, cuentas ni comunidad | Descartar |
| PRO-26 | Tráfico en vivo y reportes comunitarios (Waze) | — | — | — | **No**: offline por defecto, sin comunidad | Descartar |
| PRO-27 | Multi-región o multi-idioma (22 idiomas de Tornante) | — | — | — | **No**: solo Mallorca y español | Descartar |
| PRO-28 | Garaje, combustible y mantenimiento (RallyTrax) | Bajo | M | Bajo | Sí, pero no es copiloto | Descartar |

Resumen: 28 propuestas. Descartadas por restricciones firmes: PRO-25, 26, 27 y PRO-21 (salvo caché). Descartadas por valor/foco: PRO-19, 22, 24, 28. Condicionadas a una pregunta a Joan: PRO-12, 18, 10, 20.

## Hitos futuros propuestos
H11 (voz de Moya) y H12 (E2E/Play) siguen pendientes de Joan y no se reordenan.

| Hito | Objetivo (una línea) | Funcionalidades | Esf. |
|---|---|---|---|
| **H15 · Calibración en coche** | El ritmo del copiloto se ajusta a la velocidad real y no pierde el sitio: antelación configurable y proporcional, modo simplificado, confianza de posición y reporte de llamadas erróneas. | PRO-2, 6, 14, 4 | M |
| **H16 · Pasada con fantasma** | Cada tramo se vive contra tu mejor pasada: delta continuo, coche fantasma en el mapa, tiempo óptimo teórico y límite al inicio. | PRO-8, 7, 13 | M |
| **H17 · Notas de copiloto de verdad** | Las notas suenan como las de un copiloto: curvas cercanas combinadas, editor completo y recce guiado en dos pasadas. | PRO-1, 9, 3 | L |
| **H18 · Datos portables y noche** | Exportar/importar tramos y notas como archivo y modo nocturno automático. | PRO-10, 11 | S–M |
| **H19 · Ruta divertida** | Planificar rutas de curvas por la Tramuntana con slider de sinuosidad, offline con Valhalla. | PRO-5 (+ PRO-15) | M–L |
| **H20 · Coche y casco (condicional)** | Android Auto y/o audio e intercom Bluetooth de casco, según lo que use Joan. | PRO-12, 18 | L |
| Opcionales | Widgets/atajos, trip parcial, estadísticas, investigación de accidentalidad DGT. | PRO-15, 16, 17, 20 | S–M |

### Tres hitos recomendados
1. **H15 · Calibración en coche**: valor máximo, riesgo bajo, conecta con las pruebas reales pendientes y con la queja de Joan.
2. **H16 · Pasada con fantasma**: aprovecha el motor de sectores y deltas; diferencial frente a Tornante y RallyTrax.
3. **H17 · Notas de copiloto de verdad**: el mayor salto de calidad percibido; depende de lo aprendido en H15.

### Preguntas de producto para Joan (a QUESTIONS.md si se acepta)
- ¿Su coche tiene Android Auto, o conduce en moto con casco/intercom? Decide H20 (PRO-12 frente a PRO-18).
- ¿Se levanta el «Fuera: exportar/compartir» de H7/H8 para PRO-10 (solo archivo local)? Recomendación: sí.
- ¿Ruta divertida (PRO-5) como función de la app? Recomendación: sí.

## Incertidumbres (VERIFICAR)
- Tornante, Pacenote y RallyTrax son apps nuevas sin reseñas de texto; la valoración de calidad de cronometradores sale de fichas y del test de Auto Express.
- Android Auto: confirmar en la guía oficial vigente los requisitos de NAVIGATION y revisión de Play antes de estimar PRO-12; newly.app es de terceros y no se usa como hecho.
- Soporte de Organic Maps en Android Auto no confirmado oficialmente.
- Ventanas de antelación (2–4 s, 3–5 s): fuente no oficial; calibrar en coche.
- Dataset de accidentalidad DGT por tramos en Baleares: no localizado.
- Precios y capacidades offline de calimoto/Kurviger: fuentes contradictorias (blogs SEO, no verificadas).
- Citas de foros de EA y de blogs: no verificadas.

## Fuentes
- RallyTrax (Android): https://arnavsacheti.github.io/RallyTrax/
- Tornante: Rally Pacenotes (iOS): https://apps.apple.com/app/id6791901050
- Pacenote (iOS, Recce Mode): https://apps.apple.com/us/app/pacenote/id6762514014
- RallyPacenotes (iOS): https://apps.apple.com/app/id1012310200
- iNotebook pro (iCodriver): https://apps.apple.com/app/id499315149
- RallyEasy Pacenote: https://mwm.ai/apps/rallyeasy-pacenote/6748773993
- Team O'Neil, recce con smartphone: https://teamoneil.com/blog/making-pacenotes-with-your-smartphone/
- Pacenotes (Wikipedia): https://en.wikipedia.org/wiki/Pacenotes
- EA Sports WRC, ajustes de copiloto: https://racinggames.gg/article/ea-sports-wrc-how-to-change-co-driver-settings
- EA Sports WRC, llamadas tardías (foro, no verificado): https://forums.ea.com/discussions/wrc-technical-issues-en/late-co-driver-calls/8401708
- EA Sports WRC, guía de pace notes: https://www.overtake.gg/news/comments/75096/
- RaceChrono Pro: https://apps.apple.com/us/app/racechrono-pro/id1129429340
- TrackAddict Pro: https://apps.apple.com/us/app/id490135959
- GPS lap timer apps tested (Auto Express): https://autoexpress.co.uk/accessories-tyres/92336/gps-lap-timer-apps-tested
- Apps de telemetría (Motor1 ES): https://es.motor1.com/news/463217/mejores-apps-telemetria-telefono-movil/
- calimoto frente a Kurviger (similarweb): https://similarweb.com/app/google/com.calimoto.calimoto/vs/com.kurviger.app
- Apps de rutas moto (GeoRide, blog): https://eu.georide.com/blogs/georide/applications-itineraires-moto
- Android Auto, calidad de apps (oficial): https://developer.android.com/docs/quality-guidelines/car-app-quality
- Android for Cars App Library (oficial): https://developer.android.com/training/cars/apps
- OsmAnd en Android Auto (oficial): https://download.osmand.net/docs/user/navigation/auto-car
- Comparativas Waze/OsmAnd/Organic Maps (secundarias): https://alternativeto.net/software/waze/ · https://privacygear.pages.dev/en/reviews/organic-maps-review/
- DGT, conjuntos de datos: https://datos.gob.es/es/blog/los-conjuntos-de-datos-de-la-dgt-para-ayudar-mejorar-el-trafico-y-la-seguridad-vial
- Siniestralidad por carretera en Mallorca (Última Hora): https://www.ultimahora.es/noticias/local/2024/12/05/2286827/carreteras-mallorca-esta-registra-mas-accidentes-trafico.html

---
> Anexo de la [auditoría de noviembre de 2026](../2026-11.md) · Informe original del perfil, sin editar.
