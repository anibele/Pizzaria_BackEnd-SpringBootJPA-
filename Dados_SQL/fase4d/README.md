# Fase 4D — idempotência de adição de itens

**Fase 4D.3: backend implementado para requisições com chave.** Veja [operação, contrato efetivo e testes](IMPLEMENTACAO.md). Sem chave, os clientes continuam no fluxo legado; a integração completa com os tablets depende da Fase 4D.4.

## Histórico da preparação 4D.1

As seções abaixo registram o desenho original e seus testes SQL, não o estado atual da implementação. O SQL foi aplicado na Fase 4D.2, antes da inclusão da entidade JPA. Na 4D.3 `expira_em` permanece NULL inclusive após encerramento, sem limpeza automática; os 30 dias continuam sendo proposta futura.

**Registro histórico da 4D.1:** Não aplicar SQL no banco original nesta etapa. Nenhuma classe de produção passa a depender da tabela. O histórico de D11/D12 e os resultados da Fase 4C estão no [documento anterior](../fase4c/README.md). A ordem dos locks segue a [Fase 4B](../fase4b/README.md).

## Contrato real inspecionado

`PedidoController.adicionarItens`: `PUT /pedidos/{id}/itens`, corpo `List<ItemPedidoRequestDTO>`. Cada item contém somente `produtoId` (Long positivo) e `quantidade` (Integer positivo). A validação atual rejeita lista nula/vazia, item nulo e números incompatíveis. Não há preço, usuário ou role legítimos nesse DTO de entrada.

O sucesso é **HTTP 200 com PedidoResponseDTO**, e não 204. Campos: `id`, `numeroMesa`, `dataHora`, `formaPagamento`, `faturamento`, `status`, `itens`. Cada item de saída contém `id`, `produtoId`, `produtoNome`, `quantidade`, `precoUnitario`, `status`, `dataHoraInclusao`, `tempoPreparoMinutos`, `dataHoraConclusao`. A resposta representa o pedido inteiro naquele instante. O registro de idempotência deve guardar esse resultado, não reconstruí-lo a partir do pedido atual.

Fontes: `PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/controller/PedidoController.java`, `service/PedidoService.java`, DTOs e mappers dos mesmos pacotes. A produção permanece com o comportamento atual durante esta preparação.

## Contrato HTTP recomendado para a implementação futura

Preservar rota, método, entrada e sucesso atuais. Adotar **cabeçalho opcional** `Idempotency-Key`, UUID na forma hexadecimal 8-4-4-4-12 (36 caracteres), insensível a maiúsculas. Validar a forma completa antes de converter para os 16 bytes, sem aceitar abreviações que algum parser tolere. O cliente deve preferir UUID aleatório v4 com `crypto.randomUUID()`. UUID identifica intenção, não autentica.

| Caso | Resultado futuro |
|---|---|
| Sem cabeçalho | Fluxo legado; cada chamada adiciona novamente. Sem garantia de idempotência. |
| Cabeçalho vazio, malformado ou múltiplos valores | 400 com `mensagem`, sem alteração. |
| Nova chave e entrada válida | Adição normal; commit único de negócio + registro; 200 com JSON original. |
| Mesmo escopo, chave e hash | 200 e corpo original retido; nenhuma nova adição. |
| Mesmo escopo/chave, hash diferente | 409 com `mensagem` genérica sobre reutilização incompatível. |
| Nova chave para pedido não ABERTO | Conflito de estado 409 já existente. |
| Usuário sem autenticação/permissão atual | Preservar 401/403; nunca usar a chave para conceder acesso. |
| Recurso realmente inexistente | 404 conforme contrato existente. |
| Lock timeout/deadlock reconhecido | 409 pelo tratamento específico atual; rollback e possibilidade de retry com a mesma chave. |

Dados estruturalmente inválidos continuam 400, mesmo num suposto replay. Respostas de falha não serão registradas como sucesso idempotente. Falhas inesperadas continuam 500; não converter toda exceção de banco em 409. Nenhum retry automático no backend.

