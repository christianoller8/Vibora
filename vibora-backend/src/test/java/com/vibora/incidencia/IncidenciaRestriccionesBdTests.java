package com.vibora.incidencia;

import com.vibora.PostgresTestBase;
import com.vibora.club.Club;
import com.vibora.club.ClubRepository;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;
import com.vibora.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.vibora.TestData.club;
import static com.vibora.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las restricciones de la BD (por NOMBRE) que respaldan las reglas de incidencias, con SQL crudo. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IncidenciaRestriccionesBdTests extends PostgresTestBase {

	@Autowired ClubRepository clubs;
	@Autowired UsuarioRepository usuarios;
	@Autowired JdbcTemplate jdbc;

	Club club;
	Usuario ana;
	Usuario mant;

	@BeforeEach
	void preparar() {
		club = clubs.saveAndFlush(club());
		ana = usuarios.saveAndFlush(usuario("ana@vibora.com", Rol.JUGADOR, null));
		mant = usuarios.saveAndFlush(usuario("mant@vibora.com", Rol.MANTENIMIENTO, club));
	}

	private int insertarIncidencia(String categoria, String prioridad, String estado, Long asignadaA, String titulo) {
		return jdbc.update("""
				insert into incidencia (club_id, reportada_por, titulo, descripcion, categoria, prioridad, estado,
				    asignada_a, creada_en, actualizada_en)
				values (?, ?, ?, 'd', ?, ?, ?, ?, now(), now())
				""", club.getId(), ana.getId(), titulo, categoria, prioridad, estado, asignadaA);
	}

	@Test
	void filaMinimaValida_tomaLosValoresPorDefecto() {
		jdbc.update("insert into incidencia (club_id, reportada_por, titulo, descripcion, categoria, creada_en, "
				+ "actualizada_en) values (?, ?, 't', 'd', 'RED', now(), now())", club.getId(), ana.getId());

		assertThat(jdbc.queryForObject("select prioridad from incidencia", String.class)).isEqualTo("MEDIA");
		assertThat(jdbc.queryForObject("select estado from incidencia", String.class)).isEqualTo("ABIERTA");
		assertThat(jdbc.queryForObject("select version from incidencia", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select asignada_a from incidencia", Long.class)).isNull();
		assertThat(jdbc.queryForObject("select resuelta_en from incidencia", java.sql.Timestamp.class)).isNull();
	}

	@Test
	void categoriaDesconocida_violaCategoriaCk() {
		assertThatThrownBy(() -> insertarIncidencia("PINTURA", "MEDIA", "ABIERTA", null, "t"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_categoria_ck"));
	}

	@Test
	void prioridadDesconocida_violaPrioridadCk() {
		assertThatThrownBy(() -> insertarIncidencia("RED", "CRITICA", "ABIERTA", null, "t"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_prioridad_ck"));
	}

	@Test
	void estadoDesconocido_violaEstadoCk() {
		assertThatThrownBy(() -> insertarIncidencia("RED", "MEDIA", "PAUSADA", null, "t"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_estado_ck"));
	}

	// una transaccion de PostgreSQL queda abortada tras el primer error: un test (y su rollback) por caso
	@ParameterizedTest
	@ValueSource(strings = { "ASIGNADA", "EN_CURSO" })
	void asignadaYEnCursoExigenAsignadaA_violanAsignadaCk(String estado) {
		assertThatThrownBy(() -> insertarIncidencia("RED", "MEDIA", estado, null, "t"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_asignada_ck"));
	}

	@Test
	void conAsignadaA_ASIGNADAyEN_CURSOsonValidas_yLosDemasEstadosNoLaExigen() {
		assertThat(insertarIncidencia("RED", "MEDIA", "ASIGNADA", mant.getId(), "a")).isEqualTo(1);
		assertThat(insertarIncidencia("RED", "MEDIA", "EN_CURSO", mant.getId(), "b")).isEqualTo(1);
		for (String estado : new String[] { "ABIERTA", "RESUELTA", "CERRADA", "RECHAZADA" }) {
			assertThat(insertarIncidencia("RED", "MEDIA", estado, null, estado)).as(estado).isEqualTo(1);
		}
	}

	@Test
	void quitarElAsignadoDeUnaIncidenciaAsignada_violaAsignadaCk() {
		insertarIncidencia("RED", "MEDIA", "ASIGNADA", mant.getId(), "a");

		assertThatThrownBy(() -> jdbc.update("update incidencia set asignada_a = null"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_asignada_ck"));
	}

	@Test
	void tituloDe120Caracteres_cabe() {
		assertThat(insertarIncidencia("RED", "MEDIA", "ABIERTA", null, "x".repeat(120))).isEqualTo(1);
	}

	@Test
	void tituloDeMasDe120Caracteres_seRechaza() {
		assertThatThrownBy(() -> insertarIncidencia("RED", "MEDIA", "ABIERTA", null, "x".repeat(121)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void eventoConTipoValido_seAcepta() {
		insertarIncidencia("RED", "MEDIA", "ABIERTA", null, "t");
		Long incidenciaId = jdbc.queryForObject("select id from incidencia", Long.class);

		for (String tipo : new String[] { "CREADA", "ASIGNADA", "ESTADO", "COMENTARIO", "PRIORIDAD" }) {
			assertThat(jdbc.update("insert into incidencia_evento (incidencia_id, usuario_id, tipo, creado_en) "
					+ "values (?, ?, ?, now())", incidenciaId, ana.getId(), tipo)).as(tipo).isEqualTo(1);
		}
	}

	@Test
	void eventoConTipoDesconocido_violaEventoTipoCk() {
		insertarIncidencia("RED", "MEDIA", "ABIERTA", null, "t");
		Long incidenciaId = jdbc.queryForObject("select id from incidencia", Long.class);

		assertThatThrownBy(() -> jdbc.update("insert into incidencia_evento (incidencia_id, usuario_id, tipo, creado_en) "
				+ "values (?, ?, 'BORRADA', now())", incidenciaId, ana.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(ex).rootCause().hasMessageContaining("incidencia_evento_tipo_ck"));
	}

	@Test
	void incidenciaDeUnClubInexistente_violaLaClaveForanea() {
		assertThatThrownBy(() -> jdbc.update("insert into incidencia (club_id, reportada_por, titulo, descripcion, "
				+ "categoria, creada_en, actualizada_en) values (-1, ?, 't', 'd', 'RED', now(), now())", ana.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
