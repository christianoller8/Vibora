package com.vibora.factura;

import com.vibora.usuario.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Prueba DETERMINISTA del orden de bloqueos. El contador de facturas es el ultimo bloqueo que toma una transaccion;
 * para que sea cierto tambien en "cancelar las afectadas" (que cancela varias reservas en una transaccion) el lote
 * tiene que bloquear TODAS las reservas ANTES de cancelar la primera. Si fuese reserva a reserva, el lote ya tendria el
 * contador (por la rectificativa de la primera) cuando se quedase esperando a la segunda, y quien tuviese la segunda
 * y quisiera el contador cerraria un ciclo.
 * <p>
 * El escenario se monta con una conexion de test que hace de "otra transaccion": toma la fila de la SEGUNDA reserva
 * y despues pide el contador. Con el lote correcto el contador esta libre y todo termina; con el lote intercalado
 * PostgreSQL detecta un interbloqueo (40P01) y aborta a una de las dos.
 */
class FacturaInterbloqueoTests extends FacturaSinTransaccionBase {

	@Autowired DataSource dataSource;

	/**
	 * Espera, sondeando, a que EXISTA un backend bloqueado por la sesion {@code c} (pg_blocking_pids): como esa sesion
	 * solo tiene la fila de la segunda reserva, ese backend es el lote esperando a esa fila, y no cualquier otro
	 * esperador. Si el lote ya habia procesado la primera reserva, lo hizo ANTES de llegar a esta espera, asi que no hace
	 * falta ningun margen fijo. Falla pronto si el lote termina sin esperar.
	 */
	private void esperarAQueElLoteEsteBloqueadoPor(Connection c, Future<MvcResult> lote) throws Exception {
		long limite = System.currentTimeMillis() + 30_000;
		try (Statement s = c.createStatement()) {
			ResultSet pid = s.executeQuery("select pg_backend_pid()");
			pid.next();
			int miPid = pid.getInt(1);
			try (PreparedStatement bloqueados = c.prepareStatement(
					"select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))")) {
				while (System.currentTimeMillis() < limite) {
					if (lote.isDone()) {
						throw new AssertionError("el lote termino sin esperar a la fila de la segunda reserva");
					}
					bloqueados.setInt(1, miPid);
					ResultSet rs = bloqueados.executeQuery();
					rs.next();
					if (rs.getInt(1) > 0) {
						return;
					}
					Thread.sleep(25);
				}
			}
		}
		throw new AssertionError("el lote no llego a esperar a la segunda reserva");
	}

	@Test
	void elLoteDeCancelarAfectadasBloqueaTodasLasReservasAntesDeTomarElContador() throws Exception {
		Persona ana = persona("ana@vibora.com", Rol.JUGADOR, null);
		Persona beto = persona("beto@vibora.com", Rol.JUGADOR, null);
		long primera = reservaDe(ana, 0);
		long segunda = reservaDe(beto, 1);
		assertThat(facturar(ana, primera).getResponse().getStatus()).isEqualTo(201);
		assertThat(facturar(beto, segunda).getResponse().getStatus()).isEqualTo(201);
		assertThat(primera).isLessThan(segunda); // el lote las bloquea en orden ascendente de id
		MvcResult bloqueo = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos")
				.header("Authorization", admin.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\":\"Reparar el cristal\",\"desde\":\"" + casilla(0) + "\",\"hasta\":\"" + casilla(2) + "\"}"))
				.andReturn();
		long idBloqueo = idDe(bloqueo.getResponse().getContentAsString());

		ExecutorService pool = Executors.newSingleThreadExecutor();
		boolean contadorObtenido;
		Future<MvcResult> lote;
		try (Connection otra = dataSource.getConnection()) {
			otra.setAutoCommit(false);
			// la "otra transaccion" tiene la fila de la segunda reserva...
			try (PreparedStatement p = otra.prepareStatement("select id from reserva where id = ? for update")) {
				p.setLong(1, segunda);
				p.executeQuery();
			}
			// ...cuando el lote empieza y se queda esperando a esa fila
			lote = pool.submit((Callable<MvcResult>) () -> mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/"
					+ pista.getId() + "/bloqueos/" + idBloqueo + "/cancelar-afectadas")
					.header("Authorization", admin.token())).andReturn());
			esperarAQueElLoteEsteBloqueadoPor(otra, lote);

			// ahora la "otra transaccion" pide el contador de facturas, como haria una cancelacion suelta de esa reserva
			try (Statement s = otra.createStatement()) {
				s.execute("set local lock_timeout = '8s'");
				s.execute("select ultimo_numero from factura_serie where club_id = " + club.getId() + " for update");
				contadorObtenido = true;
			} catch (SQLException ex) {
				contadorObtenido = false;
				otra.rollback();
				assertThat(ex.getSQLState()).as("deberia ser un interbloqueo (40P01) y no un fallo cualquiera: " + ex.getMessage())
						.isEqualTo("40P01");
			}
			if (contadorObtenido) {
				otra.commit(); // suelta la segunda reserva: el lote sigue
			}
		}

		MvcResult resultado;
		try {
			resultado = lote.get(30, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}
		assertThat(contadorObtenido).as("el contador tiene que estar libre mientras el lote espera a una reserva").isTrue();
		assertThat(resultado.getResponse().getStatus()).as(resultado.getResponse().getContentAsString()).isEqualTo(200);
		assertThat(jdbc.queryForObject("select count(*) from reserva where estado = 'CANCELADA'", Long.class)).isEqualTo(2);
		assertThat(numeros(anioActual())).isEqualTo(List.of(1, 2, 3, 4));
	}
}
