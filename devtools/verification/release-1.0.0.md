# 1.0.0 verification: Set It Down

Built and checked 2026-10-07, from the README Rusty sent. **Released 2026-10-07 in pack 1.77.0** on
Rusty's go: tag `v1.0.0` at `f6bece7`, the jar and `wiki.zip` on the GitHub release (deployment
record: `knowledge/releases/pack-1.77.0.md` in the server repo).

## The release gate

`./gradlew clean build` again on the tagged tree, 2026-10-07, on Xephyr `:7`, one client: 58 JUnit
(the clean build took `:test` from Gradle's build cache, so it was run again with `test --rerun`,
green), 25 GameTests, the booth's 20 checks. The jar came out byte for byte the same, sha1
`0049226f`. Both release assets were downloaded back and match:

- `setitdown-1.0.0.jar`, 117887 bytes, sha1 `0049226f5ed004c49f82bce2bd27904806e397d3`;
- `wiki.zip`, 230230 bytes, sha1 `e50837a38b0b34743142fa4dda18f36f0387e170` (`tools/wiki/bundle.py`:
  page `set-it-down`, five images).

On the box after the restart: the jar's sha1 in `/data/mods`, `Set It Down 1.0.0 (setitdown)` in the
new log, 0 missing registry entries, the 38 baseline errors, 20.000 TPS, the Hub's "server matches
the published pack", and the live `/wiki/set-it-down` and `/wiki/news`.

## The gate

`./gradlew clean build`, on Xephyr `:7`, one client, nobody's game open:

- **58 JUnit** on the domain. Partitions are in each test's header: faces, the clamp, the stair
  cut, the reach by size, turns and tips, the pose and lift maths (flat sprites, tipped, on edge,
  3D models, piles, sizes, armour on floors, walls and ceilings), the pile and cluster tables, two
  hits, join or refuse, box sizes.
- **25 GameTests.** Clicks go through the server's own packet handler (`handleInteract`); the key
  calls the payload's handler, `Placing.setDown` and `Placing.add`.
- **The booth's 20 checks.** The pack's Iris, Complementary, EMF and ETF, Display Delight and
  Farmer's Delight, and 3D Default, Fresh Food, Farmer's 3D, Refined Tools and Armored Legacy,
  fetched from the published pack by `devtools/booth/fetch.py` and sha1-checked. It covers:
  - the scenes dressed, Display Delight's plate and the corner;
  - the measuring renders: armour on the stand's body and head, a flat item thin, a 3D model thick;
  - a real Y, and Y with a real Left Shift (XTEST);
  - piling, turning, tipping and taking, through the client's own click path;
  - the guard: a sneaking right-click with bread on a block eats it and sets nothing down.
- **Jar:** `build/libs/setitdown-1.0.0.jar`, 117887 bytes, sha1
  `0049226f5ed004c49f82bce2bd27904806e397d3`. It nests Carried 1.0.0 (`46850745`).

## Mutations

`devtools/verification/mutations-1.0.0.py`, one mutation per run, the sources restored after:

| Run | Mutation | Tests that failed |
|---|---|---|
| A | no room refusal | `PlacementTest` (2), `anythingElseWithinAQuarterBlockHasNoRoom` |
| B | the README's support rule, "not air" | `itsSupportFlooded…`, `itsSupportPushedByAPiston…`, `nothingToRestOnIsRefused` |
| C | a floor drop instead of the Carried give | `twoHitsTakeTheTopItemBack…`, `aTakenItemGoesIntoAWornBag…`, `oneHitTakesTheWholePile…` |
| D | the clamp ignoring the turn | `FacePlacementTest` (2), `nearAnEdgeItIsMovedOntoTheFace…` |

## What the booth showed, judged by eye

- **Table:** single items, piles (books, ingots), clusters (apples, coal, beef), 3D models (the
  trident, the lantern, Fresh Food's bread) all rest on the surface, none sunk. Display Delight's
  plate takes its porkchop in the middle.
- **Wall:** items hang as in an item frame. Armour hangs as the worn piece, back to the wall.
- **Sizes (D-0004):** after Rusty's word, the sword, pickaxe, book, compass and nugget line-up and
  the wall read in size order.
- **Ceiling:** items hang under it, the helmet upright.
- **Floor armour:** stands facing the camera. Pitched 45 degrees it leans and rests; at 90 it lies
  face down.
- **Reference stands:** real armour stands wearing the same pieces were set beside the displays.
  Every piece matches, including Armored Legacy's golden leggings, a tall red tabard.
- **Tips:** flat, leaning, on edge; the on-edge sword stands on the bench.
- **Corner:** a book turned 45 degrees near a pedestal's corner lies wholly on its face.

## Not verified

- **Pixlli:** the booth renders in software and without Pixlli's 128x textures; the pack's look is
  only approximated.
- **Rusty's own client:** he has not yet set anything down.
- **Modded items** beyond Create's wrench, checked by reading its class, are untested.
