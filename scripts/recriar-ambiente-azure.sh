#!/usr/bin/env bash
#
# recriar-ambiente-azure.sh
#
# Recria, via Azure CLI, o ambiente Azure originalmente definido no
# Terraform (main.tf) do grupo de recursos "tech_challenge_fase4".
#
# Pré-requisitos:
#   - Azure CLI instalado (>= 2.60)
#   - Login feito:            az login
#   - Assinatura selecionada: az account set --subscription "<ID_OU_NOME>"
#
# Uso:
#   chmod +x recriar-ambiente-azure.sh
#   ./recriar-ambiente-azure.sh
#
set -euo pipefail

# ─────────────────────────────────────────────────────────────
# VARIÁVEIS — AJUSTE ANTES DE EXECUTAR
# ─────────────────────────────────────────────────────────────
# Os nomes marcados como "precisa ser único" precisam ser
# globalmente únicos na Azure. Como já existem no ambiente de
# origem, troque-os por nomes novos antes de rodar o script.

RG="tech_challenge_fase4"
LOCATION="brazilsouth"

STORAGE_NAME="feedbacktechchallenge4"             # precisa ser único
FUNCTION_APP_NAME="appi-feedback"                 # precisa ser único
COMM_SERVICE_NAME="communication-feedback-tech"   # precisa ser único
EMAIL_SERVICE_NAME="email-feedback-tech"          # precisa ser único
COSMOS_ACCOUNT_NAME="feedback-db"                 # precisa ser único

LOG_ANALYTICS_NAME="log-feedback"
APP_INSIGHTS_NAME="insight-feedback"
IDENTITY_NAME="GitHub"
ACTION_GROUP_NAME="Application Insights Smart Detection"
ACTION_GROUP_SHORT_NAME="SmartDetect"

# Caminho opcional para o JSON do dashboard (Passo 11, opcional).
# Deixe em branco ("") para pular essa etapa.
DASHBOARD_JSON_PATH=""
DASHBOARD_NAME="insight-feedback-dashboard"

# ─────────────────────────────────────────────────────────────
# FUNÇÃO AUXILIAR
# ─────────────────────────────────────────────────────────────
step() {
  echo ""
  echo "============================================================"
  echo "  $1"
  echo "============================================================"
}

# ─────────────────────────────────────────────────────────────
# PASSO 0 — Verifica sessão/assinatura ativa
# ─────────────────────────────────────────────────────────────
step "Passo 0: Sessão do Azure CLI"
az account show --output table

# ─────────────────────────────────────────────────────────────
# PASSO 1 — Grupo de recursos
# ─────────────────────────────────────────────────────────────
step "Passo 1: Grupo de recursos ($RG)"
az group create --name "$RG" --location "$LOCATION"

# ─────────────────────────────────────────────────────────────
# PASSO 2 — Identidade gerenciada
# ─────────────────────────────────────────────────────────────
step "Passo 2: Identidade gerenciada ($IDENTITY_NAME)"
az identity create \
  --name "$IDENTITY_NAME" \
  --resource-group "$RG" \
  --location "$LOCATION"

# ─────────────────────────────────────────────────────────────
# PASSO 3 — Log Analytics Workspace
# ─────────────────────────────────────────────────────────────
step "Passo 3: Log Analytics Workspace ($LOG_ANALYTICS_NAME)"
az monitor log-analytics workspace create \
  --resource-group "$RG" \
  --workspace-name "$LOG_ANALYTICS_NAME" \
  --location "$LOCATION" \
  --sku PerGB2018 \
  --retention-time 30

# ─────────────────────────────────────────────────────────────
# PASSO 4 — Application Insights (baseado em workspace)
# ─────────────────────────────────────────────────────────────
step "Passo 4: Application Insights ($APP_INSIGHTS_NAME)"
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group "$RG" --workspace-name "$LOG_ANALYTICS_NAME" --query id -o tsv)

az monitor app-insights component create \
  --app "$APP_INSIGHTS_NAME" \
  --location "$LOCATION" \
  --resource-group "$RG" \
  --workspace "$WORKSPACE_ID" \
  --application-type web \
  --kind web

# ─────────────────────────────────────────────────────────────
# PASSO 5 — Grupo de Ação (Smart Detection)
# ─────────────────────────────────────────────────────────────
step "Passo 5: Grupo de Ação ($ACTION_GROUP_NAME)"
az monitor action-group create \
  --name "$ACTION_GROUP_NAME" \
  --resource-group "$RG" \
  --short-name "$ACTION_GROUP_SHORT_NAME" \
  --action armrole "Monitoring Contributor" 749f88d5-cbae-40b8-bcfc-e573ddc772fa usecommonalertschema \
  --action armrole "Monitoring Reader" 43d0d8ad-25c7-4714-9337-8ba259a9fe05 usecommonalertschema

# ─────────────────────────────────────────────────────────────
# PASSO 6 — Storage Account
# ─────────────────────────────────────────────────────────────
step "Passo 6: Storage Account ($STORAGE_NAME)"
az storage account create \
  --name "$STORAGE_NAME" \
  --resource-group "$RG" \
  --location "$LOCATION" \
  --sku Standard_LRS \
  --kind StorageV2 \
  --access-tier Hot

