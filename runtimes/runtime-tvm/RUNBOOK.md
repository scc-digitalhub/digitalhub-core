# runtime-tvm — guida operativa

Come far girare a mano l'intera catena TVM in locale, da zero: compilare TVM, costruire
le tre immagini, avviare l'infrastruttura, preparare un modello ed eseguire
`tvm+build` → `tvm+compile` → `tvm+serve` fino all'inferenza.

Vale per lo sviluppo su **minikube**. Per un cluster remoto cambiano solo il registry e i
valori di `RUNTIME_TVM_*`.

---

## 0. Cosa serve

| Strumento | Note |
|---|---|
| **JDK 21** | `~/.sdkman/candidates/java/21.0.4-graal`. **Non** JDK 25: da `javac` 23 in poi Lombok è disabilitato e la compilazione fallisce con `cannot find symbol` incomprensibili |
| docker | driver di minikube e builder delle immagini |
| minikube | cluster locale |
| cmake, ninja, `llvm-config-18`, g++, git | solo per compilare TVM |
| conda (`converter_env`) | solo per convertire modelli ONNX → TFLite |

Verifica rapida:

```bash
~/.sdkman/candidates/java/21.0.4-graal/bin/java -version   # deve dire 21.x
for t in docker minikube cmake ninja llvm-config-18 g++ git; do command -v $t >/dev/null || echo "MANCA: $t"; done
```

---

## 1. Da cosa dipende cosa

```
   TVM compilato da sorgente  (~/tvm/src/tvm-0.25.0)
        │  .so nativi + tree python/tvm
        ├────────────────┬────────────────────┐
        ▼                ▼                    ▼
   tvm-toolkit      tvm-runtime-go       tvm-runtime-rust
   (build+compile)  (serve, default)     (serve, alternativo)
        │                │                    │
        └────────────────┴────────────────────┘
                         │  push nel registry locale + load nel nodo
                         ▼
                    minikube  ←──  CORE (da sorgente, ./run.sh)
```

Tutte e tre le immagini **impacchettano una TVM compilata localmente**: senza il passo 2
nessuna si costruisce. Il toolkit ci copia dentro il tree Python, i due runtime di serve
ci si linkano contro (`libtvm_runtime.so`, `libtvm_ffi.so`).

---

## 2. Compilare TVM

Serve una sola volta per versione. È l'unico passo lungo: **20-60 minuti**.

```bash
cd ~/IdeaProjects/digitalhub-tvm-toolkit
./build-tvm.sh                      # versione di default (0.25.0)
TVM_VERSION=0.25.0 ./build-tvm.sh   # esplicita
FORCE=1 ./build-tvm.sh              # riconfigura e ricompila da capo
```

Lo script clona il tag `v$TVM_VERSION` in `~/tvm/src/tvm-$TVM_VERSION`, genera
`config.cmake` con `USE_LLVM=llvm-config-18` e compila con ninja. È **idempotente**: se
trova già `build/lib` esce subito.

Al termine crea/aggiorna il symlink usato da tutti gli altri script:

```bash
ls -l ~/tvm/src/tvm-current      # -> tvm-0.25.0
```

> ### ⚠️ `TVM_HOME` del profilo è rotto
>
> Il `.zshrc` esporta `TVM_HOME=/home/ltrubbiani/tvm/tvm`, **una directory che non
> esiste**. Se lasci che gli script lo ereditino, falliscono con `no TVM build at …`.
>
> **Passa sempre `TVM_HOME` esplicitamente** in tutti i comandi di build delle immagini:
>
> ```bash
> TVM_HOME=~/tvm/src/tvm-current ./build-image.sh
> ```

---

## 3. Costruire e distribuire le tre immagini

### 3.1 Il registry locale

Un `registry:2` sull'host, che il cluster vede come `192.168.49.1:5000`:

```bash
docker start registry     # se esiste già (restart=always, di solito è su)

# se non esiste:
docker run -d --restart=always -p 5000:5000 --name registry -v registry-data:/var/lib/registry registry:2
```

