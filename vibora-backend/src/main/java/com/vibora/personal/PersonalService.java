package com.vibora.personal;

import com.vibora.auth.AutorizacionClub;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.incidencia.IncidenciaService;
import com.vibora.notificacion.NotificacionService;
import com.vibora.notificacion.TextosAviso;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.usuario.BloqueoUsuario;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Alta y baja del personal de un club (solo su administrador). Orden de comprobaciones: club (404) -> administrador
 * del club (403) -> datos (400) -> persona (404) -> estado de la persona (409).
 * <p>
 * Alta y baja bloquean la fila de la persona (SELECT ... FOR UPDATE) antes de decidir: dos altas simultaneas de la
 * misma persona, o una asignacion de incidencia mientras se la retira, se serializan y la segunda ve el resultado de
 * la primera.
 * <p>
 * OJO con el token: el rol viaja en el JWT. La persona anadida o retirada conserva su token anterior (JUGADOR, o
 * MONITOR/MANTENIMIENTO) hasta que expire o vuelva a iniciar sesion, igual que al crear un club. Eso no abre ni
 * deja abierto nada: los permisos de club se leen SIEMPRE de la BD, nunca del token.
 */
@Service
@RequiredArgsConstructor
public class PersonalService {

	private static final Set<Rol> ROLES_ALTA = Set.of(Rol.MONITOR, Rol.MANTENIMIENTO);

	private final UsuarioRepository usuarios;
	private final ClubRepository clubs;
	private final AutorizacionClub autorizacion;
	private final NotificacionService notificaciones;
	private final IncidenciaService incidencias;
	private final BloqueoUsuario bloqueo;

	/** Administradores del club (marcados como ADMIN_CLUB) y su personal, administradores primero. */
	@Transactional(readOnly = true)
	public List<PersonalResponse> listar(Long clubId) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		return usuarios.findPersonalDelClub(clubId, List.of(Rol.ADMIN_CLUB, Rol.MONITOR, Rol.MANTENIMIENTO)).stream()
				.map(PersonalResponse::de).toList();
	}

	@Transactional
	public PersonalResponse anadir(Long clubId, PersonalRequest peticion) {
		Club club = buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		if (!ROLES_ALTA.contains(peticion.rol())) {
			throw new IllegalArgumentException("El rol debe ser MONITOR o MANTENIMIENTO");
		}
		String email = peticion.email().trim().toLowerCase(Locale.ROOT); // igual que el registro
		Usuario persona = usuarios.findByEmail(email).filter(Usuario::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe ningún usuario con ese email"));
		bloqueo.bloquear(persona);
		// cualquier pertenencia a un club (este u otro, con cualquier rol) lo impide; solo un JUGADOR libre puede entrar
		if (persona.getRol() != Rol.JUGADOR || persona.getClub() != null) {
			throw new ConflictoException("Ese usuario ya pertenece a un club");
		}
		persona.setRol(peticion.rol());
		persona.setClub(club);
		usuarios.saveAndFlush(persona);
		notificaciones.notificar(persona, TipoNotificacion.PERSONAL_ANADIDO,
				"Ahora formas parte del personal de %s como %s. Vuelve a iniciar sesión para que tu sesión lo refleje."
						.formatted(club.getNombre(), TextosAviso.describir(peticion.rol())), null);
		return PersonalResponse.de(persona);
	}

	/** 404 si no es personal de ESTE club; 409 si es administrador (los administradores no se quitan por aqui). */
	@Transactional
	public void quitar(Long clubId, Long usuarioId) {
		Club club = buscarClub(clubId);
		Usuario admin = autorizacion.exigirAdminDe(clubId);
		Usuario persona = usuarios.findById(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Personal no encontrado"));
		bloqueo.bloquear(persona);
		if (persona.getClub() == null || !persona.getClub().getId().equals(clubId) || persona.getRol() == Rol.JUGADOR) {
			throw new RecursoNoEncontradoException("Personal no encontrado");
		}
		if (persona.getRol() == Rol.ADMIN_CLUB) {
			throw new ConflictoException("No se puede quitar a un administrador del club");
		}
		// antes de cambiar el rol: la BD exige responsable en las incidencias asignadas o en curso
		incidencias.desasignarActivasDe(persona, admin, clubId);
		persona.setRol(Rol.JUGADOR);
		persona.setClub(null);
		usuarios.saveAndFlush(persona);
		notificaciones.notificar(persona, TipoNotificacion.PERSONAL_QUITADO,
				"Has dejado de formar parte del personal de %s".formatted(club.getNombre()), null);
	}

	private Club buscarClub(Long clubId) {
		return clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}
}
