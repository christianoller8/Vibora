package com.vibora;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Reloj de test: el "ahora" se fija y se mueve a mano. */
public class RelojMutable extends Clock {

	private final ZoneId zona;
	private volatile Instant instante;

	public RelojMutable(Instant instante, ZoneId zona) {
		this.instante = instante;
		this.zona = zona;
	}

	public void fijar(Instant nuevo) {
		this.instante = nuevo;
	}

	@Override
	public ZoneId getZone() {
		return zona;
	}

	@Override
	public Clock withZone(ZoneId otraZona) {
		return new RelojMutable(instante, otraZona);
	}

	@Override
	public Instant instant() {
		return instante;
	}
}
