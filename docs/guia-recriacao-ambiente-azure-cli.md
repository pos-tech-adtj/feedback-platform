# Guia de Recriação do Ambiente Azure via Azure CLI

Este guia foi gerado a partir da análise do arquivo `main.tf` (provider `azurerm`) e traduz cada recurso Terraform em comandos equivalentes do **Azure CLI**, para que o ambiente possa ser recriado manualmente em outra assinatura Azure, sem depender do Terraform.

## Recursos identificados no arquivo Terraform

| Recurso Terraform | Nome original | Finalidade |
|---|---|---|
| `azurerm_communication_service` | communication-feedback-tech | Serviço de Comunicação (Azure Communication Services) |
| `azurerm_email_communication_service` | email-feedback-tech | Serviço de E-mail |
| `azurerm_email_communication_service_domain` | AzureManagedDomain | Domínio gerenciado pela Azure para envio de e-mails |
| `azurerm_cosmosdb_account` | feedback-db | Banco de dados Cosmos DB (SQL API, modo Serverless) |
| `azurerm_user_assigned_identity` | GitHub | Identidade gerenciada (uso típico: CI/CD via GitHub Actions) |
| `azurerm_log_analytics_workspace` | log-feedback | Workspace do Log Analytics |
| `azurerm_portal_dashboard` | insight-feedback Dashboard | Dashboard do Portal Azure (Application Insights) |
| `azurerm_storage_account` | feedbacktechchallenge4 | Conta de armazenamento (usada pela Function App) |
| `azurerm_service_plan` | ASP-techchallengefase4-b576 | Plano do App Service (SKU `FC1` – Flex Consumption) |
| `azurerm_function_app_flex_consumption` | appi-feedback | Function App em plano Flex Consumption (Java 21) |
| `azurerm_monitor_action_group` | Application Insights Smart Detection | Grupo de Ação para alertas de detecção inteligente |
| `azurerm_application_insights` | insight-feedback | Application Insights (baseado em workspace) |

Todos os recursos estão no grupo de recursos **`tech_challenge_fase4`**, na região **`brazilsouth`** (os serviços de Communication/Email usam `location = Global` com `data-location = Brazil`).

## Observações importantes antes de começar

1. **Nomes globalmente únicos**: `feedbacktechchallenge4` (Storage Account), `appi-feedback` (Function App), `communication-feedback-tech` e `email-feedback-tech` precisam ser únicos em toda a Azure. Como esses nomes já existem no seu ambiente original, o cliente provavelmente precisará usar nomes diferentes.
2. **IDs de assinatura**: o JSON do `azurerm_portal_dashboard` no Terraform contém referências fixas à assinatura original (`b5dc37de-...`). Esse dashboard é opcional e só funcionará corretamente se o JSON for regenerado com o ID da nova assinatura (passo 12).
3. **Disponibilidade de recursos por região**: a Function App usa o plano **Flex Consumption**, disponível apenas em regiões específicas. Confirme com `az functionapp list-flexconsumption-locations -o table`.
4. **Pré-requisitos**: Azure CLI versão 2.60+ (`az --version`), login feito (`az login`) e assinatura correta selecionada (`az account set --subscription "<ID_OU_NOME>"`). As extensões `communication` e `portal` são instaladas automaticamente no primeiro uso.

## Variáveis usadas nos comandos

Defina estas variáveis no terminal (bash) para reaproveitar nos comandos abaixo. Ajuste os nomes conforme a observação 1 acima.

```bash
RG="tech_challenge_fase4"
LOCATION="brazilsouth"

STORAGE_NAME="feedbacktechchallenge4"      # precisa ser único
FUNCTION_APP_NAME="appi-feedback"          # precisa ser único
COMM_SERVICE_NAME="communication-feedback-tech"   # precisa ser único
EMAIL_SERVICE_NAME="email-feedback-tech"          # precisa ser único
COSMOS_ACCOUNT_NAME="feedback-db"          # precisa ser único
```

---

## Passo 1 – Grupo de recursos

```bash
az group create --name $RG --location $LOCATION
```

