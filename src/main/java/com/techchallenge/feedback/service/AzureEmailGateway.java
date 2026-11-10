package com.techchallenge.feedback.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;

/**
 * Envia mensagens pelo Azure Communication Services.
 */
final class AzureEmailGateway implements EmailGateway {
    private final EmailClient client;

    AzureEmailGateway(String connectionString) {
        this.client = new EmailClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }

    /**
     * Envia a mensagem e aguarda a conclusão no Azure.
     *
     * @param message mensagem a enviar
     */
    @Override
    public void send(EmailMessage message) {
        client.beginSend(message).getFinalResult();
    }
}