> ### ⚠️ Il push va fatto su `127.0.0.1:5000`, non su `192.168.49.1:5000`
>
> Il registry è in HTTP. Docker si fida di `127.0.0.0/8` come insecure registry, ma **non**
> di `192.168.49.1`, quindi un push su quest'ultimo fallisce con
> `http: server gave HTTP response to HTTPS client`.
>
> Le due forme puntano allo **stesso archivio**: si pusha via `127.0.0.1:5000` e si
> referenzia via `192.168.49.1:5000`. Aggiungere `192.168.49.1:5000` agli
> `insecure-registries` di docker richiederebbe di riavviare il demone, **che uccide
> minikube** (driver docker, Live Restore disattivo). Non farlo.

### 3.2 tvm-toolkit — esegue `tvm+build` e `tvm+compile`

```bash
cd ~/IdeaProjects/digitalhub-tvm-toolkit
TVM_HOME=~/tvm/src/tvm-current ./build-image.sh
```

Prepara il contesto (`.so` strippati + `python/tvm`), **applica le patch in `patches/`**
e costruisce `tvm-toolkit:0.25`. Il tag è derivato dalla major.minor della TVM
impacchettata, così contenuto e tag non divergono mai.

Le patch sono obbligatorie: se una non applica il build **si interrompe**, di proposito.
Un'immagine silenziosamente non patchata si costruisce benissimo e poi fallisce a runtime.
Vedi §10 per cosa contengono.

### 3.3 tvm-runtime-rust — serve alternativo

```bash
cd ~/IdeaProjects/digitalhub-tvm-rust
TVM_HOME=~/tvm/src/tvm-current ./build-image.sh
```

### 3.4 tvm-runtime-go — serve di default (Nuclio)

```bash
cd ~/IdeaProjects/digitalhub-serverless
TVM_HOME=~/tvm/src/tvm-current ./images/tvm/build.sh
```

### 3.5 Distribuirle — **il passo che si dimentica**

Costruire l'immagine non basta. Vanno messe **sia nel registry sia nella cache del nodo**,
sotto il riferimento esatto che CORE userà:

```bash
for img in tvm-toolkit tvm-runtime-rust tvm-runtime-go; do
  docker tag $img:0.25 192.168.49.1:5000/$img:0.25
  docker tag $img:0.25 127.0.0.1:5000/$img:0.25
  docker push 127.0.0.1:5000/$img:0.25          # push via 127, vedi §3.1
  minikube image load 192.168.49.1:5000/$img:0.25
done
```

Verifica che i digest coincidano:

```bash
docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' | grep -E '^tvm-'
minikube ssh -- 'sudo crictl images | grep tvm-'
```

> ### ⚠️ Collisione di tag
>
> Il tag resta `0.25` mentre il contenuto cambia. Con `imagePullPolicy: IfNotPresent` il
> nodo **si tiene la sua copia** e i Job continuano a girare il codice vecchio, senza
> alcun errore. È il modo più subdolo di perdere ore.
>
> Dopo ogni ricostruzione **confronta i digest** come sopra. Se divergono, rifai
> `minikube image load`. In alternativa `minikube ssh -- sudo crictl pull <ref>`.
>
> **Mai `minikube image rm` su un'immagine in uso.**

---

## 4. Avviare l'infrastruttura

### 4.1 minikube

```bash
minikube start
minikube status                                            # host/kubelet/apiserver Running
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.49.2:30900/minio/health/live   # MinIO: 200
```

### 4.2 CORE

CORE **non parte senza la configurazione S3**: si avvia, ma ogni run fallisce con
`BackoffLimitExceeded` perché nessun file store è registrato. La configurazione sta in
`~/.dhcore-minikube.env`:

