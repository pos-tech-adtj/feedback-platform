#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Uso:
  scripts/azure/cosmos-assign-data-role.sh --resource-group <rg> --account <conta> --database <banco> [--principal-id <object-id>] [--function-app <app>] [--subscription <id-ou-nome>]

Sem --principal-id/--function-app, usa o object id do usuário autenticado (cenário local).
Com --function-app, usa a Managed Identity atribuída à Function App (cenário hospedado).
É necessário um papel administrativo que permita criar atribuições de papel de dados do Cosmos DB.
EOF
}

resource_group=""; account=""; database=""; principal_id=""; function_app=""; subscription=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource-group) resource_group="${2:-}"; shift 2 ;;
    --account) account="${2:-}"; shift 2 ;;
    --database) database="${2:-}"; shift 2 ;;
    --principal-id) principal_id="${2:-}"; shift 2 ;;
    --function-app) function_app="${2:-}"; shift 2 ;;
    --subscription) subscription="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Argumento desconhecido: $1" >&2; usage; exit 2 ;;
  esac
done

command -v az >/dev/null 2>&1 || { echo "Azure CLI não encontrada." >&2; exit 1; }
az account show >/dev/null 2>&1 || { echo "Execute az login antes de continuar." >&2; exit 1; }
[[ -n "$subscription" ]] && az account set --subscription "$subscription"
[[ -n "$resource_group" && -n "$account" && -n "$database" ]] || { usage; exit 2; }
[[ -z "$principal_id" || -z "$function_app" ]] || { echo "Informe somente um de --principal-id ou --function-app." >&2; exit 2; }

if [[ -n "$function_app" ]]; then
  principal_id="$(az functionapp identity show --resource-group "$resource_group" --name "$function_app" --query principalId -o tsv)"
elif [[ -z "$principal_id" ]]; then
  principal_id="$(az ad signed-in-user show --query id -o tsv)"
fi
[[ -n "$principal_id" ]] || { echo "Não foi possível determinar o principal id." >&2; exit 1; }

subscription_id="$(az account show --query id -o tsv)"
role_id="$(az cosmosdb sql role definition list --resource-group "$resource_group" --account-name "$account" --query "[?roleName=='Cosmos DB Built-in Data Contributor'].id | [0]" -o tsv)"
[[ -n "$role_id" ]] || { echo "A função Cosmos DB Built-in Data Contributor não foi localizada." >&2; exit 1; }
scope="/subscriptions/$subscription_id/resourceGroups/$resource_group/providers/Microsoft.DocumentDB/databaseAccounts/$account/dbs/$database"

echo "Criando/confirmando atribuição no escopo do banco: $scope"
az cosmosdb sql role assignment create --resource-group "$resource_group" --account-name "$account" --scope "$scope" --principal-id "$principal_id" --role-definition-id "$role_id"
az cosmosdb sql role assignment list --resource-group "$resource_group" --account-name "$account" --scope "$scope" --query "[?principalId=='$principal_id']" -o table