Cabeçalho obrigatório protege todos os clientes, mas quebraria o React/tablets atuais. Recomenda-se adoção opcional nesta implantação e decisão coordenada posterior sobre obrigatoriedade. D20 continuará parcialmente coberto após o backend enquanto houver operações sem chave; a integração só estará concluída quando os fluxos relevantes do React utilizarem o mecanismo.

## Escopo e autorização

Unicidade composta **(usuario_id, pedido_id, chave)**. Método PUT e operação de adição são implícitos na tabela dedicada; nenhuma outra operação poderá reutilizar esse repositório como namespace genérico. Mesma UUID em outro pedido ou outra conta pertence a outro escopo. Por isso, o cliente não deve transferir silenciosamente tentativas entre contas.

`usuario_id` vem do usuário autenticado e persistido, consultado pelo fluxo atual do Spring Security; nunca do corpo/cabeçalho fornecido pelo cliente. Tanto execução inédita quanto replay refazem autenticação e autorização de acesso ao pedido pela associação persistida Usuario → Mesa. Permissão revogada entre execução e retry implica 401/403, sem revelar a resposta anterior. Não há implementação nova de revogação de JWT nesta fase.

Usuários MESA sem vínculo ou de outra mesa não consultam o registro. Perfis administrativos conservam as permissões atuais. A chave não substitui os locks ou a autorização. Dados sensíveis de outra conta não devem aparecer na mensagem de conflito.

## Hash canônico v1

Validar primeiro a estrutura pura da lista e seus inteiros, sem depender do estoque, disponibilidade atual ou nome do produto. Construir UTF-8 sem BOM, quebras LF e sem quebra final:

```text
v1
PUT
/pedidos/15/itens
{"pedidoId":15,"itens":[{"produtoId":3,"quantidade":2}]}
```

Calcular SHA-256 desses bytes e armazenar os 32 bytes. Vetor de teste do exemplo: `97bff4bddf9cd9dbf001611c74af4ba0af5ea1debbdc6d364fc78630493f47d9`.

Ordem fixa de propriedades, números inteiros decimais sem zeros adicionais, sem espaços opcionais. Ignorar a ordem das propriedades/whitespace do JSON recebido ao construir a representação a partir dos DTOs. Manter **ordem e repetições da lista**: duas linhas iguais não equivalem a uma linha agregada, e inverter itens produz outro hash. Isso preserva a identidade das linhas que aparecem no resultado atual, sem alterar o contrato de entrada nem o cálculo de estoque da Fase 4B. Os locks de produtos continuam sendo adquiridos por ID crescente, independentemente dessa ordem canônica.

Incluir todos os campos semanticamente relevantes existentes: hoje somente pedido, produtos e quantidades. Não usar `toString()` JPA, preço enviado pelo cliente, JWT, senha ou segredo. O usuário integra o escopo, não precisa integrar o hash. Propriedades desconhecidas continuam sujeitas à política Jackson atual; não se tornam entradas de negócio apenas por constarem do JSON bruto.

A versão `v1` deve permanecer estável durante a retenção. Mudança futura dos campos relevantes exige plano de versionamento/canonicalização, sem invalidar arbitrariamente retries antigos; não implantar nova serialização canônica incompatível sem essa análise.

## Persistência proposta

DDL único em [proposta_idempotencia.sql](../fase4c/proposta_idempotencia.sql), compatível com MySQL 8.0.42. Antes de aplicar, confirmar IDs BIGINT assinados, tabelas InnoDB, ausência da tabela e dos nomes de constraints. O script não é idempotente por si só: não reaplicar sobre tabela existente sem inspeção.

