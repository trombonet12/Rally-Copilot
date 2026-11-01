# sherpa-onnx en Android: artefacto, API Kotlin, voces Piper es_ES y generación de clips en CI

> Propósito: fijar versión, origen y hash del AAR de sherpa-onnx y de las voces Piper es_ES para integrar TTS offline en H4 · Audiencia: agente principal y usuario · Actualizado: 2026-11-01 · Relacionado: docs/DECISIONES.md § Voz, TTS y clonado, docs/investigacion/voz-tts-y-clonado.md, ROADMAP.md § H4

## Contexto
¿Cómo se incorpora sherpa-onnx (TTS Piper/VITS, español, masculino) a la app Android (minSdk 26, targetSdk 36, ver `app/build.gradle.kts`): coordenadas o AAR, versión estable con ≥ 2 semanas, hashes, tamaños, licencias, API Kotlin, voces davefx/sharvard y generación de clips en GitHub Actions?

## Hechos con fuente

### 1. Artefacto Android
- **Maven Central NO confirmado.** `https://repo1.maven.org/maven2/com/k2fsa/` y `.../com/k2fsa/sherpa/onnx/sherpa-onnx-android/` devuelven 404 (consultado 2026-11-01). Una búsqueda web devolvió una ficha `central.sonatype.com/artifact/com.k2fsa.sherpa.onnx/sherpa-onnx-android`, pero la página carga sin versiones (`Loading...`) y no se pudo comprobar su contenido ni su publicador. Tratarla como inexistente hasta verla en el navegador: `VERIFICAR`.
- **Distribución oficial = AAR adjunto a la release de GitHub.** `android/SherpaOnnxAar/README.md` (k2-fsa/sherpa-onnx) describe cómo se construye `sherpa-onnx-<ver>.aar` y no menciona Maven. Las instrucciones oficiales de Android (https://k2-fsa.github.io/sherpa/onnx/android/index.html) tratan APKs y compilación, no dependencias Gradle.
- **JitPack (vía del propio repo):** `jitpack.yml` (tag v1.13.8) descarga `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar` y lo instala con `-DgroupId=com.github.k2-fsa -DartifactId=sherpa-onnx -Dversion=1.13.8 -Dpackaging=aar`. La API de JitPack (https://jitpack.io/api/builds/com.github.k2-fsa/sherpa-onnx) marca ok los tags `v1.13.8`, `v2.0.0` y `v2.0.1`. Coordenadas implícitas: repositorio `https://jitpack.io`, `com.github.k2-fsa:sherpa-onnx:v1.13.8` (no probado en un build; `VERIFICAR`).
- **Releases recientes** (https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/<tag>; ninguna es prerelease): v2.0.1 = 2026-10-26; v2.0.0 = 2026-10-23; **v1.13.8 = 2026-09-10** (la anterior a la serie 2.x; tags previos: v1.13.7…v1.13.0). Hoy es 2026-11-01: 2.0.1 tiene 6 días y 2.0.0 tiene 9 → no cumplen «≥ 2 semanas»; v1.13.8 tiene 52 días. Trombito usa 1.13.8 (docs/investigacion/voz-tts-y-clonado.md).
- **AAR v1.13.8** (campos `size`/`digest` del API de GitHub, copiados dos veces con el mismo resultado):
  - URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar`
  - Tamaño 50 129 134 B (47,8 MiB) · SHA-256 `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`
  - Variante `sherpa-onnx-static-link-onnxruntime-1.13.8.aar`: 38 691 998 B · sha256 `b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471` (onnxruntime enlazado estáticamente; no evaluada).
- Referencia (AAR v2.0.1, no recomendado aún): 49 441 341 B · `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`; v2.0.0: 49 236 482 B · `f0f8716982d27cb4ce695e3934018b1342d9d3d6fdb6ef1f1658fe8bad50b4fc`.
- **ABIs del AAR:** el workflow `android-fdroid-reproducible-aar.yaml` y el README de `SherpaOnnxAar` copian `.so` de **arm64-v8a, armeabi-v7a, x86 y x86_64** a `jniLibs/`. El módulo declara `namespace com.k2fsa.sherpa.onnx`, **minSdk 21**, compileSdk 34 (`android/SherpaOnnxAar/sherpa_onnx/build.gradle.kts`, tag v1.13.8) → compatible con nuestro minSdk 26.
- **Tamaño por ABI: no publicado.** Solo se conoce el total del AAR (4 ABIs, 50,1 MB comprimido). Media aritmética ≈ 12 MB por ABI (cálculo mío, no medido). Los tarballs `*-android.tar.bz2` de v2.0.1 por ABI (termux-shared: aarch64 16,5 MB, armv7 16,1 MB, x86_64 18,1 MB, x86 18,0 MB) son otro empaquetado. Medir descomprimiendo el AAR: `VERIFICAR`.
- **Licencias:** sherpa-onnx = Apache-2.0 (https://github.com/k2-fsa/sherpa-onnx, barra lateral «Apache-2.0 license»; PyPI: «Apache licensed»). onnxruntime = MIT (https://github.com/microsoft/onnxruntime/blob/main/LICENSE). **espeak-ng = GPL-3.0 o posterior** (https://github.com/espeak-ng/espeak-ng, README «License Information»). `cmake/espeak-ng-for-piper.cmake` (v1.13.8) descarga el fork `csukuangfj/espeak-ng` y lo compila como biblioteca estática (`POSITION_INDEPENDENT_CODE`) para enlazarla dentro de la biblioteca compartida → el JNI de sherpa-onnx incorpora código GPL-3.0 (inferencia desde el cmake; confirmar con `strings`/`VERIFICAR`). La app ya es GPL-3.0 (docs/MEMORIA.md), por lo que es compatible.

### 2. API Kotlin (tag v1.13.8, `sherpa-onnx/kotlin-api/Tts.kt`)
- Paquete `com.k2fsa.sherpa.onnx`; carga `System.loadLibrary("sherpa-onnx-jni")`.
- Config: `OfflineTtsConfig(model: OfflineTtsModelConfig, ruleFsts = "", ruleFars = "", maxNumSentences = 1, silenceScale = 0.2f)`; `OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(), …, numThreads = 1, debug = false, provider = "cpu")`; `OfflineTtsVitsModelConfig(model = "", lexicon = "", tokens = "", dataDir = "", dictDir = "" /*unused*/, noiseScale = 0.667f, noiseScaleW = 0.8f, lengthScale = 1.0f)`.
- `class OfflineTts(assetManager: AssetManager? = null, var config: OfflineTtsConfig)`; `generate(text: String, sid: Int = 0, speed: Float = 1.0f): GeneratedAudio`; `generateWithCallback(text, sid, speed, callback: (FloatArray) -> Int)`; `sampleRate()`, `numSpeakers()`, `free()` (y `release()`).
- `class GeneratedAudio(val samples: FloatArray, val sampleRate: Int)` (muestras PCM float; con las voces Piper medium `sampleRate` = 22050).
- **Carga de modelos:** con `assetManager` no nulo, `model` y `tokens` se leen como rutas relativas a `assets/`; con `assetManager = null`, como rutas del sistema de archivos. **`dataDir` (espeak-ng-data) se pasa como ruta de archivo real:** la app de ejemplo del motor TTS (`android/SherpaOnnxTtsEngine/.../TtsEngine.kt`, v1.13.8) copia solo `espeak-ng-data` de assets a `getExternalFilesDir` y pasa esa ruta, mientras el `.onnx` y `tokens.txt` se leen por AssetManager. En la app Rally habrá que copiar a `filesDir` en el primer arranque (y versionar la copia): diseño propio, no verificado en dispositivo.
- Ejemplo de Piper en esa clase (comentado): `modelDir = "vits-piper-en_US-amy-low"`, `modelName = "<voz>.onnx"`, `dataDir = "<modelDir>/espeak-ng-data"`.

### 3. Voces Piper es_ES en formato sherpa-onnx
Release `tts-models` (https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models; API `releases/tags/tts-models`, assets actualizados 2025-12-05). URL base: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/<archivo>`. Digest del campo `digest` del API.

| Archivo | Tamaño (B) | SHA-256 |
|---|---|---|
| vits-piper-es_ES-davefx-medium.tar.bz2 (fp32) | 67 184 952 | a3f6beb54a9cb893279f72978a22f807a4d9fc9c7848157b524d5cc7b7f58b22 |
| vits-piper-es_ES-davefx-medium-int8.tar.bz2 | 21 171 632 | 8bb8ac1cefb727caec9bd9c6c3185c673c8b42c53bd29bb25d5a7715dac37125 |
| vits-piper-es_ES-davefx-medium-fp16.tar.bz2 | 35 898 551 | 604087d0701eaff11319882ad2b1fac78db3d91e662f20b3e3be729fe1934d2b |
| vits-piper-es_ES-sharvard-medium.tar.bz2 (fp32) | 80 318 184 | b30a7a83df0518f0ee1c7039506648cade99f1f9b498fc49ed2ced2e2536bb5a |
| vits-piper-es_ES-sharvard-medium-int8.tar.bz2 | 23 477 120 | bf5507cbdedea650365bd6175b3164a8ecca5847533ddf97afcba9d76ea599d1 |
| vits-piper-es_ES-sharvard-medium-fp16.tar.bz2 | 41 936 560 | 72bca80bba7ab78e3a49d97393840e2452f2b7c42370e9f1d5b0762b70b911ee |

(Los hashes int8/fp16 de davefx y sharvard se copiaron de dos lecturas distintas del API; los fp16 solo de una: `VERIFICAR` con `sha256sum` al descargar.)
- Tamaños de los ficheros sueltos en Hugging Face (https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium y `-sharvard-medium`, árbol `main`): davefx `.onnx` 63,2 MB (repo 81,2 MB); sharvard `.onnx` 76,7 MB (repo 94,7 MB). Resto = `espeak-ng-data/` ≈ 18 MB sin comprimir (diferencia mía; el tarball independiente `espeak-ng-data.tar.bz2` de la misma release pesa 7 252 012 B comprimido). Contenido: `.onnx`, `.onnx.json`, `tokens.txt`, `MODEL_CARD`, `espeak-ng-data/`.
- **davefx-medium:** 1 hablante, 22 050 Hz, calidad medium; dataset CC0 (NabuCasa/OHF-Voice voice-datasets); finetune de `lessac` medium (MODEL_CARD, https://huggingface.co/rhasspy/piper-voices/raw/main/es/es_ES/davefx/medium/MODEL_CARD). Género no indicado en la ficha: `VERIFICAR` escuchando.
- **sharvard-medium:** 2 hablantes, 22 050 Hz; **`speaker_id_map: {"M": 0, "F": 1}` → masculino = `sid 0`** (`es_ES-sharvard-medium.onnx.json`); dataset Universidad de Edimburgo DataShare (handle 10283/574) bajo **CC BY 3.0** (requiere atribución); finetune de `lessac` medium (MODEL_CARD). Parámetros por defecto del json: noise_scale 0.667, length_scale 1, noise_w 0.8, espeak voice `es`.
- Licencia de las voces: los pesos derivan de un finetune de lessac (licencia del dataset lessac no comprobada aquí) → `VERIFICAR` antes de redistribuir.
- Hay más voces es_ES en la release (carlfm x_low 26,5 MB fp32/13,4 int8; miro high 67,2/21,3 MB; glados-medium) y un modelo `vits-mimic3-es_ES-m-ailabs_low` (80 MB). No evaluadas.

### 4. Rendimiento
- Solo hay datos de sherpa-onnx en **Raspberry Pi 4 B** (https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html y `rtf.html`), con voces Piper en inglés: RTF `en_US-lessac-medium` 0,774 (1 hilo), 0,482 (2), 0,390 (3), 0,357 (4); `glados` 0,812/0,480/0,391/0,349. Son datos de otro hardware e idioma; no hay RTF publicado en móvil de gama media (`VERIFICAR` midiendo en el AVD y en el móvil real con `performance-review`). Con RTF < 1 la síntesis es más rápida que el tiempo real.

### 5. Generación de clips en GitHub Actions (Linux)
- **Pip `sherpa-onnx`** (https://pypi.org/pypi/sherpa-onnx/json): última 2.0.1; la 1.13.8 existe en PyPI (subida 2026-09-10) con wheels `manylinux2014_x86_64` para cp310-cp313, p. ej. `sherpa_onnx-1.13.8-cp312-cp312-manylinux2014_x86_64.manylinux_2_17_x86_64.whl`, 4 400 575 B, sha256 `6949773017647febc0c3696dffb2c67dd3febd4737b87e3dd135a42773704a06`. Licencia Apache. Comando: `pip install sherpa-onnx==1.13.8 soundfile` (instalación oficial: `pip install sherpa-onnx sherpa-onnx-bin`, https://k2-fsa.github.io/sherpa/onnx/python/install.html). Mismas versión y motor que el AAR → mismas salidas que en el móvil.
- **Código Python** (`python-api-examples/offline-tts.py`, v1.13.8): `OfflineTtsVitsModelConfig(model=, tokens=, data_dir=, lexicon="")` → `OfflineTtsConfig(model=OfflineTtsModelConfig(vits=…, provider="cpu", num_threads=1), max_num_sentences=1)` → `tts.generate(text, GenerationConfig(sid=, speed=))` → `audio.samples`, `audio.sample_rate` → `soundfile.write(…, subtype="PCM_16")`. CLI equivalente: `--vits-model --vits-tokens --vits-data-dir --sid --speed --output-filename`.
- **`piper-tts`** (PyPI): 1.8.0, subida 2026-09-04, licencia **GPL-3.0-or-later**, wheel `cp39-abi3-manylinux_2_28_x86_64` (https://pypi.org/pypi/piper-tts/json). Usa los `.onnx` originales de `rhasspy/piper-voices`, no el empaquetado sherpa; no aporta ventaja y su salida puede diferir ligeramente (fonemizador). No necesario.

## Alternativas
- **Origen del AAR:** Maven Central (no confirmado: 404) · JitPack `com.github.k2-fsa:sherpa-onnx:v1.13.8` (compila desde el código, no reproducible) · **AAR oficial de la release de GitHub bajado por Gradle con SHA-256 fijo** (elegida; sin 50 MB en git).
- **Voces:** fp32 (67/80 MB) · fp16 (36/42 MB) · **int8 (21/23 MB, elegida)**.
- **Clips:** generados en Actions con el pip `sherpa-onnx` · **sintetizados en el móvil por adelantado y cacheados** (elegida, ver DECISIONES 2026-11-01).

## Incertidumbres
- `VERIFICAR`: existencia de `com.k2fsa.sherpa.onnx:sherpa-onnx-android` en Maven Central (no hay metadata accesible; repo1 da 404) y que el build JitPack del tag v1.13.8 sirva el AAR.
- Comprobado (2026-11-01, descomprimido): el AAR trae `classes.jar` (API Kotlin) y por ABI arm64-v8a ≈ 31,9 MB sin comprimir (onnxruntime 22,2 MB), x86_64 ≈ 36,1 MB. SHA-256 del AAR y de las dos voces int8 verificados con `sha256sum`.
- `VERIFICAR`: cambios de API/ruptura entre 1.13.8 y la serie 2.x (v2.0.0 sale 2026-10-23); no se leyeron las notas.
- `VERIFICAR`: género de davefx; licencia de los pesos derivados de lessac; hashes fp16.
- RTF en móvil: sin datos publicados; solo Raspberry Pi 4 y voces inglesas.
- Los digests procedían del campo `digest` del API de GitHub leído a través de un resumidor automático; el AAR se leyó dos veces con el mismo resultado, el resto una o dos.

## Recomendación
**sherpa-onnx v1.13.8 (2026-09-10), AAR oficial descargado de la release de GitHub, verificado por SHA-256 y versionado como `app/libs/sherpa-onnx-1.13.8.aar`** (o descargado en Gradle con comprobación de hash), con `abiFilters` arm64-v8a + x86_64 (+ armeabi-v7a si se quiere cobertura 32 bits). Motivo: es el único canal oficial confirmado, cumple ≥ 2 semanas, es la versión que ya usa Trombito, es reproducible y evita depender de JitPack/Maven sin confirmar; sustituye esta versión por 2.x tras revisar sus notas. Voces: davefx-medium y sharvard-medium (sid 0 masculino) en variante int8 (21,2 y 23,5 MB comprimidos) o fp16 según prueba de escucha, más `espeak-ng-data` copiado a `filesDir`. Para clips en CI: `pip install sherpa-onnx==1.13.8 soundfile` con las mismas voces. Atribución CC BY 3.0 (sharvard) y GPL-3.0 (espeak-ng) en Ajustes > Acerca de.

## Consecuencias
- Positivas: mismo motor y versión en móvil y CI; AAR autocontenido (onnxruntime incluido); app ya GPL-3.0, sin conflicto con espeak-ng.
- Negativas / costes: +≈12 MB por ABI de bibliotecas nativas (estimación); copiar `espeak-ng-data` (≈18 MB sin comprimir) a almacenamiento; AAR fuera de Version Catalog salvo que se use JitPack (alta de dependencia vía skill dependency-add y decisión en DECISIONES.md).
- Seguimiento: sha256sum de lo descargado, medir RTF/tamaño por ABI, escuchar A/B davefx vs sharvard (sid 0), revisar notas de v2.x.

## ¿Afecta al producto?
No directamente (técnico). Tamaño final del APK depende de ABIs e int8/fp16: ya apuntado en QUESTIONS.md § Tamaño máximo del APK/AAB.

## Fuentes (consultadas 2026-11-01)
- https://github.com/k2-fsa/sherpa-onnx (licencia) · API `releases/tags/v1.13.8`, `v2.0.0`, `v2.0.1`, `tts-models` · `tags`
- https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v1.13.8/jitpack.yml · `.../master/jitpack.yml` · `.../v1.13.8/sherpa-onnx/kotlin-api/Tts.kt` · `.../v1.13.8/android/SherpaOnnxAar/sherpa_onnx/build.gradle.kts` · `.../v1.13.8/android/SherpaOnnxTtsEngine/app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/TtsEngine.kt` · `.../v1.13.8/cmake/espeak-ng-for-piper.cmake` · `.../v1.13.8/python-api-examples/offline-tts.py` · `.../master/.github/workflows/android-fdroid-reproducible-aar.yaml`
- https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxAar (README)
- https://jitpack.io/api/builds/com.github.k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/android/index.html · .../android/build-sherpa-onnx.html · .../tts/pretrained_models/vits.html · .../tts/pretrained_models/rtf.html · .../python/install.html
- https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium · .../vits-piper-es_ES-sharvard-medium (árbol, MODEL_CARD, `.onnx.json`) · https://huggingface.co/rhasspy/piper-voices (MODEL_CARD davefx y sharvard)
- https://pypi.org/pypi/sherpa-onnx/json · https://pypi.org/pypi/sherpa-onnx/1.13.8/json · https://pypi.org/pypi/piper-tts/json
- https://github.com/espeak-ng/espeak-ng · https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- https://repo1.maven.org/maven2/com/k2fsa/ (404) · https://central.sonatype.com/artifact/com.k2fsa.sherpa.onnx/sherpa-onnx-android (sin datos)
- No verificada: resultado de búsqueda sobre «push to maven center» en el changelog 1.12.9 del paquete npm.
