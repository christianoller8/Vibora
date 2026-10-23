package com.vibora.config;

import com.vibora.reserva.ReservaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(ReservaProperties.class)
public class ReservaConfig {

	/** Zona horaria de los clubes (vibora.zona-horaria). */
	@Bean
	ZoneId zonaHoraria(@Value("${vibora.zona-horaria}") String zona) {
		return ZoneId.of(zona);
	}
}
