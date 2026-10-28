# Generación automática de notas de rally (geometría OSM + elevación)

> Propósito: fijar el algoritmo, la escala 1–6 calibrable y el glosario base del motor de notas · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Motor de notas, ROADMAP.md § H3, H8

## Contexto
- Entrada: polilínea de la ruta (OSM, casada con el mapa), perfil de elevación (MDT del IGN), etiquetas OSM y velocidad de la vía. Salida: lista de notas con distancia, grado, dirección, modificadores y avisos. Offline, sin servicios externos (prompt §4.4).
- No existe una herramienta abierta madura OSM→notas: solo piezas. (a) Curvature (roadcurvature.com, GPL-3) calcula el radio como circunradio de 3 puntos consecutivos y pondera por longitud (<30 m "cerrada", <175 m "suave"); (b) un script del foro RBR clasifica por ángulo girado en 25 m centrales y 100 m (>140° en 100 m = horquilla); (c) Lipar et al. 2011 ajustan splines a GPS crudo y luego un círculo por tramo; (d) patentes HERE usan tripletes consecutivos (ventana deslizante). Autor de Curvature descartó el ajuste de arcos "por falsos resultados con círculos muy amplios".
- Física: en curva plana v = sqrt(μ·g·R) (Wikipedia, Banked turn); la fuente no da μ típico. Aquí μ=0,6 es **suposición calibrable** (VERIFICAR con trazas).
- MDT05 (rejilla 5 m, LiDAR PNOA, CC BY 4.0 SCNE según metadatos INSPIRE) es suficiente para rasantes largas, no para badenes pequeños. Es terreno desnudo: puentes y túneles aparecen como valle.

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| Circunradio de tripletes sobre polilínea remuestreada y suavizada (estilo Curvature) | Simple, rápido en móvil, determinista, testeable | Ruido de nodos OSM; hay que suavizar y fijar ventana | Idea libre; código de Curvature es GPL-3 (no copiarlo) | Probado en prototipo propio (abajo) |
| Cambio de rumbo acumulado por ventana (estilo script RBR) | Robusto, da ángulo total y horquillas | No da radio; peor para "cierra/abre" | Script de foro (no verificado) | Solo referencia |
| Ajuste de arcos/clotoides por tramos (Lipar 2011) | Radio y longitud de arco más fieles | Complejo; inestable en rectas largas | Paper (acceso no confirmado) | Investigación |
| Solo etiquetas OSM (`hazard=curve`, `curve`) | Cero cálculo | Cobertura escasa y subjetiva (la wiki pide no etiquetar curvas "porque sí") | ODbL | No viable como base |
| Aprender de trazas GPS de conductores | Refleja velocidad real | Requiere datos que no hay (sin backend) | — | Descartada |

## Recomendación
Pipeline propio, por etapas puras y testeables (todo en `motor/`, sin Android):
1. **Proyección y remuestreo**: UTM 31N, paso fijo de 5 m; suavizado gaussiano (σ≈10 m) antes de derivar. Puentes/túneles (`bridge`, `tunnel`) no se alisan.
2. **Radio**: circunradio con puntos a ±15 m (ventana = 3 muestras); curvatura con signo (izquierda +). Rectas: R>~400 m.
3. **Segmentación**: una curva empieza con R<250 m y termina con R>400 m (histéresis) o cambio de signo; se fusionan curvas del mismo sentido separadas <20 m. Por curva se guarda: ángulo total, longitud de arco, R mínimo, R de entrada y de salida.
4. **Grado**: de v_curva = sqrt(μ·g·R) con tabla calibrable. Horquilla = ángulo ≥~140° con R<~30 m (se canta aparte, no es grado 1).
5. **Modificadores**: *cierra* si el R baja ≥25 % o el grado baja ≥1 hacia la salida; *abre* al revés; *larga* si el arco ≥~80 m en grado ≤4 (*muy larga* ≥~150 m); *y* si el hueco al siguiente es <~25 m o <1,5 s a la velocidad prevista (no se cantan distancias entre notas unidas); si el hueco es mayor se canta la distancia redondeada (10, 20, 30, 40, 50, 60, 80, 100, 120, 150, 200…).
6. **Rasantes (MDT05)**: remuestreo a 5 m, suavizado σ≈15 m, curvatura vertical d²z/ds². *Rasante* = cresta con curvatura negativa fuerte; *cambio de rasante* = variación de pendiente ≥~5 puntos en <40 m; *bache/badén* solo desde OSM (`traffic_calming=bump|hump|table|dip`, `ford=yes`) porque 5 m no resuelve badenes. Umbrales por calibrar.
7. **Avisos desde OSM**: `bridge=yes` → "puente"; `tunnel=yes` → "túnel"; cruce = nodo con ≥3 vías del mismo rango o superior; `junction=roundabout` → "rotonda"; `railway=level_crossing`, `highway=stop|give_way|traffic_signals` → "cuidado"; "estrecha" por `width` o `lanes=1` en doble sentido (en Ma-2141 `width` no existe: solo `lanes`; VERIFICAR cobertura en el resto de la isla). "Cuidado/no cortes": curva que cierra con grado ≤2 o con visibilidad pobre (cresta justo antes), o `hazard=*`.
8. **Anticipación adaptativa**: distancia de aviso d = v·T_nivel + v·t_voz, con v suavizada y limitada a [40 m, 400 m]. T_nivel propuesto: muy pronto 8 s, pronto 6,5, normal 5, tarde 3,5, muy tarde 2,5 (ajuste de 5 niveles del prompt §4.4). Si dos notas se solapan, se encadenan con "y". Compensar ~1 s de latencia GPS/TTS.
9. **Notas simplificadas**: 1–2 → "cerrada", 3–4 → "media", 5–6 → "suave" (conserva dirección, horquilla y avisos).
10. **Relación con la velocidad de la vía**: v_ref = `maxspeed` (o valor por tipo de vía si falta). Si v_curva ≥ v_ref el límite manda: grado 6 y, por defecto, sin nota (salvo ángulo ≥~20°). El modo reconocimiento (H8) recalibra con la velocidad real del piloto.

