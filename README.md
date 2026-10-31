# feedback-system

## Avaliações no Azure Cosmos DB

O projeto é uma Azure Function Java 21 construída com Maven. `AvaliacaoRepository`
persiste o record de domínio `Avaliacao` no container definido por ambiente, enquanto
`CosmosClientProvider` mantém um único `CosmosClient` compartilhado no processo. A
autenticação usa `DefaultAzureCredential`: `az login` no desenvolvimento local e
Managed Identity quando hospedado no Azure. Não use chaves de conta nem connection strings.

### API para registrar avaliação

`EnviarAvaliacaoFunction` expõe `POST /api/avaliacao` com nível de autorização
`FUNCTION`. Envie um JSON com `descricao` e `nota` (de 0 a 10):

```json
{"descricao":"Aula muito boa","nota":9}
```

Uma solicitação válida retorna `201 Created` com a avaliação persistida; JSON inválido
ou dados fora das regras retornam `400` com `{ "erro": "..." }`. A Function somente
valida e delega a persistência para `AvaliacaoService`. O change feed do Cosmos é o
responsável por acionar alertas, portanto falhas de e-mail não afetam este registro.

Configure, sem versionar valores reais:

```text
COSMOS_ENDPOINT=https://<conta>.documents.azure.com:443/
COSMOS_DATABASE=<nome-do-banco>
COSMOS_CONTAINER_AVALIACOES=<nome-do-container>
# opcional; padrão 100, máximo 1000
COSMOS_AVALIACOES_PAGE_SIZE=100
```

`dia` é a data de avaliação e deve ser persistida como `yyyy-MM-dd`. A consulta
`buscarDesde(LocalDate)` usa `c.dia >= @dia` parametrizado e devolve uma página; o
token de continuação em `PaginaAvaliacao` deve ser usado com a sobrecarga para ler a
próxima página. Como a comparação é textual, o formato ISO é obrigatório. A gravação
usa `upsertItem` e deixa o SDK extrair a chave de partição do documento conforme a
política já existente no container; portanto, o container precisa ter uma chave de
partição que corresponda a uma propriedade presente no documento.

### Configuração e permissões

Após `az login`, carregue a configuração no shell atual:

```bash
source scripts/azure/cosmos-local-setup.sh --endpoint "https://<conta>.documents.azure.com:443/" --database "<banco>" --container "<container>" --subscription "<assinatura>"
```

Conceda a função de dados de menor privilégio necessária (`Cosmos DB Built-in Data
Contributor`) no escopo do banco. A operação requer uma identidade que possa criar
atribuições de dados do Cosmos DB; isso pode depender de um administrador da assinatura.

```bash
# Desenvolvedor autenticado pelo Azure CLI
scripts/azure/cosmos-assign-data-role.sh --resource-group "<rg>" --account "<conta>" --database "<banco>"

# Managed Identity de uma Function App
scripts/azure/cosmos-assign-data-role.sh --resource-group "<rg>" --account "<conta>" --database "<banco>" --function-app "<function-app>"
```

Os scripts consultam a definição da função, fazem a atribuição no escopo
`.../dbs/<banco>` e listam a atribuição criada. Eles não provisionam ou substituem
recursos. Confirme antes que conta, banco e container já existentes são os corretos.

### Teste de integração e Data Explorer

O teste de integração é intencionalmente desabilitado por padrão e não remove dados.
Ele cria uma avaliação com id `integration-<UUID>`, busca desde a data atual e percorre
as páginas até encontrá-la.

```bash
COSMOS_INTEGRATION_TEST=true mvn -Dtest=AvaliacaoRepositoryIntegrationTest test
```

Para validar manualmente, abra no Azure Portal a conta indicada em `COSMOS_ENDPOINT`,
selecione `COSMOS_DATABASE`, depois `COSMOS_CONTAINER_AVALIACOES`, abra **Data
Explorer** e pesquise pelo id `integration-...` emitido pelo teste. Confirme `id` e
`dia`. A validação só está concluída depois de uma execução bem-sucedida contra a conta
real.

## Envio de e-mails aos administradores

O `EmailService` envia alertas e relatórios para todos os endereços configurados em
`EMAIL_ADMINS`, sem consultar banco de dados. O envio aguarda a conclusão no Azure
Communication Services; falhas de configuração ou do Azure são propagadas ao chamador.

Configure as variáveis de ambiente abaixo:

```text
ACS_CONNECTION_STRING=endpoint=...;accesskey=...
EMAIL_REMETENTE=DoNotReply@seu-dominio.azurecomm.net
EMAIL_ADMINS=admin1@example.com,admin2@example.com
```

`EMAIL_ADMINS` deve conter pelo menos um endereço, separado por vírgulas. Não
versione valores reais dessas variáveis.

## Relatório semanal

`WeeklyReportFunction` é um Timer Trigger que envia o relatório aos endereços de `EMAIL_ADMINS`
toda segunda-feira às 08h de Brasília. O `ReportService` busca as avaliações dos últimos 7 dias
(incluindo hoje, no fuso `America/Sao_Paulo`), percorrendo todas as páginas de
`AvaliacaoRepository.buscarDesde`, e calcula total, média das notas, quantidade por dia e por
urgência, além da lista de descrição, urgência e data de envio.

### Expressão cron e UTC

A expressão é `0 0 11 * * MON` no formato NCRONTAB (`{segundo} {minuto} {hora} {dia} {mês} {dia-da-semana}`).
Funções em planos serverless interpretam o cron em **UTC**, e não no horário local. Brasília está
em UTC-3 e não adota horário de verão desde 2019, então 08:00 em Brasília equivale a 11:00 UTC.
A constante está em `WeeklyReportFunction.CRON_SEGUNDA_08H_BRASILIA`; se o horário mudar, ajuste
a hora para `hora_brasilia + 3`.

### Disparo manual pelo portal

Para validar sem esperar a segunda-feira, abra a Function App no Azure Portal, selecione
`WeeklyReportFunction`, vá em **Code + Test** e use **Test/Run**. O e-mail deve chegar a todos os
endereços em `EMAIL_ADMINS`. A função usa as mesmas variáveis de ambiente de e-mail descritas acima
(`ACS_CONNECTION_STRING`, `EMAIL_REMETENTE`, `EMAIL_ADMINS`) e as de Cosmos DB.

Se não houver avaliações no período, o relatório é enviado mesmo assim, indicando que não há
avaliações..
