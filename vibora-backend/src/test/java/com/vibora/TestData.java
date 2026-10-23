package com.vibora;

import com.vibora.club.Club;
import com.vibora.cargo.Cargo;
import com.vibora.cargo.EstadoCargo;
import com.vibora.cargo.Pago;
import com.vibora.notificacion.Notificacion;
import com.vibora.notificacion.TipoNotificacion;
import com.vibora.partido.Participante;
import com.vibora.pista.Pista;
import com.vibora.pista.TipoPista;
import com.vibora.reserva.EstadoReserva;
import com.vibora.reserva.Reserva;
import com.vibora.tarifa.TarifaFranja;
import com.vibora.tarifa.TipoTarifa;
import com.vibora.usuario.Rol;
import com.vibora.usuario.Usuario;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/** Fabricas pequenas de entidades validas para los tests. */
public final class TestData {

	public static final Instant T0 = Instant.parse("2030-01-01T10:00:00Z");

	private TestData() {
	}

	public static Club club() {
		Club c = new Club();
		c.setNombre("Club Víbora");
		c.setDireccion("Calle Falsa 123");
		c.setCiudad("Madrid");
		c.setHoraApertura(LocalTime.of(8, 0));
		c.setHoraCierre(LocalTime.of(23, 0));
		return c;
	}

	public static Usuario usuario(String email, Rol rol, Club club) {
		Usuario u = new Usuario();
		u.setEmail(email);
		u.setPasswordHash("hash");
		u.setNombre("Ana");
		u.setApellidos("García");
		u.setRol(rol);
		u.setClub(club);
		u.setActivo(true);
		return u;
	}

	public static Pista pista(Club club, String nombre) {
		Pista p = new Pista();
		p.setClub(club);
		p.setNombre(nombre);
		p.setTipo(TipoPista.INTERIOR);
		p.setActiva(true);
		return p;
	}

	public static TarifaFranja tarifa(Club club, DayOfWeek dia, TipoTarifa tipo) {
		TarifaFranja t = new TarifaFranja();
		t.setClub(club);
		t.setDiaSemana(dia);
		t.setHoraInicio(LocalTime.of(18, 0));
		t.setHoraFin(LocalTime.of(22, 0));
		t.setPrecioHora(new BigDecimal("24.50"));
		t.setTipo(tipo);
		return t;
	}

	public static Reserva reserva(Pista pista, Usuario usuario, Instant inicio, Instant fin, EstadoReserva estado) {
		Reserva r = new Reserva();
		r.setPista(pista);
		r.setUsuario(usuario);
		r.setInicio(inicio);
		r.setFin(fin);
		r.setEstado(estado);
		r.setPrecioTotal(new BigDecimal("12.00"));
		return r;
	}

	public static Participante participante(Reserva reserva, Usuario usuario, int puesto) {
		Participante p = new Participante();
		p.setReserva(reserva);
		p.setUsuario(usuario);
		p.setPuesto((short) puesto);
		p.setUnidoEn(T0);
		return p;
	}

	public static Cargo cargo(Reserva reserva, Usuario deudor, Usuario acreedor, String importe, EstadoCargo estado) {
		Cargo c = new Cargo();
		c.setReserva(reserva);
		c.setDeudor(deudor);
		c.setAcreedor(acreedor);
		c.setImporte(new BigDecimal(importe));
		c.setEstado(estado);
		c.setCreadoEn(T0);
		return c;
	}

	public static Pago pago(Cargo cargo, String referencia) {
		Pago p = new Pago();
		p.setCargo(cargo);
		p.setImporte(cargo.getImporte());
		p.setReferencia(referencia);
		p.setCreadoEn(T0);
		return p;
	}

	public static Notificacion notificacion(Usuario usuario, TipoNotificacion tipo, String mensaje) {
		Notificacion n = new Notificacion();
		n.setUsuario(usuario);
		n.setTipo(tipo);
		n.setMensaje(mensaje);
		n.setCreadaEn(T0);
		return n;
	}
}
