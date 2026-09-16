#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""What the yolo and xinet tests share.

The command-line options, the picture sent to the model (letterbox or stretch, NCHW or
NHWC, quantization for int8/uint8 models), one inference against the serve, and the
non-maximum suppression used to drop duplicate boxes.
"""

import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from serve_client import NUMPY_DTYPES, Core, ServeClient, Tensor, direct_serve, fail, find_serve

HERE = Path(__file__).resolve().parent
OUTPUT_DIR = HERE / "output"
DEFAULT_PICTURE_URL = "https://ultralytics.com/images/bus.jpg"


def add_options(parser, function, conf):
    """Options shared by both tests; function and conf are the test's own defaults."""
    serve = parser.add_argument_group("serve")
    serve.add_argument("--function", default=function, help=f"function of the serve (default {function})")
    serve.add_argument("--project", help="CORE project (default: search all of them)")
    serve.add_argument("--run", help="id of a specific tvm+serve run")
    serve.add_argument("--mode", choices=["rest", "grpc"], default="rest", help="protocol (default rest)")
    serve.add_argument("--core", default="http://localhost:8080", help="CORE address (default http://localhost:8080)")
    serve.add_argument("--user", default="admin", help="CORE user (default admin)")
    serve.add_argument("--password", default="admin", help="CORE password (default admin)")
    serve.add_argument("--url", help="REST address that already reaches the serve, e.g. http://localhost:18080 "
                       "of a dhcli port-forward: skips CORE and kubectl")
    serve.add_argument("--grpc-url", help="gRPC host:port with --url (default: host of --url, port 9000)")
    serve.add_argument("--model", help="model name on the serve with --url (default: the function)")

    picture = parser.add_argument_group("picture")
    picture.add_argument("--image", help="local path or URL (default: bus.jpg)")
    picture.add_argument("--stretch", action="store_true", help="stretching resize instead of letterbox")
    picture.add_argument("--quant", help="in_scale,in_zp,out_scale,out_zp when the serve does not expose them")
    picture.add_argument("--conf", type=float, default=conf, help=f"confidence threshold (default {conf})")
    picture.add_argument("--out", help="annotated picture (default: output/<function>.jpg)")


def result_path(args):
    if args.out:
        return Path(args.out)
    OUTPUT_DIR.mkdir(exist_ok=True)
    return OUTPUT_DIR / f"{args.function}.jpg"


# --------------------------------------------------------------------------- picture


def load_picture(source=None):
    """The test picture as RGB: a local path, a URL, or bus.jpg by default. Downloaded
    pictures are kept in output/, so they are fetched only once."""
    from PIL import Image

    if source and Path(source).is_file():
        path = Path(source)
    else:
        url = source or DEFAULT_PICTURE_URL
        path = OUTPUT_DIR / (Path(urllib.parse.urlparse(url).path).name or "picture.jpg")
        if not path.exists():
            OUTPUT_DIR.mkdir(exist_ok=True)
            print(f"download:  {url}")
            urllib.request.urlretrieve(url, path)
    return path, Image.open(path).convert("RGB")


def image_layout(shape):
    """(nhwc, channels, height, width) of a 4-D image input, None for other inputs. The
    channel count is the only small dimension, so its position tells NCHW from NHWC."""
    if len(shape) != 4:
        return None
    if shape[3] in (1, 3) and shape[1] not in (1, 3):
        return True, shape[3], shape[1], shape[2]
    if shape[1] in (1, 3):
        return False, shape[1], shape[2], shape[3]
    return None


def quant_params(tensor_metadata):
    """(scale, zero_point) of a quantized tensor from the served metadata, else None.

    Per-axis quantization (more than one scale) is refused: dequantizing it with a single
    scale would give wrong numbers without any warning.
    """
    params = (tensor_metadata or {}).get("parameters") or {}
    scale, zero_point = params.get("scale"), params.get("zero_point")
    if not scale or not zero_point:
        return None
    if len(scale) > 1:
        fail(
            f"tensor '{tensor_metadata.get('name')}' is quantized per axis ({len(scale)} scales): "
            "these tests dequantize with a single scale"
        )
    return float(scale[0]), int(zero_point[0])


def parse_quant(text):
    """--quant in_scale,in_zp,out_scale,out_zp -> ((scale, zp), (scale, zp))."""
    if not text:
        return None, None
    try:
        in_scale, in_zero, out_scale, out_zero = text.split(",")
        return (float(in_scale), int(in_zero)), (float(out_scale), int(out_zero))
    except ValueError:
        fail("--quant needs four values: in_scale,in_zp,out_scale,out_zp")


@dataclass
class ImageInput:
    """A picture resized for the model, and how model pixels map back onto it."""

    tensor: Tensor
    picture: object
    width: int
    height: int
    preprocessing: str
    ratio_x: float
    ratio_y: float
    pad_x: float
    pad_y: float

    def to_picture(self, x, y):
        """Model pixel coordinates -> coordinates on the original picture."""
        return (x - self.pad_x) / self.ratio_x, (y - self.pad_y) / self.ratio_y


