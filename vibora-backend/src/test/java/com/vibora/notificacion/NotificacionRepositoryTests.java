package com.vibora.notificacion;

import com.vibora.PostgresTestBase;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.vibora.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificacionRepositoryTests extends PostgresTestBase {

	@Autowired UsuarioRepository usuarios;
	@Autowired NotificacionRepository notificaciones;
	@Autowired JdbcTemplate jdbc;

	Usuario ana;

	@BeforeEach
	void setUp() {
		ana = usuarios.save(usuario("ana@vibora.com", Rol.JUGADOR, null));
	}

	@Test
	void guardaNotificacionSinLeerYSinReserva() {
		Notificacion n = notificaciones.saveAndFlush(notificacion(ana, TipoNotificacion.CARGO_NUEVO, "Debes 11,25 €"));

		assertThat(n.getId()).isNotNull();
		assertThat(n.isLeida()).isFalse();
		assertThat(n.getReserva()).isNull();
	}

	@Test
	void tipoDesconocido_violaTipoCk() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into notificacion (usuario_id, tipo, mensaje) values (?, 'OTRA_COSA', 'x')", ana.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("notificacion_tipo_ck"));
	}

	@Test
	void mensajeObligatorio() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into notificacion (usuario_id, tipo) values (?, 'CARGO_NUEVO')", ana.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
