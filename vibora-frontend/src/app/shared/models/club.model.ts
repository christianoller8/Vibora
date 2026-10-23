export interface Club {
  id: number;
  nombre: string;
  direccion: string | null;
  ciudad: string | null;
  /** LocalTime del backend: "HH:mm:ss". */
  horaApertura: string;
  horaCierre: string;
}

/** Body de alta/modificación de club. Las horas se envían como "HH:mm". */
export interface ClubRequest {
  nombre: string;
  direccion: string | null;
  ciudad: string | null;
  horaApertura: string;
  horaCierre: string;
}
