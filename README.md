# feedback-system

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
