package com.vibora.auth;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests extends PostgresTestBase {

	private static final String PASSWORD = "contraseña-segura";

	@Autowired MockMvc mvc;
	@Autowired UsuarioRepository usuarios;
	@Autowired ClubRepository clubs;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired JwtService jwtService;
	@jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
	@Value("${vibora.jwt.secret}") String secret;

	private String registroJson(String email, String password, String nombre, String apellidos) {
		return """
				{"email":"%s","password":"%s","nombre":"%s","apellidos":"%s"}
				""".formatted(email, password, nombre, apellidos);
	}

	private String loginJson(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}

	private Usuario crearUsuario(String email, Rol rol, Club club, boolean activo) {
		Usuario u = TestData.usuario(email, rol, club);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private String tokenDe(String email) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(email, PASSWORD))).andExpect(status().isOk()).andReturn();
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(r.getResponse().getContentAsString());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	// ---- Registro ----

	@Test
	void registro_ok_creaJugadorConHashBcryptYNoExponeElHash() throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registroJson("nuevo@vibora.com", PASSWORD, "Ana", "García")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("nuevo@vibora.com"))
				.andExpect(jsonPath("$.rol").value("JUGADOR"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();

		assertThat(r.getResponse().getContentAsString()).doesNotContain(PASSWORD).doesNotContain("$2");
		Usuario guardado = usuarios.findByEmail("nuevo@vibora.com").orElseThrow();
		assertThat(guardado.getRol()).isEqualTo(Rol.JUGADOR);
		assertThat(guardado.getClub()).isNull();
		assertThat(guardado.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, guardado.getPasswordHash())).isTrue();
	}

	@Test
	void registro_ignoraRolYClubEnviadosPorElCliente() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"intruso@vibora.com","password":"contraseña-segura","apellidos":"X",
								 "rol":"ADMIN_CLUB","clubId":1,"activo":false}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rol").value("JUGADOR"));

		Usuario guardado = usuarios.findByEmail("intruso@vibora.com").orElseThrow();
		assertThat(guardado.getRol()).isEqualTo(Rol.JUGADOR);
		assertThat(guardado.getClub()).isNull();
		assertThat(guardado.isActivo()).isTrue();
	}

	@Test
	void registro_emailDuplicado_devuelve409() throws Exception {
		crearUsuario("dup@vibora.com", Rol.JUGADOR, null, true);

		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registroJson("dup@vibora.com", PASSWORD, "Ana", "García")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	void registro_emailDuplicadoSinImportarMayusculas_devuelve409() throws Exception {
		crearUsuario("dup@vibora.com", Rol.JUGADOR, null, true);

		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registroJson("DUP@Vibora.com", PASSWORD, "Ana", "García")))
				.andExpect(status().isConflict());
	}

	@Test
	void registro_datosInvalidos_devuelve400() throws Exception {
		String[] cuerposInvalidos = {
				registroJson("no-es-un-email", PASSWORD, "Ana", "García"),
				registroJson("", PASSWORD, "Ana", "García"),
				registroJson("a@vibora.com", "corta", "Ana", "García"),
				registroJson("a@vibora.com", PASSWORD, "Ana", ""),
				registroJson("a@vibora.com", "x".repeat(73), "Ana", "García"),
		};
		for (String cuerpo : cuerposInvalidos) {
			mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").isString());
		}
		assertThat(usuarios.count()).isZero();
	}

	// ---- Login ----

	@Test
	void login_ok_devuelveTokenConClaimsCorrectos() throws Exception {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("ana@vibora.com", PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.expiraEn").exists());

		var jwt = jwtService.decodificar(tokenDe("ana@vibora.com"));
		assertThat(jwt.getClaimAsString("rol")).isEqualTo("JUGADOR");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("vibora");
		assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
	}

	@Test
	void login_emailEnOtraCapitalizacion_funciona() throws Exception {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("  ANA@vibora.com ", PASSWORD)))
				.andExpect(status().isOk());
	}

	@Test
	void login_fallos_devuelven401ConElMismoCuerpo() throws Exception {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);
		crearUsuario("baja@vibora.com", Rol.JUGADOR, null, false);

		String passwordMal = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("ana@vibora.com", "incorrecta-123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").isString())
				.andReturn().getResponse().getContentAsString();
		String emailDesconocido = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("nadie@vibora.com", PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		String inactivo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("baja@vibora.com", PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(emailDesconocido).isEqualTo(passwordMal);
		assertThat(inactivo).isEqualTo(passwordMal);
	}

	@Test
	void login_cuerpoInvalido_devuelve400() throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("", "")))
				.andExpect(status().isBadRequest());
	}

	// ---- /me y rutas protegidas ----

	@Test
	void me_conTokenValido_devuelveElUsuario() throws Exception {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenDe("ana@vibora.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ana@vibora.com"))
				.andExpect(jsonPath("$.rol").value("JUGADOR"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void me_jugador_exponeClubNulo() throws Exception {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);

		String cuerpo = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenDe("ana@vibora.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clubId").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.clubNombre").value(org.hamcrest.Matchers.nullValue()))
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).contains("\"clubId\":null").contains("\"clubNombre\":null");
	}

	@Test
	void me_adminYPersonal_exponenSuClub() throws Exception {
		Club club = clubs.saveAndFlush(TestData.club());
		crearUsuario("admin@vibora.com", Rol.ADMIN_CLUB, club, true);
		crearUsuario("monitor@vibora.com", Rol.MONITOR, club, true);

		for (String email : new String[] {"admin@vibora.com", "monitor@vibora.com"}) {
			mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenDe(email)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.clubId").value(club.getId().intValue()))
					.andExpect(jsonPath("$.clubNombre").value(club.getNombre()));
		}
	}

	@Test
	void me_noProvocaCargasPerezosasAdicionales() throws Exception {
		// Con un club asociado, /me no debe depender de una segunda consulta para el nombre del club
		Club club = clubs.saveAndFlush(TestData.club());
		crearUsuario("admin@vibora.com", Rol.ADMIN_CLUB, club, true);
		String token = tokenDe("admin@vibora.com");
		em.flush();
		em.clear();
		var stats = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
		stats.setStatisticsEnabled(true);
		stats.clear();

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

		// el filtro JWT no consulta; perfil() debe resolver usuario+club en UNA sola sentencia
		assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
		stats.setStatisticsEnabled(false);
	}

	@Test
	void registro_exponeClubNulo() throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registroJson("nuevo@vibora.com", PASSWORD, "Ana", "García")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).contains("\"clubId\":null").contains("\"clubNombre\":null");
	}

	@Test
	void me_sinToken_devuelve401Json() throws Exception {
		mvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	void me_conTokenBasura_devuelve401Json() throws Exception {
		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer esto.no.es-un-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	void me_conTokenManipulado_devuelve401() throws Exception {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);
		String[] partes = tokenDe("ana@vibora.com").split("\\.");
		String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"ana@vibora.com\",\"rol\":\"ADMIN_CLUB\",\"iss\":\"vibora\",\"exp\":"
						+ Instant.now().plusSeconds(3600).getEpochSecond() + "}").getBytes());

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + partes[0] + "." + payloadFalso + "." + partes[2]))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void me_conTokenExpirado_devuelve401() throws Exception {
		Usuario u = crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);
		Clock pasado = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), java.time.ZoneOffset.UTC);
		String expirado = new JwtService(new JwtProperties(secret, 60), pasado).generar(u).token();

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expirado))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").isString());
	}

	@Test
	void me_conTokenDeUsuarioInactivoOEliminado_devuelve401() throws Exception {
		Usuario u = crearUsuario("ana@vibora.com", Rol.JUGADOR, null, true);
		String token = tokenDe("ana@vibora.com");
		u.setActivo(false);
		usuarios.saveAndFlush(u);

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rutaNoPublicaSinToken_devuelve401_noEs404() throws Exception {
		mvc.perform(get("/api/lo-que-sea"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").isString());
	}

	// ---- Autorizacion por rol ----

	@Test
	void rutaDeAdmin_jugadorRecibe403_adminRecibe200() throws Exception {
		Club club = clubs.saveAndFlush(TestData.club());
		crearUsuario("jugador@vibora.com", Rol.JUGADOR, null, true);
		crearUsuario("admin@vibora.com", Rol.ADMIN_CLUB, club, true);

		mvc.perform(get("/api/test/admin").header("Authorization", "Bearer " + tokenDe("jugador@vibora.com")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").isString());

		mvc.perform(get("/api/test/admin").header("Authorization", "Bearer " + tokenDe("admin@vibora.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value("admin"));
	}
}
