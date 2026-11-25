import React from "react";

export type TrombitoProps = {
  /** "casco" = mascota-rally.svg; "bandera" = poses/mascota-bandera.svg (dos banderas a cuadros). */
  pose?: "casco" | "bandera";
  /** 0 = ojos abiertos, 1 = cerrados (solo pose casco). */
  parpadeo?: number;
  /** 0..1: intensidad del estirado/aplastado del rebote (escala Y +10 %, X −6 %, origen en la base). */
  rebote?: number;
  /** Fase de ondeo de las banderas, en radianes (solo pose bandera): pásala como función del frame. */
  ondeo?: number;
  /** Ancho en px. */
  tamano?: number;
  /** Prefijo de los id internos (clipPath): cámbialo si hay dos Trombito a la vez. */
  idp?: string;
};

const R_OJO = 7.6;
const PIEL = "#F9D768";
const OJOS = [
  { cx: 60, cy: 50, k: "i" },
  { cx: 76, cy: 50, k: "d" },
];

const Parpado: React.FC<{ cx: number; cy: number; id: string; cierre: number }> = ({ cx, cy, id, cierre }) => {
  if (cierre <= 0.02) return null;
  const alto = 2 * R_OJO * cierre;
  const y = cy - R_OJO;
  return (
    <g>
      <clipPath id={id}>
        <circle cx={cx} cy={cy} r={R_OJO - 1.2} />
      </clipPath>
      <rect x={cx - R_OJO} y={y} width={2 * R_OJO} height={alto} fill={PIEL} clipPath={`url(#${id})`} />
      {cierre > 0.5 ? (
        <path
          d={`M${cx - 5.2},${y + alto - 0.6}Q${cx},${y + alto + 1.6} ${cx + 5.2},${y + alto - 0.6}`}
          fill="none"
          stroke="#6B3F08"
          strokeWidth={1.6}
          strokeLinecap="round"
        />
      ) : null}
    </g>
  );
};

/** Trombón, cabeza, casco y gafas: común a las dos poses (de mascota-rally.svg). */
const Cuerpo: React.FC = () => (
  <>
    <path d="M46,47H27A7,7 0 0 0 27,61H46" fill="none" stroke="#B97A0C" strokeWidth="5.4" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M46,47H27A7,7 0 0 0 27,61H46" fill="none" stroke="#F5C033" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M42,44V64" fill="none" stroke="#B97A0C" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M30,48C46,48 54,36 66,26.5L66,81.5C54,72 46,60 30,60Z" fill="#E8A92A" />
    <path d="M49,54a19,28 0 1,0 38,0a19,28 0 1,0 -38,0Z" fill="#C98A12" />
    <path d="M52.2,54a15.8,24.6 0 1,0 31.6,0a15.8,24.6 0 1,0 -31.6,0Z" fill={PIEL} />
    <path d="M52.5,63a3,3 0 1,0 6,0a3,3 0 1,0 -6,0Z" fill="#F6A58A" />
    <path d="M77.5,63a3,3 0 1,0 6,0a3,3 0 1,0 -6,0Z" fill="#F6A58A" />
    <path d="M41.5,62C41,44 41.5,31 50,25C55,21.2 61,19.5 68,19.5C75,19.5 81,21.2 86,25C94.5,31 95,44 94.5,62C94.3,67.5 91,70.5 87,70.5L84.2,70.5C85,50 78.5,37.5 68,37.5C57.5,37.5 51,50 51.8,70.5L49,70.5C45,70.5 41.7,67.5 41.5,62Z" fill="#D8232A" stroke="#8E1217" strokeWidth="1.4" strokeLinejoin="round" />
    <path d="M63.6,19.9C66.5,19.5 69.5,19.5 72.4,19.9L72.4,37.6C69.5,37.4 66.5,37.4 63.6,37.6Z" fill="#FFFFFF" />
    <path d="M62.2,20.2V37.8M73.8,20.2V37.8" stroke="#E5AB46" strokeWidth="1.2" />
    <path d="M51.8,70.5C51,50 57.5,37.5 68,37.5C78.5,37.5 85,50 84.2,70.5" fill="none" stroke="#23262B" strokeWidth="3.2" strokeLinecap="round" />
    <path d="M46.5,42C46.5,32 51,26.5 60.5,23.6" fill="none" stroke="#FFFFFF" strokeOpacity="0.55" strokeWidth="2.4" strokeLinecap="round" />
    <path d="M88.5,58C88,66 83,70.5 76.5,70.8" fill="none" stroke="#2A2E35" strokeWidth="2.4" strokeLinecap="round" />
    <path d="M72.4,70.8a3.6,2.9 0 1,0 7.2,0a3.6,2.9 0 1,0 -7.2,0Z" fill="#23262B" />
    <path d="M52.4,50a7.6,7.6 0 1,0 15.2,0a7.6,7.6 0 1,0 -15.2,0Z" fill="#FFFFFF" stroke="#D8232A" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M68.4,50a7.6,7.6 0 1,0 15.2,0a7.6,7.6 0 1,0 -15.2,0Z" fill="#FFFFFF" stroke="#D8232A" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" />
  </>
);