| Coluna | Finalidade |
|---|---|
| `id BIGINT AUTO_INCREMENT` | PK técnica. |
| `usuario_id`, `pedido_id BIGINT NOT NULL` | Escopo e duas FKs restritivas, sem cascatas. |
| `chave BINARY(16) NOT NULL` | UUID na ordem dos bytes padrão, sem rearranjo UUID_TO_BIN com swap. |
| `payload_hash BINARY(32) NOT NULL` | SHA-256 canônico. |
| `resposta_json LONGTEXT ... NOT NULL` | Texto exato do corpo original; UTF8MB4 e collation binária. |
| `status_http SMALLINT NOT NULL` | 200, único sucesso atual, protegido por CHECK. |
| `criado_em DATETIME(6) NOT NULL` | UTC; conclusão lógica/registro do resultado antes do commit, não instante físico de commit. |
| `expira_em DATETIME(6) NULL` | Sem prazo durante pedido ativo; prazo definido após encerramento. |

UNIQUE `(usuario_id,pedido_id,chave)`; índice de expiração; índices necessários às FKs criados pelo InnoDB, incluindo acesso por pedido. CHECKs exigem status 200, objeto JSON válido e prazo nulo ou posterior à criação. O CHECK não valida todas as propriedades do DTO; a futura aplicação continua responsável pela resposta correta. FKs preservam referências e impedem excluir usuário/pedido referenciado enquanto houver registros; eventual exclusão desses recursos exige política própria, sem cascata silenciosa.

