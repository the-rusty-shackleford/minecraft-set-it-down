"""Mutation runs for 1.1.0: apply one mutation, run JUnit, report which tests failed, restore the
sources. From the repository root:

    uv run --no-project python devtools/verification/mutations-1.1.0.py A|B|C|D|E|F|G|H|I|J|K|L

A: copies on one layer never spread apart (D-0007): a 3D apple's cluster passes through itself.
B: a heap never shrunk to its box's depth (D-0007).
C: a pile too tall for its box never heaped (D-0007).
D: a model too tall to stand, a tie of its side and its front going to its side (D-0005).
H: nothing stands on its base unless its base is its broadest face (D-0005): an apple lies down.
E: a model longer than its square never shrunk into it (D-0006).
F: a model never centred on its square (D-0006).
G: a heap spread apart never drawn back into its square (D-0007).
I: a hand's model matched to the item's own across the face only (D-0006): a standing apple grows by its base.
J: a long model never laid corner to corner (D-0005).
K: a model laid corner to corner matched to its picture's side, not its diagonal (D-0006): a sword falls short.
L: flat copies that would overlap spread apart like thick ones (D-0007): two steaks shrink to fit side by side.

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOMAIN = REPO / "src/domain/java/com/chunkworks/setitdown/domain"
SCRATCH = REPO / "run"

MUTATIONS = {
    "A": [(DOMAIN / "Pose.java",
           "        if (!overlapping(slots, shown, scale, spread)) {\n            return 1.0;",
           "        if (true) {   // mutated: never apart\n            return 1.0;")],
    "B": [(DOMAIN / "Pose.java",
           "            if (tall > depth) {",
           "            if (false) {   // mutated: no depth fit")],
    "C": [(DOMAIN / "Pose.java",
           "            if (count * (body.maxZ() - body.minZ()) * scale > depth) {",
           "            if (false) {   // mutated: never heaped")],
    "D": [(DOMAIN / "Rest.java",
           "        return z <= x + TIE ? FACE_OUT : ON_SIDE;",
           "        return x <= z + TIE ? ON_SIDE : FACE_OUT;   // mutated: the side first")],
    "E": [(DOMAIN / "Pose.java",
           "        double fit = across > 1.0 ? 1.0 / across : 1.0;",
           "        double fit = 1.0;   // mutated: never shrunk into its square")],
    "F": [(DOMAIN / "Pose.java",
           "        return Affine.scale(fit).then(Affine.translation(-c[0], -c[1], 0.0));",
           "        return Affine.scale(fit);   // mutated: not centred")],
    "H": [(DOMAIN / "Rest.java",
           "        if (y <= STANDS * Math.min(x, z) + TIE) {",
           "        if (y <= Math.min(x, z) + TIE) {   // mutated: stands only on its broadest face")],
    "I": [(DOMAIN / "Pose.java",
           "        double length = Math.max(held.maxZ() - held.minZ(), Math.max(held.maxX() - held.minX(), held.maxY() - held.minY()));",
           "        double length = Math.max(held.maxX() - held.minX(), held.maxY() - held.minY());   // mutated: across the face only")],
    "J": [(DOMAIN / "Rest.java",
           "        return long_(rested.maxX() - rested.minX(), rested.maxY() - rested.minY())\n                && !long_(",
           "        return false && long_(rested.maxX() - rested.minX(), rested.maxY() - rested.minY())   // mutated: never slanted\n                && !long_(")],
    "K": [(DOMAIN / "Pose.java",
           "        double target = slanted ? Math.hypot(across, along) : Math.max(across, along);",
           "        double target = Math.max(across, along);   // mutated: the side, slanted or not")],
    "L": [(DOMAIN / "Pose.java",
           "        List<StackLayout.Slot> laid = flat && overlapping(slots, shown, scale, spread) ? layered(slots) : slots;",
           "        List<StackLayout.Slot> laid = slots;   // mutated: never layered")],
    "G": [(DOMAIN / "Pose.java",
           "        double into = apart > 1.0 ? Math.min(1.0, scale / 2.0 / reach(laid, shown, scale, spread * apart)) : 1.0;",
           "        double into = 1.0;   // mutated: never drawn back in")],
}


def junit_failures():
    failed = []
    for xml in (REPO / "build/test-results/test").glob("*.xml"):
        text = xml.read_text(errors="replace")
        for name in re.findall(r'<testcase name="([^"]+)" classname="[^"]+" time="[^"]+">\s*<failure', text):
            failed.append(xml.stem.split(".")[-1] + "." + name)
    return sorted(failed)


def main(run: str) -> int:
    backups = {}
    try:
        for path, old, new in MUTATIONS[run]:
            if path not in backups:
                backups[path] = path.read_text()
            text = path.read_text()
            assert text.count(old) == 1, (run, old)
            path.write_text(text.replace(old, new))
        env = dict(os.environ, JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64")
        out = SCRATCH / f"mutation-1.1.0-{run}.log"
        SCRATCH.mkdir(exist_ok=True)
        with out.open("w") as f:
            test = subprocess.run(["./gradlew", "test", "--rerun", "--continue", "--console=plain", "-q", "--offline"], cwd=REPO, env=env,
                                  stdout=f, stderr=subprocess.STDOUT).returncode
        print(f"run {run}: junit exit {test}")
        print("  junit failed:", junit_failures() if test else "none")
        return 0
    finally:
        for path, text in backups.items():
            path.write_text(text)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
