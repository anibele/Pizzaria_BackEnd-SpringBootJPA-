# Fase 4D.3 — idempotência no backend

O SQL da Fase 4D.1 já foi aplicado na 4D.2. Esta implementação mapeia a tabela existente e não acrescenta migrations. `ddl-auto=validate` permanece. **Não executar testes nem seeds no banco de trabalho.**

## Contrato efetivo

`PUT /pedidos/{id}/itens` mantém o array de `ItemPedidoRequestDTO` e HTTP 200 com o mesmo formato de `PedidoResponseDTO`. O cabeçalho opcional `Idempotency-Key` deve conter um único UUID completo 8-4-4-4-12, sem espaços; maiúsculas/minúsculas representam a mesma chave. Vazio, abreviado, múltiplo ou malformado retorna 400 com `mensagem`.

Sem cabeçalho, o Controller chama o método anterior, sem consultar ou criar registros idempotentes. Repetir uma chamada sem chave continua adicionando novamente. Isso mantém os clientes antigos, mas significa que D20 não está integralmente corrigido nos tablets até a integração React.

| Caso | Resultado |
|---|---|
| Chave inédita, operação válida | 200 com JSON original e gravação conjunta da operação |
| Mesmo usuário/pedido/chave e conteúdo | 200 com texto JSON original; nenhum efeito adicional |
| Mesmo escopo/chave, conteúdo diferente | 409 com `mensagem` |
| Estrutura da entrada/chave inválida | 400 com `mensagem` |
| Outra mesa/sem permissão atual | 403, mesmo que a chave exista |
| Bearer inválido | 401 pelo filtro existente |
| Token ausente | 403 pela configuração existente, preservada |
| Chave nova em pedido não ABERTO | 409 pelas regras existentes |
| Timeout/deadlock de lock conhecido | 409; rollback, sem retry automático no backend |

Um replay é autorizado novamente e funciona após AGUARDANDO_PAGAMENTO, FINALIZADO ou CANCELADO enquanto o registro existir. Não consulta estoque/atividade/preço atual do produto, não recalcula valores e não altera timestamps. Uma chave nova continua sujeita às regras atuais. A resposta antiga pode ter estado e itens anteriores à tela atual; reconciliar a interface com GET autorizado separado.

## Persistência e serialização

`IdempotenciaAdicao` usa relacionamentos LAZY sem cascatas para Usuario e Pedido. `findByUsuarioIdAndPedidoIdAndChave` corresponde à UNIQUE composta. Usuário vem do principal persistido autenticado pelo Spring Security; não existe campo de identidade confiado ao cliente.

UUID é mapeado com Hibernate `SqlTypes.BINARY`, comprimento 16, ordem binária padrão, sem swap. SHA-256 é `byte[]` BINARY(32), não texto hexadecimal. `resposta_json` é String LONGTEXT sem `@Lob`, `status_http` é SMALLINT 200, datas DATETIME(6). O mapeamento deve ser validado contra o DDL aprovado, sem substituir o schema pela geração Hibernate.

Na primeira execução com chave, o mesmo JsonMapper do Spring Boot serializa o DTO dentro da transação, e esse texto é armazenado. O Controller envia a String como corpo `application/json` pelo conversor de texto, sem nova codificação JSON. Replay devolve o texto guardado, incluindo BigDecimal sem conversão para double. Não se reconstrói o DTO consultando o pedido atual. O CHECK existente só admite sucesso 200; por isso não há outros status de replay neste endpoint.

`criado_em` usa UTC na conclusão lógica antes do commit. `expira_em` é sempre NULL nesta etapa, inclusive após fechamento/pagamento. Nenhuma rotina de expiração ou exclusão foi implementada. A proposta de pelo menos 30 dias após encerramento será tratada em etapa operacional própria; não usar a data de criação do pedido como data final e não apagar registros de pedidos ativos.

## Hash canônico

`OperacaoAdicao` recebe DTOs estruturalmente validados. Formato UTF-8 sem BOM, LF e sem quebra final:

```text
v1
PUT
/pedidos/15/itens
{"pedidoId":15,"itens":[{"produtoId":3,"quantidade":2}]}
```

SHA-256: `97bff4bddf9cd9dbf001611c74af4ba0af5ea1debbdc6d364fc78630493f47d9`.

Propriedades são emitidas em ordem fixa, inteiros em decimal, sem espaços opcionais. Ordem das propriedades JSON e whitespace da requisição não importam. Ordem e repetições da lista importam: linhas repetidas não são agregadas para o hash. Pedido, produto e quantidade participam; preço, estado, estoque, JWT e senha não participam. Manter v1 estável em evoluções futuras.

## Transação e concorrência

O caminho com chave usa uma transação Spring READ COMMITTED. Validação estrutural foi separada da busca dinâmica de produtos, preservando o comportamento anterior no caminho sem chave. Os dois caminhos reutilizam `incluirItens`, sem segunda implementação de estoque/total.

1. Validar estrutura e usuário autenticado, localizar pedido e conferir acesso.
2. Adquirir **Mesa → Pedido** com releitura existente; repetir autorização sobre o pedido relido.
3. Consultar registro pelo escopo completo, antes de exigir pedido ABERTO e antes de consultar produtos.
4. Registro existente: comparar hash e devolver snapshot ou 409.
5. Registro ausente: exigir ABERTO; consultar/bloquear produtos por ID crescente e executar a adição existente.
6. Serializar DTO, salvar/flush do registro e confirmar toda a transação antes de o Controller enviar sucesso.

