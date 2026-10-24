package com.vibora.incidencia;

import com.vibora.pista.Pista;
import com.vibora.TestData;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/clubes/{id}/incidencias (visibilidad, filtros, orden, paginacion) y GET /api/incidencias/{id}. */
class IncidenciaConsultaTests extends IncidenciaApiTestBase {

	static int n(Long id) {
		return (int) (long) id;
	}

	// ---- matriz de visibilidad del listado ----

	@Test
	void listar_cadaRolVeSoloLoSuyo_yNingunClubFiltraAOtro() throws Exception {
		long asignadaAMant1 = incidenciaDe(jugador, "Asignada a Mario");
		asignar(admin, asignadaAMant1, mant1.id(), null).andExpect(status().isOk());
		long abiertaDelJugador = incidenciaDe(jugador, "Abierta del jugador");
		long delMonitor = incidenciaDe(monitor, "Del monitor");
		long delMant1 = incidenciaDe(mant1, "Reportada por Mario");
		reportar(mantB, clubB, "Del club B", "RED", null).andExpect(status().isCreated());

		// el administrador ve todo lo de SU club y nada de otro
		listar(admin, club).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(containsInAnyOrder(n(asignadaAMant1), n(abiertaDelJugador),
						n(delMonitor), n(delMant1))));
		// mantenimiento: las que tiene asignadas y las que el mismo reporto
		listar(mant1, club).andExpect(jsonPath("$[*].id").value(containsInAnyOrder(n(asignadaAMant1), n(delMant1))));
		listar(mant2, club).andExpect(jsonPath("$", empty()));
		// el resto, solo las que reporto
		listar(jugador, club).andExpect(jsonPath("$[*].id").value(containsInAnyOrder(n(asignadaAMant1), n(abiertaDelJugador))));
		listar(monitor, club).andExpect(jsonPath("$[*].id").value(contains(n(delMonitor))));
		// sin relacion: lista vacia (no 403: no se revela nada y es lo mismo que "no tengo ninguna")
		listar(otro, club).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
		// aislamiento entre clubes
		listar(adminB, club).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
		listar(mantB, club).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
		listar(adminB, clubB).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].titulo").value("Del club B"));
		listar(admin, clubB).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
	}

	@Test
	void listar_elResumenTraLosCamposDeLaLista_sinEventosNiDescripcion() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		asignar(admin, id, mant1.id(), "ALTA").andExpect(status().isOk());

		listar(admin, club).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].clubId").value(club.getId()))
				.andExpect(jsonPath("$[0].pistaId").value(pista.getId()))
				.andExpect(jsonPath("$[0].pistaNombre").value("Pista 1"))
				.andExpect(jsonPath("$[0].titulo").value("Red rota"))
				.andExpect(jsonPath("$[0].categoria").value("RED"))
				.andExpect(jsonPath("$[0].prioridad").value("ALTA"))
				.andExpect(jsonPath("$[0].estado").value("ASIGNADA"))
				.andExpect(jsonPath("$[0].reportadaPor.id").value(jugador.id()))
				.andExpect(jsonPath("$[0].asignadaA.id").value(mant1.id()))
				.andExpect(jsonPath("$[0].asignadaA.nombre").value("Mario Mantiene"))
				.andExpect(jsonPath("$[0].creadaEn").exists())
				.andExpect(jsonPath("$[0].eventos").doesNotExist())
				.andExpect(jsonPath("$[0].descripcion").doesNotExist());
	}

	@Test
	void listar_filtraPorEstadoPistaYAsignadaA() throws Exception {
		Pista pista2 = pistas.saveAndFlush(TestData.pista(club, "Pista 2"));
		long enPista1 = incidenciaDe(jugador, "En pista 1");
		long enPista2 = idDe(reportar(jugador, club, "En pista 2", "SUELO", pista2.getId()));
		long sinPista = idDe(reportar(jugador, club, "Sin pista", "OTRA", null));
		asignar(admin, enPista1, mant1.id(), null).andExpect(status().isOk());
		asignar(admin, enPista2, mant2.id(), null).andExpect(status().isOk());
		estado(mant2, enPista2, "EN_CURSO", null).andExpect(status().isOk());

		listar(admin, club, "estado", "ABIERTA").andExpect(jsonPath("$[*].id").value(contains(n(sinPista))));
		listar(admin, club, "estado", "ASIGNADA").andExpect(jsonPath("$[*].id").value(contains(n(enPista1))));
		listar(admin, club, "estado", "EN_CURSO").andExpect(jsonPath("$[*].id").value(contains(n(enPista2))));
		listar(admin, club, "estado", "CERRADA").andExpect(jsonPath("$", empty()));
		listar(admin, club, "pistaId", pista2.getId().toString()).andExpect(jsonPath("$[*].id").value(contains(n(enPista2))));
		listar(admin, club, "asignadaA", mant1.id().toString()).andExpect(jsonPath("$[*].id").value(contains(n(enPista1))));
		listar(admin, club, "estado", "EN_CURSO", "asignadaA", mant1.id().toString()).andExpect(jsonPath("$", empty()));
		// un filtro no amplia lo que se puede ver
		listar(jugador, club, "asignadaA", mant2.id().toString()).andExpect(jsonPath("$[*].id").value(contains(n(enPista2))));
		listar(monitor, club, "asignadaA", mant2.id().toString()).andExpect(jsonPath("$", empty()));
	}

	@Test
	void listar_ordenaPorPrioridadUrgentePrimero_yLuegoLasMasNuevas() throws Exception {
		long baja = incidenciaDe(jugador, "Baja");
		long mediaVieja = incidenciaDe(monitor, "Media vieja");
		long urgente = incidenciaDe(monitor, "Urgente");
		long alta = incidenciaDe(monitor, "Alta");
		reloj.fijar(reloj.instant().plusSeconds(60));
		long mediaNueva = incidenciaDe(monitor, "Media nueva");
		asignar(admin, baja, mant1.id(), "BAJA").andExpect(status().isOk());
		asignar(admin, urgente, mant1.id(), "URGENTE").andExpect(status().isOk());
		asignar(admin, alta, mant1.id(), "ALTA").andExpect(status().isOk());

		listar(admin, club).andExpect(jsonPath("$[*].id")
				.value(contains(n(urgente), n(alta), n(mediaNueva), n(mediaVieja), n(baja))));
	}

	@Test
	void listar_paginaYLimite_conLimiteMaximoDe100() throws Exception {
		for (int i = 1; i <= 5; i++) {
			incidenciaDe(monitor, "Aviso " + i);
		}
		// mismas prioridad y hora: gana el id mas reciente
		listar(admin, club, "limit", "2").andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].titulo").value("Aviso 5")).andExpect(jsonPath("$[1].titulo").value("Aviso 4"));
		listar(admin, club, "limit", "2", "page", "1").andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].titulo").value("Aviso 3"));
		listar(admin, club, "limit", "2", "page", "2").andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].titulo").value("Aviso 1"));
		listar(admin, club, "limit", "2", "page", "3").andExpect(jsonPath("$", empty()));
		listar(admin, club, "limit", "100000").andExpect(jsonPath("$.length()").value(5)); // se recorta a 100, no falla
		listar(admin, club).andExpect(jsonPath("$.length()").value(5)); // por defecto 20
	}

	@Test
	void listar_elLimitePorDefectoEs20() throws Exception {
		Persona gestor = persona("gestor@vibora.com", "Gema", "Gestora", com.vibora.usuario.Rol.ADMIN_CLUB, club);
		for (int i = 0; i < 25; i++) {
			incidencias.save(nueva("Aviso " + i, gestor));
		}
		refrescar();

		listar(admin, club).andExpect(jsonPath("$.length()").value(20));
		listar(admin, club, "limit", "100").andExpect(jsonPath("$.length()").value(25));
	}

	private Incidencia nueva(String titulo, Persona reporta) {
		Incidencia i = new Incidencia();
		i.setClub(club);
		i.setReportadaPor(reporta.usuario());
		i.setTitulo(titulo);
		i.setDescripcion("d");
		i.setCategoria(CategoriaIncidencia.OTRA);
		i.setCreadaEn(reloj.instant());
		i.setActualizadaEn(reloj.instant());
		return i;
	}

	@Test
	void listar_parametrosInvalidos400_clubInexistente404_sinToken401() throws Exception {
		listar(admin, club, "estado", "PAUSADA").andExpect(status().isBadRequest());
		listar(admin, club, "pistaId", "abc").andExpect(status().isBadRequest());
		listar(admin, club, "asignadaA", "abc").andExpect(status().isBadRequest());
		listar(admin, club, "page", "-1").andExpect(status().isBadRequest());
		listar(admin, club, "limit", "0").andExpect(status().isBadRequest());
		mvc.perform(get("/api/clubes/999999/incidencias").header("Authorization", admin.token()))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/clubes/" + club.getId() + "/incidencias")).andExpect(status().isUnauthorized());
	}

	@Test
	void listar_noHaceUnaConsultaPorIncidencia() throws Exception {
		Pista pista2 = pistas.saveAndFlush(TestData.pista(club, "Pista 2"));
		for (int i = 1; i <= 12; i++) {
			long id = idDe(reportar(List.of(monitor, jugador, admin).get(i % 3), club, "Aviso " + i, "RED", i % 2 == 0 ? pista.getId() : pista2.getId()));
			asignar(admin, id, i % 2 == 0 ? mant1.id() : mant2.id(), null).andExpect(status().isOk());
		}
		refrescar();

		Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		estadisticas.clear();
		listar(admin, club).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(12))
				.andExpect(jsonPath("$[0].asignadaA.nombre").exists()).andExpect(jsonPath("$[0].pistaNombre").exists());
		long consultas = estadisticas.getPrepareStatementCount();
		estadisticas.setStatisticsEnabled(false);

		// 1 (usuario autenticado) + 1 (club) + 1 (listado con pista, reportero y responsable); constante, no depende de N
		assertThat(consultas).isLessThanOrEqualTo(3);
	}

	// ---- detalle ----

	@Test
	void detalle_lovenElReporteroElAsignadoYElAdministrador_conElHistorialEnOrden() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		asignar(admin, id, mant1.id(), "ALTA").andExpect(status().isOk());
		estado(mant1, id, "EN_CURSO", "Voy para alla").andExpect(status().isOk());
		comentar(jugador, id, "Gracias").andExpect(status().isCreated());

		for (Persona p : List.of(jugador, mant1, admin)) {
			detalle(p, id).andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id))
					.andExpect(jsonPath("$.titulo").value("Red rota"))
					.andExpect(jsonPath("$.estado").value("EN_CURSO"))
					.andExpect(jsonPath("$.prioridad").value("ALTA"))
					.andExpect(jsonPath("$.asignadaA.id").value(mant1.id()))
					.andExpect(jsonPath("$.eventos[*].tipo")
							.value(contains("CREADA", "ASIGNADA", "PRIORIDAD", "ESTADO", "COMENTARIO")))
					.andExpect(jsonPath("$.eventos[3].estadoAnterior").value("ASIGNADA"))
					.andExpect(jsonPath("$.eventos[3].estadoNuevo").value("EN_CURSO"))
					.andExpect(jsonPath("$.eventos[3].texto").value("Voy para alla"))
					.andExpect(jsonPath("$.eventos[3].usuario.id").value(mant1.id()))
					.andExpect(jsonPath("$.eventos[4].texto").value("Gracias"));
		}
	}

	@Test
	void detalle_losDemasRecibenElMismo404_asiNoSeFiltranLosIds() throws Exception {
		long id = incidenciaDe(jugador, "Red rota");
		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());

		for (Persona p : List.of(otro, monitor, mant2, adminB, mantB)) {
			detalle(p, id).andExpect(status().isNotFound());
		}
		detalle(admin, 999_999L).andExpect(status().isNotFound()); // la misma respuesta que para una inexistente
		mvc.perform(get("/api/incidencias/" + id)).andExpect(status().isUnauthorized());
	}

	@Test
	void detalle_unMonitorSiVeLaQueElMismoReporto() throws Exception {
		long id = incidenciaDe(monitor, "Del monitor");

		detalle(monitor, id).andExpect(status().isOk());
		detalle(jugador, id).andExpect(status().isNotFound());
	}
}
