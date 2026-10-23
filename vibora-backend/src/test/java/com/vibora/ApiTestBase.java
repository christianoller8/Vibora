package com.vibora;

import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base para tests de API: MockMvc + rollback por test + ayudas para crear usuarios
 * y obtener tokens a traves del login real.
 */
@SpringBootTest(properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTestBase extends PostgresTestBase {

	protected static final String PASSWORD = "contraseña-segura";

	@Autowired protected MockMvc mvc;
	@Autowired protected UsuarioRepository usuarios;
	@Autowired protected ClubRepository clubs;
	@Autowired protected PasswordEncoder passwordEncoder;

	protected Club crearClub(String nombre) {
		Club c = TestData.club();
		c.setNombre(nombre);
		return clubs.saveAndFlush(c);
	}

	protected Usuario crearUsuario(String email, Rol rol, Club club) {
		Usuario u = TestData.usuario(email, rol, club);
		u.setPasswordHash(passwordEncoder.encode(PASSWORD));
		return usuarios.saveAndFlush(u);
	}

	/** Token obtenido por el endpoint real de login. */
	protected String tokenDe(String email) throws Exception {
		MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD))).andExpect(status().isOk()).andReturn();
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(r.getResponse().getContentAsString());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	protected String bearer(String email) throws Exception {
		return "Bearer " + tokenDe(email);
	}

	protected String bearerAdminDe(Club club, String email) throws Exception {
		crearUsuario(email, Rol.ADMIN_CLUB, club);
		return bearer(email);
	}

	protected String bearerJugador(String email) throws Exception {
		crearUsuario(email, Rol.JUGADOR, null);
		return bearer(email);
	}
}
