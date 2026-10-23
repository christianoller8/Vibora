package com.vibora.auth;

import com.jayway.jsonpath.JsonPath;
import com.vibora.PostgresTestBase;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP REAL (Tomcat en puerto aleatorio + java.net.http.HttpClient) con los cuerpos construidos a mano en bytes UTF-8,
 * para descartar problemas de codificacion que MockMvc podria ocultar. No es @Transactional: los usuarios creados
 * se borran en @AfterEach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
class AuthUtf8HttpTests extends PostgresTestBase {

	private static final String PASSWORD = "contraseñaÁÉÍ€123";
	private static final String NOMBRE = "José Ángel";
	private static final String APELLIDOS = "Núñez Peña";

	@LocalServerPort int puerto;
	@Autowired UsuarioRepository usuarios;

	private final HttpClient cliente = HttpClient.newHttpClient();
	private final List<String> emails = new ArrayList<>();

	@AfterEach
	void limpiar() {
		emails.forEach(email -> usuarios.findByEmail(email).ifPresent(usuarios::delete));
	}

	// ---- ayudas ----

	private record Respuesta(int estado, String cuerpo) {
	}

	private Respuesta post(String ruta, String json, String contentType) throws Exception {
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
				.POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)));
		if (contentType != null) {
			peticion.header("Content-Type", contentType);
		}
		return enviar(peticion);
	}

	private Respuesta enviar(HttpRequest.Builder peticion) throws Exception {
		HttpResponse<byte[]> r = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofByteArray());
		return new Respuesta(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8));
	}

	private Respuesta registrar(String email, String password, String contentType) throws Exception {
		emails.add(email);
		return post("/api/auth/registro", """
				{"email":"%s","password":"%s","nombre":"%s","apellidos":"%s"}
				""".formatted(email, password, NOMBRE, APELLIDOS), contentType);
	}

	private Respuesta registrar(String email, String password) throws Exception {
		return registrar(email, password, "application/json; charset=UTF-8");
	}

	private Respuesta login(String email, String password) throws Exception {
		return post("/api/auth/login", """
				{"email":"%s","password":"%s"}
				""".formatted(email, password), "application/json; charset=UTF-8");
	}

	private static String nfc(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFC);
	}

	private static String nfd(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD);
	}

	// ---- UTF-8 de extremo a extremo ----

	@Test
	void registro_conCharsetUtf8_201_yNombresIntactos() throws Exception {
		Respuesta r = registrar("utf8@vibora.com", PASSWORD);

		assertThat(r.estado()).isEqualTo(201);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.nombre")).isEqualTo(NOMBRE);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.apellidos")).isEqualTo(APELLIDOS);
	}

	@Test
	void registro_sinParametroCharset_tambienEsUtf8() throws Exception {
		Respuesta r = registrar("sincharset@vibora.com", PASSWORD, "application/json");

		assertThat(r.estado()).isEqualTo(201);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.nombre")).isEqualTo(NOMBRE);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.apellidos")).isEqualTo(APELLIDOS);
		assertThat(login("sincharset@vibora.com", PASSWORD).estado()).isEqualTo(200);
	}

	@Test
	void login_conLaPasswordAcentuada_200_yConUnaAcentoDistinto_401() throws Exception {
		registrar("login@vibora.com", PASSWORD);

		assertThat(login("login@vibora.com", PASSWORD).estado()).isEqualTo(200);
		assertThat(login("login@vibora.com", "contrasenaÁÉÍ€123").estado()).isEqualTo(401);
		assertThat(login("login@vibora.com", "contraseñaAEI€123").estado()).isEqualTo(401);
	}

	@Test
	void me_devuelveLosNombresAcentuadosIntactos() throws Exception {
		registrar("me@vibora.com", PASSWORD);
		String token = JsonPath.read(login("me@vibora.com", PASSWORD).cuerpo(), "$.token");

		Respuesta r = enviar(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/auth/me"))
				.header("Authorization", "Bearer " + token).GET());

		assertThat(r.estado()).isEqualTo(200);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.nombre")).isEqualTo(NOMBRE);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.apellidos")).isEqualTo(APELLIDOS);
	}

	// ---- Limite de BCrypt: 72 BYTES ----

	@Test
	void registro_conMasDe72BytesUtf8_400ConMensaje_aunqueTengaMenosDe72Caracteres() throws Exception {
		String cuarentaCaracteresOchentaBytes = "ñ".repeat(40);

		Respuesta r = registrar("largo@vibora.com", cuarentaCaracteresOchentaBytes);

		assertThat(r.estado()).isEqualTo(400);
		assertThat((String) JsonPath.read(r.cuerpo(), "$.error")).isEqualTo("La contraseña no puede superar los 72 bytes en UTF-8");
		assertThat(usuarios.findByEmail("largo@vibora.com")).isEmpty();
	}

	@Test
	void registro_conExactamente72Bytes_201_yPuedeEntrar() throws Exception {
		String setentaYDosBytes = "ñ".repeat(36);

		assertThat(registrar("limite@vibora.com", setentaYDosBytes).estado()).isEqualTo(201);
		assertThat(login("limite@vibora.com", setentaYDosBytes).estado()).isEqualTo(200);
	}

	@Test
	void login_conMasDe72Bytes_401_nuncaEntraPorTruncadoNi500() throws Exception {
		String base = "a".repeat(72);
		registrar("truncado@vibora.com", base);

		// BCrypt.matches truncaria a 72 bytes y daria por buena la contraseña si no se rechazara antes
		assertThat(login("truncado@vibora.com", base + "cola-distinta").estado()).isEqualTo(401);
		assertThat(login("truncado@vibora.com", "ñ".repeat(40)).estado()).isEqualTo(401);
		assertThat(login("noexiste@vibora.com", "ñ".repeat(40)).estado()).isEqualTo(401);
	}

	// ---- Normalizacion Unicode ----

	@Test
	void registroEnNfc_loginEnNfd_200() throws Exception {
		String enNfc = nfc(PASSWORD);
		String enNfd = nfd(PASSWORD);
		assertThat(enNfd).isNotEqualTo(enNfc); // precondicion: realmente son secuencias distintas

		assertThat(registrar("nfc@vibora.com", enNfc).estado()).isEqualTo(201);

		assertThat(login("nfc@vibora.com", enNfd).estado()).isEqualTo(200);
		assertThat(login("nfc@vibora.com", enNfc).estado()).isEqualTo(200);
	}

	@Test
	void registroEnNfd_loginEnNfc_200() throws Exception {
		assertThat(registrar("nfd@vibora.com", nfd(PASSWORD)).estado()).isEqualTo(201);

		assertThat(login("nfd@vibora.com", nfc(PASSWORD)).estado()).isEqualTo(200);
		assertThat(login("nfd@vibora.com", nfd(PASSWORD)).estado()).isEqualTo(200);
	}

	@Test
	void registro_unaPasswordCuyaFormaNfdSuperaLos72BytesPeroNfcNo_201() throws Exception {
		// 36 'ñ' en NFC = 72 bytes; en NFD son 108 bytes. Se valida y se hashea la forma NFC.
		String enNfd = nfd("ñ".repeat(36));
		assertThat(enNfd.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);

		assertThat(registrar("nfdlargo@vibora.com", enNfd).estado()).isEqualTo(201);
		assertThat(login("nfdlargo@vibora.com", nfc(enNfd)).estado()).isEqualTo(200);
	}
}
