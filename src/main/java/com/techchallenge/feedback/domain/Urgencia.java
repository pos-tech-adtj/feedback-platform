package com.techchallenge.feedback.domain;

public enum Urgencia {
    CRITICA,
    MEDIA;

    public static Urgencia classificarNota(int nota) {
        return nota <= 4 ? CRITICA : MEDIA;
    }
}
