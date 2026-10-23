package com.vibora.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vibora.usuario.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Emite y valida JWT firmados con HMAC-SHA256 (Nimbus, sin libreria extra). */
@Service
public class JwtService {

	public static final String EMISOR = "vibora";
	public static final String CLAIM_ROL = "rol";
	private static final int MIN_BYTES_SECRETO = 32;

	private final JwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Clock reloj;
	private final Duration expiracion;

	public JwtService(JwtProperties propiedades, Clock reloj) {
		byte[] bytes = propiedades.secret() == null ? new byte[0] : propiedades.secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_BYTES_SECRETO) {
			throw new IllegalArgumentException(
					"vibora.jwt.secret debe tener al menos " + MIN_BYTES_SECRETO + " bytes");
		}
		if (propiedades.expiracionMinutos() <= 0) {
			throw new IllegalArgumentException("vibora.jwt.expiracion-minutos debe ser positivo");
		}
		SecretKey clave = new SecretKeySpec(bytes, "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
		this.decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
		this.decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISOR));
		this.reloj = reloj;
		this.expiracion = Duration.ofMinutes(propiedades.expiracionMinutos());
	}

	public TokenEmitido generar(Usuario usuario) {
		Instant ahora = Instant.now(reloj).truncatedTo(ChronoUnit.SECONDS);
		Instant expiraEn = ahora.plus(expiracion);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(EMISOR)
				.subject(usuario.getEmail())
				.claim(CLAIM_ROL, usuario.getRol().name())
				.issuedAt(ahora)
				.expiresAt(expiraEn)
				.build();
		JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
		return new TokenEmitido(token, expiraEn);
	}

	/** Valida firma, caducidad y emisor. Lanza JwtException si algo falla. */
	public Jwt decodificar(String token) {
		return decoder.decode(token);
	}

	public JwtDecoder decoder() {
		return decoder;
	}
}
