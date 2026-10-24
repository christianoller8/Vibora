package com.vibora;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6 (indice de las metricas): una base migrada hasta V5 con datos sigue igual tras V6 y gana el indice
 * reserva (pista_id, inicio); con unas 120.000 reservas de 200 pistas, la consulta de las metricas (reservas de las pistas
 * de UN club en un mes) lo usa en lugar de recorrer toda la tabla.
 */
class MigracionV6Tests extends PostgresTestBase {

	private String url;
	private String usuario;
	private String password;

	private void baseNueva(String prefijo) throws Exception {
		String base = prefijo + System.nanoTime();
		usuario = POSTGRES.getUsername();
		password = POSTGRES.getPassword();
		try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), usuario, password);
				Statement s = c.createStatement()) {
			s.execute("create database " + base);
		}
		url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + base);
	}

	private static List<String> lista(Statement s, String sql) throws SQLException {
		List<String> resultado = new ArrayList<>();
		ResultSet rs = s.executeQuery(sql);
		while (rs.next()) {
			resultado.add(rs.getString(1));
		}
		return resultado;
	}

	@Test
	void v6CreaElIndiceDeReservasPorPistaEInicio_yConservaLosDatos() throws Exception {
		baseNueva("migracion_v6_");
		Flyway.configure().dataSource(url, usuario, password).target("5").load().migrate();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) values ('C', '08:00', '23:00')");
			s.execute("insert into usuario (email, password_hash, apellidos, rol) values ('b@x.com', 'h', 'B', 'JUGADOR')");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P1', 'INTERIOR' from club");
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, '2030-01-07T10:00Z', '2030-01-07T11:30Z', 'CONFIRMADA', 45 from pista p, usuario u");
			assertThat(lista(s, "select indexname from pg_indexes where tablename = 'reserva'"))
					.doesNotContain("reserva_pista_inicio_idx");
		}

		Flyway.configure().dataSource(url, usuario, password).load().migrate();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			assertThat(lista(s, "select indexdef from pg_indexes where indexname = 'reserva_pista_inicio_idx'"))
					.singleElement().asString().contains("USING btree (pista_id, inicio)");
			assertThat(lista(s, "select count(*) from reserva")).containsExactly("1");
			assertThat(lista(s, "select max(version::int)::text from flyway_schema_history")).containsExactly("6");
		}
	}

	@Test
	void laConsultaDeMetricasUsaElIndiceConMuchasReservasDeOtrosClubes() throws Exception {
		baseNueva("migracion_v6_plan_");
		Flyway.configure().dataSource(url, usuario, password).load().migrate();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) select 'C' || g, '08:00', '23:00' "
					+ "from generate_series(1, 100) g");
			s.execute("insert into usuario (email, password_hash, apellidos, rol) values ('b@x.com', 'h', 'B', 'JUGADOR')");
			s.execute("insert into pista (club_id, nombre, tipo) select c.id, 'P' || n, 'INTERIOR' "
					+ "from club c, generate_series(1, 2) n");
			// 600 reservas por pista (una al dia, sin solapes), una de cada siete cancelada: 120.000 filas
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, timestamptz '2029-01-01 10:00+00' + n * interval '1 day', "
					+ "timestamptz '2029-01-01 11:30+00' + n * interval '1 day', "
					+ "case when n % 7 = 0 then 'CANCELADA' else 'CONFIRMADA' end, 30 "
					+ "from pista p, usuario u, generate_series(0, 599) n");
			s.execute("analyze reserva");
			s.execute("analyze pista");
			assertThat(lista(s, "select count(*) from reserva")).containsExactly("120000");

			List<String> plan = lista(s, "explain select r.pista_id, r.inicio, r.estado, r.precio_total from reserva r "
					+ "join pista p on p.id = r.pista_id where p.club_id = (select min(id) from club) "
					+ "and r.inicio >= timestamptz '2029-06-01 00:00+00' and r.inicio < timestamptz '2029-07-01 00:00+00'");

			assertThat(String.join("\n", plan)).contains("reserva_pista_inicio_idx").doesNotContain("Seq Scan on reserva");
		}
	}
}