```bash
JAVA_HOME=/home/ltrubbiani/.sdkman/candidates/java/21.0.4-graal

# S3 / MinIO
S3_CREDENTIALS_PROVIDER=true
S3_ENDPOINT_URL=http://192.168.49.2:30900
S3_BUCKET=digitalhub
S3_PATH_STYLE_ACCESS=true
AWS_ACCESS_KEY=minio
AWS_SECRET_KEY=minio123
AWS_ACCESS_KEY_ID=minio
AWS_SECRET_ACCESS_KEY=minio123
AWS_DEFAULT_REGION=us-east-1
FILES_DEFAULT_STORE=s3://digitalhub

# callback dal cluster verso CORE (IP dell'host visto da minikube)
DH_ENDPOINT=http://192.168.49.1:8080

# auth locale: OBBLIGATORIA, vedi sotto
DH_AUTH_BASIC_USER=admin
DH_AUTH_BASIC_PASSWORD=admin

# immagini dal registry locale (i default ghcr danno 403)
RUNTIME_TVM_BUILDER_ONNX=192.168.49.1:5000/tvm-toolkit:0.25
RUNTIME_TVM_BUILDER_TFLITE=192.168.49.1:5000/tvm-toolkit:0.25
RUNTIME_TVM_COMPILER=192.168.49.1:5000/tvm-toolkit:0.25
RUNTIME_TVM_SERVE=192.168.49.1:5000/tvm-runtime-go:0.25

# Kaniko (compile stage 2) + flag per il registry http
IMAGE_REGISTRY=192.168.49.1:5000
KANIKO_ARGS=--insecure,--skip-tls-verify,--insecure-pull
```

Compilazione e avvio:

```bash
cd ~/IdeaProjects/digitalhub-core

# build completo (solo dopo modifiche a piu' moduli)
JAVA_HOME=~/.sdkman/candidates/java/21.0.4-graal PATH="$JAVA_HOME/bin:$PATH" ./build.sh

# oppure il solo runtime-tvm, molto piu' veloce
JAVA_HOME=~/.sdkman/candidates/java/21.0.4-graal ./mvnw -o -pl runtimes/runtime-tvm -DskipTests install

# avvio
set -a; source ~/.dhcore-minikube.env; set +a
export PATH="$JAVA_HOME/bin:$PATH"
./run.sh default
```

Verifica:

```bash
curl -s -u admin:admin 'http://localhost:8080/api/v1/-/tvm-rust/runs?size=1' -o /dev/null -w '%{http_code}\n'   # 200
curl -s http://localhost:8080/.well-known/configuration | python3 -m json.tool | grep -E 's3_bucket|aws_endpoint'
```

Console su <http://localhost:8080/console/> (admin/admin).

> ### ⚠️ Tre cose che fanno perdere tempo qui
>
> **L'auth non è opzionale.** `TvmRuntime.run()` allega le credenziali S3 solo dentro
> `if (auth != null)`. Senza `DH_AUTH_BASIC_*` l'API è aperta ma `auth` è null, quindi ai
> Job non arriva nessuna credenziale e l'init container muore con
> `Unable to locate credentials`. Endpoint e bucket invece arrivano lo stesso (vengono
> dalle Configurations, che non richiedono auth): è questo che rende l'errore confuso.
>
> **`run.sh` risolve i moduli da `~/.m2`,** non dal working tree. Dopo aver modificato
> `runtime-tvm` devi rifare `mvn install` di quel modulo, altrimenti giri il jar vecchio.
> Vale anche per gli script pod (`builder_*.py`, `compiler.py`, `entrypoint.sh`): sono
> risorse **dentro il jar**, quindi modificarli richiede `mvn install` **e** riavvio.
>
> **Il build sporca file tracciati.** `openapi.json` e i vari `.flattened-pom.xml` sono
> output di build ma versionati. Dopo un `./build.sh` completo:
> ```bash
> git checkout -- modules/component-container-images/.flattened-pom.xml
> rm -f modules/component-run-initializer/.flattened-pom.xml
> ```
> Non usare mai `git add -A` in questo repo: c'è anche il submodule `frontend/console`
> che risulta modificato di suo.

---

## 5. Preparare un modello

### 5.0 Due assi indipendenti: formato e quantizzazione

Sono cose diverse e vanno tenute separate, perché il codice le tratta separatamente.

