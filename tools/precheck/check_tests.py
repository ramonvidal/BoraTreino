#!/usr/bin/env python3
"""Static checks for mistakes CI kept catching in Kotlin tests. Run: python3 -I tools/precheck/check_tests.py"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MEMBERS = ("assertExists", "assertDoesNotExist", "onNode", "onAllNodes")
NEEDS_IMPORT = (
    "onFirst", "onLast", "assertCountEquals",
    "onNodeWithText", "onAllNodesWithText", "onNodeWithContentDescription",
    "onAllNodesWithContentDescription", "onNodeWithTag", "onAllNodesWithTag", "onRoot",
)
# Non-inline lambdas: composables cannot be called inside them.
NON_INLINE = re.compile(r"\b(joinToString|buildString)\s*(\([^)]*\))?\s*\{")
problems = []


def report(path, line, msg):
    problems.append(f"{path.relative_to(ROOT)}:{line}: {msg}")


def check(path, is_test):
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    imports = {l.strip() for l in lines if l.startswith("import ")}
    if is_test:
        for i, l in enumerate(lines, 1):
            m = re.match(r"import androidx\.compose\.ui\.test\.(\w+)\s*$", l)
            if m and m.group(1) in MEMBERS:
                report(path, i, f"'{m.group(1)}' is a member, not a top-level function: remove the import")
            if re.search(r"^\s*(?:val|var)\s+\w+\s*=\s*assertNotNull\(|assertNotNull\([^()]*(?:\([^()]*\)[^()]*)*\)\s*\.|[=(,]\s*assertNotNull\(", l):
                report(path, i, "JUnit assertNotNull returns Unit; use the value you already have or '!!'")
            m = re.match(r"\s*(?:@Test\s+)?fun\s+`([^`]*)`", l)
            if m and re.search(r"[./]", m.group(1)):
                report(path, i, f"test name contains '.' or '/': `{m.group(1)}`")
            code = l.split("//")[0]
            for fn in NEEDS_IMPORT:
                if re.search(r"(?<!\w)" + fn + r"\(", code) and not any(
                    imp.endswith("." + fn) or imp.endswith(".*") and "compose.ui.test" in imp for imp in imports
                ):
                    report(path, i, f"'{fn}(' used without import androidx.compose.ui.test.{fn}")
    # stringResource inside non-inline lambdas (tests and main)
    for i, l in enumerate(lines, 1):
        if not NON_INLINE.search(l):
            continue
        start = NON_INLINE.search(l).end()
        depth, j, pos = 1, i - 1, start
        while j < len(lines) and depth > 0:
            seg = lines[j][pos:] if j == i - 1 else lines[j]
            for k, ch in enumerate(seg):
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        seg = seg[:k]
                        break
            if "stringResource(" in seg or "pluralStringResource(" in seg:
                report(path, j + 1, "stringResource inside a non-inline lambda "
                       f"({NON_INLINE.search(l).group(1)} at line {i}): read the strings above it")
                break
            j, pos = j + 1, 0


def main():
    for p in sorted(ROOT.rglob("*.kt")):
        rel = p.relative_to(ROOT).parts
        if "build" in rel or ".gradle" in rel:
            continue
        s = str(p)
        if "/src/test/" in s or "/src/androidTest/" in s:
            check(p, True)
        elif "/src/main/" in s:
            check(p, False)
    if problems:
        print("\n".join(problems))
        print(f"check_tests: {len(problems)} problem(s)")
        return 1
    print("check_tests: OK")
    return 0


sys.exit(main())
