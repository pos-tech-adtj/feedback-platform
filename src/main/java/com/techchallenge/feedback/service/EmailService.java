package com.techchallenge.feedback.service;

import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;

import java.util.Arrays;
import java.util.List;

public class EmailService {
    private static final String ACS_CONNECTION_STRING = "ACS_CONNECTION_STRING";
    private static final String EMAIL_REMETENTE = "EMAIL_REMETENTE";
    private static final String EMAIL_ADMINS = "EMAIL_ADMINS";

    private final String remetente;
    private final List<EmailAddress> administradores;
    private final EmailGateway gateway;

    public EmailService() {
        String connectionString = requiredEnvironmentVariable(ACS_CONNECTION_STRING);
        this.remetente = requiredEnvironmentVariable(EMAIL_REMETENTE);
        this.administradores = parseAdministradores(requiredEnvironmentVariable(EMAIL_ADMINS));

        this.gateway = new AzureEmailGateway(connectionString);
    }

    EmailService(String remetente, String administradores, EmailGateway gateway) {
        this.remetente = requireValue(EMAIL_REMETENTE, remetente);
        this.administradores = parseAdministradores(administradores);
        this.gateway = gateway;
    }

    public void enviar(String assunto, String corpo) {
        if (assunto == null || assunto.isBlank()) {
            throw new IllegalArgumentException("O assunto do e-mail é obrigatório");
        }
        if (corpo == null || corpo.isBlank()) {
            throw new IllegalArgumentException("O corpo do e-mail é obrigatório");
        }

        EmailMessage mensagem = new EmailMessage()
                .setSenderAddress(remetente)
                .setToRecipients(administradores)
                .setSubject(assunto)
                .setBodyPlainText(corpo);
        gateway.send(mensagem);
    }

    private static String requiredEnvironmentVariable(String name) {
        return requireValue(name, System.getenv(name));
    }

    private static String requireValue(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("A variável de ambiente " + name + " é obrigatória");
        }
        return value.trim();
    }

    private static List<EmailAddress> parseAdministradores(String value) {
        List<EmailAddress> parsed = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .map(EmailAddress::new)
                .toList();
        if (parsed.isEmpty()) {
            throw new IllegalStateException("A variável de ambiente EMAIL_ADMINS deve conter ao menos um e-mail");
        }
        return parsed;
    }
}
