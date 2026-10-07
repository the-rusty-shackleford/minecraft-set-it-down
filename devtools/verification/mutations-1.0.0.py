"""Mutation runs for 1.0.0: apply one mutation, run JUnit and the gametest server, report which
tests failed, restore the sources. From the repository root:

    uv run --no-project python devtools/verification/mutations-1.0.0.py A|B|C|D

A: room refusal off (another item within the radius is set down anyway).
B: the support rule back to the README's "not air" (water, a piston's head, flowers hold it up).
C: the give back to a drop at the display (spawnAtLocation, the README's way).
D: the clamp without the turn (a display turned 45 degrees keeps the straight reach).

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
"""
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
MAIN = REPO / "src/main/java/com/chunkworks/setitdown"
DOMAIN = REPO / "src/domain/java/com/chunkworks/setitdown/domain"
SCRATCH = REPO / "run"

MUTATIONS = {
    "A": [(DOMAIN / "Placement.java",
           "        return block >= 0 ? new NoRoom(block) : new Start();\n",
           "        return new Start();   // mutated: no room refusal\n")],
    "B": [(MAIN / "Displayable.java",
           "        return state.is(Blocks.SNOW) || !state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();\n",
           "        return !state.isAir();   // mutated: the README's rule\n"),
          (MAIN / "Displayable.java",
           "        if (!supports(state, level, pos)) {\n            return false;\n        }\n        int sign = face.stepX() + face.stepY() + face.stepZ();\n        for (AABB b : state.getShape(level, pos, CollisionContext.empty()).toAabbs()) {",
           "        if (!supports(state, level, pos)) {\n            return false;\n        }\n        if (true) return true;   // mutated: any non-air holds it\n        int sign = face.stepX() + face.stepY() + face.stepZ();\n        for (AABB b : state.getShape(level, pos, CollisionContext.empty()).toAabbs()) {")],
    "C": [(MAIN / "DisplayEntity.java",
           "            Carried.giveOrDrop(player, out);\n",
           "            spawnAtLocation(out);   // mutated: dropped, not given\n")],
    "D": [(DOMAIN / "FacePlacement.java",
           "        return Math.floorMod(turn, 2) == 0 ? side / 2.0 : side / 2.0 * Math.sqrt(2.0);\n",
           "        return side / 2.0;   // mutated: the turn ignored\n")],
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
        out = SCRATCH / f"mutation-{run}.log"
        SCRATCH.mkdir(exist_ok=True)
        with out.open("w") as f:
            test = subprocess.run(["./gradlew", "test", "--continue", "--console=plain", "-q", "--offline"], cwd=REPO, env=env,
                                  stdout=f, stderr=subprocess.STDOUT).returncode
            gt = subprocess.run(["./gradlew", "runGameTestServer", "--console=plain", "-q", "--offline"], cwd=REPO, env=env,
                                stdout=f, stderr=subprocess.STDOUT).returncode
        log = (REPO / "run/logs/latest.log").read_text(errors="replace")
        shutil.copy(REPO / "run/logs/latest.log", SCRATCH / f"mutation-{run}-server.log")
        failed = sorted(set(re.findall(r"(\w+) failed at", log)))
        summary = re.findall(r"(\d+ required tests failed.*|All \d+ required tests passed.*)", log)
        print(f"run {run}: junit exit {test}, gametest exit {gt}")
        print("  junit failed:", junit_failures() if test else "none")
        print("  gametest summary:", summary[-1] if summary else "(none)")
        print("  gametests failed:", failed)
        return 0
    finally:
        for path, text in backups.items():
            path.write_text(text)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
