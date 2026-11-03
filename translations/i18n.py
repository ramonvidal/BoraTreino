#!/usr/bin/env python3
"""The Baianês texts, as one table.

    python3 translations/i18n.py export   # every English text + its Baianês, to baianes.csv
    python3 translations/i18n.py apply    # baianes.csv back into each module's values-pt-rBR
    python3 translations/i18n.py check    # every text translated, placeholders and plurals match (CI)

Columns: module, key, quantity (for counted texts like "%d points": one/other), english,
baianes. Edit only the baianes column. Keep %1$s, %2$d, %d as they are: the app fills them
in (a name, a number). An empty baianes cell falls back to English in the app, and `check`
fails. `app_name` is never translated. Standard library only.
"""
import csv
import re
import sys
from collections import Counter
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TABLE = ROOT / "translations" / "baianes.csv"
LOCALE = "values-pt-rBR"


def modules():
    for english in sorted(ROOT.glob("**/src/main/res/values/strings.xml")):
        if "build" in english.parts or "node_modules" in english.parts:
            continue
        yield english.parent.parent.parent.parent.parent.relative_to(ROOT).as_posix(), english


def unescape(text):
    text = text or ""
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]  # Android's way to keep leading / trailing spaces
    return text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")


def escape(text):
    quoted = text != text.strip()
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    text = "\\" + text if text[:1] in "@?" else text
    return f'"{text}"' if quoted else text


def entries(path):
    """(key, quantity, text) for each string; quantity is '' except for plurals."""
    if not path.exists():
        return []
    out = []
    for node in ET.parse(path).getroot():
        if node.get("translatable") == "false":
            continue
        if node.tag == "string":
            out.append((node.get("name"), "", unescape(node.text)))
        elif node.tag == "plurals":
            for item in node:
                out.append((node.get("name"), item.get("quantity"), unescape(item.text)))
    return out


def export():
    rows = []
    for module, english in modules():
        local = {(k, q): t for k, q, t in entries(english.parent.parent / LOCALE / "strings.xml")}
        for key, quantity, text in entries(english):
            rows.append([module, key, quantity, text, local.get((key, quantity), "")])
    with TABLE.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["module", "key", "quantity", "english", "baianes"])
        writer.writerows(rows)
    print(f"{len(rows)} texts in {TABLE.relative_to(ROOT)}")


def apply():
    table = {}
    with TABLE.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["baianes"].strip():
                table[(row["module"], row["key"], row["quantity"])] = row["baianes"]
    missing = 0
    for module, english in modules():
        lines = ['<?xml version="1.0" encoding="utf-8"?>',
                 "<!-- Baianês. Made from translations/baianes.csv by translations/i18n.py: edit the table, not this file. -->",
                 "<resources>"]
        plurals = {}
        for key, quantity, _ in entries(english):
            text = table.get((module, key, quantity))
            if text is None:
                missing += 1
                continue
            if quantity:
                plurals.setdefault(key, []).append((quantity, text))
            else:
                lines.append(f'    <string name="{key}">{escape(text)}</string>')
        for key, items in plurals.items():
            lines.append(f'    <plurals name="{key}">')
            lines += [f'        <item quantity="{q}">{escape(t)}</item>' for q, t in items]
            lines.append("    </plurals>")
        lines.append("</resources>")
        out = english.parent.parent / LOCALE / "strings.xml"
        out.parent.mkdir(exist_ok=True)
        out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {LOCALE} for every module; {missing} texts still in English only")


PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")
EXEMPT = {"app_name"}


def check():
    problems = []
    english_rows = set()
    english_text = {}
    quantities = {}
    for module, english in modules():
        for key, quantity, text in entries(english):
            english_rows.add((module, key, quantity))
            english_text[(module, key, quantity)] = text
            if quantity:
                quantities.setdefault((module, key), set()).add(quantity)
    table = {}
    with TABLE.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            k = (row["module"], row["key"], row["quantity"])
            table[k] = row["baianes"].strip()
            if k not in english_rows:
                problems.append(f"{k[0]}: '{k[1]}' {k[2]}".rstrip() + " is in baianes.csv but no longer in English (remove the row)")
    for k in sorted(english_rows):
        if k[1] in EXEMPT:
            continue
        label = f"{k[0]}: {k[1]}" + (f" [{k[2]}]" if k[2] else "")
        text = table.get(k, "")
        if not text:
            problems.append(f"{label}: no Baianês text")
            continue
        if Counter(PLACEHOLDER.findall(english_text[k])) != Counter(PLACEHOLDER.findall(text)):
            problems.append(f"{label}: placeholders differ from English ({english_text[k]!r} vs {text!r})")
    for (module, key), wanted in sorted(quantities.items()):
        for q in sorted(wanted):
            if not table.get((module, key, q)):
                problems.append(f"{module}: {key} lacks the '{q}' quantity")
    if problems:
        print(f"{len(problems)} translation problem(s):")
        for p in dict.fromkeys(problems):
            print(f"  - {p}")
        sys.exit(1)
    print(f"Translations OK: {len(english_rows)} texts")


if __name__ == "__main__":
    {"export": export, "apply": apply, "check": check}[sys.argv[1] if len(sys.argv) > 1 else "export"]()
