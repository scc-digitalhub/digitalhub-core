#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Test of yolo, a YOLOv8 detection model served by a CORE tvm+serve.

  python3 yolo.py                                  REST, serve of the function yolo
  python3 yolo.py --mode grpc                      same inference over gRPC
  python3 yolo.py --image ~/photo.jpg --conf 0.3
  python3 yolo.py --url http://localhost:18080 --function yolo-function
                                                   remote serve through dhcli port-forward

Sends the picture to the serve, decodes the output [1, 4+classes, N], drops duplicate
boxes with NMS and draws the detections on the original picture (output/<function>.jpg).
"""

import argparse
from collections import Counter

import numpy as np

from common import add_options, columns, corners, font, infer_picture, nms, probabilities, result_path
from serve_client import fail

COCO_CLASSES = (
    "person bicycle car motorcycle airplane bus train truck boat traffic-light fire-hydrant "
    "stop-sign parking-meter bench bird cat dog horse sheep cow elephant bear zebra giraffe "
    "backpack umbrella handbag tie suitcase frisbee skis snowboard sports-ball kite baseball-bat "
    "baseball-glove skateboard surfboard tennis-racket bottle wine-glass cup fork knife spoon bowl "
    "banana apple sandwich orange broccoli carrot hot-dog pizza donut cake chair couch "
    "potted-plant bed dining-table toilet tv laptop mouse remote keyboard cell-phone microwave "
    "oven toaster sink refrigerator book clock vase scissors teddy-bear hair-drier toothbrush"
).split()


def class_name(class_id):
    return COCO_CLASSES[class_id] if class_id < len(COCO_CLASSES) else f"class-{class_id}"


def decode(output, width, height, conf_threshold):
    """[(class_id, score, x1, y1, x2, y2)] in model pixels, after per-class NMS, best first.

    Each column of the output is a box (cx, cy, w, h) followed by one score per class.
    """
    rows = columns(output)
    boxes, scores = rows[:, :4].copy(), probabilities(rows[:, 4:])
    classes, conf = scores.argmax(axis=1), scores.max(axis=1)
    keep = conf > conf_threshold
    boxes, classes, conf = boxes[keep], classes[keep], conf[keep]
    # TFLite exports give coordinates normalized to [0,1], ONNX exports give model pixels.
    if boxes.size and boxes.max() <= 1.5:
        boxes *= [width, height, width, height]
    boxes = corners(boxes)
    detections = []
    for class_id in np.unique(classes):
        members = np.where(classes == class_id)[0]
        for kept in nms(boxes[members], conf[members]):
            index = members[kept]
            detections.append((int(class_id), float(conf[index]), *map(float, boxes[index])))
    return sorted(detections, key=lambda detection: -detection[1])


def draw(image_input, detections, path):
    """Draws the boxes on the original picture."""
    from PIL import ImageDraw

    picture = image_input.picture.copy()
    canvas = ImageDraw.Draw(picture)
    font_size = max(12, picture.size[1] // 45)
    label_font, line = font(font_size), max(2, picture.size[0] // 250)
    for class_id, score, x1, y1, x2, y2 in detections:
        (x1, y1), (x2, y2) = image_input.to_picture(x1, y1), image_input.to_picture(x2, y2)
        canvas.rectangle([x1, y1, x2, y2], outline=(0, 200, 0), width=line)
        canvas.text((x1 + 3, max(0, y1 - font_size - 4)), f"{class_name(class_id)} {score:.2f}", fill=(0, 200, 0), font=label_font)
    picture.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_options(parser, function="yolo", conf=0.25)
    args = parser.parse_args()

    image_input, output, _ = infer_picture(args)
    if len(output.shape) != 3 or output.shape[1] <= 4:
        fail(f"output {output.shape} is not a YOLOv8 detection output [1, 4+classes, N]")

    detections = decode(output, image_input.width, image_input.height, args.conf)
    print(f"\ndetections with conf > {args.conf}, after NMS: {len(detections)}")
    for class_id, score, x1, y1, x2, y2 in detections:
        (x1, y1), (x2, y2) = image_input.to_picture(x1, y1), image_input.to_picture(x2, y2)
        print(f"  {class_name(class_id):14s} conf {score:.2f}  box ({x1:.0f},{y1:.0f})-({x2:.0f},{y2:.0f})")
    print(f"summary:   {dict(Counter(class_name(d[0]) for d in detections))}")

    path = result_path(args)
    draw(image_input, detections, path)
    print(f"picture:   {path}")


if __name__ == "__main__":
    main()
