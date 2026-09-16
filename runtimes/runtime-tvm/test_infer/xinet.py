#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Test of xinet, a YOLOv8-pose model (people + 17 COCO keypoints) served by a CORE tvm+serve.

  python3 xinet.py                                 REST, serve of the function xinet
  python3 xinet.py --mode grpc                     same inference over gRPC
  python3 xinet.py --dump-json output/kp.json      saves the keypoints for compare_keypoints.py
  python3 xinet.py --url http://localhost:18080 --function <function>
                                                   remote serve through dhcli port-forward

Sends the picture to the serve, decodes the output [1, 5+3*keypoints, N], drops duplicate
people with NMS and draws the skeletons on the original picture (output/<function>.jpg).
"""

import argparse
import json

import numpy as np

from common import add_options, columns, corners, infer_picture, nms, probabilities, result_path
from serve_client import fail

# COCO-17 skeleton: 19 limbs, with one color each (jet palette), as in xinet-pose.
SKELETON = [(15, 13), (13, 11), (16, 14), (14, 12), (11, 12), (5, 11), (6, 12), (5, 6), (5, 7), (6, 8),
            (7, 9), (8, 10), (1, 2), (0, 1), (0, 2), (1, 3), (2, 4), (3, 5), (4, 6)]
LIMB_COLORS = [(0, 0, 128), (0, 0, 180), (0, 0, 230), (0, 40, 255), (0, 100, 255), (0, 160, 255),
               (0, 220, 220), (0, 255, 160), (40, 255, 80), (120, 255, 0), (200, 255, 0), (255, 220, 0),
               (255, 160, 0), (255, 100, 0), (255, 40, 0), (255, 0, 40), (220, 0, 120), (160, 0, 180),
               (100, 0, 220)]


def decode(output, width, height, conf_threshold):
    """[(score, keypoints)] after NMS, best first; keypoints is [K, 3] of (x, y, visibility)
    in model pixels.

    Each column of the output is a box (cx, cy, w, h), the person score and (x, y,
    visibility) for every keypoint. The box is only used to remove duplicates.
    """
    rows = columns(output)
    count, keypoint_count = rows.shape[0], (rows.shape[1] - 5) // 3
    boxes, conf = rows[:, :4].copy(), rows[:, 4]
    keypoints = rows[:, 5:5 + 3 * keypoint_count].reshape(count, keypoint_count, 3).copy()
    if conf.size and (conf.min() < 0 or conf.max() > 1):
        conf = probabilities(conf)
        keypoints[:, :, 2] = probabilities(keypoints[:, :, 2])
    keep = conf > conf_threshold
    boxes, conf, keypoints = boxes[keep], conf[keep], keypoints[keep]
    # TFLite exports give coordinates normalized to [0,1], ONNX exports give model pixels.
    if keypoints.size and np.nanmax(keypoints[:, :, :2]) <= 1.5:
        keypoints[:, :, 0] *= width
        keypoints[:, :, 1] *= height
    return [(float(conf[i]), keypoints[i]) for i in nms(corners(boxes), conf)]


def draw(image_input, people, keypoint_threshold, path):
    """Draws limbs and joints on the original picture; returns (limbs, joints) per person."""
    from PIL import ImageDraw

    picture = image_input.picture.copy()
    canvas = ImageDraw.Draw(picture)
    line, radius = max(1, min(picture.size) // 150), max(1, min(picture.size) // 110)
    counts = []
    for _, keypoints in people:
        visible = keypoints[:, 2] > keypoint_threshold
        points = [image_input.to_picture(x, y) for x, y, _ in keypoints]
        limbs = 0
        for index, (a, b) in enumerate(SKELETON):
            if visible[a] and visible[b]:
                canvas.line([*points[a], *points[b]], fill=LIMB_COLORS[index], width=line)
                limbs += 1
        for (x, y), shown in zip(points, visible):
            if shown:
                canvas.ellipse([x - radius, y - radius, x + radius, y + radius], fill=(0, 0, 0))
        counts.append((limbs, int(visible.sum())))
    picture.save(path)
    return counts


def dump_keypoints(path, people, image_input, picture_path):
    """Keypoints in model pixels, for a numeric comparison with another pipeline
    (compare_keypoints.py). The model space is the only one two pipelines share, so the
    coordinates are not mapped back onto the picture."""
    document = {
        "image": str(picture_path),
        "preprocessing": image_input.preprocessing,
        "model_input": [image_input.width, image_input.height],
        "coords": "model-space pixels (origin at the top left)",
        "people": [
            {
                "conf": round(score, 6),
                "keypoints": [[round(float(x), 3), round(float(y), 3), round(float(v), 6)] for x, y, v in keypoints],
            }
            for score, keypoints in people
        ],
    }
    with open(path, "w") as handle:
        json.dump(document, handle, indent=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_options(parser, function="xinet", conf=0.2)
    parser.add_argument("--kp-conf", type=float, default=0.2, help="keypoint visibility threshold (default 0.2)")
    parser.add_argument("--dump-json", help="saves the keypoints as JSON for compare_keypoints.py")
    args = parser.parse_args()

    image_input, output, picture_path = infer_picture(args)
    channels = output.shape[1] if len(output.shape) == 3 else 0
    if channels < 8 or (channels - 5) % 3:
        fail(f"output {output.shape} is not a YOLOv8-pose output [1, 5+3*keypoints, N]")

    people = decode(output, image_input.width, image_input.height, args.conf)
    path = result_path(args)
    counts = draw(image_input, people, args.kp_conf, path)
    print(f"\npeople with conf > {args.conf}, after NMS: {len(people)}")
    for index, ((score, keypoints), (limbs, joints)) in enumerate(zip(people, counts)):
        print(f"  person {index}: conf {score:.2f}  limbs {limbs}/{len(SKELETON)}  joints {joints}/{len(keypoints)}")

    if args.dump_json:
        dump_keypoints(args.dump_json, people, image_input, picture_path)
        print(f"keypoints: {args.dump_json} (model pixels)")
    print(f"picture:   {path}")


if __name__ == "__main__":
    main()
