"""4D.1: testes de estrutura e concorrencia SQL, sem HTTP nem codigo de producao.
Windows/Python stdlib + MySQL Community 8.0.42. Nao conecta a localhost:3306.
"""
import argparse, concurrent.futures as futures, ctypes as C, hashlib, json, os
import pathlib, secrets, socket, subprocess, tempfile, threading, time, uuid
from decimal import Decimal

parser=argparse.ArgumentParser()
parser.add_argument("--mysql-bin",required=True,type=pathlib.Path)
args=parser.parse_args()
assert os.name=="nt"
ROOT=pathlib.Path(__file__).resolve().parents[1]
BIN=args.mysql_bin.resolve();FLAGS=subprocess.CREATE_NO_WINDOW
handles=[os.add_dll_directory(str(BIN)),os.add_dll_directory(str(BIN.parent/"lib"))]
lib=C.CDLL(str(BIN.parent/"lib/libmysql.dll"))
def api(name,params,result):
    f=getattr(lib,name);f.argtypes=params;f.restype=result;return f
init=api("mysql_init",[C.c_void_p],C.c_void_p)
options=api("mysql_options",[C.c_void_p,C.c_int,C.c_void_p],C.c_int)
connect=api("mysql_real_connect",[C.c_void_p,C.c_char_p,C.c_char_p,C.c_char_p,C.c_char_p,C.c_uint,C.c_char_p,C.c_ulong],C.c_void_p)
query=api("mysql_real_query",[C.c_void_p,C.c_char_p,C.c_ulong],C.c_int)
store=api("mysql_store_result",[C.c_void_p],C.c_void_p)
fields=api("mysql_num_fields",[C.c_void_p],C.c_uint)
row=api("mysql_fetch_row",[C.c_void_p],C.POINTER(C.c_void_p))
lengths=api("mysql_fetch_lengths",[C.c_void_p],C.POINTER(C.c_ulong))
free=api("mysql_free_result",[C.c_void_p],None)
errno=api("mysql_errno",[C.c_void_p],C.c_uint)
close=api("mysql_close",[C.c_void_p],None)
class SqlError(Exception):
    def __init__(self,code):self.code=code;super().__init__("MySQL codigo "+str(code)+"; SQL/credenciais omitidos")
class DB:
    def __init__(self,port,password,database=None):
        assert port!=3306 and (database is None or database=="fase4d_sintetica")
        self.h=init(None)
        timeout=C.c_uint(12)
        for opt in (0,11,12):options(self.h,opt,C.byref(timeout))
        options(self.h,7,C.c_char_p(b"utf8mb4"))
        if not connect(self.h,b"127.0.0.1",b"root",password.encode(),database.encode() if database else None,port,None,0):
            code=errno(self.h);self.close();raise SqlError(code)
    def sql(self,text):
        data=text.encode("utf-8")
        if query(self.h,data,len(data)):raise SqlError(errno(self.h))
        r=store(self.h);result=[]
        if r:
            count=fields(r)
            while True:
                values=row(r)
                if not values:break
                size=lengths(r)
                result.append([None if values[i] is None else C.string_at(values[i],size[i]).decode("utf-8") for i in range(count)])
            free(r)
        return result
    def close(self):
        if self.h:close(self.h);self.h=None

def text_literal(text):return "CONVERT(0x"+text.encode("utf-8").hex()+" USING utf8mb4)"
def canonical(pedido,itens):
    assert type(pedido) is int and 0<pedido<=2**63-1
    assert itens
    norm=[]
    for i in itens:
        pid=i["produtoId"];qty=i["quantidade"]
        assert type(pid) is int and 0<pid<=2**63-1 and type(qty) is int and 0<qty<=2**31-1
        norm.append({"produtoId":pid,"quantidade":qty})
    return 'v1\nPUT\n/pedidos/'+str(pedido)+'/itens\n'+json.dumps({"pedidoId":pedido,"itens":norm},separators=(",",":"),ensure_ascii=False)
def digest(pedido,itens):return hashlib.sha256(canonical(pedido,itens).encode()).hexdigest()

