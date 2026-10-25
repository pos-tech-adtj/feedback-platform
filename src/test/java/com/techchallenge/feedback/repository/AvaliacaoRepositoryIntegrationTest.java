package com.techchallenge.feedback.repository;

import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.infrastructure.CosmosClientProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "COSMOS_INTEGRATION_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "COSMOS_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "COSMOS_DATABASE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "COSMOS_CONTAINER_AVALIACOES", matches = ".+")
class AvaliacaoRepositoryIntegrationTest {
    @Test
    void deveGravarEConsultarAvaliacaoRealNoCosmos() {
        CosmosClientProvider provider = CosmosClientProvider.getInstance();
        try {
            AvaliacaoRepository repository = new AvaliacaoRepository(provider);
            LocalDate hoje = LocalDate.now();
            Avaliacao esperada = new Avaliacao(
                    "integration-" + UUID.randomUUID(),
                    "Documento criado pelo teste de integração em " + Instant.now(),
                    5,
                    hoje.toString(),
                    Instant.now().toString());

            System.out.println("Documento de integração Cosmos criado com id: " + esperada.id());
            repository.salvar(esperada);
            String continuationToken = null;
            boolean found = false;
            do {
                AvaliacaoRepository.PaginaAvaliacao resultado = repository.buscarDesde(hoje, continuationToken, 100);
                found = resultado.itens().stream().anyMatch(item -> esperada.id().equals(item.id()));
                continuationToken = resultado.continuationToken();
            } while (!found && continuationToken != null);

            assertTrue(found, "A avaliação recém-gravada deve estar no resultado da consulta");
        } finally {
            provider.close();
        }
    }
}
