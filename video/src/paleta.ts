import type { Tono } from "./guion";

/** Colores de la marca: ui/theme/Color.kt y Wrc.kt (carbón de retransmisión, rojo de las gafas, latón del trombón). */
export const CARBON = "#0E0F12";
export const CARBON_PROFUNDO = "#08090B";
export const CARBON_SUPERFICIE = "#1A1C20";
export const CARBON_ELEVADO = "#23262B";
export const TEXTO = "#ECEDEF";
export const TEXTO_SUAVE = "#BFC3C9";
export const ROJO = "#E02A32";
export const LATON = "#E5AB46";
export const DELTA_MEJOR = "#3DDC84";
export const DELTA_PEOR = "#FF6B5E";

export const ACENTO: Record<Tono, string> = {
  ROJO,
  LATON,
  VERDE: DELTA_MEJOR,
};

export const FUENTE = '"Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
