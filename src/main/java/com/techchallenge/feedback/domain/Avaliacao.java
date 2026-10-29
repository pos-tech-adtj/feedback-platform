package com.techchallenge.feedback.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Avaliacao(
        String id,
        String descricao,
        int nota,
        String dia,
        String dataEnvio) {

    public Urgencia urgencia() {
        return Urgencia.classificarNota(nota);
    }
}