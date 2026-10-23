export interface Club {
  id: number;
  nombre: string;
  direccion: string | null;
  ciudad: string | null;
  /** LocalTime del backend: "HH:mm:ss". */
  horaApertura: string;
  horaCierre: string;
}
