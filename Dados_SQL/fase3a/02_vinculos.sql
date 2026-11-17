-- MySQL 8. Execute apenas após verificar o schema e parar a aplicação.
-- Padrão: SIMULAÇÃO. Revise os resultados antes de mudar 0 para 1.
-- Não altera username, senha, role, mesas ou pedidos.
SET @fase3a_aplicar = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_pizzaria_auditoria_3a_vinculos;
CREATE TEMPORARY TABLE tmp_pizzaria_auditoria_3a_vinculos (
    username VARCHAR(255) NOT NULL PRIMARY KEY,
    numero INT NOT NULL UNIQUE
);
-- Mapeamento EXPLÍCITO da demonstração, nunca inferido de substring/regex.
INSERT INTO tmp_pizzaria_auditoria_3a_vinculos (username, numero) VALUES
    ('mesa01', 1), ('mesa02', 2), ('mesa03', 3), ('mesa04', 4), ('mesa05', 5),
    ('mesa06', 6), ('mesa07', 7), ('mesa08', 8), ('mesa09', 9), ('mesa10', 10);
-- Contas personalizadas sem vínculo exigem decisões explícitas nesta lista.
-- Não invente mesas nem remapeie contas que já possuam vínculo válido.

START TRANSACTION;

-- Inspeção ANTES do UPDATE: contas ausentes são informadas, nunca criadas.
SELECT mapa.username, mapa.numero, u.id AS usuario_id, u.role,
       u.mesa_id AS vinculo_atual, m.id AS mesa_esperada,
       ocupante.username AS credencial_que_ja_ocupa_a_mesa
FROM tmp_pizzaria_auditoria_3a_vinculos mapa
LEFT JOIN usuario u ON BINARY u.username = BINARY mapa.username
LEFT JOIN mesa m ON m.numero = mapa.numero
LEFT JOIN usuario ocupante ON ocupante.mesa_id = m.id AND ocupante.id <> u.id
ORDER BY mapa.numero;

-- Usuários não demonstrativos sem vínculo: exigem mapeamento manual aprovado.
SELECT u.id, u.username FROM usuario u
WHERE u.role = 'MESA' AND u.mesa_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM tmp_pizzaria_auditoria_3a_vinculos mapa
                  WHERE BINARY mapa.username = BINARY u.username);

-- Contagens separadas: MySQL não permite reabrir a mesma tabela temporária
-- duas vezes na mesma consulta. NULL mantém o UPDATE bloqueado se uma falhar.
SET @fase3a_conflitos = NULL, @fase3a_sem_mapa = NULL,
    @fase3a_invalidos = NULL, @fase3a_duplicados = NULL;
SET @fase3a_conflitos =
    (SELECT COUNT(*) FROM tmp_pizzaria_auditoria_3a_vinculos mapa
     JOIN usuario u ON BINARY u.username = BINARY mapa.username
     LEFT JOIN mesa m ON m.numero = mapa.numero
     WHERE m.id IS NULL OR u.role <> 'MESA'
        OR (u.mesa_id IS NOT NULL AND u.mesa_id <> m.id)
        OR EXISTS (SELECT 1 FROM usuario outro WHERE outro.mesa_id = m.id AND outro.id <> u.id));
SET @fase3a_sem_mapa = (SELECT COUNT(*) FROM usuario u
     WHERE u.role = 'MESA' AND u.mesa_id IS NULL
       AND NOT EXISTS (SELECT 1 FROM tmp_pizzaria_auditoria_3a_vinculos mapa
                       WHERE BINARY mapa.username = BINARY u.username));
SET @fase3a_invalidos = (SELECT COUNT(*) FROM usuario u LEFT JOIN mesa m ON m.id = u.mesa_id
     WHERE (u.mesa_id IS NOT NULL AND (m.id IS NULL OR u.role <> 'MESA')) OR u.role IS NULL);
SET @fase3a_duplicados = (SELECT COUNT(*) FROM
        (SELECT mesa_id FROM usuario WHERE mesa_id IS NOT NULL
         GROUP BY mesa_id HAVING COUNT(*) > 1) duplicadas);
SET @fase3a_bloqueios = @fase3a_conflitos + @fase3a_sem_mapa + @fase3a_invalidos + @fase3a_duplicados;

SELECT @fase3a_aplicar AS aplicar, @fase3a_bloqueios AS bloqueios,
       CASE WHEN @fase3a_bloqueios IS NULL OR @fase3a_bloqueios > 0
            THEN 'BLOQUEADO: revisar dados; nenhuma associação será alterada'
            WHEN @fase3a_aplicar = 0 THEN 'SIMULACAO: nenhuma associação será alterada'
            ELSE 'APLICACAO EXPLICITA AUTORIZADA PELO OPERADOR' END AS resultado;

-- Guarda GLOBAL: um conflito impede TODAS as associações desta execução.
-- Associações existentes nunca são sobrescritas; reexecução não as altera.
UPDATE usuario u
JOIN tmp_pizzaria_auditoria_3a_vinculos mapa ON BINARY mapa.username = BINARY u.username
JOIN mesa m ON m.numero = mapa.numero
SET u.mesa_id = m.id
WHERE u.role = 'MESA' AND u.mesa_id IS NULL
  AND @fase3a_aplicar = 1 AND @fase3a_bloqueios = 0;

SELECT ROW_COUNT() AS associacoes_alteradas;
SELECT u.id, u.username, u.role, u.mesa_id, m.numero
FROM usuario u LEFT JOIN mesa m ON m.id = u.mesa_id
WHERE u.role = 'MESA' OR u.mesa_id IS NOT NULL
ORDER BY u.id;

COMMIT;
DROP TEMPORARY TABLE tmp_pizzaria_auditoria_3a_vinculos;