**Formato** è come il modello è serializzato: `onnx` o `tflite`. È l'unica cosa che
`TvmFormat` descrive, e determina solo quale builder gira.

**Quantizzazione** è come sono rappresentati i numeri. Non ha niente a che vedere col
formato: esistono tutte le combinazioni.

| | float32 | int8 interno, bordi float32 | int8 anche ai bordi |
|---|---|---|---|
| **ONNX** | export normale | QDQ | QDQ con I/O int8 |
| **TFLite** | `*_float32` | `*_integer_quant` | `*_full_integer_quant` |

Le due colonne quantizzate contengono **gli stessi pesi int8** e pesano uguale: cambia
solo dove avviene la conversione affine. In `integer_quant` il grafo ha un nodo
`QUANTIZE` come prima operazione e `DEQUANTIZE` come ultima, quindi converte da sé. In
`full_integer_quant` quei nodi non ci sono e la conversione tocca al chiamante.

Da qui discende la regola che conta:

> I parametri di quantizzazione (`scale`, `zero_point`) servono **se e solo se un tensore
> di bordo è int8/uint8**, indipendentemente dal formato. Entrambi i builder li estraggono
> — dal tensore in TFLite, dai nodi QDQ in ONNX — e li scrivono in `metadata.json`; i
> serve li espongono su `/v2/models`; i client li usano per quantizzare l'input e
> dequantizzare l'output. Con bordi float32 nulla di tutto questo si attiva.

Quale scegliere: `full_integer_quant` se il modello deve girare anche su un acceleratore
edge (la Coral EdgeTPU accetta solo int8 e non può ospitare i nodi QUANTIZE/DEQUANTIZE),
o se volete servire in piattaforma **lo stesso identico file** che va sul dispositivo.
`integer_quant` se servite solo da CORE: stessa compressione e stessa velocità, ma
interfaccia float e client banale.

### 5.1 Convertire ONNX → TFLite (facoltativo)

```bash
conda activate converter_env          # obbligatorio: nel base mancano onnx, onnx2tf, tensorflow, onnxsim
cd ~/yolotest/deploy
python convert.py --onnx ../model.onnx --out ./models
python convert.py --onnx ../model.onnx --out ./models --calib-images ~/mie_foto   # calibrazione sul tuo dominio
```

L'INT8 è il comportamento **di default**: non c'è un flag da attivare, c'è `--no-int8` da
non mettere. Produce sette varianti; quelle che interessano:

| Variante | I/O | Stato con TVM 0.25 |
|---|---|---|
| `*_full_integer_quant.tflite` | int8 → int8 | ✅ validato end-to-end |
| `*_integer_quant.tflite` | float32 → float32 (pesi int8) | ✅ build+compile |
| `*_float32.tflite` | float32 | ✅ |
| `*_dynamic_range_quant.tflite` | — | ❌ rifiutato da TVM, e correttamente: le scale delle attivazioni si calcolano a runtime e non stanno nel file |
| `*_float16.tflite` | — | ❌ vedi §10 |

Il set di calibrazione viene **letterboxato** (`build_calibration`), il che determina il
preprocessing corretto in inferenza: vedi §7.

### 5.2 Caricare il modello in CORE

Il file va caricato **attraverso CORE**, non direttamente su MinIO: altrimenti il file c'è
ma la piattaforma non lo sa.

```bash
P=tvm-rust; NAME=mio-modello; FILE=~/yolotest/deploy/models/model_full_integer_quant.tflite

# 1) chiedi a CORE una URL presigned
curl -s -u admin:admin -X POST "http://localhost:8080/api/v1/-/$P/files/upload" \
     --data-urlencode "path=s3://digitalhub/$P/model/$NAME/" \
     --data-urlencode "filename=$(basename $FILE)" > /tmp/upl.json

# 2) carica i byte
URL=$(python3 -c "import json;print(json.load(open('/tmp/upl.json'))['url'])")
curl -X PUT --upload-file "$FILE" "$URL"

# 3) crea l'entita' Model con il path definitivo
curl -s -u admin:admin -X POST "http://localhost:8080/api/v1/-/$P/models" \
  -H 'Content-Type: application/json' -d "{
    \"kind\":\"model\",\"name\":\"$NAME\",\"project\":\"$P\",
    \"spec\":{\"path\":\"s3://digitalhub/$P/model/$NAME/$(basename $FILE)\",\"parameters\":{}}}"
```

