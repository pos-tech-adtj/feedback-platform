package com.techchallenge.feedback.service;

import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.domain.Urgencia;
import com.techchallenge.feedback.infrastructure.CosmosClientProvider;
import com.techchallenge.feedback.repository.AvaliacaoRepository;
import com.techchallenge.feedback.service.ReportService.RelatorioSemanal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {
    // Segunda-feira 26/10/2026, 08:00 em Brasília.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T11:00:00Z"), ZoneOffset.UTC);

    /*
     * Cenário conferido manualmente. Janela: 20/10/2026 a 26/10/2026 (7 dias, incluindo hoje).
     *
     * | dia        | nota | urgência | incluída? |
     * | 2026-10-19 |  2   | CRITICA  | não (antes da janela) |
     * | 2026-10-20 |  3   | CRITICA  | sim |
     * | 2026-10-20 |  8   | MEDIA    | sim |
     * | 2026-10-22 | 10   | MEDIA    | sim |
     * | 2026-10-25 |  4   | CRITICA  | sim (limite de CRITICA) |
     * | 2026-10-26 |  5   | MEDIA    | sim (hoje) |
     *
     * Total: 5. Soma das notas: 3+8+10+4+5 = 30. Média: 30/5 = 6,00.
     * Por dia: 20/10 = 2, 22/10 = 1, 25/10 = 1, 26/10 = 1.
     * Por urgência: CRITICA = 2 (notas 3 e 4), MEDIA = 3 (notas 8, 10 e 5).
     */
    private static final List<Avaliacao> CENARIO = List.of(
            new Avaliacao("a1", "Avaliação fora da janela", 2, "2026-10-19", "2026-10-19T10:00-03:00"),
            new Avaliacao("a2", "Aula atrasada", 3, "2026-10-20", "2026-10-20T09:00-03:00"),
            new Avaliacao("a3", "Material muito bom", 8, "2026-10-20", "2026-10-20T18:00-03:00"),
            new Avaliacao("a4", "Excelente professor", 10, "2026-10-22", "2026-10-22T20:30-03:00"),
            new Avaliacao("a5", "Som ruim", 4, "2026-10-25", "2026-10-25T14:15-03:00"),
            new Avaliacao("a6", "Sala lotada", 5, "2026-10-26", "2026-10-26T07:45-03:00"));

    private RepositoryPaginadoFake repository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repository = new RepositoryPaginadoFake(CENARIO);
        service = new ReportService(repository, CLOCK);
    }

    @Test
    void deveCalcularTotalEMediaConferidosManualmente() {
        RelatorioSemanal relatorio = service.gerarRelatorioSemanal();

        assertEquals(LocalDate.parse("2026-10-20"), relatorio.inicio());
        assertEquals(LocalDate.parse("2026-10-26"), relatorio.fim());
        assertEquals(5, relatorio.total());
        assertEquals(6.0, relatorio.media().getAsDouble(), 0.0001);
    }

    @Test
    void deveContarQuantidadePorDiaConferidaManualmente() {
        RelatorioSemanal relatorio = service.gerarRelatorioSemanal();

        Map<String, Long> esperado = new TreeMap<>(Map.of(
                "2026-10-20", 2L,
                "2026-10-22", 1L,
                "2026-10-25", 1L,
                "2026-10-26", 1L));
        assertEquals(esperado, relatorio.quantidadePorDia());
    }

    @Test
    void deveContarQuantidadePorUrgenciaConferidaManualmente() {
        RelatorioSemanal relatorio = service.gerarRelatorioSemanal();

        assertEquals(2L, relatorio.quantidadePorUrgencia().get(Urgencia.CRITICA));
        assertEquals(3L, relatorio.quantidadePorUrgencia().get(Urgencia.MEDIA));
    }

    @Test
    void deveListarItensDaJanelaComDescricaoUrgenciaEDataEnvio() {
        RelatorioSemanal relatorio = service.gerarRelatorioSemanal();

        List<RelatorioSemanal.Item> itens = relatorio.itens();
        assertEquals(5, itens.size());
        assertEquals("Aula atrasada", itens.get(0).descricao());
        assertEquals(Urgencia.CRITICA, itens.get(0).urgencia());
        assertEquals("2026-10-20T09:00-03:00", itens.get(0).dataEnvio());
        assertFalse(itens.stream().anyMatch(item -> "Avaliação fora da janela".equals(item.descricao())));
    }

    @Test
    void deveIncluirTodasAsPaginasDoRepositorio() {
        service.gerarRelatorioSemanal();

        // Fake devolve 2 itens por página: 5 itens da janela exigem 3 chamadas.
        assertEquals(3, repository.chamadas);
    }

    @Test
    void deveMontarCorpoComMediaEmFormatoBrasileiro() {
        String corpo = service.gerarRelatorioSemanal().corpo();

        assertTrue(corpo.contains("Total de avaliações: 5"), corpo);
        assertTrue(corpo.contains("Média das notas: 6,00"), corpo);
        assertTrue(corpo.contains("20/10/2026: 2"), corpo);
    }

    @Test
    void deveMontarAssuntoComPeriodo() {
        RelatorioSemanal relatorio = service.gerarRelatorioSemanal();

        assertEquals("Relatório semanal de avaliações (20/10/2026 a 26/10/2026)", relatorio.assunto());
    }

    @Test
    void deveGerarRelatorioVazioQuandoNaoHaAvaliacoes() {
        ReportService servicoVazio = new ReportService(new RepositoryPaginadoFake(List.of()), CLOCK);

        RelatorioSemanal relatorio = servicoVazio.gerarRelatorioSemanal();

        assertEquals(0, relatorio.total());
        assertTrue(relatorio.media().isEmpty());
        assertTrue(relatorio.corpo().contains("sem avaliações no período"));
    }

    private static class RepositoryPaginadoFake extends AvaliacaoRepository {
        private static final int ITENS_POR_PAGINA = 2;

        private final List<Avaliacao> dados;
        private int chamadas;

        RepositoryPaginadoFake(List<Avaliacao> dados) {
            super(CosmosClientProvider.getInstance());
            this.dados = new ArrayList<>(dados);
        }

        @Override
        public PaginaAvaliacao buscarDesde(LocalDate dia, String continuationToken, int pageSize) {
            chamadas++;
            List<Avaliacao> filtradas = dados.stream()
                    .filter(avaliacao -> !LocalDate.parse(avaliacao.dia()).isBefore(dia))
                    .toList();
            int inicio = continuationToken == null ? 0 : Integer.parseInt(continuationToken);
            int fim = Math.min(inicio + ITENS_POR_PAGINA, filtradas.size());
            String proximoToken = fim < filtradas.size() ? String.valueOf(fim) : null;
            return new PaginaAvaliacao(filtradas.subList(inicio, fim), proximoToken);
        }
    }
}