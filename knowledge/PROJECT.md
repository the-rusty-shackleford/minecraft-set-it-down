---
title: Set It Down — project
type: overview
layer: store
tags: [overview]
---

# Set It Down

## What this is

Put any item down on any block face as decoration (Rusty, 2026-10-07, from a README he sent for a
mod of that name; only the README exists, so this is built from it). One entity, the display, and
one key, Y. Chunkworks, AGPL-3.0-or-later, `com.chunkworks.setitdown`; nests Carried.

## Status: 1.0.0 released (pack 1.77.0, 2026-10-07)

- Built (2026-10-07): the key and its payloads, the display (one item, a pile, a cluster, or
  armour on an invisible stand), placement with the clamp, the stair cut, the plate and room, turn,
  tip, two-hit takes through Carried, the support rule and drops, the renderer with measured
  bounds, configs, tags, the README.
- Gate (2026-10-07): 58 JUnit, 25 GameTests, the booth's 20 checks, 4 mutation runs each caught;
  jar sha1 `0049226f`. The record is `devtools/verification/release-1.0.0.md`.
- Relative sizes added after Rusty watched the first booth (D-0004): a sword was the size of a
  compass.
- Released on Rusty's go, 2026-10-07: public at `the-rusty-shackleford/minecraft-set-it-down`, tag
  `v1.0.0` (`f6bece7`), jar and `wiki.zip` on the GitHub release; in pack 1.77.0, both sides,
  deployed 20:43 UTC. The server repo's `knowledge/releases/pack-1.77.0.md` is the deployment
  record.
- Not yet seen: anyone setting something down on the box, or Rusty in his own client.

## 1.1.0: a friend's feedback on 1.0.0, released 2026-10-08 in pack 1.78.0

Rusty passed it on with "sneak this into the same release after all done with submersibles", and it
shipped with the submarines on his go: tag `v1.1.0` at `bf528ca`; the release gate (2026-10-08,
`clean build --no-build-cache` on `ff6393f`) green with 68 JUnit, 26 GameTests and the booth's 28
checks; sha1 `75e4c7da` on GitHub and on the server (the server repo's
`knowledge/releases/pack-1.78.0.md`). `bf528ca` adds only the wiki's images, all six now from 1.1.0's
booth, and two booth cameras: the ceiling photo had been the inside of a reference armour stand's
chestplate since 1.0.0 (its eye stood in the stand), the wall's too close for the hung armour. The
booth ran green again on it. Not yet seen: Rusty's look at 1.1.0 (photos or game), and the friend's.

- **Models (D-0005).** "Some models are defaulting to 2d sprite model when 3d model exists from
  resource packs. Ex, all tools / weapons, some food like the apple." The pack's Modefite gives
  Fresh Food's and Refined Tools' 3D models only to the hands' views; 1.0.0 drew the item-frame
  view. Now drawn in a hand's view, the hand's pose swapped for a resting one (a client mixin): a
  model only the hand draws stands on its base if it could, else lies on its broadest face, a
  long one corner to corner as its sprite drew it.
- **Sizes (D-0006).** "Pistol & shield are unrealistically small, should do a pass on other items
  that might be under sized." The cause was the item frame's own shrinking (a block, a shield to
  half, the pistol to 0.45), multiplied by the size. Now a model is drawn at its own size, filling
  its square at most; a hand's model as big as the item's own shows; a whole block's box is its
  cube. Shields moved to long, pistols and revolvers to normal.
- **Piles with 3D models (D-0007).** Rusty: "should also be conscious of stackable items clipping
  with 3d models in effect". Flat copies that would overlap lie one on another; thick ones are
  spread apart and drawn back into the square; a pile too tall for its box is heaped; a heap is
  never taller than its box.
- **Gate (2026-10-07), at the commit:**
  - 68 JUnit; the twelve new rules each caught by `devtools/verification/mutations-1.1.0.py`.
  - 26 GameTests.
  - The booth, 29 checks green. It fetches Modefite, the Ranged Weapons Mod and Metals and
    Materials and lists Fresh Food as an incompatible pack as Rusty's game does: 1.0.0's booth ran
    without either, so it never saw the friend's flat sprites. Its key taps are one helper each,
    after a release sent from a second helper held Y past X's repeat delay.
  - Photos in `run/booth/screenshots/` (`booth-held*.png` new); 1.0.0's for comparison were kept
    outside the repo.

## 1.1.1: kitted armour rests as itself; built and gated 2026-10-09, unreleased

For Survivalist Armor (nfx's cosmetic kits, a data component on the piece): a kitted chestplate set
down after a plain one took the plain one's measured box, since `BoundsCache.armour` kept one box per
`Item`. Now per item and components (D-0008). Also English names for the mod's item tags, which EMI
listed as untranslated. Survivalist Armor's booth check ("Set It Down measures a kitted chestplate
apart from a plain one") fails on 1.1.0 and passes on 1.1.1.

- **Gate (2026-10-09, `clean build --no-build-cache`):** 68 JUnit, 26 GameTests, the booth's 29
  checks, on the rootful `Xwayland :7` that stands in for Xephyr since the OS upgrade.
- **Release** with Survivalist Armor 0.1.0, on Rusty's go.

## Decisions

D-0001 the key (no right-click changes), D-0002 support, room, clamp and the give, D-0003 armour on
an invisible stand, D-0004 relative sizes, D-0005 the hand's model at rest, D-0006 at its own size
(shields long, pistols normal), D-0007 copies never pass through each other. The README's
departures are listed in `README.md`.

## Shape

`src/domain` (pure, JUnit) ← `src/main` ← `src/gametest` (GameTests and the booth). The booth's
game directory is filled by `devtools/booth/fetch.py` from the published pack.
