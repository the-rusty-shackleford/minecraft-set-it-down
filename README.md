# Set It Down (NeoForge 1.21.1)

Put any item down on any block face as decoration: on a table, hung on a wall, under a ceiling.
Turn it, tip it, pile more of the same on it, take it back. Armour stands as the worn piece.

By Rusty Shackleford and nfx, AGPL-3.0-or-later, mod id `setitdown`, package
`com.chunkworks.setitdown`. Built from the README Rusty sent (2026-10-07) for a mod of that name;
where this one differs, and why, is below.

## Controls

| Action | Result |
| --- | --- |
| **Set Down key (`Y`)** with the crosshair on a block face | One of what you hold is set down where the crosshair is: lying flat on floors and ceilings (turned so its top points away from you), hanging on walls as in an item frame (no frame). Main hand; the off-hand if the main hand is empty. |
| **`Y`** at a display of the same item, or at a face within a quarter block of one | Adds one to the pile, up to `maxStackedItems` (4). |
| **Right-click** a display | Turns it 45 degrees in the surface's plane. |
| **Sneak + right-click** a display | Tips it 45 degrees up out of the plane: flat, leaning, on edge, flat again. |
| **Left-click** a display | Takes the top item back into your inventory, or a bag you carry. Two hits by default (`requireTwoHits`): the first marks it, the second takes. The last item takes the display with it. Creative players take and get nothing back; middle-click picks the item. |
| Break, blow up, push away or flood the block it rests on | Drops everything on it. |

`Y` is free in the pack (taken: B G H J M N O K P R V Z C X, Left Alt, Left Control) and
rebindable under Set It Down in the controls screen. It works while sneaking.

**No right-click of any item or block changes** (D-0001). The README's gesture was sneak +
right-click on a block, taken before the held item's own use. That shadows tilling, stripping,
waxing, bone meal, buckets, boats, spawn eggs, item frames, armour stands and more while sneaking,
and in this pack Create's wrench, which dismantles by sneak + right-click (`WrenchItem.useOn`).
Even "only when nothing else used the click" would eat food, draw bows, raise shields and block
the off-hand (pickaxe in the main hand, torches in the off-hand). Rusty: "most if not all things
should be able to be set down in such a way that does not interfere with their other uses. If
this means changing the control scheme to accommodate for that, so be it." So the gesture is a
key of its own, every item can be set down (block items, buckets, food, shields, bows, spawn eggs
included), and the README's deny-list and `allowBlockItems` are gone. The mouse acts on a display
only with the crosshair on it, as on an item frame.

## How it looks

**As you hold it** (1.1.0, D-0005). An item is drawn through the game's item renderer as a hand
draws it, so the model a resource pack gives a held item is what is seen set down: Fresh Food's 3D
apple and Refined Tools' sword (through Modefite, which reads their newer item definitions on
1.21.1 and gives them only to hands), NeoForge's per-view models, the trident's. Only the hand's
pose is dropped (a client mixin on the item renderer's view transform, active only while a display
draws): the item's own model is turned as an item frame turns it. A model only a hand draws is
built upright, as it would sit on a plate, so it stands on its base where it could (no taller than
twice its narrowest width): Fresh Food's apple stands, stem up, its loaf and steak lie flat.
Anything taller lies on its broadest face (a carrot on its side, a sword on its flat), and a long
one corner to corner as its sprite drew it. It is drawn as big as the item's own model shows
(D-0006). 1.0.0 drew the item-frame view, where those packs give the flat sprite.

