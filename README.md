# Víbora

Plataforma para reservar pistas de pádel en clubes y montar partidos abiertos en los que otros jugadores se apuntan.
Backend Spring Boot (REST + JWT, PostgreSQL) y frontend Angular.

## Arranque en local

```bash
docker compose up -d db                      # PostgreSQL (puerto 5433)
cd vibora-backend && ./mvnw spring-boot:run  # API en http://localhost:8081
cd vibora-frontend && npm start              # app en http://localhost:4200
```

## Stack completo con Docker

Base de datos, backend y frontend (nginx) en contenedores. Los dos últimos viven bajo el perfil `app`, así que el
flujo de desarrollo de arriba no cambia: `docker compose up -d db` sigue levantando solo PostgreSQL.

```bash
cp env.example .env        # edita .env: pon un VIBORA_JWT_SECRET propio (el fichero explica cómo generarlo)
docker compose --profile app up -d --build
```

La web queda en http://localhost:8088 (cambia el puerto con `FRONTEND_PORT` en `.env`). Nginx sirve la SPA y reenvía
`/api` y `/ws` al backend, que no se publica en el host.

`VIBORA_JWT_SECRET` es obligatorio. Un servicio de comprobación (`comprobar-VIBORA_JWT_SECRET`, solo con el perfil
`app`) corre antes que el backend y aborta el `up` al instante si el secreto está vacío, tiene menos de 32 caracteres o
sigue siendo el marcador `CAMBIAME-...` de `env.example`:

```text
service "comprobar-VIBORA_JWT_SECRET" didn't complete successfully: exit 1
```

El motivo exacto sale con `docker compose --profile app logs comprobar-VIBORA_JWT_SECRET`. El backend ni se crea (no
hay bucle de reinicios), y `docker compose up -d db` no se ve afectado: no necesita `.env`. Si arrancas el backend
saltándote la comprobación (`--no-deps`), su `ENTRYPOINT` también se niega a arrancar sin secreto.

```bash
docker compose --profile app stop backend frontend   # para solo la app; la base de datos sigue en marcha
docker compose --profile app up -d --build           # la vuelve a levantar (reconstruye si hay cambios)
```

> No uses `docker compose down -v` si quieres conservar los datos: `-v` borra el volumen `vibora-pgdata`.

El script de datos de ejemplo también sirve contra este stack (entra por nginx, que es `localhost`):
`node scripts/seed-dev.mjs --base-url http://localhost:8088`.

## Integración continua

`.github/workflows/ci.yml` se ejecuta en cada push y pull request a `main`:

- **backend**: `./mvnw verify` con Java 25 (los tests usan Testcontainers).
- **frontend**: `ng test` y `ng build --configuration production` con Node 24.
- **scripts**: tests del helper de fechas del seed.
- **docker**: construye las dos imágenes, levanta el stack con `docker compose --profile app` y comprueba por nginx
  que la web responde, que una ruta de la SPA devuelve `index.html`, que `/api/auth/me` da 401 y que
  `/actuator/health` no queda publicado.

## Datos de ejemplo para desarrollo

Con el backend en marcha:

```bash
node scripts/seed-dev.mjs [--base-url http://localhost:8081]
```

Requiere Node 20 o superior. Es idempotente: se puede repetir sin duplicar datos. Crea el club "Club Vibora Demo"
(09:00-21:00) con dos pistas, tarifas para todos los días y un partido abierto a 3 días vista, a las 18:00 (hora de
Madrid), con 2 plazas libres.

| Usuario                 | Rol        |
| ----------------------- | ---------- |
| `admin@vibora.test`     | ADMIN_CLUB |
| `jugador1@vibora.test` (organiza el partido) | JUGADOR |
| `jugador2@vibora.test` (apuntado al partido) | JUGADOR |
| `jugador3@vibora.test`, `jugador4@vibora.test` | JUGADOR |

Contraseña de todos: `Vibora-dev-2026`.

> **Solo desarrollo.** El script crea usuarios con una contraseña conocida y se niega a ejecutarse contra hosts que no
> sean `localhost`, `127.0.0.1` o `[::1]`. No lo uses en ningún entorno compartido ni de producción.

Tests del helper de fechas del script: `node --test "scripts/lib/*.test.mjs"`.
