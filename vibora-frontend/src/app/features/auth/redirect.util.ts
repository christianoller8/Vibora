export const DESTINO_POR_DEFECTO = '/agenda';

/**
 * Valida el `redirectTo` de la query string: solo rutas internas. Evita un open redirect del estilo
 * `/login?redirectTo=https://sitio-malo.com` o `//sitio-malo.com`.
 */
export function destinoSeguro(redirectTo: string | null): string {
  const esInterna =
    redirectTo !== null &&
    redirectTo.startsWith('/') &&
    !redirectTo.startsWith('//') &&
    !redirectTo.startsWith('/\\');
  return esInterna ? redirectTo : DESTINO_POR_DEFECTO;
}
