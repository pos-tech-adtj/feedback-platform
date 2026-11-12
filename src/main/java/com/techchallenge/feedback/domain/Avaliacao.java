package com.techchallenge.feedback.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Avaliação recebida pela plataforma.
 *
 * @param id identificador da avaliação
 * @param descricao texto do feedback
 * @param nota nota atribuída
 * @param dia data no formato yyyy-MM-dd
 * @param dataEnvio data e hora do envio com offset
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Avaliacao(
        String id,
        String descricao,
        int nota,
        String dia,
        String dataEnvio) {

    /**
     * Classifica a urgência pela nota.
     *
     * @return urgência calculada
     */
    public Urgencia urgencia() {
        return Urgencia.classificarNota(nota);
    }
}
