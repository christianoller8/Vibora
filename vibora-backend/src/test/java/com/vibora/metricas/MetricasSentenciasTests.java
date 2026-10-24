package com.vibora.metricas;

import com.vibora.club.Club;
import com.vibora.pista.Pista;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El numero de sentencias SQL de cada metrica es CONSTANTE: no crece con el numero de pistas ni de dias (nada de una
 * consulta por casilla, por pista o por dia). Se cuenta con las estadisticas de Hibernate (sentencias preparadas).
 */
class MetricasSentenciasTests extends MetricasApiTestBase {

	private static final LocalDate DESDE = d(2030, 1, 7);

	@Autowired jakarta.persistence.EntityManagerFactory emf;

	/** Crea {@code numPistas} pistas con reservas (confirmadas y canceladas) y bloqueos repartidos en {@code dias} dias. */
	private List<Pista> cargar(Club c, int numPistas, int dias) {
		List<Pista> creadas = new ArrayList<>();
		List<Object[]> filasReserva = new ArrayList<>();
		List<Object[]> filasBloqueo = new ArrayList<>();
		for (int i = 0; i < numPistas; i++) {
			Pista p = pista(c, "Pista " + (char) ('A' + i));
			creadas.add(p);
			for (int dia = 0; dia < dias; dia++) {
				LocalDate fecha = DESDE.plusDays(dia);
				for (int s = 0; s < HORAS.length; s++) {
					boolean confirmada = (s + i + dia) % 3 == 0;
					boolean cancelada = !confirmada && (s + dia) % 5 == 0;
					if (confirmada || cancelada) {
						var inicio = local(fecha, HORAS[s]);
						filasReserva.add(new Object[] { p.getId(), organizador.getId(), OffsetDateTime.ofInstant(inicio, ZoneOffset.UTC),
								OffsetDateTime.ofInstant(inicio.plus(CASILLA), ZoneOffset.UTC), confirmada ? "CONFIRMADA" : "CANCELADA",
								new java.math.BigDecimal("12.50").add(java.math.BigDecimal.valueOf(s)) });
					}
				}
				if (dia % 4 == 1) {
					var desde = local(fecha, "11:00");
					filasBloqueo.add(new Object[] { p.getId(), "obras", OffsetDateTime.ofInstant(desde, ZoneOffset.UTC),
							OffsetDateTime.ofInstant(desde.plus(java.time.Duration.ofHours(3)), ZoneOffset.UTC), organizador.getId(),
							OffsetDateTime.ofInstant(desde.minus(java.time.Duration.ofDays(2)), ZoneOffset.UTC) });
				}
			}
		}
		jdbc.batchUpdate("insert into reserva (pista_id, usuario_id, inicio, fin, estado, precio_total) values (?, ?, ?, ?, ?, ?)",
				filasReserva);
		jdbc.batchUpdate("insert into bloqueo_pista (pista_id, motivo, desde, hasta, creado_por, creado_en) values (?, ?, ?, ?, ?, ?)",
				filasBloqueo);
		return creadas;
	}

	private long sentencias(Callable<org.springframework.test.web.servlet.ResultActions> peticion) throws Exception {
		em.flush();
		em.clear();
		Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		estadisticas.clear();
		peticion.call().andExpect(status().isOk());
		return estadisticas.getPrepareStatementCount();
	}

	@Test
	void ningunaMetricaCreceConElNumeroDePistasNiDeDias() throws Exception {
		Club grande = crearClub("Club grande");
		String adminGrande = bearerAdminDe(grande, "admin@grande.com");
		cargar(club, 2, 7);
		cargar(grande, 6, 60);

		for (String recurso : RECURSOS) {
			// una primera vez para que cualquier inicializacion unica no cuente
			pedir(admin, club, recurso, rango(DESDE, DESDE.plusDays(6)));
			long pequeno = sentencias(() -> pedir(admin, club, recurso, rango(DESDE, DESDE.plusDays(6))));
			long grandeN = sentencias(() -> pedir(adminGrande, grande, recurso, rango(DESDE, DESDE.plusDays(59))));

			LoggerFactory.getLogger(MetricasSentenciasTests.class).info("SENTENCIAS {}: 2 pistas x 7 dias = {}, 6 pistas x 60 dias = {}",
					recurso, pequeno, grandeN);
			assertThat(grandeN).as("sentencias de %s: 2 pistas x 7 dias = %d, 6 pistas x 60 dias = %d", recurso, pequeno, grandeN)
					.isEqualTo(pequeno);
			assertThat(pequeno).as("sentencias de %s", recurso).isLessThanOrEqualTo(10);
		}
	}

	@Test
	void ocupacionYFranjasDevuelvenLoEsperadoConElClubGrande() throws Exception {
		Club grande = crearClub("Club grande");
		String adminGrande = bearerAdminDe(grande, "admin@grande.com");
		cargar(grande, 6, 60);

		var ocupacion = json(pedir(adminGrande, grande, "ocupacion", rango(DESDE, DESDE.plusDays(59))).andExpect(status().isOk()));
		var franjas = json(pedir(adminGrande, grande, "franjas", rango(DESDE, DESDE.plusDays(59), "limit", "50")).andExpect(status().isOk()));

		assertThat(ocupacion.read("$.pistas.length()", Integer.class)).isEqualTo(6);
		assertThat(ocupacion.read("$.ranurasOfrecidas", Integer.class)).isGreaterThan(3000);
		assertThat(franjas.read("$.matriz.length()", Integer.class)).isEqualTo(70);
	}
}
