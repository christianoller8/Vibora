package com.vibora.config;

import com.vibora.almacen.AlmacenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlmacenProperties.class)
public class AlmacenConfig {
}
