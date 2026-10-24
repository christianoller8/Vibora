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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migra una base nueva hasta V2, inserta datos "antiguos" (incluida una notificacion) y aplica V3: los datos
 * deben sobrevivir, las tablas de incidencias deben existir con sus restricciones NOMBRADAS y el CHECK de tipos
 * de notificacion debe aceptar los tipos nuevos y seguir rechazando los desconocidos.
 */
class MigracionV3Tests extends PostgresTestBase {

	private static final List<String> TIPOS_NUEVOS = List.of("PERSONAL_ANADIDO", "PERSONAL_QUITADO", "INCIDENCIA_NUEVA",
			"INCIDENCIA_ASIGNADA", "INCIDENCIA_ESTADO", "INCIDENCIA_COMENTARIO");

	private String url;
	private String usuario;
	private String password;

	private void migrarHastaV2ConDatos() throws Exception {
		String base = "migracion_v3_" + System.nanoTime();
		usuario = POSTGRES.getUsername();
		password = POSTGRES.getPassword();
		try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), usuario, password);
				Statement s = c.createStatement()) {
			s.execute("create database " + base);
		}
		url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + base);

		Flyway.configure().dataSource(url, usuario, password).target("2").load().migrate();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) values ('C', '08:00', '23:00')");
			s.execute("insert into usuario (email, password_hash, apellidos, rol, club_id) "
					+ "select 'admin@x.com', 'h', 'A', 'ADMIN_CLUB', id from club");
			s.execute("insert into usuario (email, password_hash, apellidos, rol) values ('b@x.com', 'h', 'B', 'JUGADOR')");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P1', 'INTERIOR' from club");
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, '2030-01-07T10:00Z', '2030-01-07T11:30Z', 'CONFIRMADA', 30 "
					+ "from pista p, usuario u where u.email = 'b@x.com'");
			s.execute("insert into notificacion (usuario_id, tipo, mensaje) "
					+ "select id, 'CARGO_NUEVO', 'Debes 11,25 euros' from usuario where email = 'b@x.com'");
		}
	}

	private int contar(Statement s, String sql) throws Exception {
		ResultSet rs = s.executeQuery(sql);
		rs.next();
		return rs.getInt(1);
	}

	@Test
	void v3CreaLasTablasDeIncidenciasConSusRestriccionesNombradas_yConservaLosDatos() throws Exception {
		migrarHastaV2ConDatos();

		Flyway.configure().dataSource(url, usuario, password).load().migrate();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			List<String> restricciones = new ArrayList<>();
			ResultSet rs = s.executeQuery("select conname from pg_constraint where conname like 'incidencia%'");
			while (rs.next()) {
				restricciones.add(rs.getString(1));
			}
			assertThat(restricciones).contains("incidencia_categoria_ck", "incidencia_prioridad_ck",
					"incidencia_estado_ck", "incidencia_asignada_ck", "incidencia_evento_tipo_ck");

			List<String> indices = new ArrayList<>();
			ResultSet idx = s.executeQuery("select indexname from pg_indexes where tablename in ('incidencia', 'incidencia_evento')");
			while (idx.next()) {
				indices.add(idx.getString(1));
			}
			assertThat(indices).contains("incidencia_club_estado_idx", "incidencia_reportada_por_idx",
					"incidencia_asignada_a_idx", "incidencia_pista_id_idx", "incidencia_evento_incidencia_creado_idx");

			assertThat(contar(s, "select count(*) from incidencia")).isZero();
			assertThat(contar(s, "select count(*) from incidencia_evento")).isZero();
			// los datos de V1/V2 siguen ahi
			assertThat(contar(s, "select count(*) from usuario")).isEqualTo(2);
			assertThat(contar(s, "select count(*) from reserva")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from notificacion where tipo = 'CARGO_NUEVO'")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from flyway_schema_history where version = '3' and success")).isEqualTo(1);
		}
	}

	@Test
	void v3AmpliaElCheckDeTiposDeNotificacion_aceptaLosNuevos_yRechazaLosDesconocidos() throws Exception {
		migrarHastaV2ConDatos();
		Flyway.configure().dataSource(url, usuario, password).load().migrate();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			for (String tipo : TIPOS_NUEVOS) {
				s.execute("insert into notificacion (usuario_id, tipo, mensaje) "
						+ "select id, '" + tipo + "', 'x' from usuario where email = 'b@x.com'");
			}
			// los de siempre siguen valiendo
			s.execute("insert into notificacion (usuario_id, tipo, mensaje) "
					+ "select id, 'RESERVA_CANCELADA', 'x' from usuario where email = 'b@x.com'");
			assertThat(contar(s, "select count(*) from notificacion")).isEqualTo(1 + TIPOS_NUEVOS.size() + 1);

			assertThatThrownBy(() -> s.execute("insert into notificacion (usuario_id, tipo, mensaje) "
					+ "select id, 'OTRA_COSA', 'x' from usuario where email = 'b@x.com'"))
					.hasMessageContaining("notificacion_tipo_ck");
		}
	}

	@Test
	void v3_laRestriccionDeAsignadaAplicaAFilasInsertadasTrasLaMigracion() throws Exception {
		migrarHastaV2ConDatos();
		Flyway.configure().dataSource(url, usuario, password).load().migrate();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			assertThatThrownBy(() -> s.execute("insert into incidencia (club_id, reportada_por, titulo, descripcion, "
					+ "categoria, estado, creada_en, actualizada_en) select c.id, u.id, 't', 'd', 'RED', 'ASIGNADA', now(), now() "
					+ "from club c, usuario u where u.email = 'b@x.com'"))
					.hasMessageContaining("incidencia_asignada_ck");
			// la fila valida sale con los valores por defecto
			s.execute("insert into incidencia (club_id, reportada_por, titulo, descripcion, categoria, creada_en, "
					+ "actualizada_en) select c.id, u.id, 't', 'd', 'RED', now(), now() from club c, usuario u "
					+ "where u.email = 'b@x.com'");
			ResultSet rs = s.executeQuery("select prioridad, estado, version from incidencia");
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("MEDIA");
			assertThat(rs.getString(2)).isEqualTo("ABIERTA");
			assertThat(rs.getLong(3)).isZero();
		}
	}
}
