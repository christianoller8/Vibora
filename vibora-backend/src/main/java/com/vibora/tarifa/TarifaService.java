package com.vibora.tarifa;

import com.vibora.auth.AutorizacionClub;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.common.exception.ConflictoException;
import com.vibora.common.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Orden de comprobaciones: club (404) -> administrador del club (403, solo escrituras) -> tarifa (404). */
@Service
@RequiredArgsConstructor
public class TarifaService {

	private final TarifaFranjaRepository tarifas;
	private final ClubRepository clubs;
	private final AutorizacionClub autorizacion;

	@Transactional(readOnly = true)
	public List<TarifaResponse> listar(Long clubId) {
		buscarClub(clubId);
		return tarifas.findByClubIdOrderByDiaSemanaAscHoraInicioAsc(clubId).stream().map(TarifaResponse::de).toList();
	}

	@Transactional
	public TarifaResponse crear(Long clubId, TarifaRequest peticion) {
		Club club = buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		validarHoras(peticion);
		comprobarSolape(clubId, peticion, null);
		TarifaFranja tarifa = new TarifaFranja();
		tarifa.setClub(club);
		aplicar(tarifa, peticion);
		return TarifaResponse.de(tarifas.saveAndFlush(tarifa));
	}

	@Transactional
	public TarifaResponse actualizar(Long clubId, Long id, TarifaRequest peticion) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		TarifaFranja tarifa = buscarTarifa(clubId, id);
		validarHoras(peticion);
		comprobarSolape(clubId, peticion, id);
		aplicar(tarifa, peticion);
		return TarifaResponse.de(tarifas.saveAndFlush(tarifa));
	}

	/** Borrado real: las reservas congelan su precio, no referencian la tarifa. */
	@Transactional
	public void eliminar(Long clubId, Long id) {
		buscarClub(clubId);
		autorizacion.exigirAdminDe(clubId);
		tarifas.delete(buscarTarifa(clubId, id));
	}

	private static void validarHoras(TarifaRequest peticion) {
		if (!peticion.horaFin().isAfter(peticion.horaInicio())) {
			throw new IllegalArgumentException("horaFin debe ser posterior a horaInicio");
		}
	}

	/** Franjas semiabiertas [inicio, fin): una puede empezar justo donde acaba otra. */
	private void comprobarSolape(Long clubId, TarifaRequest peticion, Long idPropio) {
		boolean solapa = tarifas.findByClubIdAndDiaSemana(clubId, peticion.diaSemana()).stream()
				.filter(otra -> !otra.getId().equals(idPropio))
				.anyMatch(otra -> peticion.horaInicio().isBefore(otra.getHoraFin())
						&& otra.getHoraInicio().isBefore(peticion.horaFin()));
		if (solapa) {
			throw new ConflictoException("La franja se solapa con otra del mismo día");
		}
	}

	private static void aplicar(TarifaFranja tarifa, TarifaRequest peticion) {
		tarifa.setDiaSemana(peticion.diaSemana());
		tarifa.setHoraInicio(peticion.horaInicio());
		tarifa.setHoraFin(peticion.horaFin());
		tarifa.setPrecioHora(peticion.precioHora());
		tarifa.setTipo(peticion.tipo());
	}

	private Club buscarClub(Long clubId) {
		return clubs.findById(clubId).orElseThrow(() -> new RecursoNoEncontradoException("Club no encontrado"));
	}

	private TarifaFranja buscarTarifa(Long clubId, Long id) {
		return tarifas.findByIdAndClubId(id, clubId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Tarifa no encontrada"));
	}
}
