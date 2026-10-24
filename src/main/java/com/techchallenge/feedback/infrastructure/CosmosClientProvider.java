package com.techchallenge.feedback.infrastructure;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

public final class CosmosClientProvider implements AutoCloseable {
    public static final String COSMOS_ENDPOINT = "COSMOS_ENDPOINT";
    public static final String COSMOS_DATABASE = "COSMOS_DATABASE";
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

    public static CosmosClientProvider getInstance() {
        return INSTANCE;
    }


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

    public CosmosDatabase getDatabase() {
        return getClient().getDatabase(requiredEnvironmentVariable(COSMOS_DATABASE));
    }

    public CosmosContainer getAvaliacaoContainer() {
        return getDatabase().getContainer(requiredEnvironmentVariable(COSMOS_CONTAINER_AVALIACOES));
    }

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
