package com.udea.bancodigital.shared.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

// Mantiene acotada la tabla tokens_revocados: sin esto crece con cada logout
// para siempre, y cada peticion autenticada la consulta.
@Component
public class PurgaTokensRevocadosTask {

    private static final Logger log = LoggerFactory.getLogger(PurgaTokensRevocadosTask.class);

    private final RevocacionTokenService revocacionTokenService;

    public PurgaTokensRevocadosTask(RevocacionTokenService revocacionTokenService) {
        this.revocacionTokenService = revocacionTokenService;
    }

    @Scheduled(cron = "${jwt.revocados.purga-cron}")
    public void purgar() {
        int eliminados = revocacionTokenService.purgarExpirados(Instant.now());
        log.info("Purga de tokens revocados: {} filas expiradas eliminadas", eliminados);
    }
}
