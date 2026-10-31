package com.techchallenge.feedback.service;

import com.techchallenge.feedback.domain.Avaliacao;
import com.techchallenge.feedback.domain.Urgencia;
import com.techchallenge.feedback.infrastructure.CosmosClientProvider;
import com.techchallenge.feedback.repository.AvaliacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvaliacaoServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T02:30:00Z"), ZoneOffset.UTC);

    private RepositoryFake repository;
    private AvaliacaoService service;

    @BeforeEach
    void setUp() {
        repository = new RepositoryFake();
        service = new AvaliacaoService(repository, CLOCK);
    }

    @Test
    void deveRegistrarAvaliacaoComIdDiaEDataEnvioNoFusoDeSaoPaulo() {
        Avaliacao avaliacao = service.registrar("Aula muito boa", 9);

        assertDoesNotThrow(() -> UUID.fromString(avaliacao.id()));
        assertEquals("Aula muito boa", avaliacao.descricao());
        assertEquals(9, avaliacao.nota());
        assertEquals("2026-10-24", avaliacao.dia());
        assertEquals("2026-10-24T23:30-03:00", avaliacao.dataEnvio());
        assertEquals(List.of(avaliacao), repository.salvas);
    }
    @Test
    void deveRetornarAvaliacaoMontadaMesmoQuandoRepositorioRetornaNull() {
        Avaliacao avaliacao = service.registrar("Aula muito boa", 9);

        assertSame(repository.salvas.get(0), avaliacao);
    }

    @Test
    void deveGerarIdsDiferentesACadaRegistro() {
        Avaliacao primeira = service.registrar("Primeira", 5);
        Avaliacao segunda = service.registrar("Segunda", 5);

        assertNotEquals(primeira.id(), segunda.id());
    }

    @Test
    void deveRemoverEspacosDasExtremidadesDaDescricao() {
        Avaliacao avaliacao = service.registrar("  Aula confusa  ", 3);

        assertEquals("Aula confusa", avaliacao.descricao());
    }

    @Test
    void deveClassificarNotaBaixaComoCritica() {
        assertEquals(Urgencia.CRITICA, service.registrar("Aula ruim", 4).urgencia());
        assertEquals(Urgencia.MEDIA, service.registrar("Aula razoavel", 5).urgencia());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void deveAceitarNotasNosLimites(int nota) {
        assertEquals(nota, service.registrar("Descricao", nota).nota());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void deveRejeitarNotaForaDoIntervalo(int nota) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Descricao", nota));

        assertEquals("nota deve estar entre 0 e 10", erro.getMessage());
        assertTrue(repository.salvas.isEmpty());
    }

    @Test
    void deveRejeitarNotaAusente() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.registrar("Descricao", null));

        assertEquals("nota e obrigatoria", erro.getMessage());
        assertTrue(repository.salvas.isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarDescricaoAusente(String descricao) {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.registrar(descricao, 5));

        assertEquals("descricao e obrigatoria", erro.getMessage());
        assertTrue(repository.salvas.isEmpty());
    }

    @Test
    void deveAceitarDescricaoComTamanhoMaximo() {
        String descricao = "a".repeat(AvaliacaoService.TAMANHO_MAXIMO_DESCRICAO);

        assertEquals(descricao, service.registrar(descricao, 5).descricao());
    }

    @Test
    void deveRejeitarDescricaoAcimaDoTamanhoMaximo() {
        String descricao = "a".repeat(AvaliacaoService.TAMANHO_MAXIMO_DESCRICAO + 1);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> service.registrar(descricao, 5));

        assertEquals("descricao deve ter no maximo 1000 caracteres", erro.getMessage());
        assertTrue(repository.salvas.isEmpty());
    }

    private static class RepositoryFake extends AvaliacaoRepository {
        private final List<Avaliacao> salvas = new ArrayList<>();

        RepositoryFake() {
            super(CosmosClientProvider.getInstance());
        }

        @Override
        public Avaliacao salvar(Avaliacao avaliacao) {
            salvas.add(avaliacao);
            return null;
        }
    }
}
