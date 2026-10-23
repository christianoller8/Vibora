package com.vibora.websocket;

import com.vibora.notificacion.NotificacionResponse;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.partido.ParticipanteRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * El push solo sale tras el COMMIT y nunca rompe la peticion REST. Clase aparte porque espia beans (contexto propio).
 */
class WebSocketTransaccionalTests extends WebSocketTestBase {

	@MockitoSpyBean SimpMessagingTemplate plantilla;
	@MockitoSpyBean ParticipanteRepository participantesEspia;

	@RepeatedTest(3)
	void siLaTransaccionSeRevierte_noSeEmpujaNada() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		Jug carla = jugador("carla@vibora.test");
		long id = partidoAbierto(organizador);
		Conexion deOrganizador = conectarYSuscribir(organizador);

		// control positivo: una alta que si se confirma, llega
		assertThat(apuntarse(carla, id)).isEqualTo(201);
		assertThat(deOrganizador.siguiente()).isNotNull();
		assertThat(deOrganizador.siguienteEnPoco()).isNull();
		long notificacionesAntes = notificaciones.count();

		// la ultima lectura de apuntarse() (ya con las notificaciones creadas y el evento publicado) falla -> rollback
		doThrow(new IllegalStateException("fallo forzado")).when(participantesEspia).findByReservaIdOrderByPuesto(anyLong());
		assertThatThrownBy(() -> apuntarse(beto, id)).hasRootCauseMessage("fallo forzado");

		assertThat(notificaciones.count()).as("nada quedo guardado").isEqualTo(notificacionesAntes);
		assertThat(participantes.count()).as("beto no quedo apuntado").isEqualTo(2);
		assertThat(deOrganizador.siguienteEnPoco()).as("ni se empujo nada del intento revertido").isNull();
	}

	@RepeatedTest(3)
	void siElPushFalla_laPeticionRestSigueFuncionandoYLaNotificacionQuedaGuardada() throws Exception {
		Jug organizador = jugador("organizador@vibora.test");
		Jug beto = jugador("beto@vibora.test");
		long id = partidoAbierto(organizador);
		Conexion deOrganizador = conectarYSuscribir(organizador);
		doThrow(new MessagingException("canal roto")).when(plantilla).convertAndSendToUser(anyString(), anyString(), any());

		assertThat(apuntarse(beto, id)).as("la peticion REST no se rompe").isEqualTo(201);

		verify(plantilla, atLeastOnce()).convertAndSendToUser(anyString(), anyString(), any());
		assertThat(participantes.count()).isEqualTo(2);
		assertThat(notificaciones.findAll()).extracting(n -> n.getTipo())
				.contains(TipoNotificacion.JUGADOR_SE_APUNTO, TipoNotificacion.CARGO_NUEVO);
		NotificacionResponse recibida = deOrganizador.siguienteEnPoco();
		assertThat(recibida).as("el push fallo, pero queda la via REST").isNull();
	}
}
