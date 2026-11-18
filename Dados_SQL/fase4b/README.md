# Fase 4B — concorrência (D09)

## Estratégia

Locks pessimistas com Spring Data JPA, dentro das transações de escrita. Não há alteração de entidades, schema, índices ou `ddl-auto=validate`.

`@Version` exigiria coluna/migração e política de repetição; uma baixa condicional isolada protegeria estoque, mas não o agregado Pedido nem a disponibilidade da mesa. A proteção pessimista reutiliza os registros existentes e mantém os DTOs atuais.

As transações dos fluxos protegidos usam **READ COMMITTED**. A mesa bloqueada serializa os escritores da mesma mesa, e a consulta normal de pedidos ativos precisa enxergar o commit anterior depois de esperar pelo lock. Isso também evita introduzir locks de intervalo em buscas por contas inexistentes no REPEATABLE READ. Não se altera o isolamento global do servidor. Chamadas atuais partem dos Controllers; uma futura transação externa que envolver esses comandos deverá respeitar essa fronteira e esse isolamento.

## Recursos e ordem

| Fluxo | Ordem dos locks | Alterações protegidas |
|---|---|---|
| Abertura | Mesa → Produtos por ID crescente | Disponibilidade, conta única, estoque, novos itens e total |
| Adição | Mesa → Pedido → Produtos por ID crescente | Estado ABERTO, itens, total e estoque |
| Fechamento/pagamento | Mesa → Pedido | Transições e liberação da mesa |
| Administração da mesa | Mesa | Estado/ativação e consulta de contas ativas |
| Edição/status/exclusão de produto | Produto | Estoque e demais campos da entidade |
| Atualização de item | Mesa → Pedido → Item | Coordenação com gravações do agregado; regras de transição de D11 não mudam |

Nenhum fluxo que bloqueia produto passa depois a bloquear Mesa ou Pedido. IDs repetidos são deduplicados para adquirir locks; os itens originais continuam sendo processados sobre o mesmo produto bloqueado, preservando o contrato. A validação e a baixa de cada quantidade ocorrem na mesma transação; insuficiência em um item posterior reverte também as baixas anteriores. Não se soma quantidade em `Integer` com risco de overflow.

Leituras preliminares de produtos/pedidos continuam servindo para validação e autorização. Antes de decidir ou modificar, os registros são lidos novamente sob lock. Entidades já carregadas são destacadas e relidas, antes de qualquer modificação, para não reutilizar valores antigos do primeiro nível de cache. Isso também evita um refresh em cascata que poderia adquirir locks fora da ordem. Os locks já adquiridos permanecem no banco até commit/rollback; destacar a entidade não os libera.

Consultas informativas continuam sem locks de escrita. O SQL observado no MySQL 8.0.42 foi `SELECT ... FROM mesa WHERE numero=? FOR UPDATE OF ...` e consultas equivalentes por PK para Pedido, Produto e Item. São bloqueados os registros selecionados; não se presume proteção automática de relacionamentos. A consulta por número usa a unicidade já existente da mesa.

## Conflitos e frontend

Os endpoints, DTOs, permissões e respostas de sucesso não mudam. Estoque insuficiente e conflitos operacionais continuam em 409. Produto que deixou de existir/estar ativo durante uma venda mantém o tratamento de entrada inválida em 400.

Falhas específicas de aquisição de lock, timeout e deadlock traduzidas pelo Spring/JPA recebem HTTP **409** com `mensagem`. Falhas genéricas de infraestrutura continuam seguindo o tratamento anterior; não são convertidas indiscriminadamente em conflito. Não há retry automático. O timeout efetivo em implantação continua sendo o configurado no MySQL; o executor usa oito segundos apenas na instância descartável.

Após 409, o frontend deve atualizar os dados e oferecer nova tentativa explícita. Falha de rede com resultado desconhecido não autoriza repetição automática: idempotência permanece em D20.

