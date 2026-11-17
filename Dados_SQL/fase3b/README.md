# Fase 3B — operação e pendência de revogação

## JWT obrigatório

`API_SECURITY_TOKEN_SECRET` deve conter pelo menos 32 bytes gerados por um CSPRNG, codificados em Base64. O código verifica a codificação, o comprimento e rejeita valores de pouca variedade; isso não comprova a entropia de uma chave escolhida manualmente. Não utilize frases, senhas ou exemplos como chave. HMAC-SHA256 e o issuer `pizzaria-maua-api` foram preservados.

No PowerShell, gere uma chave sem imprimi-la nem gravá-la no repositório:

```powershell
$chaveJwt = New-Object byte[] 32
$geradorJwt = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$geradorJwt.GetBytes($chaveJwt)
$env:API_SECURITY_TOKEN_SECRET = [Convert]::ToBase64String($chaveJwt)
$geradorJwt.Dispose()
[Array]::Clear($chaveJwt, 0, $chaveJwt.Length)
Remove-Variable chaveJwt, geradorJwt
```

A variável vale para o terminal e seus processos filhos. Guarde a chave gerada em um gerenciador de segredos e reutilize-a nas próximas inicializações; **não execute a geração a cada reinício**. A aplicação não gera chaves automaticamente. Para configurar a IDE, informe `API_SECURITY_TOKEN_SECRET` nas variáveis de ambiente da configuração de execução, sem compartilhar/versionar essa configuração. Uma IDE já aberta não herda variáveis de um terminal iniciado depois.

Ausência, Base64 inválido, chave curta ou trivial impedem a criação do `TokenService`, com mensagem sem revelar a chave. A chave antiga não tem fallback de compatibilidade. Todos os clientes devem realizar novo login após a troca da chave. Rotação invalida os tokens de todos os usuários.

| Perfil | Padrão | Propriedade | Variável opcional |
|---|---|---|---|
| GERENTE, COZINHA, CAIXA | 8 horas | `api.security.token.validade-equipe` | `API_SECURITY_TOKEN_VALIDADE_EQUIPE` |
| MESA | 7 dias | `api.security.token.validade-mesa` | `API_SECURITY_TOKEN_VALIDADE_MESA` |

Use durações ISO-8601 positivas, em segundos inteiros, por exemplo `PT8H` e `P7D`. A emissão usa o perfil do usuário autenticado; permissões continuam sendo obtidas do usuário atual no banco. São exigidos issuer, subject não vazio, emissão, expiração e claim role textual. A role do JWT não concede permissões. Não há exceção para tokens antigos de dez anos; tokens cuja duração exceda a maior política configurada também são recusados. Reduzir políticas pode invalidar tokens já emitidos acima desse limite.

O login continua em `POST /auth/login`, com os mesmos campos e resposta `{token, username, role}`. Bearer inválido, expirado ou de usuário inexistente recebe **401 sem corpo**; o React deve limpar a sessão e solicitar login. Permissões insuficientes continuam em 403. Não há refresh token.

O login público ignora o Bearer antigo eventualmente anexado pelo cliente, permitindo autenticar novamente com username e senha.

## Inicialização e Hibernate

O padrão é `spring.jpa.hibernate.ddl-auto=validate`: o banco deve estar previamente preparado. Não há criação automática de banco pela URL local. Nenhum perfil é ativado por padrão. Não iniciar contra um banco antigo sem a migração 3A; ela já foi aplicada ao banco de trabalho e não deve ser reaplicada.

Para usar o banco já migrado, configure a chave, mantenha o perfil demo desativado e execute na pasta `PizzariaMauaBackEnd`:

```powershell
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
.\mvnw.cmd spring-boot:run
```

O perfil de implantação é `prod`: exige `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`, fornecidos externamente, e mantém `validate`. Não há fallback para credenciais locais nesse perfil. Ative-o com `SPRING_PROFILES_ACTIVE=prod`. Remova overrides antigos de `ddl-auto=update` da IDE/ambiente. O processo não foi iniciado no banco original nesta fase.

Para uma demonstração **em outro servidor/porta e armazenamento descartáveis**, configure primeiro `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` para esse destino e ative `SPRING_PROFILES_ACTIVE=demo`. O perfil demo herda `validate`; não muda silenciosamente o datasource nem cria schema. Se precisar gerar um schema novo exclusivamente nesse destino descartável, autorize explicitamente `SPRING_JPA_HIBERNATE_DDL_AUTO=update` na primeira preparação e retorne a `validate` depois. Nunca use esse override no banco de trabalho. O perfil `prod` impede os seeds mesmo se combinado com `demo`.

