<div align="center">
  <img src="docs/assets/logo.svg" width="120" alt="Rally Copilot">
  <h1>Rally Copilot</h1>
  <p><strong>Tu copiloto en cada curva.</strong> Navegación con notas de rally por voz para las carreteras de Mallorca.</p>
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Licencia" src="https://img.shields.io/badge/licencia-GPL--3.0-E5AB46">
  <img alt="Estado" src="https://img.shields.io/badge/estado-H0%20preparaci%C3%B3n-D8232A">
</div>

## ✨ Qué hará

| | |
|---|---|
| 🗣️ **Copiloto por voz** | Lee la carretera con notas de rally («derecha cuatro larga, cincuenta, izquierda dos cierra») generadas a partir de la geometría y la elevación. |
| 🗺️ **Mapas offline** | Vectorial (OpenStreetMap) y satélite (PNOA del IGN) de toda Mallorca, sin conexión. |
| 🧭 **Navegación giro a giro** | Rutas y recálculo en el propio móvil, también con la pantalla apagada. |
| ⏱️ **Tramos cronometrados** | Sectores, microsectores, delta en vivo contra tu mejor tiempo y análisis posterior. |
| 🚨 **Alertas** | Radares e incidencias de la DGT. |

Todo funciona **sin conexión y sin cuentas**: los datos se quedan en el móvil. El plan completo está en
[ROADMAP.md](ROADMAP.md).

## 📲 Instalación
Con [Obtainium](https://github.com/ImranR98/Obtainium): *Add app* →
`https://github.com/trombonet12/Rally-Copilot` (filtro de APK `rally-copilot-.*\.apk`). Detalles en
[docs/obtainium.md](docs/obtainium.md).

## 🛠️ Desarrollo
- Kotlin, Jetpack Compose (Material 3), Hilt, Navigation type-safe. Módulo `:motor` en Kotlin/JVM puro para la
  lógica crítica (notas, cronometraje, geometría).
- Requisitos: JDK 21, Android SDK 37, Node ≥ 22, Python 3.12.

```
python -I scripts/preparar-local-properties.py
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :motor:test assembleDebug :app:minifyReleaseWithR8
node scripts/gps-replay.mjs docs/rutas/ma-2141-sa-calobra.gpx --kmh 50   # GPS simulado en el emulador
```

| Documento | Para qué |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Reglas de trabajo con Claude Code |
| [docs/DECISIONES.md](docs/DECISIONES.md) | Decisiones técnicas y versiones |
| [docs/HERENCIA.md](docs/HERENCIA.md) | Qué se hereda de Sous Chef y Trombito |
| [QUESTIONS.md](QUESTIONS.md) | Preguntas de producto y sus respuestas |
| [docs/investigacion/](docs/investigacion/) | Investigaciones previas (enrutado, mapas, notas, DGT, voz) |

## 📜 Licencia y atribuciones
Código bajo [GPL-3.0](LICENSE). Datos de mapas © colaboradores de [OpenStreetMap](https://www.openstreetmap.org/copyright)
(ODbL); ortofotos PNOA y MDT © [Instituto Geográfico Nacional](https://www.ign.es) (CC BY 4.0); incidencias y radares:
[DGT](https://nap.dgt.es).

<sub>Desarrollado por Trombonet12© and Co. Interactive Systems Solutions · https://www.trombonet12.es/</sub>
