package com.vibora.factura;

import com.vibora.auth.AutorizacionClub;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.club.ValidadorNif;
import com.vibora.common.LimitesFecha;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.notificacion.NotificacionService;
import com.vibora.notificacion.TextosAviso;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Facturacion SIMULADA de las reservas. Se factura al ORGANIZADOR por el precio completo (las deudas entre jugadores
 * son privadas y no se facturan). Una factura emitida es inmutable (lo impone un trigger de la BD): cancelar una
 * reserva ya facturada emite una RECTIFICATIVA con los importes en negativo, en la misma transaccion que la cancelacion.
 * <p>
 * Orden de comprobaciones al emitir: sesion (401) -> reserva (404) -> permiso (403) -> receptor (400) -> reserva
 * confirmada (409) -> ya facturada (devuelve la existente) -> datos fiscales del club (409) -> importe (409).
 * <p>
 * Orden de bloqueos de fila (el mismo que el resto del sistema, con el contador de facturas AL FINAL): reserva ->
 * cargos de la reserva -> contador de la serie (factura_serie). El contador es el ultimo bloqueo que toma una
 * transaccion y quien lo tiene no vuelve a pedir ninguna fila de reserva, cargo, pista ni incidencia; por eso no puede
 * formar un ciclo con ellos (ver NumeracionFacturas). Emitir y cancelar la misma reserva a la vez se serializan en la fila
 * de la reserva: gana uno y el otro ve el estado ya cambiado.
 */
@Service
@RequiredArgsConstructor
public class FacturaService {

	static final int LIMITE_POR_DEFECTO = 20;
	static final int LIMITE_MAXIMO = 100;
	private static final int MAX_NOMBRE = 200;
	private static final int MAX_DIRECCION = 300;
	private static final int MAX_CONCEPTO = 300;
	/** Fechas extremas para "sin filtro" (PostgreSQL no tipa un parametro nulo en "? is null"). */
	private static final Instant PRINCIPIO = Instant.parse("0001-01-01T00:00:00Z");
	private static final Instant FIN = Instant.parse("9999-12-31T00:00:00Z");

	private final FacturaRepository facturas;
	private final ReservaRepository reservas;
	private final ClubRepository clubs;
	private final NumeracionFacturas numeracion;
	private final AutorizacionClub autorizacion;
	private final NotificacionService notificaciones;
	private final TextosAviso textos;
	private final Clock reloj;
	private final ZoneId zona;

	/** Resultado de pedir una factura: la factura y si se ha creado ahora (201) o ya existia (200). */
	public record Emision(FacturaResponse factura, boolean creada) {
	}

	/** Un PDF listo para descargar. */
	public record PdfFactura(String nombreFichero, byte[] contenido) {
	}

	// ---- emitir ----

	@Transactional
	public Emision emitir(Long reservaId, EmitirFacturaRequest peticion) {
		Usuario actor = autorizacion.usuarioActual();
		// con bloqueo de la fila: emitir, cancelar y emitir otra vez la misma reserva se serializan aqui
		Reserva reserva = reservas.findByIdParaActualizar(reservaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));
		Club club = reserva.getPista().getClub();
		exigirOrganizadorOAdmin(actor, reserva);
		Receptor receptor = receptor(peticion, reserva.getUsuario());

