package com.vibora.tarifa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface TarifaFranjaRepository extends JpaRepository<TarifaFranja, Long> {

	List<TarifaFranja> findByClubIdOrderByDiaSemanaAscHoraInicioAsc(Long clubId);

	List<TarifaFranja> findByClubId(Long clubId);

	List<TarifaFranja> findByClubIdAndDiaSemana(Long clubId, DayOfWeek diaSemana);

	Optional<TarifaFranja> findByIdAndClubId(Long id, Long clubId);
}
