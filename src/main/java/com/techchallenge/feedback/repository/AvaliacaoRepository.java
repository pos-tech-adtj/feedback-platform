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

/**
 * Persiste avaliações e consulta páginas no Cosmos DB.
 */
public class AvaliacaoRepository {
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 1_000;
    private final CosmosClientProvider clientProvider;

    /**
     * Cria o repositório com o provedor compartilhado.
     */
    public AvaliacaoRepository() {
        this(CosmosClientProvider.getInstance());
    }

    /**
     * Cria o repositório com o provedor informado.
     *
     * @param clientProvider provedor de acesso ao Cosmos DB
     * @throws NullPointerException se o provedor for nulo
     */
    public AvaliacaoRepository(CosmosClientProvider clientProvider) {
        this.clientProvider = Objects.requireNonNull(clientProvider, "clientProvider e obrigatorio");
    }

    /**
     * Insere ou substitui a avaliação pela identidade e partição do documento.
     *
     * @param avaliacao avaliação a persistir
     * @return documento retornado pelo Cosmos DB
     * @throws NullPointerException se a avaliação for nula
     * @throws IllegalArgumentException se id, descrição ou dia forem inválidos
     */
    public Avaliacao salvar(Avaliacao avaliacao) {
        validateAvaliacao(avaliacao);
        return container().upsertItem(avaliacao).getItem();
    }

    /**
     * Consulta a primeira página a partir do dia, inclusive.
     *
     * @param dia data inicial
     * @return página com tamanho definido por COSMOS_AVALIACOES_PAGE_SIZE (padrão 100)
     * @throws NullPointerException se dia for nulo
     * @throws IllegalStateException se o tamanho configurado não for inteiro
     * @throws IllegalArgumentException se o tamanho estiver fora de 1 a 1.000
     */
    public PaginaAvaliacao buscarDesde(LocalDate dia) {
        return buscarDesde(dia, null, configuredPageSize());
    }

    /**
     * Consulta uma página ordenada por dia a partir da data informada.
     *
     * @param dia data inicial inclusiva
     * @param continuationToken token da página anterior ou null para iniciar
     * @param pageSize tamanho da página entre 1 e 1.000
     * @return itens e token da próxima página; token null indica o fim
     * @throws NullPointerException se dia for nulo
     * @throws IllegalArgumentException se pageSize estiver fora do intervalo
     */
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

    /**
     * Página de resultados da consulta de avaliações.
     *
     * @param itens avaliações da página
     * @param continuationToken token da próxima página ou null ao finalizar
     */
    public record PaginaAvaliacao(List<Avaliacao> itens, String continuationToken) {
    }
}
