package com.vibora;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migra una base nueva hasta V1, inserta reservas "antiguas" y aplica V2: cada organizador debe quedar
 * como participante puesto 1 (tambien en reservas canceladas) y ninguna reserva abierta.
 */
class MigracionV2Tests extends PostgresTestBase {

	@Test
	void v2RellenaAlOrganizadorDeCadaReservaExistenteComoPuestoUno() throws Exception {
		String base = "migracion_v2_" + System.nanoTime();
		String usuario = POSTGRES.getUsername();
		String password = POSTGRES.getPassword();
		try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), usuario, password);
				Statement s = c.createStatement()) {
			s.execute("create database " + base);
		}
		String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + base);

		Flyway.configure().dataSource(url, usuario, password).target("1").load().migrate();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) values ('C', '08:00', '23:00')");
			s.execute("insert into usuario (email, password_hash, apellidos, rol) values "
					+ "('a@x.com', 'h', 'A', 'JUGADOR'), ('b@x.com', 'h', 'B', 'JUGADOR')");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P1', 'INTERIOR' from club");
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, '2030-01-07T10:00Z', '2030-01-07T11:30Z', 'CONFIRMADA', 30 "
					+ "from pista p, usuario u where u.email = 'a@x.com'");
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, '2030-01-08T10:00Z', '2030-01-08T11:30Z', 'CANCELADA', 30 "
					+ "from pista p, usuario u where u.email = 'b@x.com'");
		}

		Flyway.configure().dataSource(url, usuario, password).load().migrate();

		List<String> filas = new ArrayList<>();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			ResultSet rs = s.executeQuery("select u.email, p.puesto from participante p "
					+ "join usuario u on u.id = p.usuario_id order by u.email");
			while (rs.next()) {
				filas.add(rs.getString(1) + ":" + rs.getInt(2));
			}
			ResultSet abiertas = s.executeQuery("select count(*) from reserva where abierta");
			abiertas.next();
			assertThat(abiertas.getInt(1)).isZero();
			// cada reserva coincide con su organizador
			ResultSet coherentes = s.executeQuery("select count(*) from reserva r join participante p "
					+ "on p.reserva_id = r.id and p.usuario_id = r.usuario_id and p.puesto = 1");
			coherentes.next();
			assertThat(coherentes.getInt(1)).isEqualTo(2);
		}
		assertThat(filas).containsExactly("a@x.com:1", "b@x.com:1");
	}
}
