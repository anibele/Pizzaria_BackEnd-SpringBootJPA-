# Fase 5A — Faturamento recebido e limites dos dados financeiros

## Diagnóstico e contrato preservado

D16: o resumo somava e contava todos os pedidos não cancelados, incluindo ABERTO e AGUARDANDO_PAGAMENTO. A distribuição por pagamento também incluía pendências e apresentava forma ausente como DINHEIRO. O caixa já filtrava FINALIZADO, mas usava intervalos inclusivos.

Endpoints existentes:

| GET | Fonte e significado após a correção |
|---|---|
| `/api/dashboard/resumo` | `Pedido.faturamento`: soma de FINALIZADOS; ticket = soma / quantidade desses mesmos pedidos, arredondado apenas no resultado para duas casas, HALF_UP |
| `/api/dashboard/pagamentos` | Quantidade de FINALIZADOS agrupada pela forma persistida; não é distribuição de valores monetários |
| `/pedidos/faturamento?data=AAAA-MM-DD` | Soma de FINALIZADOS na data solicitada |
| `/pedidos/finalizados?data=AAAA-MM-DD` | Pedidos FINALIZADOS na data solicitada |

**Os períodos acima continuam classificados por `Pedido.dataHora`, a data de abertura. Não representam recebimentos ocorridos naquele dia.** Essa limitação de D23 não pode ser resolvida sem registrar o instante do pagamento.

O fluxo real `PedidoService.confirmarPagamentoEFecharMesa` exige AGUARDANDO_PAGAMENTO e as invariantes de mesa/conta ativa, adquire os locks existentes, atribui FINALIZADO e libera a mesa na mesma transação. Esse é o único fluxo normal que atribui FINALIZADO. Portanto, esse status é a evidência disponível de recebimento confirmado; não comprova a data nem a forma de recebimento de registros históricos incompletos.

Não existem entidade Pagamento, data de solicitação de fechamento, data de atualização ou data de pagamento no Pedido. Datas dos itens registram inclusão/preparo, e datas da idempotência registram adições: nenhuma comprova recebimento. `formaPagamento` é uma String definida na solicitação da conta, não um comprovante externo de liquidação.

## Regras implementadas

- Somente FINALIZADO compõe receita e contagem paga. ABERTO, AGUARDANDO_PAGAMENTO e CANCELADO ficam excluídos.
- A soma usa BigDecimal e o total persistido do pedido, sem joins com itens. O ticket usa numerador e denominador obtidos no mesmo SELECT, evitando inconsistência entre duas leituras durante um commit concorrente.
- Sem pedidos elegíveis, soma e ticket são zero e a distribuição é vazia. Totais nulos na agregação são tratados com COALESCE.
- Intervalos financeiros são `[início do dia, início do dia seguinte)`. Microssegundos imediatamente anteriores ao próximo dia pertencem somente ao primeiro intervalo.
- O dia corrente do resumo/distribuição é escolhido explicitamente em America/Sao_Paulo, independente do timezone da máquina. O Clock interno pode ser controlado nos testes.
- Os LocalDateTime existentes continuam comparados como horários civis, sem conversão ou reinterpretação do histórico. Não há convenção histórica de timezone comprovada para os DATETIME; alterar o timezone da JVM não recupera essa informação. Esta fase não modifica gravação de datas, configurações JDBC/MySQL nem dados antigos.
- `/vendas-hora` continua contando demanda operacional, não receita. Rankings e D17 permanecem fora do escopo.
- Taxa de bebidas continua usando pedidos operacionais não cancelados no numerador e denominador; não passa a dividir consumo em andamento por quantidade paga. As consultas auxiliares diárias de bebidas e tempo de cozinha apenas compartilham os limites semiabertos do resumo; suas demais regras são preservadas.

## Formas de pagamento — parte corrigível de D23

O React existente utiliza PIX, CARTAO e DINHEIRO. Na solicitação de fechamento, o Service aceita essas três formas, normaliza espaços externos e caixa com Locale.ROOT e rejeita ausentes, vazias e desconhecidas com HTTP 400 e JSON contendo `mensagem`, antes de alterar o pedido.

O contrato de abertura continua aceitando o DTO existente; seu campo de forma não é evidência de pagamento (o cliente legado envia PENDENTE). Não se passa a gravá-lo como uma forma recebida.

Os valores antigos não são regravados: NULL permanece NULL na distribuição, separado de DINHEIRO. Strings vazias e categorias legadas continuam representando o valor realmente persistido. Agrupamentos de strings seguem a collation do banco. Não se inventa categoria para informações ausentes. Uma conta legada já AGUARDANDO_PAGAMENTO continua podendo ser confirmada sem uma reclassificação automática da forma antiga.

URLs, permissões, DTOs e formatos de sucesso permanecem iguais. O React deverá apresentar formas ausentes como informação desconhecida, se necessário, sem convertê-las em dinheiro. O resumo passa a mostrar apenas valores pagos; pode diminuir em relação ao cálculo anterior. A distribuição passa a contar apenas pedidos pagos. Nenhuma alteração no frontend foi realizada.

