# Fase 4C — itens, exclusão e proposta de idempotência

## D11 — preparação

O fluxo aceito é PENDENTE → EM_PREPARO → PRONTO, com PENDENTE → PRONTO preservado por compatibilidade. O frontend local `pizzaria_maua_frontend/src/routes/PedidosCozinha.tsx`, método `handleMarcarComoEntregue`, envia PRONTO diretamente (linha 83 na inspeção desta fase). Nenhum arquivo React foi alterado.

Em pedidos ABERTO ou AGUARDANDO_PAGAMENTO, repetir o estado atual retorna 204 sem salvar nem alterar conclusão. Regressões retornam 409. Entrar em PRONTO registra a conclusão uma única vez. FINALIZADO/CANCELADO retornam 409, inclusive em repetições; status desconhecido/ausente retorna 400 e item inexistente retorna 404. O corpo de erros tratados mantém `mensagem`.

Itens sem pedido e dados legados incoerentes são conflitos, não são reparados automaticamente. Uma conclusão já existente em item não concluído bloqueia novas transições; repetir seu estado não modifica o histórico. Um item legado já PRONTO com conclusão ausente continua sem conclusão ao repetir PRONTO: esta fase não inventa uma data histórica.

Mantêm-se READ COMMITTED e os locks Mesa → Pedido → Item, com releitura do estado atual antes de decidir. Não se alteram enums, consultas da cozinha ou cálculos financeiros. A saída de pedidos pagos da fila quando ainda há preparo pendente permanece uma decisão de negócio sobre pagamento antecipado; não foi corrigida silenciosamente.

## D12 — exclusão

O Controller delega a exclusão ao Service e retorna 204 apenas em sucesso. Não existe mais fallback para desativação. Sob o lock de Mesa, o Service verifica pedidos vinculados antes de tocar na credencial. Histórico ou conta ativa retorna 409 sem alteração. Desativar continua sendo uma operação separada.

A exclusão usa somente a associação real Usuario → Mesa, mantendo os guards de perfil anteriores. Uma FK adicional/desconhecida ainda pode impedir a operação depois da exclusão provisória da credencial: a exceção vira conflito e toda a transação é revertida. Não há cascata para pedidos ou apagamento de histórico. A abertura e exclusão continuam coordenadas pelo mesmo lock da mesa.

## D20 — PENDENTE, proposta sem dependência em produção

Não existe no modelo atual um identificador persistente que diferencie reenvio acidental de nova adição intencional. Comparar itens/quantidades confundiria essas intenções. Trocar PUT por POST também não resolveria reenvio após perda da resposta. Uma lista em memória perderia estado em reinício e não coordenaria duas instâncias.

Recomenda-se **Idempotency-Key** enviado pelo React e registro persistente de operações. Um campo no corpo também funcionaria, mas alteraria o DTO atual; o cabeçalho preserva o formato do corpo e a rota `PUT /pedidos/{id}/itens`.

### Migração proposta

`proposta_idempotencia.sql` contém apenas CREATE TABLE, sem alterações dos registros existentes. **Não foi aplicado ao banco de trabalho e não é uma migration automática.** Não existe entidade, repository ou consulta de produção que dependa dessa tabela nesta entrega; `ddl-auto=validate` continua intacto.

Colunas propostas:

- ID técnico BIGINT; usuário e pedido com FKs restritivas, sem cascatas.
- Chave UUID fornecida pelo cliente, convertida para BINARY(16); não é gerada por tentativa no backend.
- SHA-256 do payload validado, BINARY(32).
- Snapshot JSON da resposta de sucesso e status 200.
- Datas UTC de criação e expiração; índice para futura limpeza em lotes.
- UNIQUE `(usuario_id, pedido_id, chave)` para coordenar instâncias e rejeitar registro duplicado.

O hash deve usar uma representação canônica dos DTOs validados: IDs/quantidades inteiros, campos em ordem fixa, mantendo a ordem e as repetições da lista. Espaços e ordem das propriedades do JSON bruto não devem importar. Reordenar itens será considerado payload diferente, conforme esse contrato proposto. Não se usa apenas o hash como identificador da intenção.

### Fluxo futuro a aprovar e implementar

1. Autenticar e autorizar o usuário atual sobre o pedido, sem confiar em usuário/mesa enviados pelo cliente.
2. Bloquear Mesa → Pedido conforme a Fase 4B e consultar a chave no escopo do usuário autenticado e do pedido.
3. Se existir: hash diferente retorna 409; hash igual e resposta retida devolve o status e o conteúdo JSON original (mesmos campos e valores, sem promessa de identidade byte a byte), sem novos itens, estoque ou total. Isso pode ocorrer mesmo se o pedido tiver fechado desde a primeira execução, mas exige novamente autorização atual.
4. Se não existir: validar estado ABERTO, bloquear Produtos por ID e executar a adição normal.
5. Gravar a resposta e a chave **na mesma transação** da adição; commit confirma ambas, rollback desfaz ambas. Não há linha concluída numa transação independente nem resposta enviada antes do commit.

