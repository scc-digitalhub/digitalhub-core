# test_infer — inferenza end-to-end contro un serve TVM lanciato da CORE

`run_infer.py` prova un modello servito dal runtime TVM **scoprendo l'endpoint
dall'API di CORE** (non dal pod): trova il run `tvm+serve:run` RUNNING, ricava il
Service che CORE ha creato e il nome del modello, scarica un'immagine di test,
chiama l'inferenza in **REST** o **gRPC** e salva l'immagine con i **bounding box**
(decodifica YOLOv8 + NMS + classi COCO).

## Prerequisiti
- CORE avviato (default `http://localhost:8080`, basic auth `admin/admin`) con
  **almeno un `tvm+serve` in stato RUNNING**.
- `minikube` (kubectl) — il Service è ClusterIP, quindi lo si raggiunge via
  `kubectl port-forward` del Service creato da CORE.
- Python: `numpy`, `Pillow`; per `--mode grpc` anche `grpcio` + `grpcio-tools`
  (il proto KServe v2 è qui accanto: `grpc_predict_v2.proto`).

## Uso
```bash
cd runtimes/runtime-tvm/test_infer

python3 run_infer.py                       # REST, run RUNNING auto, scarica bus.jpg
python3 run_infer.py --mode grpc           # stessa inferenza via gRPC (:9000)
python3 run_infer.py --image /path/foto.jpg
python3 run_infer.py --run <run-id> --project tvm-rust --conf 0.3
```
Output: elenco rilevamenti in console + immagine annotata (`detections.jpg`).

## Opzioni principali
| Flag | Default | Significato |
|---|---|---|
| `--mode` | `rest` | `rest` (/v2/.../infer su :8080) o `grpc` (ModelInfer su :9000) |
| `--project` | `tvm-rust` | progetto CORE in cui cercare il run serve |
| `--run` | (auto) | id di un `tvm+serve:run` specifico; default = il primo RUNNING |
| `--image` | (bus.jpg) | path locale o URL; se omesso scarica `--image-url` |
| `--core` / `--user` / `--password` | localhost:8080 / admin / admin | endpoint e credenziali CORE |
| `--input-name` / `--input-shape` | `images` / `1,3,640,640` | usati solo se il serve non espone i metadata |
| `--conf` | `0.25` | soglia di confidenza per i box |

## Note
- Entrambi i backend (**rust** e **go**) espongono i metadata del modello (input/output)
  via `/v2/models/<name>`, letti da `metadata.json`; `--input-name`/`--input-shape`
  restano come fallback per modelli pubblicati senza metadata (default YOLOv8).
- Funziona identico per entrambi i backend: stessa API OpenInference v2.
- Il plumbing comune (scoperta del run dall'API di CORE, port-forward, trasporto
  REST/gRPC con compilazione al volo del proto) è in `_client.py`, condiviso da
  `run_infer.py` e `test_xinet.py` — i due script vanno lanciati da questa directory.

## test_xinet.py — pose estimation per il modello `xinet`
`xinet` è un modello **YOLOv8-pose** (persona + 17 keypoint COCO): output
`[1, 56, 1029]` = `4(box) + 1(conf) + 17*3(keypoint x,y,vis)` su `1029 = 28²+14²+7²`
anchor (input 224). `test_xinet.py` è il gemello di `run_infer.py` per questo modello e
**segue il client di riferimento** `tvm-edge/plugins/xinet-pose`: stessa scoperta del
serve dall'API di CORE, stesso port-forward, stessa API v2 (REST/gRPC). Preferisce fra
i serve RUNNING quello della function `xinet-function`, legge shape/dtype dai metadata,
prepara l'input con **resize pieno a `224x224` (stretch, no letterbox), RGB, `/255`,
NCHW** (il pre-processing con cui il modello è servito), fa il sanity check shape/dtype
+ statistiche, poi **decodifica gli scheletri**: filtra le persone per confidenza
(`--conf`), NMS (il box serve solo a questo, **non** viene disegnato), riproietta i
keypoint da 224 alla risoluzione originale e traccia i **19 segmenti dello scheletro**
(un colore per limb, palette jet) + i giunti **sull'immagine ORIGINALE a piena
risoluzione** (`--out`, default `xinet_out.png`). Soglia keypoint = `--kp-conf`.
```bash
python3 test_xinet.py                       # REST, serve xinet RUNNING auto
python3 test_xinet.py --mode grpc           # stessa inferenza via gRPC (:9000)
python3 test_xinet.py --conf 0.3 --kp-conf 0.3 --out pose.png
python3 test_xinet.py --image /path/foto.jpg
```
Canali dell'output (verificati sull'infer grezzo): `0-3` box `cx,cy,w,h` (px 0–224),
`4` conf persona (già `[0,1]`), `5-55` = 17 keypoint × `(x, y, vis)` (x,y in px 0–224,
vis già `[0,1]`). Soglie default `--conf 0.2` / `--kp-conf 0.2` (come il riferimento).
I/O (TVM 0.25, ONNX): input `images` FP32 `[1,3,224,224]` → output `output0` FP32
`[1,56,1029]`. Su bus.jpg disegna 3 scheletri (2 completi) a piena risoluzione; REST e
gRPC producono output bit-identico.
