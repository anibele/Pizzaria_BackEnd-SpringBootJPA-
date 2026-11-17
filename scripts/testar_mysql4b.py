"""Windows: ensaio D09 em MySQL 8.0.42 novo. Nao conecta ao MySQL de trabalho.
Uso: python scripts/testar_mysql4b.py --mysql-bin "C:/Program Files/MySQL/MySQL Server 8.0/bin" --maven-cmd CAMINHO/mvn.cmd
Dependencias: apenas Python, MySQL local e Maven; nenhuma biblioteca Python externa.
"""
import argparse, json, os, pathlib, secrets, socket, subprocess, tempfile, time

parser=argparse.ArgumentParser()
parser.add_argument("--mysql-bin",required=True,type=pathlib.Path)
parser.add_argument("--maven-cmd",required=True,type=pathlib.Path)
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
assert os.name=="nt","Executor nativo para Windows"
flags=subprocess.CREATE_NO_WINDOW
work=pathlib.Path(tempfile.mkdtemp(prefix="pizzaria_d09_mysql_"))
data=work/"data";data.mkdir()
with (work/"inicializacao.log").open("xb") as log:
    init=subprocess.run([str(args.mysql_bin/"mysqld.exe"),"--no-defaults","--initialize-insecure","--basedir="+str(args.mysql_bin.parent),"--datadir="+str(data)],stdout=log,stderr=subprocess.STDOUT,creationflags=flags,timeout=60)
assert init.returncode==0,"Inicializacao isolada falhou; consultar log local"
with socket.socket() as sock:
    sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
assert port!=3306
process=subprocess.Popen([str(args.mysql_bin/"mysqld.exe"),"--no-defaults","--basedir="+str(args.mysql_bin.parent),"--datadir="+str(data),"--bind-address=127.0.0.1","--port="+str(port),"--mysqlx=OFF","--event-scheduler=OFF","--log-error="+str(work/"mysql.log")],creationflags=flags)
password="";verified=False;exitcode=1

def sql(statement):
    env=os.environ.copy();env["MYSQL_PWD"]=password
    result=subprocess.run([str(args.mysql_bin/"mysql.exe"),"--no-defaults","--protocol=TCP","--host=127.0.0.1","--port="+str(port),"--user=root","--batch","--skip-column-names"],input=statement.encode(),capture_output=True,env=env,creationflags=flags,timeout=15)
    if result.returncode:raise RuntimeError("Comando na instancia isolada falhou; credenciais omitidas")
    return result.stdout.decode().strip()

try:
    for _ in range(100):
        assert process.poll() is None,"MySQL isolado terminou"
        try:
            identity=sql("SELECT VERSION(),@@port,@@server_uuid,@@datadir").split("\t");break
        except RuntimeError:time.sleep(.2)
    else:raise RuntimeError("MySQL isolado nao iniciou")
    assert identity[0]=="8.0.42" and identity[1]==str(port)
    # mysql --batch escapes Windows paths; compare the normalized datadir before any write.
    server_dir=identity[3].replace("\\\\","\\")
    assert pathlib.Path(server_dir).resolve()==data.resolve(),"Datadir inesperado"
    assert len(identity[2])==36
    verified=True
    new_password=secrets.token_hex(32)
    sql("ALTER USER 'root'@'localhost' IDENTIFIED BY '"+new_password+"'")
    password=new_password
    sql("CREATE DATABASE fase4b_sintetica")
    print("ISOLAMENTO_COMPROVADO "+json.dumps({"version":identity[0],"port":port,"uuid":identity[2],"datadir":str(data)}),flush=True)
    env=os.environ.copy();env.update(TEST_MYSQL_URL="jdbc:mysql://127.0.0.1:"+str(port)+"/fase4b_sintetica?sessionVariables=innodb_lock_wait_timeout=8",TEST_MYSQL_PASSWORD=password,TEST_MYSQL_UUID=identity[2],TEST_MYSQL_DATADIR=str(data))
    with (root/"PizzariaMauaBackEnd/target/fase4b-mysql.log").open("wb") as log:
        result=subprocess.run([str(args.maven_cmd),"-o","test"],cwd=root/"PizzariaMauaBackEnd",env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=flags,timeout=240)
    exitcode=result.returncode
    print("MAVEN_EXIT "+str(exitcode),flush=True)
finally:
    if verified and process.poll() is None:
        sql("SHUTDOWN");process.wait(timeout=20)
    elif process.poll() is None:
        process.terminate();process.wait(timeout=20)
    print("MYSQL_ISOLADO_ENCERRADO "+str(process.poll())+"; temporarios sinteticos preservados em "+str(work),flush=True)
raise SystemExit(exitcode)
