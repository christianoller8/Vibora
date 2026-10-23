package com.vibora.partido;

import com.vibora.TestData;
import com.vibora.cargo.EstadoCargo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demuestra que son las RESTRICCIONES de la BD (no el bloqueo ni las comprobaciones previas) las que, si todo lo
 * demas fallase, se traducen a 409 y nunca a 500. El repositorio de participantes es un spy que hace que las
 * comprobaciones previas "no vean nada".
 */
class PartidoRestriccionBdTests extends PartidoSinTransaccionBase {

	@MockitoSpyBean ParticipanteRepository espia;

	@AfterEach
	void restaurarEspia() {
		reset(espia);
	}

	@Test
	void puestoOcupado_violaPuestoUk_yDevuelve409() throws Exception {
		Jug organizador = jugador("organizador@vibora.com");
		long id = partidoAbierto(organizador);
		Jug otro = jugador("otro@vibora.com");
		// la lectura de puestos ocupados "no ve" al organizador: se intentara el puesto 1
		doReturn(List.of()).when(espia).findPuestosOcupados(anyLong());

		mvc.perform(post("/api/reservas/" + id + "/apuntarse").header("Authorization", otro.token()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("El partido está completo"));

		verify(espia).findPuestosOcupados(id);
		assertThat(participantes.findAll()).hasSize(1);
		assertThat(cargos.findAll()).isEmpty();
		assertThat(notificaciones.findAll()).isEmpty();
	}

	@Test
	void usuarioDuplicado_violaUsuarioUk_yDevuelve409() throws Exception {
		Jug organizador = jugador("organizador@vibora.com");
		long id = partidoAbierto(organizador);
		Jug otro = jugador("otro@vibora.com");
		assertThat(apuntarse(otro, id)).isEqualTo(201);
		// la comprobacion "ya estas apuntado" dice que no: lo atrapa participante_usuario_uk
		doReturn(false).when(espia).existsByReservaIdAndUsuarioId(anyLong(), anyLong());

		mvc.perform(post("/api/reservas/" + id + "/apuntarse").header("Authorization", otro.token()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya estás apuntado a este partido"));

		assertThat(participantes.findAll()).hasSize(2);
		assertThat(cargos.findAll()).hasSize(1);
	}

	@Test
	void cargoVivoYaExistente_violaCargoVivoUk_409_yRevierteElAlta() throws Exception {
		Jug organizador = jugador("organizador@vibora.com");
		long id = partidoAbierto(organizador);
		Jug otro = jugador("otro@vibora.com");
		// estado incoherente: cargo vivo sin participante. El alta del participante pasa, el del cargo no.
		cargos.saveAndFlush(TestData.cargo(reservas.findById(id).orElseThrow(), usuarios.findById(otro.id()).orElseThrow(),
				usuarios.findById(organizador.id()).orElseThrow(), "11.25", EstadoCargo.PENDIENTE));

		mvc.perform(post("/api/reservas/" + id + "/apuntarse").header("Authorization", otro.token()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Ya estás apuntado a este partido"));

		assertThat(participantes.findAll()).hasSize(1); // la transaccion entera se revirtio
		assertThat(cargos.findAll()).hasSize(1);
		assertThat(notificaciones.findAll()).isEmpty();
	}
}
