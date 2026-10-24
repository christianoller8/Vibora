package com.vibora.incidencia;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Por la red de verdad (Tomcat + multipart real): los limites de spring.servlet.multipart no se aplican a MockMvc, asi
 * que el 413 del contenedor solo se puede comprobar aqui. Los datos se crean con commits reales y se borran al final.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "vibora.jwt.secret=secreto-de-test-con-al-menos-32-bytes-ok!")
class FotoHttpRealTests extends PostgresTestBase {

	static final Path CARPETA = AlmacenDeTest.carpeta("vibora-test-http-");
	static final String PASSWORD = "contraseña-segura";

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registro) {
		registro.add("vibora.almacen.directorio", CARPETA::toString);
	}

	@Value("${local.server.port}") int puerto;
	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired IncidenciaRepository incidencias;
	@Autowired IncidenciaEventoRepository eventos;
	@Autowired IncidenciaFotoRepository fotos;
	@Autowired PasswordEncoder passwordEncoder;

	final HttpClient http = HttpClient.newHttpClient();
	String token;
	long incidencia;

	@BeforeEach
	void preparar() throws Exception {
		Club club = clubs.saveAndFlush(TestData.club());
		Usuario admin = TestData.usuario("admin-http@vibora.com", Rol.ADMIN_CLUB, club);
		admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
		usuarios.saveAndFlush(admin);
		HttpResponse<String> login = enviar(HttpRequest.newBuilder(uri("/api/auth/login"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"admin-http@vibora.com\",\"password\":\"" + PASSWORD + "\"}")));
		Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(login.body());
		assertThat(m.find()).as(login.body()).isTrue();
		token = m.group(1);
		HttpResponse<String> creada = enviar(HttpRequest.newBuilder(uri("/api/clubes/" + club.getId() + "/incidencias"))
				.header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString("{\"titulo\":\"Red\",\"descripcion\":\"d\",\"categoria\":\"RED\"}")));
		Matcher id = Pattern.compile("\"id\":(\\d+)").matcher(creada.body());
		assertThat(id.find()).as(creada.body()).isTrue();
		incidencia = Long.parseLong(id.group(1));
	}

	@AfterEach
	void limpiar() {
		fotos.deleteAllInBatch();
		eventos.deleteAllInBatch();
		incidencias.deleteAllInBatch();
		usuarios.deleteAllInBatch();
		clubs.deleteAllInBatch();
		AlmacenDeTest.vaciar(CARPETA);
	}

	private URI uri(String ruta) {
		return URI.create("http://localhost:" + puerto + ruta);
	}

	private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws Exception {
		return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private HttpResponse<String> subir(String parte, String nombre, String tipo, byte[] contenido) throws Exception {
		String limite = "----vibora" + System.nanoTime();
		ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
		cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + parte + "\"; filename=\""
				+ nombre + "\"\r\nContent-Type: " + tipo + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		cuerpo.writeBytes(contenido);
		cuerpo.writeBytes(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return enviar(HttpRequest.newBuilder(uri("/api/incidencias/" + incidencia + "/fotos"))
				.header("Content-Type", "multipart/form-data; boundary=" + limite)
				.header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray())));
	}

	@Test
	void unaSubidaNormalPorLaRed_201() throws Exception {
		HttpResponse<String> respuesta = subir("file", "pista.png", "image/png", Imagenes.png(20, 10, false));

		assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
		assertThat(respuesta.body()).contains("\"tipoContenido\":\"image/png\"");
		assertThat(fotos.count()).isEqualTo(1);
	}

	@Test
	void unFicheroDeSeisMegas_413ConJson_porElLimiteDelContenedor_yNoSeGuardaNada() throws Exception {
		byte[] seisMegas = new byte[6 * 1024 * 1024];
		byte[] jpeg = Imagenes.jpeg(10, 10);
		System.arraycopy(jpeg, 0, seisMegas, 0, jpeg.length);

		HttpResponse<String> respuesta = subir("file", "grande.jpg", "image/jpeg", seisMegas);

		assertThat(respuesta.statusCode()).isEqualTo(413);
		assertThat(respuesta.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		assertThat(respuesta.body()).isEqualTo("{\"error\":\"La imagen supera el tamaño máximo de 5 MB\"}");
		assertThat(fotos.count()).isZero();
		assertThat(AlmacenDeTest.ficheros(CARPETA)).isEmpty();
	}

	@Test
	void sinLaParteFile_400ConJson() throws Exception {
		HttpResponse<String> respuesta = subir("otra", "pista.png", "image/png", Imagenes.png(20, 10, false));

		assertThat(respuesta.statusCode()).isEqualTo(400);
		assertThat(respuesta.body()).isEqualTo("{\"error\":\"Datos no válidos\"}");
	}

	@Test
	void descargarPorLaRed_devuelveLosMismosBytesYLasCabecerasSeguras() throws Exception {
		HttpResponse<String> subida = subir("file", "pista.png", "image/png", Imagenes.png(20, 10, false));
		Matcher id = Pattern.compile("\"id\":(\\d+)").matcher(subida.body());
		assertThat(id.find()).isTrue();

		HttpResponse<byte[]> descarga = http.send(HttpRequest.newBuilder(uri("/api/incidencias/" + incidencia + "/fotos/" + id.group(1)))
				.header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

		assertThat(descarga.statusCode()).isEqualTo(200);
		assertThat(descarga.headers().firstValue("Content-Type")).contains("image/png");
		assertThat(descarga.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
		assertThat(descarga.headers().firstValue("Content-Disposition")).contains("inline");
		assertThat(descarga.headers().firstValue("Cache-Control")).contains("private, max-age=3600");
		assertThat(Imagenes.leer(descarga.body()).getWidth()).isEqualTo(20);
		HttpResponse<byte[]> sinToken = http.send(HttpRequest.newBuilder(uri("/api/incidencias/" + incidencia + "/fotos/" + id.group(1)))
				.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		assertThat(sinToken.statusCode()).isEqualTo(401);
	}
}