work=pathlib.Path(tempfile.mkdtemp(prefix="pizzaria_4d_sql_"));data=work/"data";data.mkdir()
with (work/"init.log").open("xb") as log:
    p=subprocess.run([str(BIN/"mysqld.exe"),"--no-defaults","--initialize-insecure","--basedir="+str(BIN.parent),"--datadir="+str(data)],stdout=log,stderr=subprocess.STDOUT,creationflags=FLAGS,timeout=60)
assert p.returncode==0
with socket.socket() as sock:sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
assert port!=3306
server=subprocess.Popen([str(BIN/"mysqld.exe"),"--no-defaults","--basedir="+str(BIN.parent),"--datadir="+str(data),"--bind-address=127.0.0.1","--port="+str(port),"--mysqlx=OFF","--event-scheduler=OFF","--log-error="+str(work/"mysql.log")],creationflags=FLAGS)
password="";admin=None;verified=False;passed=[];facts={}
def session():
    db=DB(port,password,"fase4d_sintetica")
    assert db.sql("SELECT @@server_uuid,@@port,DATABASE()")[0]==[identity[2],str(port),"fase4d_sintetica"]
    db.sql("SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED")
    db.sql("SET SESSION innodb_lock_wait_timeout=6")
    return db
hash_value=digest(1,[{"produtoId":1,"quantidade":1}])
def insert(key,actor=1,pedido=1,hash_text=None,body='{"id":1}',expiry="NULL",status=200):
    return ("INSERT INTO idempotencia_adicao(usuario_id,pedido_id,chave,payload_hash,resposta_json,status_http,criado_em,expira_em) VALUES ("
        +str(actor)+","+str(pedido)+",0x"+key+",0x"+(hash_text or hash_value)+","+text_literal(body)+","+str(status)+",'2026-01-01 00:00:00',"+expiry+")")
def error(code,fn):
    try:fn()
    except SqlError as e:assert e.code in (code if isinstance(code,tuple) else (code,)),e.code;return
    raise AssertionError("SQL deveria ser rejeitado")
def count():return int(admin.sql("SELECT COUNT(*) FROM idempotencia_adicao")[0][0])
def snapshot():
    return {t:admin.sql("SELECT * FROM "+t+" ORDER BY id") for t in ("usuario","mesa","pedido","produto")}
def waits(number):
    limit=time.monotonic()+4
    while time.monotonic()<limit:
        n=int(admin.sql("SELECT COUNT(DISTINCT w.REQUESTING_THREAD_ID) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON t.THREAD_ID=w.REQUESTING_THREAD_ID WHERE t.PROCESSLIST_DB='fase4d_sintetica'")[0][0])
        if n>=number:return
        time.sleep(.005)
    raise AssertionError("Sobreposicao de locks nao comprovada")
def run(name,fn):
    admin.sql("DELETE FROM idempotencia_adicao")
    fn();passed.append(name);print("APROVADO "+name,flush=True)

