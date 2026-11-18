# Fase 5A.2 — Preparação de D23

Este documento complementa o README da Fase 5A. **Não autoriza execução no banco original.** O SQL de proposta permanece inalterado; não há mapeamento ou gravação novos no Java.

## Modelo e evento de negócio

`Pedido.dataHora` corresponde a `pedido.data_hora`, abertura em LocalDateTime. `status` e `forma_pagamento` guardam respectivamente o enum e uma String. Não há data de solicitação, atualização, finalização ou recebimento no Pedido. Datas de ItemPedido representam inclusão/conclusão de preparo; datas de IdempotenciaAdicao representam adições de itens.

O Controller encaminha `PATCH /pedidos/{id}/confirmar` para `PedidoService.confirmarPagamentoEFecharMesa`. O Service, em transação READ COMMITTED, bloqueia Mesa → Pedido, exige AGUARDANDO_PAGAMENTO e as invariantes de mesa/conta ativa, atribui FINALIZADO e libera a mesa. Essa é a única atribuição de FINALIZADO encontrada no código de produção. Solicitar fechamento apenas atribui AGUARDANDO_PAGAMENTO e a forma escolhida. Não há endpoint administrativo de atribuição livre do status do pedido.

Scripts e testes sintéticos podem atribuir estados diretamente; não são caminhos de recebimento da aplicação. Não foi consultado o servidor original nesta fase: não se afirma que inexistam rotinas externas ou alterações operacionais nele. Verificá-las é uma pré-condição da Fase 5A.3.

A semântica futura é **instante em que o sistema aceitou a confirmação do recebimento**, persistido somente se a transação que finaliza o pedido confirmar. Não é o instante físico do commit do MySQL, nem uma data fornecida pelo cliente.

## SQL preservado

```sql
ALTER TABLE pedido ADD COLUMN data_pagamento_utc DATETIME(6) NULL;
```

- Coluna acrescentada ao final, sem reposicionar outras colunas.
- Nullable, com valor implícito NULL. Não há DEFAULT CURRENT_TIMESTAMP, ON UPDATE, UPDATE ou backfill. O MySQL pode exibir `DEFAULT NULL` no SHOW CREATE TABLE; isso não gera uma data.
- Microssegundos, coerentes com os DATETIME(6) do modelo atual.
- Nenhuma FK, CHECK ou índice novo. Em particular, não exigir timestamp de todos os FINALIZADOS: isso invalidaria a preservação histórica.
- Nenhuma coluna, FK, índice, constraint ou valor anterior deve mudar.
- A execução não é idempotente: uma segunda tentativa produz erro 1060. Conferir a ausência da coluna antes de executar; se já existir, interromper, registrar e inspecionar a definição. Não usar SQL dinâmico ou IF NOT EXISTS para ocultar divergências.

### Algoritmo, locks e falhas

O SQL não força algoritmo. No InnoDB 8.0.42, ADD COLUMN pode usar INSTANT, mas a elegibilidade depende da tabela real e dos limites internos. Não prometer operação sem espera: metadata locks e transações longas podem bloquear DDL. A documentação de referência descreve [operações online](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl-operations.html) e [concorrência do DDL](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl-performance.html).

No ensaio, medir o tempo e conferir todas as estruturas. Na aplicação futura, usar janela sem escritores, inspecionar transações e esperas de metadata locks, definir timeout de sessão aprovado e interromper ao primeiro erro. Não alterar o SQL para forçar reconstrução ou acrescentar opções sem novo ensaio/revisão. Não usar cliente que continue após erros. DDL pode realizar commit implícito; ROLLBACK não é um plano de recuperação.

## Histórico e relatórios

Todos os pedidos anteriores, inclusive FINALIZADOS, devem permanecer NULL. Mesmo a existência de uma forma de pagamento não comprova quando o dinheiro foi recebido. Não copiar `data_hora`, datas dos itens, instante da migração ou data atual.

Faturamento histórico total poderá continuar incluindo FINALIZADOS segundo D16. Relatórios por recebimento deverão exigir a nova data conhecida e usar essa data no período. Valores FINALIZADOS sem data devem ser identificáveis separadamente como **período de recebimento desconhecido**. Não somá-los a um dia fictício, nem excluí-los silenciosamente de um total histórico apresentado como completo.

As consultas atuais permanecem por abertura até a Fase 5A.4. A simples aplicação do ALTER não corrige o dashboard nem registra pagamentos novos.

## Convenção temporal para a Fase 5A.4

O projeto utiliza LocalDateTime; a configuração versionada não define uma convenção global explícita de timezone JDBC/Hibernate. A abertura e os timestamps de preparo usam `LocalDateTime.now()`. A idempotência já usa `LocalDateTime.now(ZoneOffset.UTC)`. O dashboard financeiro escolhe o dia em America/Sao_Paulo, mas compara os valores civis de abertura. Não reinterpretar esses campos históricos.

