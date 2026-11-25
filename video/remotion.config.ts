import { Config } from "@remotion/cli/config";

Config.setVideoImageFormat("jpeg");
Config.setOverwriteOutput(true);
// BT.709 con rango limitado: sale yuv420p estándar (no yuvj420p) y los colores se ven igual en YouTube y en cualquier
// reproductor.
Config.setColorSpace("bt709");
