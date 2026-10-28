"""Genera local.properties de Rally Copilot sin imprimir valores.

- sdk.dir: %LOCALAPPDATA%/Android/Sdk (o ANDROID_HOME).
Uso: python -I scripts/preparar-local-properties.py
"""

import os
import pathlib

RAIZ = pathlib.Path(__file__).resolve().parent.parent
DESTINO = RAIZ / "local.properties"


def leer(path: pathlib.Path) -> dict:
    datos = {}
    if path.is_file():
        for linea in path.read_text(encoding="utf-8").splitlines():
            if "=" in linea and not linea.lstrip().startswith("#"):
                k, v = linea.split("=", 1)
                datos[k.strip()] = v.strip()
    return datos


def main() -> None:
    actual = leer(DESTINO)
    sdk = os.environ.get("ANDROID_HOME") or str(pathlib.Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk")
    # Formato de Android Studio (lint PropertyEscape): C\:\\Users\\...
    actual["sdk.dir"] = sdk.replace("/", "\\").replace("\\", "\\\\").replace(":", "\\:")
    lineas = ["# Generado por scripts/preparar-local-properties.py. No se versiona (secretos)."]
    lineas += [f"{k}={v}" for k, v in actual.items()]
    DESTINO.write_text("\n".join(lineas) + "\n", encoding="utf-8", newline="\n")
    print("local.properties: sdk.dir", "ok" if actual.get("sdk.dir") else "vacía")


if __name__ == "__main__":
    main()
