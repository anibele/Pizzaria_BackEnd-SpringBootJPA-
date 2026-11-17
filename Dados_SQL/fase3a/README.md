# Fase 3A — migração manual de Usuario → Mesa

**Nenhum destes scripts foi executado no banco de trabalho.** Não são carregados pelo Spring Boot. A associação das contas existentes depende desta migração ou de uma operação manual equivalente, verificada pelo responsável pelo banco.

## Modelo e efeito operacional

- `usuario.mesa_id`: BIGINT opcional, FK para `mesa.id`, UNIQUE; sem cascatas.
- Usuários MESA devem ter vínculo; demais perfis devem manter NULL. O código valida isso na persistência e o script 03 acrescenta a restrição no banco.
- O CHECK `ck_usuario_mesa_perfil` exige explicitamente `role IS NOT NULL`, independentemente de a coluna já ter NOT NULL. Assim, perfis nulos são rejeitados mesmo em schema nullable, sem depender da aceitação de UNKNOWN pelo MySQL.
- Uma mesa pode continuar sem credencial. Não há vínculo inverso nem alteração de pedidos.
- Contas MESA antigas sem vínculo podem autenticar, mas recebem 403 nas operações de mesa/pedido protegidas. JWT, senhas e formato do login não mudaram.
- Novas credenciais recebem vínculo no cadastro. O seed cria mesas antes dos usuários e vincula somente contas novas; contas antigas e conflitos geram aviso e ficam intactos. D02/D15 não foram corrigidos.

## Preparação obrigatória

1. Fazer backup consistente e verificar restauração; ensaiar a sequência numa cópia descartável MySQL da mesma versão. Os testes JPA usam H2; o ensaio independente MySQL descrito abaixo não substitui a validação de uma cópia do schema e dos dados reais.
2. Parar todas as instâncias da aplicação e impedir gravações concorrentes durante toda a inspeção e aplicação. A guarda de conflitos do script 02 pressupõe essa janela exclusiva de manutenção.
3. Usar conexão dedicada, selecionar explicitamente o banco correto e conferir `SELECT DATABASE(), VERSION();`, `SHOW CREATE TABLE usuario;` e `SHOW CREATE TABLE mesa;`. Nunca executar com cliente configurado para continuar após erros.
4. Registrar contagens de usuários, mesas, pedidos e itens e guardar comparação protegida dos registros de usuários antes/depois (senhas não devem ser impressas em relatórios ou logs).

## Sequência de aplicação

1. **Schema:** se `mesa_id` não existir e `mesa.id` for BIGINT assinado, revisar e executar `01_schema.sql`. DDL MySQL tem commit implícito: não prometer rollback de schema. Não iniciar a nova aplicação antes de preparar e conferir o schema.
2. **Schema já existente:** não reaplicar o script 01. Pode ter sido criado por `ddl-auto=update`. Inspecionar tipo, FK, unicidade, nulos e duplicidades antes de qualquer correção. Conferir índices e constraints com `SHOW CREATE TABLE usuario` e `information_schema`. Se tipo, índices ou vínculos divergirem, interromper e preparar ajuste específico; não apagar coluna nem recriá-la.
3. **Simulação:** executar `02_vinculos.sql` mantendo `@fase3a_aplicar = 0`. Só são gravados dados temporários da sessão; o UPDATE persistente tem guarda falsa. Examinar a tabela de mapeamento, ocupantes, contas ausentes e bloqueios. A lista contém os dez pares literais aprovados; não deriva mesa de username.
4. **Conflitos:** qualquer vínculo diferente, perfil incompatível, mesa esperada ausente, vínculo órfão, credencial concorrente ou conta MESA sem mapeamento bloqueia o lote inteiro. Contas inexistentes são apenas listadas e nunca criadas. Contas personalizadas sem vínculo exigem adicionar um par literal à lista após decisão do proprietário. Não sobrescrever vínculos válidos; em caso de conflito com os exemplos demonstrativos, parar para decisão explícita.
5. **Aplicação:** somente após revisão e simulação sem bloqueios, alterar a variável para 1 numa cópia revisada do script 02 e executar na mesma janela exclusiva. O único UPDATE de dados modifica `usuario.mesa_id` NULL; não muda senhas, usernames, perfis, mesas ou histórico. Erro de FK/UNIQUE deve interromper a execução; fechar a sessão/ROLLBACK antes de nova tentativa. A tabela temporária desaparece ao fechar a conexão.
6. **Verificação:** repetir a simulação; os pares existentes devem apontar à mesa aprovada, `bloqueios=0` e `associacoes_alteradas=0`. Confirmar que todos os MESA têm vínculo, nenhum outro perfil o tem, não há mesa com duas credenciais, e os demais campos/contagens permanecem iguais.
7. **Restrição condicional:** somente com zero linhas na consulta do script 03, MySQL >= 8.0.16 e ausência da constraint, executar o ALTER do `03_restricao_perfil.sql`. Não reaplicar se já existir. MySQL anterior exige decisão de compatibilidade; não considerar CHECK inefetivo uma proteção.
8. Reiniciar e conferir acesso de mesa01 à mesa 1 e negação à mesa 2, além dos perfis administrativos. Registrar o resultado antes de liberar uso normal.

## Limites e recuperação

Os scripts são intencionalmente manuais, não migrations automáticas. A simulação e a repetição do UPDATE são seguras; os ALTERs não são para reaplicação indiscriminada. O código não comprova que as contas do banco existente estejam associadas: essa verificação cabe à execução acima. Não se deve reverter removendo coluna ou apagando usuários; diante de falha, manter manutenção, revisar backup e preparar recuperação específica. Nenhum conflito é resolvido por adivinhação ou exclusão de dados.

D12 (desativação após exclusão frustrada), concorrência, estados de pedidos e política de credenciais/JWT permanecem fora da Fase 3A.

## Ensaio desta implementação

Os três scripts foram executados em MySQL Community 8.4.9 descartável, com schema mínimo equivalente e dados sintéticos. A instância foi inicializada com `--no-defaults`, datadir exclusivo em `target`, bind em localhost e porta exclusiva; `@@datadir` foi conferido antes de criar o banco sintético. O processo foi encerrado ao final. Nenhuma conexão com o banco de trabalho foi utilizada.

Passaram dez verificações: simulação sem alteração; bloqueio integral por vínculo conflitante, mesa ocupada, conta sem mapa, perfil conflitante e mesa esperada ausente; aplicação dos dez pares preservando senhas e vínculo personalizado válido; repetição sem sobrescrita; preservação de mesas/pedidos/itens; e rejeição por UNIQUE, CHECK e FK.

Isso valida a execução dos scripts nesse cenário sintético, **não** a compatibilidade do schema real nem a associação das contas existentes. Antes da implantação, continuam obrigatórios backup, inspeção, ensaio numa cópia real e aprovação dos mapeamentos.

Referências de revisão: [DDL e commits implícitos](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html) e [limitações de tabelas temporárias](https://dev.mysql.com/doc/refman/8.0/en/temporary-table-problems.html). As contagens de bloqueios usam consultas separadas para não reabrir a mesma tabela temporária numa única consulta.
