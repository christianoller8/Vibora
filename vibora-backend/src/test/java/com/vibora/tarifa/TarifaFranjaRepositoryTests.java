package com.vibora.tarifa;

import com.vibora.PostgresTestBase;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

import static com.vibora.TestData.club;
import static com.vibora.TestData.tarifa;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TarifaFranjaRepositoryTests extends PostgresTestBase {

	@Autowired ClubRepository clubs;
	@Autowired TarifaFranjaRepository tarifas;
	@Autowired JdbcTemplate jdbc;

	@Test
	void guardaYRecuperaDiaSemanaYTipo() {
		Club c = clubs.save(club());
		TarifaFranja guardada = tarifas.saveAndFlush(tarifa(c, DayOfWeek.SUNDAY, TipoTarifa.LUZ_ARTIFICIAL));

		// columna cruda: domingo = 7 (ISO)
		Short crudo = jdbc.queryForObject("select dia_semana from tarifa_franja where id = ?", Short.class, guardada.getId());
		assertThat(crudo).isEqualTo((short) 7);

		TarifaFranja leida = tarifas.findById(guardada.getId()).orElseThrow();
		assertThat(leida.getDiaSemana()).isEqualTo(DayOfWeek.SUNDAY);
		assertThat(leida.getTipo()).isEqualTo(TipoTarifa.LUZ_ARTIFICIAL);
		assertThat(leida.getPrecioHora()).isEqualByComparingTo(new BigDecimal("24.50"));
		assertThat(leida.getHoraInicio()).isEqualTo(LocalTime.of(18, 0));
	}

	@Test
	void rechazaHoraFinNoPosteriorAHoraInicio() {
		Club c = clubs.save(club());
		TarifaFranja t = tarifa(c, DayOfWeek.MONDAY, TipoTarifa.NORMAL);
		t.setHoraFin(t.getHoraInicio());

		assertThatThrownBy(() -> tarifas.saveAndFlush(t)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rechazaPrecioNegativo() {
		Club c = clubs.save(club());
		TarifaFranja t = tarifa(c, DayOfWeek.MONDAY, TipoTarifa.NORMAL);
		t.setPrecioHora(new BigDecimal("-1.00"));

		assertThatThrownBy(() -> tarifas.saveAndFlush(t)).isInstanceOf(DataIntegrityViolationException.class);
	}
}
