# Chat API - Android App

Cliente nativo Android estilo ChatGPT em **Kotlin**, **Jetpack Compose** e **Material 3** para comunicação com a API FastAPI.

## Configuração do Token de Desenvolvimento

Para que o aplicativo se autentique com sucesso na API, adicione a seguinte linha no arquivo `local.properties` na raiz do projeto:

```properties
CHAT_API_TOKEN=COLE_SEU_TOKEN_AQUI
```

Substitua `COLE_SEU_TOKEN_AQUI` pelo seu token de autenticação real.

### Notas de Segurança:
- O arquivo `local.properties` está configurado no `.gitignore` e não deve ser versionado.
- Caso o token não seja fornecido ou mantenha o valor padrão de exemplo, a tela principal exibirá um aviso explicativo e permitirá também inserir/atualizar o token diretamente na interface do app.
