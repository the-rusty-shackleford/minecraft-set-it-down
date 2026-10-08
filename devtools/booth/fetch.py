# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Fill the booth's game directory (run/booth) with what the pack plays with: the shader mods and
Complementary, EMF and ETF (armour on its stand), Display Delight and Farmer's Delight (a plate),
Modefite (which reads the newer item definitions Fresh Food and Refined Tools give their held 3D
models by, D-0005), the Ranged Weapons Mod and its Metals and Materials (the pistol, D-0006), and
the resource packs that change an item's shape: 3D Default, Fresh Food, Farmer's 3D, Refined Tools
and Armored Legacy with its no-tassets patch. Pixlli's 128x textures are left out: they
change how things look, not where they rest, and an atlas that size is past llvmpipe.

Every file comes from the published pack on the Mod Hub and is checked against the pack's own
sha1; the folders come from the pack's overrides. Then the booth's options and Iris's settings are
written, the resource packs in the order Rusty's own game has them. Run once, and again when the
pack changes one of these:

    uv run --no-project python devtools/booth/fetch.py [pack-url]
"""
import hashlib
import io
import json
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

HUB = "http://192.168.0.203:8080/pack.mrpack"
BOOTH = Path(__file__).resolve().parents[2] / "run" / "booth"

MODS = ("iris-neoforge", "sodium-neoforge", "entity_model_features", "entity_texture_features", "displaydelight", "FarmersDelight",
        "modefite", "rangedweaponsmod", "metalsandmaterials")
SHADER = "ComplementaryUnbound_r5.8.1.zip"
# Lowest priority first, as options.txt lists them; the pack's own order (Rusty's game).
PACKS = ("3D Default 1.20+ v1.15.0.zip", "Fresh Food.zip", "Farmers3D_1.21.1_fixed.zip",
         "FarmersDelight_ConsistentRaw_v21.zip", "Refined Tools 3.0", "Armored Legacy 1.5.1.zip",
         "Armored-Legacy-No-Tassets")
# Made for newer versions, loaded anyway: Rusty's game lists it in incompatibleResourcePacks. Without
# that the game drops it from the list at start ("no longer compatible"), as 1.0.0's booth did.
INCOMPATIBLE = ("Fresh Food.zip",)


def fetch(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()


def main():
    pack = zipfile.ZipFile(io.BytesIO(fetch(sys.argv[1] if len(sys.argv) > 1 else HUB)))
    index = json.loads(pack.read("modrinth.index.json"))
    for sub in ("mods", "resourcepacks", "shaderpacks", "config"):
        (BOOTH / sub).mkdir(parents=True, exist_ok=True)
    wanted = []
    for f in index["files"]:
        name = f["path"].split("/")[-1]
        folder = f["path"].split("/")[0]
        if (folder == "mods" and name.startswith(MODS)) or (folder == "resourcepacks" and name in PACKS) \
                or (folder == "shaderpacks" and name == SHADER):
            wanted.append(f)
    for f in wanted:
        target = BOOTH / f["path"]
        if target.exists() and hashlib.sha1(target.read_bytes()).hexdigest() == f["hashes"]["sha1"]:
            print("have", f["path"])
            continue
        data = fetch(f["downloads"][0])
        if hashlib.sha1(data).hexdigest() != f["hashes"]["sha1"]:
            sys.exit(f"sha1 mismatch for {f['path']}")
        target.write_bytes(data)
        print("got ", f["path"], len(data))
    for name in PACKS:
        prefix = f"overrides/resourcepacks/{name}"
        members = [m for m in pack.namelist() if m == prefix or m.startswith(prefix + "/")]
        if not members:
            continue
        if (BOOTH / "resourcepacks" / name).is_dir():
            shutil.rmtree(BOOTH / "resourcepacks" / name)
        for m in members:
            if m.endswith("/"):
                continue
            out = BOOTH / "resourcepacks" / m[len("overrides/resourcepacks/"):]
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(pack.read(m))
        print("got ", prefix, f"({len(members)} entries)")
    missing = [p for p in PACKS if not (BOOTH / "resourcepacks" / p).exists()]
    if missing:
        sys.exit(f"not in the pack: {missing}")
    (BOOTH / "config" / "iris.properties").write_text(
        "enableShaders=true\nshaderPack=" + SHADER + "\ndisableUpdateMessage=true\nmaxShadowRenderDistance=8\n")
    packs = ",".join(json.dumps(p) for p in ["vanilla", "mod_resources"] + ["file/" + p for p in PACKS])
    incompatible = ",".join(json.dumps("file/" + p) for p in INCOMPATIBLE)
    (BOOTH / "options.txt").write_text(
        "onboardAccessibility:false\nnarrator:0\nsoundCategory_master:0.0\nrenderDistance:4\nsimulationDistance:5\n"
        "graphicsMode:1\nmaxFps:120\nguiScale:2\nentityShadows:true\nfov:0.0\n"
        f"resourcePacks:[{packs}]\nincompatibleResourcePacks:[{incompatible}]\n")
    print("wrote options.txt and config/iris.properties")


main()