az storage account blob-service-properties update \
  --account-name "$STORAGE_NAME" \
  --resource-group "$RG" \
  --enable-delete-retention true \
  --delete-retention-days 1 \
  --enable-container-delete-retention true \
  --container-delete-retention-days 1

# ─────────────────────────────────────────────────────────────
# PASSO 7 — Azure Communication Services
# ─────────────────────────────────────────────────────────────
step "Passo 7: Communication Service ($COMM_SERVICE_NAME)"
az communication create \
  --name "$COMM_SERVICE_NAME" \
  --location "Global" \
  --data-location "Brazil" \
  --resource-group "$RG"

# ─────────────────────────────────────────────────────────────
# PASSO 8 — Serviço de E-mail + Domínio Gerenciado
# ─────────────────────────────────────────────────────────────
step "Passo 8: Email Communication Service ($EMAIL_SERVICE_NAME)"
az communication email create \
  --name "$EMAIL_SERVICE_NAME" \
  --location "Global" \
  --data-location "Brazil" \
  --resource-group "$RG"

az communication email domain create \
  --domain-name AzureManagedDomain \
  --email-service-name "$EMAIL_SERVICE_NAME" \
  --resource-group "$RG" \
  --location "Global" \
  --domain-management AzureManaged

# ─────────────────────────────────────────────────────────────
# PASSO 9 — Cosmos DB (SQL API, Serverless)
# ─────────────────────────────────────────────────────────────
step "Passo 9: Cosmos DB ($COSMOS_ACCOUNT_NAME)"
az cosmosdb create \
  --name "$COSMOS_ACCOUNT_NAME" \
  --resource-group "$RG" \
  --locations regionName="$LOCATION" failoverPriority=0 isZoneRedundant=False \
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
  --name "$COSMOS_ACCOUNT_NAME" \
  --resource-group "$RG" \
  --tags defaultExperience="Core (SQL)" hidden-workload-type="Learning"

# ─────────────────────────────────────────────────────────────
# PASSO 10 — Function App (Plano Flex Consumption, Java 21)
# ─────────────────────────────────────────────────────────────
step "Passo 10: Function App ($FUNCTION_APP_NAME)"
az functionapp create \
  --resource-group "$RG" \
  --name "$FUNCTION_APP_NAME" \
  --storage-account "$STORAGE_NAME" \
  --flexconsumption-location "$LOCATION" \
  --runtime java \
  --runtime-version 21

echo "Aplicando configurações adicionais da Function App..."

# Identidade gerenciada do sistema (identity { type = "SystemAssigned" })
az functionapp identity assign --resource-group "$RG" --name "$FUNCTION_APP_NAME"

# HTTPS obrigatório (https_only = true)
az functionapp update --resource-group "$RG" --name "$FUNCTION_APP_NAME" --set httpsOnly=true

# Certificado de cliente obrigatório (client_certificate_mode = "Required")
az functionapp update --resource-group "$RG" --name "$FUNCTION_APP_NAME" \
  --set clientCertEnabled=true clientCertMode=Required

# CORS (site_config.cors.allowed_origins)
az functionapp cors add --resource-group "$RG" --name "$FUNCTION_APP_NAME" \
  --allowed-origins "https://portal.azure.com"

# Conecta a Function App ao Application Insights criado no Passo 4
APPI_CONN_STRING=$(az monitor app-insights component show \
  --app "$APP_INSIGHTS_NAME" --resource-group "$RG" --query connectionString -o tsv)

az functionapp config appsettings set --resource-group "$RG" --name "$FUNCTION_APP_NAME" \
  --settings APPLICATIONINSIGHTS_CONNECTION_STRING="$APPI_CONN_STRING"

# Desativa autenticação básica do SCM (webdeploy_publish_basic_authentication_enabled = false)
az resource update \
  --resource-group "$RG" \
  --namespace Microsoft.Web \
  --resource-type basicPublishingCredentialsPolicies \
  --parent "sites/$FUNCTION_APP_NAME" \
  --name scm \
  --set properties.allow=false

# ─────────────────────────────────────────────────────────────
# PASSO 11 (OPCIONAL) — Dashboard do Portal
# ─────────────────────────────────────────────────────────────
if [[ -n "$DASHBOARD_JSON_PATH" ]]; then
  step "Passo 11: Dashboard do Portal ($DASHBOARD_NAME)"
  az portal dashboard create \
    --resource-group "$RG" \
    --name "$DASHBOARD_NAME" \
    --location "$LOCATION" \
    --input-path "$DASHBOARD_JSON_PATH"
else
  echo ""
  echo "Passo 11 (Dashboard) pulado — defina DASHBOARD_JSON_PATH no topo do script para criá-lo."
  echo "Lembre-se de atualizar, no JSON, o ID da assinatura e o nome do grupo de recursos antes de usar."
fi

# ─────────────────────────────────────────────────────────────
# VERIFICAÇÃO FINAL
# ─────────────────────────────────────────────────────────────
step "Verificação final"
az resource list --resource-group "$RG" --output table

echo ""
echo "✔ Ambiente recriado com sucesso no grupo de recursos: $RG"