Para a coluna **nova**, estabelecer UTC como convenção de aplicação. DATETIME não contém offset: o sufixo `_utc` documenta o acordo, não uma conversão feita pelo MySQL. Na implementação futura:

1. Obter o instante de Clock, converter explicitamente para LocalDateTime em UTC e adequar a precisão a microssegundos. Mapear somente após a migração estar comprovada.
2. Atribuir uma única vez após as validações da confirmação, dentro da mesma transação e dos locks existentes. Uma rejeição/rollback não persiste timestamp; repetição inválida não o sobrescreve. Operações pendentes não recebem data.
3. Para um dia de negócio, converter `data.atStartOfDay(America/Sao_Paulo)` e o início do dia seguinte para instantes; representar ambos em UTC para comparar com `data_pagamento_utc >= início AND data_pagamento_utc < fim`.
4. Usar mapeamento JDBC coerente de LocalDateTime/DATETIME e testar com JVM/sessão em zonas diferentes. Não modificar globalmente o tratamento dos campos antigos como atalho.

Exemplo em novembro de 2026: pagamento às 00h20 do dia 11 em São Paulo é armazenado como 03h20 UTC do dia 11; entra no intervalo `[11/11 03h00 UTC, 12/11 03h00 UTC)`. A abertura às 23h40 do dia 10 permanece intacta.

No ensaio, o servidor possui timezone de sessão +00:00, o backend é iniciado com JVM America/Sao_Paulo e a URL temporária usa connectionTimeZone=UTC. A sonda JDBC confirma microssegundos e NULL e verifica que alterar a sessão para +09:00 não converte o valor civil DATETIME. Isso valida o transporte atual, não implementa a futura gravação JPA do novo campo. Os timezones do MySQL original não foram consultados; deverão ser registrados na aplicação futura.

## Índices

Não acrescentar índice na primeira migração. A referência histórica de 125 pedidos não foi revalidada no original nesta fase; não tratá-la como volume atual. A coluna ficará inicialmente toda NULL e o código atual não a consulta. Um índice teria custo imediato sem benefício para esse código.

O executor registra EXPLAIN de uma consulta futura por status/período e verifica que os índices anteriores permanecem iguais. O conjunto de seis pedidos serve para validação funcional, não para dimensionamento de desempenho. Na Fase 5A.4, avaliar `(status, data_pagamento_utc)` com volume/distribuição sintéticos representativos e EXPLAIN; considerar custo de escrita e tamanho antes de aprovar índice separado. Nenhum índice foi proposto ou criado nesta etapa.

## Ensaio reproduzível

Pré-requisitos: Windows, Python stdlib, Java, MySQL Community 8.0.42 local e dependências Maven já resolvidas. Não é necessário instalar biblioteca Python. Na raiz:

```powershell
# Compilar as classes atuais sem executar testes ou iniciar a aplicação:
& '<maven local>/bin/mvn.cmd' -o -f PizzariaMauaBackEnd/pom.xml test-compile
python scripts/testar_data_pagamento_mysql.py --mysql-bin 'C:/Program Files/MySQL/MySQL Server 8.0/bin'
```

O executor usa o classpath do relatório Surefire financeiro local anterior. Se ele não estiver disponível, fornecer `--classpath-file <arquivo externo UTF-8>` contendo o classpath resolvido, separado pelo separador de paths do sistema. Dependências e classes devem corresponder ao POM/HEAD atuais; o executor verifica a existência dos arquivos e exclui test-classes e devtools. Não incluir configurações de conexão nesse arquivo. O classpath não substitui a compilação.

O executor não recebe host, URL ou schema externos. Inicializa dois servidores com `--no-defaults`, portas diferentes de 3306, armazenamento novo, schemas sintéticos e UUIDs distintos. Confere versão, datadir, porta, UUID e schema antes das escritas. Remove overrides de conexão/segredo herdados e fornece a configuração de teste via ambiente do processo. Nenhum segredo é passado na linha de comando ou versionado.

Somente no primeiro schema novo, inicia o backend com create para preparar as tabelas das entidades; não usa demo. Substitui a tabela vazia de idempotência pelo SQL aprovado e acrescenta o CHECK aprovado da Fase 3A. Essa preparação não é uma cópia da produção nem depende de seus registros. Cria seis pedidos (todos os estados, formas conhecidas e ausente), seis mesas/itens e produto, ingrediente, credencial e idempotência sintéticos.

Antes do ALTER, captura conteúdo por PK e metadados de todas as tabelas, gera backup sintético completo com snapshot transacional e compara os dados novamente. Aplica o arquivo de proposta sem modificá-lo e executa A–I. Inicia o backend **atual** com validate e perfil validacao5a2, confirma HTTP local e ausência de mudanças de schema/dados. O ensaio de precisão usa uma sonda JDBC temporária fora do Git; não modifica Java da aplicação.

