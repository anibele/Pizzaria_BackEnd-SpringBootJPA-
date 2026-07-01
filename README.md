# Pizzaria Mauá Back-End

Back-end REST da aplicação de pizzaria desenvolvido com Spring Boot, Spring Data JPA, Spring Security, JWT e MySQL.

O projeto concentra toda a regra de negócio da operação da pizzaria: autenticação, controle de mesas, catálogo de produtos, abertura e acompanhamento de pedidos, fechamento de contas e indicadores de dashboard.

* Frontend da pizzaria, disponível em [Frontend Pizzaria Mauá](https://github.com/anibele/Pizzaria_FrontEnd-React-Vite-Axios).
* Este projeto ainda está em desenvolvimento e pode sofrer alterações. Sugestões e contribuições são bem-vindas.

## Visão geral

Este repositório contém a API responsável por atender o front-end do sistema, oferecendo endpoints para:

- autenticação baseada em JWT;
- cadastro, consulta, atualização e remoção de produtos;
- gestão de mesas e seus status operacionais;
- criação e acompanhamento de pedidos;
- atualização individual de itens de pedido pela cozinha;
- confirmação de pagamento e encerramento de mesa;
- geração de métricas para o painel gerencial.

O código principal está dentro da pasta `PizzariaMauaBackEnd/`.

## Tecnologias utilizadas

- Java 17
- Spring Boot 4.1.0
- Spring Web
- Spring Data JPA
- Spring Security
- JWT com `java-jwt`
- MySQL
- Lombok

## Estrutura do projeto

```text
Pizzaria_BackEnd-SpringBootJPA-
├─ Dados_SQL/
│  └─ historico_junho_2026.sql
├─ PizzariaMauaBackEnd/
│  ├─ src/main/java/inf/anibele/pizzariamauabackend/
│  │  ├─ config/        # Inicialização de dados e configuração da aplicação
│  │  ├─ controller/    # Endpoints REST
│  │  ├─ dto/           # Objetos de entrada e saída da API
│  │  ├─ mapper/        # Conversão entre entidades e DTOs
│  │  ├─ model/         # Entidades JPA e enums de domínio
│  │  ├─ repository/    # Interfaces de persistência
│  │  ├─ security/      # JWT, filtro e regras de segurança
│  │  └─ service/       # Regras de negócio
│  └─ src/main/resources/
│     └─ application.properties
└─ README.md
```

## Arquitetura

O projeto segue uma arquitetura em camadas, separando responsabilidades de forma clara:

1. `controller`
   - recebe as requisições HTTP;
   - valida a entrada no nível do endpoint;
   - devolve respostas HTTP com `ResponseEntity`.

2. `service`
   - concentra as regras de negócio;
   - aplica validações, transições de status e tratamentos de erro;
   - coordena o fluxo entre controllers, repositórios e mappers.

3. `repository`
   - acessa o banco de dados via Spring Data JPA;
   - executa consultas derivadas e queries customizadas para relatórios e dashboard.

4. `model`
   - representa as entidades persistidas;
   - contém os enums de domínio, como `RoleName`, `StatusMesa`, `StatusPedido` e `StatusItemPedido`.

5. `dto`
   - define contratos de entrada e saída da API;
   - evita expor diretamente as entidades JPA ao front-end.

6. `mapper`
   - transforma entidades em DTOs e vice-versa;
   - padroniza a saída de dados da API.

7. `security`
   - realiza autenticação com JWT;
   - aplica autorização por perfis;
   - intercepta requisições com filtro de segurança.

## Como a aplicação funciona

### Fluxo geral de requisição

1. O front-end faz login em `POST /auth/login`.
2. A API autentica o usuário e gera um token JWT.
3. O token é enviado nas próximas requisições no cabeçalho `Authorization: Bearer <token>`.
4. O `SecurityFilter` valida o token e carrega o usuário autenticado.
5. O controller chama o service responsável pela regra de negócio.
6. O service consulta ou altera dados via repository.
7. A resposta é convertida em DTO e retornada ao cliente.

### Fluxo de pedidos

- a mesa autentica ou usa o acesso permitido pela role `MESA`;
- o pedido é aberto com o estado `ABERTO`;
- itens podem ser adicionados enquanto o pedido está ativo;
- a cozinha atualiza o status de cada item para `PENDENTE`, `EM_PREPARO` ou `PRONTO`;
- quando o cliente finaliza, o pedido entra em `AGUARDANDO_PAGAMENTO`;
- o caixa ou gerente confirma o pagamento, encerrando o pedido e liberando a mesa.

## Perfis de acesso

O sistema trabalha com quatro perfis principais:

| Perfil | Responsabilidade |
| --- | --- |
| `GERENTE` | Acesso completo aos cadastros, dashboard e operações administrativas |
| `COZINHA` | Acompanhamento e atualização dos itens em preparo |
| `CAIXA` | Confirmação de pagamento, consulta operacional e fechamento de mesa |
| `MESA` | Acesso do tablet/cardápio para abertura e acompanhamento de pedidos |

As permissões são aplicadas na configuração de segurança do Spring.

## Segurança

### Autenticação

- A autenticação é baseada em JWT.
- O login é realizado em `POST /auth/login`.
- A resposta do login inclui:
  - token JWT;
  - username;
  - role do usuário.

### Autorização

As rotas são protegidas por perfil. Em linhas gerais:

- rota pública: `POST /auth/login`;
- cozinha: leitura do fluxo operacional e atualização de itens;
- mesa: acesso ao cardápio ativo, pedidos e finalização;
- caixa: confirmação de pagamento, relatórios operacionais e consultas de fechamento;
- gerente: acesso total aos cadastros, dashboard e rotinas administrativas.

### Implementação de segurança

- `SecurityConfigurations` define `SecurityFilterChain`, CORS e `PasswordEncoder`.
- `SecurityFilter` intercepta cada requisição para validar o token.
- `TokenService` gera e valida o JWT usando HMAC.
- As senhas são armazenadas com `BCryptPasswordEncoder`.

### Observações de segurança

Para uso em produção, recomenda-se:

- mover credenciais de banco de dados para variáveis de ambiente;
- definir um segredo forte para o JWT;
- restringir origens de CORS apenas ao front-end oficial;
- revisar credenciais padrão geradas pelo inicializador de dados.

## Banco de dados

O projeto utiliza MySQL. A configuração principal está em `PizzariaMauaBackEnd/src/main/resources/application.properties`.

Configuração atual de desenvolvimento:

- banco: `pizzamaua`
- usuário: `root`
- senha: `mysql`
- estratégia JPA: `update`

### Inicialização automática

O componente `DataInitializer` cria ou garante a presença de dados básicos ao subir a aplicação:

- usuários padrão:
  - `gerente1`
  - `cozinha1`
  - `caixa1`
  - `mesa01` até `mesa10`
- mesas de 1 a 10;
- catálogo inicial com pizzas, sobremesas, acompanhamentos e bebidas.

### Histórico de vendas

A pasta `Dados_SQL/` contém o arquivo `historico_junho_2026.sql`, que pode ser usado para popular o banco com um histórico realista de pedidos finalizados e itens associados.

Esse arquivo é útil para:

- testar dashboards;
- validar relatórios;
- simular movimentos de venda e gargalos de cozinha.

## Requisitos para execução

- Java 17
- Maven Wrapper disponível no projeto
- MySQL 8 ou superior
- Banco de dados local acessível na porta padrão `3306`

## Como executar

### 1. Clonar o repositório

```powershell
git clone <url-do-repositorio>
cd Pizzaria_BackEnd-SpringBootJPA-\PizzariaMauaBackEnd
```

### 2. Preparar o banco de dados

Crie o banco `pizzamaua` no MySQL, caso ele ainda não exista.

Se desejar carregar o histórico de pedidos, importe o arquivo `Dados_SQL/historico_junho_2026.sql` no seu cliente MySQL e execute o comando `SOURCE Dados_SQL/historico_junho_2026.sql;`.

### 3. Ajustar as credenciais, se necessário

Edite `src/main/resources/application.properties` se seu MySQL usar usuário, senha ou host diferentes.

### 4. Iniciar a aplicação

```powershell
.\mvnw.cmd spring-boot:run
```

Se preferir, também é possível compilar e executar o teste padrão com:

```powershell
.\mvnw.cmd clean test
```

### 5. Acessar a API

Por padrão, a aplicação sobe na porta `8080`, salvo configuração diferente no ambiente.

## Credenciais padrão de desenvolvimento

Os usuários abaixo são criados automaticamente na inicialização, caso ainda não existam:

| Usuário | Senha | Perfil |
| --- | --- | --- |
| `gerente1` | `gerente1` | `GERENTE` |
| `cozinha1` | `cozinha1` | `COZINHA` |
| `caixa1` | `caixa1` | `CAIXA` |
| `mesa01` a `mesa10` | igual ao username | `MESA` |

Essas credenciais servem apenas para ambiente local e de testes.

## Principais endpoints

### Autenticação

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/auth/login` | Autentica o usuário e retorna o JWT |

### Produtos

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/produtos` | Cria um produto |
| `GET` | `/produtos` | Lista todos os produtos |
| `GET` | `/produtos/ativos` | Lista apenas os produtos ativos |
| `GET` | `/produtos/{id}` | Busca um produto por ID |
| `PUT` | `/produtos/{id}` | Atualiza um produto |
| `PATCH` | `/produtos/{id}/status?ativo=true|false` | Ativa ou desativa um produto |
| `DELETE` | `/produtos/{id}` | Remove um produto |

### Mesas

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/mesas` | Cadastra uma mesa |
| `GET` | `/mesas` | Lista todas as mesas |
| `GET` | `/mesas/status?status=LIVRE` | Lista mesas por status |
| `GET` | `/mesas/{numero}` | Busca mesa pelo número |
| `PATCH` | `/mesas/{numero}/status?status=OCUPADA` | Atualiza o status da mesa |
| `PATCH` | `/mesas/{numero}/ativacao?ativo=true|false` | Ativa ou desativa a mesa |
| `DELETE` | `/mesas/{numero}` | Remove a mesa, com tratamento de conflito quando há pedidos associados |

### Pedidos

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/pedidos` | Abre um novo pedido |
| `PUT` | `/pedidos/{id}/itens` | Adiciona itens a um pedido existente |
| `PATCH` | `/pedidos/itens/{itemId}/status?status=PRONTO` | Atualiza o status de um item |
| `PATCH` | `/pedidos/{id}/finalizar?formaPagamento=PIX` | Finaliza o pedido e informa a forma de pagamento |
| `PATCH` | `/pedidos/{id}/confirmar` | Confirma o pagamento e encerra a mesa |
| `GET` | `/pedidos/cozinha` | Lista pedidos ativos para a cozinha |
| `GET` | `/pedidos/mesa/{numero}/aberto` | Busca o pedido aberto de uma mesa |
| `GET` | `/pedidos/finalizados?data=2026-06-29` | Lista pedidos finalizados em uma data |
| `GET` | `/pedidos/faturamento?data=2026-06-29` | Retorna o faturamento de uma data |

### Dashboard

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `GET` | `/api/dashboard/resumo` | Resumo do dia |
| `GET` | `/api/dashboard/vendas-hora` | Vendas por hora |
| `GET` | `/api/dashboard/pagamentos` | Formas de pagamento |
| `GET` | `/api/dashboard/rankings` | Rankings gerenciais |

## Observações importantes de implementação

- As entidades usam JPA e Lombok para reduzir boilerplate.
- Os DTOs evitam vazamento de campos sensíveis, como senha do usuário.
- O relacionamento entre `Pedido` e `ItemPedido` permite histórico detalhado de consumo.
- O `ItemPedido` registra data de inclusão e conclusão, permitindo métricas de cozinha.
- O `DashboardController` depende de consultas agregadas nos repositórios para montar os indicadores.

## Consultas e métricas disponíveis

O sistema já possui suporte para métricas como:

- faturamento diário;
- total de pedidos do dia;
- distribuição por forma de pagamento;
- vendas por hora;
- top produtos mais vendidos;
- pedidos com bebidas;
- tempo médio de cozinha;
- principais gargalos de preparo.

## Front-end

Este repositório expõe apenas o back-end. O front-end deve consumir os endpoints via HTTP, enviando o token JWT nas requisições autenticadas.
* Frontend da pizzaria, disponível em [Frontend Pizzaria Mauá](https://github.com/anibele/Pizzaria_FrontEnd-React-Vite-Axios).

## Autor e informações adicionais

Projeto frontend desenvolvido para a **Pizzaria Mauá** na Disciplina de Programação para a Web do curso de Sistemas de Informação da [UFN](https://ufn.edu.br/).
Feito por [Gustavo Anibele](https://github.com/anibele), com supervisão do professor [Herysson Figueiredo](https://github.com/herysson).
Qualquer dúvida ou sugestão, [entre em contato por e-mail](mailto:gustavoanibele@gmail.com).