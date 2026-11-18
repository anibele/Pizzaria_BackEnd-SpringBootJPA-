-- MySQL >= 8.0.16 (CHECK efetivamente aplicado).
-- Execute SOMENTE após aplicar e verificar todas as associações.
-- Deve retornar ZERO linhas antes de adicionar a restrição:
SELECT id, username, role, mesa_id FROM usuario
WHERE role IS NULL OR (role = 'MESA' AND mesa_id IS NULL)
   OR (role <> 'MESA' AND mesa_id IS NOT NULL);

-- Se houver qualquer linha, PARE e resolva por mapeamento explícito aprovado.
-- Se a constraint já existir, apenas confira SHOW CREATE TABLE usuario.
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_mesa_perfil
CHECK (
    role IS NOT NULL
    AND (
        (role = 'MESA' AND mesa_id IS NOT NULL)
        OR (role <> 'MESA' AND mesa_id IS NULL)
    )
);
