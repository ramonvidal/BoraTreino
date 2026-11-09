#!/usr/bin/env python3
"""Builds core/data/src/main/assets/exercise_library.json.

Source: github.com/yuhonas/free-exercise-db (Unlicense), dist/exercises.json.
Portuguese names live in pt_names.tsv next to this script (English name TAB Portuguese name).

Usage: python3 tools/exercise_library/build.py [path/to/exercises.json]
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = sys.argv[1] if len(sys.argv) > 1 else "/home/user/extsrc/fedb/dist/exercises.json"
OUT = os.path.join(ROOT, "core/data/src/main/assets/exercise_library.json")

EQUIPMENT = {
    "barbell": "BARBELL", "dumbbell": "DUMBBELL", "body only": "BODY_ONLY", "cable": "CABLE",
    "machine": "MACHINE", "kettlebells": "KETTLEBELLS", "bands": "BANDS", "e-z curl bar": "EZ_BAR",
    "medicine ball": "MEDICINE_BALL", "exercise ball": "EXERCISE_BALL", "foam roll": "FOAM_ROLL",
    "other": "OTHER", None: "OTHER",
}
LEVELS = {"beginner": "BEGINNER", "intermediate": "INTERMEDIATE", "expert": "EXPERT"}


def load_pt():
    pt = {}
    with open(os.path.join(HERE, "pt_names.tsv"), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line:
                en, p = line.split("\t")
                pt[en] = p.strip()
    return pt


def main():
    pt = load_pt()
    with open(SRC, encoding="utf-8") as f:
        data = [x for x in json.load(f) if x["category"] != "stretching"]
    data.sort(key=lambda x: (x["name"].lower(), x["id"]))
    out, ids = [], set()
    for x in data:
        name = x["name"]
        if not pt.get(name):
            sys.exit("missing pt for: " + name)
        if x["id"] in ids:
            sys.exit("duplicate id: " + x["id"])
        ids.add(x["id"])
        out.append({
            "id": x["id"], "name": name, "pt": pt[name],
            "equip": EQUIPMENT[x.get("equipment")], "level": LEVELS[x["level"]],
            "cat": x["category"], "mech": x.get("mechanic"), "force": x.get("force"),
            "pm": x["primaryMuscles"], "sm": x["secondaryMuscles"], "steps": x["instructions"],
        })
    extra = set(pt) - {x["name"] for x in data}
    if extra:
        sys.exit("pt names without an entry: %s" % sorted(extra))
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("%d entries, %d bytes -> %s" % (len(out), os.path.getsize(OUT), OUT))


if __name__ == "__main__":
    main()
