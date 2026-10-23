package com.vibora.reserva;

import com.vibora.PostgresTestBase;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.pista.Pista;
import com.vibora.pista.PistaRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;

import static com.vibora.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservaRepositoryTests extends PostgresTestBase {

	private static final Duration HORA = Duration.ofHours(1);

	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired ReservaRepository reservas;

	Pista pista1;
	Pista pista2;
	Usuario jugador;

	@BeforeEach
	void setUp() {
		Club club = clubs.save(club());
		jugador = usuarios.save(usuario("jugador@vibora.com", Rol.JUGADOR, null));
		pista1 = pistas.save(pista(club, "Pista 1"));
		pista2 = pistas.save(pista(club, "Pista 2"));
	}

	private Reserva guardar(Pista pista, Instant inicio, Instant fin, EstadoReserva estado) {
		return reservas.saveAndFlush(reserva(pista, jugador, inicio, fin, estado));
	}

	@Test
	void guardaReservaYAsignaCreadoEn() {
		Reserva r = guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		assertThat(r.getId()).isNotNull();
		assertThat(r.getCreadoEn()).isNotNull();
	}

	@Test
	void rechazaSolapeConfirmadoEnLaMismaPista() {
		guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		assertThatThrownBy(() ->
				guardar(pista1, T0.plus(Duration.ofMinutes(30)), T0.plus(Duration.ofMinutes(90)), EstadoReserva.CONFIRMADA))
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("reserva_sin_solape"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aceptaReservasConsecutivasEnLaMismaPista() {
		guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		Reserva siguiente = guardar(pista1, T0.plus(HORA), T0.plus(HORA.multipliedBy(2)), EstadoReserva.CONFIRMADA);

		assertThat(siguiente.getId()).isNotNull();
	}

	@Test
	void aceptaMismoHorarioEnOtraPista() {
		guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		Reserva otra = guardar(pista2, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		assertThat(otra.getId()).isNotNull();
	}

	@Test
	void solapeConReservaCanceladaEsAceptado() {
		guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CANCELADA);

		Reserva nueva = guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		assertThat(nueva.getId()).isNotNull();
	}

	@Test
	void reservaCanceladaNuncaBloqueaOtraNueva() {
		guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CONFIRMADA);

		// una cancelada encima de una confirmada tampoco entra en la exclusion
		Reserva cancelada = guardar(pista1, T0, T0.plus(HORA), EstadoReserva.CANCELADA);

		assertThat(cancelada.getId()).isNotNull();
	}

	@Test
	void rechazaFinMenorOIgualQueInicio() {
		assertThatThrownBy(() -> guardar(pista1, T0, T0, EstadoReserva.CONFIRMADA))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rechazaFinAnteriorAInicio() {
		assertThatThrownBy(() -> guardar(pista1, T0, T0.minus(HORA), EstadoReserva.CONFIRMADA))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
