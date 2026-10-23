package com.vibora.pista;

import com.vibora.auth.AutorizacionClub;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Orden de comprobaciones en todas las operaciones: club (404) -> administrador del club (403) -> pista (404). */
@Service
@RequiredArgsConstructor
public class PistaService {

	private final PistaRepository pistas;
	private final ClubRepository clubs;
	private final AutorizacionClub autorizacion;

	/** El administrador del club ve todas las pistas; el resto, solo las activas. */
	@Transactional(readOnly = true)
	public List<PistaResponse> listar(Long clubId) {
		buscarClub(clubId);
		List<Pista> resultado = autorizacion.esAdminDe(clubId)
				? pistas.findByClubIdOrderByNombre(clubId)
				: pistas.findByClubIdAndActivaTrueOrderByNombre(clubId);
		return resultado.stream().map(PistaResponse::de).toList();
	}

	@Transactional
	public PistaResponse crear(Long clubId, PistaRequest peticion) {
		Club club = buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		String nombre = peticion.nombre().trim();
		if (pistas.existsByClubIdAndNombre(clubId, nombre)) {
			throw nombreDuplicado();
		}
		Pista pista = new Pista();
		pista.setClub(club);
		pista.setNombre(nombre);
		pista.setTipo(peticion.tipo());
		pista.setActiva(true);
		return guardar(pista);
	}

	@Transactional
	public PistaResponse actualizar(Long clubId, Long id, PistaRequest peticion) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		Pista pista = buscarPista(clubId, id);
		String nombre = peticion.nombre().trim();
		if (pistas.existsByClubIdAndNombreAndIdNot(clubId, nombre, id)) {
			throw nombreDuplicado();
		}
		pista.setNombre(nombre);
		pista.setTipo(peticion.tipo());
		if (peticion.activa() != null) {
			pista.setActiva(peticion.activa());
		}
		return guardar(pista);
	}

	/** Baja logica (activa = false): las reservas referencian pistas. Idempotente. */
	@Transactional
	public void desactivar(Long clubId, Long id) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		Pista pista = buscarPista(clubId, id);
		pista.setActiva(false);
		pistas.save(pista);
	}

	private PistaResponse guardar(Pista pista) {
		try {
			return PistaResponse.de(pistas.saveAndFlush(pista));
		} catch (DataIntegrityViolationException ex) {
			// Carrera: otra peticion creo el mismo nombre entre la comprobacion y el insert
			throw nombreDuplicado();
		}
	}

	private Club buscarClub(Long clubId) {
		return clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}

	private Pista buscarPista(Long clubId, Long id) {
		return pistas.findByIdAndClubId(id, clubId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Pista no encontrada"));
	}

	private static ConflictoException nombreDuplicado() {
		return new ConflictoException("Ya existe una pista con ese nombre en el club");
	}
}