Each item's box is measured once per model (and per its components, which an item definition may
choose a model by), from what its render actually emits (the same capture as Quick Slot's), and
the display is pushed off the surface by however much its turn, its tip and its pile between them
need. A flat sprite sits a hair above the surface, a 3D model rests on it instead of sinking in,
and a piece tipped on its edge still clears the block. A
display set off-centre stays wholly on its face: near an edge it is moved in by half its width, or
by half its diagonal (0.354, not 0.25) once turned 45 degrees, so no corner pokes over. A stair's
lower step takes it on its open half.

**Sizes** (D-0004, D-0006). An item frame draws every item the same size, so the README's sword
came out the size of a compass. Rusty: "Mounted swords seem pretty small … can we do a better job
with relative sizing?" Each item now has a size, the side of the square it fills, from the first
of four tags it is in:

| Size | Side | Tag | Seeded with |
|---|---|---|---|
| long | 0.8 | `#setitdown:size/long` | swords, tridents, bows, crossbows, maces, shields, Ranged Weapons Mod's long guns |
| tool | 0.7 | `#setitdown:size/tool` | pickaxes, axes, shovels, hoes, fishing rods, wrenches |
| normal | 0.5 | (none) | everything else, as in an item frame: books, blocks, food, Ranged Weapons Mod's pistols |
| small | 0.375 | `#setitdown:size/small` | compasses, clocks, ingots, gems, raw ore, apples, potatoes, eggs, pearls, magazines |
| tiny | 0.3 | `#setitdown:size/tiny` | nuggets, seeds, dusts, berries, rounds |

A model is drawn at its own size, sixteen of its pixels to the square, shrunk only if it would not
fit the square, in the middle of it (D-0006). An item frame shrinks some models to fit its frame
(a block, a shield and a chest to half, Ranged Weapons Mod's pistol to 0.45), and 1.0.0 drew them
shrunk again by their size: a block a quarter block, a pistol 0.12. A friend's feedback on 1.0.0:
"Pistol & shield are unrealistically small". So now a block is the half-block cube its size says,
a sword's blade about 0.85 of a block long, short of full size, and the shield and the pistol are
a size up. A whole block's box is the cube it is drawn as, so the crosshair finds it. Its box,
its edge clamp and its pile's spread go with its size. A pack can move items between the tags.

**Armour** (helmets, chestplates, leggings, boots, elytra) is shown as the worn 3D piece, not its
sprite (D-0003). An invisible armour stand, never added to the world, wears it and is drawn
through the game's entity renderer, so it looks exactly as on a real stand in your packs: EMF and
Armored Legacy's `armor_stand_*.jem` models, trims, dyes, glint and modded armour included. It
stands on floors facing whoever set it down, hangs upright under ceilings, and hangs back to the
wall on walls. On a floor or a ceiling, sneak + right-click pitches it forward onto its face;
walls ignore the tip, since their turn already rolls it about the wall. Armour never piles. Its box
is measured once per item and components (1.1.1, D-0008), so a piece Survivalist Armor's kits
reshape rests as its own shape, not as the plain piece's.

**Piles and clusters**, by item tag:

- `#setitdown:stackable_pile`: flat things that climb, such as ingots, nuggets, gems, books,
  paper, maps, bread, cookies, pies, leather, bricks, rods and discs. Each layer lies on the one
  below, nudged and turned a few degrees.
- `#setitdown:stackable_cluster`: round things that heap, such as coal, raw ore, dusts, eggs,
  slime, ender pearls, apples, carrots, potatoes, berries, meat, fish and seeds. They shrink a
  little and spread across the spot: a line, then a triangle, then a square, and from five a
  second layer. Every item stays within the display's half-block square.

Copies never pass through each other (D-0007), which a resource pack's 3D models would. Flat ones
that would overlap lie one on another, a layer each, as steaks or coal do; thick ones are spread
apart until none overlaps another (by what each shows), then shrunk together into the display's
square. A pile whose layers would stand taller than its box (a quarter block flat; eight 3D loaves
would stand three quarters) is heaped as a cluster is, and a heap is never taller than its box. A
sprite's pile and cluster look as before.

Both tags list vanilla items and pull in the matching `c:` tags (`#c:ingots`, `#c:foods/fruit`,
…), so modded ingots and foods pile without work. Anything else is one to a spot; a different item
within a quarter block of a display is refused ("No room here"), so displays never interpenetrate.
The numbers are one table, `domain/StackLayout`.

**Display Delight**: its plates (`#c:food_display_plate`) take a display in their middle. Its own
sneak + right-click on foods is untouched.

## Support, drops and the give (D-0002)

A display rests on its support, the block it was set on. That support must have something to rest
on: a collision shape (so water, lava, flowers, torches and cobwebs are refused) or a layer of
snow. The display stays only while the support still has a face in its plane under it. When the
block is broken, blown up, flooded, pushed by a piston (whose head moves into the cell) or
swapped for something shorter, the display drops what it holds where it is, within half a second
(`doEntityDrops` respected). An explosion near it drops it too.

A taken item goes through Carried (`Carried.giveOrDrop`) into the inventory, or a carried bag
already holding some of it, and only what fits nowhere lands at the player's feet. The README's
floor drop would have left it on the ground with a five-minute life. Arrows and bullets pass
through displays.

## Config

`config/setitdown-server.toml` is the server's, synced to every client:

- `maxStackedItems` (4): how many of the same item share a spot, 1 to 8. 1 turns piling off.
- `requireTwoHits` (true): take an item only on a second hit by the same player.
- `breakClickWindowTicks` (40): how long the first hit waits for the second.
- `dropEntirePile` (false): one take takes the whole pile.

`config/setitdown-client.toml` is each player's own:

- `itemScale` (0.5): how big items look; 0.5 is as designed (a normal item fills half a block, as
  in an item frame, and a sword 0.8). Only the look: the boxes you click keep their designed size.

`#setitdown:undisplayable` ships empty, for a server to name items that may not be set down.

## Build and test

`publishToMavenLocal` in `../minecraft-carried` first (nested). Then:

```
./gradlew clean build          # JUnit on domain/, the GameTests, the booth (needs :7)
./gradlew build -PskipBooth    # without the booth
```

- **Layers:** `src/domain` is pure Java, tested by JUnit in `src/test`: placement, the clamp and
  the stair cut, turns and tips, the pose and lift maths, the pile and cluster tables, clicks,
  join or refuse, box sizes. `src/main` adapts it to the game. `src/gametest` is a mod of its own
  with the GameTests and the booth.
- **GameTests:** real players clicking through the server's own packet handler: every face,
  every kind of item, piles, refusals, the edge, the stair, the plate, armour, turn, tip, two-hit
  takes into the inventory and into a worn Backpacks+ bag (the sibling build), creative, the
  support broken, flooded, exploded and pistoned, save and load, the payloads. One guards that a
  right-click on a block, sneaking or not, sets nothing down and the hoe still tills.
- **Mutations:** `devtools/verification/mutations-1.0.0.py` and `mutations-1.1.0.py` run one
  mutation per run, each caught by the tests named in its record.
- **The booth** (`./gradlew runPhotoBooth`, Xephyr `:7`) runs the pack's Iris, Complementary,
  EMF, ETF, Display Delight, Modefite, the Ranged Weapons Mod and the shape-changing resource
  packs. `devtools/booth/fetch.py` fetches them from the published pack, sha1-checked. It
  photographs every scene and checks the controls on real input: Y and Shift through XTEST, the
  mouse through the client's click path; and that the hand's 3D models are what is measured and
  drawn. Pixlli's 128x textures are left out: they change the look, not the shapes, and an atlas
  that size is past llvmpipe. Without Modefite, Fresh Food's and Refined Tools' item definitions
  do nothing on 1.21.1, and without `incompatibleResourcePacks` listing Fresh Food (as Rusty's game
  does) the game drops it at start: 1.0.0's booth had neither, so it never showed the flat sprites
  a player saw. A key tap is one helper process (`xkey.py tap`): a press and a release sent from
  two held the key past X's repeat delay when the machine was busy, and Y repeated.
- **The mixin:** `mixin/ItemRendererMixin` (client, MixinExtras' `@WrapOperation` on the item
  renderer's `ClientHooks.handleCameraTransforms` call) acts only on a render `client/HeldView`
  has asked for, once; every other item render passes through.

The jar is needed on the server and on every client: the entity and the key's payloads are
registered on both.