A inserção ocorre depois dos locks de negócio, nunca antes dos pais. Duas requisições da mesma mesa aguardam o lock compartilhado no banco; após commit, a segunda vê o registro em READ COMMITTED. Se a primeira reverte, a segunda não encontra operação concluída. A UNIQUE é defesa adicional; não há captura de duplicidade seguida de novas consultas numa transação inválida. Nenhum REQUIRES_NEW, cache de chaves, lock Java ou retry automático foi introduzido.

Erro na serialização, inserção ou commit reverte itens, total, estoque e registro. Perda da resposta após commit permite recuperar o texto com a mesma chave. Fechamento/pagamento continuam seguindo os locks e regras anteriores; replay não reabre pedidos.

## React e CORS

Gerar `crypto.randomUUID()` **uma vez por intenção**, conservar pedido, payload e chave durante tentativas e enviar o header junto ao Bearer. Timeout ou perda da resposta exige reenviar com a mesma chave/conteúdo. Uma nova adição intencional, mesmo com os mesmos produtos, exige outra chave. Não trocar automaticamente a chave após erro desconhecido ou 409 de conteúdo incompatível.

CORS permite `Idempotency-Key` nas origens já autorizadas, mantendo os demais métodos/headers e regras. A chave não é credencial nem substitui JWT. Após sessão expirada, realizar login e retentar com a mesma conta; outra conta tem outro escopo e não representa automaticamente o retry anterior.

O frontend não foi modificado. Retenção de intenção após reload/offline e tratamento visual de conflitos pertencem à Fase 4D.4. Não aplicar automaticamente o snapshot antigo sobre estado mais recente da tela.

## Testes isolados

Executar com `scripts/testar_mysql4b.py` conforme a documentação da Fase 4B. Ele cria MySQL 8.0.42 independente, com UUID/datadir próprios, porta não 3306 e schema `fase4b_sintetica`. O guard do DataSource confere novamente a identidade antes de qualquer DDL. Os testes não utilizam o datasource de produção.

`IdempotenciaHttpMysqlTest` inicia servidores Spring Boot restritos a 127.0.0.1 com porta aleatória, dados sintéticos, seeds ausentes e segredo apenas de teste em memória. O bootstrap de teste usa geração de schema somente no MySQL descartável, substitui a tabela vazia pelo DDL aprovado e reinicia com `validate`; as chamadas HTTP efetivas usam esse schema validado. Nenhum desses parâmetros altera as configurações versionadas de produção.

Há testes de HTTP real, assinatura/ausência de token, CORS, chave e payload, snapshot exato, BigDecimal, conflitos de estado, autorização atual, rollback forçado antes do commit e recuperação da resposta previamente confirmada. A perda de resposta é simulada descartando o resultado da primeira chamada após seu commit, sem injetar falha no transporte TCP.

Concorrência usa conexões independentes, latch inicial e um lock de mesa retido pelo teste; `performance_schema.data_lock_waits` comprova que ambas as chamadas HTTP estão aguardando simultaneamente, com timeouts limitados. A inspeção dos SQLs verifica Mesa/Pedido/Produto, e a regressão preserva os testes de ordem de produtos da Fase 4B. Um teste fecha o contexto e reinicia outro com `validate`, preservando o registro. Outro usa dois contextos Spring Boot simultâneos, portas HTTP e EntityManagerFactories independentes no mesmo MySQL descartável. Esses dois contextos rodam na mesma JVM de testes; não houve ensaio com dois processos de sistema operacional separados.

Os testes anteriores só recebem a dependência JsonMapper nos contextos montados manualmente e adaptação do teste SQL da proposta, agora que a tabela também é entidade mapeada. Nenhum cenário anterior foi removido. Resultado final: **567 testes Java aprovados, zero falhas/erros/ignorados**, preservando os 527 anteriores e acrescentando 40: 10 de canonicalização/chaves e 30 de HTTP real/MySQL. Classificação da suíte: 134 unitários/configuração, 244 web isolados, 88 JPA/contexto H2 e 101 MySQL (71 anteriores + 30 novos). Compilação e `git diff --check` aprovados.

Execução final em MySQL 8.0.42, porta 64139, UUID `167151bf-e2cc-11f1-a891-9829a6e19634`, datadir temporário exclusivo. Executor e MySQL terminaram com código 0. Os 30 testes incluem replay em dois contextos Spring, reinício, Unicode, precisão monetária grande, origem CORS negada, timeout 409 e rollback 500 forçado por trigger apenas no banco descartável. Resposta descartada após commit simula perda de resposta; não foi introduzido proxy de falhas TCP. A primeira execução teve uma expectativa incorreta de 401 para token ausente; ela foi ajustada ao 403 vigente, sem modificar autenticação. Token inválido continua 401.

Nenhuma conexão ou escrita no MySQL original foi necessária nesta execução; nenhum script de migração foi executado contra ele. Não se iniciou o backend de trabalho.

## Pendências

Integração React, limpeza/retenção operacional, dimensionamento dos snapshots e eventual obrigatoriedade futura do header exigem etapas próprias. Respostas completas por adição podem crescer muito em contas longas; não apagar chaves ativas para reduzir volume. Não foram implementados indicadores financeiros, revogação adicional de JWT ou outras recomendações da auditoria.