> ### ⚠️ Lo spec di un Model è immutabile
>
> Un `PUT` per correggere `spec.path` risponde **200 e rispedisce il vecchio spec**, senza
> applicare nulla e senza errori. Il path va messo giusto alla creazione: non creare
> l'entità con un placeholder pensando di correggerla dopo.

---

## 6. Eseguire la pipeline

Dalla console è più comodo. Da API, per automatizzare:

```bash
P=tvm-rust; MID=<id del Model>
FREF() { echo "tvm://$P/$1:$2"; }

# funzione
curl -s -u admin:admin -X POST "http://localhost:8080/api/v1/-/$P/functions" \
  -H 'Content-Type: application/json' -d "{
    \"kind\":\"tvm\",\"name\":\"mia-funzione\",\"project\":\"$P\",
    \"spec\":{\"model\":\"store://$P/model/model/mio-modello:$MID\",\"format\":\"auto\"}}"

# task (uno per fase) e run: vedi lo schema sotto
```

Payload minimi:

```jsonc
// task
{"kind":"tvm+build",   "project":"tvm-rust",
 "spec":{"function":"tvm://tvm-rust/mia-funzione:<fid>","resources":{"cpu":"4","mem":"8Gi"}}}

// run
{"kind":"tvm+build:run","project":"tvm-rust",
 "spec":{"task":"tvm+build://tvm-rust/<tid>","function":"tvm://tvm-rust/mia-funzione:<fid>",
         "local_execution":false,"resources":{"cpu":"4","mem":"8Gi"}}}
```

Per `tvm+compile` aggiungi `"target_architecture":"cpu"` (o `x86`, `arm64`, `armv7l`) e
`"opt_level":3`. Per `tvm+serve` non serve indicare il modello: viene da
`function.spec.so_model`, scritto automaticamente dal compile.

Il concatenamento è automatico: build scrive `function.spec.ir_model`, compile legge
quello e scrive `so_model`, serve legge quello.

> ### ⚠️ Risorse
>
> Con il default (**282 MB**) il compile va in OOM. Metti sempre
> `resources: {cpu: "4", mem: "8Gi"}` su build e compile. Un IR quantizzato sta anche in
> 4 GB; uno float32 di YOLO no.

Stato di un run:

```bash
curl -s -u admin:admin "http://localhost:8080/api/v1/-/tvm-rust/runs/<id>" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);s=d['status'];print(s['state'],'|',s.get('outputs'),'|',str(s.get('message'))[:120])"
```

Log di un run fallito (arrivano in base64):

```bash
curl -s -u admin:admin "http://localhost:8080/api/v1/-/tvm-rust/logs?run=<id>&size=5" \
  | python3 -c "
import json,sys,base64
for l in json.load(sys.stdin).get('content',[]):
    try: print(base64.b64decode(l.get('content','')).decode('utf-8','replace')[-1500:])
    except Exception: pass"
```

---

## 7. Testare l'inferenza

```bash
cd ~/IdeaProjects/digitalhub-core/runtimes/runtime-tvm/test_infer

python3 run_infer.py                              # YOLOv8-detect (84 canali)
python3 test_xinet.py --function xinet-quant      # YOLOv8-pose (56 canali), disegna gli scheletri
```

I client trovano da soli il serve RUNNING, aprono un port-forward, leggono shape, dtype e
parametri di quantizzazione da `/v2/models`, quantizzano l'input e dequantizzano l'output.
Con più serve attivi disambigua con `--run <id>`.

Opzioni utili: `--image <path|url>`, `--mode grpc`, `--conf 0.3`, `--out <file>`,
`--dump-json <file>` (solo `test_xinet.py`).

