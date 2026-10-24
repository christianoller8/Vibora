package com.vibora.factura;

import com.vibora.usuario.Rol;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrencia REAL de la facturacion (commits de verdad, una conexion por peticion). Nunca debe aparecer un 500, un
 * numero repetido ni un hueco en la numeracion.
 */
class FacturaConcurrenciaTests extends FacturaSinTransaccionBase {

	private static String cuerpo(MvcResult r) throws Exception {
		return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static List<Integer> estados(List<MvcResult> resultados) {
		return resultados.stream().map(r -> r.getResponse().getStatus()).toList();
	}

	// ---- (1) la misma reserva a la vez ----

	@RepeatedTest(3)
	void ochoPeticionesSimultaneasParaLaMismaReserva_unaSolaFactura_unaRespuesta201_yLasDemas200ConLaMismaFactura()
			throws Exception {
		Persona ana = persona("ana@vibora.com", Rol.JUGADOR, null);
		long reserva = reservaDe(ana, 0);

		List<Callable<MvcResult>> tareas = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			Persona quien = i % 2 == 0 ? ana : admin; // organizador y administrador a la vez
			tareas.add(() -> facturar(quien, reserva));
		}
		List<MvcResult> respuestas = lanzarALaVez(tareas);

		List<Integer> estados = estados(respuestas);
		assertThat(estados.stream().filter(s -> s == 201).count()).as("201: %s", estados).isEqualTo(1);
		assertThat(estados.stream().filter(s -> s == 200).count()).as("200: %s", estados).isEqualTo(7);
		Set<Long> ids = new HashSet<>();
		for (MvcResult r : respuestas) {
			ids.add(idDe(cuerpo(r)));
			assertThat(numeroDe(cuerpo(r))).isEqualTo(1);
		}
		assertThat(ids).as("todas devuelven la misma factura").hasSize(1);
		assertThat(contarFacturas()).isEqualTo(1);
		assertThat(numeros(anioActual())).containsExactly(1);
		assertThat(contador(anioActual())).isEqualTo(1);
	}

	// ---- (2) reservas distintas del mismo club a la vez ----

	@RepeatedTest(3)
	void diezReservasDistintasDelMismoClubFacturandoALaVez_losNumerosSonExactamenteDel1AlDiez_sinHuecosNiRepetidos()
			throws Exception {
		List<Persona> jugadores = new ArrayList<>();
		List<Long> reservas = new ArrayList<>();
		for (int k = 0; k < 10; k++) {
			Persona p = persona("jugador" + k + "@vibora.com", Rol.JUGADOR, null);
			jugadores.add(p);
			reservas.add(reservaDe(p, k));
		}

		List<Callable<MvcResult>> tareas = new ArrayList<>();
		for (int k = 0; k < 10; k++) {
			Persona p = jugadores.get(k);
			long reserva = reservas.get(k);
			tareas.add(() -> facturar(p, reserva));
		}
		List<MvcResult> respuestas = lanzarALaVez(tareas);

		assertThat(estados(respuestas)).as("sin 500 ni conflictos").containsOnly(201);
		Set<Integer> devueltos = new HashSet<>();
		for (MvcResult r : respuestas) {
			devueltos.add(numeroDe(cuerpo(r)));
		}
		assertThat(devueltos).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
		assertThat(numeros(anioActual())).isEqualTo(IntStream.rangeClosed(1, 10).boxed().toList());
		assertThat(contador(anioActual())).isEqualTo(10);
		assertThat(contarFacturas()).isEqualTo(10);
	}

	// ---- (3) cancelar y facturar a la vez ----

