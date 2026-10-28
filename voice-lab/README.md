# voice-lab

Taller de voz en el PC (Windows 11, RTX 4080 SUPER). Aquí se prepara la **voz de Luis Moya** (con su autorización) y
se saca de material real el vocabulario de copiloto. El pipeline completo se monta en el hito **H11**
([ROADMAP.md](../ROADMAP.md)) y se documenta en `docs/VOZ.md` para poder repetirlo. Ver también
[docs/investigacion/voz-tts-y-clonado.md](../docs/investigacion/voz-tts-y-clonado.md).

## Dónde dejar el material (ya puedes ir haciéndolo)

```
voice-lab/data/raw/moya/      ← audios y vídeos de Luis Moya (entrevistas, onboards, notas leídas…)
voice-lab/data/raw/tramos/    ← audios y vídeos de tramos reales con copiloto en español (onboards)
voice-lab/data/raw/notas.md   ← (opcional) de dónde sale cada archivo, fecha, coche/piloto, permisos
```

- Formatos aceptados: cualquier audio o vídeo que lea ffmpeg (`.mp4`, `.mkv`, `.webm`, `.mov`, `.wav`, `.mp3`,
  `.m4a`, `.flac`…). No hace falta convertir ni recortar nada.
- Nombres libres, mejor sin espacios raros. Puedes crear subcarpetas (por rally, por año…).
- **Nada de `voice-lab/data/` se sube a git** (está en `.gitignore`): son archivos grandes y con derechos de terceros.
- Lo más valioso para clonar la voz: **voz de Moya sola y limpia** (sin música ni motor), en total al menos
  20–30 minutos; idealmente más de 1 hora. Los onboards con motor sirven sobre todo para el vocabulario.
- Para el vocabulario sirve cualquier onboard en español donde se oiga bien al copiloto, aunque no sea Moya.

## Qué hará el pipeline (H11)

1. **Separar voz** del ruido de motor y música en los vídeos onboard.
2. **Transcribir** en local con Whisper (sin subir nada a internet).
3. **Extraer vocabulario** y expresiones de copiloto → refinar el glosario de notas (`docs/investigacion/notas-de-rally.md`).
4. **Entrenar o clonar** la voz con la GPU, en un formato que el móvil pueda ejecutar sin conexión.
5. **Pregenerar** los audios de las notas y frases más comunes (números, direcciones, modificadores, distancias,
   alertas) para empaquetarlos en la app.

Salidas (tampoco van a git): `voice-lab/out/` (transcripciones, modelos, audios pregenerados). Lo que acabe dentro
del APK se copia a la app con un script cuando toque.