O PUT continua substituindo integralmente o produto, incluindo estoque absoluto. Um gerente enviando deliberadamente estoque 20 concorre com uma venda de uma unidade em uma ordem serial válida: saldo 19 se a edição ocorrer primeiro, ou 20 se ela ocorrer depois. Detectar que o próprio payload foi montado a partir de uma tela antiga exigiria versão/ETag no contrato HTTP e permanece uma limitação documentada. A correção impede sobrescrita causada por estado antigo da entidade dentro da transação.

## Testes reproduzíveis no Windows

Pré-requisitos: Python, binários MySQL Community **8.0.42** e Maven já resolvido localmente. Na raiz do repositório:

```powershell
python scripts/testar_mysql4b.py --mysql-bin 'C:/Program Files/MySQL/MySQL Server 8.0/bin' --maven-cmd 'C:/caminho/para/maven/bin/mvn.cmd'
```

O executor inicializa armazenamento novo no diretório temporário, escolhe porta diferente de 3306, verifica versão/datadir/UUID antes das escritas e gera senha aleatória somente em memória. Não conecta ao banco original. A instância contém exclusivamente dados sintéticos e é encerrada ao final. Arquivos sintéticos temporários são preservados fora do Git; logs Maven ficam em `PizzariaMauaBackEnd/target/fase4b-mysql.log`.

O teste Java verifica novamente identidade, diretório e nome do banco antes de criar o schema descartável. Recusa porta 3306 e banco `pizzamaua`. Sem `TEST_MYSQL_URL`, a classe MySQL é desativada para impedir conexão acidental; executar apenas `mvn test` sem esse ambiente **não comprova D09**. O executor fornece `TEST_MYSQL_URL`, `TEST_MYSQL_PASSWORD`, `TEST_MYSQL_UUID` e `TEST_MYSQL_DATADIR` somente ao processo filho.

Há threads e conexões independentes, latches, futuros com timeout e um bloqueio de teste inicialmente retido. A liberação ocorre depois de observar todas as operações esperando em `performance_schema.data_lock_waits`, incluindo cadeias de espera por índices. `data_locks` também verifica que produtos posteriores não foram bloqueados antes do primeiro ID. Os testes não dependem somente de atraso ou de sorte no escalonamento.

Resultado da execução: **472 testes Java aprovados, zero falhas/erros/ignorados**. Os 446 anteriores foram preservados; acrescentaram-se 22 cenários MySQL, três cenários web e um cenário unitário de preservação do comportamento de item legado sem pedido para erros de lock. Os mocks antigos receberam aliases explícitos dos métodos de lock e EntityManager falso; os testes JPA/MySQL usam repositories e locks reais.

Foram validados: última unidade; seis vendas concorrentes sobre dez unidades; dupla abertura; adições simultâneas; adição versus fechamento; dupla confirmação; PUT/status/desativação/exclusão de produto versus venda; produtos em ordem inversa; rollback integral; regras 4A e contas legadas duplicadas; quantidades repetidas; timeout; entidade previamente carregada; leituras sem locks; administração de mesa versus abertura; item da cozinha versus adição. Após cada cenário, conferem-se estoque não negativo, unicidade de conta ativa e total correspondente à soma dos itens.

O teste de deadlock chama deliberadamente os repositories em ordem inversa, fora dos Services, para provocar o erro MySQL 1213 e verificar tradução e rollback. Esse deadlock é esperado nesse teste negativo. Os fluxos dos Services que respeitam a ordem definida não apresentaram deadlock no ensaio; isso não é uma promessa de ausência de todo deadlock possível em operações externas ou futuras.

Nenhum dado real foi lido/restaurado nos testes; nenhuma migration ou inicialização do backend no banco de trabalho foi executada. D11, D12 e D20 permanecem para a Fase 4C. Não há revogação JWT nova nesta fase.

Referências: [locking do Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) e [locking reads do MySQL 8.0](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html).
