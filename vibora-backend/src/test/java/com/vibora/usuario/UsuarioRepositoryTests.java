package com.vibora.usuario;

import com.vibora.PostgresTestBase;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static com.vibora.TestData.club;
import static com.vibora.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTests extends PostgresTestBase {

	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;

	@Test
	void buscaPorEmail() {
		usuarios.saveAndFlush(usuario("a@vibora.com", Rol.JUGADOR, null));

		assertThat(usuarios.findByEmail("a@vibora.com")).isPresent();
		assertThat(usuarios.findByEmail("otro@vibora.com")).isEmpty();
	}

	@Test
	void rechazaEmailDuplicado() {
		usuarios.saveAndFlush(usuario("dup@vibora.com", Rol.JUGADOR, null));

		assertThatThrownBy(() -> usuarios.saveAndFlush(usuario("dup@vibora.com", Rol.JUGADOR, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = {"ADMIN_CLUB", "MONITOR", "MANTENIMIENTO"})
	void rechazaPersonalSinClub(Rol rol) {
		assertThatThrownBy(() -> usuarios.saveAndFlush(usuario("staff@vibora.com", rol, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = {"ADMIN_CLUB", "MONITOR", "MANTENIMIENTO"})
	void aceptaPersonalConClub(Rol rol) {
		Club c = clubs.save(club());

		Usuario u = usuarios.saveAndFlush(usuario("staff@vibora.com", rol, c));

		assertThat(usuarios.findById(u.getId())).get().extracting(Usuario::getRol).isEqualTo(rol);
	}

	@Test
	void aceptaJugadorSinClub() {
		Usuario u = usuarios.saveAndFlush(usuario("j@vibora.com", Rol.JUGADOR, null));

		assertThat(u.getId()).isNotNull();
		assertThat(u.getCreadoEn()).isNotNull();
		assertThat(u.isActivo()).isTrue();
	}
}
