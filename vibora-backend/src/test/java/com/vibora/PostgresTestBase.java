package com.vibora;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para tests de integracion contra un PostgreSQL real (Testcontainers).
 * Contenedor singleton: arranca una vez por JVM y se reutiliza en todas las clases de test.
 */
public abstract class PostgresTestBase {

	@ServiceConnection
	protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	static {
		POSTGRES.start();
	}
}
