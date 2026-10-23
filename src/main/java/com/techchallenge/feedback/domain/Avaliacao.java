package com.techchallenge.feedback.domain;

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