Há um único runner demo transacional. Apenas se usuários, mesas, produtos, pedidos e itens estiverem **todos vazios**, ele cria mesas 1–10, contas originais e produtos, nessa ordem. Usuários MESA recebem a associação antes de persistir. Senhas demonstrativas e BCrypt foram preservados. A conta existente `mesa11` e seu vínculo não são alterados; o catálogo original de seeds continua com dez mesas.

Uma base com qualquer dado é preservada integralmente: não completa cargas parciais nem recria produtos, mesas ou usuários removidos. Nomes duplicados de produtos não são consultados pelo seed. Falha na primeira carga reverte toda a transação. Se todas as tabelas forem deliberadamente esvaziadas, não existe marcador histórico para distinguir isso de uma base nova: ativar `demo` voltará a carregar o conjunto. Não esvazie dados para forçar recuperação.

Desativar seeds não protege as contas demonstrativas já existentes: **não exponha publicamente um banco com essas credenciais de teste**. Esta fase não muda senhas nem provisiona contas de implantação.

## Testes isolados

`mvnw.cmd test` executa testes unitários/web com mocks e integração em H2 em memória. O antigo teste de contexto passou a sobrescrever URL, driver, usuário, senha e DDL para H2, com chave aleatória apenas de teste e perfil `test`. Não retire essas configurações nem execute testes com overrides apontando para MySQL de trabalho. Os testes não dependem de uma chave real nem executam os inicializadores sobre dados reais.

Referências: [configuração de inicialização do Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html) e [verificação JWT com Clock no Auth0](https://github.com/auth0/java-jwt/blob/master/EXAMPLES.md).

Na execução da Fase 3B passaram 375 testes Java (322 anteriores, 52 novos cenários e o teste de contexto anteriormente excluído, agora isolado). Incluem validação de segredo/configuração, expiração com Clock, assinatura/issuer/claims, perfil atual no banco, HTTP 401, login com Bearer antigo, perfis sem seeds, carga/reinício/exclusão/base parcial/histórico e rollback em H2. Nenhum teste foi ignorado. Compilação Java com release 17 e `git diff --check` aprovados. Não houve conexão ao MySQL original nesta fase. Entidades e scripts 3A permanecem inalterados; a compatibilidade com o banco migrado foi avaliada estaticamente e pelos testes JPA isolados, sem repetir validação contra o banco real. A verificação funcional HTTP sobre esse banco continua pendente.

## Revogação individual — proposta, NÃO aplicada

**D10 permanece parcialmente corrigido.** `Usuario` não possui estado persistido de bloqueio nem versão de autenticação. Seus métodos `isEnabled`/`isAccountNonLocked` retornam constantes; nenhuma coluna existente representa revogação. Não se utiliza senha, username, mesa ou role como substituto desse estado. Trocar a senha isoladamente não revoga os JWTs atuais.

Proposta mínima para uma fase posterior: uma coluna de versão de autenticação. O SQL abaixo é apenas uma proposta para revisão; **não executar no banco de trabalho nesta fase**:

```sql
ALTER TABLE usuario
    ADD COLUMN versao_autenticacao BIGINT NOT NULL DEFAULT 0;
```

Os quatorze usuários existentes receberiam zero, sem alterar senhas, nomes, perfis, vínculos ou histórico. Antes da aplicação futura: parar a aplicação, backup atualizado com restauração comprovada, conferir ausência da coluna e ensaiar sobre cópia integral. DDL tem commit implícito; não prometer rollback do ALTER nem remover a coluna automaticamente em recuperação.

Somente depois de aprovada e aplicada essa migração, uma implementação futura deverá mapear a coluna, emitir sua versão em cada JWT e compará-la ao valor atual consultado no banco. Uma operação administrativa autorizada incrementaria a versão atomicamente para invalidar todos os tokens anteriores daquele usuário, mantendo a revogação após reinício. Tokens sem versão precisariam de novo login; um novo login emitiria a versão atual. Isso revoga sessões, mas não bloqueia novos logins de quem ainda conhece a senha: um bloqueio permanente de conta exigiria decisão adicional. Não há endpoint novo, alteração da entidade ou dependência dessa coluna nesta entrega.

A proposta deve ser testada em MySQL descartável com quatorze registros sintéticos, verificando default zero, preservação das colunas anteriores, incremento de uma só versão e persistência após reconexão. Esses testes de schema não significam que a revogação já esteja implementada na API.

### Resultado do teste da proposta

Em MySQL Community 8.0.42 independente, porta 50272 e armazenamento temporario exclusivo, passaram cinco verificacoes com quatorze usuarios sinteticos: ALTER preservando os campos originais, default zero para todos, tipo BIGINT NOT NULL, incremento de apenas uma versao mantendo as outras treze e persistencia apos reconexao. Nenhuma conexao com o MySQL original foi feita nesse teste. A instancia foi encerrada. Isso valida apenas a proposta de schema; a revogacao na API permanece pendente.