## D23 — PENDENTE DE MIGRAÇÃO

Exemplo ainda impossível de classificar corretamente por recebimento: pedido aberto às 23h40 e pago no dia seguinte continua no período da abertura. Um teste explícito registra esse limite, sem afirmar que a data financeira foi corrigida. Data ausente permanece ausente e não é atribuída a um período fictício.

`proposta_data_pagamento.sql` é uma proposta separada: acrescentar `pedido.data_pagamento_utc DATETIME(6) NULL`, sem DEFAULT, backfill ou UPDATE. Foi preparada para ensaio isolado, não para execução nesta fase no banco de trabalho. Não existe novo mapeamento JPA de produção, mantendo a compatibilidade com `ddl-auto=validate` e o schema atual.

Etapa posterior, sujeita a autorização específica:

1. Conferir identidade/schema, obter backup restaurável, ensaiar e autorizar a migração nullable. Preservar NULL para recebimentos históricos sem instante conhecido; não copiar abertura, fechamento ou data atual.
2. Somente após a migração, implementar a gravação do instante UTC na confirmação efetiva, dentro da transação e dos locks existentes. Não atualizar em repetição rejeitada.
3. Para relatórios por recebimento, converter limites de datas em America/Sao_Paulo para UTC e filtrar com início inclusivo/fim exclusivo. Documentar separadamente recebimentos sem data conhecida, sem incluí-los artificialmente em um período.
4. Validar viradas de dia/mês/ano e microssegundos com a nova coluna, bem como o contrato de exposição de históricos desconhecidos. Avaliar índices com EXPLAIN e volume real antes de propor novos índices.

A proposta adiciona apenas o campo mínimo; não cria novas formas, pagamentos parciais, estornos ou um sistema contábil. A escolha da forma no fechamento também não permite inferir categorias inexistentes no histórico.

## Verificação segura

Novos testes: `DashboardFinanceiroTest` e `FinanceiroMysqlTest`. Incluem elegibilidade dos quatro estados, centavos, múltiplos itens sem multiplicação de total, ticket pago, formas ausentes/legadas, normalização e rejeição, contratos HTTP, limites temporais, timezone explícito e confirmação real pelo Service. A leitura concorrente ocorre enquanto a confirmação foi executada/flushada em outra conexão, antes de liberar o commit com latches; somente depois do commit o valor aparece.

O teste MySQL reutiliza a guarda da Fase 4B: servidor 8.0.42 em 127.0.0.1, porta diferente de 3306, schema sintético fase4b_sintetica, UUID e datadir conferidos antes do DDL. Prepara entidades, SQL aprovado da idempotência e CHECK da Fase 3A apenas nessa instância; o EntityManager usado pelos Services opera com validate. Snapshots determinísticos comparam conteúdo das tabelas sem exibir dados sensíveis.

A proposta nullable é ensaiada somente nesse ambiente, comprovando histórico NULL, ausência de DEFAULT e validação Hibernate com coluna extra ainda não mapeada. A coluna é removida exclusivamente da base sintética após o teste. Isso não comprova relatórios por data de pagamento, ainda não implementados.

Executor existente, com caminhos locais apropriados:

```powershell
python scripts/testar_mysql4b.py --mysql-bin "C:/Program Files/MySQL/MySQL Server 8.0/bin" --maven-cmd "<caminho local>/mvn.cmd"
```

Esse executor inicia seu próprio servidor, exporta apenas conexão sintética para os testes e o encerra ao terminar. Não utiliza o MySQL original e não inicia o backend conectado ao banco de trabalho. Sem TEST_MYSQL_URL, os testes MySQL são condicionais; uma execução sem eles não comprova a integração.

D16 pode ser entregue independentemente. D23 está parcialmente melhorado quanto à categorização, mas permanece **PENDENTE DE MIGRAÇÃO** para data real de recebimento. D17 fica reservado à Fase 5B. Os mecanismos de concorrência e idempotência anteriores não foram alterados.

### Resultado desta execução

Suíte completa: **604 testes aprovados, zero falhas, zero erros e zero ignorados**. Os 567 anteriores foram preservados e foram acrescentados 37 (9 de resumo/contrato/timezone e 28 financeiros MySQL). Total MySQL: 129, sendo 71 de concorrência, 30 de idempotência HTTP e 28 financeiros, em MySQL 8.0.42 independente. Compilação e regressão concluídas; o executor confirmou o encerramento da instância descartável.

Resultados sintéticos observados: R$30,35 permanecem fora da receita em AGUARDANDO_PAGAMENTO e entram após confirmação; dois finalizados de R$20,30 e R$10,01 resultam em R$30,31 e ticket R$15,16, mesmo com pedidos abertos/pendentes/cancelados no conjunto. Intervalos adjacentes preservam os microssegundos sem dupla contabilização. A leitura não vê pagamento ainda sem commit.

A proposta SQL foi testada somente no ambiente sintético. Nenhuma conexão ou escrita no MySQL original foi necessária, nenhum dado real foi alterado e o frontend permaneceu fora das alterações. Nenhum teste comprovou classificação por data real de pagamento: ela continua pendente da etapa futura descrita acima.
