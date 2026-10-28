"""Convierte la mascota SVG en los VectorDrawable de Android sin dependencias.

Uso:
  python -I scripts/svg-a-vector.py mascota   # docs/assets/mascota-rally.svg -> res/drawable/mascota_rally.xml
  python -I scripts/svg-a-vector.py launcher  # misma fuente -> res/drawable/ic_launcher_foreground.xml (zona segura)

Solo entiende lo que usa la mascota: <path> con d, fill, stroke, stroke-width, stroke-linecap/linejoin round y
stroke-opacity. ic_launcher_monochrome.xml e ic_stat_rally.xml se mantienen a mano (siluetas simplificadas).
"""

import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "docs" / "assets" / "mascota-rally.svg"
DRAWABLE = RAIZ / "app" / "src" / "main" / "res" / "drawable"

CABECERA_VECTOR = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
"""


def paths_android(svg: str) -> list[str]:
    salida = []
    for crudo in re.findall(r"<path ([^>]*)/>", svg):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', crudo))
        attrs = [f'android:pathData="{a["d"]}"']
        if a.get("fill", "") not in ("", "none"):
            attrs.append(f'android:fillColor="{a["fill"]}"')
        if "stroke" in a:
            attrs.append(f'android:strokeColor="{a["stroke"]}"')
            attrs.append(f'android:strokeWidth="{a["stroke-width"]}"')
            if a.get("stroke-linecap") == "round":
                attrs.append('android:strokeLineCap="round"')
            if a.get("stroke-linejoin") == "round":
                attrs.append('android:strokeLineJoin="round"')
            if "stroke-opacity" in a:
                attrs.append(f'android:strokeAlpha="{a["stroke-opacity"]}"')
        salida.append("<path " + " ".join(attrs) + " />")
    return salida


def main() -> None:
    modo = sys.argv[1] if len(sys.argv) > 1 else ""
    paths = paths_android(FUENTE.read_text(encoding="utf-8"))
    if modo == "mascota":
        destino = DRAWABLE / "mascota_rally.xml"
        texto = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Trombito con casco de rally, sin fondo, para la app. Generado por scripts/svg-a-vector.py desde\n"
            "     docs/assets/mascota-rally.svg (viewBox 108). No editar a mano. -->\n"
            + CABECERA_VECTOR
            + "".join(f"    {p}\n" for p in paths)
            + "</vector>\n"
        )
    elif modo == "launcher":
        destino = DRAWABLE / "ic_launcher_foreground.xml"
        texto = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Trombito con casco de rally. Generado por scripts/svg-a-vector.py desde docs/assets/mascota-rally.svg\n"
            "     (viewBox 108). Escalada al 75 % y bajada 8,4 dp para caer en la zona segura de 66 dp. No editar a mano. -->\n"
            + CABECERA_VECTOR
            + '    <group android:translateY="8.4">\n'
            + "        <group\n"
            + '            android:pivotX="54"\n'
            + '            android:pivotY="54"\n'
            + '            android:scaleX="0.75"\n'
            + '            android:scaleY="0.75">\n'
            + "".join(f"            {p}\n" for p in paths)
            + "        </group>\n    </group>\n</vector>\n"
        )
    else:
        sys.exit("Uso: python -I scripts/svg-a-vector.py <mascota|launcher>")
    destino.write_text(texto, encoding="utf-8", newline="\n")
    print(f"svg-a-vector: {len(paths)} paths -> {destino.relative_to(RAIZ).as_posix()}")


if __name__ == "__main__":
    main()
