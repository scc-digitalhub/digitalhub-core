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
| `--input-name` / `--input-shape` | `images` / `1,3,640,640` | usati solo se il serve non espone i metadata (es. backend Go) |
| `--conf` | `0.25` | soglia di confidenza per i box |

## Note
- Il backend **rust** espone i metadata del modello (input/output) via `/v2/models/<name>`;
  il backend **go** no → in quel caso si usano `--input-name`/`--input-shape` (default YOLOv8).
- Funziona identico per entrambi i backend: stessa API OpenInference v2.
