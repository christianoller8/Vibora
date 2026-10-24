package com.vibora.incidencia;

import com.vibora.ApiTestBase;
import com.vibora.almacen.AlmacenFicheros;
import com.vibora.RelojMutable;
import com.vibora.RelojTestConfig;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.NotificacionRepository;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.partido.ParticipanteRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de personal e incidencias (rollback por test). Reloj fijado en 2030-01-01T00:00Z (todos los
 * eventos de un test comparten instante: el orden del historial lo desempata el id). Club A con su administrador,
 * dos de mantenimiento, un monitor, un jugador que ha jugado en el club y otro que no; club B con los suyos.
 * Todo pasa por HTTP; los repositorios solo se usan para montar datos y comprobar la BD.
 */
@Import(RelojTestConfig.class)
abstract class IncidenciaApiTestBase extends ApiTestBase {

	static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

	/** Carpeta temporal compartida por todos los tests de esta base: las fotos se borran solas al hacer rollback. */
	static final Path ALMACEN_DE_TEST = carpetaTemporal();

	private static Path carpetaTemporal() {
		try {
			return Files.createTempDirectory("vibora-test-almacen-");
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@DynamicPropertySource
	static void propiedadesDelAlmacen(DynamicPropertyRegistry registro) {
		registro.add("vibora.almacen.directorio", () -> ALMACEN_DE_TEST.toString());
	}

	@Autowired AlmacenFicheros almacen;
	@Autowired IncidenciaFotoRepository fotos;
	@Autowired RelojMutable reloj;
	@Autowired PistaRepository pistas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;
	@Autowired IncidenciaRepository incidencias;
	@Autowired IncidenciaEventoRepository eventos;
	@Autowired NotificacionRepository notificaciones;
	@Autowired EntityManager em;

	Club club;
	Club clubB;
	Pista pista;
	Pista pistaB;
	int reservasCreadas;

	/** Usuario con su token (el token se emite en el momento de crearlo: refleja su rol de entonces). */
	record Persona(Usuario usuario, String token) {
		Long id() {
			return usuario.getId();
		}
	}

	Persona admin;
	Persona mant1;
	Persona mant2;
	Persona monitor;
	Persona jugador; // ha reservado en el club A
	Persona otro; // sin ninguna relacion con el club A
	Persona adminB;
	Persona mantB;

	@BeforeEach
	void prepararIncidencias() throws Exception {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
		club = crearClub("Club A");
		clubB = crearClub("Club B");
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		pistaB = pistas.saveAndFlush(TestData.pista(clubB, "Pista B1"));
		admin = persona("admin@vibora.com", "Alba", "Admin", Rol.ADMIN_CLUB, club);
		mant1 = persona("mant1@vibora.com", "Mario", "Mantiene", Rol.MANTENIMIENTO, club);
		mant2 = persona("mant2@vibora.com", "Marta", "Arregla", Rol.MANTENIMIENTO, club);
		monitor = persona("monitor@vibora.com", "Moni", "Tor", Rol.MONITOR, club);
		jugador = persona("jugador@vibora.com", "Juan", "Jugador", Rol.JUGADOR, null);
		reservaDe(jugador, pista);
		otro = persona("otro@vibora.com", "Olga", "Otra", Rol.JUGADOR, null);
		adminB = persona("adminb@vibora.com", "Berta", "AdminB", Rol.ADMIN_CLUB, clubB);
		mantB = persona("mantb@vibora.com", "Bruno", "MantB", Rol.MANTENIMIENTO, clubB);
	}

	// ---- datos ----

	Persona persona(String email, String nombre, String apellidos, Rol rol, Club c) throws Exception {
		Usuario u = TestData.usuario(email, rol, c);
		u.setNombre(nombre);
		u.setApellidos(apellidos);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Persona(u, bearer(email));
	}

	/** Reserva (confirmada, sin solapes) de la persona en la pista; el organizador es el puesto 1. */
	Reserva reservaDe(Persona p, Pista pistaReservada) {
		Instant inicio = TestData.T0.plus(Duration.ofHours(3L * reservasCreadas++));
		Reserva r = TestData.reserva(pistaReservada, p.usuario(), inicio, inicio.plus(Duration.ofMinutes(90)),
				EstadoReserva.CONFIRMADA);
		r = reservas.saveAndFlush(r);
		participantes.saveAndFlush(TestData.participante(r, p.usuario(), 1));
		return r;
	}

	// ---- llamadas HTTP ----

	static String json(String... paresClaveValor) {
		StringBuilder sb = new StringBuilder("{");
		for (int i = 0; i < paresClaveValor.length; i += 2) {
			if (paresClaveValor[i + 1] == null) {
				continue;
			}
			if (sb.length() > 1) {
				sb.append(',');
			}
			sb.append('"').append(paresClaveValor[i]).append("\":").append(paresClaveValor[i + 1]);
		}
		return sb.append('}').toString();
	}

	static String texto(String valor) {
		return valor == null ? null : "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	ResultActions reportar(Persona p, Club c, String titulo, String categoria, Long pistaId) throws Exception {
		return mvc.perform(post("/api/clubes/" + c.getId() + "/incidencias").header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("titulo", texto(titulo), "descripcion", texto("Descripcion de " + titulo),
						"categoria", texto(categoria), "pistaId", pistaId == null ? null : pistaId.toString())));
	}

	/** Incidencia nueva del jugador en el club A (pista A). Devuelve su id. */
	long incidenciaDe(Persona p, String titulo) throws Exception {
		return idDe(reportar(p, club, titulo, "RED", pista.getId()).andExpect(status().isCreated()));
	}

	long incidenciaDe(Persona p) throws Exception {
		return incidenciaDe(p, "Red rota");
	}

	ResultActions asignar(Persona p, long id, Long usuarioId, String prioridad) throws Exception {
		return mvc.perform(post("/api/incidencias/" + id + "/asignar").header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("usuarioId", usuarioId == null ? null : usuarioId.toString(), "prioridad", texto(prioridad))));
	}