Para J, restaura o backup anterior ao ALTER no segundo servidor independente e exige igualdade de conteúdo, colunas, engines, índices, FKs, CHECKs e ausência da nova coluna; depois inicia o backend atual com validate nesse estado recuperado. Erros SQL não são ignorados. Todos os processos criados são encerrados no finally. Backups, logs e resultado.json contêm apenas dados sintéticos e ficam no diretório temporário informado, fora do Git.

O dump compacto exporta todas as tabelas em ordem de dependência das FKs, calculada pelos metadados, sem desabilitar FOREIGN_KEY_CHECKS. Um ciclo de dependências interrompe o executor em vez de improvisar uma restauração. O executor identifica o executável real em java.home/bin para evitar filhos órfãos do launcher Windows; encerra apenas a árvore do PID criado e confere a liberação da porta HTTP.

O executor é exclusivamente de teste: não deve ser adaptado para receber a conexão original como atalho de implantação. Os testes são de estrutura, transporte JDBC, inicialização e recuperação; não comprovam relatórios por recebimento ainda não implementados. Os 604 testes da Fase 5A permanecem o registro da fase anterior, não uma execução nova desta revisão.

### Resultado da revisão — 17/11/2026

**Dez grupos A–J aprovados na execução final**, sem alteração do SQL original:

| Grupo | Resultado observado |
|---|---|
| A — ALTER com dados | Sucesso em 0,607 s neste pequeno conjunto sintético; dados anteriores iguais |
| B — Histórico | Todos os seis pedidos permanecem NULL, inclusive os três FINALIZADOS |
| C — Estrutura | Só a coluna aprovada foi adicionada; colunas antigas, FKs, CHECKs, índices e engines iguais |
| D — Backend atual | Inicializou com validate, perfil validacao5a2, sem seeds nem alteração dos snapshots |
| E — Precisão | SQL e JDBC preservaram `2026-11-11 03:20:00.123456`; mudança de timezone de sessão não reinterpretou DATETIME |
| F — NULL | JDBC retornou null e wasNull verdadeiro para histórico sem data |
| G — Preservação | Comparação determinística por PK/conteúdo de todas as sete tabelas aprovada |
| H — Índices | Nenhum novo índice proposto; anteriores iguais; EXPLAIN da consulta futura fez ALL sobre seis linhas, sem possível índice temporal |
| I — Reexecução | Erro esperado 1060, sem alterações adicionais nos dados ou objetos |
| J — Recuperação | Backup anterior restaurado em outro servidor; dados e metadados iguais, coluna ausente e backend validate aprovado |

Instâncias independentes, ambas MySQL 8.0.42 e schema fase5a2_sintetica:

- Ensaio: 127.0.0.1:51818, UUID `30d20776-e2ef-11f1-b3a8-9829a6e19634`, datadir próprio em `%TEMP%/pizzaria_5a2__p7mivu5/ensaio/data`.
- Recuperação: 127.0.0.1:52120, UUID `534f8ab6-e2ef-11f1-b679-9829a6e19634`, datadir próprio em `%TEMP%/pizzaria_5a2__p7mivu5/recuperacao/data`.
- HTTP: preparação 51825, validate após ALTER 51970, validate após restauração 52158. Todas essas portas e as dos dois MySQL foram conferidas como liberadas; nenhum processo Java do ensaio permaneceu ativo.

Conteúdo preservado: 1 usuário, 6 mesas, 6 pedidos, 6 itens, 1 produto, 1 ingrediente e 1 registro idempotente. Não são contagens do banco real. O timestamp sintético usado no teste E foi devolvido a NULL apenas na instância de teste.

Backup sintético: `%TEMP%/pizzaria_5a2__p7mivu5/antes_5a2_sintetico.sql`, 7.199 bytes, SHA-256 `8a16c9de3a54f3ba53af0ff7e0263ad7ebdf8b86a436b366e5446a72c86f371b`. Preservado fora do Git, junto ao resultado.json e logs sintéticos. Não substitui o backup novo obrigatório do original na Fase 5A.3.

O empacotamento JAR offline encontrou dependências ausentes do plugin de empacotamento. As fases de compilação foram concluídas; a inicialização real foi validada com as classes atuais e o classpath local da suíte, sem baixar dependências. Os testes iniciais do executor exigiram ajustes na sondagem HTTP, ordem das tabelas no dump e encerramento do launcher Java; a execução final acima validou as correções. Nenhum código de aplicação foi modificado. Não foi repetida a suíte de 604 testes nem implementada/testada a futura gravação da data pelo Service.