> ### ⚠️ Preprocessing: letterbox, non stretch
>
> Il default è il **letterbox**, e non è una preferenza estetica: `build_calibration` in
> `convert.py` letterboxa il set di calibrazione, quindi **le scale della quantizzazione
> sono tarate su quella distribuzione**. Passare immagini stirate significa darle al
> modello fuori dalla distribuzione su cui è stato calibrato. È anche ciò che ultralytics
> usa in training e inferenza.
>
> `test_xinet.py --stretch` riproduce il comportamento di `tvm-edge/plugins/xinet-pose`,
> che deforma. Serve solo per confronto.

### Confronto numerico con un riferimento esterno

Per verificare che il `.so` compilato da TVM dia gli stessi numeri dell'interprete TFLite:

```bash
python3 test_xinet.py --function xinet-quant --dump-json /tmp/kp_core.json
# ... esporta lo stesso formato dall'altra implementazione ...
python3 compare_keypoints.py /tmp/kp_core.json kp_riferimento.json --step 3.8638
```

Due avvertenze. Le coordinate vanno esportate **nello spazio del modello (224×224), prima
di invertire il letterbox**: è l'unico spazio comune fra pipeline che disegnano su
superfici diverse. E il passo di quantizzazione (`scale_output × lato`, per xinet
`0.017249212 × 224 = 3.86 px`) è il limite di risoluzione: due implementazioni entrambe
corrette possono differire fino a un passo.

---

## 8. Spegnere

```bash
pkill -f 'spring-boot:run'    # CORE
minikube stop                 # cluster (stop, non delete: dati e immagini restano)
docker stop registry          # opzionale
```

---

## 9. Riavvio rapido (tutto già costruito)

```bash
docker start registry
minikube start
cd ~/IdeaProjects/digitalhub-core
set -a; source ~/.dhcore-minikube.env; set +a
export PATH="$JAVA_HOME/bin:$PATH"
./run.sh default
```

Poi **rilancia il serve dalla console**: vedi §10.

---

## 10. Guasti frequenti

### I run vanno in ERROR dopo un riavvio di minikube

Normale. Mentre il cluster è giù CORE non raggiunge l'apiserver
(`NoRouteToHostException`) e marca i run come falliti. Al riavvio il pod torna su da solo
(passa da `Unknown` a `1/1 Running`) ma il run resta ERROR e i client non lo trovano più.

**Rilancia solo il `tvm+serve`**, non build e compile: `so_model` è ancora valido.
Elimina prima il deployment orfano:

```bash
minikube kubectl -- delete deploy -n default d-tvmserve-<runid>
```

### Il Job gira codice vecchio

Digest divergenti fra docker e nodo. Vedi §3.5.

### `no TVM build at …`

`TVM_HOME` ereditato dal profilo, che punta a una directory inesistente. Vedi §2.

### `http: server gave HTTP response to HTTPS client`

Push su `192.168.49.1:5000` invece che su `127.0.0.1:5000`. Vedi §3.1.

### `cannot detect the TVM source format of '…'`

`format: auto` su una sorgente senza estensione riconoscibile (`store://` o cartella).
Metti `format` esplicito (`onnx` o `tflite`) nella spec della funzione.

### `The following quantized TFLite operators are not supported`

Il modello usa un'operazione quantizzata non in allowlist. **Non aggiungerla a caso**:
verifica prima che il converter la implementi davvero per il caso quantizzato.

Per sapere subito quali mancano tutte, invece di scoprirle una alla volta:

