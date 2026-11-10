package com.techchallenge.feedback.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.FixedDelayRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.service.NotificacaoUrgenciaService;

import java.util.Arrays;
import java.util.List;

/**
 * Recebe alterações do Cosmos DB e delega os alertas de avaliações críticas.
 */
public class NotificarUrgenciaFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static volatile NotificacaoUrgenciaService service;

    /**
     * Cria a função; o serviço de notificação é inicializado no primeiro disparo.
     */
    public NotificarUrgenciaFunction() {
    }

    /**
     * Processa o lote do change feed e registra a quantidade de alertas enviados.
     *
     * @param avaliacoesJson array JSON de avaliações alteradas
     * @param context contexto de execução e logs
     * @throws JsonProcessingException se o lote não puder ser desserializado
     */
    @FunctionName("NotificarUrgencia")
    @FixedDelayRetry(maxRetryCount = 3, delayInterval = "00:00:10")
    public void run(
            @CosmosDBTrigger(
                    name = "avaliacoes",
                    databaseName = "%COSMOS_DATABASE%",
                    containerName = "%COSMOS_CONTAINER_AVALIACOES%",
                    leaseContainerName = "%COSMOS_LEASES_CONTAINER%",
                    connection = "COSMOS_CONNECTION")
            String avaliacoesJson,
            final ExecutionContext context) throws JsonProcessingException {

        List<Avaliacao> avaliacoes = lerAvaliacoes(avaliacoesJson);
        context.getLogger().info("Change feed: " + avaliacoes.size() + " avaliação(ões) recebida(s)");

        int enviados = servico().notificarCriticas(avaliacoes);
        context.getLogger().info("E-mails de urgência enviados: " + enviados);
    }

    private static List<Avaliacao> lerAvaliacoes(String json) throws JsonProcessingException {
        return Arrays.asList(MAPPER.readValue(json, Avaliacao[].class));
    }

    private static NotificacaoUrgenciaService servico() {
        NotificacaoUrgenciaService atual = service;
        if (atual == null) {
            synchronized (NotificarUrgenciaFunction.class) {
                atual = service;
                if (atual == null) {
                    atual = new NotificacaoUrgenciaService();
                    service = atual;
                }
            }
        }
        return atual;
    }
}