	ResultActions estado(Persona p, long id, String estado, String comentario) throws Exception {
		return mvc.perform(post("/api/incidencias/" + id + "/estado").header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("estado", texto(estado), "comentario", texto(comentario))));
	}

	ResultActions comentar(Persona p, long id, String textoComentario) throws Exception {
		return mvc.perform(post("/api/incidencias/" + id + "/comentarios").header("Authorization", p.token())
				.contentType(MediaType.APPLICATION_JSON).content(json("texto", texto(textoComentario))));
	}

	ResultActions detalle(Persona p, long id) throws Exception {
		return mvc.perform(get("/api/incidencias/" + id).header("Authorization", p.token()));
	}

	ResultActions listar(Persona p, Club c, String... parametros) throws Exception {
		var peticion = get("/api/clubes/" + c.getId() + "/incidencias").header("Authorization", p.token());
		for (int i = 0; i < parametros.length; i += 2) {
			peticion.param(parametros[i], parametros[i + 1]);
		}
		return mvc.perform(peticion);
	}

	long idDe(ResultActions resultado) throws Exception {
		Matcher m = ID.matcher(resultado.andReturn().getResponse().getContentAsString());
		assertThat(m.find()).isTrue();
		return Long.parseLong(m.group(1));
	}

	/** Lleva una incidencia nueva del jugador hasta el estado indicado por el camino legal. */
	long incidenciaEn(EstadoIncidencia destino) throws Exception {
		long id = incidenciaDe(jugador);
		if (destino == EstadoIncidencia.ABIERTA) {
			return id;
		}
		if (destino == EstadoIncidencia.RECHAZADA) {
			estado(admin, id, "RECHAZADA", "No procede").andExpect(status().isOk());
			return id;
		}
		asignar(admin, id, mant1.id(), null).andExpect(status().isOk());
		if (destino == EstadoIncidencia.ASIGNADA) {
			return id;
		}
		estado(mant1, id, "EN_CURSO", null).andExpect(status().isOk());
		if (destino == EstadoIncidencia.EN_CURSO) {
			return id;
		}
		estado(mant1, id, "RESUELTA", null).andExpect(status().isOk());
		if (destino == EstadoIncidencia.RESUELTA) {
			return id;
		}
		estado(admin, id, "CERRADA", null).andExpect(status().isOk());
		return id;
	}

	// ---- estado de la BD ----

	void refrescar() {
		em.flush();
		em.clear();
	}

	Incidencia incidencia(long id) {
		refrescar();
		return incidencias.findById(id).orElseThrow();
	}

	List<IncidenciaEvento> historial(long id) {
		refrescar();
		return eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(id);
	}

	List<Notificacion> notifsDe(Persona p, TipoNotificacion tipo) {
		refrescar();
		return notificaciones.findAll().stream()
				.filter(n -> n.getUsuario().getId().equals(p.id()) && (tipo == null || n.getTipo() == tipo))
				.sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
	}

	long totalNotifs() {
		refrescar();
		return notificaciones.count();
	}
}