		if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
			throw new ConflictoException("Solo se pueden facturar reservas confirmadas");
		}
		Optional<Factura> existente = facturas.findByReservaIdAndTipo(reservaId, TipoFactura.ORIGINAL);
		if (existente.isPresent()) {
			return new Emision(FacturaResponse.de(existente.get()), false); // idempotente: se devuelve la que ya hay
		}
		if (!club.tieneDatosFiscales()) {
			throw new ConflictoException("El club no ha configurado sus datos fiscales");
		}
		BigDecimal total = reserva.getPrecioTotal();
		if (total.signum() <= 0) {
			throw new ConflictoException("La reserva no tiene importe que facturar");
		}

		CalculadoraIva.Desglose desglose = CalculadoraIva.desglosar(total, club.getTipoIva());
		// emitidaEn se lee ANTES de tomar el contador (a proposito: asi el anio del numero y el de la fecha coinciden en el
		// cambio de anio), asi que bajo concurrencia el orden de los numeros puede diferir del de las fechas
		Instant ahora = ahora();
		int anio = ahora.atZone(zona).getYear();
		Factura factura = new Factura();
		factura.setClub(club);
		factura.setReserva(reserva);
		factura.setReceptorUsuario(reserva.getUsuario());
		factura.setTipo(TipoFactura.ORIGINAL);
		factura.setEmitidaEn(ahora);
		factura.setEmisorRazonSocial(club.getRazonSocial());
		factura.setEmisorNif(club.getNif());
		factura.setEmisorDireccion(club.getDireccionFiscal());
		factura.setReceptorNombre(receptor.nombre());
		factura.setReceptorNif(receptor.nif());
		factura.setReceptorDireccion(receptor.direccion());
		factura.setConcepto(abreviar("Alquiler de pista: " + textos.describir(reserva), MAX_CONCEPTO));
		factura.setBase(desglose.base());
		factura.setTipoIva(club.getTipoIva());
		factura.setCuotaIva(desglose.cuota());
		factura.setTotal(total.setScale(2));
		// El numero se toma al final: el bloqueo del contador dura hasta el commit y no debe retener nada mas que lo necesario
		numerar(factura, club, anio);
		return new Emision(FacturaResponse.de(facturas.saveAndFlush(factura)), true);
	}

	/**
	 * Al cancelar una reserva: si tiene factura original y aun no tiene rectificativa, emite la rectificativa (copia de
	 * la original con los importes en negativo, numerada con el anio de ESTA emision) y avisa al receptor. Si la reserva
	 * nunca se facturo, no hace nada; si ya tiene rectificativa, tampoco (idempotente). El llamador ya ha BLOQUEADO la
	 * fila de la reserva y toca los cargos antes: esta llamada toma el contador de la serie despues, y es lo ultimo.
	 */
	@Transactional
	public void rectificarSiFacturada(Reserva reserva) {
		Factura original = facturas.findByReservaIdAndTipo(reserva.getId(), TipoFactura.ORIGINAL).orElse(null);
		if (original == null || facturas.existsByRectificaAId(original.getId())) {
			return;
		}
		Instant ahora = ahora();
		int anio = ahora.atZone(zona).getYear();
		Factura rectificativa = new Factura();
		rectificativa.setClub(original.getClub());
		rectificativa.setReserva(reserva);
		rectificativa.setReceptorUsuario(original.getReceptorUsuario());
		rectificativa.setTipo(TipoFactura.RECTIFICATIVA);
		rectificativa.setRectificaA(original);
		rectificativa.setEmitidaEn(ahora);
		rectificativa.setEmisorRazonSocial(original.getEmisorRazonSocial());
		rectificativa.setEmisorNif(original.getEmisorNif());
		rectificativa.setEmisorDireccion(original.getEmisorDireccion());
		rectificativa.setReceptorNombre(original.getReceptorNombre());
		rectificativa.setReceptorNif(original.getReceptorNif());
		rectificativa.setReceptorDireccion(original.getReceptorDireccion());
		rectificativa.setConcepto(original.getConcepto());
		rectificativa.setBase(original.getBase().negate());
		rectificativa.setTipoIva(original.getTipoIva());
		rectificativa.setCuotaIva(original.getCuotaIva().negate());
		rectificativa.setTotal(original.getTotal().negate());
		// Se vuelcan YA los cambios pendientes de la cancelacion (la reserva y sus cargos): asi el contador, que es lo ultimo
		// que se bloquea, se toma de verdad despues de ellos
		facturas.flush();
		numerar(rectificativa, original.getClub(), anio);
		Factura guardada = facturas.saveAndFlush(rectificativa);
		notificaciones.notificar(original.getReceptorUsuario(), TipoNotificacion.FACTURA_RECTIFICATIVA,
				textos.rectificativa(guardada.numeroCompleto(), original.numeroCompleto(), guardada.getTotal(), reserva),
				reserva);
	}

	// ---- consultar ----

	/** Original y rectificativa (si la hay) de una reserva: el organizador y el administrador de su club. */
	@Transactional(readOnly = true)
	public List<FacturaResponse> deReserva(Long reservaId) {
		Usuario actor = autorizacion.usuarioActual();
		Reserva reserva = reservas.findById(reservaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));
		exigirOrganizadorOAdmin(actor, reserva);
		return facturas.findByReservaIdOrderByIdAsc(reservaId).stream().map(FacturaResponse::de).toList();
	}

	/** Las facturas de las que soy receptor, las mas recientes primero. */
	@Transactional(readOnly = true)
	public List<FacturaResponse> mias(int pagina, int limite) {
		validarPagina(pagina, limite);
		Long usuarioId = autorizacion.usuarioActual().getId();
		return facturas.deReceptor(usuarioId, PageRequest.of(pagina, Math.min(limite, LIMITE_MAXIMO))).stream()
				.map(FacturaResponse::de).toList();
	}

	/**
	 * Facturas del club para su administrador, con filtro opcional por fechas (dia local de Madrid, ambos extremos
	 * inclusivos) y por tipo. Los totales son los de todo el conjunto filtrado, no solo los de la pagina.
	 */
	@Transactional(readOnly = true)
	public ListadoFacturasClubResponse delClub(Long clubId, LocalDate desde, LocalDate hasta, TipoFactura tipo, int pagina,
			int limite) {
		clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
		autorizacion.exigirAdminDe(clubId);
		validarPagina(pagina, limite);
		LimitesFecha.validar(desde);
		LimitesFecha.validar(hasta);
		if (desde != null && hasta != null && desde.isAfter(hasta)) {
			throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
		}
		Instant principio = desde == null ? PRINCIPIO : desde.atStartOfDay(zona).toInstant();
		Instant fin = hasta == null ? FIN : hasta.plusDays(1).atStartOfDay(zona).toInstant();
		List<TipoFactura> tipos = tipo == null ? List.of(TipoFactura.values()) : List.of(tipo);
		int limiteReal = Math.min(limite, LIMITE_MAXIMO);

		List<FacturaResponse> filas = facturas.delClub(clubId, principio, fin, tipos, PageRequest.of(pagina, limiteReal))
				.stream().map(FacturaResponse::de).toList();
		FacturaRepository.Totales t = facturas.totalesDelClub(clubId, principio, fin, tipos);
		return new ListadoFacturasClubResponse(filas,
				new ListadoFacturasClubResponse.Totales(t.getCantidad(), suma(t.getBase()), suma(t.getCuotaIva()),
						suma(t.getTotal())),
				pagina, limiteReal);
	}

	/** El receptor y el administrador del club emisor; para cualquier otro la factura no existe (404). */
	@Transactional(readOnly = true)
	public FacturaResponse obtener(Long id) {
		return FacturaResponse.de(visible(id));
	}

	@Transactional(readOnly = true)
	public PdfFactura pdf(Long id) {
		Factura factura = visible(id);
		String rectificada = factura.getRectificaA() == null ? null : factura.getRectificaA().numeroCompleto();
		byte[] contenido = GeneradorPdf.generar(FacturaResponse.de(factura), rectificada);
		return new PdfFactura(factura.numeroCompleto().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf", contenido);
	}

	// ---- ayudas ----

	private Factura visible(Long id) {
		Usuario actor = autorizacion.usuarioActual();
		Factura factura = facturas.findById(id).orElse(null);
		boolean esReceptor = factura != null && factura.getReceptorUsuario().getId().equals(actor.getId());
		if (factura == null || !(esReceptor || AutorizacionClub.esAdminDe(actor, factura.getClub().getId()))) {
			throw new RecursoNoEncontradoException("Factura no encontrada");
		}
		return factura;
	}

	private static void exigirOrganizadorOAdmin(Usuario actor, Reserva reserva) {
		boolean organizador = reserva.getUsuario().getId().equals(actor.getId());
		if (!organizador && !AutorizacionClub.esAdminDe(actor, reserva.getPista().getClub().getId())) {
			throw new AccessDeniedException("Acceso denegado");
		}
	}

	/** Toma el siguiente numero de la serie del anio y lo deja puesto en la factura. */
	private void numerar(Factura factura, Club club, int anio) {
		factura.setSerie(NumeroFactura.serie(anio));
		factura.setNumero(numeracion.siguiente(club.getId(), anio));
	}

	private record Receptor(String nombre, String nif, String direccion) {
	}

	/** Nombre, NIF y direccion del receptor: lo pedido (limpio) o, sin nombre, el nombre del organizador. */
	private static Receptor receptor(EmitirFacturaRequest peticion, Usuario organizador) {
		String nombre = peticion == null ? null : limpiar(peticion.nombre());
		String nif = peticion == null ? null : peticion.nif();
		String direccion = peticion == null ? null : limpiar(peticion.direccion());
		if (nif != null && !nif.isBlank() && !ValidadorNif.esValido(nif)) {
			throw new IllegalArgumentException("El NIF/CIF no es válido");
		}
		return new Receptor(nombre != null ? nombre : abreviar(limpiar(organizador.nombreCompleto()), MAX_NOMBRE),
				nif == null || nif.isBlank() ? null : ValidadorNif.normalizar(nif),
				direccion == null ? null : abreviar(direccion, MAX_DIRECCION));
	}

	/** Saltos de linea y tabuladores a espacios, espacios repetidos a uno y nulo si queda vacio. */
	private static String limpiar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
		return limpio.isEmpty() ? null : limpio;
	}

	/** Recorta a {@code maximo} caracteres sin partir un par sustituto (un emoji) por la mitad. */
	private static String abreviar(String texto, int maximo) {
		if (texto.length() <= maximo) {
			return texto;
		}
		int fin = Character.isHighSurrogate(texto.charAt(maximo - 1)) ? maximo - 1 : maximo;
		return texto.substring(0, fin);
	}

	private static BigDecimal suma(BigDecimal valor) {
		return valor == null ? BigDecimal.ZERO.setScale(2) : valor;
	}

	private static void validarPagina(int pagina, int limite) {
		if (pagina < 0) {
			throw new IllegalArgumentException("La página no puede ser negativa");
		}
		if (limite < 1) {
			throw new IllegalArgumentException("El límite debe ser al menos 1");
		}
	}

	/** Microsegundos, la precision de la BD. */
	private Instant ahora() {
		return reloj.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