A escolha de LONGTEXT é intencional. JSON nativo normaliza o documento e pode converter números fracionários grandes para representação aproximada. O teste isolado reproduziu perda de precisão no valor `12345678901234567890.12`. Guardar o texto serializado evita essa transformação. Referência: [documentação MySQL JSON](https://dev.mysql.com/doc/refman/8.0/en/json.html).

A futura implementação deve serializar o DTO uma única vez, antes do commit, guardar esse texto e entregar o mesmo texto como `application/json` tanto na primeira resposta quanto no replay, sem dupla codificação como string JSON. Isso mantém campos/valores do contrato atual e garante identidade do corpo armazenado, sem depender de configurações futuras do ObjectMapper. Headers transitórios (Date, Connection, CORS, autenticação) não são snapshots. Não guardar JWT ou senha.

Não há PROCESSANDO/CONCLUIDO: a linha só é inserida após a operação e a serialização terem sido bem-sucedidas, dentro da transação que ainda não fez commit. Visibilidade após commit indica conclusão. `criado_em` já registra esse momento lógico; uma segunda data não teria significado distinto neste desenho. Erro ao serializar/inserir deve desfazer também os itens/estoque/total.

Não há nova entidade JPA nesta entrega. Uma tabela adicional ainda não mapeada não cria dependência de validação Hibernate; `ddl-auto=validate` permanece. A futura entidade deverá mapear explicitamente BINARY(16), BINARY(32), LONGTEXT UTF8MB4 e DATETIME(6), e ser validada em MySQL antes da implantação.

## Transação e ordem dos locks

Fluxo futuro, em uma única transação READ COMMITTED, sem REQUIRES_NEW:

1. Autenticar; validar ID, formato da chave e estrutura pura do corpo; resolver pedido e autorizar o usuário atual.
2. Adquirir **Mesa → Pedido** e reler o estado conforme a Fase 4B. Confirmar autorização sobre o recurso relido antes de consultar o registro.
3. Consultar a chave pelo escopo completo, com SELECT atual sob READ COMMITTED. A tabela dedicada não deve ser bloqueada/reservada antes de Mesa/Pedido.
4. Registro encontrado: comparar hash e retornar snapshot, ou 409 se diferente. Não consultar novamente disponibilidade/estoque dos produtos nem exigir pedido ainda ABERTO para um replay confirmado. Pedido AGUARDANDO_PAGAMENTO, FINALIZADO ou CANCELADO ainda admite replay retido autorizado.
5. Registro ausente: exigir pedido ABERTO; executar validações atuais de produto/estoque sob os locks de **Produtos por ID crescente**. Aplicar itens, estoque e total conforme o fluxo existente, preservando preços históricos.
6. Serializar o resultado e inserir o registro idempotente ao final, **após os locks de negócio**, antes do commit conjunto. Só enviar sucesso após a transação efetivamente confirmar.

Ponto de atenção real: `PedidoService.validarItens` hoje inclui busca dinâmica de produto antes do lock de Pedido. A futura implementação precisará separar validação estrutural de consultas dinâmicas; apenas colocar uma consulta de chave depois desse método bloquearia indevidamente retries quando o produto tivesse sido desativado/excluído ou o estoque mudado. Essa separação não foi implementada nesta etapa.

Reenvios do mesmo pedido competem pelo mesmo lock de Mesa/Pedido. O segundo aguarda o commit; em READ COMMITTED, sua consulta vê o registro confirmado e não executa outra adição. Se o primeiro fizer rollback, não há registro nem alteração e o segundo poderá executar normalmente. Chaves diferentes no mesmo pedido também serializam suas alterações e continuam sendo novas operações.

Adição/replay versus fechamento ou pagamento usa a mesma ordem; um replay existente recupera o resultado antigo, uma operação nova respeita o estado atual após obter locks. Inserção idempotente não deve anteceder os pais, pois UNIQUE e FKs também adquirem locks no InnoDB. Qualquer futura limpeza deve seguir Mesa → Pedido → registros idempotentes; não criar um caminho inverso idempotência → pedido.

UNIQUE é defesa adicional entre processos, não substitui a serialização do agregado. Não capturar duplicidade e continuar a mesma transação JPA possivelmente marcada rollback-only. Uma violação inesperada é rollback e investigação; o retry deve ocorrer como nova chamada com a mesma chave. Não adicionar retries automáticos. Timeout/deadlock conhecido recebe o 409 genérico já existente; resultado ambíguo por falha de rede exige repetir a mesma chave, nunca gerar outra automaticamente.

Falha antes do commit, inclusive após itens e antes da chave: rollback integral. Falha de rede depois do commit: retry encontra o resultado original. Reinício/duas instâncias: persistência e locks do banco continuam coordenando; ainda precisam de testes da implementação real na 4D.3.

## Retenção recomendada

**Enquanto ABERTO ou AGUARDANDO_PAGAMENTO: manter registro e snapshot integral sem expiração.** Nunca purgar com base apenas em `criado_em`. Após FINALIZADO/CANCELADO: conservar por pelo menos **30 dias**, prazo futuramente configurável e positivo. Não há limpeza automática nesta entrega.

O modelo atual só tem `Pedido.dataHora` de criação, não timestamp confiável de encerramento. Não usá-lo como data final. Na implementação futura, o encerramento pode definir `expira_em = agora UTC + prazo` na mesma transação, sob locks existentes. Para terminais sem prazo, um procedimento futuro pode observar o encerramento pela primeira vez sob Mesa/Pedido e iniciar o prazo a partir dessa observação. Isso preserva por pelo menos o período proposto após o encerramento real, embora possa conservar por mais tempo. Prazo ausente significa reter, não apagar.

Uma limpeza futura em lotes pequenos poderá remover a linha inteira **somente após confirmar sob os locks dos pais que o pedido segue terminal e que o prazo venceu**. Não remover registros de pedidos ativos nem não confirmados. Não há reabertura de pedido terminal na API atual: após a remoção, um retry perde o replay original, mas será rejeitado como nova operação em pedido encerrado (409), sem baixa de estoque. Durante a retenção, continua devolvendo 200 original. Isso deve ser comunicado ao cliente.

Essa política depende de nunca reabrir o mesmo pedido encerrado. Qualquer futura funcionalidade de reabertura exigirá rever retenção/namespace ou conservar tombstones; não a introduzir silenciosamente. Não excluir linhas antecipadamente em pedidos ainda editáveis. Transações da limpeza precisam da mesma ordem de locks para não competir incorretamente com operações em andamento.

Crescimento: uma linha por intenção e snapshot de todo o pedido por adição. Pedidos com muitas adições podem produzir volume de snapshots aproximadamente quadrático em quantidade de linhas de itens; monitorar tamanho e duração dos pedidos antes de definir lotes/rotinas. Não inventar limite de payload ou apagar registros ativos para reduzir volume. Retenção/limpeza exigem implementação e testes próprios posteriores.

## Desenho mínimo da futura implementação

Após migração aprovada/aplicada: uma entidade `IdempotenciaAdicao` e um Repository específico com busca pelo escopo, nas estruturas atuais. Controller recebe cabeçalho opcional e delega; PedidoService incorpora a busca/replay e gravação na transação existente. Um pequeno método/helper determinístico constrói o hash; não há camada genérica ou interface de Service nova. O retorno interno precisará permitir enviar o texto serializado preservado, mantendo o contrato HTTP de sucesso. ApiExceptionHandler e autorização existentes continuam sendo utilizados.

Esse desenho não é código implementado. Nenhuma classe mapeada para a tabela será adicionada antes de sua existência estar comprovada no banco de implantação.

## Integração futura com React e CORS

Gerar UUID uma vez por intenção (`crypto.randomUUID()`), armazenar junto ao payload exato e pedido enquanto a tentativa estiver pendente e enviar `Idempotency-Key` em todos os retries dessa operação. Nova intenção exige nova chave, mesmo com conteúdo igual. Timeout/rede/409 transitório: mesma chave e conteúdo. 409 por hash incompatível: parar e apresentar conflito, sem gerar automaticamente outra chave e cobrar novamente. Conflito real de estoque/estado exige revisão da intenção pelo usuário.

JWT expirado: renovar sessão por login e só retentar com a mesma conta/pedido/chave; não transferir operação para outra conta. A chave não é credencial e não substitui Bearer. Não registrar JWT/payload sensível em logs. Se a interface precisa sobreviver a reload/offline, persistir apenas a intenção mínima segundo a política do frontend, sem guardar tokens junto à chave; testar recuperação e não gerar nova intenção ao remontar componentes.

Um replay pode conter estado mais antigo que a tela atual, pois devolve o snapshot da operação original. Não sobrescrever cegamente o estado mais novo: confirmar a conclusão da intenção e reconciliar a tela com GET separado autorizado. Essa consulta não altera o resultado idempotente original.

CORS atual permite somente `Authorization` e `Content-Type`: será necessário incluir explicitamente `Idempotency-Key` nos headers permitidos na etapa de backend; sem isso o preflight do navegador falhará. Nenhuma alteração de CORS/React foi feita agora. Não é necessário expor header novo de resposta neste contrato.

## Testes executados na preparação

Executor [scripts/testar_idempotencia_mysql.py](../../scripts/testar_idempotencia_mysql.py): Python stdlib/cliente nativo, Windows, `--mysql-bin` obrigatório. Cria MySQL 8.0.42 novo com porta não 3306, UUID/datadir próprios e dados sintéticos; valida identidade antes de DDL e em cada conexão de teste. Não lê datasource nem acessa o original. Limites de execução, barriers e observação de `performance_schema.data_lock_waits` comprovam sobreposição; o teste não depende apenas de sleeps aleatórios. Encerra somente seu próprio servidor. Diretórios temporários contêm apenas dados sintéticos.

```powershell
python scripts/testar_idempotencia_mysql.py --mysql-bin 'C:/Program Files/MySQL/MySQL Server 8.0/bin'
```

Resultado: **12 grupos aprovados**, MySQL 8.0.42, READ COMMITTED, porta 53463, instância encerrada com código 0:

1. DDL, PK, tipos, índices, UNIQUE, duas FKs, três CHECKs e pais preservados.
2. Roundtrip exato UTF-8/emoji/valor monetário grande; comparação demonstra alteração numérica com JSON nativo.
3. Duplicidade rejeitada (1062), escopos distintos aceitos, FKs inválidas (1452), nulidade, JSON não objeto/malformado, status/prazo inválidos rejeitados.
4. Vetores de canonicalização e UUID, incluindo propriedades reordenadas, repetição e ordem de itens.
5. Mesma chave: segundo INSERT aguarda commit e recebe duplicidade; uma linha.
6. Mesma chave: segundo aguarda rollback e então insere; uma linha.
7. Chaves diferentes: duas transações sobrepostas confirmam duas linhas.
8. Rollback do registro junto com alterações sintéticas de estoque/total; falha no registro não deixa efeitos.
9. Timeout 1205 real e rollback explícito sem registro parcial.
10. Deadlock 1213 proposital em ordem inversa de chaves, fora do desenho recomendado, com uma vítima revertida. Demonstra o risco de reservar chaves antes dos pais; não é um deadlock observado no fluxo proposto.
11. Modelo SQL Mesa → Pedido → consulta/registro: duas transações aguardam o pai; uma executa e outra encontra resultado, um único efeito no estoque.
12. Modelo de retenção: ativo sem expiração não é elegível, terminal pode expirar; rollback restaura dados. Nenhum mecanismo de limpeza de produção implementado.

A regressão também foi executada com `scripts/testar_mysql4b.py` e Maven offline: **527 testes Java aprovados, zero falhas/erros/ignorados**, incluindo 71 cenários MySQL, além dos testes unitários, web e JPA/H2 existentes. MySQL 8.0.42 na porta 53489, UUID/datadir independentes, encerrado com código 0; `BUILD SUCCESS`. Nenhum teste Java foi modificado. Os 12 grupos SQL novos são contabilizados separadamente dos 527 testes Java.

Os pais sintéticos retornaram exatamente ao snapshot inicial ao final. A primeira execução do CHECK revisado revelou erro 3812 (CASE não reconhecido como expressão booleana de CHECK); a comparação explícita `= TRUE` corrigiu a proposta e a execução final passou. Não houve tentativa de ajuste no banco original.

Esses são testes SQL/fixtures, não testes de idempotência HTTP, JPA da nova entidade, restart ou duas instâncias do backend. A Fase 4D.3 deverá implementar e testar esses cenários, inclusive perda de resposta após commit, autorização revogada, replay após fechamento/pagamento, serialização original e falha transacional. Não declarar D20 corrigido com base neste executor.

## Implantação em etapas

1. **4D.1 (esta entrega):** proposta e contrato revisados, testes sintéticos, sem alteração de produção/schema original.
2. **4D.2 (autorização específica posterior):** confirmar Git/DDL/identidade, aplicação parada, tabelas/IDs compatíveis e ausência da tabela; backup completo atualizado e restaurado em instância independente; ensaiar DDL sobre cópia, comparar fingerprints de todas as tabelas antigas; então aplicar somente SQL aprovado e verificar metadados/constraints sem registros inválidos no original. Preservar backup e considerar commit implícito de CREATE TABLE. Não executar agora.
3. **4D.3:** após tabela confirmada, implementar persistência/replay transacionais e CORS, manter cliente sem chave, executar regressão e testes MySQL/HTTP concorrentes de mesma/diferentes chaves, rollback, falha pós-commit, reinício e múltiplas instâncias; validar Hibernate com o novo mapeamento. Commit independente. Não implantar código dependente antes da migração.
4. **4D.4:** React usa intenção/chave estável e testes ponta a ponta, incluindo preflight, login expirado e reenvio após perda de resposta. Só então avaliar cobertura completa de D20 e eventual obrigatoriedade do header mediante aprovação coordenada.

A migração estrutural sozinha não corrige D20, e a tabela será inicialmente vazia. Nenhum backfill por comparação de payloads históricos é seguro ou proposto. Aprovação futura deve abranger o SQL revisado, contrato opcional, política de retenção, janela de manutenção e backup restaurável. Esta documentação não solicita nem executa essa autorização.
