package com.techchallenge.feedback.service;

import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.domain.Urgencia;
import com.techchallenge.feedback.repository.AvaliacaoRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ReportService {
    static final int DIAS_DO_RELATORIO = 7;
    static final int TAMANHO_PAGINA = 100;
    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

    private final AvaliacaoRepository repository;
    private final Clock clock;

    public ReportService() {
        this(new AvaliacaoRepository(), Clock.system(FUSO_HORARIO));
    }

    ReportService(AvaliacaoRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio").withZone(FUSO_HORARIO);
    }

    /**
     * Gera o relatório dos últimos 7 dias, incluindo hoje (horário de Brasília).
     */
    public RelatorioSemanal gerarRelatorioSemanal() {
        LocalDate fim = LocalDate.now(clock);
        LocalDate inicio = fim.minusDays(DIAS_DO_RELATORIO - 1);
        return RelatorioSemanal.de(inicio, fim, buscarTodasDesde(inicio));
    }

    /**
     * O repositório devolve uma página por vez; percorre todas até o token de continuação acabar.
     */
    private List<Avaliacao> buscarTodasDesde(LocalDate inicio) {
        List<Avaliacao> todas = new ArrayList<>();
        String continuationToken = null;
        do {
            AvaliacaoRepository.PaginaAvaliacao pagina =
                    repository.buscarDesde(inicio, continuationToken, TAMANHO_PAGINA);
            todas.addAll(pagina.itens());
            continuationToken = pagina.continuationToken();
        } while (continuationToken != null);
        return todas;
    }

    public record RelatorioSemanal(
            LocalDate inicio,
            LocalDate fim,
            long total,
            OptionalDouble media,
            Map<String, Long> quantidadePorDia,
            Map<Urgencia, Long> quantidadePorUrgencia,
            List<Item> itens) {

        private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

        /**
         * Cálculo puro: não acessa banco nem rede. Considera apenas avaliações com dia entre inicio e fim (inclusive).
         */
        public static RelatorioSemanal de(LocalDate inicio, LocalDate fim, List<Avaliacao> avaliacoes) {
            List<Avaliacao> noPeriodo = avaliacoes.stream()
                    .filter(avaliacao -> estaEntre(avaliacao, inicio, fim))
                    .sorted(Comparator.comparing(Avaliacao::dia)
                            .thenComparing(Avaliacao::dataEnvio, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            OptionalDouble media = noPeriodo.stream().mapToInt(Avaliacao::nota).average();

            Map<String, Long> porDia = noPeriodo.stream()
                    .collect(Collectors.groupingBy(Avaliacao::dia, TreeMap::new, Collectors.counting()));

            Map<Urgencia, Long> porUrgencia = noPeriodo.stream()
                    .collect(Collectors.groupingBy(Avaliacao::urgencia,
                            () -> new EnumMap<>(Urgencia.class),
                            Collectors.counting()));

            List<Item> itens = noPeriodo.stream()
                    .map(avaliacao -> new Item(avaliacao.descricao(), avaliacao.urgencia(), avaliacao.dataEnvio()))
                    .toList();

            return new RelatorioSemanal(inicio, fim, noPeriodo.size(), media, porDia, porUrgencia, itens);
        }

        public String assunto() {
            return "Relatório semanal de avaliações (" + DATA_BR.format(inicio) + " a " + DATA_BR.format(fim) + ")";
        }

        public String corpo() {
            StringBuilder corpo = new StringBuilder();
            corpo.append("Relatório semanal de avaliações\n");
            corpo.append("Período: ").append(DATA_BR.format(inicio))
                    .append(" a ").append(DATA_BR.format(fim)).append("\n\n");

            corpo.append("Total de avaliações: ").append(total).append("\n");
            corpo.append("Média das notas: ")
                    .append(media.isPresent()
                            ? String.format(PT_BR, "%.2f", media.getAsDouble())
                            : "sem avaliações no período")
                    .append("\n\n");

            corpo.append("Quantidade por dia:\n");
            if (quantidadePorDia.isEmpty()) {
                corpo.append("  (nenhuma avaliação)\n");
            }
            quantidadePorDia.forEach((dia, quantidade) -> corpo.append("  ")
                    .append(DATA_BR.format(LocalDate.parse(dia))).append(": ").append(quantidade).append("\n"));

            corpo.append("\nQuantidade por urgência:\n");
            for (Urgencia urgencia : Urgencia.values()) {
                corpo.append("  ").append(urgencia).append(": ")
                        .append(quantidadePorUrgencia.getOrDefault(urgencia, 0L)).append("\n");
            }

            corpo.append("\nAvaliações:\n");
            if (itens.isEmpty()) {
                corpo.append("  (nenhuma avaliação)\n");
            }
            for (Item item : itens) {
                corpo.append("  - [").append(item.urgencia()).append("] ")
                        .append(item.descricao())
                        .append(" (enviada em ").append(item.dataEnvio()).append(")\n");
            }
            return corpo.toString();
        }

        private static boolean estaEntre(Avaliacao avaliacao, LocalDate inicio, LocalDate fim) {
            LocalDate dia = LocalDate.parse(avaliacao.dia());
            return !dia.isBefore(inicio) && !dia.isAfter(fim);
        }

        public record Item(String descricao, Urgencia urgencia, String dataEnvio) {
        }
    }
}
