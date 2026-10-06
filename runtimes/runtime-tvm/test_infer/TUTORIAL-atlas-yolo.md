# Tutorial — testing yolo on a remote CORE (atlas) with dhcli

How to try a model served by `tvm+serve` on **https://core.dev.atlas.fbk.eu** from your own
PC, without kubectl, using the `dhcli` port-forward and the `test_infer` scripts.

```
 PC                                                    atlas
 yolo.py ──► localhost:18080 ──► dhcli port-forward ──► platform proxy ──► serve Service :8080
```

The example uses the **`tvm-test`** project and the **`yolo-function`** function; for another
serve, change only these two names.

For xinet, see the twin tutorial [TUTORIAL-atlas-xinet.md](TUTORIAL-atlas-xinet.md).

---

## 0. Prerequisites

- `dhcli` 0.15 or later (it provides `port-forward`).
- Python 3 with `numpy` and `Pillow`.
- A user that belongs to the project on atlas.
- The `tvm+serve` run in state **RUNNING** (started from the CORE console).

The commands use `$DHCLI`: set it to `dhcli`, or to the path of your dhcli binary.

```bash
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
$DHCLI services -p tvm-test
```

```
NAME          ID                                 FUNCTION        KIND            SERVICE                                                         STATE
cloudy-lynx   7398740f73b14fdd957f10c2e54c8ed2   yolo-function   tvm+serve:run   s-tvmserve-7398740f73b14fdd957f10c2e54c8ed2.dev-platform:8080   RUNNING
```

You need a row with your function and `STATE` **RUNNING**. If there is none, start (or
restart) the serve from the console.

---

## 4. Open the port-forward

In a **first terminal**, to keep open for the whole test:

```bash
DHCLI=dhcli
$DHCLI port-forward -p tvm-test -f yolo-function -l 18080
```

```
✔ Port-forward listening on localhost:18080
```

- `-f yolo-function` uses the most recent RUNNING run of the function; for a specific run,
  pass its ID instead of `-f`: `$DHCLI port-forward -p tvm-test 7398740f73b14fdd957f10c2e54c8ed2 -l 18080`.
- Port **18080** avoids clashing with a local CORE on 8080; any other port works, as long as
  you use the same one in the next steps.

---

## 5. Check that the serve answers

In a **second terminal**:

```bash
curl http://localhost:18080/v2/health/ready
```

```json
{"ready":true}
```

```bash
curl http://localhost:18080/v2/models/yolo-function
```

```json
{"name":"yolo-function","versions":["1"],"platform":"tvm",
 "inputs":[{"name":"images","datatype":"FP32","shape":[1,3,640,640]}],
 "outputs":[{"name":"output0","datatype":"FP32","shape":[1,84,8400]}]}
```

The name in the URL is the serve's `served_name`, which defaults to the function name.

---

## 6. Run the test

Still in the second terminal:

```bash
cd digitalhub-core/runtimes/runtime-tvm/test_infer
python3 yolo.py --url http://localhost:18080 --function yolo-function
```

```
serve:     http://localhost:18080  model 'yolo-function'
input:     images FP32 [1, 3, 640, 640]
output:    output0 FP32 [1, 84, 8400]
picture:   810x1080 -> 640x640 letterbox, NCHW, [0,1]
inference: REST in 7798 ms -> output0 FP32 [1, 84, 8400]

detections with conf > 0.25, after NMS: 5
  person         conf 0.90  box (671,385)-(810,880)
  person         conf 0.88  box (222,407)-(344,856)
  person         conf 0.87  box (50,398)-(244,905)
  bus            conf 0.84  box (31,231)-(801,778)
  person         conf 0.43  box (0,549)-(59,868)
summary:   {'person': 4, 'bus': 1}
picture:   .../test_infer/output/yolo-function.jpg
```

The detections are the boxes left after non-maximum suppression, and the summary counts them
by class. Open `output/yolo-function.jpg` to see the boxes drawn on the picture.

More useful runs:

```bash
# another picture (local path or URL) and another confidence threshold
python3 yolo.py --url http://localhost:18080 --function yolo-function --image ~/photo.jpg --conf 0.3

# served_name different from the function name
python3 yolo.py --url http://localhost:18080 --function yolo-function --model <served_name>
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
| dhcli does not find the run | The serve is not RUNNING, or the project or function name is wrong: `$DHCLI services -p tvm-test`. |
| `--mode grpc` does not work | Expected: the dhcli port-forward goes through the platform HTTP proxy, which does not carry gRPC. On atlas only REST can be tested. |
| Inference takes seconds | Expected from remote: the picture (about 12 MB of JSON) travels over the network and through the proxy. |
