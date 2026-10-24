package com.vibora.factura;

import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.pista.Pista;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Numeracion consecutiva, sin huecos, por club y por anio (el de la hora de Madrid). */
class FacturaNumeracionTests extends FacturaApiTestBase {

	/** Lunes 2031-01-06 a la hora dada (para reservar cuando el reloj ya esta en 2031). */
	private static Instant lunes2031(int hora, int minuto) {
		return local(LocalDate.of(2031, 1, 6), hora, minuto);
	}

	@Test
	void lasFacturasDeUnClubSonConsecutivasDesdeElUno_enOrdenDeEmision() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");
		long r1 = reservaEnElLunes(ana, 8, 0);
		long r2 = reservaEnElLunes(beto, 9, 30);
		long r3 = reservaEnElLunes(ana, 11, 0);

		// se emiten en otro orden que las reservas: manda el orden de emision
		facturar(ana, r3).andExpect(status().isCreated()).andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));
		facturar(beto, r2).andExpect(status().isCreated()).andExpect(jsonPath("$.numeroCompleto").value("F2030-0002"));
		facturar(admin, r1).andExpect(status().isCreated()).andExpect(jsonPath("$.numeroCompleto").value("F2030-0003"));

		assertThat(numerosEmitidos(club)).containsExactly("F2030-0001", "F2030-0002", "F2030-0003");
		assertThat(contador(club, 2030)).isEqualTo(3);
	}

	@Test
	void cadaClubLlevaSuPropiaNumeracion() throws Exception {
		configurarDatosFiscales(admin, club);
		Club otro = crearClub("Club B");
		Pista pistaB = pistas.saveAndFlush(TestData.pista(otro, "Pista B"));
		tarifaPlanaLosLunes(otro, "20.00");
		Jug adminB = administrador(otro, "admin-b@vibora.com");
		configurarDatosFiscales(adminB, otro);
		Jug ana = ana("ana@vibora.com");
		Jug beto = jugador("beto@vibora.com", "Beto", "Ruiz");

		long a1 = reservaEnElLunes(ana, 8, 0);
		long b1 = idDe(reservar(beto, pistaB, lunes(8, 0)).andExpect(status().isCreated()));
		long a2 = reservaEnElLunes(ana, 9, 30);
		long b2 = idDe(reservar(beto, pistaB, lunes(9, 30)).andExpect(status().isCreated()));

		facturar(ana, a1).andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));
		facturar(beto, b1).andExpect(jsonPath("$.numeroCompleto").value("F2030-0001")); // el club B empieza en 1 tambien
		facturar(beto, b2).andExpect(jsonPath("$.numeroCompleto").value("F2030-0002")).andExpect(jsonPath("$.total").value(30.0));
		facturar(ana, a2).andExpect(jsonPath("$.numeroCompleto").value("F2030-0002"));

		assertThat(contador(club, 2030)).isEqualTo(2);
		assertThat(contador(otro, 2030)).isEqualTo(2);
		assertThat(numerosEmitidos(otro)).containsExactly("F2030-0001", "F2030-0002");
	}

	@Test
	void alCambiarDeAnioLaNumeracionVuelveAUno_yElAnioAnteriorConservaSuContador() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");
		facturar(ana, reservaEnElLunes(ana, 8, 0)).andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));
		facturar(ana, reservaEnElLunes(ana, 9, 30)).andExpect(jsonPath("$.numeroCompleto").value("F2030-0002"));

		reloj.fijar(Instant.parse("2031-01-01T00:00:00Z"));
		long nueva = idDe(reservar(ana, pista, lunes2031(8, 0)).andExpect(status().isCreated()));
		long otra = idDe(reservar(ana, pista, lunes2031(9, 30)).andExpect(status().isCreated()));

		facturar(ana, nueva).andExpect(status().isCreated()).andExpect(jsonPath("$.serie").value("F2031"))
				.andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.numeroCompleto").value("F2031-0001"))
				.andExpect(jsonPath("$.emitidaEn").value("2031-01-01T00:00:00Z"));
		facturar(ana, otra).andExpect(jsonPath("$.numeroCompleto").value("F2031-0002"));

		assertThat(contador(club, 2030)).isEqualTo(2);
		assertThat(contador(club, 2031)).isEqualTo(2);
		assertThat(numerosEmitidos(club)).containsExactly("F2030-0001", "F2030-0002", "F2031-0001", "F2031-0002");
	}

	@Test
	void elAnioSeTomaEnHoraDeMadrid_noEnUtc() throws Exception {
		configurarDatosFiscales(admin, club);
		Jug ana = ana("ana@vibora.com");

		// 22:30Z del 31-dic = 23:30 en Madrid: todavia 2030
		reloj.fijar(Instant.parse("2030-12-31T22:30:00Z"));
		long antes = idDe(reservar(ana, pista, lunes2031(8, 0)).andExpect(status().isCreated()));
		facturar(ana, antes).andExpect(jsonPath("$.numeroCompleto").value("F2030-0001"));

		// 23:30Z del 31-dic = 00:30 del 1-ene en Madrid: ya 2031 (aunque en UTC aun sea 2030)
		reloj.fijar(Instant.parse("2030-12-31T23:30:00Z"));
		long despues = idDe(reservar(ana, pista, lunes2031(9, 30)).andExpect(status().isCreated()));
		facturar(ana, despues).andExpect(jsonPath("$.numeroCompleto").value("F2031-0001"));

		assertThat(contador(club, 2030)).isEqualTo(1);
		assertThat(contador(club, 2031)).isEqualTo(1);
	}
}
