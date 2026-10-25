package com.techchallenge.feedback.service;

import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.repository.AvaliacaoRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

public class AvaliacaoService {
    static final int TAMANHO_MAXIMO_DESCRICAO = 1_000;
    static final int NOTA_MINIMA = 0;
    static final int NOTA_MAXIMA = 10;
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    private final AvaliacaoRepository repository;
    private final Clock clock;

    public AvaliacaoService() {
        this(new AvaliacaoRepository(), Clock.system(FUSO_HORARIO));
    }

    AvaliacaoService(AvaliacaoRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio").withZone(FUSO_HORARIO);
    }

    public Avaliacao registrar(String descricao, Integer nota) {
        String descricaoValidada = validarDescricao(descricao);
        int notaValidada = validarNota(nota);

        OffsetDateTime agora = OffsetDateTime.now(clock);
        Avaliacao avaliacao = new Avaliacao(
                UUID.randomUUID().toString(),
                descricaoValidada,
                notaValidada,
                LocalDate.from(agora).toString(),
                agora.toString());

        repository.salvar(avaliacao);
        return avaliacao;
    }

    private static String validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("descricao e obrigatoria");
        }
        String normalizada = descricao.trim();
        if (normalizada.length() > TAMANHO_MAXIMO_DESCRICAO) {
            throw new IllegalArgumentException(
                    "descricao deve ter no maximo " + TAMANHO_MAXIMO_DESCRICAO + " caracteres");
        }
        return normalizada;
    }

    private static int validarNota(Integer nota) {
        if (nota == null) {
            throw new IllegalArgumentException("nota e obrigatoria");
        }
        if (nota < NOTA_MINIMA || nota > NOTA_MAXIMA) {
            throw new IllegalArgumentException(
                    "nota deve estar entre " + NOTA_MINIMA + " e " + NOTA_MAXIMA);
        }
        return nota;
    }
}
