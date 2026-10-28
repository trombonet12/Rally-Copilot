"""Crea la keystore de release de Rally Copilot en ~/.rallycopilot/ (una sola vez) sin imprimir secretos.

Genera contraseñas aleatorias, llama a keytool y escribe ~/.rallycopilot/release-signing.properties con
RALLY_KEYSTORE_FILE, RALLY_KEYSTORE_PASSWORD, RALLY_KEY_ALIAS y RALLY_KEY_PASSWORD.
Si la keystore ya existe, no hace nada: perderla impide actualizar la app instalada.
Uso: python -I scripts/crear-keystore.py
"""

import os
import pathlib
import secrets
import shutil
import subprocess
import sys

DIR = pathlib.Path.home() / ".rallycopilot"
KEYSTORE = DIR / "rallycopilot-release.jks"
PROPS = DIR / "release-signing.properties"
ALIAS = "rallycopilot"


def jdks() -> list[pathlib.Path]:
    """JAVA_HOME y los JDK 21 instalados en las rutas habituales de Windows (Microsoft, Temurin, Oracle)."""
    candidatos = [pathlib.Path(os.environ["JAVA_HOME"])] if os.environ.get("JAVA_HOME") else []
    for raiz in ("C:/Program Files/Microsoft", "C:/Program Files/Eclipse Adoptium", "C:/Program Files/Java"):
        candidatos += sorted(pathlib.Path(raiz).glob("jdk-21*"), reverse=True) if pathlib.Path(raiz).is_dir() else []
    return candidatos


def keytool() -> str:
    for base in jdks():
        for nombre in ("keytool.exe", "keytool"):
            if (base / "bin" / nombre).is_file():
                return str(base / "bin" / nombre)
    encontrado = shutil.which("keytool")
    if not encontrado:
        sys.exit("No encuentro keytool (JDK).")
    return encontrado


def main() -> None:
    if KEYSTORE.exists():
        print(f"Ya existe {KEYSTORE.name}: no se toca.")
        return
    DIR.mkdir(parents=True, exist_ok=True)
    clave = secrets.token_urlsafe(24)
    subprocess.run(
        [
            keytool(), "-genkeypair", "-v", "-keystore", str(KEYSTORE), "-storetype", "PKCS12",
            "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
            "-dname", "CN=Rally Copilot, O=Joan Lopez Ferrer, C=ES",
            "-storepass:env", "SC_STOREPASS", "-keypass:env", "SC_STOREPASS",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        env={**os.environ, "SC_STOREPASS": clave},
    )
    # PKCS12: la clave de la entrada es la misma que la del almacén.
    PROPS.write_text(
        "\n".join(
            [
                f"RALLY_KEYSTORE_FILE={KEYSTORE.as_posix()}",
                f"RALLY_KEYSTORE_PASSWORD={clave}",
                f"RALLY_KEY_ALIAS={ALIAS}",
                f"RALLY_KEY_PASSWORD={clave}",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Creada {KEYSTORE.name} y {PROPS.name} en ~/.rallycopilot (valores no mostrados). Haz copia de seguridad.")


if __name__ == "__main__":
    main()
