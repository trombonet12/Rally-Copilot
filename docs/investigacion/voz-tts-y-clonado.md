# Voz: TTS en el dispositivo, clonado de Luis Moya y pipeline de audio

> Propósito: elegir motor TTS on-device y la ruta de clonado/pregeneración de la voz de Moya · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Voz, docs/VOZ.md (futuro), ROADMAP.md § H4, H11

## Contexto
- Prompt §5: primero voz sintética masculina offline (50–150 MB por voz aceptable); después voz clonada de Moya (con autorización), entrenada en PC con RTX 4080 SUPER 16 GB y Python 3.12; todo dentro de la app, sin internet. Solo español.
- Las notas son un **vocabulario cerrado** (dirección × grado × modificador + distancias + avisos: del orden de varios cientos de frases). Se pueden **pregenerar todas** y reproducir clips; el motor TTS queda para frases imprevistas. Esto reduce la latencia a ~0 y relaja la elección de motor.
- Proyecto hermano Trombito ya tiene: sherpa-onnx (AAR 1.13.8 en su ADR 0023; la última release de GitHub hoy es v2.0.1, 2026-10-26), `tools/voice-train` (Docker, piper1-gpl con `warmstart` desde `es_ES davefx medium`, faster-whisper para filtrar tomas) y mediciones propias en RTX 4080 SUPER: 3,3 s/época con 8,4 min de audio, pipeline de prueba 5 min 28 s, `voice.zip` 68 MB. Extrapolación suya: 30 min de audio ≈ 2,6 h. Sus objetivos de grabación: mínimo 5 min, recomendado 30, ideal 60 (objetivos, no garantía).
- Ducking: con `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` el sistema atenúa automáticamente a las demás apps desde Android 8 (API 26 = nuestro minSdk); no se atenúa si la otra app emite voz. La doc de Android cita las indicaciones de conducción como caso típico.

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| [A] sherpa-onnx + Piper/VITS `es_ES` (davefx medium, sharvard medium M=speaker 0, carlfm x_low, miro high) | Existe como paquete oficial; int8 ≈21 MB, fp16 ≈36 MB, fp32 ≈67 MB (davefx); mismo formato que el modelo clonado; ya integrado en Trombito | Calidad "medium", prosodia plana; necesita espeak-ng-data (GPL-3, ver riesgo); género de davefx/carlfm/miro no figura en las fichas → escuchar (VERIFICAR) | Motor Apache-2.0; datos: davefx CC0, sharvard CC BY 3.0, carlfm dominio público; espeak-ng GPL-3.0 | Assets `vits-piper-es_ES-*` en release `tts-models` (medido 2026-10-28) |
| [A] sherpa-onnx + Supertonic 3 int8 (es, ~145 MB, 10 voces; M1 existe) | Más natural; sin espeak; rápido en CPU; Trombito ya lo integra | Pesos OpenRAIL-M (restricciones de uso que se heredan a la salida), desarrollo parado según el ADR de Trombito; más pesado | Código MIT, pesos OpenRAIL-M | Paquete `sherpa-onnx-supertonic-3-tts-int8-2026-05-11` (128,8 MB) |
| [A] sherpa-onnx + Kokoro | Buena calidad en inglés | Sin voces españolas confirmadas en los paquetes de sherpa (listados zh+en); Trombito midió 2–3 min de cálculo por minuto de audio en móvil modesto | Apache-2.0 (VERIFICAR pesos) | Descartar |
| [A] Matcha / MMS-spa / Coqui-css10 | MMS-spa (107 MB) y css10 (67 MB) existen en sherpa | Matcha sin español; MMS suele ser no comercial (VERIFICAR); css10 voz y licencia por confirmar | VERIFICAR | Baja prioridad |
| [A] Piper Android directo (sin sherpa) | — | Piper original MIT archivado; piper1-gpl es GPL-3.0; sin ventaja frente a sherpa | MIT (archivado) / GPL-3.0 | No recomendada |
| [A] TTS del sistema (Android) | 0 MB | Depende del dispositivo/GMS; voz no controlable | — | Solo fallback |
| [B] Fine-tune Piper/VITS (piper1-gpl, `warmstart`) | Resultado ejecutable en sherpa; sin dependencias nuevas en el móvil; Trombito ya lo tiene dockerizado y medido | Calidad limitada por audio limpio disponible; necesita datos limpios (ruido de motor no vale) | Entrenador GPL-3.0 (solo herramienta en PC); datos de Moya: contrato | Funciona hoy en tu PC (medido en Trombito) |
| [B] XTTS v2 (clonado zero-shot, ~6 s de referencia) | Calidad alta, español soportado | **CPML (Coqui, no comercial)** y empresa sin mantenimiento (VERIFICAR) → no para app publicada | CPML | Evitar para producto |
| [B] F5-TTS | Zero-shot con buena calidad | Ficha HF: CC-BY-NC-4.0; idioma español no confirmado en la ficha | CC-BY-NC-4.0 | Solo experimentos |
| [B] OpenVoice V2 | MIT, uso comercial permitido; español nativo; conversión de timbre | Parecido de timbre inferior al fine-tune (VERIFICAR); más piezas | MIT | Candidato a prototipo |
| [B] Chatterbox (multilingüe v3 + pack `es-es`) | MIT, español de España, ref ~10 s; watermark Perth en salida | 500 M parámetros; solo PC; watermark inaudible pero presente | MIT | Candidato para pregenerar |
| [B] CosyVoice 3 (Fun-CosyVoice3-0.5B-2512) | Apache-2.0; cloning multilingüe con español | Requisitos de VRAM no documentados; instalación pesada | Apache-2.0 | Candidato |
| [B] Fish Speech / S2-Pro | Cloning con 10–30 s | Licencia "Community"/"Research": uso comercial no aclarado; español no confirmado | Fish Audio Community/Research | Evitar sin revisar |
| [C] Transcripción: faster-whisper `large-v3` (CTranslate2, CUDA 12 + cuDNN 9) | MIT; `word_timestamps`, VAD Silero; cabe de sobra en 16 GB | WER sube con ruido de motor → separar antes | MIT | Dockerizable como en Trombito (faster-whisper 1.2.1 pinneado allí) |
| [C] Separación: audio-separator (MDX-Net/Roformer/Demucs, MIT) o Demucs `htdemucs_ft` | Vocals vs. ruido; Demucs ≥3 GB VRAM (≈7 GB por defecto) | Demucs archivado (fork adefossez); el intercom del copiloto puede dejar residuo de motor | MIT | Comparar ambos con 5 clips reales |
| [C] Diarización: pyannote `community-1` | Aísla a Moya del piloto | Exige aceptar condiciones y token de Hugging Face (descarga inicial) | MIT (código); modelo con condiciones | Opcional si el vídeo mezcla voces |

