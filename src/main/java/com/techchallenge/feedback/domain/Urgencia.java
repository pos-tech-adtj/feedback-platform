package com.techchallenge.feedback.domain;

/**
 * Classificação de urgência de uma avaliação.
 */
public enum Urgencia {
    /**
     * Avaliação com nota menor ou igual a 4.
     */
    CRITICA,
    /**
     * Avaliação com nota maior que 4.
     */
    MEDIA;

    /**
     * Classifica a nota sem validar seu intervalo.
     *
     * @param nota nota da avaliação
     * @return CRITICA até 4; MEDIA acima de 4
     */
    public static Urgencia classificarNota(int nota) {
        return nota <= 4 ? CRITICA : MEDIA;
    }
}
