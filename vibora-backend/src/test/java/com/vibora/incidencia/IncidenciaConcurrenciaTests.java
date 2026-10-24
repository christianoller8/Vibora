package com.vibora.incidencia;

import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrencia REAL (commits de verdad, una conexion por peticion). Reglas que se prueban:
 * <ul>
 * <li>cambios de estado simultaneos: bloqueo optimista (@Version) -> uno gana, el otro 409, nunca 500;</li>
 * <li>limite de reportes: se serializa bloqueando la fila del reportero (SELECT ... FOR UPDATE) antes de contar;</li>
 * <li>asignar vs retirar personal / dos altas del mismo usuario: se serializan bloqueando la fila del usuario.</li>
 * </ul>
 */
class IncidenciaConcurrenciaTests extends IncidenciaSinTransaccionBase {

	private static long cuantos(List<Integer> estados, int codigo) {
		return estados.stream().filter(s -> s == codigo).count();
	}

	private List<IncidenciaEvento> historial(long id) {
		return eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(id);
	}

	private List<Notificacion> avisos(TipoNotificacion tipo) {
		return notificaciones.findAll().stream().filter(n -> n.getTipo() == tipo).toList();
	}

	// ---- (1) dos cambios de estado simultaneos sobre la misma incidencia ----

	@RepeatedTest(3)
	void dosCambiosDeEstadoIgualesAlaVez_unoGana_elOtro409_yElHistorialEsCoherente() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		assertThat(asignar(admin, id, mant1.id())).isEqualTo(200);
		long avisosAntes = avisos(TipoNotificacion.INCIDENCIA_ESTADO).size();

		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> estado(mant1, id, "EN_CURSO"),
				() -> estado(admin, id, "EN_CURSO")));

		assertThat(cuantos(estados, 200)).as("200: %s", estados).isEqualTo(1);
		assertThat(cuantos(estados, 409)).as("409: %s", estados).isEqualTo(1);
		Incidencia i = incidencias.findById(id).orElseThrow();
		assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.EN_CURSO);
		// un unico evento ESTADO ASIGNADA -> EN_CURSO (el perdedor no deja rastro)
		List<IncidenciaEvento> cambios = historial(id).stream().filter(e -> e.getTipo() == TipoEventoIncidencia.ESTADO).toList();
		assertThat(cambios).hasSize(1);
		assertThat(cambios.get(0).getEstadoAnterior()).isEqualTo("ASIGNADA");
		assertThat(cambios.get(0).getEstadoNuevo()).isEqualTo("EN_CURSO");
		assertThat(historial(id)).extracting(IncidenciaEvento::getTipo).containsExactly(TipoEventoIncidencia.CREADA,
				TipoEventoIncidencia.ASIGNADA, TipoEventoIncidencia.ESTADO);
		// y avisos solo de la transaccion ganadora
		assertThat(avisos(TipoNotificacion.INCIDENCIA_ESTADO).size() - avisosAntes).isEqualTo(3L); // reportero + 2 que no actuaron
	}

	@RepeatedTest(3)
	void cerrarYReabrirUnaResueltaAlaVez_soloUnaDeLasDosOperaciones() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		assertThat(asignar(admin, id, mant1.id())).isEqualTo(200);
		assertThat(estado(mant1, id, "EN_CURSO")).isEqualTo(200);
		assertThat(estado(mant1, id, "RESUELTA")).isEqualTo(200);

		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> estado(jugador, id, "CERRADA"),
				() -> estado(admin, id, "EN_CURSO")));

		assertThat(cuantos(estados, 200)).as("200: %s", estados).isEqualTo(1);
		assertThat(cuantos(estados, 409)).as("409: %s", estados).isEqualTo(1);
		Incidencia i = incidencias.findById(id).orElseThrow();
		List<IncidenciaEvento> h = historial(id);
		IncidenciaEvento ultimo = h.get(h.size() - 1);
		assertThat(h).hasSize(5); // CREADA, ASIGNADA y dos ESTADO previos + exactamente uno nuevo
		assertThat(ultimo.getEstadoNuevo()).isEqualTo(i.getEstado().name()); // el estado final es el del ultimo evento
		if (i.getEstado() == EstadoIncidencia.EN_CURSO) {
			assertThat(i.getResueltaEn()).isNull();
		} else {
			assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.CERRADA);
			assertThat(i.getResueltaEn()).isNotNull();
		}
	}

	// ---- (2) dos administradores asignan a personas distintas a la vez ----

	@RepeatedTest(3)
	void dosAdministradoresAsignanAPersonasDistintasAlaVez_quedaUnGanadorCoherente() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");

		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> asignar(admin, id, mant1.id()),
				() -> asignar(admin2, id, mant2.id())));

		assertThat(estados).as("estados: %s", estados).allMatch(s -> s == 200 || s == 409);
		long exitos = cuantos(estados, 200);
		assertThat(exitos).as("alguno gana: %s", estados).isGreaterThanOrEqualTo(1);
		Incidencia i = incidencias.findById(id).orElseThrow();
		assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.ASIGNADA);
		List<IncidenciaEvento> asignaciones = historial(id).stream().filter(e -> e.getTipo() == TipoEventoIncidencia.ASIGNADA).toList();
		assertThat(asignaciones).hasSize((int) exitos); // ni eventos huerfanos del perdedor ni exitos sin evento
		// el responsable final es el de la ULTIMA asignacion registrada
		Long ganador = i.getAsignadaA().getId();
		String nombreGanador = usuarios.findById(ganador).orElseThrow().nombreCompleto();
		assertThat(asignaciones.get(asignaciones.size() - 1).getTexto()).contains(nombreGanador);
		// un aviso de asignacion por asignacion confirmada, y el ganador final tiene el suyo
		List<Notificacion> asignadas = avisos(TipoNotificacion.INCIDENCIA_ASIGNADA);
		assertThat(asignadas).hasSize((int) exitos);
		assertThat(asignadas).anySatisfy(n -> assertThat(n.getUsuario().getId()).isEqualTo(ganador));
	}

	// ---- (3) el limite de reportes aguanta las rafagas ----

	@RepeatedTest(3)
	void ochoReportesAlaVezCercaDelLimite_cuatroPrevios_entraExactamenteUno() throws Exception {
		for (int i = 1; i <= 4; i++) {
			incidenciaDe(jugador, "Previa " + i);
		}

		List<Callable<Integer>> tareas = new ArrayList<>();
		for (int i = 1; i <= 8; i++) {
			String titulo = "Rafaga " + i;
			tareas.add(() -> reportar(jugador, titulo));
		}
		List<Integer> estados = lanzarALaVez(tareas);

		assertThat(cuantos(estados, 201)).as("201: %s", estados).isEqualTo(1);
		assertThat(cuantos(estados, 409)).as("409: %s", estados).isEqualTo(7);
		assertThat(incidencias.count()).isEqualTo(5);
		assertThat(eventos.count()).isEqualTo(5); // un evento CREADA por incidencia realmente creada
		assertThat(avisos(TipoNotificacion.INCIDENCIA_NUEVA)).hasSize(5 * 2); // 2 administradores
	}

	// ---- asignar vs retirar a la persona de mantenimiento ----

	@RepeatedTest(3)
	void asignarAUnaPersonaMientrasSeLaRetira_nuncaQuedaAsignadaAAlguienQueYaNoEsDelClub() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");

		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> asignar(admin, id, mant1.id()),
				() -> mvc.perform(delete("/api/clubes/" + club.getId() + "/personal/" + mant1.id())
						.header("Authorization", admin2.token())).andReturn().getResponse().getStatus()));

		assertThat(estados.get(1)).as("retirada: %s", estados).isEqualTo(204);
		assertThat(estados.get(0)).as("asignacion: %s", estados).isIn(200, 400, 409);
		Usuario m = usuarios.findById(mant1.id()).orElseThrow();
		assertThat(m.getRol()).isEqualTo(Rol.JUGADOR);
		assertThat(m.getClub()).isNull();
		Incidencia i = incidencias.findById(id).orElseThrow();
		// ni asignada ni en curso a nadie sin club: o nunca se asigno, o se asigno y la retirada la devolvio a ABIERTA
		assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.ABIERTA);
		assertThat(i.getAsignadaA()).isNull();
	}

	// ---- alta simultanea de la misma persona ----

	@RepeatedTest(3)
	void dosAdministradoresAnadenALaMismaPersonaAlaVez_unoGana_elOtro409() throws Exception {
		List<Integer> estados = lanzarALaVez(List.<Callable<Integer>>of(
				() -> anadir(admin, "jugador@vibora.com", "MONITOR"),
				() -> anadir(admin2, "jugador@vibora.com", "MANTENIMIENTO")));

		assertThat(cuantos(estados, 201)).as("201: %s", estados).isEqualTo(1);
		assertThat(cuantos(estados, 409)).as("409: %s", estados).isEqualTo(1);
		assertThat(avisos(TipoNotificacion.PERSONAL_ANADIDO)).hasSize(1);
		Usuario u = usuarios.findById(jugador.id()).orElseThrow();
		assertThat(u.getRol()).isIn(Rol.MONITOR, Rol.MANTENIMIENTO);
		assertThat(u.getClub()).isNotNull();
	}

	private int anadir(Persona actor, String email, String rol) throws Exception {
		return mvc.perform(post("/api/clubes/" + club.getId() + "/personal").header("Authorization", actor.token())
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"rol\":\"%s\"}".formatted(email, rol))).andReturn().getResponse().getStatus();
	}
}