## Recomendación
1. **Hito H4 (voz sintética)**: sherpa-onnx con dos voces masculinas `es_ES` (davefx medium y sharvard speaker 0; fp16 ≈36 MB / int8 ≈21 MB tras probar calidad) y, si hay presupuesto, Supertonic 3 como voz "premium". Reproducir **clips pregenerados** para notas comunes y TTS solo para texto libre. Foco de audio: `AudioFocusRequest` con `GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, `CONTENT_TYPE_SPEECH` (esta combinación es mi propuesta: la doc consultada no trata ese `usage`; VERIFICAR en dispositivo), y abandonar el foco al terminar cada frase.
2. **Hito H11 (Moya)**: fine-tune Piper con el entrenador de Trombito (copiar `tools/voice-train` a `voice-lab/`), no zero-shot. Mejor material = **sesión de lectura de las ~300 frases del glosario** grabada por Moya (autorización escrita); el audio de vídeos onboard sirve para extraer vocabulario y, si se limpia bien, como complemento. Zero-shot (OpenVoice o Chatterbox, ambos MIT) solo como plan B para pregenerar clips mientras no haya datos.
3. **Pipeline de §5.2** (todo local, en Docker con GPU, `voice-lab/in/` solo lectura): (a) `ffmpeg` extrae audio mono; (b) separación de voz (audio-separator/Demucs) y, si hay dos voces, diarización; (c) faster-whisper `large-v3` es, `word_timestamps`, `vad_filter`, `initial_prompt` con vocabulario de rally; (d) informe de frecuencias de n-gramas → refinar glosario (notas-de-rally.md); (e) segmentar clips de 2–12 s por timestamps, filtrar por CER (como Trombito) y SNR; (f) fine-tune (`warmstart` desde `es_ES davefx medium`); (g) pregenerar todas las frases del glosario con sherpa-onnx en el PC → WAV 22,05 kHz → Opus mono ~24 kbps con manifest `texto→archivo` (un clip de 1,5 s ≈ 4–5 kB, ≈2–3 MB por voz con ~500 clips; estimación mía); (h) empaquetar.
4. **En el APK/AAB**: clips Opus por voz + `manifest.json` + `voice.zip` del modelo (68 MB fp32; evaluar int8/fp16) como asset pack de instalación (límites de Play: VERIFICAR) para cumplir "sin descargas".

## Consecuencias
- Positivas: latencia de síntesis nula en las frases del glosario (clips); el mismo formato de modelo (sherpa/Piper) sirve para voz sintética y clonada; flujo ya probado en Trombito; evita CPML y licencias NC.
- Negativas / costes: calidad de Piper "medium" menor que zero-shot grandes; se necesita audio limpio de Moya (el onboard con motor limita); cambiar el glosario obliga a regenerar clips (el script debe ser idempotente); tamaño APK crece 20–70 MB por voz.
- Riesgos: (1) **espeak-ng es GPL-3.0** y sherpa/Piper lo usan para fonemizar; apps como SherpaTTS se publican en GPLv3. Si Rally Copilot no es GPL, VERIFICAR con un jurista si basta con enlazar dinámicamente o hay que evitar espeak (Supertonic, solo clips). (2) Derechos de voz de Moya: contrato escrito que cubra síntesis, distribución en Play y duración. (3) Piper RTF en móvil medio no medido (VERIFICAR con `performance-review` en dispositivo).
- Seguimiento: prueba de concepto de separación sobre 5 clips; escucha A/B davefx vs. sharvard vs. Supertonic; confirmar género de cada voz; medir primer audio y RTF en el móvil real; comprobar licencia de uso del Kokoro/MMS si se retoman.

## ¿Afecta al producto?
Sí → QUESTIONS.md: § Voces iniciales (¿cuáles y cuántas?), § Autorización y contrato con Moya (¿hay sesión de estudio?), § Licencia de la app (¿GPL-3 aceptable por espeak-ng?), § Tamaño máximo del APK/AAB, § Voz sintética vs. solo clips para frases imprevistas.

## Fuentes
- https://github.com/k2-fsa/sherpa-onnx/releases (API `releases/latest` y `tts-models`, 2026-10-28) y https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html
- https://huggingface.co/rhasspy/piper-voices (voices.json y MODEL_CARD de es_ES/es_MX, 2026-10-28)
- https://huggingface.co/Supertone/supertonic-3 (2026-10-28)
- https://developer.android.com/media/optimize/audio-focus (2026-10-28)
- https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/TRAINING.md (2026-10-28)
- https://huggingface.co/coqui/XTTS-v2 · https://huggingface.co/SWivid/F5-TTS · https://github.com/myshell-ai/OpenVoice · https://github.com/resemble-ai/chatterbox · https://github.com/FunAudioLLM/CosyVoice · https://github.com/fishaudio/fish-speech (2026-10-28)
- https://github.com/SYSTRAN/faster-whisper · https://github.com/facebookresearch/demucs · https://github.com/nomadkaraoke/python-audio-separator · https://github.com/pyannote/pyannote-audio (2026-10-28)
- Local: C:\Github\APPS\trombito\tools\voice-train\README.md y docs\adr\0023-voz-de-trombito-tts-local.md (mediciones y decisiones del proyecto hermano)
- No verificada (búsqueda, sin fuente oficial): SherpaTTS en GPLv3 con datos eSpeak NG (resultado de búsqueda, 2026-10-28)