def prepare_image(input_metadata, picture, stretch=False, quant=None):
    """Builds the model input from a picture.

    The picture is resized to the model size (letterbox with grey padding by default,
    full stretch with stretch=True), scaled to [0,1], laid out as NCHW or NHWC as the
    model wants, and quantized when the input is int8/uint8. Letterbox is the default
    because YOLO models are trained and calibrated with it.
    """
    from PIL import Image

    shape = [int(d) for d in input_metadata["shape"]]
    layout = image_layout(shape)
    if layout is None:
        fail(f"input '{input_metadata['name']}' {shape} does not have the shape of a picture")
    nhwc, channels, height, width = layout
    datatype = input_metadata.get("datatype", "FP32")
    color_mode = "L" if channels == 1 else "RGB"
    source = picture.convert(color_mode)
    picture_width, picture_height = source.size

    if stretch:
        resized = source.resize((width, height), Image.BILINEAR)
        ratio_x, ratio_y, pad_x, pad_y = width / picture_width, height / picture_height, 0, 0
    else:
        ratio = min(width / picture_width, height / picture_height)
        new_width, new_height = round(picture_width * ratio), round(picture_height * ratio)
        pad_x, pad_y = (width - new_width) // 2, (height - new_height) // 2
        resized = Image.new(color_mode, (width, height), 114 if channels == 1 else (114, 114, 114))
        resized.paste(source.resize((new_width, new_height), Image.BILINEAR), (pad_x, pad_y))
        ratio_x = ratio_y = ratio

    array = np.asarray(resized, np.float32) / 255.0
    if channels == 1:
        array = array[..., None]
    if not nhwc:
        array = np.transpose(array, (2, 0, 1))
    data = array.reshape(-1)
    if datatype in ("INT8", "UINT8"):
        params = quant or quant_params(input_metadata)
        if params is None:
            fail("the input is quantized but the serve does not expose scale/zero_point: pass --quant")
        low, high, dtype = (-128, 127, np.int8) if datatype == "INT8" else (0, 255, np.uint8)
        data = np.clip(np.round(data / params[0] + params[1]), low, high).astype(dtype)
    else:
        data = data.astype(NUMPY_DTYPES.get(datatype, np.float32))

    preprocessing = "stretch" if stretch else "letterbox"
    print(
        f"picture:   {picture_width}x{picture_height} -> {width}x{height} {preprocessing}, "
        f"{'NHWC' if nhwc else 'NCHW'}, [0,1]{', quantized' if datatype in ('INT8', 'UINT8') else ''}"
    )
    tensor = Tensor(input_metadata["name"], datatype, shape, data)
    return ImageInput(tensor, picture, width, height, preprocessing, ratio_x, ratio_y, pad_x, pad_y)


# --------------------------------------------------------------------------- inference


def infer_picture(args):
    """Finds the serve, sends the picture and returns the first output, dequantized when
    the model is int8/uint8. Returns (image_input, output, picture_path)."""
    if args.url:
        serve = direct_serve(args.url, args.model or args.function)
    else:
        serve = find_serve(Core(args.core, args.user, args.password), args.project, args.function, args.run)
    client = ServeClient(serve, args.mode, args.url, args.grpc_url)
    metadata = client.metadata()
    for kind in ("inputs", "outputs"):
        for tensor in metadata.get(kind) or []:
            print(f"{kind[:-1] + ':':10s} {tensor['name']} {tensor.get('datatype')} {tensor.get('shape')}")

    quant_in, quant_out = parse_quant(args.quant)
    picture_path, picture = load_picture(args.image)
    image_input = prepare_image(metadata["inputs"][0], picture, args.stretch, quant_in)

    outputs, milliseconds = client.infer([image_input.tensor])
    output = outputs[0]
    print(f"inference: {args.mode.upper()} in {milliseconds:.0f} ms -> {output.name} {output.datatype} {output.shape}")
    if output.datatype in ("INT8", "UINT8"):
        params = quant_out or quant_params((metadata.get("outputs") or [{}])[0])
        if params is None:
            fail("the output is quantized but the serve does not expose scale/zero_point: pass --quant")
        output.data = (output.data.astype(np.float32) - params[1]) * params[0]
    return image_input, output, picture_path


# --------------------------------------------------------------------------- decoding helpers


def columns(tensor):
    """A YOLOv8 output [1, channels, N] as [N, channels] float rows."""
    channels, count = tensor.shape[1], tensor.shape[2]
    return tensor.data.astype(np.float32).reshape(channels, count).T


def probabilities(values):
    """Scores are probabilities in the usual exports; some exports keep logits."""
    if values.size and (values.min() < 0 or values.max() > 1):
        return 1.0 / (1.0 + np.exp(-np.clip(values, -30, 30)))
    return values


def corners(boxes):
    """(cx, cy, w, h) -> (x1, y1, x2, y2)."""
    cx, cy, w, h = boxes.T
    return np.stack([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], axis=1)


def nms(boxes, scores, iou_threshold=0.45):
    """Greedy non-maximum suppression on (x1, y1, x2, y2) boxes: indices of the kept
    boxes, best score first."""
    def area(b):
        return (b[..., 2] - b[..., 0]) * (b[..., 3] - b[..., 1])

    order = scores.argsort()[::-1]
    keep = []
    while order.size:
        best, rest = order[0], order[1:]
        keep.append(int(best))
        x1 = np.maximum(boxes[best, 0], boxes[rest, 0])
        y1 = np.maximum(boxes[best, 1], boxes[rest, 1])
        x2 = np.minimum(boxes[best, 2], boxes[rest, 2])
        y2 = np.minimum(boxes[best, 3], boxes[rest, 3])
        overlap = np.clip(x2 - x1, 0, None) * np.clip(y2 - y1, 0, None)
        iou = overlap / (area(boxes[best]) + area(boxes[rest]) - overlap + 1e-9)
        order = rest[iou <= iou_threshold]
    return keep


def font(size):
    from PIL import ImageFont

    try:
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)
    except OSError:
        return ImageFont.load_default()
