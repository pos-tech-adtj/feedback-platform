package com.techchallenge.feedback.service;

import com.azure.communication.email.models.EmailMessage;

@FunctionalInterface
interface EmailGateway {
    void send(EmailMessage message);
}
