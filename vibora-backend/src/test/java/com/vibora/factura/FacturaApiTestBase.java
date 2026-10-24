package com.vibora.factura;

import com.vibora.ApiTestBase;
import com.vibora.RelojMutable;
import com.vibora.RelojTestConfig;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TarifaFranjaRepository;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de facturacion (rollback por test). Reloj en 2030-01-01T00:00Z; las reservas son los lunes (el
 * lunes 2030-01-07 es el de siempre) y con la tarifa plana de 30.00 la hora una reserva de 90 minutos cuesta 45.00,
 * que con IVA al 21 % da base 37.19 y cuota 7.81. Todo pasa por HTTP; JdbcTemplate solo se usa para comprobar la BD.
 */
@Import(RelojTestConfig.class)
abstract class FacturaApiTestBase extends ApiTestBase {

	static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	static final LocalDate LUNES = LocalDate.of(2030, 1, 7);
	static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
	static final String NIF_CLUB = "B12345674";

	@Autowired RelojMutable reloj;
	@Autowired PistaRepository pistas;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired JdbcTemplate jdbc;
	@Autowired EntityManager em;

	Club club;
	Pista pista;
	Jug admin;

	/** Usuario con su token. */
	record Jug(Usuario usuario, String token) {
		Long id() {
			return usuario.getId();
		}
	}

	@BeforeEach
	void prepararFacturacion() throws Exception {
		reloj.fijar(RelojTestConfig.AHORA_INICIAL);
		club = crearClub("Club A");
		pista = pistas.saveAndFlush(TestData.pista(club, "Pista 1"));
		tarifaPlanaLosLunes(club, "30.00");
		admin = administrador(club, "admin@vibora.com");
	}

	// ---- datos ----

	static Instant local(LocalDate fecha, int hora, int minuto) {
		return fecha.atTime(hora, minuto).atZone(MADRID).toInstant();
	}

	static Instant lunes(int hora, int minuto) {
		return local(LUNES, hora, minuto);
	}

	TarifaFranja tarifaPlanaLosLunes(Club c, String precioHora) {
		TarifaFranja t = TestData.tarifa(c, DayOfWeek.MONDAY, TipoTarifa.NORMAL);
		t.setHoraInicio(LocalTime.of(8, 0));
		t.setHoraFin(LocalTime.of(23, 0));
		t.setPrecioHora(new BigDecimal(precioHora));
		return tarifas.saveAndFlush(t);
	}

	Jug jugador(String email, String nombre, String apellidos) throws Exception {
		Usuario u = TestData.usuario(email, Rol.JUGADOR, null);
		u.setNombre(nombre);
		u.setApellidos(apellidos);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u = usuarios.saveAndFlush(u);
		return new Jug(u, bearer(email));
	}

	/** Jugador "Ana García" con el email dado. */
	Jug ana(String email) throws Exception {
		return jugador(email, "Ana", "García");
	}

	Jug administrador(Club c, String email) throws Exception {
		Usuario u = crearUsuario(email, Rol.ADMIN_CLUB, c);
		return new Jug(u, bearer(email));
	}

	Jug personal(Club c, String email, Rol rol) throws Exception {
		Usuario u = crearUsuario(email, rol, c);
		return new Jug(u, bearer(email));
	}

	/** Datos fiscales validos del club (por la API, como administrador). */
	void configurarDatosFiscales(Jug quien, Club c) throws Exception {
		datosFiscales(quien, c, """
				{"razonSocial":"Pádel Víbora S.L.","nif":"%s","direccionFiscal":"Calle Álvaro Cunqueiro 5, 36001 Pontevedra"}
				""".formatted(NIF_CLUB)).andExpect(status().isOk());
	}

	// ---- llamadas HTTP ----

