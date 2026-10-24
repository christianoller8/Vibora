package com.vibora.incidencia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fotos de una incidencia: subida segura, permisos, limites, descarga autenticada y borrado. */
class IncidenciaFotoTests extends IncidenciaApiTestBase {

	private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	// ---- ayudas ----

	private ResultActions subir(Persona p, long incidencia, String nombre, String tipoDeclarado, byte[] bytes) throws Exception {
		return mvc.perform(multipart("/api/incidencias/" + incidencia + "/fotos")
				.file(new MockMultipartFile("file", nombre, tipoDeclarado, bytes)).header("Authorization", p.token()));
	}

	private ResultActions subirPng(Persona p, long incidencia) throws Exception {
		return subir(p, incidencia, "pista.png", "image/png", Imagenes.png(20, 10, false));
	}

	private long fotoSubida(Persona p, long incidencia) throws Exception {
		return idDe(subirPng(p, incidencia).andExpect(status().isCreated()));
	}

	private ResultActions listarFotos(Persona p, long incidencia) throws Exception {
		return mvc.perform(get("/api/incidencias/" + incidencia + "/fotos").header("Authorization", p.token()));
	}

	private ResultActions descargar(Persona p, long incidencia, long foto) throws Exception {
		return mvc.perform(get("/api/incidencias/" + incidencia + "/fotos/" + foto).header("Authorization", p.token()));
	}

	private ResultActions borrar(Persona p, long incidencia, long foto) throws Exception {
		return mvc.perform(delete("/api/incidencias/" + incidencia + "/fotos/" + foto).header("Authorization", p.token()));
	}

	private byte[] guardado(long fotoId) throws Exception {
		refrescar();
		IncidenciaFoto foto = fotos.findById(fotoId).orElseThrow();
		try (InputStream in = almacen.abrir(foto.getClaveAlmacen())) {
			return in.readAllBytes();
		}
	}

	private long filas(long incidencia) {
		refrescar();
		return fotos.countByIncidenciaId(incidencia);
	}

	// ---- subir: camino feliz ----

	@Test
	void elReporteroSubeUnPng_201_conMetadatosSaneados_yElFicheroQuedaGuardado() throws Exception {
		long incidencia = incidenciaDe(jugador);
		byte[] original = Imagenes.png(20, 10, false);
		long antesDeNotificaciones = totalNotifs();

		long fotoId = idDe(subir(jugador, incidencia, "Pista rota.png", "image/png", original)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombreOriginal").value("Pista rota.png"))
				.andExpect(jsonPath("$.tipoContenido").value("image/png"))
				.andExpect(jsonPath("$.subidaPor.id").value(jugador.id()))
				.andExpect(jsonPath("$.subidaPor.nombre").value("Juan Jugador"))
				.andExpect(jsonPath("$.creadaEn").value("2030-01-01T00:00:00Z"))
				.andExpect(jsonPath("$.claveAlmacen").doesNotExist()));

		refrescar();
		IncidenciaFoto fila = fotos.findById(fotoId).orElseThrow();
		assertThat(fila.getClaveAlmacen()).matches(UUID_RE);
		assertThat(almacen.existe(fila.getClaveAlmacen())).isTrue();
		assertThat(fila.getTamano()).isEqualTo(guardado(fotoId).length); // el tamano es el del fichero ya recodificado
		assertThat(fila.getTipoContenido()).isEqualTo("image/png");
		assertThat(totalNotifs()).as("las fotos no generan avisos").isEqualTo(antesDeNotificaciones);
	}

	@Test
	void subirUnaFotoDejaEventoFotoAnadidaEnElHistorial() throws Exception {
		long incidencia = incidenciaDe(jugador);

		subir(jugador, incidencia, "red.jpg", "image/jpeg", Imagenes.jpeg(30, 20)).andExpect(status().isCreated());

		List<IncidenciaEvento> historial = historial(incidencia);
		IncidenciaEvento ultimo = historial.get(historial.size() - 1);
		assertThat(ultimo.getTipo()).isEqualTo(TipoEventoIncidencia.FOTO_ANADIDA);
		assertThat(ultimo.getUsuario().getId()).isEqualTo(jugador.id());
		assertThat(ultimo.getTexto()).isEqualTo("red.jpg");
	}

