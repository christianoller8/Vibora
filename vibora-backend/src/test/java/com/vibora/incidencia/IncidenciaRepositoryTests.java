package com.vibora.incidencia;

import com.vibora.PostgresTestBase;
import com.vibora.TestData;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mapeo JPA de incidencia/incidencia_evento: valores por defecto, @Version e historial ordenado. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IncidenciaRepositoryTests extends PostgresTestBase {

	static final Instant T0 = TestData.T0;

	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired IncidenciaRepository incidencias;
	@Autowired IncidenciaEventoRepository eventos;
	@Autowired EntityManager em;

	Club club;
	Usuario ana;
	Usuario mant;

	@BeforeEach
	void preparar() {
		club = clubs.saveAndFlush(TestData.club());
		ana = usuarios.saveAndFlush(TestData.usuario("ana@vibora.com", Rol.JUGADOR, null));
		mant = usuarios.saveAndFlush(TestData.usuario("mant@vibora.com", Rol.MANTENIMIENTO, club));
	}

	private Incidencia nueva(String titulo) {
		Incidencia i = new Incidencia();
		i.setClub(club);
		i.setReportadaPor(ana);
		i.setTitulo(titulo);
		i.setDescripcion("d");
		i.setCategoria(CategoriaIncidencia.RED);
		i.setCreadaEn(T0);
		i.setActualizadaEn(T0);
		return i;
	}

	@Test
	void guarda_conAbiertaMediaYVersionCero() {
		Incidencia i = incidencias.saveAndFlush(nueva("Red rota"));

		assertThat(i.getId()).isNotNull();
		assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.ABIERTA);
		assertThat(i.getPrioridad()).isEqualTo(PrioridadIncidencia.MEDIA);
		assertThat(i.getVersion()).isZero();
		assertThat(i.getAsignadaA()).isNull();
		assertThat(i.getPista()).isNull();
	}

	@Test
	void cadaModificacionSumaUnoALaVersion() {
		Incidencia i = incidencias.saveAndFlush(nueva("Red rota"));

		i.setPrioridad(PrioridadIncidencia.ALTA);
		incidencias.saveAndFlush(i);

		assertThat(i.getVersion()).isEqualTo(1L);
	}

	@Test
	void unaCopiaDesactualizada_noPuedeGuardarse_bloqueoOptimista() {
		Incidencia i = incidencias.saveAndFlush(nueva("Red rota"));
		em.detach(i); // copia "vieja" con version 0
		Incidencia actual = incidencias.findById(i.getId()).orElseThrow();
		actual.setEstado(EstadoIncidencia.RECHAZADA);
		incidencias.saveAndFlush(actual); // version 1
		em.clear();

		i.setEstado(EstadoIncidencia.ASIGNADA);
		i.setAsignadaA(mant);
		assertThatThrownBy(() -> incidencias.saveAndFlush(i))
				.isInstanceOfAny(OptimisticLockingFailureException.class, OptimisticLockException.class);
	}

	@Test
	void asignadaSinResponsable_laRechazaLaBd() {
		Incidencia i = nueva("Red rota");
		i.setEstado(EstadoIncidencia.ASIGNADA);

		assertThatThrownBy(() -> incidencias.saveAndFlush(i)).isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_asignada_ck"));
	}

	@Test
	void historial_ordenadoPorFechaYDespuesPorId_conSuAutor() {
		Incidencia i = incidencias.saveAndFlush(nueva("Red rota"));
		evento(i, TipoEventoIncidencia.CREADA, T0);
		evento(i, TipoEventoIncidencia.ASIGNADA, T0.plusSeconds(10));
		evento(i, TipoEventoIncidencia.PRIORIDAD, T0.plusSeconds(10)); // mismo instante: desempata el id
		evento(i, TipoEventoIncidencia.COMENTARIO, T0.plusSeconds(5));
		em.flush();
		em.clear();

		List<IncidenciaEvento> h = eventos.findByIncidenciaIdOrderByCreadoEnAscIdAsc(i.getId());

		assertThat(h).extracting(IncidenciaEvento::getTipo).containsExactly(TipoEventoIncidencia.CREADA,
				TipoEventoIncidencia.COMENTARIO, TipoEventoIncidencia.ASIGNADA, TipoEventoIncidencia.PRIORIDAD);
		assertThat(h.get(0).getUsuario().getEmail()).isEqualTo("ana@vibora.com");
	}

	private void evento(Incidencia i, TipoEventoIncidencia tipo, Instant cuando) {
		IncidenciaEvento e = new IncidenciaEvento();
		e.setIncidencia(i);
		e.setUsuario(ana);
		e.setTipo(tipo);
		e.setCreadoEn(cuando);
		eventos.save(e);
	}
}
