package com.vibora.incidencia;

import com.vibora.common.exception.ArchivoDemasiadoGrandeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Fotos de una incidencia. La descarga necesita el JWT (cabecera Authorization): un {@code <img src>} del navegador
 * no puede enviarla, asi que el frontend debe pedir los bytes con fetch/HttpClient como blob y mostrarlos con una
 * URL de objeto.
 */
@RestController
@RequiredArgsConstructor
public class FotoController {

	private final FotoService fotoService;

	@PostMapping(path = "/api/incidencias/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public FotoResponse subir(@PathVariable Long id, @RequestParam("file") MultipartFile archivo) {
		// El limite de multipart ya corta lo grande en el contenedor; esto cubre cualquier otro camino
		if (archivo.getSize() > ValidadorImagen.TAMANO_MAXIMO) {
			throw new ArchivoDemasiadoGrandeException(ValidadorImagen.DEMASIADO_PESADA);
		}
		try {
			return fotoService.subir(id, archivo.getOriginalFilename(), archivo.getBytes());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@GetMapping("/api/incidencias/{id}/fotos")
	public List<FotoResponse> listar(@PathVariable Long id) {
		return fotoService.listar(id);
	}

	@GetMapping("/api/incidencias/{id}/fotos/{fotoId}")
	public ResponseEntity<byte[]> descargar(@PathVariable Long id, @PathVariable Long fotoId) {
		FotoContenido foto = fotoService.descargar(id, fotoId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(foto.tipoContenido()))
				.header("Content-Disposition", "inline")
				.header("X-Content-Type-Options", "nosniff")
				.header("Cache-Control", "private, max-age=3600")
				// por si algun navegador intentara interpretarlo como documento: nada de scripts ni recursos
				.header("Content-Security-Policy", "default-src 'none'; sandbox")
				.body(foto.bytes());
	}

	@DeleteMapping("/api/incidencias/{id}/fotos/{fotoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id, @PathVariable Long fotoId) {
		fotoService.eliminar(id, fotoId);
	}
}
