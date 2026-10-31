package com.techchallenge.feedback.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
@EnabledIfEnvironmentVariable(named = "EMAIL_INTEGRATION_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "ACS_CONNECTION_STRING", matches = ".+")
@EnabledIfEnvironmentVariable(named = "EMAIL_REMETENTE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "EMAIL_ADMINS", matches = ".+")
class EmailServiceIntegrationTest {
    @Test
    void deveEnviarEmailRealAosAdministradores() {
        EmailService service = new EmailService();
        service.enviar(
                "Teste de integração - " + Instant.now(),
                "Mensagem enviada pelo teste de integração do feedback-platform.");
    }
}
