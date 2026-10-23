package com.vibora.cargo;

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
class CargoRepositoryTests extends PostgresTestBase {

	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired PistaRepository pistas;
	@Autowired ReservaRepository reservas;
	@Autowired CargoRepository cargos;
	@Autowired PagoRepository pagos;

	Usuario organizador;
	Usuario ana;
	Reserva reserva;

	@BeforeEach
	void setUp() {
		Club club = clubs.save(club());
		organizador = usuarios.save(usuario("org@vibora.com", Rol.JUGADOR, null));
		ana = usuarios.save(usuario("ana@vibora.com", Rol.JUGADOR, null));
		Pista pista = pistas.save(pista(club, "Pista 1"));
		reserva = reservas.save(reserva(pista, organizador, T0, T0.plusSeconds(5400), EstadoReserva.CONFIRMADA));
	}

	private Cargo guardar(Usuario deudor, Usuario acreedor, String importe, EstadoCargo estado) {
		return cargos.saveAndFlush(cargo(reserva, deudor, acreedor, importe, estado));
	}

	@Test
	void guardaCargoPendiente() {
		Cargo c = guardar(ana, organizador, "11.25", EstadoCargo.PENDIENTE);

		assertThat(c.getId()).isNotNull();
		assertThat(c.getEstado()).isEqualTo(EstadoCargo.PENDIENTE);
	}

	@Test
	void importeCeroONegativo_violaImporteCk() {
		assertThatThrownBy(() -> guardar(ana, organizador, "0.00", EstadoCargo.PENDIENTE))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("cargo_importe_ck"));
	}

	@Test
	void importeNegativo_violaImporteCk() {
		assertThatThrownBy(() -> guardar(ana, organizador, "-1.00", EstadoCargo.PENDIENTE))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("cargo_importe_ck"));
	}

	@Test
	void deudorIgualAAcreedor_violaPartesCk() {
		assertThatThrownBy(() -> guardar(ana, ana, "5.00", EstadoCargo.PENDIENTE))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("cargo_partes_ck"));
	}

	@Test
	void dosCargosVivosDelMismoDeudorEnLaMismaReserva_violaCargoVivoUk() {
		guardar(ana, organizador, "11.25", EstadoCargo.PENDIENTE);

		assertThatThrownBy(() -> guardar(ana, organizador, "11.25", EstadoCargo.PENDIENTE))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("cargo_vivo_uk"));
	}

	@Test
	void pendienteYPagadoTambienCuentanComoVivos() {
		guardar(ana, organizador, "11.25", EstadoCargo.PAGADO);

		assertThatThrownBy(() -> guardar(ana, organizador, "11.25", EstadoCargo.PENDIENTE))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("cargo_vivo_uk"));
	}

	@Test
	void cargoAnuladoOReembolsadoNoImpideUnoNuevo() {
		guardar(ana, organizador, "11.25", EstadoCargo.ANULADO);
		guardar(ana, organizador, "11.25", EstadoCargo.REEMBOLSADO);

		Cargo nuevo = guardar(ana, organizador, "11.25", EstadoCargo.PENDIENTE);

		assertThat(nuevo.getId()).isNotNull();
	}

	@Test
	void unPagoPorCargo_segundoViolaPagoCargoUk() {
		Cargo c = guardar(ana, organizador, "11.25", EstadoCargo.PAGADO);
		pagos.saveAndFlush(pago(c, "SIM-1"));

		assertThatThrownBy(() -> pagos.saveAndFlush(pago(c, "SIM-2")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("pago_cargo_uk"));
	}

	@Test
	void pagoGuardaReferencia() {
		Cargo c = guardar(ana, organizador, "11.25", EstadoCargo.PAGADO);

		Pago p = pagos.saveAndFlush(pago(c, "SIM-abc"));

		assertThat(pagos.findByCargoIdIn(java.util.List.of(c.getId()))).singleElement()
				.satisfies(x -> assertThat(x.getReferencia()).isEqualTo("SIM-abc"))
				.isEqualTo(p);
	}
}
