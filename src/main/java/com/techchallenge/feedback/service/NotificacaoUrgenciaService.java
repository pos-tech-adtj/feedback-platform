package com.techchallenge.feedback.service;

import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.domain.Urgencia;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Envia um alerta por avaliação crítica aos administradores.
 */
public class NotificacaoUrgenciaService {
    private static final String ASSUNTO = "[URGENTE] Avaliação crítica recebida";
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmailService emailService;

    /**
     * Cria o serviço com o envio de e-mails padrão.
     */
    public NotificacaoUrgenciaService() {
        this(new EmailService());
    }

    NotificacaoUrgenciaService(EmailService emailService) {
        this.emailService = Objects.requireNonNull(emailService, "emailService e obrigatorio");
    }

    /**
     * Envia alertas para notas até 4 e ignora itens nulos ou não críticos.
     *
     * @param avaliacoes lote de avaliações
     * @return quantidade de avaliações notificadas
     * @throws RuntimeException se um envio falhar; interrompe o restante do lote
     */
    public int notificarCriticas(List<Avaliacao> avaliacoes) {
        int enviados = 0;
        for (Avaliacao avaliacao : avaliacoes) {
            if (avaliacao == null || avaliacao.urgencia() != Urgencia.CRITICA) {
                continue;
            }
            emailService.enviar(ASSUNTO, montarCorpo(avaliacao));
            enviados++;
        }
        return enviados;
    }

    private static String montarCorpo(Avaliacao avaliacao) {
        return """
                Uma avaliação crítica foi registrada na plataforma de feedback.

                Descrição: %s
                Urgência: %s
                Data de envio: %s
                """.formatted(
                avaliacao.descricao(),
                avaliacao.urgencia(),
                formatarData(avaliacao.dataEnvio()));
    }

    private static String formatarData(String dataEnvio) {
        if (dataEnvio == null || dataEnvio.isBlank()) {
            return "não informada";
        }
        try {
            return OffsetDateTime.parse(dataEnvio).format(FORMATO_DATA);
        } catch (DateTimeException e) {
            return dataEnvio;
        }
    }
}