## Passo 2 – Identidade gerenciada (User Assigned Identity)

```bash
az identity create \
  --name GitHub \
  --resource-group $RG \
  --location $LOCATION
```

## Passo 3 – Log Analytics Workspace

```bash
az monitor log-analytics workspace create \
  --resource-group $RG \
  --workspace-name log-feedback \
  --location $LOCATION \
  --sku PerGB2018 \
  --retention-time 30
```

## Passo 4 – Application Insights (baseado em workspace)

```bash
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group $RG --workspace-name log-feedback --query id -o tsv)

az monitor app-insights component create \
  --app insight-feedback \
  --location $LOCATION \
  --resource-group $RG \
  --workspace $WORKSPACE_ID \
  --application-type web \
  --kind web
```

> Ajustes finos do Terraform (`ip_masking_enabled`, `sampling_percentage = 0`, `daily_data_cap_notifications_enabled`, `local_authentication_enabled`) não têm sempre um parâmetro direto e estável no `az monitor app-insights component create`. Confirme/ajuste pelo Portal em *Application Insights > Configurar* ou via `az resource update` caso precise reproduzi-los exatamente.

## Passo 5 – Grupo de Ação (Application Insights Smart Detection)

Os IDs abaixo são papéis internos (built-in roles) do Azure RBAC: **Monitoring Contributor** e **Monitoring Reader**, exatamente como no Terraform.

```bash
az monitor action-group create \
  --name "Application Insights Smart Detection" \
  --resource-group $RG \
  --short-name SmartDetect \
  --action armrole "Monitoring Contributor" 749f88d5-cbae-40b8-bcfc-e573ddc772fa usecommonalertschema \
  --action armrole "Monitoring Reader" 43d0d8ad-25c7-4714-9337-8ba259a9fe05 usecommonalertschema
```

## Passo 6 – Conta de Armazenamento (Storage Account)

```bash
az storage account create \
  --name $STORAGE_NAME \
  --resource-group $RG \
  --location $LOCATION \
  --sku Standard_LRS \
  --kind StorageV2 \
  --access-tier Hot

az storage account blob-service-properties update \
  --account-name $STORAGE_NAME \
  --resource-group $RG \
  --enable-delete-retention true \
  --delete-retention-days 1 \
  --enable-container-delete-retention true \
  --container-delete-retention-days 1
```

## Passo 7 – Azure Communication Services

```bash
az communication create \
  --name $COMM_SERVICE_NAME \
  --location "Global" \
  --data-location "Brazil" \
  --resource-group $RG
```

## Passo 8 – Serviço de E-mail e Domínio Gerenciado

```bash
az communication email create \
  --name $EMAIL_SERVICE_NAME \
  --location "Global" \
  --data-location "Brazil" \
  --resource-group $RG

az communication email domain create \
  --domain-name AzureManagedDomain \
  --email-service-name $EMAIL_SERVICE_NAME \
  --resource-group $RG \
  --location "Global" \
  --domain-management AzureManaged
```

## Passo 9 – Azure Cosmos DB (SQL API, Serverless)

```bash
az cosmosdb create \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RG \
  --locations regionName=$LOCATION failoverPriority=0 isZoneRedundant=False \
  --capabilities EnableServerless \
  --default-consistency-level Session \
  --enable-automatic-failover true \
  --enable-analytical-storage true \
  --analytical-storage-schema-type WellDefined \
  --backup-policy-type Periodic \
  --backup-interval 240 \
  --backup-retention 8 \
  --backup-redundancy Local

az cosmosdb update \
  --name $COSMOS_ACCOUNT_NAME \
  --resource-group $RG \
  --tags defaultExperience="Core (SQL)" hidden-workload-type="Learning"
```

> O Terraform também define `capacity.total_throughput_limit = 4000`. Esse limite de governança de custos normalmente é tratado pelo Portal/ARM/Bicep (propriedade `capacity.totalThroughputLimit`); não há um parâmetro direto e estável no `az cosmosdb create` para isso — ajuste pelo Portal em *Cost Management* da conta Cosmos DB, se necessário.

