-- PROPOSTA D23: NAO APLICAR NO BANCO ORIGINAL NESTA FASE.
-- MySQL 8.0.42. Antes de eventual aplicacao: backup restauravel, ensaio em copia,
-- verificar schema/identidade, ausencia da coluna e janela sem escritores.
-- Campo futuro em UTC somente na confirmacao efetiva; NULL significa desconhecido.
-- Sem DEFAULT, UPDATE/backfill ou copia de data_hora. Nao mapeado em producao.
ALTER TABLE pedido ADD COLUMN data_pagamento_utc DATETIME(6) NULL;
