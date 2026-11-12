package com.techchallenge.feedback.service;

import com.azure.communication.email.models.EmailMessage;

/**
 * Abstrai o envio de uma mensagem pelo provedor de e-mail.
 */
@FunctionalInterface
interface EmailGateway {
    /**
     * Envia a mensagem e aguarda o resultado.
     *
     * @param message mensagem com remetente e destinatários
     */
    void send(EmailMessage message);
}
