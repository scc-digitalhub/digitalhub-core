# Tutorial — testing xinet on a remote CORE (atlas) with dhcli

How to try **xinet**, a YOLOv8-pose model (people + 17 COCO keypoints) served by `tvm+serve`
on **https://core.dev.atlas.fbk.eu**, from your own PC, without kubectl, using the `dhcli`
port-forward and `xinet.py`.

```
 PC                                                     atlas
 xinet.py ──► localhost:18080 ──► dhcli port-forward ──► platform proxy ──► serve Service :8080
```

For yolo, see the twin tutorial [TUTORIAL-atlas-yolo.md](TUTORIAL-atlas-yolo.md).

---

## 0. Prerequisites

- `dhcli` 0.15 or later (it provides `port-forward`).
- Python 3 with `numpy` and `Pillow`.
- A user that belongs to the project on atlas.
- The xinet function with its `tvm+serve` run in state **RUNNING** (started from the CORE
  console).

Set the project and function of your serve (the values below are only an example) and the
dhcli command (`dhcli`, or the path of your dhcli binary); all the following commands use them:

```bash
PROJECT=tvm-test
FUNCTION=xinet-function
DHCLI=dhcli
```

---

## 1. Configure the environment (first time only)

`dhcli` stores its environments in `~/.dhcore.ini`. Check whether atlas is already there:

```bash
$DHCLI list-env
```

If no environment points to atlas, register it and make it the default one:

```bash
$DHCLI register -e dhcore https://core.dev.atlas.fbk.eu
$DHCLI use dhcore
```

---

## 2. Log in

```bash
$DHCLI login -e dhcore
```

Press Enter: the browser opens, sign in, and the terminal shows `✔ Login successful`. The
token lasts about **5 hours**; when it expires (401 errors) run:

```bash
$DHCLI refresh -e dhcore
```

If the refresh fails too, log in again.

---

## 3. Find the serve

```bash
$DHCLI services -p $PROJECT
```

```
NAME          ID                                 FUNCTION         KIND            SERVICE                                        STATE
<run-name>    <run-id>                           xinet-function   tvm+serve:run   s-tvmserve-<run-id>.dev-platform:8080          RUNNING
```

You need a row with your function and `STATE` **RUNNING**. If there is none, start (or
restart) the serve from the console. A `403 Forbidden` means your user is not in the project.

---

## 4. Open the port-forward

In a **first terminal**, to keep open for the whole test:

```bash
PROJECT=tvm-test
FUNCTION=xinet-function
DHCLI=dhcli

$DHCLI port-forward -p $PROJECT -f $FUNCTION -l 18080
```

```
✔ Port-forward listening on localhost:18080
```

- `-f $FUNCTION` uses the most recent RUNNING run of the function; for a specific run, pass
  its ID instead of `-f`: `$DHCLI port-forward -p $PROJECT <run-id> -l 18080`.
- Port **18080** avoids clashing with a local CORE on 8080; any other port works, as long as
  you use the same one in the next steps.
- To test yolo and xinet together, open two port-forwards on different ports (e.g. 18080
  and 18081).

---

## 5. Check that the serve answers

In a **second terminal** (set the variables of step 0 again):

```bash
curl http://localhost:18080/v2/health/ready
```

```json
{"ready":true}
```

```bash
curl http://localhost:18080/v2/models/$FUNCTION
```

```json
{"name":"xinet-function","versions":["1"],"platform":"tvm",
 "inputs":[{"name":"images","datatype":"FP32","shape":[1,3,224,224]}],
 "outputs":[{"name":"output0","datatype":"FP32","shape":[1,56,1029]}]}
```

- The name in the URL is the serve's `served_name`, which defaults to the function name.
- The `[1, 56, 1029]` output is the YOLOv8-pose layout: 4 box coordinates, 1 confidence and
  17 keypoints × 3 (x, y, visibility) for each of the 1029 positions. A quantized model shows
  `INT8` / `UINT8` and the `scale` / `zero_point` parameters, which `xinet.py` uses on its own.

---

## 6. Run the test

Still in the second terminal:

```bash
cd digitalhub-core/runtimes/runtime-tvm/test_infer
python3 xinet.py --url http://localhost:18080 --function $FUNCTION
```

```
serve:     http://localhost:18080  model 'xinet-function'
input:     images FP32 [1, 3, 224, 224]
output:    output0 FP32 [1, 56, 1029]
picture:   810x1080 -> 224x224 letterbox, NCHW, [0,1]
inference: REST in 411 ms -> output0 FP32 [1, 56, 1029]

people with conf > 0.2, after NMS: 3
  person 0: conf 0.76  limbs 7/19  joints 10/17
  person 1: conf 0.57  limbs 19/19  joints 17/17
  person 2: conf 0.33  limbs 5/19  joints 8/17
picture:   .../test_infer/output/xinet-function.jpg
```

(Example obtained with xinet on bus.jpg; from remote the inference time is higher.)

The people are the ones left after non-maximum suppression. For each person, `limbs`
(skeleton bones) and `joints` (keypoints) count how many are above the visibility threshold.
Open `output/<function>.jpg` to see the skeletons drawn on the picture.

More useful runs:

```bash
# another picture (local path or URL), person and keypoint thresholds
python3 xinet.py --url http://localhost:18080 --function $FUNCTION --image ~/photo.jpg --conf 0.3 --kp-conf 0.5

# stretching resize instead of letterbox, as some edge clients do
python3 xinet.py --url http://localhost:18080 --function $FUNCTION --stretch

# served_name different from the function name
python3 xinet.py --url http://localhost:18080 --function $FUNCTION --model <served_name>

# save the keypoints and compare them with another runtime (e.g. the TFLite interpreter of the notebook)
python3 xinet.py --url http://localhost:18080 --function $FUNCTION --dump-json output/kp_atlas.json
python3 compare_keypoints.py output/kp_atlas.json output/kp_notebook.json
```

---

## 7. Close

In the first terminal press **Ctrl+C** to close the port-forward.

---

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `401` from dhcli or curl | Expired token: `$DHCLI refresh -e dhcore`, or log in again. |
| `403 Forbidden` from dhcli | Your user is not a member of the project: ask a project admin to add you. |
| `ERROR: the serve does not answer at http://localhost:18080/...` | The port-forward is not open or uses another port: check the first terminal. |
| `address already in use` on the port-forward | The port is busy: pick another one with `-l` and use it in `--url` too. |
| `404` on `/v2/models/<name>` | That is not the serve's name: check `served_name` in the run and pass `--model`. |
| dhcli does not find the run | The serve is not RUNNING, or the project or function name is wrong: `$DHCLI services -p $PROJECT`. |
| `... is not a YOLOv8-pose output` | The function does not serve a pose model (for example it is a yolo detection model): use `yolo.py`. |
| Skeletons shifted on the picture | The model was exported for another preprocessing: try `--stretch`. |
| `--mode grpc` does not work | Expected: the dhcli port-forward goes through the platform HTTP proxy, which does not carry gRPC. On atlas only REST can be tested. |
