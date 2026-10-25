package com.techchallenge.feedback.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.infrastructure.CosmosClientProvider;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

public class AvaliacaoRepository {
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 1_000;
    private final CosmosClientProvider clientProvider;

    public AvaliacaoRepository() {
        this(CosmosClientProvider.getInstance());
    }

    public AvaliacaoRepository(CosmosClientProvider clientProvider) {
        this.clientProvider = Objects.requireNonNull(clientProvider, "clientProvider e obrigatorio");
    }

    public Avaliacao salvar(Avaliacao avaliacao) {
        validateAvaliacao(avaliacao);
        return container().upsertItem(avaliacao).getItem();
    }

    public PaginaAvaliacao buscarDesde(LocalDate dia) {
        return buscarDesde(dia, null, configuredPageSize());
    }

    public PaginaAvaliacao buscarDesde(LocalDate dia, String continuationToken, int pageSize) {
        Objects.requireNonNull(dia, "dia e obrigatorio");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize deve estar entre 1 e " + MAX_PAGE_SIZE);
        }

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.dia >= @dia ORDER BY c.dia ASC",
                new SqlParameter("@dia", dia.toString()));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        var pages = container().queryItems(query, options, Avaliacao.class)
                .iterableByPage(continuationToken, pageSize)
                .iterator();

        if (!pages.hasNext()) {
            return new PaginaAvaliacao(List.of(), null);
        }
        FeedResponse<Avaliacao> page = pages.next();
        return new PaginaAvaliacao(List.copyOf(page.getResults()), page.getContinuationToken());
    }

    private CosmosContainer container() {
        return clientProvider.getAvaliacaoContainer();
    }

    private static void validateAvaliacao(Avaliacao avaliacao) {
        Objects.requireNonNull(avaliacao, "avaliacao e obrigatoria");
        required("id", avaliacao.id());
        required("descricao", avaliacao.descricao());
        required("dia", avaliacao.dia());
        try {
            LocalDate.parse(avaliacao.dia());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("dia deve usar o formato ISO-8601 yyyy-MM-dd", exception);
        }
    }

    private static void required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " e obrigatorio");
        }
    }

    private static int configuredPageSize() {
        String configured = System.getenv("COSMOS_AVALIACOES_PAGE_SIZE");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("COSMOS_AVALIACOES_PAGE_SIZE deve ser um número inteiro", exception);
        }
    }

    public record PaginaAvaliacao(List<Avaliacao> itens, String continuationToken) {
    }
}
