#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Uso (execute com source para preservar as variáveis no terminal atual):
  source scripts/azure/cosmos-local-setup.sh --endpoint https://<conta>.documents.azure.com:443/ --database <banco> --container <container> [--subscription <id-ou-nome>]

Os nomes são obrigatórios para evitar selecionar recursos incorretos. O script não grava segredos.
EOF
}

endpoint=""
database=""
container=""
subscription=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --endpoint) endpoint="${2:-}"; shift 2 ;;
    --database) database="${2:-}"; shift 2 ;;
    --container) container="${2:-}"; shift 2 ;;
    --subscription) subscription="${2:-}"; shift 2 ;;
    -h|--help) usage; return 0 2>/dev/null || exit 0 ;;
    *) echo "Argumento desconhecido: $1" >&2; usage; return 2 2>/dev/null || exit 2 ;;
  esac
done

command -v az >/dev/null 2>&1 || { echo "Azure CLI não encontrada. Instale-a antes de continuar." >&2; return 1 2>/dev/null || exit 1; }
if ! az account show >/dev/null 2>&1; then
  echo "Você não está autenticado. Execute 'az login' e rode este script novamente." >&2
  return 1 2>/dev/null || exit 1
fi
if [[ -n "$subscription" ]]; then
  az account set --subscription "$subscription"
fi
echo "Assinatura ativa: $(az account show --query name -o tsv) ($(az account show --query id -o tsv))"

[[ "$endpoint" =~ ^https:// ]] || { echo "--endpoint deve iniciar com https://" >&2; return 2 2>/dev/null || exit 2; }
[[ -n "$database" && -n "$container" ]] || { echo "--database e --container são obrigatórios." >&2; return 2 2>/dev/null || exit 2; }

export COSMOS_ENDPOINT="$endpoint"
export COSMOS_DATABASE="$database"
export COSMOS_CONTAINER_AVALIACOES="$container"

echo "Configuração aplicada ao shell atual (sem credenciais)."
echo "Compile: mvn -DskipTests compile"
echo "Integração: COSMOS_INTEGRATION_TEST=true mvn -Dtest=AvaliacaoRepositoryIntegrationTest test"
