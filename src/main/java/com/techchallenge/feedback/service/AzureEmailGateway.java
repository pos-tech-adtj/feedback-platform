package com.techchallenge.feedback.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;

final class AzureEmailGateway implements EmailGateway {
    private final EmailClient client;

    AzureEmailGateway(String connectionString) {
        this.client = new EmailClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }

    @Override
    public void send(EmailMessage message) {
        client.beginSend(message).getFinalResult();
    }
}
