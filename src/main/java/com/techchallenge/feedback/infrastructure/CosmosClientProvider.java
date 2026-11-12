package com.techchallenge.feedback.infrastructure;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

/**
 * Compartilha o cliente Cosmos DB autenticado por DefaultAzureCredential.
 */
public final class CosmosClientProvider implements AutoCloseable {
    /**
     * Nome da variável com o endpoint da conta Cosmos DB.
     */
    public static final String COSMOS_ENDPOINT = "COSMOS_ENDPOINT";
    /**
     * Nome da variável com o banco de dados.
     */
    public static final String COSMOS_DATABASE = "COSMOS_DATABASE";
    /**
     * Nome da variável com o container de avaliações.
     */
    public static final String COSMOS_CONTAINER_AVALIACOES = "COSMOS_CONTAINER_AVALIACOES";

    private static final CosmosClientProvider INSTANCE = new CosmosClientProvider();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::close, "cosmos-client-close"));
    }

    private final DefaultAzureCredential credential;
    private volatile CosmosClient client;

    private CosmosClientProvider() {
        this.credential = new DefaultAzureCredentialBuilder().build();
    }

    /**
     * Obtém o provedor compartilhado.
     *
     * @return instância única do processo
     */
    public static CosmosClientProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Obtém o cliente, criando-o no primeiro acesso.
     *
     * @return cliente compartilhado
     * @throws IllegalStateException se COSMOS_ENDPOINT não estiver configurado
     */
    public CosmosClient getClient() {
        CosmosClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new CosmosClientBuilder()
                            .endpoint(requiredEnvironmentVariable(COSMOS_ENDPOINT))
                            .credential(credential)
                            .buildClient();
                    client = current;
                }
            }
        }
        return current;
    }

    /**
     * Obtém a referência ao banco configurado.
     *
     * @return banco de avaliações
     * @throws IllegalStateException se a configuração obrigatória estiver ausente
     */
    public CosmosDatabase getDatabase() {
        return getClient().getDatabase(requiredEnvironmentVariable(COSMOS_DATABASE));
    }

    /**
     * Obtém a referência ao container de avaliações.
     *
     * @return container configurado
     * @throws IllegalStateException se a configuração obrigatória estiver ausente
     */
    public CosmosContainer getAvaliacaoContainer() {
        return getDatabase().getContainer(requiredEnvironmentVariable(COSMOS_CONTAINER_AVALIACOES));
    }

    /**
     * Fecha o cliente inicializado e libera sua referência.
     */
    @Override
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("A variável de ambiente " + name + " é obrigatória para acessar o Azure Cosmos DB");
        }
        return value.trim();
    }
}
