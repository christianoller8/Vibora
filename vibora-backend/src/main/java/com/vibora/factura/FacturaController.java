package com.vibora.factura;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class FacturaController {

	private final FacturaService facturaService;

	/** 201 la primera vez; 200 con la misma factura si ya estaba emitida. El cuerpo (receptor) es opcional. */
	@PostMapping("/api/reservas/{id}/factura")
	public ResponseEntity<FacturaResponse> emitir(@PathVariable Long id,
			@Valid @RequestBody(required = false) EmitirFacturaRequest peticion) {
		FacturaService.Emision emision = facturaService.emitir(id, peticion);
		return ResponseEntity.status(emision.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(emision.factura());
	}

	@GetMapping("/api/reservas/{id}/facturas")
	public List<FacturaResponse> deReserva(@PathVariable Long id) {
		return facturaService.deReserva(id);
	}

	@GetMapping("/api/facturas")
	public List<FacturaResponse> mias(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + FacturaService.LIMITE_POR_DEFECTO) int limit) {
		return facturaService.mias(page, limit);
	}

	@GetMapping("/api/clubes/{clubId}/facturas")
	public ListadoFacturasClubResponse delClub(@PathVariable Long clubId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(required = false) TipoFactura tipo, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + FacturaService.LIMITE_POR_DEFECTO) int limit) {
		return facturaService.delClub(clubId, desde, hasta, tipo, page, limit);
	}

	@GetMapping("/api/facturas/{id}")
	public FacturaResponse obtener(@PathVariable Long id) {
		return facturaService.obtener(id);
	}

	/**
	 * Descarga del PDF. Cabeceras: es un adjunto (no se abre en la pagina), el navegador no debe adivinar otro tipo
	 * (nosniff) y no se guarda en ninguna cache compartida ni del navegador (es un documento personal).
	 */
	@GetMapping("/api/facturas/{id}/pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
		FacturaService.PdfFactura pdf = facturaService.pdf(id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.nombreFichero() + "\"")
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.contentLength(pdf.contenido().length)
				.body(pdf.contenido());
	}
}