```bash
docker run --rm -v <dir-modelli>:/m:ro \
  -v ~/tvm/src/tvm-current/python/tvm/relax/frontend/tflite:/fe:ro \
  --entrypoint python3 tvm-toolkit:0.25 -c "
import re, tflite
from tflite.BuiltinOperator import BuiltinOperator
code2name={v:k for k,v in vars(BuiltinOperator).items() if not k.startswith('_')}
m=tflite.Model.GetRootAsModel(open('/m/<modello>.tflite','rb').read(),0)
used={code2name.get(max(m.OperatorCodes(i).DeprecatedBuiltinCode(), m.OperatorCodes(i).BuiltinCode()),'?')
      for i in range(m.OperatorCodesLength())}
blk=re.search(r'_SUPPORTED_QUANTIZED_OPS = frozenset\(\s*\{(.*?)\}\s*\)', open('/fe/tflite_frontend.py').read(), re.S).group(1)
print('MANCANTI:', sorted(used - set(re.findall(r'\"([A-Z_0-9]+)\"', blk))))"
```

Poi aggiungi l'operazione a `patches/tflite-quantized-ops.patch`, ricostruisci e
ridistribuisci il toolkit (§3.2, §3.5).

### Le patch TVM che portiamo

`digitalhub-tvm-toolkit/patches/tflite-quantized-ops.patch`, applicata a ogni build.
Tre correzioni al frontend Relax TFLite di TVM 0.25:

1. **Sei operazioni assenti dall'allowlist quantizzata** benché il converter le
   implementi: `MAX_POOL_2D`, `PAD`, `RESIZE_NEAREST_NEIGHBOR`, `STRIDED_SLICE`,
   `TRANSPOSE` (spostano soltanto elementi, e TFLite dà a input e output gli stessi
   parametri di quantizzazione) e `AVERAGE_POOL_2D` (fa aritmetica, ma `convert_pool2d`
   ha già un percorso corretto: casta a int32 per accumulare, media, ricasta).
2. **Scalari 0-d nel QDQ**: `relax.op.quantize/dequantize` validano
   `0 <= axis <= ndim-1`, e un operando scalare non ha assi validi. Sollevati a rango 1 e
   riportati indietro.
3. **`relax.op.cast` / `relax.cast` non esistono** in TVM 0.25 (l'operazione è
   `relax.op.astype`). Era codice morto raggiungibile solo dopo la correzione 1; sistemarlo
   sblocca anche i modelli **float16**, che fallivano proprio lì.

Vanno riapplicate a ogni aggiornamento di TVM, finché non arrivano upstream.

### `/tmp` viene ripulito

Il file di configurazione di CORE stava in `/tmp` e spariva a ogni giro: ora è in
`~/.dhcore-minikube.env`. Non rimetterlo in `/tmp`.

### Modelli pubblicati con kind generico `model`

L'immagine toolkit installa `digitalhub` da PyPI, e la versione pubblicata non espone
ancora `log_tvm_ir`/`log_tvm_so`. `_dh_publish.py` ripiega su `dh.log_model(kind="model")`
ripiegando i campi tipizzati in `parameters`. La catena funziona lo stesso; per avere i
kind `tvm-ir`/`tvm-so` serve una release dell'SDK e poi **ricostruire il toolkit**.

---

## 11. Riferimento rapido

```bash
# TVM (una volta, 20-60 min)
cd ~/IdeaProjects/digitalhub-tvm-toolkit && ./build-tvm.sh

# immagini
TVM_HOME=~/tvm/src/tvm-current ./build-image.sh                        # toolkit
cd ~/IdeaProjects/digitalhub-tvm-rust  && TVM_HOME=~/tvm/src/tvm-current ./build-image.sh
cd ~/IdeaProjects/digitalhub-serverless && TVM_HOME=~/tvm/src/tvm-current ./images/tvm/build.sh

# distribuzione
docker tag X:0.25 127.0.0.1:5000/X:0.25 && docker push 127.0.0.1:5000/X:0.25
docker tag X:0.25 192.168.49.1:5000/X:0.25 && minikube image load 192.168.49.1:5000/X:0.25

# infrastruttura
docker start registry && minikube start
cd ~/IdeaProjects/digitalhub-core
set -a; source ~/.dhcore-minikube.env; set +a; export PATH="$JAVA_HOME/bin:$PATH"; ./run.sh default

# test
cd runtimes/runtime-tvm/test_infer && python3 test_xinet.py --function xinet-quant

# spegnimento
pkill -f 'spring-boot:run'; minikube stop
```
