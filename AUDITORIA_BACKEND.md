# Auditoria técnica — Backend Pizzaria Mauá

> Os achados originais abaixo foram preservados como histórico. O estado atual, evidências e ressalvas estão no **Encerramento da auditoria — 18/11/2026**, ao final deste arquivo.

Data: 17/11/2026 — America/Sao_Paulo
Repositório: C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-
Commit inspecionado: f9bf46e9172118fe1e4ec4bb64f47c7a19b7a7ed
Escopo: investigação, documentação e recomendações. Nenhuma correção aplicada.

## A. Resumo executivo

**O backend possui um fluxo principal implementado, mas ainda não é logicamente consistente nem está pronto para implantação real.** Produtos têm as quatro operações CRUD; mesas têm cadastro, consultas, alterações específicas e exclusão; pedidos usam operações de domínio e não precisam de um CRUD genérico. Os problemas mais relevantes estão na autenticação padrão, autorização por recurso, validação de quantidades, estados operacionais e concorrência.

Pontos positivos: arquitetura simples em camadas; DTOs nas respostas; BCrypt nos caminhos de criação efetivamente utilizados; cálculo monetário com BigDecimal; preço unitário histórico no item; produtos inativos bloqueados na inclusão; transações nas operações de pedidos e mesas; restrições únicas para número de mesa e username; verificação de existência antes das alterações; transições do pedido ABERTO → AGUARDANDO_PAGAMENTO → FINALIZADO verificadas no service.

Fragilidades: segredo JWT público como fallback, contas previsíveis criadas automaticamente, tokens válidos por dez anos, ausência de vínculo usuário–mesa e de autorização sobre pedidos individuais, alteração de ativação acessível a qualquer autenticado, entradas sem validação, abertura de pedido ignorando pagamento pendente e indisponibilidade de mesa, ausência de controle de concorrência, exceções sem contrato consistente e apenas um teste de carregamento de contexto.

Maturidade: **protótipo acadêmico com uma base de organização adequada, mas com controles operacionais e de segurança insuficientes para uso real**. A prontidão funcional não pôde ser demonstrada em execução: a validação Maven offline falhou na resolução do parent e o teste existente não oferece isolamento do banco.

As cinco descobertas prioritárias são D01/D02 (acesso administrativo na configuração padrão), D03 (pedidos de outras mesas), D04 (ativação administrativa sem restrição de perfil), D05 (quantidades negativas alteram estoque e cobrança) e D07/D09 (múltiplas contas ativas e concorrência).

### Critérios e limites de certeza

- **Confirmado:** comportamento demonstrado pela leitura direta do código ou por verificação estática. Não significa que foi explorado por HTTP.
- **Provável:** consequência sustentada pelo código, dependente de concorrência, configuração ou estado externo.
- **A verificar:** exige resolução das dependências, especificação de negócio ou ambiente isolado.
- As gravidades consideram o impacto se a aplicação for implantada com o comportamento encontrado. O ambiente de produção, seus overrides e o banco não foram acessados.
- As linhas são da cópia analisada. As referências abaixo apontam para arquivos locais; a notação de intervalo identifica o trecho e o link abre sua primeira linha.

## B. Arquitetura identificada

### Tecnologias e dependências

O [pom.xml:5-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/pom.xml:5) declara Spring Boot **4.1.0** e [pom.xml:29-31](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/pom.xml:29) define Java **17**. O Java disponível no terminal é Oracle JDK **23.0.2**; isso não altera o alvo declarado no POM. Configurações locais da IDE citam outros JDKs, sem substituir a configuração Maven.

Dependências declaradas em [pom.xml:32-89](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/pom.xml:32):

| Dependência | Finalidade / versão declarada |
|---|---|
| spring-boot-starter-data-jpa | Persistência JPA, versão herdada |
| spring-boot-starter-webmvc | API MVC, versão herdada |
| spring-boot-starter-json | JSON, versão herdada |
| spring-boot-starter-web | Starter web adicional, versão herdada |
| com.fasterxml.jackson.core:jackson-databind | Jackson 2, versão herdada |
| spring-boot-devtools | Runtime opcional |
| mysql-connector-j | Driver MySQL em runtime |
| lombok | Geração de código, opcional |
| spring-boot-starter-security | Autenticação/autorização |
| com.auth0:java-jwt | **4.4.0**, fixada diretamente |
| spring-boot-starter-data-jpa-test | Testes de persistência |
| spring-boot-starter-webmvc-test | Testes web |

O plugin Spring Boot exclui Lombok do empacotamento; o compiler plugin configura processamento de anotações em main e test ([pom.xml:92-142](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/pom.xml:92)). Maven Wrapper 3.3.4 aponta para Maven **3.9.16** ([maven-wrapper.properties:1-3](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/.mvn/wrapper/maven-wrapper.properties:1)).

A leitura do BOM 4.1.0 existente no cache local indica gerenciamento de Spring Framework 7.0.8, Spring Security 7.1.0, Hibernate 7.4.1.Final, Lombok 1.18.46, MySQL Connector 9.7.0 e BOMs Jackson 2.21.4/3.1.4. **São versões de gerenciamento observadas no cache, não uma árvore de dependências resolvida nesta auditoria.** Não foi confirmado o classpath efetivo do projeto.

Não há declaração de starter de Bean Validation, Flyway, Liquibase, H2, Testcontainers, scanner de dependências ou plugin de cobertura. A presença de Bean Validation transitiva não foi determinada; de qualquer forma, o código não usa constraints nem @Valid.

### Organização e responsabilidades

Foram examinados os **54 arquivos Java de produção e o único arquivo Java de teste**, além de POM, properties, wrapper, README, SQL e configurações auxiliares relevantes. Não foram encontrados AGENTS.md aplicáveis, migrations versionadas, perfis de teste ou pipelines de CI no inventário.

Pacote raiz: inf.anibele.pizzariamauabackend.

| Camada | Conteúdo |
|---|---|
| controller | AutenticacaoController, ProdutoController, MesaController, PedidoController, DashboardController |
| service | AutenticacaoService, ProdutoService, MesaService, PedidoService, DashboardService |
| repository | UsuarioRepository, ProdutoRepository, MesaRepository, PedidoRepository, ItemPedidoRepository |
| model | Cinco entidades, um embeddable e cinco enums |
| dto | Contratos de login, usuário, produtos, mesas, pedidos, itens e seis DTOs de dashboard |
| mapper | UsuarioMapper, ProdutoMapper, MesaMapper, PedidoMapper, ItemPedidoMapper |
| security | SecurityConfigurations, SecurityFilter, TokenService |
| config | DataInitializer, com três CommandLineRunner |

A separação Controller → Service → Repository é adequada ao tamanho do projeto. Services são classes concretas; não criar interfaces apenas por convenção é uma escolha válida. A exceção de responsabilidade mais visível é o controller de mesas executar uma desativação como recuperação de erro de exclusão (D12). AutenticacaoService implementa UserDetailsService.

### Modelo JPA

| Tipo | Relacionamentos e observações |
|---|---|
| Usuario | Entidade UserDetails; username único, senha e role obrigatórios; sem associação com Mesa |
| Mesa | Número obrigatório e único; status enum; ativo obrigatório |
| Produto | BigDecimal precoBase; categoria enum; descrição @Embedded; estoque inteiro |
| DetalhesDescricao | @Embeddable; ingredientes em @ElementCollection / produto_ingredientes |
| Pedido | @ManyToOne Mesa obrigatório; @OneToMany itens mappedBy=pedido, cascade ALL |
| ItemPedido | @ManyToOne Produto e Pedido; quantidade, preço histórico, status, inclusão e conclusão |
| Categoria | PIZZAS, BEBIDAS, SOBREMESAS, ACOMPANHAMENTOS |
| Perfis | GERENTE, COZINHA, MESA, **CAIXA** |
| Estados | Mesa: LIVRE/OCUPADA/RESERVADA/MANUTENCAO; pedido: ABERTO/AGUARDANDO_PAGAMENTO/FINALIZADO/CANCELADO; item: PENDENTE/EM_PREPARO/PRONTO |

Os ManyToOne usam EAGER padrão; itens e ingredientes usam LAZY padrão. O mappedBy coincide com ItemPedido.pedido e os dois lados são preenchidos no cadastro. Não há cascade do item para produto ou mesa: apagar pedido pelo repository não apaga esses recursos. Não há endpoint de exclusão de pedido. Ausência de orphanRemoval não é um defeito no fluxo atual, que não remove itens. Não há ManyToMany, OneToOne, @Version, locks, EntityGraph ou fetch join.

O uso de BigDecimal é positivo. Não há definição explícita de precisão/escala ou limites para preços; a adequação do DDL efetivo exige inspecionar um banco isolado (D06). DTOs evitam ciclos na resposta HTTP. @Data em entidades traz outro risco, detalhado em D18.

### Banco, inicialização e autenticação

[application.properties:3-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/resources/application.properties:3): MySQL local pizzamaua, criação automática do banco, usuário root, senha fixa e ddl-auto=update. Não há migrations. O script Dados_SQL/historico_junho_2026.sql é uma carga de histórico com INSERTs, não um mecanismo de migração.