	ResultActions datosFiscales(Jug quien, Club c, String cuerpo) throws Exception {
		return mvc.perform(put("/api/clubes/" + c.getId() + "/datos-fiscales").header("Authorization", quien.token())
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	ResultActions verDatosFiscales(Jug quien, Club c) throws Exception {
		return mvc.perform(get("/api/clubes/" + c.getId() + "/datos-fiscales").header("Authorization", quien.token()));
	}

	ResultActions reservar(Jug j, Pista p, Instant inicio) throws Exception {
		return mvc.perform(post("/api/clubes/" + p.getClub().getId() + "/pistas/" + p.getId() + "/reservas")
				.header("Authorization", j.token()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"inicio\":\"" + inicio + "\"}"));
	}

	long idDe(ResultActions resultado) throws Exception {
		return idDe(resultado.andReturn().getResponse().getContentAsString());
	}

	static long idDe(String cuerpo) {
		Matcher m = ID.matcher(cuerpo);
		assertThat(m.find()).as(cuerpo).isTrue();
		return Long.parseLong(m.group(1));
	}

	long reservaCreada(Jug j, Instant inicio) throws Exception {
		return idDe(reservar(j, pista, inicio).andExpect(status().isCreated()));
	}

	/** Reserva el lunes de siempre a la hora indicada (casillas de 90 minutos desde las 08:00: 08:00, 09:30, 11:00...). */
	long reservaEnElLunes(Jug j, int hora, int minuto) throws Exception {
		return reservaCreada(j, lunes(hora, minuto));
	}

	ResultActions facturar(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/factura").header("Authorization", j.token()));
	}

	ResultActions facturar(Jug j, long reservaId, String cuerpo) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/factura").header("Authorization", j.token())
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** Emite la factura (debe salir bien) y devuelve su id. */
	long facturaEmitida(Jug j, long reservaId) throws Exception {
		return idDe(facturar(j, reservaId).andExpect(status().is2xxSuccessful()));
	}

	ResultActions cancelar(Jug j, long reservaId) throws Exception {
		return mvc.perform(post("/api/reservas/" + reservaId + "/cancelar").header("Authorization", j.token()));
	}

	ResultActions facturasDeReserva(Jug j, long reservaId) throws Exception {
		return mvc.perform(get("/api/reservas/" + reservaId + "/facturas").header("Authorization", j.token()));
	}

	ResultActions factura(Jug j, long facturaId) throws Exception {
		return mvc.perform(get("/api/facturas/" + facturaId).header("Authorization", j.token()));
	}

	ResultActions pdf(Jug j, long facturaId) throws Exception {
		return mvc.perform(get("/api/facturas/" + facturaId + "/pdf").header("Authorization", j.token()));
	}

	ResultActions misFacturas(Jug j) throws Exception {
		return mvc.perform(get("/api/facturas").header("Authorization", j.token()));
	}

	ResultActions facturasDelClub(Jug j, Club c, String... paramsClaveValor) throws Exception {
		var peticion = get("/api/clubes/" + c.getId() + "/facturas").header("Authorization", j.token());
		for (int i = 0; i < paramsClaveValor.length; i += 2) {
			peticion.param(paramsClaveValor[i], paramsClaveValor[i + 1]);
		}
		return mvc.perform(peticion);
	}

	ResultActions notificaciones(Jug j) throws Exception {
		return mvc.perform(get("/api/notificaciones").header("Authorization", j.token()));
	}

	// ---- estado de la BD ----

	/** Vacia la sesion de test: tras un UPDATE por JDBC, las entidades ya cargadas estarian desactualizadas. */
	void refrescar() {
		em.flush();
		em.clear();
	}

	long contarFacturas() {
		em.flush();
		return jdbc.queryForObject("select count(*) from factura", Long.class);
	}

	long contarFacturas(Club c) {
		em.flush();
		return jdbc.queryForObject("select count(*) from factura where club_id = ?", Long.class, c.getId());
	}

	/** Ultimo numero usado por la serie del club y anio, o 0 si la serie no existe. */
	int contador(Club c, int anio) {
		em.flush();
		List<Integer> filas = jdbc.queryForList("select ultimo_numero from factura_serie where club_id = ? and anio = ?",
				Integer.class, c.getId(), anio);
		return filas.isEmpty() ? 0 : filas.get(0);
	}

	/** Numeros completos de las facturas del club en orden de emision (id). */
	List<String> numerosEmitidos(Club c) {
		em.flush();
		return jdbc.queryForList("select serie || '-' || lpad(numero::text, 4, '0') from factura where club_id = ? order by id",
				String.class, c.getId());
	}
}
