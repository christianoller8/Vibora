/** Instante de caducidad (ms desde epoch) leído del claim `exp` del JWT, o null si no se puede leer. */
export function expiraEnMs(token: string): number | null {
  const payload = token.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const exp = (JSON.parse(atob(base64)) as { exp?: unknown }).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}

/** Un token ilegible o sin `exp` se trata como caducado: no vale la pena mandarlo al backend. */
export function tokenCaducado(token: string, ahoraMs: number = Date.now()): boolean {
  const expira = expiraEnMs(token);
  return expira === null || expira <= ahoraMs;
}