	@Test
	void elJpegSeGuardaRecodificado_sinExifNiGps() throws Exception {
		long incidencia = incidenciaDe(jugador);
		byte[] conGps = Imagenes.conExif(Imagenes.jpeg(30, 20), 1, Imagenes.SECRETO);

		long id = idDe(subir(jugador, incidencia, "gps.jpg", "image/jpeg", conGps).andExpect(status().isCreated())
				.andExpect(jsonPath("$.tipoContenido").value("image/jpeg")));

		byte[] enDisco = guardado(id);
		assertThat(Imagenes.contiene(enDisco, Imagenes.SECRETO)).isFalse();
		assertThat(Imagenes.tieneMarcadorJpeg(enDisco, 0xE1)).isFalse();
		assertThat(Imagenes.leer(enDisco).getWidth()).isEqualTo(30);
	}

	@Test
	void elTipoLoDecidenLosBytes_noElContentTypeDelCliente() throws Exception {
		long incidencia = incidenciaDe(jugador);

		// un JPEG que el cliente declara como texto: se acepta y se guarda como lo que es
		subir(jugador, incidencia, "foto.txt", "text/plain", Imagenes.jpeg(30, 20)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.tipoContenido").value("image/jpeg"));
		// un PNG que declara ser JPEG
		subir(jugador, incidencia, "foto.jpg", "image/jpeg", Imagenes.png(10, 10, false)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.tipoContenido").value("image/png"));
	}

	@Test
	void elNombreOriginalSeSaneaParaMostrarlo_yLaClaveNoSale_deEl() throws Exception {
		long incidencia = incidenciaDe(jugador);

		String respuesta = subir(jugador, incidencia, "..\\..\\etc/evil<script>\"x\".png", "image/png", Imagenes.png(10, 10, false))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(respuesta).contains("\"nombreOriginal\":\"evil_script__x_.png\"");
		refrescar();
		IncidenciaFoto fila = fotos.findAll().get(0);
		assertThat(fila.getClaveAlmacen()).matches(UUID_RE).doesNotContain("evil");
	}

	@Test
	void unNombreVacioOMuyLargo_seSustituyeOSeRecorta() throws Exception {
		long incidencia = incidenciaDe(jugador);

		subir(jugador, incidencia, "", "image/png", Imagenes.png(10, 10, false)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombreOriginal").value("foto"));
		String larga = "a".repeat(300) + ".png";
		String respuesta = subir(jugador, incidencia, larga, "image/png", Imagenes.png(10, 10, false))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		assertThat(respuesta).doesNotContain("a".repeat(101));
		subir(jugador, incidencia, "pista ñandú (2).jpeg", "image/png", Imagenes.png(10, 10, false))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nombreOriginal").value("pista ñandú (2).jpeg"));
	}

	// ---- subir: permisos y estados ----

	@Test
	void elAdministradorYLaPersonaAsignadaTambienPuedenSubir() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());

		subirPng(admin, incidencia).andExpect(status().isCreated());
		subirPng(mant1, incidencia).andExpect(status().isCreated());

		assertThat(filas(incidencia)).isEqualTo(2);
	}

	@Test
	void quienNoVeLaIncidencia_recibe404_yNoSeGuardaNada() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());

