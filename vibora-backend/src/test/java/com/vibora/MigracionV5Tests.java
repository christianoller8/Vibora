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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migra una base nueva hasta V4, inserta datos "antiguos" (club, reservas, una notificacion) y aplica V5: los datos deben
 * sobrevivir, las tablas de facturacion existen con sus restricciones NOMBRADAS, el CHECK de tipos de aviso acepta
 * FACTURA_RECTIFICATIVA y el trigger rechaza cualquier UPDATE o DELETE de una factura.
 */
class MigracionV5Tests extends PostgresTestBase {

	private String url;
	private String usuario;
	private String password;

	private void migrarHastaV4ConDatos() throws Exception {
		String base = "migracion_v5_" + System.nanoTime();
		usuario = POSTGRES.getUsername();
		password = POSTGRES.getPassword();
		try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), usuario, password);
				Statement s = c.createStatement()) {
			s.execute("create database " + base);
		}
		url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + base);

		Flyway.configure().dataSource(url, usuario, password).target("4").load().migrate();
		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) values ('C', '08:00', '23:00')");
			s.execute("insert into club (nombre, hora_apertura, hora_cierre) values ('D', '08:00', '23:00')");
			s.execute("insert into usuario (email, password_hash, apellidos, rol, club_id) "
					+ "select 'admin@x.com', 'h', 'A', 'ADMIN_CLUB', id from club where nombre = 'C'");
			s.execute("insert into usuario (email, password_hash, apellidos, rol) values ('b@x.com', 'h', 'B', 'JUGADOR')");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P1', 'INTERIOR' from club where nombre = 'C'");
			s.execute("insert into pista (club_id, nombre, tipo) select id, 'P2', 'INTERIOR' from club where nombre = 'C'");
			s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
					+ "select p.id, u.id, '2030-01-07T10:00Z', '2030-01-07T11:30Z', 'CONFIRMADA', 45 "
					+ "from pista p, usuario u where u.email = 'b@x.com' and p.nombre = 'P1'");
			s.execute("insert into notificacion (usuario_id, tipo, mensaje) select id, 'CARGO_NUEVO', 'hola' from usuario "
					+ "where email = 'b@x.com'");
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

	/** Segunda reserva (en P2) para poder tener mas de una factura original. */
	private void segundaReserva(Statement s) throws Exception {
		s.execute("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) "
				+ "select p.id, u.id, '2030-01-07T10:00Z', '2030-01-07T11:30Z', 'CONFIRMADA', 30 "
				+ "from pista p, usuario u where u.email = 'b@x.com' and p.nombre = 'P2'");
	}

	/** reserva: 1 = la de P1, 2 = la de P2. rectificaA: id de factura o "null". */
	private void factura(Statement s, int reserva, String serie, int numero, String tipo, String rectificaA, String base,
			String cuota, String total) throws SQLException {
		s.execute("insert into factura (club_id, reserva_id, receptor_usuario_id, serie, numero, tipo, rectifica_a, "
				+ "emitida_en, emisor_razon_social, emisor_nif, emisor_direccion, receptor_nombre, concepto, base, tipo_iva, "
				+ "cuota_iva, total) select c.id, r.id, u.id, '" + serie + "', " + numero + ", '" + tipo + "', " + rectificaA
				+ ", now(), 'Club S.L.', 'B12345674', 'Calle 1', 'B', 'Pista', " + base + ", 21.00, " + cuota + ", " + total
				+ " from club c, usuario u, reserva r join pista p on p.id = r.pista_id where c.nombre = 'C' "
				+ "and u.email = 'b@x.com' and p.nombre = '" + (reserva == 1 ? "P1" : "P2") + "'");
	}

	private void facturaOriginal(Statement s, int reserva, int numero) throws SQLException {
		factura(s, reserva, "F2030", numero, "ORIGINAL", "null", "37.19", "7.81", "45.00");
	}

	// ---- estructura ----

	@Test
	void v5CreaLasTablasConSusRestriccionesNombradas_yConservaLosDatos() throws Exception {
		migrarHastaV4ConDatos();

		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			assertThat(lista(s, "select conname from pg_constraint where conrelid in ('factura'::regclass, "
					+ "'factura_serie'::regclass, 'club'::regclass)"))
					.contains("club_tipo_iva_ck", "factura_serie_pk", "factura_serie_ultimo_ck", "factura_numero_ck",
							"factura_tipo_ck", "factura_total_signo_ck", "factura_base_cuota_ck", "factura_tipo_iva_ck",
							"factura_numero_uk", "factura_rectifica_uk");
			assertThat(lista(s, "select indexname from pg_indexes where tablename = 'factura'"))
					.contains("factura_original_reserva_uk", "factura_reserva_idx", "factura_club_emitida_idx",
							"factura_receptor_idx");
			assertThat(lista(s, "select tgname from pg_trigger where tgrelid = 'factura'::regclass and not tgisinternal"))
					.containsExactly("factura_inmutable_tg");
			assertThat(lista(s, "select tgenabled from pg_trigger where tgname = 'factura_inmutable_tg'")).containsExactly("O");

			// los datos de V1..V4 siguen ahi y el club antiguo estrena IVA por defecto y sin datos fiscales
			assertThat(contar(s, "select count(*) from club")).isEqualTo(2);
			assertThat(contar(s, "select count(*) from reserva")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from notificacion where tipo = 'CARGO_NUEVO'")).isEqualTo(1);
			assertThat(contar(s, "select count(*) from club where tipo_iva = 21.00 and razon_social is null "
					+ "and nif is null and direccion_fiscal is null")).isEqualTo(2);
			assertThat(contar(s, "select count(*) from factura")).isZero();
			assertThat(contar(s, "select count(*) from factura_serie")).isZero();
			assertThat(contar(s, "select count(*) from flyway_schema_history where version = '5' and success")).isEqualTo(1);
		}
	}

	@Test
	void v5AmpliaElCheckDeTiposDeAviso_aceptaLaRectificativa_yRechazaLosDesconocidos() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			for (String tipo : List.of("FACTURA_RECTIFICATIVA", "RESERVA_CANCELADA", "INCIDENCIA_COMENTARIO", "PERSONAL_ANADIDO")) {
				s.execute("insert into notificacion (usuario_id, tipo, mensaje) select id, '" + tipo + "', 'm' from usuario "
						+ "where email = 'b@x.com'");
			}
			assertThatThrownBy(() -> s.execute("insert into notificacion (usuario_id, tipo, mensaje) select id, 'FACTURA_ANULADA', "
					+ "'m' from usuario where email = 'b@x.com'")).hasMessageContaining("notificacion_tipo_ck");
		}
	}

	@Test
	void v5_elTipoDeIvaDelClubVaDeCeroACien() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("update club set tipo_iva = 0.00 where nombre = 'C'");
			s.execute("update club set tipo_iva = 10.50 where nombre = 'C'");
			s.execute("update club set tipo_iva = 99.99 where nombre = 'C'");

			assertThatThrownBy(() -> s.execute("update club set tipo_iva = -0.01 where nombre = 'C'"))
					.hasMessageContaining("club_tipo_iva_ck");
			assertThat(contar(s, "select count(*) from club where nombre = 'C' and tipo_iva = 99.99")).isEqualTo(1);
		}
	}

	// ---- reglas de las facturas ----

	@Test
	void v5_laOriginalExigeTotalPositivoYSinReferencia_laRectificativaTotalNegativoYConReferencia() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			segundaReserva(s);
			facturaOriginal(s, 1, 1); // valida

			// original con total cero o negativo
			assertThatThrownBy(() -> factura(s, 2, "F2030", 2, "ORIGINAL", "null", "0.00", "0.00", "0.00"))
					.hasMessageContaining("factura_total_signo_ck");
			assertThatThrownBy(() -> factura(s, 2, "F2030", 2, "ORIGINAL", "null", "-37.19", "-7.81", "-45.00"))
					.hasMessageContaining("factura_total_signo_ck");
			// original que dice rectificar a otra
			assertThatThrownBy(() -> factura(s, 2, "F2030", 2, "ORIGINAL", "(select id from factura)", "37.19", "7.81", "45.00"))
					.hasMessageContaining("factura_total_signo_ck");
			// rectificativa con total positivo, o sin referencia
			assertThatThrownBy(() -> factura(s, 1, "F2030", 2, "RECTIFICATIVA", "(select id from factura)", "37.19", "7.81", "45.00"))
					.hasMessageContaining("factura_total_signo_ck");
			assertThatThrownBy(() -> factura(s, 1, "F2030", 2, "RECTIFICATIVA", "null", "-37.19", "-7.81", "-45.00"))
					.hasMessageContaining("factura_total_signo_ck");

			factura(s, 1, "F2030", 2, "RECTIFICATIVA", "(select id from factura)", "-37.19", "-7.81", "-45.00"); // valida
			assertThat(contar(s, "select count(*) from factura")).isEqualTo(2);
		}
	}

	@Test
	void v5_baseMasCuotaTienenQueSumarElTotal_yElTipoSoloUnoDeLosDos() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			assertThatThrownBy(() -> factura(s, 1, "F2030", 1, "ORIGINAL", "null", "37.19", "7.80", "45.00"))
					.hasMessageContaining("factura_base_cuota_ck");
			assertThatThrownBy(() -> factura(s, 1, "F2030", 1, "ORIGINAL", "null", "38.00", "7.81", "45.00"))
					.hasMessageContaining("factura_base_cuota_ck");
			assertThatThrownBy(() -> factura(s, 1, "F2030", 1, "OTRA", "null", "37.19", "7.81", "45.00"))
					.hasMessageContaining("factura_tipo_ck");
			assertThat(contar(s, "select count(*) from factura")).isZero();
		}
	}

	@Test
	void v5_elNumeroDebeSerPositivo_yUnicoPorClubYSerie() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			segundaReserva(s);
			facturaOriginal(s, 1, 1);

			assertThatThrownBy(() -> facturaOriginal(s, 2, 1)).hasMessageContaining("factura_numero_uk");
			assertThatThrownBy(() -> facturaOriginal(s, 2, 0)).hasMessageContaining("factura_numero_ck");
			assertThatThrownBy(() -> facturaOriginal(s, 2, -3)).hasMessageContaining("factura_numero_ck");

			factura(s, 2, "F2031", 1, "ORIGINAL", "null", "37.19", "7.81", "45.00"); // otra serie (otro anio): el 1 vuelve a valer
			assertThat(contar(s, "select count(*) from factura")).isEqualTo(2);
		}
	}

	@Test
	void v5_unaReservaTieneComoMuchoUnaFacturaOriginal_yUnaFacturaComoMuchoUnaRectificativa() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			segundaReserva(s);
			facturaOriginal(s, 1, 1);

			assertThatThrownBy(() -> facturaOriginal(s, 1, 2)).hasMessageContaining("factura_original_reserva_uk");

			factura(s, 1, "F2030", 2, "RECTIFICATIVA", "(select id from factura where numero = 1)", "-37.19", "-7.81", "-45.00");
			assertThatThrownBy(() -> factura(s, 1, "F2030", 3, "RECTIFICATIVA", "(select id from factura where numero = 1)",
					"-37.19", "-7.81", "-45.00")).hasMessageContaining("factura_rectifica_uk");

			// otra reserva tiene su propia original y su propia rectificativa
			facturaOriginal(s, 2, 3);
			factura(s, 2, "F2030", 4, "RECTIFICATIVA", "(select id from factura where numero = 3)", "-37.19", "-7.81", "-45.00");
			assertThat(contar(s, "select count(*) from factura")).isEqualTo(4);
		}
	}

	// ---- inmutabilidad ----

	@Test
	void v5_elTriggerRechazaCualquierUpdateYDelete_yLaFilaQuedaIntacta() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			facturaOriginal(s, 1, 1);

			assertThatThrownBy(() -> s.execute("update factura set concepto = 'otro'"))
					.hasMessageContaining("Las facturas son inmutables");
			assertThatThrownBy(() -> s.execute("update factura set total = 1, base = 1, cuota_iva = 0"))
					.hasMessageContaining("Las facturas son inmutables");
			assertThatThrownBy(() -> s.execute("update factura set numero = 99 where id > 0"))
					.hasMessageContaining("Las facturas son inmutables");
			assertThatThrownBy(() -> s.execute("delete from factura")).hasMessageContaining("Las facturas son inmutables");
			assertThatThrownBy(() -> s.execute("delete from factura where id > 0"))
					.hasMessageContaining("Las facturas son inmutables");

			assertThat(contar(s, "select count(*) from factura where concepto = 'Pista' and numero = 1 and total = 45.00"))
					.isEqualTo(1);
		}
	}

	@Test
	void v5_unaActualizacionQueNoCambiaNadaTambienSeRechaza_yTruncateSiPermiteLimpiar() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			facturaOriginal(s, 1, 1);

			assertThatThrownBy(() -> s.execute("update factura set concepto = concepto"))
					.hasMessageContaining("Las facturas son inmutables");

			s.execute("truncate factura, factura_serie"); // el trigger no cubre TRUNCATE: asi limpian los tests
			assertThat(contar(s, "select count(*) from factura")).isZero();
		}
	}

	@Test
	void v5_elContadorDeSerieEsUnaFilaPorClubYAnio_quePuedeCrecer() throws Exception {
		migrarHastaV4ConDatos();
		migrarTodo();

		try (Connection c = DriverManager.getConnection(url, usuario, password); Statement s = c.createStatement()) {
			s.execute("insert into factura_serie (club_id, anio, ultimo_numero) select id, 2030, 0 from club where nombre = 'C'");
			s.execute("update factura_serie set ultimo_numero = ultimo_numero + 1");
			s.execute("insert into factura_serie (club_id, anio) select id, 2031 from club where nombre = 'C'");
			s.execute("insert into factura_serie (club_id, anio) select id, 2030 from club where nombre = 'D'");

			assertThatThrownBy(() -> s.execute("insert into factura_serie (club_id, anio) select id, 2030 from club where nombre = 'C'"))
					.hasMessageContaining("factura_serie_pk");
			assertThatThrownBy(() -> s.execute("update factura_serie set ultimo_numero = -1"))
					.hasMessageContaining("factura_serie_ultimo_ck");
			assertThat(contar(s, "select ultimo_numero from factura_serie where anio = 2030 and club_id = "
					+ "(select id from club where nombre = 'C')")).isEqualTo(1);
		}
	}
}