Tabla radio → grado inicial (μ=0,6; **propuesta**, calibrable en `ajustes/escala.json`):
| Grado | v_curva | Radio aprox. | Nota |
|---|---|---|---|
| Horquilla | — | ángulo ≥140° y R<30 m | cantar aparte |
| 1 | <45 km/h | R <27 m | muy cerrada/lenta |
| 2 | 45–65 | 27–55 m | cerrada |
| 3 | 65–85 | 55–95 m | media |
| 4 | 85–110 | 95–160 m | media-rápida |
| 5 | 110–140 | 160–260 m | suave |
| 6 | >140 | 260–400 m | casi recta, a fondo |

**Prototipo desechable** (2026-10-28, Python, geometría OSM de Ma-2141 Sa Calobra, 12,7 km, sin MDT): 125 curvas; grados 1:35, 2:25, 3:29, 4:24, 5:12; 20 candidatas a horquilla; el lazo de ~263° a los 3,2 km coincide con el "nus de sa corbata" (identificación mía, VERIFICAR). Los radios mínimos de 6–8 m delatan que el circunradio sobre geometría OSM sobreestima la cerrazón → usar R de la mediana de la zona central de la curva, no el mínimo puntual.

## Glosario borrador (estilo copiloto español; **todo pendiente de refinar con transcripciones de §5.2**)
| Concepto | Frase propuesta | Notas |
|---|---|---|
| Curva | "derecha cuatro", "izquierda dos" | Dirección primero, grado después |
| Largas | "derecha cuatro larga", "…muy larga" | |
| Cierra / abre | "izquierda tres cierra", "derecha cinco abre" | "Cierra" puede cambiar el grado final: "…cierra a dos" (pendiente) |
| Horquilla | "horquilla izquierda" | Sin grado |
| Casi recta | "izquierda seis" (a fondo) | Muchos copilotos la omiten: opción |
| Encadenadas | "izquierda tres y derecha cuatro" | "y" une sin distancia |
| Distancias | "cincuenta", "cien", "doscientos" tras la nota | Redondeo a 10; unidad implícita |
| Precaución | "cuidado", "no cortes", "cuidado bache" | Vocabulario real por definir |
| Rasantes | "rasante", "cambio de rasante", "salto" | "salto" solo con cresta muy marcada |
| Bache/badén | "bache", "badén" | Desde OSM |
| Estrechamiento | "estrecha" | |
| Elementos | "puente", "túnel", "cruce", "rotonda", "paso a nivel" | |
Pendiente de §5.2: si dicen "izquierda" o "izda.", "recto" vs "a fondo", cómo expresan grados 5–6, si cantan distancias antes o después, uso de "contra", "corta", "abierta", y la cadencia entre notas.

## Consecuencias
- Positivas: determinista y testeable con fixtures de Mallorca; todo offline; calibrable sin recompilar; reutilizable para el modo reconocimiento.
- Negativas / costes: μ, umbrales y T_nivel son suposiciones; la geometría OSM es ruidosa (hay que suavizar y validar); MDT05 no ve badenes; la cobertura de `maxspeed`/`width` en OSM es parcial (en Ma-2141: `maxspeed` en 8 de 23 vías, `width` en 0).
- Seguimiento: validar con GPX reales de Ma-10 (Sóller–Lluc–Pollença) y Ma-2141 más una pasada con audio propia (precisión/recall de curvas y matriz de confusión de grados con tolerancia ±1; objetivos a fijar); comparar grados con las notas de Moya de los vídeos (§5.2) por distancia recorrida; medir el tiempo de cálculo en móvil medio; VERIFICAR μ y tabla con el usuario en carretera.

## ¿Afecta al producto?
Sí → QUESTIONS.md: § Escala y vocabulario (¿cantar grados 5–6 o omitirlos?, ¿"horquilla" sin grado?), § Estilo de distancias, § Avisos de precaución automáticos (¿cuántos?), § Referencia de calibración (¿límite de la vía o velocidad del piloto?).

## Fuentes
- https://github.com/adamfranco/curvature (README, 2026-10-28) y https://api.openstreetmap.org/user/Adam%20Franco/diary/394374 (vía búsqueda, 2026-10-28)
- https://www.overtake.gg/threads/rbr-automatic-generation-of-co-drivers-pacenotes.6979/ (solo extracto de búsqueda; foro, no verificado; 403 al abrir)
- https://bjrbe-journals.rtu.lv/article/view/bjrbe.2011.21 (Lipar et al., extracto de búsqueda, 2026-10-28)
- https://en.wikipedia.org/wiki/Banked_turn y https://en.wikipedia.org/wiki/Pacenotes (2026-10-28; sistemas 1–5 y 6–1, "hairpin", "square", "don't cut")
- https://wiki.openstreetmap.org/wiki/Key:hazard y https://wiki.openstreetmap.org/wiki/Key:traffic_calming (2026-10-28)
- https://inspire-geoportal.ec.europa.eu/srv/api/records/spaignMDT05 (licencia CC BY 4.0 SCNE, 2026-10-28)
- Consulta propia a Overpass API (ref=Ma-2141) y prototipo local, 2026-10-28
