package com.techchallenge.feedback.function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.service.AvaliacaoService;

import java.util.Optional;
import java.util.logging.Level;

public class EnviarAvaliacaoFunction {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AvaliacaoService avaliacaoService;

    public EnviarAvaliacaoFunction() {
        this(new AvaliacaoService());
    }

    EnviarAvaliacaoFunction(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "request",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "avaliacao") HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {
        try {
            RequisicaoAvaliacao corpo = lerCorpo(request.getBody());
            Avaliacao avaliacao = avaliacaoService.registrar(corpo.descricao(), corpo.nota());

            context.getLogger().info(() -> "Avaliacao registrada: id=" + avaliacao.id()
                    + ", urgencia=" + avaliacao.urgencia());
            return json(request, HttpStatus.CREATED, avaliacao);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return erro(request, exception.getMessage());
        } catch (Exception exception) {
            context.getLogger().log(Level.SEVERE, "Erro ao registrar avaliacao", exception);
            return erro(request, "erro interno ao registrar avaliacao", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static RequisicaoAvaliacao lerCorpo(Optional<String> corpo) throws JsonProcessingException {
        if (corpo == null || corpo.isEmpty() || corpo.get().isBlank()) {
            throw new IllegalArgumentException("corpo da requisicao e obrigatorio");
        }
        return OBJECT_MAPPER.readValue(corpo.get(), RequisicaoAvaliacao.class);
    }

    private static HttpResponseMessage erro(HttpRequestMessage<?> request, String mensagem) {
        return erro(request, mensagem, HttpStatus.BAD_REQUEST);
    }

    private static HttpResponseMessage erro(HttpRequestMessage<?> request, String mensagem, HttpStatus status) {
        return json(request, status, new RespostaErro(mensagem == null ? "requisicao invalida" : mensagem));
    }

    private static HttpResponseMessage json(HttpRequestMessage<?> request, HttpStatus status, Object corpo) {
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(corpo)
                .build();
    }

    private record RequisicaoAvaliacao(String descricao, Integer nota) {
    }

    private record RespostaErro(String erro) {
    }
}