## Procedimento futuro — Fase 5A.3

Somente com autorização específica e SQL/HEAD aprovados:

1. Conferir Git, hash do SQL e configurações sem expor credenciais. Identificar localhost:3306/pizzamaua por versão 8.0.42, UUID e datadir, comparando com registros confiáveis anteriores. Não usar só o nome do schema.
2. Manter aplicação parada. Inspecionar processos, conexões, transações longas, escritores, DDL concorrente, triggers, routines e events que possam afetar pedidos. Não encerrar processos desconhecidos automaticamente. Interromper se a janela não puder ser comprovada.
3. Capturar SHOW CREATE TABLE e information_schema de todas as tabelas, engines, PK/FK/UNIQUE/CHECK, índices, defaults e tipos. Exigir pedido InnoDB, coluna ausente e ausência de mudanças incompatíveis com o ensaio. Registrar contagens e comparações determinísticas de conteúdo por PK, com valores sensíveis apenas comparados, nunca publicados.
4. Gerar novo backup completo fora do Git, em diretório protegido: snapshot transacional consistente para InnoDB, todas as tabelas/dados, índices, constraints, triggers, routines e events pertinentes. Verificar exit code, tamanho, SHA-256 e ausência de alterações concorrentes. Não depender de backups anteriores.
5. Restaurar o backup em MySQL 8.0.42 independente, com porta/datadir/UUID conferidos. Comparar conteúdo e estrutura integralmente. Aplicar exatamente o SQL aprovado nessa cópia; conferir a nova coluna, NULL em todos os históricos, preservação de objetos/dados e inicialização do backend atual com validate. Nenhum seed. Se qualquer etapa falhar, não escrever no original.
6. Reconfirmar identidade, manutenção, ausência da coluna, SQL idêntico e snapshot original ainda estável. Com cliente que interrompa após erro, executar o único ALTER uma vez. Registrar sucesso, duração e eventuais warnings. Não presumir rollback de DDL; não repetir automaticamente em caso de desconexão/resultado incerto.
7. Conferir coluna DATETIME(6), nullable, sem gerador automático, na última posição; comparar schema antigo excetuando apenas a nova coluna. Conferir todas as contagens, conteúdo por PK, relações, estoque, valores, status, formas e datas anteriores. Exigir NULL para todos os pedidos preexistentes. Preservar backup e evidências sem dados sensíveis no relatório.
8. Testar compatibilidade preferencialmente na cópia restaurada. Não iniciar o backend original como ferramenta de migração. A coluna extra não mapeada permite implantação gradual; registrar que a API ainda não a preenche e que pagamentos durante o intervalo até a Fase 5A.4 continuarão sem essa data. Coordenar a janela se esse intervalo não for aceitável.

Interromper por identidade/schema divergentes, coluna já existente, escritor/DDL concorrente, backup/restauração não comprovados, falha SQL, alteração inesperada ou perda de integridade. Preservar o estado e informar a última operação concluída. Não inventar correções nem ampliar o SQL aprovado.

## Recuperação

- **Antes da escrita:** abortar sem mudanças. Preservar backup e diagnóstico.
- **Após o ALTER, antes de novas gravações da coluna:** preferir manter a coluna nullable extra e voltar ao backend anterior compatível. Não remover coluna nem restaurar sobre o original automaticamente. Se for necessário recuperar o estado pré-migração, restaurar primeiro em nova instância, comparar e planejar troca controlada, sob autorização específica e sem perder gravações posteriores ao backup.
- **Reversão apenas da aplicação:** manter a coluna e seus dados. O backend antigo validado tolera a coluna extra; não confundir rollback de aplicação com exclusão de dados.
- **Após a Fase 5A.4 gravar datas:** remover a coluna perderia informação nova e restaurar o backup antigo também perderia transações posteriores. Parar escritores, obter novo backup do estado encontrado, preservar timestamps e histórico, restaurar/avaliar em ambiente independente e aprovar recuperação específica. Nunca DROP COLUMN ou restore destrutivo automáticos.
- **Erro ou resposta incerta de DDL:** consultar metadados antes de decidir se houve aplicação. Não executar novamente o script integral nem tentar compensar alterações sem diagnóstico.

## Próximas etapas

- 5A.3: migração original autorizada, backup novo restaurado e auditoria de preservação.
- 5A.4: mapeamento nullable, Clock/UTC, gravação única na confirmação sob os locks existentes; consultas por recebimento e tratamento explícito do histórico sem data. Não alterar contratos REST silenciosamente.
- 5A.5: testes integrados com frontend/backend/MySQL descartável; criação e pagamento em dias distintos, limites temporais, microssegundos, repetição/rollback/concorrência e valores desconhecidos.

D23 não está concluído apenas por esta preparação. D17 e Fase 5B permanecem fora do escopo.
