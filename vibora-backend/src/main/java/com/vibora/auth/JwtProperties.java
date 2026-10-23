package com.vibora.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion del JWT: vibora.jwt.secret y vibora.jwt.expiracion-minutos. */
@ConfigurationProperties(prefix = "vibora.jwt")
public record JwtProperties(String secret, long expiracionMinutos) {
}