const Pupilas: React.FC = () => (
  <>
    <path d="M57.4,51a3.4,3.4 0 1,0 6.8,0a3.4,3.4 0 1,0 -6.8,0Z" fill="#2B1B0E" />
    <path d="M73.4,51a3.4,3.4 0 1,0 6.8,0a3.4,3.4 0 1,0 -6.8,0Z" fill="#2B1B0E" />
    <path d="M60.8,49.8a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z" fill="#FFFFFF" />
    <path d="M76.8,49.8a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z" fill="#FFFFFF" />
  </>
);

/** Bandera a cuadros de 22x14 que ondea: `k` (−1..1) mueve las curvas del borde. */
const Bandera: React.FC<{ id: string; k: number }> = ({ id, k }) => {
  const a = 2.2 * k;
  const forma = `M0,0C6.6,${-a} 14.3,${a} 22,0L22,14C14.3,${14 + a} 6.6,${14 - a} 0,14Z`;
  return (
    <>
      <clipPath id={id}>
        <path d={forma} />
      </clipPath>
      <path d={forma} fill="#FFFFFF" />
      <path
        d="M0.00,-4.00H5.50V4.67H0.00ZM0.00,9.33H5.50V18.00H0.00ZM5.50,4.67H11.00V9.33H5.50ZM11.00,-4.00H16.50V4.67H11.00ZM11.00,9.33H16.50V18.00H11.00ZM16.50,4.67H22.00V9.33H16.50Z"
        fill="#23262B"
        clipPath={`url(#${id})`}
      />
      <path d={forma} fill="none" stroke="#23262B" strokeWidth="1.4" strokeLinejoin="round" />
    </>
  );
};

/** Trombito (casco de rally) sin fondo; todo se anima por props, nada de CSS ni azar. */
export const Trombito: React.FC<TrombitoProps> = ({
  pose = "casco",
  parpadeo = 0,
  rebote = 0,
  ondeo = 0,
  tamano = 300,
  idp = "t",
}) => {
  const escalaY = 1 + rebote * 0.1;
  const escalaX = 1 - rebote * 0.06;
  const bandera = pose === "bandera";
  const viewBox = bandera ? "-4 -8 118 98" : "16 16 82 70";
  const [, , vw, vh] = viewBox.split(" ").map(Number);
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={viewBox}
      width={tamano}
      height={tamano * (vh / vw)}
      role="img"
      aria-label="Trombito con casco de rally"
      style={{ overflow: "visible", transformOrigin: "50% 100%", transform: `scale(${escalaX}, ${escalaY})` }}
    >
      {bandera ? (
        <>
          {/* Mástil y bandera izquierdos */}
          <path d="M54,70L20,10" fill="none" stroke="#B97A0C" strokeWidth="3.4" strokeLinecap="round" />
          <path d="M54,70L20,10" fill="none" stroke="#F5C033" strokeWidth="1.2" strokeLinecap="round" />
          <g transform={`translate(20,10) rotate(${-29 + 5 * Math.sin(ondeo)}) translate(-1.6,0)`}>
            <Bandera id={`${idp}-fb1`} k={Math.sin(ondeo * 2)} />
          </g>
          <path d="M54,70L88,10" fill="none" stroke="#B97A0C" strokeWidth="3.4" strokeLinecap="round" />
          <path d="M54,70L88,10" fill="none" stroke="#F5C033" strokeWidth="1.2" strokeLinecap="round" />
          <g transform={`translate(88,10) rotate(${29 + 5 * Math.sin(ondeo + 1.7)}) translate(-20.4,0)`}>
            <Bandera id={`${idp}-fb2`} k={Math.sin(ondeo * 2 + 1.7)} />
          </g>
          <g transform="translate(7,19) scale(0.86)">
            <Cuerpo />
            <path d="M56,52.4Q60,46.4 64,52.4M72,52.4Q76,46.4 80,52.4" fill="none" stroke="#2B1B0E" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M60.5,63.5Q68,76 75.5,63.5Z" fill="#6B3F08" stroke="#6B3F08" strokeWidth="2" strokeLinejoin="round" />
            <path d="M64,69.2Q68,66.6 72,69.2Q68,72.4 64,69.2Z" fill="#E8725C" />
          </g>
        </>
      ) : (
        <>
          <Cuerpo />
          <Pupilas />
          {OJOS.map((o) => (
            <Parpado key={o.k} cx={o.cx} cy={o.cy} id={`${idp}-ojo-${o.k}`} cierre={parpadeo} />
          ))}
          <path d="M62,64Q68,70 74,64" fill="none" stroke="#6B3F08" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
        </>
      )}
    </svg>
  );
};

/** Curva de parpadeo: ~0,15 s cada ~2,5 s. */
export const parpadeoEnFrame = (frame: number, fps: number, desfase = 0): number => {
  const periodo = Math.round(2.5 * fps);
  const dur = 0.15 * fps;
  const p = (((frame + desfase) % periodo) + periodo) % periodo;
  return p < dur ? Math.sin((Math.PI * p) / dur) : 0;
};