O lock do pedido serializa repetições simultâneas entre instâncias; a UNIQUE é uma proteção adicional. Perda da resposta após commit permite buscar o resultado persistido com a mesma chave. Reinício não perde esse registro. Um usuário MESA de outra mesa é bloqueado antes de consultar respostas; a chave não concede acesso. Como o escopo inclui usuário, um retry não deve ser transferido silenciosamente para outra conta autenticada.

O React deverá criar um UUID por intenção de adição (por exemplo com `crypto.randomUUID()` quando disponível), guardá-lo durante todas as tentativas e usar outro UUID para uma nova adição intencional, mesmo com os mesmos produtos. Na futura implementação, será necessário também permitir `Idempotency-Key` no CORS. Nenhuma dessas mudanças do React/CORS foi feita nesta fase.

### Retenção e crescimento — decisão pendente

Proposta inicial para aprovação: reter o snapshot por sete dias. Após expiração, retornar 409 para a chave antiga, nunca executar novamente a adição. Uma limpeza futura pode remover apenas o JSON, mantendo a linha compacta (chave, hash, escopo e datas) como tombstone. A coluna JSON é nullable por isso; uma linha existente sem resposta nunca pode ser interpretada como operação inédita.

Não apagar a linha inteira e depois aceitar a mesma chave como nova: isso permitiria cobrança duplicada tardia. Tombstones permanecem até uma política explícita de arquivamento/remoção do pedido que garanta que ele não poderá receber novas adições. O custo cresce em uma linha por intenção confirmada mais o JSON durante sua retenção; dimensionamento, prazo final e limpeza precisam de aprovação, sem estimativas de tráfego inventadas. A limpeza não pode inverter a ordem dos locks. As FKs restritivas também exigem considerar o ciclo de vida futuro de usuários e pedidos.

Para compatibilidade, pode-se iniciar com cabeçalho opcional e migrar o React, mas isso seria **correção parcial** enquanto existirem adições sem chave. Torná-lo obrigatório exige aprovação da mudança de contrato. Atualmente o endpoint não interpreta a chave e continua sem idempotência: **D20 permanece pendente**, sem garantia nova para reenvios.

### Limites dos testes da proposta

O script deve ser validado somente no MySQL descartável para conferir DDL, UNIQUE, FKs, CHECKs, preservação dos registros anteriores e rollback do registro proposto. Isso não equivale a testar idempotência na API. Não foram implementados nem considerados aprovados os cenários HTTP de replay, falha de rede, reinício, duas instâncias ou limpeza de chaves.

Antes de uma implementação futura: aprovar contrato/retenção, preparar backup restaurável e ensaio numa cópia, aplicar a migration em etapa autorizada, então implementar armazenamento e replay com testes completos e integração React. Não iniciar código que exija a tabela antes dessa aprovação/aplicação.

## Execução segura

Usa-se o executor já existente `scripts/testar_mysql4b.py`, que cria MySQL 8.0.42 independente, porta diferente de 3306, UUID/datadir próprios e dados sintéticos. A classe de teste confere novamente a identidade antes de qualquer DDL. O executor executa a suíte completa e encerra a instância; não conecta ao MySQL original. Os testes de D11/D12 incluem latches e esperas reais de locks, preservando os testes de D09. Nenhum segredo ou dado real precisa ser restaurado.

Resultado desta fase: **527 testes aprovados, zero falhas/erros/ignorados**, preservando os 472 anteriores. Foram acrescentados 49 cenários MySQL (incluindo um teste exclusivamente da proposta SQL), cinco testes web e um unitário. A classe MySQL totaliza 71 cenários. A execução final usou MySQL 8.0.42 na porta 50329, datadir temporário exclusivo, e encerrou a instância com sucesso.

Os testes cobrem todas as combinações dos três estados de item com os quatro estados de pedido, avanço direto compatível com o React, repetições sem UPDATE, preservação da conclusão, erros 400/404/409, dados legados, duas atualizações simultâneas e pagamento versus preparo. Exclusão foi validada via Controller e banco real descartável, com e sem credencial, username personalizado/homônimo, pedidos ativos/históricos, flags preservadas, FK adicional causando rollback da remoção provisória da credencial e exclusão versus abertura concorrente. Snapshots determinísticos das seis tabelas verificam preservação em rejeições/rollback.

O teste da proposta D20 validou criação não destrutiva, UNIQUE por escopo, as duas FKs, nulidade da chave, CHECKs de prazo/status e rollback de registros junto a uma alteração sintética de estoque. A tabela proposta e a dependência de FK artificial foram removidas apenas do banco descartável após seus respectivos testes. Não houve testes de replay HTTP, reinício ou integração React de D20; essas garantias permanecem pendentes.

Três expectativas antigas foram atualizadas justificadamente: o fallback de DELETE não deve mais desativar a mesa; a falha de FK no Service agora é conflito de domínio; e um item legado sem pedido não pode mais receber preparação sem que seja possível verificar o estado do pedido. Nenhum teste foi removido.
