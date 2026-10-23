package com.vibora.club;

import com.vibora.auth.AutorizacionClub;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClubService {

	private final ClubRepository clubs;
	private final UsuarioRepository usuarios;
	private final AutorizacionClub autorizacion;

	@Transactional(readOnly = true)
	public List<ClubResponse> listar() {
		return clubs.findAll().stream().map(ClubResponse::de).toList();
	}

	@Transactional(readOnly = true)
	public ClubResponse obtener(Long id) {
		return ClubResponse.de(buscar(id));
	}

	/**
	 * Onboarding: un JUGADOR sin club crea su club y, en la misma transaccion, pasa a ser su ADMIN_CLUB.
	 * OJO: el JWT que ya tiene el usuario sigue diciendo JUGADOR (el rol viaja en el token);
	 * debe volver a iniciar sesion para obtener un token con rol ADMIN_CLUB.
	 */
	@Transactional
	public ClubResponse crear(ClubRequest peticion) {
		Usuario usuario = autorizacion.usuarioActual();
		if (usuario.getRol() != Rol.JUGADOR || usuario.getClub() != null) {
			throw new ConflictoException("Ya perteneces a un club");
		}
		validarHorario(peticion);
		Club club = new Club();
		aplicar(club, peticion);
		club = clubs.save(club);
		usuario.setRol(Rol.ADMIN_CLUB);
		usuario.setClub(club);
		usuarios.save(usuario);
		return ClubResponse.de(club);
	}

	/** Orden: 404 si el club no existe, luego 403 si no es su administrador, luego 400 por datos. */
	@Transactional
	public ClubResponse actualizar(Long id, ClubRequest peticion) {
		Club club = buscar(id);
		autorizacion.exigirAdminDe(id);
		validarHorario(peticion);
		aplicar(club, peticion);
		return ClubResponse.de(clubs.save(club));
	}

	private Club buscar(Long id) {
		return clubs.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}

	private static void validarHorario(ClubRequest peticion) {
		if (!peticion.horaCierre().isAfter(peticion.horaApertura())) {
			throw new IllegalArgumentException("La hora de cierre debe ser posterior a la de apertura");
		}
	}

	private static void aplicar(Club club, ClubRequest peticion) {
		club.setNombre(peticion.nombre().trim());
		club.setDireccion(peticion.direccion());
		club.setCiudad(peticion.ciudad());
		club.setHoraApertura(peticion.horaApertura());
		club.setHoraCierre(peticion.horaCierre());
	}
}
