-- PROPOSTA D20: NAO APLICADA E NAO CARREGADA PELA APLICACAO.
-- Depende de aprovacao, backup/restauracao e ensaio da migracao em copia isolada.
-- MySQL 8.0.42; confirmar usuario.id e pedido.id BIGINT assinados/InnoDB.
-- Confirmar ausencia desta tabela e nomes de constraints antes de futura aplicacao.
-- Nao executar no pizzamaua de trabalho nesta fase.
CREATE TABLE idempotencia_adicao (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    pedido_id BIGINT NOT NULL,
    chave BINARY(16) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    resposta_json JSON NULL,
    status_http SMALLINT NOT NULL DEFAULT 200,
    criado_em DATETIME(6) NOT NULL,
    expira_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotencia_adicao UNIQUE (usuario_id, pedido_id, chave),
    CONSTRAINT fk_idempotencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_idempotencia_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT ck_idempotencia_status CHECK (status_http = 200),
    CONSTRAINT ck_idempotencia_prazo CHECK (expira_em > criado_em),
    INDEX ix_idempotencia_expira (expira_em)
) ENGINE=InnoDB;
