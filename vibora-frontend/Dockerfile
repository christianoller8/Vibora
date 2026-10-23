# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa 1: compilar la SPA (Node 24, el mismo major que se usa en local y en CI).
# Los tests no se ejecutan aqui: corren en CI (job "frontend").
# ---------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npx ng build --configuration production \
 && mkdir -p /out && node docker/gen-csp.mjs dist/vibora-frontend/browser/index.html /out/csp.conf

# ---------------------------------------------------------------------------
# Etapa 2: servir con nginx SIN privilegios (nginxinc/nginx-unprivileged: corre como uid 101 y escucha en 8080,
# asi el contenedor no necesita root ni capacidades para el puerto 80).
# ---------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /out/csp.conf /etc/nginx/snippets/csp.conf
COPY --from=build /app/dist/vibora-frontend/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
