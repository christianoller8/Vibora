package com.vibora.incidencia;

import com.vibora.auth.AutorizacionClub;
import com.vibora.bloqueo.BloqueoCreadoResponse;
import com.vibora.bloqueo.BloqueoPistaRepository;
import com.vibora.bloqueo.BloqueoService;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.notificacion.NotificacionService;
import com.vibora.notificacion.TextosAviso;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.partido.ParticipanteRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.ReservaRepository;
import com.vibora.usuario.BloqueoUsuario;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incidencias de un club. Orden de comprobaciones: recurso (404) -> visibilidad (404 si no la ves, para no filtrar
 * ids) -> permiso para la accion (403) -> estado/regla de negocio (409) -> datos (400).
 * <p>
 * Concurrencia:
 * <ul>
 * <li>Cambios sobre una incidencia: bloqueo optimista ({@code @Version}). Se hace flush ANTES de crear eventos y
 * avisos, asi el perdedor falla sin dejar rastro y el manejador global lo traduce a 409.</li>
 * <li>Limite de reportes por persona: contar y luego insertar tiene carrera (varias peticiones ven "4" a la vez).
 * Se serializa bloqueando la fila del reportero (SELECT ... FOR UPDATE) antes de contar: las demas peticiones de esa
 * misma persona esperan, y al entrar (READ COMMITTED) ya ven lo que confirmo la anterior.</li>
 * <li>Asignar a alguien mientras se le retira del personal: ambas operaciones bloquean la fila de esa persona antes
 * de decidir, en el mismo orden (usuario y despues incidencia), asi que no se cruzan.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IncidenciaService {

	static final int MAXIMO_SIN_TERMINAR = 5;
	static final int LIMITE_POR_DEFECTO = 20;
	static final int LIMITE_MAXIMO = 100;
	/** Capacidad de incidencia_evento.texto (varchar(2000), V3). */
	private static final int MAX_TEXTO_EVENTO = 2000;

	private static final String RESPONSABLE_INVALIDO = "El responsable debe ser personal de mantenimiento de este club";

	/** Transiciones que admite el endpoint de estado. ABIERTA -> ASIGNADA y el regreso a ABIERTA van por otras vias. */
	private static final Map<EstadoIncidencia, Set<EstadoIncidencia>> TRANSICIONES = Map.of(
			EstadoIncidencia.ABIERTA, Set.of(EstadoIncidencia.RECHAZADA),
			EstadoIncidencia.ASIGNADA, Set.of(EstadoIncidencia.EN_CURSO),
			EstadoIncidencia.EN_CURSO, Set.of(EstadoIncidencia.RESUELTA),
			EstadoIncidencia.RESUELTA, Set.of(EstadoIncidencia.CERRADA, EstadoIncidencia.EN_CURSO));

	private static final Set<EstadoIncidencia> ASIGNABLES = Set.of(EstadoIncidencia.ABIERTA, EstadoIncidencia.ASIGNADA,
			EstadoIncidencia.EN_CURSO);

	/** Estados a los que se llega al terminar el problema (o descartarlo): el bloqueo de pista ligado se levanta. */
	private static final Set<EstadoIncidencia> LIBERAN_LA_PISTA = Set.of(EstadoIncidencia.RESUELTA,
			EstadoIncidencia.CERRADA, EstadoIncidencia.RECHAZADA);

	private final IncidenciaRepository incidencias;
	private final IncidenciaEventoRepository eventos;
	private final ClubRepository clubs;
	private final PistaRepository pistas;
	private final UsuarioRepository usuarios;
	private final ReservaRepository reservas;
	private final ParticipanteRepository participantes;
	private final AutorizacionClub autorizacion;
	private final NotificacionService notificaciones;
	private final TextosAviso textos;
	private final BloqueoUsuario bloqueo;
	private final HistorialIncidencia historial;
	private final IncidenciaFotoRepository fotos;
	private final BloqueoPistaRepository bloqueosPista;
	private final BloqueoService bloqueoService;
	private final Clock reloj;

	// ---- crear ----

	/**
	 * Puede reportar el personal del club (cualquier rol) y quien ha reservado o jugado alguna vez en una pista del
	 * club (reservas canceladas incluidas: es un vinculo pasado). El resto, 403.
	 */
	@Transactional
	public IncidenciaDetalleResponse crear(Long clubId, IncidenciaRequest peticion) {
		Club club = buscarClub(clubId);
		Usuario actor = autorizacion.usuarioActual();
		if (!puedeReportar(actor, clubId)) {
			throw new AccessDeniedException("Acceso denegado");
		}
		Pista pista = peticion.pistaId() == null ? null : pistas.findByIdAndClubId(peticion.pistaId(), clubId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pista no encontrada"));

		bloqueo.bloquear(actor); // serializa los reportes de esta persona: ver la nota de concurrencia de la clase
		if (incidencias.countByReportadaPorIdAndClubIdAndEstadoNotIn(actor.getId(), clubId,
				EstadoIncidencia.TERMINALES) >= MAXIMO_SIN_TERMINAR) {
			throw new ConflictoException("Ya tienes %d incidencias sin cerrar en este club; espera a que se resuelvan"
					.formatted(MAXIMO_SIN_TERMINAR));
		}

		Instant ahora = ahora();
		Incidencia incidencia = new Incidencia();
		incidencia.setClub(club);
		incidencia.setPista(pista);
		incidencia.setReportadaPor(actor);
		incidencia.setTitulo(peticion.titulo().trim());
		incidencia.setDescripcion(peticion.descripcion().trim());
		incidencia.setCategoria(peticion.categoria());
		incidencia.setPrioridad(PrioridadIncidencia.MEDIA);
		incidencia.setEstado(EstadoIncidencia.ABIERTA);
		incidencia.setCreadaEn(ahora);
		incidencia.setActualizadaEn(ahora);
		incidencias.saveAndFlush(incidencia);
		registrar(incidencia, actor, TipoEventoIncidencia.CREADA, null, EstadoIncidencia.ABIERTA.name(), null);

		String aviso = "Nueva incidencia %s: %s".formatted(textos.describir(incidencia), incidencia.getDescripcion());
		for (Usuario destinatario : administradoresDe(clubId)) {
			if (!destinatario.getId().equals(actor.getId())) {
				notificaciones.notificar(destinatario, TipoNotificacion.INCIDENCIA_NUEVA, aviso, null);
			}
		}
		return detalle(incidencia);
	}

	private boolean puedeReportar(Usuario actor, Long clubId) {
		return AutorizacionClub.esPersonalDe(actor, clubId)
				|| reservas.existsByUsuarioIdAndPistaClubId(actor.getId(), clubId)
				|| participantes.existsByUsuarioIdAndReservaPistaClubId(actor.getId(), clubId);
	}

	// ---- consultar ----

	/**
	 * Visibilidad: el administrador ve todas las del club; el resto, solo las que reporto y, si es de mantenimiento
	 * del club, las que tiene asignadas. Quien no tiene nada (o no tiene relacion con el club) recibe una lista vacia,
	 * no un 403: es lo que ve cualquiera que aun no ha reportado nada, y no revela nada del club.
	 */
	@Transactional(readOnly = true)
	public List<IncidenciaResumenResponse> listar(Long clubId, EstadoIncidencia estado, Long pistaId, Long asignadaA,
			int pagina, int limite) {
		if (pagina < 0) {
			throw new IllegalArgumentException("La página no puede ser negativa");
		}
		if (limite < 1) {
			throw new IllegalArgumentException("El límite debe ser al menos 1");
		}
		buscarClub(clubId);
		Usuario actor = autorizacion.usuarioActual();
		boolean todas = AutorizacionClub.esAdminDe(actor, clubId);
		boolean mantenimiento = AutorizacionClub.esMantenimientoDe(actor, clubId);
		return incidencias.listar(clubId, todas, actor.getId(), mantenimiento, estado, pistaId, asignadaA,
						PageRequest.of(pagina, Math.min(limite, LIMITE_MAXIMO)))
				.stream().map(IncidenciaResumenResponse::de).toList();
	}

	/** Visible para quien la reporto, la persona de mantenimiento asignada y el administrador del club; el resto, 404. */
	@Transactional(readOnly = true)
	public IncidenciaDetalleResponse obtener(Long id) {
		Incidencia incidencia = cargar(id);
		exigirVisible(autorizacion.usuarioActual(), incidencia);
		return detalle(incidencia);
	}

	// ---- asignar ----

	/**
	 * Solo el administrador. Desde ABIERTA, ASIGNADA o EN_CURSO. Reasignar una EN_CURSO la devuelve a ASIGNADA (la
	 * persona nueva empieza de cero). Asignar a quien ya la tiene solo vale para cambiar la prioridad (es la unica
	 * via para hacerlo); sin cambio alguno, 409.
	 */
	@Transactional
	public IncidenciaDetalleResponse asignar(Long id, AsignarRequest peticion) {
		Incidencia incidencia = cargar(id);
		Usuario actor = autorizacion.usuarioActual();
		exigirVisible(actor, incidencia);
		Long clubId = incidencia.getClub().getId();
		if (!AutorizacionClub.esAdminDe(actor, clubId)) {
			throw new AccessDeniedException("Acceso denegado");
		}
		if (!ASIGNABLES.contains(incidencia.getEstado())) {
			throw new ConflictoException("No se puede asignar una incidencia en estado %s"
					.formatted(incidencia.getEstado().etiqueta()));
		}
		// Misma respuesta para inexistente, de otro club o de otro rol: no se revela quien existe
		Usuario destino = usuarios.findById(peticion.usuarioId())
				.orElseThrow(() -> new IllegalArgumentException(RESPONSABLE_INVALIDO));
		bloqueo.bloquear(destino); // si se le esta retirando a la vez, esperamos y lo vemos ya como jugador
		if (!destino.isActivo() || !AutorizacionClub.esMantenimientoDe(destino, clubId)) {
			throw new IllegalArgumentException(RESPONSABLE_INVALIDO);
		}

		PrioridadIncidencia prioridadAnterior = incidencia.getPrioridad();
		boolean cambiaPrioridad = peticion.prioridad() != null && peticion.prioridad() != prioridadAnterior;
		boolean mismoResponsable = incidencia.getAsignadaA() != null
				&& incidencia.getAsignadaA().getId().equals(destino.getId());
		if (mismoResponsable && !cambiaPrioridad) {
			throw new ConflictoException("La incidencia ya está asignada a esa persona");
		}
		EstadoIncidencia estadoAnterior = incidencia.getEstado();
		if (!mismoResponsable) {
			incidencia.setAsignadaA(destino);
			incidencia.setEstado(EstadoIncidencia.ASIGNADA);
		}
		if (cambiaPrioridad) {
			incidencia.setPrioridad(peticion.prioridad());
		}
		incidencia.setActualizadaEn(ahora());
		incidencias.saveAndFlush(incidencia); // aqui salta el bloqueo optimista, antes de crear eventos y avisos

		if (!mismoResponsable) {
			registrar(incidencia, actor, TipoEventoIncidencia.ASIGNADA, estadoAnterior.name(),
					EstadoIncidencia.ASIGNADA.name(), "Asignada a " + destino.nombreCompleto());
			notificaciones.notificar(destino, TipoNotificacion.INCIDENCIA_ASIGNADA,
					"Te han asignado la incidencia %s".formatted(textos.describir(incidencia)), null);
			Usuario reportero = incidencia.getReportadaPor();
			if (!reportero.getId().equals(actor.getId()) && !reportero.getId().equals(destino.getId())) {
				notificaciones.notificar(reportero, TipoNotificacion.INCIDENCIA_ESTADO,
						"Tu incidencia %s ha pasado a %s: se encargará %s".formatted(textos.describir(incidencia),
								EstadoIncidencia.ASIGNADA.etiqueta(), destino.nombreCompleto()), null);
			}
		}
		if (cambiaPrioridad) {
			registrar(incidencia, actor, TipoEventoIncidencia.PRIORIDAD, prioridadAnterior.name(),
					peticion.prioridad().name(), null);
		}
		return detalle(incidencia);
	}

	// ---- cambiar estado ----

	/**
	 * Maquina de estados (ver TRANSICIONES). Quien puede cada paso: rechazar, solo el administrador; iniciar y
	 * resolver, la persona asignada o el administrador; cerrar o reabrir una resuelta, quien la reporto o el
	 * administrador. Reabrir (RESUELTA -> EN_CURSO) si la persona asignada ya no es de mantenimiento del club la deja
	 * ABIERTA y sin responsable, para poder reasignarla.
	 */
	@Transactional
	public IncidenciaDetalleResponse cambiarEstado(Long id, CambioEstadoRequest peticion) {
		Incidencia incidencia = cargar(id);
		Usuario actor = autorizacion.usuarioActual();
		exigirVisible(actor, incidencia);
		Long clubId = incidencia.getClub().getId();
		EstadoIncidencia origen = incidencia.getEstado();
		EstadoIncidencia destino = peticion.estado();
		if (!TRANSICIONES.getOrDefault(origen, Set.of()).contains(destino)) {
			throw new ConflictoException("No se puede pasar de %s a %s".formatted(origen.etiqueta(), destino.etiqueta()));
		}
		boolean admin = AutorizacionClub.esAdminDe(actor, clubId);
		boolean permitido = switch (destino) {
			case RECHAZADA -> admin;
			case RESUELTA -> admin || esAsignado(actor, incidencia);
			case CERRADA -> admin || esReportero(actor, incidencia);
			default -> origen == EstadoIncidencia.RESUELTA // EN_CURSO: reabrir o empezar
					? admin || esReportero(actor, incidencia)
					: admin || esAsignado(actor, incidencia);
		};
		if (!permitido) {
			throw new AccessDeniedException("Acceso denegado");
		}
		String comentario = peticion.comentario() == null || peticion.comentario().isBlank() ? null
				: peticion.comentario().trim();
		if (destino == EstadoIncidencia.RECHAZADA && comentario == null) {
			throw new IllegalArgumentException("Indica el motivo del rechazo");
		}

		Instant ahora = ahora();
		EstadoIncidencia resultado = destino;
		String texto = comentario;
		incidencia.setEstado(destino);
		if (destino == EstadoIncidencia.RESUELTA) {
			incidencia.setResueltaEn(ahora);
		}
		if (origen == EstadoIncidencia.RESUELTA && destino == EstadoIncidencia.EN_CURSO) {
			incidencia.setResueltaEn(null);
			Usuario responsable = incidencia.getAsignadaA();
			if (responsable == null || !AutorizacionClub.esMantenimientoDe(responsable, clubId)) {
				incidencia.setEstado(EstadoIncidencia.ABIERTA);
				incidencia.setAsignadaA(null);
				resultado = EstadoIncidencia.ABIERTA;
				String nota = "la persona asignada ya no pertenece al club; vuelve a estar abierta";
				String sufijo = " (" + nota + ")";
				// incidencia_evento.texto es varchar(2000): se recorta el comentario, nunca la nota
				texto = comentario == null ? nota
						: (comentario.length() + sufijo.length() <= MAX_TEXTO_EVENTO ? comentario
								: comentario.substring(0, MAX_TEXTO_EVENTO - sufijo.length())) + sufijo;
			}
		}
		incidencia.setActualizadaEn(ahora);
		incidencias.saveAndFlush(incidencia); // aqui salta el bloqueo optimista, antes de crear eventos y avisos

		registrar(incidencia, actor, TipoEventoIncidencia.ESTADO, origen.name(), resultado.name(), texto);
		if (LIBERAN_LA_PISTA.contains(resultado)) {
			// Resuelta, cerrada o rechazada: la pista deja de estar bloqueada por esta incidencia, en la misma transaccion
			bloqueoService.levantarDeIncidencia(incidencia, actor, "al pasar la incidencia a " + resultado.etiqueta());
		}
		String aviso = "%s ha cambiado la incidencia %s de %s a %s%s".formatted(actor.nombreCompleto(),
				textos.describir(incidencia), origen.etiqueta(), resultado.etiqueta(),
				comentario == null ? "" : ": " + comentario);
		avisarAInteresados(incidencia, actor, TipoNotificacion.INCIDENCIA_ESTADO, aviso);
		return detalle(incidencia);
	}

	// ---- bloquear la pista ----

	/**
	 * Atajo del administrador: bloquea la pista de la incidencia, con el titulo como motivo y sin fin, desde ahora. El
	 * bloqueo queda ligado a la incidencia y se levanta solo al resolverla, cerrarla o rechazarla.
	 */
	@Transactional
	public BloqueoCreadoResponse bloquearPista(Long id) {
		Incidencia incidencia = cargar(id);
		Usuario actor = autorizacion.usuarioActual();
		exigirVisible(actor, incidencia);
		if (!AutorizacionClub.esAdminDe(actor, incidencia.getClub().getId())) {
			throw new AccessDeniedException("Acceso denegado");
		}
		if (incidencia.getPista() == null) {
			throw new ConflictoException("La incidencia no tiene una pista asociada");
		}
		return bloqueoService.crearBloqueo(actor, incidencia.getPista().getId(), incidencia, incidencia.getTitulo(), null,
				null);
	}

	// ---- comentar ----

	/** Reportero, persona asignada y administrador (los mismos que la ven). No en estados terminales. */
	@Transactional
	public IncidenciaDetalleResponse comentar(Long id, ComentarioRequest peticion) {
		Incidencia incidencia = cargar(id);
		Usuario actor = autorizacion.usuarioActual();
		exigirVisible(actor, incidencia);
		if (incidencia.getEstado().esTerminal()) {
			throw new ConflictoException("No se puede comentar una incidencia %s"
					.formatted(incidencia.getEstado().etiqueta().toLowerCase()));
		}
		String texto = peticion.texto().trim();
		registrar(incidencia, actor, TipoEventoIncidencia.COMENTARIO, null, null, texto);
		avisarAInteresados(incidencia, actor, TipoNotificacion.INCIDENCIA_COMENTARIO,
				"%s ha comentado en la incidencia %s: %s".formatted(actor.nombreCompleto(),
						textos.describir(incidencia), texto));
		return detalle(incidencia);
	}

	// ---- retirada de personal ----

	/**
	 * Devuelve a ABIERTA las incidencias ASIGNADA/EN_CURSO de una persona a la que se retira del personal (la BD
	 * exige responsable en esos estados), dentro de la transaccion de la retirada. Las RESUELTAS conservan su
	 * historial. Avisa a los administradores del club (una vez, con el total). Devuelve cuantas se desasignaron.
	 */
	@Transactional
	public int desasignarActivasDe(Usuario personal, Usuario actor, Long clubId) {
		List<Incidencia> activas = incidencias.findByAsignadaAIdAndEstadoIn(personal.getId(), EstadoIncidencia.ACTIVAS);
		if (activas.isEmpty()) {
			return 0;
		}
		Instant ahora = ahora();
		for (Incidencia incidencia : activas) {
			EstadoIncidencia anterior = incidencia.getEstado();
			incidencia.setEstado(EstadoIncidencia.ABIERTA);
			incidencia.setAsignadaA(null);
			incidencia.setActualizadaEn(ahora);
			incidencias.saveAndFlush(incidencia);
			registrar(incidencia, actor, TipoEventoIncidencia.ASIGNADA, anterior.name(), EstadoIncidencia.ABIERTA.name(),
					"Desasignada: %s ya no forma parte del personal del club".formatted(personal.nombreCompleto()));
		}
		String aviso = (activas.size() == 1 ? "1 incidencia ha vuelto a estar abierta" : activas.size()
				+ " incidencias han vuelto a estar abiertas") + " porque " + personal.nombreCompleto()
				+ " ya no es personal del club: hay que reasignarlas";
		for (Usuario admin : administradoresDe(clubId)) {
			notificaciones.notificar(admin, TipoNotificacion.INCIDENCIA_ESTADO, aviso, null);
		}
		return activas.size();
	}

	// ---- ayudas ----

	private Club buscarClub(Long clubId) {
		return clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}

	private Incidencia cargar(Long id) {
		return incidencias.buscarCompleta(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));
	}

	/** Quien no la ve recibe 404 (no se revela que existe). La reutilizan las fotos y los bloqueos. */
	static void exigirVisible(Usuario actor, Incidencia incidencia) {
		if (!AutorizacionClub.esAdminDe(actor, incidencia.getClub().getId()) && !esReportero(actor, incidencia)
				&& !esAsignado(actor, incidencia)) {
			throw new RecursoNoEncontradoException("Incidencia no encontrada");
		}
	}

	private static boolean esReportero(Usuario usuario, Incidencia incidencia) {
		return incidencia.getReportadaPor().getId().equals(usuario.getId());
	}

	/** Responsable actual Y todavia de mantenimiento del club (un ex-miembro con una incidencia resuelta ya no cuenta). */
	private static boolean esAsignado(Usuario usuario, Incidencia incidencia) {
		return incidencia.getAsignadaA() != null && incidencia.getAsignadaA().getId().equals(usuario.getId())
				&& AutorizacionClub.esMantenimientoDe(usuario, incidencia.getClub().getId());
	}

	private List<Usuario> administradoresDe(Long clubId) {
		return usuarios.findByClubIdAndRol(clubId, Rol.ADMIN_CLUB);
	}

	/** Reportero, responsable (si sigue siendo del club) y administradores, sin repetir y SIN el autor del cambio. */
	private void avisarAInteresados(Incidencia incidencia, Usuario actor, TipoNotificacion tipo, String mensaje) {
		Long clubId = incidencia.getClub().getId();
		Map<Long, Usuario> destinatarios = new LinkedHashMap<>();
		destinatarios.put(incidencia.getReportadaPor().getId(), incidencia.getReportadaPor());
		Usuario responsable = incidencia.getAsignadaA();
		if (responsable != null && AutorizacionClub.esMantenimientoDe(responsable, clubId)) {
			destinatarios.put(responsable.getId(), responsable);
		}
		administradoresDe(clubId).forEach(a -> destinatarios.put(a.getId(), a));
		destinatarios.remove(actor.getId());
		destinatarios.values().forEach(u -> notificaciones.notificar(u, tipo, mensaje, null));
	}

	private IncidenciaEvento registrar(Incidencia incidencia, Usuario autor, TipoEventoIncidencia tipo,
			String anterior, String nuevo, String texto) {
		return historial.registrar(incidencia, autor, tipo, anterior, nuevo, texto);
	}

	private IncidenciaDetalleResponse detalle(Incidencia incidencia) {
		return IncidenciaDetalleResponse.de(incidencia,
				eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(incidencia.getId()),
				fotos.findByIncidenciaIdOrderByIdAsc(incidencia.getId()),
				BloqueoActivoResponse.de(bloqueosPista.findActivoDeIncidencia(incidencia.getId(), ahora()).orElse(null)));
	}

	/** Microsegundos, la precision de la BD. */
	private Instant ahora() {
		return reloj.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