	@RepeatedTest(3)
	void cancelarYFacturarLaMismaReservaALaVez_siempreUnEstadoCoherente_sinHuecosEnLaNumeracion() throws Exception {
		List<Persona> jugadores = new ArrayList<>();
		List<Long> reservas = new ArrayList<>();
		for (int k = 0; k < 5; k++) {
			Persona p = persona("jugador" + k + "@vibora.com", Rol.JUGADOR, null);
			jugadores.add(p);
			reservas.add(reservaDe(p, k));
		}

		// por cada reserva, una factura y una cancelacion a la vez (10 peticiones simultaneas)
		List<Callable<MvcResult>> tareas = new ArrayList<>();
		for (int k = 0; k < 5; k++) {
			Persona p = jugadores.get(k);
			long reserva = reservas.get(k);
			tareas.add(() -> facturar(p, reserva));
			tareas.add(() -> cancelar(p, reserva));
		}
		List<MvcResult> respuestas = lanzarALaVez(tareas);

		for (int k = 0; k < 5; k++) {
			int factura = respuestas.get(2 * k).getResponse().getStatus();
			int cancelacion = respuestas.get(2 * k + 1).getResponse().getStatus();
			assertThat(cancelacion).as("cancelar %d", k).isEqualTo(200);
			assertThat(factura).as("facturar %d", k).isIn(201, 409);
			List<Map<String, Object>> filas = jdbc.queryForList("select tipo, total from factura where reserva_id = ? order by id",
					reservas.get(k));
			if (factura == 201) { // la factura gano la carrera: original y rectificativa que la anula
				assertThat(filas).extracting(f -> f.get("tipo")).as("reserva %d", k).containsExactly("ORIGINAL", "RECTIFICATIVA");
				assertThat(((BigDecimal) filas.get(0).get("total")).add((BigDecimal) filas.get(1).get("total")))
						.isEqualByComparingTo("0");
			} else { // la cancelacion gano: no hay ninguna factura
				assertThat(filas).as("reserva %d", k).isEmpty();
			}
		}
		long emitidas = contarFacturas();
		assertThat(numeros(anioActual())).as("numeracion sin huecos")
				.isEqualTo(IntStream.rangeClosed(1, (int) emitidas).boxed().toList());
		assertThat(contador(anioActual())).isEqualTo((int) emitidas);
		assertThat(jdbc.queryForObject("select coalesce(sum(total), 0) from factura", BigDecimal.class)).isEqualByComparingTo("0");
	}

	// ---- (4) cancelar-afectadas contra cancelaciones sueltas: sin interbloqueos ----

	@RepeatedTest(3)
	void cancelarAfectadasMientrasSeCancelanLasMismasReservasUnaAUna_sinInterbloqueoNi500_yTodasConSuRectificativa()
			throws Exception {
		List<Persona> jugadores = new ArrayList<>();
		List<Long> reservas = new ArrayList<>();
		for (int k = 0; k < 4; k++) {
			Persona p = persona("jugador" + k + "@vibora.com", Rol.JUGADOR, null);
			jugadores.add(p);
			long reserva = reservaDe(p, k);
			reservas.add(reserva);
			assertThat(facturar(p, reserva).getResponse().getStatus()).isEqualTo(201);
		}
		MvcResult bloqueo = mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos")
				.header("Authorization", admin.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\":\"Reparar el cristal\",\"desde\":\"" + casilla(0) + "\",\"hasta\":\"" + casilla(4) + "\"}"))
				.andReturn();
		assertThat(bloqueo.getResponse().getStatus()).isEqualTo(201);
		long idBloqueo = idDe(cuerpo(bloqueo));

		// el bloqueo cancela las cuatro mientras sus organizadores y el admin cancelan las mismas, cada una por su lado
		List<Callable<MvcResult>> tareas = new ArrayList<>();
		tareas.add(() -> mvc.perform(post("/api/clubes/" + club.getId() + "/pistas/" + pista.getId() + "/bloqueos/" + idBloqueo
				+ "/cancelar-afectadas").header("Authorization", admin.token())).andReturn());
		for (int k = 3; k >= 0; k--) { // en orden inverso al de bloqueo del lote, para provocar el cruce
			Persona quien = k % 2 == 0 ? jugadores.get(k) : admin;
			long reserva = reservas.get(k);
			tareas.add(() -> cancelar(quien, reserva));
		}
		List<MvcResult> respuestas = lanzarALaVez(tareas);

		assertThat(estados(respuestas)).as("sin 500 ni interbloqueos: %s", estados(respuestas)).containsOnly(200);
		assertThat(jdbc.queryForObject("select count(*) from reserva where estado = 'CANCELADA'", Long.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from factura where tipo = 'ORIGINAL'", Long.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from factura where tipo = 'RECTIFICATIVA'", Long.class)).isEqualTo(4);
		assertThat(numeros(anioActual())).isEqualTo(IntStream.rangeClosed(1, 8).boxed().toList());
		assertThat(jdbc.queryForObject("select sum(total) from factura", BigDecimal.class)).isEqualByComparingTo("0");
		assertThat(jdbc.queryForObject("select count(*) from notificacion where tipo = 'FACTURA_RECTIFICATIVA'", Long.class))
				.as("un aviso por rectificativa").isEqualTo(4);
	}
}
