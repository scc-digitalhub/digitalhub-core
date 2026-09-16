#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Compares two keypoint exports (xinet.py --dump-json) produced by different
runtimes on the same model and the same picture.

It answers one precise question: does the .so compiled by TVM and served by CORE give
the same numbers as the reference TFLite interpreter? The two pipelines share only the
source .tflite file, so a systematic difference is a compilation problem, not a client one.

int8 coordinates are quantized: the step is output_scale * input_side (for xinet
0.017249212 * 224 = 3.86 px). Two correct implementations can therefore differ by up to
one step without any error; the default tolerates 1.5 steps.

  python3 compare_keypoints.py core.json notebook.json
  python3 compare_keypoints.py core.json notebook.json --step 3.8638 --tol 1.5
"""
import argparse
import json
import sys

import numpy as np


def load(path):
    with open(path) as fh:
        d = json.load(fh)
    people = [(float(p["conf"]), np.asarray(p["keypoints"], np.float64)) for p in d["people"]]
    people.sort(key=lambda t: -t[0])
    return d, people


def pair_by_position(a, b):
    """Pairs the people by the centroid of their visible keypoints, greedy on the nearest.
    The confidence alone is not enough: after quantization it can match by chance."""
    def centroid(kp):
        vis = kp[kp[:, 2] > 0.05]
        return vis[:, :2].mean(axis=0) if len(vis) else kp[:, :2].mean(axis=0)

    ca = [centroid(k) for _, k in a]
    cb = [centroid(k) for _, k in b]
    pairs, used = [], set()
    for i in range(len(a)):
        best, bd = None, None
        for j in range(len(b)):
            if j in used:
                continue
            d = float(np.hypot(*(ca[i] - cb[j])))
            if bd is None or d < bd:
                best, bd = j, d
        if best is not None:
            used.add(best)
            pairs.append((i, best, bd))
    return pairs


def main():
    ap = argparse.ArgumentParser(description="Numeric comparison of two keypoint exports")
    ap.add_argument("a", help="reference JSON (e.g. the CORE one)")
    ap.add_argument("b", help="JSON to compare (e.g. the notebook one)")
    ap.add_argument("--step", type=float, default=None,
                    help="quantization step in px (default: derived from the data)")
    ap.add_argument("--tol", type=float, default=1.5, help="tolerance in steps (default 1.5)")
    ap.add_argument("--kp-conf", dest="kp_conf", type=float, default=0.2,
                    help="compare only the keypoints visible in both exports")
    args = ap.parse_args()

    da, pa = load(args.a)
    db, pb = load(args.b)

    print(f"A: {args.a}\n   {len(pa)} people · preprocessing={da.get('preprocessing')} · input={da.get('model_input')}")
    print(f"B: {args.b}\n   {len(pb)} people · preprocessing={db.get('preprocessing')} · input={db.get('model_input')}")

    if da.get("preprocessing") != db.get("preprocessing"):
        print("\nWARNING: the two exports use a different preprocessing. The coordinates can NOT\n"
              "be compared directly: align the preprocessing and export again.")
    if da.get("model_input") != db.get("model_input"):
        sys.exit("ERROR: different input size, the exports cannot be compared.")

    if len(pa) != len(pb):
        print(f"\nWARNING: different number of people ({len(pa)} vs {len(pb)}). "
              "Near the threshold a borderline detection may show up on one side only.")

    step = args.step
    if step is None:
        vals = np.concatenate([k[:, :2].ravel() for _, k in pa if len(k)])
        vals = vals[vals > 0]
        step = float(np.min(np.diff(np.unique(np.round(vals, 3))))) if len(vals) > 1 else 1.0
    print(f"\nquantization step: {step:.4f} px · tolerance: {args.tol} steps "
          f"({args.tol * step:.2f} px)")

    pairs = pair_by_position(pa, pb)
    if not pairs:
        sys.exit("no pairs to compare")

    worst = 0.0
    print(f"\n{'':4s} {'confA':>7s} {'confB':>7s} {'kp':>5s} {'mean':>9s} {'max':>9s}  result")
    for i, j, _ in pairs:
        (ca_, ka), (cb_, kb) = pa[i], pb[j]
        vis = (ka[:, 2] > args.kp_conf) & (kb[:, 2] > args.kp_conf)
        if not vis.any():
            print(f"  p{i} {ca_:7.4f} {cb_:7.4f} {'0':>5s}  (no keypoint visible in both)")
            continue
        d = np.hypot(ka[vis, 0] - kb[vis, 0], ka[vis, 1] - kb[vis, 1])
        worst = max(worst, float(d.max()))
        ok = "OK" if d.max() <= args.tol * step else "MISMATCH"
        print(f"  p{i} {ca_:7.4f} {cb_:7.4f} {int(vis.sum()):5d} "
              f"{d.mean():7.2f}px {d.max():7.2f}px  {ok}")

    print()
    if worst <= args.tol * step:
        print(f"MATCH: largest difference {worst:.2f}px = {worst / step:.2f} quantization steps.\n"
              "The two runtimes give the same result within the int8 granularity.")
    else:
        print(f"NO MATCH: largest difference {worst:.2f}px = {worst / step:.2f} steps.\n"
              "Above the int8 granularity: there is a real pipeline difference\n"
              "(preprocessing, channel order, normalization, or the compilation itself).")
        sys.exit(1)


if __name__ == "__main__":
    main()
