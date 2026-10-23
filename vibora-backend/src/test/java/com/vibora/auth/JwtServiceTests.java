package com.vibora.auth;

import com.vibora.TestData;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {

	private static final String SECRET = "secreto-de-test-con-al-menos-32-bytes-ok!";

	private final Usuario usuario = TestData.usuario("ana@vibora.com", Rol.JUGADOR, null);

	private JwtService servicio(Clock reloj) {
		return new JwtService(new JwtProperties(SECRET, 60), reloj);
	}

	@Test
	void generarYDecodificar_devuelveLosClaimsEsperados() {
		JwtService servicio = servicio(Clock.systemUTC());

		TokenEmitido emitido = servicio.generar(usuario);
		Jwt jwt = servicio.decodificar(emitido.token());

		assertThat(jwt.getSubject()).isEqualTo("ana@vibora.com");
		assertThat(jwt.getClaimAsString("rol")).isEqualTo("JUGADOR");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("vibora");
		assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
		assertThat(jwt.getExpiresAt()).isEqualTo(emitido.expiraEn().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
	}

	@Test
	void tokenExpirado_esRechazado() {
		Clock pasado = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
		String token = servicio(pasado).generar(usuario).token();

		assertThatThrownBy(() -> servicio(Clock.systemUTC()).decodificar(token))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void firmaManipulada_esRechazada() {
		JwtService servicio = servicio(Clock.systemUTC());
		String token = servicio.generar(usuario).token();
		String[] partes = token.split("\\.");
		// Cambiamos el payload (rol) manteniendo la firma original
		String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"ana@vibora.com\",\"rol\":\"ADMIN_CLUB\",\"iss\":\"vibora\",\"exp\":"
						+ Instant.now().plusSeconds(3600).getEpochSecond() + "}").getBytes());
		String manipulado = partes[0] + "." + payloadFalso + "." + partes[2];

		assertThatThrownBy(() -> servicio.decodificar(manipulado)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenFirmadoConOtroSecreto_esRechazado() {
		JwtService otro = new JwtService(new JwtProperties("otro-secreto-distinto-de-al-menos-32-bytes", 60), Clock.systemUTC());
		String token = otro.generar(usuario).token();

		assertThatThrownBy(() -> servicio(Clock.systemUTC()).decodificar(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void secretoCorto_esRechazadoAlArrancar() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties("corto", 60), Clock.systemUTC()))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