## Passo 10 – Function App em Plano Flex Consumption (Java 21)

O comando abaixo já cria, nos bastidores, o plano Flex Consumption (`FC1`) e o container de implantação no Storage Account — não é necessário criar o `azurerm_service_plan` separadamente.

```bash
az functionapp create \
  --resource-group $RG \
  --name $FUNCTION_APP_NAME \
  --storage-account $STORAGE_NAME \
  --flexconsumption-location $LOCATION \
  --runtime java \
  --runtime-version 21
```

Em seguida, aplique as demais configurações presentes no Terraform:

```bash
# Identidade gerenciada do sistema (identity { type = "SystemAssigned" })
az functionapp identity assign --resource-group $RG --name $FUNCTION_APP_NAME

# HTTPS obrigatório (https_only = true)
az functionapp update --resource-group $RG --name $FUNCTION_APP_NAME --set httpsOnly=true

# Certificado de cliente obrigatório (client_certificate_mode = "Required")
az functionapp update --resource-group $RG --name $FUNCTION_APP_NAME \
  --set clientCertEnabled=true clientCertMode=Required

# CORS (cors.allowed_origins)
az functionapp cors add --resource-group $RG --name $FUNCTION_APP_NAME \
  --allowed-origins "https://portal.azure.com"

# Conectar ao Application Insights criado no Passo 4
APPI_CONN_STRING=$(az monitor app-insights component show \
  --app insight-feedback --resource-group $RG --query connectionString -o tsv)

az functionapp config appsettings set --resource-group $RG --name $FUNCTION_APP_NAME \
  --settings APPLICATIONINSIGHTS_CONNECTION_STRING="$APPI_CONN_STRING"
```

> `webdeploy_publish_basic_authentication_enabled = false` desativa a autenticação básica do SCM/FTP. Isso é feito via `az resource update` sobre `basicPublishingCredentialsPolicies` (recurso filho do site), por exemplo:
> `az resource update --resource-group $RG --namespace Microsoft.Web --resource-type basicPublishingCredentialsPolicies --parent sites/$FUNCTION_APP_NAME --name scm --set properties.allow=false`

## Passo 11 (opcional) – Dashboard do Portal

O `azurerm_portal_dashboard` é um JSON grande que monta um dashboard visual em cima do Application Insights `insight-feedback`. Ele é **cosmético** (não afeta o funcionamento do ambiente) e contém IDs fixos da assinatura/recursos originais, então precisa ser adaptado antes de recriar:

1. Exporte o bloco `dashboard_properties` do `main.tf` (ou gere um novo pelo Portal: *Dashboard > Compartilhar > Baixar*) para um arquivo `dashboard.json`.
2. Substitua, no JSON, todas as ocorrências do ID de assinatura original (`b5dc37de-f5ac-44f3-9e12-7325a6179fad`) pelo ID da nova assinatura, e `tech_challenge_fase4` pelo nome do novo grupo de recursos (se for diferente).
3. Crie o dashboard:

```bash
az portal dashboard create \
  --resource-group $RG \
  --name "insight-feedback-dashboard" \
  --location $LOCATION \
  --input-path dashboard.json
```

---

## Verificação final

```bash
az resource list --resource-group $RG --output table
```

Confirme que os 11 recursos (fora o dashboard opcional) aparecem listados: Communication Service, Email Service, Email Domain, Cosmos DB, Identidade, Log Analytics, Storage Account, Function App (+ plano Flex Consumption gerado automaticamente), Action Group e Application Insights.

## Resumo das ordens de dependência

1. Grupo de recursos
2. Log Analytics Workspace → Application Insights
3. Storage Account (antes da Function App)
4. Serviço de E-mail (antes do Domínio de E-mail)
5. Function App (depende do Storage Account e, para a connection string, do Application Insights)
6. Dashboard do Portal (depende do Application Insights já existir — faça por último)

Identidade gerenciada, Communication Service, Cosmos DB e Grupo de Ação não têm dependências entre si e podem ser criados em qualquer ordem.
