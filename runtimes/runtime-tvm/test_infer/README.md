# test_infer — testing models served by CORE

Test scripts for YOLOv8 models served by `tvm+serve`. They find the serve through the CORE
API (or use an address you already have, such as a `dhcli port-forward`), call the Open
Inference Protocol v2 over REST or gRPC, and draw the result on the picture.

## Requirements

- Python 3 with `numpy` and `Pillow`; for `--mode grpc` also `grpcio` and `grpcio-tools`.
- A serve in state **RUNNING**, reachable in one of these ways:
  - **local CORE**: CORE on `http://localhost:8080` (user `admin`/`admin` by default) and
    `kubectl` towards its cluster (`minikube kubectl` is used when `kubectl` is missing);
  - **any CORE**: a `dhcli port-forward` to the serve, passed with `--url`.

## The two tests

| Test | Model | Output | Result |
| --- | --- | --- | --- |
| `yolo.py` | YOLOv8 detection | `[1, 4+classes, N]`, e.g. `[1, 84, 8400]` | boxes in `output/<function>.jpg` |
| `xinet.py` | YOLOv8-pose (people + 17 keypoints) | `[1, 56, N]` | skeletons in `output/<function>.jpg` |

```bash
cd digitalhub-core/runtimes/runtime-tvm/test_infer

python3 yolo.py                                   # serve of the function yolo, REST
python3 yolo.py --mode grpc --image ~/photo.jpg --conf 0.3
python3 xinet.py                                  # serve of the function xinet, REST
python3 xinet.py --function xinet-quant --dump-json output/kp.json
```

Without `--image` the tests use `bus.jpg`, downloaded once into `output/`.

## Serve on a remote CORE

On a remote CORE neither kubectl nor user and password are needed: `dhcli` logs in and opens
an authenticated port-forward to the serve, and the scripts use it with `--url`.

```bash
# 1. log in (opens the browser; renew an expired session with: dhcli refresh)
dhcli login

# 2. the RUNNING serves of the project
dhcli services -p <project>

# 3. port-forward to the serve of the function: keep it open in a terminal
dhcli port-forward -p <project> -f <function> -l 18080

# 4. in another terminal: quick check and test
curl http://localhost:18080/v2/health/ready
curl http://localhost:18080/v2/models/<function>
python3 yolo.py --url http://localhost:18080 --function <function>
```

- **REST only**: the dhcli port-forward goes through the HTTP proxy of the platform, which
  does not carry gRPC.
- The model name on the serve is its `served_name`, which defaults to the function name;
  when it differs, pass `--model`.
- Port 18080 avoids clashing with a local CORE on 8080.
- Inference is slower than locally: the picture (about 12 MB of JSON) travels over the network.

Step-by-step guides, with the expected output and common problems:
[TUTORIAL-atlas-yolo.md](TUTORIAL-atlas-yolo.md) and [TUTORIAL-atlas-xinet.md](TUTORIAL-atlas-xinet.md).

## How the serve is found

Without `--url`, the scripts ask CORE:

- they look for the RUNNING `tvm+serve:run` of the function (`yolo` or `xinet` by default)
  **in all projects**, or only in `--project`;
- `--run` picks a specific run;
- when more than one serve matches, they do not pick one at random: they list them and stop.

## Options

| Option | Test | Default | Meaning |
| --- | --- | --- | --- |
| `--function` | both | `yolo` / `xinet` | function of the serve to test |
| `--project` | both | all projects | CORE project to search |
| `--run` | both | — | id of a specific `tvm+serve` run |
| `--mode` | both | `rest` | `rest` (port 8080) or `grpc` (port 9000) |
| `--core` `--user` `--password` | both | `http://localhost:8080` `admin` `admin` | CORE and its credentials |
| `--url` | both | — | REST address that already reaches the serve (e.g. `http://localhost:18080` of `dhcli port-forward`): skips CORE and kubectl |
| `--grpc-url` | both | host of `--url`, port 9000 | gRPC `host:port` when using `--url` |
| `--model` | both | the function | model name on the serve when using `--url` |
| `--image` | both | `bus.jpg` | local path or URL of the picture |
| `--stretch` | both | letterbox | stretching resize instead of letterbox |
| `--quant` | both | from the metadata | `in_scale,in_zp,out_scale,out_zp` when the serve does not expose them |
| `--conf` | both | `0.25` / `0.2` | confidence threshold |
| `--out` | both | `output/<function>.jpg` | annotated picture |
| `--kp-conf` | xinet | `0.2` | keypoint visibility threshold |
| `--dump-json` | xinet | — | saves the keypoints for `compare_keypoints.py` |

## Preprocessing

- The picture is brought to the model size with a **letterbox** (aspect ratio kept, grey
  padding), as in the training and calibration of YOLO models. `--stretch` stretches the
  picture instead, as some edge clients do.
- Values are scaled to `[0,1]`, in the layout the model asks for: NCHW for ONNX models, NHWC
  for TFLite models.
- For int8/uint8 models, scale and zero point come from the serve metadata
  (`/v2/models/<name>`): the input is quantized and the output dequantized.
- Boxes and skeletons are mapped back and drawn on the **original picture**.

## compare_keypoints.py

Compares two keypoint exports of the same picture, for example CORE against a reference
TFLite interpreter. Both exports must use the same preprocessing.

```bash
python3 xinet.py --dump-json output/kp_core.json
python3 compare_keypoints.py output/kp_core.json kp_reference.json --step 3.8638
```

## Files

| File | Content |
| --- | --- |
| `yolo.py` | yolo test: box decoding and drawing |
| `xinet.py` | xinet test: skeleton decoding, drawing, keypoint export |
| `common.py` | shared parts: options, picture to tensor, quantization, inference, NMS |
| `serve_client.py` | serve lookup in CORE, port-forward, REST and gRPC calls |
| `compare_keypoints.py` | numeric comparison of two keypoint exports |
| `grpc_predict_v2.proto` | KServe v2 proto, compiled on the fly for `--mode grpc` |
| `TUTORIAL-atlas-*.md` | step-by-step guides for a remote CORE with `dhcli port-forward` |

## Getting a model

- **YOLOv8 detection**: `pip install ultralytics && yolo export model=yolov8n.pt format=onnx`.
- **YOLOv8-pose**: `yolo export model=yolov8n-pose.pt format=onnx`, or `format=tflite` for a
  TFLite model.

Upload the file as a Model, point a `tvm` function to it and run `build`, `compile` and
`serve`: see the TVM runtime page of the platform documentation.