try:
    for _ in range(100):
        assert server.poll() is None
        try:admin=DB(port,password);break
        except SqlError:time.sleep(.2)
    assert admin
    identity=admin.sql("SELECT VERSION(),@@port,@@server_uuid,@@datadir")[0]
    assert identity[0]=="8.0.42" and identity[1]==str(port) and pathlib.Path(identity[3]).resolve()==data.resolve()
    verified=True
    password=secrets.token_hex(32)
    admin.sql("ALTER USER 'root'@'localhost' IDENTIFIED BY '"+password+"'")
    admin.sql("CREATE DATABASE fase4d_sintetica");admin.close();admin=session()
    for ddl in ["CREATE TABLE usuario(id BIGINT PRIMARY KEY) ENGINE=InnoDB", "CREATE TABLE mesa(id BIGINT PRIMARY KEY,numero INT UNIQUE) ENGINE=InnoDB", "CREATE TABLE pedido(id BIGINT PRIMARY KEY,mesa_id BIGINT NOT NULL,status VARCHAR(30) NOT NULL,faturamento DECIMAL(38,2) NOT NULL,FOREIGN KEY(mesa_id) REFERENCES mesa(id)) ENGINE=InnoDB", "CREATE TABLE produto(id BIGINT PRIMARY KEY,estoque INT NOT NULL) ENGINE=InnoDB"]:admin.sql(ddl)
    admin.sql("INSERT INTO usuario VALUES(1),(2)");admin.sql("INSERT INTO mesa VALUES(1,1),(2,2)")
    admin.sql("INSERT INTO pedido VALUES(1,1,'ABERTO',50),(2,2,'ABERTO',50)");admin.sql("INSERT INTO produto VALUES(1,10)")
    baseline=snapshot();admin.sql((ROOT/"Dados_SQL/fase4c/proposta_idempotencia.sql").read_text(encoding="utf-8"))
    def structure():
        assert snapshot()==baseline
        cols=admin.sql("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='idempotencia_adicao' ORDER BY ORDINAL_POSITION")
        assert [r for r in cols if r[0]=='resposta_json']==[['resposta_json','longtext','NO']]
        assert [r for r in cols if r[0]=='expira_em']==[['expira_em','datetime(6)','YES']]
        assert admin.sql("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='idempotencia_adicao' AND INDEX_NAME='uk_idempotencia_adicao' ORDER BY SEQ_IN_INDEX")==[['usuario_id'],['pedido_id'],['chave']]
        assert admin.sql("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='idempotencia_adicao'")==[['2']]
        assert admin.sql("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='idempotencia_adicao' AND INDEX_NAME='PRIMARY'")==[['1']]
        assert admin.sql("SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='idempotencia_adicao' AND INDEX_NAME='ix_idempotencia_expira'")==[['expira_em']]
        assert admin.sql("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='idempotencia_adicao' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'")==[['3']]
    run("estrutura_indices_tipos_checks_e_preservacao",structure)
    def precision():
        body='{"id":1,"faturamento":12345678901234567890.12,"itens":[{"produtoNome":"muçarela 🍕","precoUnitario":12345678901234567890.12}]}'
        admin.sql(insert(uuid.uuid4().hex,body=body))
        stored=admin.sql("SELECT resposta_json FROM idempotencia_adicao")[0][0];assert stored.encode()==body.encode()
        native=admin.sql("SELECT CAST("+text_literal(body)+" AS JSON)")[0][0]
        facts['json_nativo_mudou_texto']=native!=body
        facts['json_nativo_mudou_valor_monetario']=json.loads(native,parse_float=Decimal)['faturamento']!=json.loads(body,parse_float=Decimal)['faturamento']
    run("snapshot_textual_utf8_e_precisao",precision)
    def constraints():
        key=uuid.uuid4().hex;admin.sql(insert(key));error(1062,lambda:admin.sql(insert(key,hash_text='11'*32)))
        admin.sql(insert(key,actor=2));admin.sql(insert(key,pedido=2));assert count()==3
        error(1452,lambda:admin.sql(insert(uuid.uuid4().hex,actor=999)))
        error(1452,lambda:admin.sql(insert(uuid.uuid4().hex,pedido=999)))
        error(3819,lambda:admin.sql(insert(uuid.uuid4().hex,status=204)))
        error(3819,lambda:admin.sql(insert(uuid.uuid4().hex,expiry="'2025-01-01'")))
        for body in ['null','[]','false','invalido']:error((3819,3141),lambda body=body:admin.sql(insert(uuid.uuid4().hex,body=body)))
        error(1048,lambda:admin.sql(insert(uuid.uuid4().hex).replace(text_literal('{"id":1}'),"NULL")))
        error(1048,lambda:admin.sql(insert(uuid.uuid4().hex).replace('0x'+hash_value,'NULL')))
        raw=insert(uuid.uuid4().hex);start=raw.index(',0x');end=raw.index(',',start+1)
        error(1048,lambda:admin.sql(raw[:start]+',NULL'+raw[end:]))
    run("unicidade_escopo_fks_nulos_json_status_prazo",constraints)
    def canonical_test():
        one=[{"produtoId":3,"quantidade":2}]
        assert digest(15,one)==digest(15,json.loads('[ { "quantidade": 2, "produtoId": 3 } ]'))
        assert digest(15,one)!=digest(16,one)
        assert digest(15,one)!=digest(15,[{"produtoId":3,"quantidade":1},{"produtoId":3,"quantidade":1}])
        assert digest(15,[{"produtoId":1,"quantidade":1},{"produtoId":2,"quantidade":1}])!=digest(15,[{"produtoId":2,"quantidade":1},{"produtoId":1,"quantidade":1}])
        assert uuid.UUID('ABCDEFAB-1234-1234-1234-ABCDEFABCDEF').bytes==uuid.UUID('abcdefab-1234-1234-1234-abcdefabcdef').bytes
        facts['vetor_hash_v1']=digest(15,one)
    run("vetores_canonicos_e_uuid",canonical_test)
    def competing(commit):
        key=uuid.uuid4().hex;a=session();b=session()
        try:
            a.sql("START TRANSACTION");a.sql(insert(key))
            def worker():
                b.sql("START TRANSACTION")
                try:b.sql(insert(key));b.sql("COMMIT");return 'inserida'
                except SqlError as e:b.sql("ROLLBACK");return e.code
            with futures.ThreadPoolExecutor(max_workers=1) as pool:
                f=pool.submit(worker);waits(1);a.sql('COMMIT' if commit else 'ROLLBACK')
                assert f.result(timeout=10)==(1062 if commit else 'inserida')
            assert count()==1
        finally:
            a.sql("ROLLBACK");b.sql("ROLLBACK");a.close();b.close()
    run("mesma_chave_aguarda_commit_e_rejeita_duplicata",lambda:competing(True))
    run("mesma_chave_aguarda_rollback_e_pode_inserir",lambda:competing(False))
    def different():
        barrier=threading.Barrier(2,timeout=6)
        def worker():
            db=session()
            try:db.sql('START TRANSACTION');db.sql(insert(uuid.uuid4().hex));barrier.wait();db.sql('COMMIT')
            finally:db.sql('ROLLBACK');db.close()
        with futures.ThreadPoolExecutor(max_workers=2) as pool:
            fs=[pool.submit(worker) for _ in range(2)]
            for f in fs:f.result(timeout=12)
        assert count()==2
    run("chaves_diferentes_com_transacoes_sobrepostas",different)
    def atomic():
        before=snapshot();db=session()
        try:
            db.sql('START TRANSACTION');db.sql('UPDATE produto SET estoque=estoque-1 WHERE id=1')
            db.sql('UPDATE pedido SET faturamento=faturamento+5 WHERE id=1');db.sql(insert(uuid.uuid4().hex));db.sql('ROLLBACK')
            assert snapshot()==before and count()==0
            db.sql('START TRANSACTION');db.sql('UPDATE produto SET estoque=estoque-1 WHERE id=1')
            error(1048,lambda:db.sql(insert(uuid.uuid4().hex).replace(text_literal('{"id":1}'),"NULL")))
            db.sql('ROLLBACK');assert snapshot()==before and count()==0
        finally:db.sql('ROLLBACK');db.close()
    run("rollback_de_registro_e_alteracoes_modelo_sql",atomic)
    def timeout():
        key=uuid.uuid4().hex;a=session();b=session()
        try:
            a.sql('START TRANSACTION');a.sql(insert(key));b.sql('SET SESSION innodb_lock_wait_timeout=1')
            def worker():
                b.sql('START TRANSACTION')
                try:b.sql(insert(key));return 'inesperado'
                except SqlError as e:b.sql('ROLLBACK');return e.code
            with futures.ThreadPoolExecutor(max_workers=1) as pool:
                f=pool.submit(worker);waits(1);assert f.result(timeout=7)==1205
            a.sql('ROLLBACK');assert count()==0
        finally:a.sql('ROLLBACK');b.sql('ROLLBACK');a.close();b.close()
    run("timeout_1205_sem_registro_parcial",timeout)
    def deadlock():
        keys=[uuid.uuid4().hex,uuid.uuid4().hex];barrier=threading.Barrier(2,timeout=6)
        def worker(i):
            db=session()
            try:
                db.sql('START TRANSACTION');db.sql(insert(keys[i]));barrier.wait()
                try:db.sql(insert(keys[1-i]));db.sql('COMMIT');return 'ok'
                except SqlError as e:db.sql('ROLLBACK');return e.code
            finally:db.sql('ROLLBACK');db.close()
        with futures.ThreadPoolExecutor(max_workers=2) as pool:
            fs=[pool.submit(worker,i) for i in range(2)];r=[f.result(timeout=12) for f in fs]
        assert sorted(map(str,r))==['1213','ok'] and count()==2
    run("deadlock_1213_induzido_fora_da_ordem_proposta",deadlock)
    def lock_model():
        key=uuid.uuid4().hex;holder=session()
        try:
            holder.sql('START TRANSACTION');holder.sql('SELECT id FROM mesa WHERE id=1 FOR UPDATE')
            def worker():
                db=session()
                try:
                    db.sql('START TRANSACTION');db.sql('SELECT id FROM mesa WHERE id=1 FOR UPDATE');db.sql('SELECT id FROM pedido WHERE id=1 FOR UPDATE')
                    existing=db.sql("SELECT id FROM idempotencia_adicao WHERE usuario_id=1 AND pedido_id=1 AND chave=0x"+key)
                    if not existing:db.sql('UPDATE produto SET estoque=estoque-1 WHERE id=1');db.sql(insert(key))
                    db.sql('COMMIT');return 'encontrada' if existing else 'inserida'
                finally:db.sql('ROLLBACK');db.close()
            before=snapshot()
            with futures.ThreadPoolExecutor(max_workers=2) as pool:
                fs=[pool.submit(worker) for _ in range(2)];waits(2);holder.sql('COMMIT');r=[f.result(timeout=12) for f in fs]
            assert sorted(r)==['encontrada','inserida'] and count()==1
            assert int(admin.sql('SELECT estoque FROM produto WHERE id=1')[0][0])==int(before['produto'][0][1])-1
            admin.sql('UPDATE produto SET estoque=10 WHERE id=1')
        finally:holder.sql('ROLLBACK');holder.close()
    run("modelo_sql_ordem_mesa_pedido_e_efeito_unico",lock_model)
    def retention():
        key=uuid.uuid4().hex;admin.sql(insert(key))
        assert admin.sql('SELECT expira_em FROM idempotencia_adicao')==[[None]]
        db=session()
        try:
            db.sql('START TRANSACTION');db.sql('SELECT id FROM mesa WHERE id=1 FOR UPDATE');db.sql('SELECT id FROM pedido WHERE id=1 FOR UPDATE')
            assert db.sql("SELECT status FROM pedido WHERE id=1")==[['ABERTO']]
            # Active rows are not eligible even with an artificially old date.
            assert db.sql("SELECT i.id FROM idempotencia_adicao i JOIN pedido p ON p.id=i.pedido_id WHERE p.status IN ('FINALIZADO','CANCELADO')")==[]
            db.sql("UPDATE pedido SET status='FINALIZADO' WHERE id=1")
            db.sql("UPDATE idempotencia_adicao SET expira_em='2026-02-01' WHERE pedido_id=1")
            db.sql("DELETE i FROM idempotencia_adicao i JOIN pedido p ON p.id=i.pedido_id WHERE p.status IN ('FINALIZADO','CANCELADO') AND i.expira_em<'2026-03-01'")
            assert db.sql('SELECT COUNT(*) FROM idempotencia_adicao')==[['0']]
            assert db.sql('SELECT status FROM pedido WHERE id=1')==[['FINALIZADO']]
            db.sql('ROLLBACK')
        finally:db.sql('ROLLBACK');db.close()
        assert count()==1
    run("retencao_ativa_e_limpeza_terminal_modelo_sql",retention)
    assert snapshot()==baseline
    facts.update(mysql=identity[0],port=port,uuid=identity[2],datadir=str(data),aprovados=len(passed),testes=passed,origem_acessada=False,limite='SQL/modelo somente; nao valida HTTP nem Services de idempotencia')
    print(json.dumps(facts,ensure_ascii=True,indent=2),flush=True)
finally:
    if admin:
        try:
            if verified:admin.sql('SHUTDOWN')
        finally:admin.close()
    if server.poll() is None:
        try:server.wait(timeout=20)
        except subprocess.TimeoutExpired:server.terminate();server.wait(timeout=20)
    print('MYSQL_DESCARTAVEL_ENCERRADO '+str(server.poll())+'; arquivos sinteticos '+str(work),flush=True)