DataInitializer cria gerente, cozinha, caixa, dez usuários MESA, mesas 1–10 e 34 produtos ausentes por nome. Não está condicionado a perfil de desenvolvimento ([DataInitializer.java:22-83](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:22), [DataInitializer.java:228-234](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:228)).

Login valida senha via AuthenticationManager e retorna token, username e role ([AutenticacaoController.java:23-41](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/controller/AutenticacaoController.java:23)). Tokens HMAC256 exigem issuer e assinatura válidos; SecurityFilter busca novamente o usuário pelo subject e usa as authorities atuais do banco. Alterar apenas a claim role não concede acesso. O sistema usa Bearer no cabeçalho, sessões STATELESS e não usa refresh token, cookie de autenticação ou endpoint de logout.

### Superfície REST e permissões encontradas

| Operação | Perfil admitido pela configuração atual |
|---|---|
| POST /auth/login | Público |
| POST, GET coleção, PUT, PATCH status, DELETE /produtos | GERENTE |
| GET /produtos/ativos e /produtos/{id} | MESA, GERENTE |
| POST /mesas; DELETE /mesas/{numero} | GERENTE |
| GET /mesas | COZINHA, GERENTE, CAIXA |
| GET /mesas/{numero} | MESA, GERENTE, CAIXA |
| GET /mesas/status | **MESA também**, por sobreposição com /mesas/* |
| PATCH /mesas/{numero}/status | GERENTE, CAIXA |
| PATCH /mesas/{numero}/ativacao | **Qualquer autenticado**, pela regra final |
| POST /pedidos; PUT /pedidos/{id}/itens | MESA, GERENTE |
| PATCH /pedidos/{id}/finalizar | MESA, GERENTE, CAIXA |
| PATCH /pedidos/{id}/confirmar | CAIXA, GERENTE |
| GET /pedidos/mesa/{numero}/aberto | MESA, GERENTE, CAIXA |
| GET /pedidos/cozinha | COZINHA, GERENTE, CAIXA |
| PATCH /pedidos/itens/{id}/status | COZINHA, GERENTE |
| GET /pedidos/finalizados e /faturamento | CAIXA, GERENTE |
| GET /api/dashboard/resumo, vendas-hora, pagamentos, rankings | GERENTE |

A autorização é por rota, sem conferência de titularidade. Regras /usuarios/** e /relatorios/** existem na segurança, mas não há controllers correspondentes. O matcher hasAnyRole em /pedidos/** não combina com as authorities sem ROLE_; os GETs atuais relevantes são atendidos antes por regras específicas, portanto **não foi comprovada uma falha nesses endpoints por esse matcher residual** (ver E).

## C. Matriz de CRUDs

“Implementado” descreve presença da operação, não aprovação funcional nem execução bem-sucedida.

| Recurso | CREATE | READ | UPDATE | DELETE | Situação |
|---|---|---|---|---|---|
| Produto | Implementado | Implementado | Implementado: PUT e PATCH ativo | Implementado | CRUD completo em superfície; validação e erros deficientes |
| Mesa | Implementado, usuário opcional | Implementado por número, coleção e status | Implementado: status e ativação | Parcial: exclusão ou desativação com 409 | Falhas de autorização, vínculo e efeitos de erro |
| Pedido | Implementado | Parcial: cozinha, mesa ativa, finalizados por data | Parcial: adicionar itens, solicitar e confirmar fechamento | Ausente | Operações de domínio implementadas; cancelamento ausente |
| ItemPedido | Implementado dentro do pedido | Implementado dentro das respostas de pedido | Parcial: somente status | Ausente | Sem redução de quantidade, retirada ou cancelamento |
| Usuario | Parcial: seeds e criação de credencial MESA | Parcial: consulta interna para login/JWT | Ausente na API | Parcial interno, por exclusão de mesa | Não existe CRUD público, apesar dos DTOs e regra /usuarios/** |
| Categoria | Não aplicável | Parcial: valores nos produtos | Não aplicável | Não aplicável | Enum fixo; ausência de CRUD não é defeito |
| DetalhesDescricao / ingredientes | Implementado via produto | Implementado via produto | Implementado via PUT produto | Implementado como parte do produto | Objeto de valor, sem necessidade de API própria |
| Dashboard | Não aplicável | Implementado: quatro consultas | Não aplicável | Não aplicável | Leitura gerencial; métricas com problemas semânticos |
| Autenticação | Login implementado | Não aplicável | Não aplicável | Logout/revogação ausentes | Não é CRUD; tokens de longa duração |

### Avaliação individual das operações

**Produto:** cadastrar mapeia e salva sem checar conteúdo ou duplicidade; nomes duplicados não têm proibição de negócio demonstrada, mas conflitam com o Optional usado no seed (D15). READ por ID verifica existência, porém lança RuntimeException; listagens não têm paginação. PUT substitui todos os campos, inclusive ativo/estoque, e limpa descrição se omitida; isso pode ser válido como substituição completa, mas precisa de validação e contrato explícito. PATCH ativo é restrito ao gerente. DELETE não tem cascata para itens históricos: a expectativa é bloqueio por FK, com sugestão de desativação; a resposta não está mapeada adequadamente (D13). O DDL real não foi inspecionado.

**Mesa:** cadastro evita repetição na aplicação e há unique no banco; novo status LIVRE e ativo=true são definidos no mapper. Não se valida número positivo ou senha obrigatória/não vazia. A transação cobre mesa e usuário, logo falha no encode/save deve reverter o cadastro. READ usa número público, diferente do ID interno; isso é aceitável. Status pode ser alterado independentemente de pedidos. Ativação não está protegida por perfil e não impede pedido. DELETE tenta remover um usuário por nome presumido, trata FK e pode desativar mesmo retornando conflito.

**Pedido:** criação atribui mesa existente, preço atual a cada item e soma quantidade × preço histórico; produtos precisam estar ativos e ter estoque suficiente para a comparação encontrada. Não usa o preço enviado pelo cliente. Lista vazia é aceita e quantidade negativa passa. Adicionar itens verifica ABERTO, mas usa PUT para acrescentar, sem idempotência. O fechamento verifica os dois passos do pedido, porém a abertura só bloqueia ABERTO e a cozinha não verifica o estado do pedido. Não existe GET por ID, listagem geral, cancelamento ou exclusão. Uma consulta de pedido ativo retorna o primeiro sem ordenação se houver duplicidade. Apagar histórico pode ser indesejável; não proponho um DELETE genérico.

**Item:** não existe cadastro isolado, o que é coerente com o agregado Pedido. Não existe edição de quantidade já enviada, remoção ou compensação de estoque. É necessário decidir a política de correção/cancelamento; o enum CANCELADO sozinho não implementa a operação.

**Usuário:** senhas são codificadas no seed e em MesaService; UsuarioMapper copia senha literal, mas não tem consumidor ativo no cadastro. Isso é risco de reutilização futura, **não evidência de senha em texto puro sendo salva pelas rotas atuais**. Ausência de CRUD completo de usuários pode ser uma decisão para a demonstração; ainda assim, não há caminho de API para trocar senha, bloquear conta ou revogar acesso.

## D. Problemas encontrados

### D01 — Segredo JWT conhecido como fallback

- **Gravidade:** CRÍTICA. **Certeza:** Confirmado no código; exploração depende de o segredo não ser sobrescrito no ambiente.
- **Local/classe/método:** [TokenService.java:18-43](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/TokenService.java:18), TokenService.gerarToken/getSubject; [application.properties:1-16](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/resources/application.properties:1).
- **Problema/evidência:** @Value fornece um segredo literal previsível quando api.security.token.secret está ausente; properties não configura outro. Geração e verificação usam esse mesmo valor.
- **Consequência:** quem conhece o segredo pode assinar token com subject de um usuário gerente existente. A role efetiva vem do banco, o que não impede personificação desse usuário.
- **Cenário:** implantação padrão com gerente1 existente e sem override do segredo.
- **Recomendação:** exigir segredo externo forte, falhar ao iniciar se estiver ausente e rotacionar o segredo usado em qualquer ambiente exposto.
- **Teste recomendado:** inicialização sem segredo deve falhar; token assinado pelo segredo antigo/padrão deve ser recusado; subject inexistente não deve autenticar.

### D02 — Contas administrativas previsíveis criadas automaticamente

- **Gravidade:** CRÍTICA. **Certeza:** Confirmado na inicialização; acesso indevido depende de contas ainda presentes com as credenciais iniciais e API acessível.
- **Local/classe/método:** [DataInitializer.java:22-60](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:22), carregarUsuarios; [Usuario.java:46-57](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Usuario.java:46), verificações de conta.
- **Problema/evidência:** gerente, cozinha, caixa e mesas recebem senha igual ao username, sem @Profile ou condição de ambiente. BCrypt protege armazenamento, mas não torna uma senha pública imprevisível.
- **Consequência:** login administrativo sem necessidade de quebrar hash ou JWT; recriação de contas padrão removidas no próximo start.
- **Cenário:** primeira execução em um servidor acessível com configuração padrão.
- **Recomendação:** restringir seeds demonstrativos a perfil explícito; provisionar gerente com segredo externo e troca inicial; remover/rotacionar credenciais já utilizadas.
- **Teste recomendado:** perfil de implantação não deve criar usuários de demonstração; reinício não deve recriar credenciais revogadas.

### D03 — Ausência de autorização sobre a mesa e o pedido específico

- **Gravidade:** ALTA. **Certeza:** Confirmado por inspeção do fluxo completo.
- **Local/classe/método:** [SecurityConfigurations.java:53-59](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityConfigurations.java:53); [PedidoService.java:46-52](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:46), abrirPedido; [PedidoService.java:99-105](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:99), adicionarItens; [PedidoService.java:156-169](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:156), finalizarPedido; [PedidoService.java:200-208](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:200), buscarPedidoAbertoDaMesa; [Usuario.java:14-28](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Usuario.java:14).
- **Problema/evidência:** o perfil MESA autoriza rotas com qualquer ID/número; services não recebem principal nem verificam vínculo. O modelo não relaciona Usuario a Mesa.
- **Consequência:** leitura do consumo, adição de produtos e solicitação de fechamento de outra mesa.
- **Cenário:** mesa01 troca o número da URL para 2, descobre o pedido e usa seu ID em PUT/finalizar.
- **Recomendação:** vínculo explícito entre credencial e mesa, com autorização no service para cada recurso; manter acesso operacional dos perfis administrativos previsto em regra.
- **Teste recomendado:** token da mesa A deve receber 403 ao ler/alterar pedido da mesa B; testar os quatro caminhos e acesso permitido do gerente.

### D04 — Ativação de mesa sem restrição de perfil e matcher sobreposto

- **Gravidade:** ALTA. **Certeza:** Confirmado.
- **Local/classe/método:** [MesaController.java:53-56](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/controller/MesaController.java:53), alterarAtivacao; [SecurityConfigurations.java:59-84](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityConfigurations.java:59), securityFilterChain.
- **Problema/evidência:** PATCH /mesas/*/ativacao não tem matcher específico e cai em authenticated(). Além disso, GET /mesas/status casa com /mesas/* antes da regra restrita de /mesas/status.
- **Consequência:** MESA e COZINHA podem ativar/desativar mesas; MESA acessa a listagem por status que o matcher posterior pretendia restringir.
- **Cenário:** token MESA chama PATCH /mesas/2/ativacao?ativo=false e GET /mesas/status?status=LIVRE.
- **Recomendação:** definir permissão explícita para ativação, ordenar regras específicas antes das genéricas e avaliar denyAll para caminhos não previstos.
- **Teste recomendado:** matriz MockMvc de todos os perfis e anônimo em cada rota, incluindo colisões literal/coringa.

### D05 — Quantidade negativa cria estoque e reduz a cobrança

- **Gravidade:** ALTA. **Certeza:** Confirmado pela lógica; sem execução contra banco.
- **Local/classe/método:** [ItemPedidoRequestDTO.java:3-6](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/ItemPedidoRequestDTO.java:3); [PedidoService.java:63-82](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:63), abrirPedido; [PedidoService.java:109-128](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:109), adicionarItens.
- **Problema/evidência:** não existe quantidade > 0. Com estoque 10 e quantidade -2, a condição 10 < -2 é falsa, o estoque passa a 12 e preço 50 produz subtotal -100.
- **Consequência:** corrupção de estoque e total, explorável pelo perfil MESA autorizado a pedir.
- **Cenário:** item com quantidade=-2 em pedido novo ou existente; zero também é aceito.
- **Recomendação:** exigir quantidade positiva e limitada no contrato e no domínio; validar IDs e elementos da lista; reforçar invariantes no banco quando apropriado.
- **Teste recomendado:** -2, zero, null e inteiro extremo devem ser rejeitados sem alterar estoque, itens ou faturamento; incluir lista com item válido seguido de inválido.

### D06 — Campos essenciais sem validação permitem dados inválidos ou falhas nulas

- **Gravidade:** MÉDIA. **Certeza:** Confirmado; persistência exata depende das constraints existentes no banco.
- **Local/classe/método:** [ProdutoRequestDTO.java:6-14](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/ProdutoRequestDTO.java:6); [Produto.java:14-22](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Produto.java:14); [ProdutoService.java:27-30](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:27), cadastrar, e [ProdutoService.java:55-79](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:55), editar; [PedidoRequestDTO.java:5-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/PedidoRequestDTO.java:5); [MesaService.java:37-59](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:37), cadastrar.
- **Problema/evidência:** nome/categoria/preço/estoque sem constraints ou validação; pedidos aceitam lista vazia e não tratam lista/elemento/quantidade nulos; número da mesa não precisa ser positivo; senha de mesa pode ser vazia ou null. Não há @Valid nos controllers.
- **Consequência:** produto incompleto ou negativo, pedido vazio, erros de unboxing/multiplicação e respostas internas em entradas previsivelmente inválidas.
- **Cenário:** produto com precoBase=-1 ou qtdEstoque=null; pedido com itens=[] ou quantidade=null; criarUsuario=true com password=null.
- **Recomendação:** Bean Validation em entradas e validação do domínio; regras condicionais para credencial; limites e escala monetária explícitos, preservando semântica de PUT completo.
- **Teste recomendado:** tabela de entradas ausentes, nulas, vazias, negativas, extensas e decimais com excesso de casas; assert 400 e ausência de gravação.

### D07 — Abertura permite segundo pedido enquanto o anterior aguarda pagamento

- **Gravidade:** ALTA. **Certeza:** Confirmado, mesmo sem concorrência.
- **Local/classe/método:** [PedidoService.java:50-53](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:50), abrirPedido; [PedidoService.java:178-188](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:178), confirmarPagamentoEFecharMesa; [PedidoService.java:200-208](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:200), buscarPedidoAbertoDaMesa.
- **Problema/evidência:** abertura busca apenas ABERTO; consulta de pedido ativo considera ABERTO e AGUARDANDO_PAGAMENTO. Confirmação sempre libera a mesa.
- **Consequência:** dois pedidos ativos na mesma mesa, consulta ambígua e mesa LIVRE com outro pedido ainda aberto.
- **Cenário:** abrir A → finalizar A → abrir B na mesma mesa → confirmar A. O pedido B continua ativo e a mesa é liberada.
- **Recomendação:** adotar definição única de pedido ativo, impedir nova conta até o encerramento e liberar mesa apenas quando a invariável operacional for satisfeita.
- **Teste recomendado:** sequência acima deve bloquear B ou manter consistência conforme política explícita; consulta ativa nunca deve escolher arbitrariamente entre dois pedidos.

### D08 — Disponibilidade e status da mesa não controlam abertura

- **Gravidade:** MÉDIA. **Certeza:** Confirmado.
- **Local/classe/método:** [PedidoService.java:46-58](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:46), abrirPedido, e [PedidoService.java:89-90](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:89); [MesaService.java:84-98](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:84), atualizarStatus/alterarAtivacao.
- **Problema/evidência:** mesa existente basta: ativo e estados MANUTENCAO/RESERVADA são ignorados. Atualização manual permite LIVRE com pedido ativo.
- **Consequência:** desativação não bloqueia consumo, manutenção perde efeito e visão do salão diverge dos pedidos.
- **Cenário:** gerente desativa mesa ou a marca MANUTENCAO; um usuário abre pedido, que sobrescreve o estado para OCUPADA.
- **Recomendação:** definir estados que permitem abertura, validar ativo e coordenar status com conta ativa; decidir se desativação durante consumo é permitida.
- **Teste recomendado:** matriz ativo/status versus abertura, alteração de status com pedido ativo e manutenção após tentativa rejeitada.

### D09 — Operações concorrentes sem controle de estoque, total ou conta ativa

- **Gravidade:** ALTA. **Certeza:** Provável, condicionada à sobreposição de transações; ausência de controle confirmada.
- **Local/classe/método:** [PedidoService.java:46-92](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:46), abrirPedido; [PedidoService.java:99-134](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:99), adicionarItens; [ProdutoService.java:55-89](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:55), editar/alternarStatus; [Produto.java:9-22](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Produto.java:9), [Pedido.java:11-31](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Pedido.java:11), [Mesa.java:8-21](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Mesa.java:8); repositories sem locks.
- **Problema/evidência:** operações leem, verificam e regravam valores sem @Version, lock ou atualização condicional. @Transactional dá atomicidade ao fluxo, mas não resolve atualizações perdidas.
- **Consequência:** duas vendas da última unidade, duas contas simultâneas da mesma mesa ou itens persistidos com total do pedido insuficiente; edição de produto pode regravar estoque antigo.
- **Cenário:** transações A/B leem estoque=1; ambas vendem uma unidade e salvam zero. Duas adições leem total=100 e gravam 150/130, embora ambas insiram itens.
- **Recomendação:** escolher controle proporcional: versionamento com tratamento de conflito, locks nos agregados críticos ou decremento condicional atômico; serializar abertura por mesa e manter ordem de locks.
- **Teste recomendado:** integração MySQL descartável com barreiras entre leituras/escritas para última unidade, dupla abertura, duas adições e adição concorrente a fechamento; conferir soma dos itens e estoque final.

### D10 — Tokens de dez anos e ausência de revogação operacional

- **Gravidade:** ALTA. **Certeza:** Confirmado.
- **Local/classe/método:** [TokenService.java:21-30](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/TokenService.java:21), gerarToken; [SecurityFilter.java:33-45](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityFilter.java:33), doFilterInternal; [Usuario.java:46-57](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Usuario.java:46).
- **Problema/evidência:** expiração em dez anos para todos os perfis, sem versão de token, revogação, bloqueio efetivo de conta ou endpoint de logout. Trocar senha isoladamente não invalida o subject assinado.
- **Consequência:** token capturado mantém acesso duradouro; tablet e gerente recebem a mesma política de validade.
- **Cenário:** remoção de acesso de um funcionário sem apagar usuário ou rotacionar o segredo global.
- **Recomendação:** validade curta especialmente para equipe, mecanismo simples de revogação por usuário e política específica para dispositivos de mesa.
- **Teste recomendado:** expiração com relógio controlado, bloqueio/revogação imediata e alteração de senha conforme política documentada.

### D11 — Status de item pode regredir e alterar pedidos já encerrados

- **Gravidade:** MÉDIA. **Certeza:** Confirmado.
- **Local/classe/método:** [PedidoService.java:141-151](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:141), atualizarStatusItem.
- **Problema/evidência:** qualquer status do enum é atribuído, sem consultar estado do pedido. Cada PRONTO redefine a conclusão; regressão para PENDENTE mantém a conclusão anterior.
- **Consequência:** histórico incoerente e métricas de preparo distorcidas. A cozinha pode alterar item de pedido FINALIZADO ou CANCELADO existente no banco.
- **Cenário:** PRONTO → PENDENTE → PRONTO muda a conclusão e o tempo medido; atualização tardia após pagamento.
- **Recomendação:** definir transições permitidas, idempotência de PRONTO e regras para pedido encerrado; se reabertura for legítima, modelar o efeito sobre conclusão.
- **Teste recomendado:** todas as transições de item, repetição de PRONTO e atualização em cada estado do pedido. Não existe rota que finalize diretamente CANCELADO: a guarda do pedido bloqueia esse caso corretamente.

### D12 — DELETE de mesa altera estado apesar de responder conflito

- **Gravidade:** MÉDIA. **Certeza:** Confirmado no caminho de DataIntegrityViolationException.
- **Local/classe/método:** [MesaController.java:59-69](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/controller/MesaController.java:59), excluir; [MesaService.java:101-118](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:101), excluir.
- **Problema/evidência:** service tenta delete/flush em transação; ao falhar, o controller inicia outra alteração para ativo=false e lança 409.
- **Consequência:** cliente recebe falha, mas recurso foi modificado; uma mesa com conta em andamento também pode ser desativada.
- **Cenário:** excluir mesa com histórico vinculado. O delete reverte, porém a desativação posterior pode confirmar separadamente.
- **Recomendação:** escolher contrato explícito: 409 sem alteração ou operação de desativação bem-sucedida com resposta coerente; concentrar decisão no service.
- **Teste recomendado:** exclusão com histórico deve verificar banco e HTTP conjuntamente; validar rollback do usuário e ausência de alteração inesperada.

### D13 — Exceções de domínio sem tradução e falhas internas convertidas em 404

- **Gravidade:** MÉDIA. **Certeza:** Confirmado na implementação; formato final depende do tratamento padrão do runtime.
- **Local/classe/método:** [ProdutoService.java:48-50](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:48), buscarPorId, e [ProdutoService.java:94-103](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:94), excluir; [MesaService.java:37-47](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:37), cadastrar; [PedidoController.java:79-84](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/controller/PedidoController.java:79), buscarPedidoAbertoDaMesa.
- **Problema/evidência:** inexistência, duplicidade, estoque insuficiente e transição inválida lançam RuntimeException sem advice/@ResponseStatus. A consulta ativa captura toda RuntimeException e responde 404.
- **Consequência:** erros de negócio viram erro de servidor; falha de banco ou do mapper pode parecer “pedido inexistente”, levando frontend a iniciar fluxo inadequado.
- **Cenário:** ID de produto inexistente; exclusão com FK; mapper lança NumberFormatException na consulta ativa.
- **Recomendação:** exceções específicas e handler global pequeno, com 400/404/409 coerentes; capturar somente ausência real; não expor detalhes internos.
- **Teste recomendado:** contrato de erros para cada caso e indisponibilidade simulada de repository retornando 5xx, sem ser mascarada como 404. Não foi comprovado vazamento de stack trace em resposta atual.

### D14 — Exclusão de usuário por nome presumido, sem vínculo com mesa

- **Gravidade:** ALTA. **Certeza:** Confirmado no algoritmo; exclusão indevida depende dos nomes existentes.
- **Local/classe/método:** [MesaService.java:54-59](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:54), cadastrar, e [MesaService.java:110-115](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:110), excluir; [DataInitializer.java:49-58](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:49), carregarUsuarios.
- **Problema/evidência:** cadastro aceita username arbitrário; exclusão calcula “mesa” + numero, sem zero à esquerda e sem checar role. Seed usa mesa01 etc.
- **Consequência:** credencial real pode permanecer ativa; usuário sem relação com a mesa pode ser apagado, inclusive de outro perfil.
- **Cenário:** excluir mesa 1 não remove mesa01; excluir mesa 12 criada com tablet12 apaga um usuário mesa12 diferente, se existir.
- **Recomendação:** associação persistida explícita e política de desativação/revogação do usuário associado; nunca inferir propriedade apenas por nome.
- **Teste recomendado:** usernames personalizados, seed com zero, usuário homônimo de outro perfil, mesa sem credencial e rollback por FK.

### D15 — Seeds recriam recursos excluídos e presumem nome único de produto

- **Gravidade:** MÉDIA. **Certeza:** Confirmado na lógica; falha por duplicidade depende dos dados.
- **Local/classe/método:** [DataInitializer.java:65-78](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:65), carregarMesas; [DataInitializer.java:228-233](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:228), carregarProdutos; [ProdutoRepository.java:21](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/repository/ProdutoRepository.java:21), findByNome; [ProdutoService.java:27-29](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:27), cadastrar.
- **Problema/evidência:** qualquer mesa padrão/produto inicial ausente reaparece no restart. API aceita nome duplicado sem unique, enquanto findByNome retorna Optional de um único resultado.
- **Consequência:** exclusões administrativas não persistem no ciclo de vida; duplicação de nome pode fazer a inicialização falhar por resultado não único.
- **Cenário:** excluir mesa 3 sem histórico e reiniciar; cadastrar segunda “Pizza Calabresa” e reiniciar.
- **Recomendação:** seeds apenas em perfil de demonstração; decidir se nome deve ser único e alinhar repository/invariantes, sem impor unicidade como regra arbitrária.
- **Teste recomendado:** restart com recursos removidos e dois produtos de mesmo nome; perfil real não deve executar carga demonstrativa.

### D16 — Dashboard mistura pedidos não pagos com faturamento e pagamentos

- **Gravidade:** MÉDIA. **Certeza:** Confirmado quanto aos filtros e à divergência; definição gerencial desejada precisa ser validada.
- **Local/classe/método:** [PedidoRepository.java:35-57](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/repository/PedidoRepository.java:35), sumFaturamentoFinalizadoByDataHoraBetween/calcularFaturamentoDoDia/agruparPorFormaPagamento; [DashboardService.java:23-47](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/DashboardService.java:23), obterResumoDoDia.
- **Problema/evidência:** faturamento do caixa soma só FINALIZADO; dashboard soma todo status diferente de CANCELADO, incluindo ABERTO. Pagamentos transformam null em DINHEIRO e agrupam pelo valor original.
- **Consequência:** receita aparenta recebida antes do pagamento; contas sem forma definida aparecem como dinheiro; grupos null e DINHEIRO podem produzir rótulos repetidos.
- **Cenário:** um pedido aberto de R$100 faz dashboard mostrar 100 e faturamento do caixa zero.
- **Recomendação:** distinguir demanda, valor a receber e receita confirmada; alinhar filtros dos indicadores relacionados; não inferir pagamento em dinheiro de valor ausente.
- **Teste recomendado:** dia com pedidos ABERTO, AGUARDANDO, FINALIZADO e CANCELADO; comparar totais e grupos null/DINHEIRO.

### D17 — Ranking de pizzas e indicador de atraso não correspondem à consulta

- **Gravidade:** MÉDIA. **Certeza:** Confirmado.
- **Local/classe/método:** [DashboardRankingsDTO.java:4-6](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/dashboarddto/DashboardRankingsDTO.java:4); [ItemPedidoRepository.java:20-24](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/repository/ItemPedidoRepository.java:20), buscarTopProdutos, e [ItemPedidoRepository.java:40-47](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/repository/ItemPedidoRepository.java:40), buscarMaioresGargalosDaCozinhaNativo; [DashboardService.java:67-85](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/DashboardService.java:67), obterRankings.
- **Problema/evidência:** topPizzas usa todos os produtos, sem filtro PIZZAS; vezesAtrasado conta todos os itens concluídos, sem comparar tempo esperado.
- **Consequência:** bebidas podem ocupar ranking anunciado como pizzas e itens dentro do prazo são contados como atraso.
- **Cenário:** refrigerante é o produto mais vendido; cem bebidas rápidas geram vezesAtrasado=100.
- **Recomendação:** adequar consulta ao significado ou renomear/documentar o indicador em mudança coordenada com frontend.
- **Teste recomendado:** mistura de categorias e itens com tempos abaixo/acima do estimado; garantir que contratos não prometam métrica não calculada.

### D18 — @Data em Pedido/ItemPedido gera métodos recursivos

- **Gravidade:** MÉDIA. **Certeza:** Provável quanto à ocorrência em runtime; estrutura geradora confirmada.
- **Local/classe/método:** [Pedido.java:9-31](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Pedido.java:9); [ItemPedido.java:8-22](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/ItemPedido.java:8); toString/equals/hashCode gerados por Lombok.
- **Problema/evidência:** Pedido.itens inclui ItemPedido.pedido; @Data inclui os campos nesses métodos, criando ciclo.
- **Consequência:** StackOverflowError ao imprimir grafo bidirecional e possíveis problemas em equals/hashCode; estado mutável também é inadequado para chaves de coleções.
- **Cenário:** log.debug de um pedido com itens chama toString; comparação de dois grafos equivalentes percorre referências.
- **Recomendação:** getters/setters separados, excluir relacionamentos de toString e adotar igualdade deliberada e estável para entidades.
- **Teste recomendado:** toString e comparação de entidades com relação bidirecional, transient/persistidas e uso em Set. DTOs impedem ciclo HTTP atual; isso não foi classificado como serialização circular efetiva da API.

### D19 — Conversão de tempo textual pode corromper valor ou impedir resposta

- **Gravidade:** MÉDIA. **Certeza:** Confirmado.
- **Local/classe/método:** [ItemPedidoMapper.java:15-27](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/mapper/ItemPedidoMapper.java:15), toResponseDTO; [DetalhesDescricaoDTO.java:5-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/DetalhesDescricaoDTO.java:5).
- **Problema/evidência:** replaceAll remove tudo que não é dígito e Integer.parseInt lê o restante, sem validação.
- **Consequência:** “20-30 min” vira 2030, “1h30” vira 130 e número longo causa NumberFormatException; um produto assim pode quebrar consultas de pedidos.
- **Cenário:** gerente cadastra tempoMedioPreparo=“999999999999 min” e o produto aparece num pedido.
- **Recomendação:** representar duração em minutos numéricos com limite; se preservar texto no contrato atual, validar formato estrito e tratar conversão.
- **Teste recomendado:** formato válido, intervalo, horas, vazio, null e overflow; resposta da cozinha não deve falhar devido a texto de catálogo.

### D20 — PUT de adição não é idempotente

- **Gravidade:** MÉDIA. **Certeza:** Confirmado.
- **Local/classe/método:** [PedidoController.java:35-41](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/controller/PedidoController.java:35), adicionarItens; [PedidoService.java:109-134](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:109).
- **Problema/evidência:** cada repetição do mesmo PUT insere novos itens, desconta estoque e aumenta total.
- **Consequência:** repetição após timeout ou política de retry pode cobrar e baixar estoque duas vezes.
- **Cenário:** frontend reenviando a mesma chamada por não ter recebido a primeira resposta.
- **Recomendação:** revisar semântica para operação de acréscimo e avaliar chave de idempotência onde houver retry; coordenar qualquer mudança contratual com frontend.
- **Teste recomendado:** repetir payload e identificador de operação e verificar efeito único conforme contrato escolhido; duas operações intencionais distintas devem funcionar.

### D21 — Configuração de banco e evolução de schema inseguras para implantação

- **Gravidade:** MÉDIA. **Certeza:** Confirmado na configuração; impacto real depende de overrides e privilégios.
- **Local/classe/método:** [application.properties:3-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/resources/application.properties:3), configuração de datasource/JPA; sem classe/método aplicável.
- **Problema/evidência:** credencial administrativa fixa (senha omitida), createDatabaseIfNotExist e ddl-auto=update sem perfis ou migrations.
- **Consequência:** segredo versionado e permissões excessivas se reutilizados; alterações de schema automáticas sem histórico reproduzível. Não foi demonstrada perda de dados já ocorrida.
- **Cenário:** aplicação inicia após alteração de entidade usando uma conta com privilégios administrativos.
- **Recomendação:** segredos externos, usuário de privilégio mínimo, perfil local separado e migrations simples para implantação; validate em runtime após migração.
- **Teste recomendado:** iniciar ambiente descartável com conta limitada e schema migrado; sem credencial deve falhar de forma explícita; validar upgrades sem dados reais.

### D22 — Cobertura automatizada não demonstra regras críticas e teste não é isolado

- **Gravidade:** MÉDIA. **Certeza:** Confirmado.
- **Local/classe/método:** [PizzariaMauaBackEndApplicationTests.java:6-11](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/test/java/inf/anibele/pizzariamauabackend/PizzariaMauaBackEndApplicationTests.java:6), contextLoads; [DataInitializer.java:22-83](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/config/DataInitializer.java:22); [application.properties:3-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/resources/application.properties:3).
- **Problema/evidência:** único teste é @SpringBootTest sem perfil/datasource isolado ou exclusão dos runners. O método não contém asserções de negócio.
- **Consequência:** regressões de autorização/estoque/estado passam despercebidas; executar “test” pode conectar no banco normal, alterar schema e inserir seeds.
- **Cenário:** executar o comando clean test sugerido no README com MySQL local de trabalho disponível.
- **Recomendação:** separar testes unitários sem contexto e integração em MySQL descartável; impedir qualquer acesso ao datasource de uso normal nos testes.
- **Teste recomendado:** verificar isolamento automaticamente e cobrir os cenários D01–D21. Não se atribui porcentagem de cobertura sem instrumento de medição.

### D23 — Campos de pagamento e data não sustentam um histórico financeiro inequívoco

- **Gravidade:** MÉDIA. **Certeza:** Confirmado no contrato; classificação por dia de recebimento precisa de confirmação do negócio.
- **Local/classe/método:** [PedidoRequestDTO.java:5-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/PedidoRequestDTO.java:5); [PedidoService.java:55-58](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:55), abrirPedido, [PedidoService.java:156-188](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:156), finalizar/confirmar; [Pedido.java:21-28](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Pedido.java:21); [PedidoRepository.java:32-38](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/repository/PedidoRepository.java:32).
- **Problema/evidência:** formaPagamento do cadastro não é utilizada; no fechamento aceita qualquer String, inclusive vazia. Só há dataHora de abertura, nunca uma data de pagamento; relatórios de finalizados filtram essa data.
- **Consequência:** divergência do contrato de entrada, grupos de pagamento livres e impossibilidade de apurar recebimento pela data real.
- **Cenário:** pedido aberto às 23h50 e pago às 00h10 é atribuído ao dia da abertura; forma “pix”, “PIX” e texto arbitrário viram grupos distintos.
- **Recomendação:** documentar quando forma de pagamento é exigida, validar conjunto permitido e registrar fechamento/pagamento se o relatório representar recebimento.
- **Teste recomendado:** payload de abertura com forma, pagamento vazio/inválido, normalização e fechamento atravessando meia-noite.

## E. Más práticas e oportunidades de melhoria

Os itens D01–D23 possuem consequências concretas ou risco fundamentado. Os pontos seguintes são recomendações proporcionais e pendências; não devem ser confundidos com defeitos comprovados em execução.

| ID / nível / certeza | Evidência e recomendação |
|---|---|
| E01 — MELHORIA / Provável | [PedidoService.java:193-218](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:193), [PedidoMapper.java:25-32](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/mapper/PedidoMapper.java:25), [ProdutoMapper.java:46-54](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/mapper/ProdutoMapper.java:46): listas percorrem relações LAZY e ManyToOne EAGER sem plano de fetch. Há risco N+1, a medir com estatísticas SQL em MySQL isolado; usar projeção/fetch específico ou batch quando houver evidência de custo. Não declarar número de consultas sem medição. |
| E02 — MELHORIA / Confirmado | ProdutoService.listarTodos/listarAtivos, MesaService.listarTodas e PedidoService.listarPedidosFinalizadosPorData retornam listas completas ([ProdutoService.java:34-44](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:34), [MesaService.java:65-74](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/MesaService.java:65), [PedidoService.java:212-218](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:212)). Paginar principalmente histórico e limitar ranking no banco em vez de buscar tudo e cortar no service ([DashboardService.java:71-73](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/DashboardService.java:71)). Baixa urgência para 10 mesas/34 produtos. |
| E03 — BAIXA / Confirmado | [SecurityConfigurations.java:67-68](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityConfigurations.java:67), securityFilterChain: regra GET cozinha duplicada; hasAnyRole acrescenta ROLE_, enquanto [Usuario.java:31-33](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Usuario.java:31) fornece nomes simples. Sem quebra comprovada nos GETs atuais, pois regras anteriores os cobrem. Remover redundância e padronizar após testes da matriz. |
| E04 — MELHORIA / A verificar | [SecurityConfigurations.java:101-109](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityConfigurations.java:101) limita origens a FRONTAPI e localhost:3000, com credentials=true; controllers usam @CrossOrigin("*"). Consolidar política e testar requisição real/preflight. Não concluir que a API aceita qualquer origem apenas pela anotação: filtro de segurança CORS também participa. Origem Vite localhost:5173 não é permitida pela lista atual se não houver override; validar a origem efetiva do frontend. |
| E05 — BAIXA / Confirmado | [pom.xml:37-51](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/pom.xml:37): starters web sobrepostos e Jackson 2 direto numa base Boot 4. A coexistência com java-jwt pode ser legítima. Revisar árvore resolvida e remover apenas dependências redundantes comprovadas; não afirmar incompatibilidade de Jackson nem falha de compilação sem resolução. |
| E06 — BAIXA / Confirmado | [ProdutoService.java:68-76](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/ProdutoService.java:68) duplica mapeamento de descrição de [ProdutoMapper.java:58-67](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/mapper/ProdutoMapper.java:58). Extrair função de atualização se reduzir divergências; não introduzir framework de mapping apenas para isso. |
| E07 — MELHORIA / Confirmado | [SecurityFilter.java:47-52](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityFilter.java:47): catch amplo transforma também erro de banco em ausência de autenticação, sem registro diagnóstico. Distinguir token inválido de falha de infraestrutura e registrar eventos sem token/senha. Usar construtores de forma consistente em security/auth como já ocorre nos services. |
| E08 — MÉDIA / A verificar | [StatusPedido.java:3-7](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/StatusPedido.java:3) contém CANCELADO, mas nenhum método o atribui. Não há cancelamento, retirada de item nem ajuste de quantidade enviada. Especificar permissão, prazo, motivo e recomposição de estoque antes de implementar; não criar DELETE para histórico financeiro. |
| E09 — MÉDIA / A verificar | [PedidoService.java:174-188](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:174) permite confirmar pagamento com itens pendentes, removendo o pedido da consulta da cozinha ([PedidoService.java:193-194](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/service/PedidoService.java:193)). Pode ser correto para pagamento antecipado, mas nesse caso a fila não deveria depender só do estado financeiro. Validar regra e testar entrega após pagamento. |
| E10 — MELHORIA / Confirmado | @ManyToOne de ItemPedido não declara nullable=false e diversos valores monetários/status não têm constraints ([ItemPedido.java:16-25](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/ItemPedido.java:16), [Pedido.java:21-28](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/model/Pedido.java:21)). Rotas atuais preenchem vínculos, então não há prova de item órfão criado normalmente. Reforçar invariantes reais no schema e definir precisão/escala após examinar DDL isolado. |
| E11 — BAIXA / Confirmado | [UsuarioMapper.java:23-29](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/mapper/UsuarioMapper.java:23) copia senha sem encode, mas o método não é usado no cadastro ativo. Documentar responsabilidade ou impedir futura persistência insegura; testar qualquer novo fluxo de usuário com PasswordEncoder. @Data de Usuario também inclui senha no toString; não foi encontrado log que a exponha atualmente. |
| E12 — MELHORIA / A verificar | [DadosLoginDTO.java:3](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/dto/DadosLoginDTO.java:3), [SecurityFilter.java:60-64](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/PizzariaMauaBackEnd/src/main/java/inf/anibele/pizzariamauabackend/security/SecurityFilter.java:60): falta validação de login e parsing estrito do prefixo Bearer. Não foi evidenciado bypass de assinatura. Validar cabeçalho e padronizar 401/403; proteção contra tentativas de senha e HTTPS podem estar no proxy, fora do escopo verificado. |

### JPA, transações e integridade: parecer específico

- PedidoService e MesaService usam transações nos fluxos mutáveis principais; misturar jakarta.transaction.Transactional e Spring @Transactional não é por si só erro.
- ProdutoService.editar/alternarStatus fazem leitura e save em transações de repository separadas. Uma fronteira no service melhora unidade de trabalho, mas **não corrige a concorrência D09 sem controle adicional**.
- Leituras e mapeamento não estão delimitados por transação readOnly. O projeto não configura open-in-view; não há prova de LazyInitializationException atual. Medir comportamento e retornar DTOs com dados carregados dentro de fronteira deliberada, sem aplicar EAGER em tudo.
- Pedido → itens com cascade ALL faz sentido na criação do agregado; não há remoção exposta que demonstre deleção acidental em cascata. Alterações futuras de cancelamento devem preservar histórico.
- Uniques de número e username cobrem corrida de cadastro no banco, embora erro de constraint precise de tradução. Produto não precisa automaticamente de nome único: decidir a regra e corrigir o seed.
- Preço histórico é preservado no item ao criar/adicionar. Alterar preçoBase não altera precoUnitario histórico. Nome, categoria e tempo exibidos em histórico vêm do Produto atual; decidir se precisam de snapshot para auditoria, sem transformar isso em requisito inventado.
- Consultas são parametrizadas com @Param; não foi encontrada concatenação de entrada em SQL/JPQL. Queries nativas de TIMESTAMPDIFF e LIMIT são compatíveis com a escolha MySQL e não precisam ser portáveis sem requisito.
- Agrupamento JPQL por pr.id selecionando nome deve ser validado no provider/SQL real; não foi afirmado defeito de query sem iniciar contexto isolado.

### Compatibilidade com React/Vite/TypeScript

Não há código frontend neste workspace; integração ponta a ponta não foi verificada. A referência externa do README não foi tratada como especificação definitiva. Os contratos encontrados exigem:

1. Login usa **senha**; cadastro opcional de usuário de mesa usa **password**.
2. Mesa é endereçada pelo **numero**; produto, pedido e item pelo **id**. Não intercambiar IDs.
3. Adicionar itens recebe array na raiz; abertura recebe objeto com numeroMesa/itens.
4. Finalização recebe formaPagamento como query parameter; alterações de status usam nomes exatos dos enums.
5. Várias mutações retornam 204 sem corpo; frontend não deve exigir JSON.
6. GET de pedido ativo pode retornar 404 sem corpo. O catch amplo atual pode esconder erro operacional (D13).
7. DELETE de mesa pode retornar 409 após alterar ativo (D12), deixando cache de interface desatualizado.
8. Prefixo /api existe só no dashboard; URLs e CORS devem corresponder à origem efetiva.
9. BigDecimal representa valor exato no Java; revisar contrato numérico TypeScript e arredondamento da exibição, sem afirmar perda no backend.
10. PUT de produto é substituição completa: campo omitido pode virar null/false; não tratá-lo como PATCH.
11. formaPagamento na abertura é ignorada; topPizzas/vezesAtrasado não correspondem às consultas atuais (D17/D23).

### Dependências e avisos de segurança

Consulta pontual a fontes primárias em 17/11/2026, sem scanner instalado/resolvido:

- O fornecedor publicou correção de segurança em java-jwt 4.6.2 para exceção de data no PayloadDeserializer. O projeto fixa 4.4.0. Isso justifica revisão/atualização e teste de tokens malformados, **mas não comprova exploração aqui**; SecurityFilter captura Exception. [Release oficial](https://github.com/auth0/java-jwt/releases/tag/4.6.2).
- O BOM local indica Spring Security 7.1.0. O aviso CVE-2026-41707 inclui essa versão, mas trata de DPoPProofJwtDecoderFactory. O código usa Auth0 HMAC próprio e não demonstrou uso de DPoP; portanto não classificado como vulnerabilidade explorável do projeto. [Aviso oficial](https://spring.io/security/cve-2026-41707/).
- Boot 4 usa Jackson 3 como preferência e mantém suporte transitório a Jackson 2. A presença dos dois namespaces exige inspecionar dependências; não é prova automática de falha. [Documentação oficial](https://docs.spring.io/spring-boot/reference/features/json.html).
- Não foi produzida SBOM, árvore efetiva nem varredura completa de CVEs transitivas. Não se declara o projeto livre de vulnerabilidades por essa consulta limitada.

CSRF desativado é coerente com autenticação stateless por Bearer explícito encontrada. Não há evidência de autenticação automática por cookie; não foi classificado como vulnerabilidade. Não existe endpoint administrativo público identificado além dos riscos de credenciais/segredo padrão. Também não foi encontrada rota de cadastro público que permita escolher GERENTE.

## F. Testes e execução

| Verificação | Resultado | Alcance / impedimento |
|---|---|---|
| Inventário e leitura estática | Executada | 54 Java main, 1 teste, configuração, repositories, DTOs, mappers, README, SQL e auxiliares |
| git status --short | Passou | Árvore inicialmente limpa; rechecagem sem mudanças no código |
| git diff --check | Passou | Nenhum diff de fonte introduzido; não é análise estática Java |
| java -version | Executada | JDK 23.0.2 disponível; alvo do POM é 17 |
| mvn -version pelo PATH | Falhou | Comando mvn não encontrado |
| Maven local 3.9.16 -o -B -ntp validate | Falhou | Parent 4.1.0 não resolvido pelo resolver offline para central; não chegou à compilação |
| Parse XML do POM em memória | Passou | XML bem formado; não prova validade do modelo Maven ou classpath |
| Inspeção do cache do parent/BOM | Executada | Arquivos existem no cache do usuário, apesar de o resolver usado informar artifact absent; causa ambiental não foi determinada |
| Compilação Java / empacotamento | Não realizada | Modelo Maven não resolvido; compile também criaria arquivos target, além do único relatório autorizado |
| contextLoads / integração | Não executados por segurança | @SpringBootTest usa datasource normal e runners de escrita, sem isolamento |
| Testes unitários de negócio | Ausentes | Nenhum suite de CRUD, permissão, estoque ou estado encontrada |
| HTTP/MockMvc em execução | Não realizado | Exigiria classpath/contexto isolado e artefatos adicionais |
| Análise estática automatizada Java | Não realizada | Nenhum Checkstyle/PMD/SpotBugs configurado; revisão estática manual executada |
| CVEs | Consulta primária limitada | Sem dependency tree/SBOM resolvida ou scanner; aplicabilidade separada de versão |
| Banco real e SQL de carga | Não acessados/executados | Nenhuma operação persistente autorizada |

O validate foi chamado pelo binário já existente:
C:/Users/gusta/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn.cmd
com os argumentos -o -B -ntp validate, no diretório PizzariaMauaBackEnd. Não foi usado clean, iniciado servidor, baixada dependência, alterado cache propositalmente ou executado script SQL. A falha offline **não prova que Spring Boot 4.1.0 inexista ou que o projeto não compile em ambiente configurado**.

### Validação da carga SQL, somente por parsing em memória

Todas as 401 linhas foram processadas sem executar SQL:

- 101 INSERTs de pedidos e 297 INSERTs de itens.
- Nenhuma divergência entre faturamento dos pedidos e soma de preço_unitario × quantidade.
- Nenhum item referenciando pedido ausente do próprio script.
- Nenhuma quantidade não positiva na amostra.
- As 297 inclusões de item antecedem data_hora do respectivo pedido. Isso contrasta com a abertura antes da inclusão no fluxo Java e sugere que a massa usa data_hora como encerramento; não prova corrupção do banco, mas impede tratar a carga como validação fiel da semântica temporal atual.
- IDs de pedidos, itens, produtos e mesas são fixos ([historico_junho_2026.sql:3-9](C:/Users/gusta/OneDrive/Documentos/GitHub/Pizzaria_BackEnd-SpringBootJPA-/Dados_SQL/historico_junho_2026.sql:3)); reexecução pode colidir com dados existentes, e relações com catálogo dependem de IDs previamente compatíveis. Não é uma migration idempotente. Aplicar somente a banco descartável preparado explicitamente.
- O script termina em COMMIT (linha 401). Nenhum comando dele foi enviado ao MySQL.

A demonstração estoque=10, quantidade=-2 → estoque=12 e subtotal negativo foi aritmética em memória, **não teste executando PedidoService**. A transformação de tempo textual também foi reproduzida em memória; overflow Java foi identificado pela chamada Integer.parseInt sem tratamento.

### Testes adicionais prioritários

1. Unitários dos services com repositories simulados: negativos/nulos, existência, ativo, estoque, soma, preço histórico e rollback lógico esperado.
2. MockMvc com todos os perfis, anônimo, literal/coringa, mesa proprietária e outra mesa; tokens inválidos/expirados e conta revogada.
3. Integração com MySQL descartável: FKs de exclusão, cascatas, rollback após segundo item inválido, constraints e consultas agregadas.
4. Concorrência com duas transações sincronizadas: última unidade, duas contas da mesma mesa, duas adições e fechar/adicionar simultaneamente.
5. Estados: abertura durante pagamento, repetição de confirmação, item pronto repetido/regressivo, cancelamento se especificado e pagamento antecipado.
6. Contratos: 201/200/204, 400/404/409/401/403, corpo de erro, array de itens, substituição de produto e retry.
7. Métricas: dia vazio, pedidos abertos versus pagos, dinheiro/null, mudança de dia, categorias, tempos e alteração do catálogo.
8. Inicialização: perfil de implantação sem seeds, nomes duplicados, exclusões seguidas de restart e isolamento obrigatório dos testes.

## G. Plano de correção priorizado

As complexidades são relativas e não são estimativas de tempo. Toda implementação depende de etapa futura autorizada.

### Fase 1 — Correções essenciais

| Trabalho | IDs | Complexidade |
|---|---|---|
| Exigir segredo JWT externo e rotacionar credenciais/segredo usados | D01, D02 | Baixa |
| Desabilitar seeds fora do perfil demonstrativo | D02, D15 | Baixa |
| Restringir ativação e corrigir precedência dos matchers | D04 | Baixa |
| Associar usuário à mesa e autorizar recurso individual | D03, D14 | Média |
| Rejeitar quantidade inválida e campos indispensáveis | D05, D06 | Baixa |
| Unificar pedido ativo e impedir abertura durante pagamento | D07, D08 | Média |
| Introduzir controle concorrente nos agregados críticos | D09 | Alta |
| Reduzir validade de token e permitir revogação por usuário | D10 | Média |
| Proteger a verificação com testes unitários/web isolados | D22 | Média |

### Fase 2 — Consistência

| Trabalho | IDs | Complexidade |
|---|---|---|
| Definir transições e idempotência de status dos itens | D11, E09 | Média |
| Tornar exclusão/desativação de mesa uma operação com resposta coerente | D12, D14 | Média |
| Padronizar exceções e contrato de erro | D13 | Média |
| Validar preço, estoque, duração e credenciais condicionais | D06, D19 | Média |
| Revisar semântica/retry da adição de itens com frontend | D20 | Média |
| Definir pagamento, datas e métricas financeiras | D16, D17, D23 | Média |
| Especificar cancelamento, correção de itens e recomposição de estoque | E08 | Média |
| Separar configuração local e implantar migrações simples | D21 | Média |
| Viabilizar build reproduzível e integração MySQL descartável | D22 | Média |

### Fase 3 — Qualidade

| Trabalho | IDs | Complexidade |
|---|---|---|
| Retirar relações dos métodos gerados inadequados das entidades | D18 | Baixa |
| Consolidar mappers e injeção por construtor | E06, E07 | Baixa |
| Revisar CORS e dependências redundantes com classpath resolvido | E04, E05 | Baixa |
| Medir queries, ajustar fetch e paginar histórico | E01, E02 | Média |
| Revisar constraints, precisão/escala e snapshots necessários | E10 | Média |
| Atualizar dependências com avaliação de avisos aplicáveis | Seção de dependências | Média |
| Ampliar regressão, CI e diagnóstico sem segredos em logs | D22, E07 | Média |

## H. Avaliação final

1. **O backend está funcionalmente coerente?** Parcialmente. O caminho feliz existe e as duas transições financeiras têm guardas, mas conta ativa, disponibilidade, autorização por recurso e estado da cozinha têm inconsistências demonstradas.
2. **Os CRUDs estão completos para as necessidades do sistema?** Produto tem CRUD completo em superfície; mesa cobre operações operacionais, com exclusão problemática. Pedidos e itens têm fluxos parciais, e usuários não têm gestão exposta. Não se exige DELETE de pedidos ou CRUD de enum. A completude de cancelamento/correções precisa de regra explícita.
3. **Existem falhas de segurança relevantes?** Sim: segredo e contas padrão, acesso entre mesas, ativação sem permissão específica e tokens de dez anos. A exploração dos padrões depende da configuração/credenciais reais, não inspecionadas.
4. **Existem riscos de inconsistência no banco?** Sim: quantidade negativa, segundo pedido durante pagamento, concorrência sem controle e associação de usuário inferida pelo nome. Há também estados divergentes entre mesa/pedido/item.
5. **Está adequado para portfólio profissional?** A organização e o domínio são uma base aproveitável. Recomenda-se corrigir os achados essenciais e demonstrar testes antes de apresentá-lo como backend seguro ou pronto para produção. Como projeto em evolução, pode ser apresentado com limitações claras.
6. **O que priorizar antes de implantar?** Eliminar padrões inseguros, restringir perfis e titularidade, validar entradas, garantir uma conta ativa por mesa, proteger estoque/total contra concorrência, isolar testes e padronizar erros. Depois consolidar histórico financeiro, configuração e observabilidade.

O resultado desta etapa é exclusivamente este relatório. Código-fonte, dependências, configuração, schema e dados não foram corrigidos, e nenhum commit ou push foi realizado.


## Encerramento da auditoria — 18/11/2026

Revisão do código a partir de `b3dd85a`, sem acesso ao MySQL original ou alterações no React. O diagnóstico original acima não descreve o estado corrigido atual. Java disponível 23; o POM continua com alvo Java 17 e Spring Boot 4.1.0, sem mudança de versão nesta rodada.

| ID | Estado atual | Evidência e prioridade residual |
|---|---|---|
| D01 | CORRIGIDO | TokenService exige chave externa Base64 forte, sem fallback conhecido; testes de ausência, assinatura, issuer, claims e datas inválidas. Nesta revisão, datas JWT fora do intervalo passam a 401 em vez de DateTimeException não tratada. |
| D02 | CORRIGIDO COM RESSALVAS | DataInitializer restrito a `demo & !prod`; contas existentes não são alteradas. Credenciais acadêmicas conhecidas continuam inadequadas para exposição pública; preparação de credenciais reais é responsabilidade da implantação. |
| D03 | CORRIGIDO | Usuario.mesa persistido; AutorizacaoMesa compara ID/número e Services verificam acesso antes de leitura/mutação/replay. AutorizacaoPedidosTest e MesaPermissoesTest cobrem as matrizes. |
| D04 | CORRIGIDO | SecurityConfigurations ordena matcher literal de status antes de wildcard e restringe ativação ao GERENTE; testes web por perfil. |
| D05 | CORRIGIDO | Quantidade inteira positiva, itens/IDs validados no Service, rollback e estoque testados, inclusive produtos repetidos e concorrência MySQL. |
| D06 | CORRIGIDO | DTOs com Bean Validation e Controllers @Valid; validações condicionais de mesa e credencial. Acrescentado @Digits para impedir preço com frações menores que centavo ou acima do DECIMAL(38,2), antes da gravação. |
| D07 | CORRIGIDO | ABERTO e AGUARDANDO_PAGAMENTO são ativos; conta adicional e múltiplos ativos legados retornam conflito, sem escolha arbitrária. EstadosPedidosJpaTest/ConcorrenciaMysqlTest. |
| D08 | CORRIGIDO | Abertura exige mesa ativa/LIVRE; administração rejeita desativação/status incompatível com pedido ativo. Validações precedem alterações. |
| D09 | CORRIGIDO COM RESSALVAS | Transações READ COMMITTED e locks Mesa → Pedido/Item → Produtos em ID crescente; concorrência real e rollback testados. PUT com estoque absoluto deliberadamente baseado em tela antiga ainda exigiria ETag/versão de contrato para detectar a intenção obsoleta do cliente; não é atualização perdida dentro da transação protegida. |
| D10 | CORRIGIDO COM RESSALVAS | Validades configuráveis 8h/equipe e 7d/MESA; filtro relê usuário e permissões atuais. Exclusão do usuário impede novo uso do token, mudança de perfil reflete imediatamente. Revogação individual persistente continua pendência importante, dependente de schema/gestão de conta; logout local e troca de senha não revogam o JWT. |
| D11 | CORRIGIDO | Transições válidas, repetição sem efeito, PRONTO preserva conclusão; encerrados bloqueiam preparo, aguardando pagamento permite. Salto PENDENTE→PRONTO preserva o React. Testes ItensStatusHttpTest/ConcorrenciaMysqlTest. |
| D12 | CORRIGIDO | Exclusão com dependências retorna 409 sem desativar ou remover credencial; decisão e rollback no MesaService, coordenados pelo lock da mesa. |
| D13 | CORRIGIDO | Exceções específicas e ApiExceptionHandler para erros operacionais; consulta ativa captura somente recurso ausente, mantendo falhas internas como 5xx. ValidacaoFase2Test e contratos HTTP. |
| D14 | CORRIGIDO | Exclusão usa UsuarioRepository.findByMesaId, nunca username presumido; verifica perfil e preserva rollback/FKs. ProtecaoVinculoMesaTest/VinculoMesaJpaTest. |
| D15 | CORRIGIDO | Runner único só inicia o conjunto demo se todas as tabelas de negócio estiverem vazias; base parcial é preservada, sem recriações. Helpers não são runners autônomos; duplicidade histórica de nome não aciona seed. DataInitializerFase3BTest. |
| D16 | CORRIGIDO | Soma e contagem de FINALIZADOS no mesmo SELECT; ticket BigDecimal, sem multiplicação por itens; pendências fora da receita. FinanceiroMysqlTest/DashboardFinanceiroTest. |
| D17 | PENDENTE IMPORTANTE | Ranking PIZZAS corrigido, desempate por ID, limite cinco e quantidades sem join de ingredientes. Porém `vezesAtrasado` é COUNT de concluídos e não uma contagem confiável de atrasos; exige definição/contrato coordenados, sem prazo inventado nesta revisão. Não impede abertura, preparo, cobrança ou pagamento; não usar como medida de atraso. |
| D18 | CORRIGIDO | Pedido/ItemPedido não usam mais @Data: identidade por ID não nulo, hash estável, toString apenas ID, sem percorrer relações. Testes de ciclos/transientes/Set e proxies MySQL. Usuario exclui senha de logs/igualdade/JSON; Controllers continuam retornando DTOs. |
| D19 | CORRIGIDO | TempoPreparo aceita formato estrito de minutos inteiros e trata overflow; DTO valida escrita e mapper tolera legado incompatível sem quebrar leitura. TempoPreparoTest. |
| D20 | CORRIGIDO COM RESSALVAS | Escopo usuário/pedido/UUID, hash do payload e resposta original persistidos atomicamente com a adição; autorização e locks antes do replay, inclusive após fechamento. IdempotenciaHttpMysqlTest. Sem cabeçalho continua legado; não há limpeza automática/retentiva implementada, evitando reexecução tardia por remoção precoce. |
| D21 | CORRIGIDO COM RESSALVAS | validate padrão/prod; seeds fora de implantação; removidas credenciais fixas também do arquivo padrão. DB_USERNAME e DB_PASSWORD obrigatórios externamente; DB_URL obrigatório no prod, URL local padrão preservada. O segredo antigo permanece no histórico Git: rotação operacional recomendada, sem alterar usuários/servidor nesta rodada. |
| D22 | CORRIGIDO | Contexto Boot com H2 explícito e segredo sintético; MySQL opt-in com guarda de versão/porta/schema/UUID/datadir, instância nova e dados sintéticos. Regressão completa, incluindo transações, concorrência e idempotência; nenhum teste usa o banco original. |
| D23 | CORRIGIDO COM RESSALVAS | dataPagamentoUtc gravada uma vez na confirmação, UTC/microssegundos e mesma transação; dias São Paulo convertidos para intervalos UTC semiabertos. Históricos NULL não recebem datas fictícias; falta somente apresentação específica de valores sem período conhecido, limitação informativa sem endpoint novo. |

### Ajustes essenciais desta rodada

1. D18: eliminada recursão reproduzível de Pedido/ItemPedido e exposição de credencial por métodos gerados de Usuario, sem alterar mapeamento/schema ou DTOs.
2. D01/D10: JWT com `exp`, `iat` ou `nbf` fora do intervalo era capaz de produzir DateTimeException antes da validação terminar, inclusive com assinatura incorreta. Agora é normalizado para JWTVerificationException e 401, sem consulta ao banco. Falhas de infraestrutura continuam sem ser mascaradas.
3. D06: preços como 1.005 eram aceitos antes de chegar ao DECIMAL com duas casas; agora retornam 400 com mensagem antes do Service, evitando arredondamento silencioso. Preços válidos e contratos de sucesso não mudam.
4. D21: arquivo padrão deixa de conter usuário/senha fixos. Para iniciar localmente, configurar DB_USERNAME e DB_PASSWORD na sessão/IDE, além de API_SECURITY_TOKEN_SECRET. Perfil prod já exigia essas variáveis; nenhuma credencial ou permissão real foi alterada.

### Ressalvas de entrega

- Não há estado persistido de bloqueio/versão de autenticação em Usuario. isEnabled permanece true; `mesa.ativo` significa disponibilidade operacional e não foi reinterpretado como conta revogada. Uma mesa inativa não abre pedidos, mas isso não é revogação individual de token. Logout no cliente não invalida token em outros dispositivos; rotação global do segredo é distinta de revogação por usuário.
- O catálogo agora valida minutos no formato estrito, mas tempo médio de preparo não estabelece, por si só, uma política de atraso; o item não preserva estimativa histórica e uma edição do catálogo pode alterar a referência. Não se redefiniu `vezesAtrasado`, nem se removeu campo utilizado pelo React ou se substituiu a contagem por zero arbitrário. Exige decisão de negócio e apresentação coordenada; pendência importante restrita ao indicador.
- Pagamento antecipado com itens ainda em preparo depende de política: FINALIZADO sai da fila e bloqueia novas transições. O fluxo normal de encerramento após preparo permanece; não foi inventada uma política diferente nesta rodada.
- Credenciais demonstrativas existentes são decisão acadêmica preservada; não se afirma prontidão para exposição pública com essas contas. Configurar credenciais adequadas, segredo forte e conta de banco com privilégios mínimos na implantação. Valores anteriormente versionados devem ser rotacionados operacionalmente, sem reescrever o histórico Git nesta tarefa.
- Sem chave idempotente, clientes legados continuam sujeitos a repetição de adição. Retenção/limpeza, revogação persistente, ETag de estoque e apresentação do histórico sem data são trabalhos coordenados futuros, não correções artificiais com memória ou datas inventadas.

Veredito de escopo: **PRONTO COM RESSALVAS para integração e operação principal dos tablets/funcionários em ambiente controlado**, condicionado à configuração externa de segredos/credenciais e à comunicação das limitações acima. Não é uma certificação de segurança absoluta nem validação de exposição pública. Nenhum defeito transacional ou de acesso entre mesas conhecido foi deixado sem correção; não foram criados novos endpoints, dependências, migrations ou fases.


### Resultado da execução de encerramento

**641 testes Java aprovados, zero falhas, zero erros e zero ignorados**. Preservados os 626 anteriores e acrescentados 15: oito de identidade/segurança de entidades, três web de preço, três de JWT com datas extremas e um MySQL de proxies destacados. Total MySQL **152** (71 de concorrência, 30 de idempotência HTTP, 38 financeiros/JPA e 13 de ranking), em instância independente MySQL 8.0.42. Os 53 testes direcionados também passaram antes da regressão. Compilação e validação Hibernate contra o schema isolado aprovadas; não se mediu percentual de cobertura nem se executou novo E2E React.

A instância descartável foi encerrada pelo executor; nenhum acesso ao MySQL original, migration ou alteração de dados reais ocorreu. Configurações permanentes de teste não foram criadas; logs e classes geradas permanecem em target, sem versionamento. Frontend, JWT de produção, credenciais reais e configurações do servidor não foram modificados. O valor antigo de senha local citado no achado original D21 foi omitido deste relatório ao versioná-lo; o achado foi preservado.
