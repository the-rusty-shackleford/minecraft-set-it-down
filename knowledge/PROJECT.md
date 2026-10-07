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

## Decisions

D-0001 the key (no right-click changes), D-0002 support, room, clamp and the give, D-0003 armour on
an invisible stand, D-0004 relative sizes. The README's departures are listed in `README.md`.

## Shape

`src/domain` (pure, JUnit) ← `src/main` ← `src/gametest` (GameTests and the booth). The booth's
game directory is filled by `devtools/booth/fetch.py` from the published pack.
