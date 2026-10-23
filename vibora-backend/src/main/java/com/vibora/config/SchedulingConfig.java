package com.vibora.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa @Scheduled. Cada tarea indica su planificador (hoy solo el del WebSocket: ver WebSocketConfig). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
