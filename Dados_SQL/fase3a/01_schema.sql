-- MySQL 8: aplicação PARADA, backup verificado, banco selecionado explicitamente.
-- Antes: SHOW CREATE TABLE usuario; SHOW CREATE TABLE mesa;
-- Execute SOMENTE se mesa_id ainda não existir e mesa.id for BIGINT assinado.
-- Se existir, não reaplique: confira tipo, UNIQUE e FK segundo README.md.
-- DDL causa commit implícito no MySQL. Não é revertido por ROLLBACK.
ALTER TABLE usuario
    ADD COLUMN mesa_id BIGINT NULL,
    ADD CONSTRAINT uk_usuario_mesa UNIQUE (mesa_id),
    ADD CONSTRAINT fk_usuario_mesa FOREIGN KEY (mesa_id) REFERENCES mesa (id);