		for (Persona intruso : List.of(mant2, monitor, otro, adminB, mantB)) {
			subirPng(intruso, incidencia).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Incidencia no encontrada"));
		}
		assertThat(filas(incidencia)).isZero();
	}

	@Test
	void sinToken_401() throws Exception {
		long incidencia = incidenciaDe(jugador);

		mvc.perform(multipart("/api/incidencias/" + incidencia + "/fotos")
				.file(new MockMultipartFile("file", "a.png", "image/png", Imagenes.png(10, 10, false))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unaIncidenciaInexistente_404() throws Exception {
		subirPng(admin, 999_999L).andExpect(status().isNotFound());
	}

	@ParameterizedTest
	@EnumSource(value = EstadoIncidencia.class, names = { "CERRADA", "RECHAZADA" })
	void enEstadoTerminal_noSePuedeSubir_409(EstadoIncidencia terminal) throws Exception {
		long incidencia = incidenciaEn(terminal);

		subirPng(jugador, incidencia).andExpect(status().isConflict());
		subirPng(admin, incidencia).andExpect(status().isConflict());

		assertThat(filas(incidencia)).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = EstadoIncidencia.class, names = { "ABIERTA", "ASIGNADA", "EN_CURSO", "RESUELTA" })
	void enEstadosNoTerminales_sePuedeSubir(EstadoIncidencia estado) throws Exception {
		long incidencia = incidenciaEn(estado);

		subirPng(jugador, incidencia).andExpect(status().isCreated());
	}

	// ---- subir: validacion ----

	static Stream<Object[]> rechazadas() {
		return Stream.of(
				new Object[] { "svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8) },
				new Object[] { "svg disfrazado de png", "image/png", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8) },
				new Object[] { "gif", "image/gif", "GIF89a\u0001\u0000\u0001\u0000\u0080\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1) },
				new Object[] { "gif disfrazado de jpeg", "image/jpeg", "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1) },
				new Object[] { "texto", "text/plain", "hola, esto no es una imagen".getBytes(StandardCharsets.UTF_8) },
				new Object[] { "html", "text/html", "<html><body onload=alert(1)></body></html>".getBytes(StandardCharsets.UTF_8) },
				new Object[] { "ejecutable de windows renombrado .png", "image/png", new byte[] { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF } },
				new Object[] { "ejecutable de linux renombrado .png", "image/png", new byte[] { 0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 } },
				new Object[] { "fichero vacio", "image/png", new byte[0] },
				new Object[] { "firma png sin datos", "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' } });
	}

	@ParameterizedTest
	@MethodSource("rechazadas")
	void loQueNoEsJpegPngNiWebp_400_conSuMensaje_yNoDejaRastro(String descripcion, String tipoDeclarado, byte[] bytes) throws Exception {
		long incidencia = incidenciaDe(jugador);

		subir(jugador, incidencia, "foto.png", tipoDeclarado, bytes)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Formato de imagen no permitido"));

		assertThat(filas(incidencia)).as(descripcion).isZero();
		assertThat(historial(incidencia)).extracting(IncidenciaEvento::getTipo).containsExactly(TipoEventoIncidencia.CREADA);
	}

	@Test
	void unPngQueDeclaraVeinteMilPorVeinteMilPixeles_400_sinDecodificarlo() throws Exception {
		long incidencia = incidenciaDe(jugador);

		subir(jugador, incidencia, "bomba.png", "image/png", Imagenes.pngSoloCabecera(20_000, 20_000))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("La imagen es demasiado grande (máximo 25 megapíxeles)"));

		assertThat(filas(incidencia)).isZero();
	}

	@Test
	void unFicheroDeMasDeCincoMegas_413_aunqueSePasaraPorAlgunaImagen() throws Exception {
		long incidencia = incidenciaDe(jugador);
		byte[] enorme = new byte[5 * 1024 * 1024 + 1];
		System.arraycopy(Imagenes.jpeg(10, 10), 0, enorme, 0, 100);

		subir(jugador, incidencia, "enorme.jpg", "image/jpeg", enorme)
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error").value("La imagen supera el tamaño máximo de 5 MB"));

		assertThat(filas(incidencia)).isZero();
	}

	@Test
	void sinLaParteFile_400_conElFormatoDeErroresDeLaApi() throws Exception {
		long incidencia = incidenciaDe(jugador);

		mvc.perform(multipart("/api/incidencias/" + incidencia + "/fotos")
						.file(new MockMultipartFile("otra", "a.png", "image/png", Imagenes.png(10, 10, false)))
						.header("Authorization", jugador.token()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Datos no válidos"));
	}

	// ---- limite de 4 fotos ----

	@Test
	void conCuatroFotos_laQuintaEs409_yNoSeGuarda() throws Exception {
		long incidencia = incidenciaDe(jugador);
		for (int i = 0; i < 4; i++) {
			subirPng(jugador, incidencia).andExpect(status().isCreated());
		}

		subirPng(jugador, incidencia).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Una incidencia admite como máximo 4 fotos"));

		assertThat(filas(incidencia)).isEqualTo(4);
	}

	@Test
	void elLimiteEsPorIncidencia_otraIncidenciaTieneSusPropiasCuatro() throws Exception {
		long a = incidenciaDe(jugador, "Una");
		long b = incidenciaDe(jugador, "Otra");
		for (int i = 0; i < 4; i++) {
			subirPng(jugador, a).andExpect(status().isCreated());
		}

		subirPng(jugador, b).andExpect(status().isCreated());
	}

	@Test
	void alBorrarUnaFoto_sePuedeSubirOtra() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long primera = fotoSubida(jugador, incidencia);
		for (int i = 0; i < 3; i++) {
			subirPng(jugador, incidencia).andExpect(status().isCreated());
		}
		subirPng(jugador, incidencia).andExpect(status().isConflict());

		borrar(jugador, incidencia, primera).andExpect(status().isNoContent());

		subirPng(jugador, incidencia).andExpect(status().isCreated());
	}

	// ---- listar ----

	@Test
	void listarDevuelveLosMetadatosEnOrdenDeSubida_soloAQuienVeLaIncidencia() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
		long uno = idDe(subir(jugador, incidencia, "uno.png", "image/png", Imagenes.png(10, 10, false)).andExpect(status().isCreated()));
		long dos = idDe(subir(mant1, incidencia, "dos.jpg", "image/jpeg", Imagenes.jpeg(10, 10)).andExpect(status().isCreated()));

		for (Persona quien : List.of(jugador, mant1, admin)) {
			listarFotos(quien, incidencia).andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(2)))
					.andExpect(jsonPath("$[0].id").value(uno))
					.andExpect(jsonPath("$[0].nombreOriginal").value("uno.png"))
					.andExpect(jsonPath("$[0].tipoContenido").value("image/png"))
					.andExpect(jsonPath("$[0].tamano").isNumber())
					.andExpect(jsonPath("$[0].subidaPor.nombre").value("Juan Jugador"))
					.andExpect(jsonPath("$[1].id").value(dos))
					.andExpect(jsonPath("$[1].subidaPor.nombre").value("Mario Mantiene"))
					.andExpect(jsonPath("$[0].claveAlmacen").doesNotExist());
		}
		for (Persona intruso : List.of(mant2, monitor, otro, adminB)) {
			listarFotos(intruso, incidencia).andExpect(status().isNotFound());
		}
	}

	@Test
	void sinFotos_listarDevuelveUnaListaVacia() throws Exception {
		long incidencia = incidenciaDe(jugador);

		listarFotos(jugador, incidencia).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void elDetalleDeLaIncidenciaIncluyeLasFotos_yBloqueoActivoNuloSinBloqueo() throws Exception {
		long incidencia = incidenciaDe(jugador);
		detalle(jugador, incidencia).andExpect(jsonPath("$.fotos", hasSize(0))).andExpect(jsonPath("$.bloqueoActivo").value(nullValue()));
		long foto = fotoSubida(jugador, incidencia);

		detalle(admin, incidencia).andExpect(status().isOk())
				.andExpect(jsonPath("$.fotos", hasSize(1)))
				.andExpect(jsonPath("$.fotos[0].id").value(foto))
				.andExpect(jsonPath("$.fotos[0].tipoContenido").value("image/png"))
				.andExpect(jsonPath("$.bloqueoActivo").value(nullValue()));
	}

	// ---- descargar ----

	@Test
	void descargarDevuelveLosBytesGuardados_conCabecerasSeguras() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);
		byte[] esperado = guardado(foto);

		descargar(jugador, incidencia, foto).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("Content-Disposition", "inline"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", "private, max-age=3600"))
				.andExpect(content().bytes(esperado));
	}

	@Test
	void elTipoDeLaRespuestaEsElVerificadoAlSubir_aunqueSeSubieraConOtroContentType() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = idDe(subir(jugador, incidencia, "x.txt", "text/plain", Imagenes.jpeg(10, 10)).andExpect(status().isCreated()));

		descargar(jugador, incidencia, foto).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"));
	}

	@Test
	void descargarExigeAutenticacionYVisibilidad() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);

		mvc.perform(get("/api/incidencias/" + incidencia + "/fotos/" + foto)).andExpect(status().isUnauthorized());
		for (Persona intruso : List.of(mant2, monitor, otro, adminB)) {
			descargar(intruso, incidencia, foto).andExpect(status().isNotFound());
		}
		descargar(admin, incidencia, foto).andExpect(status().isOk());
	}

	@Test
	void unaFotoDeOtraIncidencia_404_aunqueQuienPideVeaAmbas() throws Exception {
		long a = incidenciaDe(jugador, "Una");
		long b = incidenciaDe(jugador, "Otra");
		long fotoDeA = fotoSubida(jugador, a);

		descargar(jugador, b, fotoDeA).andExpect(status().isNotFound());
		descargar(admin, b, fotoDeA).andExpect(status().isNotFound());
		borrar(jugador, b, fotoDeA).andExpect(status().isNotFound());
		borrar(admin, b, fotoDeA).andExpect(status().isNotFound());
		assertThat(filas(a)).as("sigue en A").isEqualTo(1);
		descargar(jugador, a, fotoDeA).andExpect(status().isOk());
	}

	@Test
	void unaFotoDeUnaIncidenciaQueNoVeo_404_viaOtraIncidenciaQueSiVeo() throws Exception {
		// "otro" no tiene relacion con el club A: reporta en el club B y pide la foto del club A por SU incidencia
		long ajena = incidenciaDe(jugador);
		long fotoAjena = fotoSubida(jugador, ajena);
		reservaDe(otro, pistaB);
		long propia = idDe(reportar(otro, clubB, "Mia", "RED", pistaB.getId()).andExpect(status().isCreated()));

		descargar(otro, propia, fotoAjena).andExpect(status().isNotFound());
		descargar(otro, ajena, fotoAjena).andExpect(status().isNotFound());
	}

	@Test
	void unaFotoInexistente_404() throws Exception {
		long incidencia = incidenciaDe(jugador);

		descargar(jugador, incidencia, 999_999L).andExpect(status().isNotFound());
	}

	@Test
	void siElFicheroDesapareceDelDisco_404_noUn500() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);
		refrescar();
		almacen.borrar(fotos.findById(foto).orElseThrow().getClaveAlmacen());

		descargar(jugador, incidencia, foto).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Foto no encontrada"));
	}

	// ---- borrar ----

	@Test
	void quienSubioLaFotoPuedeBorrarla_204_yQuedaEventoFotoEliminada() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);

		borrar(jugador, incidencia, foto).andExpect(status().isNoContent());

		assertThat(filas(incidencia)).isZero();
		List<IncidenciaEvento> historial = historial(incidencia);
		IncidenciaEvento ultimo = historial.get(historial.size() - 1);
		assertThat(ultimo.getTipo()).isEqualTo(TipoEventoIncidencia.FOTO_ELIMINADA);
		assertThat(ultimo.getUsuario().getId()).isEqualTo(jugador.id());
		assertThat(ultimo.getTexto()).isEqualTo("pista.png");
		descargar(jugador, incidencia, foto).andExpect(status().isNotFound());
	}

	@Test
	void elAdministradorPuedeBorrarCualquierFoto() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);

		borrar(admin, incidencia, foto).andExpect(status().isNoContent());

		assertThat(filas(incidencia)).isZero();
	}

	@Test
	void quienVeLaIncidenciaPeroNoSubioLaFotoNiEsAdministrador_403() throws Exception {
		long incidencia = incidenciaDe(jugador);
		asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
		long delAdmin = fotoSubida(admin, incidencia);
		long delMantenimiento = fotoSubida(mant1, incidencia);

		borrar(jugador, incidencia, delAdmin).andExpect(status().isForbidden());
		borrar(mant1, incidencia, delAdmin).andExpect(status().isForbidden());
		borrar(jugador, incidencia, delMantenimiento).andExpect(status().isForbidden());

		assertThat(filas(incidencia)).isEqualTo(2);
	}

	@Test
	void quienNoVeLaIncidencia_404_alBorrar() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);

		for (Persona intruso : List.of(mant2, monitor, otro, adminB)) {
			borrar(intruso, incidencia, foto).andExpect(status().isNotFound());
		}
		assertThat(filas(incidencia)).isEqualTo(1);
	}

	@Test
	void sinToken_401_alBorrar() throws Exception {
		mvc.perform(delete("/api/incidencias/1/fotos/1")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@EnumSource(value = EstadoIncidencia.class, names = { "CERRADA", "RECHAZADA" })
	void enEstadoTerminal_noSePuedeBorrar_409(EstadoIncidencia terminal) throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);
		if (terminal == EstadoIncidencia.RECHAZADA) {
			estado(admin, incidencia, "RECHAZADA", "No procede").andExpect(status().isOk());
		} else {
			asignar(admin, incidencia, mant1.id(), null).andExpect(status().isOk());
			estado(mant1, incidencia, "EN_CURSO", null).andExpect(status().isOk());
			estado(mant1, incidencia, "RESUELTA", null).andExpect(status().isOk());
			estado(admin, incidencia, "CERRADA", null).andExpect(status().isOk());
		}

		borrar(jugador, incidencia, foto).andExpect(status().isConflict());
		borrar(admin, incidencia, foto).andExpect(status().isConflict());

		assertThat(filas(incidencia)).isEqualTo(1);
	}

	@Test
	void borrarDosVeces_laSegunda404() throws Exception {
		long incidencia = incidenciaDe(jugador);
		long foto = fotoSubida(jugador, incidencia);

		borrar(jugador, incidencia, foto).andExpect(status().isNoContent());
		borrar(jugador, incidencia, foto).andExpect(status().isNotFound());
	}
}
