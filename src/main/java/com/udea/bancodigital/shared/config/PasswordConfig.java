package com.udea.bancodigital.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// Separado de SecurityConfig a proposito: SecurityConfig necesita el filtro JWT,
// el filtro necesita UsuarioApi (UsuarioService) y UsuarioService necesita el
// PasswordEncoder. Si el encoder viviera en SecurityConfig, eso seria un ciclo
// de dependencias y la aplicacion no arrancaria.
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
