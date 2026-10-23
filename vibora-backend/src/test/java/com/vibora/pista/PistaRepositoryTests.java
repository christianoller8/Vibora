package com.vibora.pista;

import com.vibora.PostgresTestBase;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static com.vibora.TestData.club;
import static com.vibora.TestData.pista;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PistaRepositoryTests extends PostgresTestBase {

	@Autowired ClubRepository clubs;
	@Autowired PistaRepository pistas;

	@Test
	void rechazaNombreDuplicadoEnElMismoClub() {
		Club c = clubs.save(club());
		pistas.saveAndFlush(pista(c, "Pista 1"));

		assertThatThrownBy(() -> pistas.saveAndFlush(pista(c, "Pista 1")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aceptaMismoNombreEnOtroClub() {
		Club c1 = clubs.save(club());
		Club c2 = clubs.save(club());
		pistas.saveAndFlush(pista(c1, "Pista 1"));

		Pista otra = pistas.saveAndFlush(pista(c2, "Pista 1"));

		assertThat(otra.getId()).isNotNull();
	}
}
