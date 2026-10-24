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
 * Migra una base nueva hasta V3, inserta datos "antiguos" (con una incidencia y su evento) y aplica V4: los datos
 * deben sobrevivir, las tablas de fotos y bloqueos deben existir con sus restricciones NOMBRADAS, el CHECK de tipos de
 * evento debe aceptar los nuevos y seguir rechazando los desconocidos, y solo puede haber un bloqueo activo por
 * incidencia.
 */
class MigracionV4Tests extends PostgresTestBase {

	private static final List<String> TIPOS_NUEVOS = List.of("FOTO_ANADIDA", "FOTO_ELIMINADA", "BLOQUEO_CREADO",
			"BLOQUEO_LEVANTADO");

	private String url;
	private String usuario;
	private String password;

	private void migrarHastaV3ConDatos() throws Exception {
		String base = "migracion_v4_" + System.nanoTime();
		usuario = POSTGRES.getUsername();
		password = POSTGRES.getPassword();
		try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), usuario, password);
				Statement s = c.createStatement()) {
			s.execute("create database " + base);
		}
		url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + base);

		Flyway.configure().dataSource(url, usuario, password).target("3").load().migrate();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) values ('C', '08:00', '23:00')");
			s.execute("insert into usuario (email, password_hash, apellidos, rol, club_id) "
					+ "select 'admin@x.com', 'h', 'A', 'ADMIN_CLUB', id from club");
			s.execute("insert into usuario (email, password_hash, apellidos, rol) values ('b@x.com', 'h', 'B', 'JUGADOR')");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P1', 'INTERIOR' from club");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P2', 'INTERIOR' from club");
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, '2030-01-07T10:00Z', '2030-01-07T11:30Z', 'CONFIRMADA', 30 "
					+ "from pista p, usuario u where u.email = 'b@x.com' and p.nombre = 'P1'");
			s.execute("insert into incidencia (club_id, pista_id, reportada_por, titulo, descripcion, categoria, creada_en, "
					+ "actualizada_en) select c.id, p.id, u.id, 'Red rota', 'd', 'RED', now(), now() "
					+ "from club c, pista p, usuario u where u.email = 'b@x.com' and p.nombre = 'P1'");
			s.execute("insert into incidencia_evento (incidencia_id, usuario_id, tipo, estado_nuevo, creado_en) "
					+ "select i.id, u.id, 'CREADA', 'ABIERTA', now() from incidencia i, usuario u where u.email = 'b@x.com'");
		}
	}

	private void migrarTodo() {
		Flyway.configure().dataSource(url, usuario, password).load().migrate();
	}

	private int contar(Statement s, String sql) throws Exception {
		ResultSet rs = s.executeQuery(sql);
		rs.next();
		return rs.getInt(1);
	}

	private List<String> lista(Statement s, String sql) throws Exception {
		List<String> resultado = new ArrayList<>();
		ResultSet rs = s.executeQuery(sql);
		while (rs.next()) {
			resultado.add(rs.getString(1));
		}
		return resultado;
	}

	private void insertarBloqueo(Statement s, String pista, String incidencia, String hasta, String levantadoEn) throws Exception {
		s.execute("insert into bloqueo_pista (pista_id, incidencia_id, motivo, desde, hasta, creado_por, creado_en, "
				+ "levantado_en, levantado_por) select p.id, " + incidencia + ", 'motivo', '2030-01-01T00:00Z', " + hasta
				+ ", u.id, now(), " + levantadoEn + ", " + (levantadoEn.equals("null") ? "null" : "u.id")
				+ " from pista p, usuario u where p.nombre = '" + pista + "' and u.email = 'admin@x.com'");
	}

	@Test
	void v4CreaFotosYBloqueosConSusRestriccionesNombradas_yConservaLosDatos() throws Exception {
		migrarHastaV3ConDatos();

		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			assertThat(lista(s, "select conname from pg_constraint where conname like 'incidencia_foto%' "
					+ "or conname like 'bloqueo_pista%' or conname = 'incidencia_evento_tipo_ck'"))
					.contains("incidencia_foto_clave_uk", "incidencia_foto_tipo_ck", "incidencia_foto_tamano_ck",
							"bloqueo_pista_rango_ck", "incidencia_evento_tipo_ck");
			assertThat(lista(s, "select indexname from pg_indexes where tablename in ('incidencia_foto', 'bloqueo_pista')"))
					.contains("incidencia_foto_incidencia_idx", "bloqueo_pista_pista_levantado_idx",
							"bloqueo_pista_incidencia_activo_uk");

			assertThat(contar(s, "select count(*) from incidencia_foto")).isZero();
			assertThat(contar(s, "select count(*) from bloqueo_pista")).isZero();
			// los datos de V1..V3 siguen ahi
			assertThat(contar(s, "select count(*) from usuario")).isEqualTo(2);
			assertThat(contar(s, "select count(*) from reserva")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from incidencia")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from incidencia_evento where tipo = 'CREADA'")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from flyway_schema_history where version = '4' and success")).isEqualTo(1);
		}
	}

	@Test
	void v4AmpliaElCheckDeTiposDeEvento_aceptaLosNuevos_yRechazaLosDesconocidos() throws Exception {
		migrarHastaV3ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			for (String tipo : TIPOS_NUEVOS) {
				s.execute("insert into incidencia_evento (incidencia_id, usuario_id, tipo, creado_en) "
						+ "select i.id, u.id, '" + tipo + "', now() from incidencia i, usuario u where u.email = 'b@x.com'");
			}
			for (String tipo : List.of("CREADA", "ASIGNADA", "ESTADO", "COMENTARIO", "PRIORIDAD")) {
				s.execute("insert into incidencia_evento (incidencia_id, usuario_id, tipo, creado_en) "
						+ "select i.id, u.id, '" + tipo + "', now() from incidencia i, usuario u where u.email = 'b@x.com'");
			}
			assertThat(contar(s, "select count(*) from incidencia_evento")).isEqualTo(1 + TIPOS_NUEVOS.size() + 5);

			assertThatThrownBy(() -> s.execute("insert into incidencia_evento (incidencia_id, usuario_id, tipo, creado_en) "
					+ "select i.id, u.id, 'BORRADA', now() from incidencia i, usuario u where u.email = 'b@x.com'"))
					.hasMessageContaining("incidencia_evento_tipo_ck");
		}
	}

	@Test
	void v4_laFotoSoloAceptaImagenesDeHastaCincoMegas_yClaveUnica() throws Exception {
		migrarHastaV3ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			String base = "insert into incidencia_foto (incidencia_id, clave_almacen, nombre_original, tipo_contenido, "
					+ "tamano, subida_por, creada_en) select i.id, '%s', 'f.jpg', '%s', %d, u.id, now() from incidencia i, "
					+ "usuario u where u.email = 'b@x.com'";
			s.execute(base.formatted("k1", "image/jpeg", 1));
			s.execute(base.formatted("k2", "image/png", 5242880));
			s.execute(base.formatted("k3", "image/webp", 100));

			assertThatThrownBy(() -> s.execute(base.formatted("k4", "image/gif", 100)))
					.hasMessageContaining("incidencia_foto_tipo_ck");
			assertThatThrownBy(() -> s.execute(base.formatted("k5", "image/jpeg", 0)))
					.hasMessageContaining("incidencia_foto_tamano_ck");
			assertThatThrownBy(() -> s.execute(base.formatted("k6", "image/jpeg", 5242881)))
					.hasMessageContaining("incidencia_foto_tamano_ck");
			assertThatThrownBy(() -> s.execute(base.formatted("k1", "image/jpeg", 10)))
					.hasMessageContaining("incidencia_foto_clave_uk");
		}
	}

	@Test
	void v4_elBloqueoExigeHastaPosteriorADesde_yAdmiteAbiertos() throws Exception {
		migrarHastaV3ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			insertarBloqueo(s, "P1", "null", "null", "null"); // abierto (sin fin)
			insertarBloqueo(s, "P1", "null", "'2030-01-02T00:00Z'", "null");

			assertThatThrownBy(() -> insertarBloqueo(s, "P2", "null", "'2030-01-01T00:00Z'", "null")) // hasta = desde
					.hasMessageContaining("bloqueo_pista_rango_ck");
			assertThatThrownBy(() -> insertarBloqueo(s, "P2", "null", "'2029-12-31T00:00Z'", "null"))
					.hasMessageContaining("bloqueo_pista_rango_ck");
			assertThat(contar(s, "select count(*) from bloqueo_pista")).isEqualTo(2);
		}
	}

	@Test
	void v4_soloUnBloqueoActivoPorIncidencia_peroSePuedenAcumularLevantadosYLosSinIncidencia() throws Exception {
		migrarHastaV3ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			String incidencia = "(select id from incidencia)";
			insertarBloqueo(s, "P1", incidencia, "null", "now()"); // levantado: no cuenta
			insertarBloqueo(s, "P1", incidencia, "null", "now()");
			insertarBloqueo(s, "P1", incidencia, "null", "null"); // el unico activo

			assertThatThrownBy(() -> insertarBloqueo(s, "P1", incidencia, "null", "null"))
					.hasMessageContaining("bloqueo_pista_incidencia_activo_uk");
			assertThatThrownBy(() -> insertarBloqueo(s, "P1", incidencia, "'2030-02-01T00:00Z'", "null"))
					.hasMessageContaining("bloqueo_pista_incidencia_activo_uk");

			// sin incidencia no hay limite
			insertarBloqueo(s, "P2", "null", "null", "null");
			insertarBloqueo(s, "P2", "null", "null", "null");
			assertThat(contar(s, "select count(*) from bloqueo_pista")).isEqualTo(5);

			// al levantar el activo se puede crear otro
			s.execute("update bloqueo_pista set levantado_en = now(), levantado_por = creado_por "
					+ "where incidencia_id is not null and levantado_en is null");
			insertarBloqueo(s, "P1", incidencia, "null", "null");
			assertThat(contar(s, "select count(*) from bloqueo_pista where incidencia_id is not null and levantado_en is null"))
					.isEqualTo(1);
		}
	}
}
