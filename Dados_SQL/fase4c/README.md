# Fase 4C — itens, exclusão e proposta de idempotência

## D11 — preparação

O fluxo aceito é PENDENTE → EM_PREPARO → PRONTO, com PENDENTE → PRONTO preservado por compatibilidade. O frontend local `pizzaria_maua_frontend/src/routes/PedidosCozinha.tsx`, método `handleMarcarComoEntregue`, envia PRONTO diretamente (linha 83 na inspeção desta fase). Nenhum arquivo React foi alterado.

Em pedidos ABERTO ou AGUARDANDO_PAGAMENTO, repetir o estado atual retorna 204 sem salvar nem alterar conclusão. Regressões retornam 409. Entrar em PRONTO registra a conclusão uma única vez. FINALIZADO/CANCELADO retornam 409, inclusive em repetições; status desconhecido/ausente retorna 400 e item inexistente retorna 404. O corpo de erros tratados mantém `mensagem`.

Itens sem pedido e dados legados incoerentes são conflitos, não são reparados automaticamente. Uma conclusão já existente em item não concluído bloqueia novas transições; repetir seu estado não modifica o histórico. Um item legado já PRONTO com conclusão ausente continua sem conclusão ao repetir PRONTO: esta fase não inventa uma data histórica.

Mantêm-se READ COMMITTED e os locks Mesa → Pedido → Item, com releitura do estado atual antes de decidir. Não se alteram enums, consultas da cozinha ou cálculos financeiros. A saída de pedidos pagos da fila quando ainda há preparo pendente permanece uma decisão de negócio sobre pagamento antecipado; não foi corrigida silenciosamente.

## D12 — exclusão

O Controller delega a exclusão ao Service e retorna 204 apenas em sucesso. Não existe mais fallback para desativação. Sob o lock de Mesa, o Service verifica pedidos vinculados antes de tocar na credencial. Histórico ou conta ativa retorna 409 sem alteração. Desativar continua sendo uma operação separada.

A exclusão usa somente a associação real Usuario → Mesa, mantendo os guards de perfil anteriores. Uma FK adicional/desconhecida ainda pode impedir a operação depois da exclusão provisória da credencial: a exceção vira conflito e toda a transação é revertida. Não há cascata para pedidos ou apagamento de histórico. A abertura e exclusão continuam coordenadas pelo mesmo lock da mesa.

## D20 — PENDENTE, preparação revisada na Fase 4D.1

A Fase 4C deixou uma proposta sem dependência no código de produção. A revisão do contrato, SQL, canonicalização, retenção e concorrência está agora em [Fase 4D.1](../fase4d/README.md), documento de referência para a futura implementação.

[proposta_idempotencia.sql](proposta_idempotencia.sql) continua sendo apenas uma proposta de CREATE TABLE, sem migration automática e sem aplicação no banco de trabalho. A revisão substituiu JSON binário por texto JSON validado para preservar a resposta e tornou a expiração nullable para reter operações de pedidos ativos. A proposta anterior de sete dias com tombstones foi substituída pela política documentada na Fase 4D.1.

O endpoint atual continua sem interpretar `Idempotency-Key`: **D20 permanece pendente**. Não há entidade, repository ou consulta de produção para a tabela proposta; `ddl-auto=validate` permanece intacto. Migração, backend e React exigem etapas posteriores separadas.

## Execução segura

Usa-se o executor já existente `scripts/testar_mysql4b.py`, que cria MySQL 8.0.42 independente, porta diferente de 3306, UUID/datadir próprios e dados sintéticos. A classe de teste confere novamente a identidade antes de qualquer DDL. O executor executa a suíte completa e encerra a instância; não conecta ao MySQL original. Os testes de D11/D12 incluem latches e esperas reais de locks, preservando os testes de D09. Nenhum segredo ou dado real precisa ser restaurado.

Resultado desta fase: **527 testes aprovados, zero falhas/erros/ignorados**, preservando os 472 anteriores. Foram acrescentados 49 cenários MySQL (incluindo um teste exclusivamente da proposta SQL), cinco testes web e um unitário. A classe MySQL totaliza 71 cenários. A execução final usou MySQL 8.0.42 na porta 50329, datadir temporário exclusivo, e encerrou a instância com sucesso.

Os testes cobrem todas as combinações dos três estados de item com os quatro estados de pedido, avanço direto compatível com o React, repetições sem UPDATE, preservação da conclusão, erros 400/404/409, dados legados, duas atualizações simultâneas e pagamento versus preparo. Exclusão foi validada via Controller e banco real descartável, com e sem credencial, username personalizado/homônimo, pedidos ativos/históricos, flags preservadas, FK adicional causando rollback da remoção provisória da credencial e exclusão versus abertura concorrente. Snapshots determinísticos das seis tabelas verificam preservação em rejeições/rollback.

O teste da proposta D20 validou criação não destrutiva, UNIQUE por escopo, as duas FKs, nulidade da chave, CHECKs de prazo/status e rollback de registros junto a uma alteração sintética de estoque. A tabela proposta e a dependência de FK artificial foram removidas apenas do banco descartável após seus respectivos testes. Não houve testes de replay HTTP, reinício ou integração React de D20; essas garantias permanecem pendentes.

Três expectativas antigas foram atualizadas justificadamente: o fallback de DELETE não deve mais desativar a mesa; a falha de FK no Service agora é conflito de domínio; e um item legado sem pedido não pode mais receber preparação sem que seja possível verificar o estado do pedido. Nenhum teste foi removido.
