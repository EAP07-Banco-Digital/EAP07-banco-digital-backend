package com.udea.bancodigital.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa las tareas @Scheduled (hoy: la purga de tokens revocados).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
