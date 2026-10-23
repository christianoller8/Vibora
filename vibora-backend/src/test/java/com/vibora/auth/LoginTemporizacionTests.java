package com.vibora.auth;

import com.vibora.ApiTestBase;
import com.vibora.usuario.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mitigacion de enumeracion de usuarios por tiempos: con un email desconocido el login hace igualmente una
 * comparacion BCrypt (contra un hash ficticio), de modo que tarda lo mismo que con una contraseña incorrecta. Se
 * comprueba por comportamiento (el PasswordEncoder recibe la llamada), nunca midiendo tiempo real.
 */
class LoginTemporizacionTests extends ApiTestBase {

	@MockitoSpyBean PasswordEncoder encoderEspiado;

	@BeforeEach
	void prepararUsuarios() {
		crearUsuario("ana@vibora.com", Rol.JUGADOR, null);
		clearInvocations(encoderEspiado); // lo que cuenta es el login, no el alta de los usuarios de prueba
	}

	private String login(String email, String password, int estadoEsperado) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.andExpect(status().is(estadoEsperado))
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void emailDesconocido_comparaIgualmenteContraUnHashFicticio() throws Exception {
		login("nadie@vibora.com", PASSWORD, 401);

		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(encoderEspiado).matches(eq(PASSWORD), hash.capture());
		assertThat(hash.getValue()).as("hash ficticio con forma de BCrypt").startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, hash.getValue()))
				.as("el hash ficticio no puede aceptar ninguna contraseña real").isFalse();
	}

	@Test
	void emailDesconocido_usaSiempreElMismoHashFicticio_yNoUnoDeUnUsuarioReal() throws Exception {
		String hashDeAna = usuarios.findByEmail("ana@vibora.com").orElseThrow().getPasswordHash();

		login("nadie@vibora.com", PASSWORD, 401);
		login("otro@vibora.com", PASSWORD, 401);

		ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
		verify(encoderEspiado, org.mockito.Mockito.times(2)).matches(eq(PASSWORD), hashes.capture());
		assertThat(hashes.getAllValues()).doesNotContain(hashDeAna).containsOnly(hashes.getAllValues().get(0));
	}

	@Test
	void passwordIncorrecta_hacelaMismaUnicaComparacion() throws Exception {
		login("ana@vibora.com", "incorrecta-123", 401);

		verify(encoderEspiado).matches(eq("incorrecta-123"), anyString());
	}

	@Test
	void mismoEstadoYCuerpo_paraEmailDesconocidoQueParaPasswordIncorrecta() throws Exception {
		String passwordMal = login("ana@vibora.com", "incorrecta-123", 401);
		String desconocido = login("nadie@vibora.com", "incorrecta-123", 401);

		assertThat(desconocido).isEqualTo(passwordMal);
	}

	@Test
	void loginCorrecto_noUsaElHashFicticio() throws Exception {
		login("ana@vibora.com", PASSWORD, 200);

		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(encoderEspiado).matches(eq(PASSWORD), hash.capture());
		assertThat(hash.getValue()).isEqualTo(usuarios.findByEmail("ana@vibora.com").orElseThrow().getPasswordHash());
		verify(encoderEspiado, never()).matches(eq(PASSWORD), org.mockito.ArgumentMatchers.argThat(h -> !h.startsWith("$2")));
	}
}
