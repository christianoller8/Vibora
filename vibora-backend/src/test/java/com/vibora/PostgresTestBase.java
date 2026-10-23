package com.vibora;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para tests de integracion contra un PostgreSQL real (Testcontainers).
 * Contenedor singleton: arranca una vez por JVM y se reutiliza en todas las clases de test.
 */
public abstract class PostgresTestBase {

	/**
	 * Cada configuracion distinta de test crea su propio contexto de Spring (cacheado durante toda la JVM) con su pool
	 * de 10 conexiones; con el max_connections=100 por defecto de PostgreSQL, a partir de ~10 contextos el siguiente
	 * falla con "too many clients already". Se sube el limite en vez de limitar los pools.
	 */
	@ServiceConnection
	protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
			.withCommand("postgres", "-c", "max_connections=400");

	static {
		POSTGRES.start();
	}
}
