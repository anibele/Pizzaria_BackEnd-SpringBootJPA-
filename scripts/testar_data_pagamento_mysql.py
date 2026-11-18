"""5A.2: SQL, backend validate e recuperacao; somente servidores novos/sinteticos.

Windows, Python stdlib, MySQL 8.0.42, Java e classes/dependencias ja compiladas.
Nao recebe uma URL de banco: cria obrigatoriamente seus proprios servidores.
O classpath pode vir de arquivo externo ou de relatorio Surefire local anterior.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = "fase5a2_sintetica"
MAIN = "inf.anibele.pizzariamauabackend.PizzariaMauaBackEndApplication"
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
JAVA = None


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    require(port != 3306, "Porta proibida")
    return port


def clean_env():
    env = os.environ.copy()
    for key in list(env):
        if key.upper().startswith(("SPRING_", "DB_", "MYSQL_", "API_SECURITY_")) or key.upper() in (
            "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"
        ):
            env.pop(key)
    return env


class Server:
    def __init__(self, binary, work):
        self.binary, self.work = binary, work
        self.data = work / "data"
        self.data.mkdir(parents=True)
        self.password = ""
        self.process = None
        self.verified = False
        self.port = free_port()
        with (work / "initialize.log").open("wb") as log:
            result = subprocess.run([
                str(binary / "mysqld.exe"), "--no-defaults", "--initialize-insecure",
                "--basedir=" + str(binary.parent), "--datadir=" + str(self.data)
            ], stdout=log, stderr=subprocess.STDOUT, creationflags=FLAGS, timeout=60)
        require(result.returncode == 0, "Inicializacao MySQL falhou; consultar log sintetico")
        self.process = subprocess.Popen([
            str(binary / "mysqld.exe"), "--no-defaults", "--basedir=" + str(binary.parent),
            "--datadir=" + str(self.data), "--port=" + str(self.port),
            "--bind-address=127.0.0.1", "--mysqlx=OFF", "--event-scheduler=OFF",
            "--default-time-zone=+00:00", "--log-error=" + str(work / "mysql.log")
        ], env=clean_env(), creationflags=FLAGS)

    def client(self):
        require(self.port != 3306, "Destino proibido")
        return ["--no-defaults", "--protocol=TCP", "--host=127.0.0.1",
                "--port=" + str(self.port), "--user=root", "--default-character-set=utf8mb4"]

    def sql(self, statement, schema=True, expected_error=None):
        env = clean_env()
        env["MYSQL_PWD"] = self.password
        command = [str(self.binary / "mysql.exe"), *self.client(), "--batch", "--raw", "--skip-column-names"]
        if schema:
            require(self.verified, "Identidade ainda nao validada")
            command += ["--database=" + SCHEMA]
        result = subprocess.run(command, input=statement.encode("utf-8"), capture_output=True,
                                env=env, creationflags=FLAGS, timeout=30)
        if expected_error is not None:
            require(result.returncode != 0 and re.search(
                rb"ERROR " + str(expected_error).encode() + rb"\b", result.stderr
            ), "Erro negativo diferente do esperado (SQL omitido)")
            return ""
        code = re.search(rb"ERROR (\d+)", result.stderr)
        require(result.returncode == 0, "SQL falhou; codigo=" + (code[1].decode() if code else "desconhecido") + "; comando e credenciais omitidos")
        return result.stdout.decode("utf-8").strip()

    def verify(self):
        for _ in range(150):
            require(self.process.poll() is None, "Servidor descartavel terminou")
            try:
                row = json.loads(self.sql("SELECT JSON_ARRAY(VERSION(),@@port,@@server_uuid,@@datadir,@@system_time_zone,@@session.time_zone)", False))
                break
            except RuntimeError:
                time.sleep(.2)
        else:
            raise RuntimeError("Timeout de inicializacao MySQL")
        require(row[0] == "8.0.42" and row[1] == self.port, "Versao/porta inesperadas")
        require(Path(row[3]).resolve() == self.data.resolve() and len(row[2]) == 36, "Identidade inesperada")
        self.identity = dict(host="127.0.0.1", port=self.port, schema=SCHEMA,
                             version=row[0], uuid=row[2], datadir=row[3], system_timezone=row[4], session_timezone=row[5])
        self.verified = True
        password = secrets.token_hex(32)
        self.sql("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "'", False)
        self.password = password
        self.sql("CREATE DATABASE " + SCHEMA, False)
        require(self.sql("SELECT DATABASE()") == SCHEMA, "Schema inesperado")
        print("ISOLAMENTO " + json.dumps(self.identity), flush=True)

    def stop(self):
        if self.process is not None and self.process.poll() is None:
            if self.verified:
                try:
                    self.sql("SHUTDOWN", False)
                    self.process.wait(timeout=20)
                except Exception:
                    self.process.terminate()
                    self.process.wait(timeout=20)
            else:
                self.process.terminate()
                self.process.wait(timeout=20)
        print("MYSQL_ENCERRADO porta=" + str(self.port), flush=True)


def classpath(source):
    if source:
        value = source.read_text(encoding="utf-8").strip()
    else:
        report = ROOT / "PizzariaMauaBackEnd/target/surefire-reports/TEST-inf.anibele.pizzariamauabackend.service.FinanceiroMysqlTest.xml"
        props = {p.attrib["name"]: p.attrib["value"] for p in ET.parse(report).findall(".//properties/property")}
        value = props["java.class.path"]
    entries = [p for p in value.split(os.pathsep) if p and "test-classes" not in p and "spring-boot-devtools" not in p]
    require(all(Path(p).exists() for p in entries), "Classpath local incompleto")
    require(any(Path(p).resolve() == (ROOT / "PizzariaMauaBackEnd/target/classes").resolve() for p in entries), "Classes atuais ausentes")
    return os.pathsep.join(entries)


def java_args(work, name, cp, tail):
    file = work / (name + ".args")
    # Java argument files: quoted forward-slash paths avoid Windows escape sequences.
    file.write_text("\n".join('"' + x.replace("\\", "/") + '"' for x in ["-cp", cp, *tail]), encoding="utf-8")
    return "@" + str(file)


def backend(server, work, cp, ddl):
    port = free_port()
    env = clean_env()
    env["SPRING_APPLICATION_JSON"] = json.dumps({
        "spring.datasource.url": f"jdbc:mysql://127.0.0.1:{server.port}/{SCHEMA}?connectionTimeZone=UTC",
        "spring.datasource.username": "root", "spring.datasource.password": server.password,
        "spring.jpa.hibernate.ddl-auto": ddl, "spring.sql.init.mode": "never",
        "spring.profiles.active": "validacao5a2", "spring.jpa.open-in-view": False,
        "spring.jpa.show-sql": False, "spring.main.banner-mode": "off",
        "server.address": "127.0.0.1", "server.port": port,
        "api.security.token.secret": base64.b64encode(secrets.token_bytes(48)).decode()
    })
    logpath = work / ("backend-" + ddl + ".log")
    args = java_args(work, "backend-" + ddl, cp, ["-Duser.timezone=America/Sao_Paulo", MAIN])
    with logpath.open("wb") as log:
        process = subprocess.Popen([str(JAVA), args], cwd=ROOT / "PizzariaMauaBackEnd", env=env,
                                   stdout=log, stderr=subprocess.STDOUT, creationflags=FLAGS)
    try:
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            require(process.poll() is None, "Backend terminou; consultar log local (nao publicar configuracao)")
            text = logpath.read_text(encoding="utf-8", errors="replace")
            if "Started PizzariaMauaBackEndApplication" in text:
                break
            time.sleep(.2)
        else:
            raise RuntimeError("Timeout de inicializacao do backend")
        try:
            request = urllib.request.Request(f"http://127.0.0.1:{port}/mesas",
                                             headers={"Authorization": "Bearer token-sintetico-invalido"})
            urllib.request.urlopen(request, timeout=5)
            raise RuntimeError("Rota protegida inesperadamente publica")
        except urllib.error.HTTPError as error:
            require(error.code == 401, "Backend nao respondeu 401 na rota protegida")
        require("Carga demo" not in text and "perfil demo" not in text.lower(), "Seed inesperado")
        print("BACKEND_OK " + json.dumps(dict(port=port, profile="validacao5a2", ddl=ddl)), flush=True)
        return port
    finally:
        if process.poll() is None:
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                           capture_output=True, creationflags=FLAGS, timeout=20)
        process.wait(timeout=20)
        with socket.socket() as sock:
            sock.settimeout(1)
            require(sock.connect_ex(("127.0.0.1", port)) != 0, "Porta do backend permaneceu ativa")


def snapshot(server, old_columns=None):
    tables = server.sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name").splitlines()
    result = {}
    for table in tables:
        columns = server.sql(f"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' ORDER BY ordinal_position").splitlines()
        if old_columns and table == "pedido":
            columns = old_columns
        expressions = ["HEX(`" + c + "`)" if table == "idempotencia_adicao" and c in ("chave", "payload_hash") else "`" + c + "`" for c in columns]
        order = "produto_id,ingrediente" if table == "produto_ingredientes" else "id"
        data = server.sql(f"SELECT JSON_ARRAY({','.join(expressions)}) FROM `{table}` ORDER BY {order}")
        result[table] = dict(count=int(server.sql(f"SELECT COUNT(*) FROM `{table}`")), sha256=hashlib.sha256(data.encode()).hexdigest())
    return result


def structure(server, exclude_new=False):
    condition = " AND column_name <> 'data_pagamento_utc'" if exclude_new else ""
    columns = server.sql("SELECT JSON_ARRAY(table_name,column_name,column_type,is_nullable,column_default,extra,ordinal_position,character_set_name,collation_name) FROM information_schema.columns WHERE table_schema=DATABASE()" + condition + " ORDER BY table_name,ordinal_position")
    indexes = server.sql("SELECT JSON_ARRAY(table_name,index_name,non_unique,seq_in_index,column_name,index_type) FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index")
    constraints = server.sql("SELECT JSON_ARRAY(table_name,constraint_name,constraint_type,enforced) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name")
    keys = server.sql("SELECT JSON_ARRAY(table_name,constraint_name,column_name,ordinal_position,referenced_table_name,referenced_column_name) FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() ORDER BY table_name,constraint_name,ordinal_position")
    checks = server.sql("SELECT JSON_ARRAY(constraint_name,check_clause) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() ORDER BY constraint_name")
    refs = server.sql("SELECT JSON_ARRAY(constraint_name,update_rule,delete_rule) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() ORDER BY constraint_name")
    engines = server.sql("SELECT JSON_ARRAY(table_name,engine,row_format,table_collation) FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")
    return dict(columns=columns, indexes=indexes, constraints=constraints, keys=keys, checks=checks, refs=refs, engines=engines)


def seed(server):
    server.sql("INSERT INTO mesa(id,numero,ativo,status) VALUES (1,1,1,'OCUPADA'),(2,2,1,'OCUPADA'),(3,3,1,'LIVRE'),(4,4,1,'LIVRE'),(5,5,1,'LIVRE'),(6,6,1,'LIVRE')")
    # Deliberately unusable synthetic credential; no real password/hash copied.
    server.sql("INSERT INTO usuario(id,username,senha,role,mesa_id) VALUES (1,'sintetico','NAO_E_CREDENCIAL_REAL','MESA',1)")
    server.sql("INSERT INTO produto(id,nome,ativo,categoria,preco_base,qtd_estoque,prato_vegano) VALUES (1,'Produto sintetico',1,'PIZZAS',10.25,40,0)")
    server.sql("INSERT INTO produto_ingredientes(produto_id,ingrediente) VALUES (1,'Ingrediente sintetico')")
    for number, status, form in [(1,"ABERTO","NULL"),(2,"AGUARDANDO_PAGAMENTO","'PIX'"),(3,"FINALIZADO","'CARTAO'"),(4,"CANCELADO","'DINHEIRO'"),(5,"FINALIZADO","NULL"),(6,"FINALIZADO","'DINHEIRO'")]:
        server.sql(f"INSERT INTO pedido(id,mesa_id,data_hora,forma_pagamento,faturamento,status) VALUES ({number},{number},'2026-11-10 23:40:00.123456',{form},10.25,'{status}')")
        server.sql(f"INSERT INTO item_pedido(id,pedido_id,produto_id,quantidade,preco_unitario,status,data_hora_inclusao) VALUES ({number},{number},1,1,10.25,'PENDENTE','2026-11-10 23:41:00.654321')")
    server.sql("INSERT INTO idempotencia_adicao(usuario_id,pedido_id,chave,payload_hash,resposta_json,criado_em) VALUES (1,1,UNHEX(REPEAT('01',16)),UNHEX(REPEAT('02',32)),'{\"id\":1}','2026-11-11 02:41:00.654321')")


def dump(server, file):
    env = clean_env(); env["MYSQL_PWD"] = server.password
    # Compact dumps preserve FK enforcement. Restore parent tables before children
    # instead of disabling integrity checks to accommodate alphabetical order.
    pending = set(server.sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()").splitlines())
    edges = [json.loads(row) for row in server.sql("SELECT JSON_ARRAY(table_name,referenced_table_name) FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND referenced_table_name IS NOT NULL").splitlines()]
    ordered = []
    while pending:
        ready = sorted(t for t in pending if not any(child == t and parent in pending for child,parent in edges))
        require(ready, "Ciclo de FKs exige plano de backup separado")
        ordered.extend(ready); pending.difference_update(ready)
    command = [str(server.binary / "mysqldump.exe"), *server.client(), "--single-transaction", "--skip-lock-tables",
               "--routines", "--events", "--triggers", "--set-gtid-purged=OFF", "--no-tablespaces",
               "--hex-blob", "--column-statistics=0", "--compact", SCHEMA, *ordered]
    with file.open("wb") as output:
        result = subprocess.run(command, env=env, stdout=output, stderr=subprocess.PIPE, creationflags=FLAGS, timeout=30)
    require(result.returncode == 0 and file.stat().st_size > 0, "Backup sintetico falhou")
    require("FOREIGN_KEY_CHECKS" not in file.read_text(encoding="utf-8").upper(), "Backup deve preservar verificacao de FKs")


def main():
    global JAVA
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql-bin", required=True, type=Path)
    parser.add_argument("--classpath-file", type=Path)
    args = parser.parse_args()
    require(os.name == "nt", "Executor nativo Windows")
    # Oracle's javapath launcher may spawn another java.exe. Use java.home/bin
    # directly so the handle/PID belongs to the real JVM, not its launcher.
    settings = subprocess.run(["java", "-XshowSettings:properties", "-version"],
                              env=clean_env(), capture_output=True, timeout=15, creationflags=FLAGS)
    home = re.search(r"^\s*java.home = (.+)$", settings.stderr.decode(errors="replace"), re.M)
    require(settings.returncode == 0 and home is not None, "JDK local nao identificado")
    JAVA = Path(home[1].strip()) / "bin/java.exe"
    require(JAVA.is_file(), "Executavel real do JDK ausente")
    cp = classpath(args.classpath_file)
    work = Path(tempfile.mkdtemp(prefix="pizzaria_5a2_"))
    servers = []
    results = {}
    try:
        source = Server(args.mysql_bin.resolve(), work / "ensaio"); servers.append(source); source.verify()
        # Only the brand-new isolated schema is bootstrapped with create; never the normal datasource.
        backend(source, work, cp, "create")
        source.sql("DROP TABLE idempotencia_adicao")
        source.sql((ROOT / "Dados_SQL/fase4c/proposta_idempotencia.sql").read_text(encoding="utf-8"))
        check = (ROOT / "Dados_SQL/fase3a/03_restricao_perfil.sql").read_text(encoding="utf-8")
        source.sql(check[check.index("ALTER TABLE usuario"):])
        seed(source)
        columns = source.sql("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido' ORDER BY ordinal_position").splitlines()
        require("data_pagamento_utc" not in columns, "Coluna ja existente")
        before, schema_before = snapshot(source), structure(source)
        backup = work / "antes_5a2_sintetico.sql"; dump(source, backup)
        require(snapshot(source) == before, "Dados mudaram durante backup")
        proposal = (ROOT / "Dados_SQL/fase5a/proposta_data_pagamento.sql").read_text(encoding="utf-8")
        uncommented = re.sub(r"--[^\n]*", "", proposal).strip()
        require(re.fullmatch(r"ALTER\s+TABLE\s+pedido\s+ADD\s+COLUMN\s+data_pagamento_utc\s+DATETIME\(6\)\s+NULL\s*;", uncommented, re.I), "Proposta diferente do ALTER unico aprovado")
        # Confirm identity immediately before the migration.
        require(json.loads(source.sql("SELECT JSON_ARRAY(@@server_uuid,@@port,@@datadir,DATABASE())")) == [source.identity["uuid"],source.port,source.identity["datadir"],SCHEMA], "Identidade mudou")
        started = time.monotonic(); source.sql(proposal); elapsed = time.monotonic() - started
        require(snapshot(source, columns) == before, "Dados anteriores alterados")
        results["A"] = dict(result="PASS", alter_seconds=round(elapsed, 3))
        require(source.sql("SELECT COUNT(*) FROM pedido WHERE data_pagamento_utc IS NULL") == "6", "Historico preenchido indevidamente")
        results["B"] = "PASS: seis historicos NULL, incluindo finalizados"
        require(structure(source, True) == schema_before, "Estrutura anterior alterada")
        require(json.loads(source.sql("SELECT JSON_ARRAY(column_type,is_nullable,column_default,extra) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido' AND column_name='data_pagamento_utc'")) == ["datetime(6)","YES",None,""], "Nova coluna divergente")
        results["C"] = "PASS: colunas, FKs, CHECKs, indices e engines preservados"
        full_before_boot = snapshot(source)
        validate_port = backend(source, work, cp, "validate")
        require(snapshot(source) == full_before_boot and structure(source, True) == schema_before, "Inicializacao alterou dados/schema")
        results["D"] = dict(result="PASS", http_port=validate_port, profile="validacao5a2", hibernate="validate")
        source.sql("UPDATE pedido SET data_pagamento_utc='2026-11-11 03:20:00.123456' WHERE id=3")
        require(source.sql("SELECT DATE_FORMAT(data_pagamento_utc,'%Y-%m-%d %H:%i:%s.%f') FROM pedido WHERE id=3") == "2026-11-11 03:20:00.123456", "Microssegundos perdidos")
        probe = work / "JdbcProbe.java"
        probe.write_text('''import java.sql.*; import java.time.*;
class JdbcProbe { public static void main(String[] args) throws Exception {
 try (var c=DriverManager.getConnection(System.getenv("TEST_JDBC_URL"),"root",System.getenv("TEST_JDBC_PASSWORD")); var s=c.createStatement()) {
  try(var r=s.executeQuery("select data_pagamento_utc from pedido where id=3")){r.next();if(!LocalDateTime.of(2026,11,11,3,20,0,123456000).equals(r.getObject(1,LocalDateTime.class)))throw new AssertionError("Precisao JDBC");}
  try(var r=s.executeQuery("select data_pagamento_utc from pedido where id=5")){r.next();if(r.getObject(1,LocalDateTime.class)!=null || !r.wasNull())throw new AssertionError("NULL JDBC");}
  s.execute("set time_zone='+09:00'");
  try(var r=s.executeQuery("select data_pagamento_utc from pedido where id=3")){r.next();if(!LocalDateTime.of(2026,11,11,3,20,0,123456000).equals(r.getObject(1,LocalDateTime.class)))throw new AssertionError("DATETIME reinterpretado");}
 } System.out.println("JDBC_PRECISAO_NULL_OK"); }}''', encoding="utf-8")
        env = clean_env(); env.update(TEST_JDBC_URL=f"jdbc:mysql://127.0.0.1:{source.port}/{SCHEMA}?connectionTimeZone=UTC", TEST_JDBC_PASSWORD=source.password)
        result = subprocess.run([str(JAVA),java_args(work,"jdbc",cp,[str(probe)])], env=env, capture_output=True, creationflags=FLAGS, timeout=30)
        require(result.returncode == 0 and b"JDBC_PRECISAO_NULL_OK" in result.stdout, "Verificacao JDBC falhou")
        results["E"] = "PASS: SQL/JDBC preservam .123456 e valor civil DATETIME"
        results["F"] = "PASS: JDBC retorna NULL e wasNull verdadeiro"
        source.sql("UPDATE pedido SET data_pagamento_utc=NULL WHERE id=3")
        require(snapshot(source, columns) == before, "Valores financeiros/historicos alterados")
        results["G"] = dict(result="PASS", before=before, after=snapshot(source, columns))
        plan = source.sql("EXPLAIN SELECT COALESCE(SUM(faturamento),0) FROM pedido WHERE status='FINALIZADO' AND data_pagamento_utc>='2026-11-11 03:00:00' AND data_pagamento_utc<'2026-11-12 03:00:00'")
        require(structure(source,True)["indexes"] == schema_before["indexes"], "Indices alterados")
        results["H"] = dict(result="PASS", proposed_index=False, explain=plan)
        full_after = snapshot(source)
        source.sql(proposal, expected_error=1060)
        require(snapshot(source) == full_after and structure(source,True) == schema_before, "Reexecucao modificou estrutura/dados")
        results["I"] = "PASS: segunda execucao rejeitada com 1060; nenhuma mudanca"
        restored = Server(args.mysql_bin.resolve(),work / "recuperacao"); servers.append(restored); restored.verify()
        require(restored.identity["uuid"] != source.identity["uuid"] and restored.port != source.port, "Recuperacao nao isolada")
        restored.sql(backup.read_text(encoding="utf-8"))
        require(snapshot(restored) == before and structure(restored) == schema_before, "Recuperacao divergiu do snapshot anterior")
        require(restored.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pedido' AND column_name='data_pagamento_utc'") == "0", "Backup nao corresponde ao estado anterior")
        backend(restored,work,cp,"validate")
        require(snapshot(restored) == before, "Backend alterou dados recuperados")
        results["J"] = dict(result="PASS", backup=str(backup),bytes=backup.stat().st_size,sha256=hashlib.sha256(backup.read_bytes()).hexdigest(),restored_port=restored.port)
        report = dict(results=results,servers=[s.identity for s in servers],proposal_sha256=hashlib.sha256(proposal.encode()).hexdigest())
        (work / "resultado.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
        print("RESULTADOS " + json.dumps({k:(v.get("result") if isinstance(v,dict) else v) for k,v in results.items()}),flush=True)
        print("EVIDENCIAS_SINTETICAS " + str(work),flush=True)
    finally:
        for server in reversed(servers):
            server.stop()


if __name__ == "__main__":
    main()
