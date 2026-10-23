package com.vibora.partido;

import com.vibora.PostgresTestBase;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.reserva.ReservaRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static com.vibora.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ParticipanteRepositoryTests extends PostgresTestBase {

	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired ReservaRepository reservas;
	@Autowired ParticipanteRepository participantes;

	Usuario organizador;
	Usuario ana;
	Usuario luis;
	Reserva reserva;

	@BeforeEach
	void setUp() {
		Club club = clubs.save(club());
		organizador = usuarios.save(usuario("org@vibora.com", Rol.JUGADOR, null));
		ana = usuarios.save(usuario("ana@vibora.com", Rol.JUGADOR, null));
		luis = usuarios.save(usuario("luis@vibora.com", Rol.JUGADOR, null));
		Pista pista = pistas.save(pista(club, "Pista 1"));
		reserva = reservas.save(reserva(pista, organizador, T0, T0.plusSeconds(5400), EstadoReserva.CONFIRMADA));
	}

	@Test
	void reservaNuevaNoEstaAbiertaPorDefecto() {
		reservas.flush();

		assertThat(reservas.findById(reserva.getId())).get().satisfies(r -> assertThat(r.isAbierta()).isFalse());
	}

	@Test
	void guardaParticipanteYSeLeeDeVuelta() {
		Participante p = participantes.saveAndFlush(participante(reserva, ana, 2));

		assertThat(p.getId()).isNotNull();
		assertThat(participantes.existsByReservaIdAndUsuarioId(reserva.getId(), ana.getId())).isTrue();
		assertThat(participantes.existsByReservaIdAndUsuarioId(reserva.getId(), luis.getId())).isFalse();
		assertThat(participantes.findPuestosOcupados(reserva.getId())).containsExactly((short) 2);
	}

	@Test
	void puestoRepetidoEnLaMismaReserva_violaPuestoUk() {
		participantes.saveAndFlush(participante(reserva, organizador, 1));

		assertThatThrownBy(() -> participantes.saveAndFlush(participante(reserva, ana, 1)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("participante_puesto_uk"));
	}

	@Test
	void usuarioRepetidoEnLaMismaReserva_violaUsuarioUk() {
		participantes.saveAndFlush(participante(reserva, ana, 2));

		assertThatThrownBy(() -> participantes.saveAndFlush(participante(reserva, ana, 3)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("participante_usuario_uk"));
	}

	@Test
	void puestoFueraDeRango_violaPuestoCk() {
		assertThatThrownBy(() -> participantes.saveAndFlush(participante(reserva, ana, 5)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("participante_puesto_ck"));
	}

	@Test
	void puestoCero_violaPuestoCk() {
		assertThatThrownBy(() -> participantes.saveAndFlush(participante(reserva, ana, 0)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("participante_puesto_ck"));
	}

	@Test
	void mismoPuestoEnOtraReservaEsValido() {
		Reserva otra = reservas.save(reserva(reserva.getPista(), organizador, T0.plusSeconds(10_000),
				T0.plusSeconds(15_400), EstadoReserva.CONFIRMADA));
		participantes.saveAndFlush(participante(reserva, ana, 2));

		Participante p = participantes.saveAndFlush(participante(otra, ana, 2));

		assertThat(p.getId()).isNotNull();
	}
}
