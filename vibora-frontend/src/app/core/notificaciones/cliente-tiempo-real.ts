import { InjectionToken } from '@angular/core';

/**
 * Abstracción mínima de la conexión en tiempo real. Esconde la librería STOMP detrás de una fábrica
 * inyectable: los tests inyectan un doble (sin sockets reales) y la librería solo se descarga al conectar.
 */
export interface OpcionesClienteTiempoReal {
  /** ws:// o wss:// del endpoint STOMP. */
  url: string;
  /** Cabeceras del frame CONNECT (aquí viaja el JWT: el navegador no puede ponerlas en el handshake). */
  cabeceras: Record<string, string>;
  /** Destino al que suscribirse en cada (re)conexión. */
  destino: string;
  /** Se llama en cada conexión establecida, también tras una reconexión automática. */
  alConectar: () => void;
  /** Cuerpo (texto) de cada mensaje recibido en el destino. */
  alMensaje: (cuerpo: string) => void;
  /** El servidor respondió con un frame ERROR; recibe su cabecera "message". */
  alErrorStomp: (mensaje: string) => void;
}

export interface ClienteTiempoReal {
  /** Empieza a conectar (y a reconectar con espera creciente si se cae). */
  activar(): void;
  /** Cierra la conexión y cancela cualquier reconexión pendiente. */
  desactivar(): Promise<void>;
}

export type FabricaClienteTiempoReal = (
  opciones: OpcionesClienteTiempoReal,
) => Promise<ClienteTiempoReal>;

/**
 * Fábrica real: STOMP sobre WebSocket nativo. La librería se carga con `import()` dinámico para que no
 * engorde el bundle inicial (solo hace falta tras iniciar sesión).
 */
export const fabricaStomp: FabricaClienteTiempoReal = async (opciones) => {
  const { Client, ReconnectionTimeMode } = await import('@stomp/stompjs');

  const cliente = new Client({
    brokerURL: opciones.url,
    connectHeaders: opciones.cabeceras,
    // espera creciente (1 s, 2 s, 4 s... hasta 30 s) en lugar de martillear al servidor caído
    reconnectDelay: 1000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: 30_000,
    // el broker simple del backend no manda latidos; si algún día los manda, se negocian
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    onConnect: () => {
      cliente.subscribe(opciones.destino, (mensaje) => opciones.alMensaje(mensaje.body));
      opciones.alConectar();
    },
    onStompError: (frame) => opciones.alErrorStomp(frame.headers['message'] ?? ''),
  });

  return {
    activar: () => cliente.activate(),
    desactivar: () => cliente.deactivate(),
  };
};

export const FABRICA_CLIENTE_TIEMPO_REAL = new InjectionToken<FabricaClienteTiempoReal>(
  'FABRICA_CLIENTE_TIEMPO_REAL',
  { providedIn: 'root', factory: () => fabricaStomp },
);
