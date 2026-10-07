# Submersibles — handoff & design summary

**Status: design only. No code written yet.** Written 2026-10-07 from nfx's brief, plus a
read of the three jars this mod has to interoperate with (Immersive Aircraft 1.4.6, Man of
Many Planes 0.2.1, Luminance 1.1.0) in the Prism "The Boys" instance.

Working names (change freely): mod id `submersibles`, vehicles `submersibles:explorer_sub`
(4-seat) and `submersibles:scout_sub` (1-seat).

---

## 1. The brief

Two crewed submarines for deep-water exploration:

| | **Explorer** (4 seats) | **Scout** (1 seat) |
|---|---|---|
| Role | slow, tough, hauls cargo and people | fast, cheap, fragile, solo |
| Storage | large hold (9x3) | small locker (9x1) |
| Headlights | wide twin floods | single narrow spot |
| Upgrades | 4 upgrade + 2 weapon slots | 2 upgrade + 1 weapon slot |
| Crafting / repair | Vanilla-Wheels-style parts + repair | same |

Plus, from nfx's follow-up: **the upgrades and upgrade slots from Man of Many Planes must
work in these subs.**

---

## 2. The one decision that shapes everything

**Build this as an Immersive Aircraft (IA) addon, not as a Vanilla Wheels (VW) vehicle.**

The follow-up request settles it. "The upgrades and upgrade slots from Man of Many Planes"
are not actually MoMP's — MoMP is a 182-file addon that ships two entity classes, two
renderers, two `.bbmodel`s and two data files, and gets its slots, weapons, boosters, dye
and GUI entirely from Immersive Aircraft. Verified: MoMP's whole upgrade surface is this,
in `data/man_of_many_planes/aircraft/scarlet_biplane.json`:

```json
"inventorySlots": [
  {"type": "boiler",    "x": 12, "y": 37},
  {"type": "booster",   "x": 40, "y": 32},
  {"type": "weapon",    "x": 62, "y": 21},
  {"type": "upgrade",   "x": 84, "y": 32},
  {"type": "inventory", "x": 8, "y": 68, "cols": 9, "rows": 3}
]
```

The upgrade *items* (Hull Reinforcement, Enhanced Propeller, Nether Engine, ...) live in
`immersive_aircraft`, and the code that reads them —
`InventoryVehicleEntity.getTotalUpgrade(VehicleStat)` — is on IA's own vehicle base class.
So: any vehicle that extends `immersive_aircraft.entity.InventoryVehicleEntity` and ships
an aircraft JSON with `upgrade` slots accepts every IA and MoMP upgrade **for free, with no
compat code at all**. Any vehicle that does not, accepts none of them without
reimplementing IA's inventory, slot, upgrade, weapon and GUI stack.

A VW submarine would be the second case. VW (nfx's own patched `vanillawheels-1.12.0`) has
storage regions, cargo slots, dye, a key fob and a Mechanic Lift, but **no upgrade-slot
concept and no `VehicleStat` system** — its profile JSON is fixed tuning. Bolting IA
upgrades onto it means an ASM patch of a source-less library plus a new container GUI:
weeks of work, and a fresh patch to re-derive on every VW update.

**What we keep from Vanilla Wheels is the *feel* of its crafting and repair**, which is
cheap to reproduce because VW's repair is already IA-shaped. Compare the two jars'
`en_us.json`:

- VW: `"Broken — right-click it to repair it, or use a Mechanic Lift"`, `"Too hungry to work on it"`, `"%s%% repaired"`
- IA: `"%s%% repaired!"`, plus config `repairSpeed` ("Repair per click") and `repairExhaustion` ("Player exhaustion per click")

Same gesture, same hunger cost, same percentage readout. IA gives us that out of the box;
the VW-specific part we add on top is the **part items** (hull/engine/props) feeding a
plain workbench recipe. **No lift/dock block** — nfx's call, 2026-10-07: craft both subs
at a crafting table, repair them by right-clicking.

### Cost of the two routes

| | IA addon (recommended) | VW vehicle |
|---|---|---|
| MoMP/IA upgrades | free, zero code | full reimplementation |
| Weapons (guns) | free: rotary cannon, heavy crossbow, bomb bay, telescope | none exist; build from scratch |
| Storage GUI | free (`inventory` slots) | free (`storage.region`) |
| Boost rockets, dye, banner | free | dye only |
| Underwater physics | write `updateVelocity()` ourselves (~150 lines) | write it ourselves too, by ASM, in a source-less jar |
| Headlights | add via Luminance (already in the pack) | free-ish (VW has `headlights`) |
| VW-style parts crafting + repair | ~a day of items and recipes | free |
| Source to work against | none (javap only, but a clean public API) | none (javap, plus our own ASM patch tree) |

---

## 3. Target environment

- Prism instance: `C:\Users\nfx73\AppData\Roaming\PrismLauncher\instances\The Boys\minecraft\`
  — NeoForge 1.21.1 (21.1.248), Java 21. Same loader as the V10 pack.
- The jars that matter, all already installed:
  - `immersive_aircraft-1.4.6+1.21.1-neoforge.jar` — the base we extend.
  - `man_of_many_planes-0.2.1+1.21.1-neoforge.jar` — the addon we copy, and whose upgrades
    must work. Nothing to link against; it is a *sibling*, not a dependency.
  - `luminance-1.1.0.jar` — ChunkWorks' dynamic block-light mod (loose in the pack, also
    nested in VW). This is the headlight engine.
- Project home (new): `C:\Users\nfx73\Dev mods\submersibles\`. Scaffold from
  `Dev mods\cavestalker` (`build.gradle`, `settings.gradle`, `gradlew*`,
  `gradle/wrapper/`), then edit `gradle.properties`. This mod needs real Java, so it is
  **not** a low-code jar like Trailblazer / Farmpickup / Trailer.
- IA is Architectury-built but ships a NeoForge jar; compile against the jar as a file
  dependency — there is no Maven artifact in the pack. IA also ships
  `immersive_aircraft.accessWidener`; if something needed turns out to be package-private
  at runtime, find a public alternative rather than trying to re-run their widener from a
  consumer mod.

---

## 4. What an IA addon actually has to ship

Read straight off MoMP, which is the minimal working example:

```
src/main/java/.../Submersibles.java               # entity types + items (Registration.register per vehicle)
                 /entity/ExplorerSubEntity.java   # extends EngineVehicle (see section 6)
                 /entity/ScoutSubEntity.java
                 /client/SubmersiblesClient.java  # two Registration.register(EntityType, renderer) calls
                 /client/ExplorerSubRenderer.java # extends InventoryVehicleRenderer; getModel()/getModelId()
resources/assets/submersibles/objects/explorer_sub.bbmodel    # the model, loaded by IA's BBModelRenderer
                 textures/entity/explorer_sub.png
                 textures/item/explorer_sub.png
                 textures/item/explorer_sub_color.png         # dye overlay (optional)
                 models/item/explorer_sub.json
                 lang/en_us.json
                 luminance/entities.json                      # optional whole-hull glow; see section 8
          data/submersibles/aircraft/explorer_sub.json        # THE vehicle definition: stats, slots, mounts, seats, boxes
                 recipe/explorer_sub.json, pressure_hull.json, ...
                 advancements/...
```

Facts worth knowing before writing any of it:

- **`VehicleEntity.identifier` is the entity type's id**, and `getVehicleData()` is
  `VehicleDataLoader.get(identifier)`. `VehicleDataLoader` is a
  `SimpleJsonResourceReloadListener("aircraft")`, so our file at
  `data/submersibles/aircraft/explorer_sub.json` is found automatically — no registration,
  no mixin. Same for `aircraft_upgrades` if we ever ship our own upgrade items.
- **The GUI is generic.** MoMP registers no screen; IA's `VehicleScreen` builds itself from
  `inventorySlots`. The slot coordinates in the JSON are literal GUI pixels.
- **Unknown property keys are silently ignored.** `VehicleData` iterates
  `VehicleStat.STATS` and pulls each name out of the JSON, so a typo leaves that stat at
  its default with no error. Upstream ships two live examples — `bamboo_hopper.json`
  contains `"fraction"` and `"wasterFriction"`, both dead. **Diff our property keys against
  the list in section 5.**
- The model is a Blockbench `.bbmodel` read by IA's own `BBModelRenderer`, not a vanilla
  JSON model. Bones are addressable from the renderer via
  `ModelPartRenderHandler.add("<bone>", ...)` (MoMP uses this for `dyed_body` and
  `dyed_body_highlights`). nfx's bbmodel conventions and the pure-Python generator from the
  Trailblazer / pickup work carry over, but **IA's loader is not VW's loader** — check what
  it tolerates (format version, per-element visibility, multiple textures) before reusing
  the VW mesh pipeline.

---

## 5. Upgrade compatibility — the actual contract

### How upgrades resolve

`VehicleProperties.get(stat)` is `base * total`, where `total` starts at 1 and every
upgrade item in an `upgrade` slot adds its delta (negatives in a first pass, positives in a
second). `getAdditive(stat)` is `base + total - 1`. The item-to-upgrade map is
`VehicleUpgradeRegistry.INSTANCE`, filled by `UpgradeDataLoader` from
`data/<any namespace>/aircraft_upgrades/<item path>.json`, whose body is just
`{"<statName>": <float delta>}`.

So compatibility is not a code path — it is **naming our base stats the same as IA's**.
Every stat an upgrade touches must exist in our vehicle JSON (or be meaningful at its
default), or that upgrade silently does nothing.

### The full stat list (from `VehicleStat`, defaults in brackets)

`engineSpeed`, `verticalSpeed`, `yawSpeed`, `pitchSpeed`, `pushSpeed`, `brakeFactor` (0.95),
`acceleration`, `durability`, `fuel`, `friction` (0.015), `glideFactor`, `lift`,
`rollFactor`, `groundPitch`, `stabilizer`, `wind`, `mass`, `hud` (-1), `dials` (-1),
`groundFriction` (0.95), `waterFriction` (0.9), `rotationDecay` (0.97),
`horizontalDecay` (0.97), `verticalDecay` (0.97). Anything unlisted defaults to 0.

### Every installed upgrade, and what it should do to a sub

| Upgrade item | Its JSON | In a submarine |
|---|---|---|
| `hull_reinforcement` | `durability +1.0` | **works as-is** — this is the "hull reinforcement" nfx asked for, already in the pack |
| `nether_engine` | `engineSpeed +0.4`, `fuel +0.3` | **works** — the main speed modifier |
| `steel_boiler` | `engineSpeed +0.25`, `fuel +0.5` | works |
| `sturdy_pipes` | `engineSpeed +0.1` | works |
| `eco_engine` | `engineSpeed -0.2`, `fuel -0.75` | works (the long-range explorer build) |
| `industrial_gears` | `fuel -0.2` | works |
| `enhanced_propeller` | `friction -0.75` | **works only if our drag term is `friction`** — see the rule below |
| `gyroscope` / `_dials` / `_hud` | `wind -1.0`, `stabilizer +0.03`, (+`dials`/`hud`) | partial: `wind` is a no-op underwater (we set base `wind: 0.0`), but `stabilizer` and the dashboards are useful. Acceptable; worth a tooltip line. |
| `improved_landing_gear` | `acceleration +0.5` | no-op unless we use `acceleration` for engine spin-up. Suggest we do, so the item is not dead weight. |

**The design rule that falls out of this table: never invent a new stat name for something
an existing upgrade already names.** Our drag term must be `friction`, thrust
`engineSpeed`, ballast rate `verticalSpeed`, toughness `durability`, burn rate `fuel`. New
stats are for genuinely new axes only. Two candidates:

- `lightRange` (default 0) — headlight reach, so a future "Floodlight Array" upgrade can
  extend it. `VehicleStat.register("lightRange", true)` is public API; no mixin needed.
- `crushDepth` (default 0) — how deep the hull survives, if we do depth damage at all
  (section 9, optional). It would pair naturally with `hull_reinforcement` — but giving
  that IA item a `crushDepth` delta means **overriding another mod's data file** from our
  pack. It works (ours loads later) and it is a pack-wide change; prefer shipping our own
  upgrade item.

### Weapons ("can be fitted with guns")

`weapon` slots take anything in the `immersive_aircraft:weapons` tag — Rotary Cannon
(gunpowder), Heavy Crossbow (arrows), Bomb Bay (TNT), Telescope. The mount geometry is
ours, in the vehicle JSON: `weaponMounts` is a list indexed by weapon-slot number, each
entry a map of `ROTATING` / `FRONT` / `DROP` to positions (`x`, `y`, `z`, optional `yaw`,
`pitch`, `roll`) in model units.

**Guns do fire underwater — the question is range, and it is a requirement, so we fix it.**
What the bytecode says:

- `BulletEntity extends AbstractHurtingProjectile` with no water handling of its own, and
  vanilla's `tick()` keeps a hurting projectile flying in water (inertia 0.8 per tick
  instead of 0.95, plus a bubble trail). So the Rotary Cannon shoots and hits at depth.
- `BulletEntity.tick()` is `super.tick()` then `if (getDeltaMovement().lengthSqr() < 0.1) discard()`,
  and the cannon's muzzle velocity is `4.0` blocks/tick. Decaying at 0.8/tick, that
  threshold (0.316 b/t) arrives after ~11 ticks, so **roughly 19 blocks of range
  underwater against ~74 in air** (quick geometric-series estimate from those constants,
  not a measurement — confirm in game).
- The Heavy Crossbow fires a vanilla arrow, whose water inertia is 0.6 — a handful of
  blocks at most. The Bomb Bay is worse: water's blast resistance absorbs most of an
  explosion, so TinyTNT barely scratches anything submerged.

So the stock cannon is usable but short-legged, and the other two are near useless at
depth. The fix is a **sub-specific weapon**, not a hope: `WeaponRegistry` is public
(`WeaponRegistry.register(...)` with a `WeaponConstructor`, plus
`WeaponRendererRegistry` client-side), so we ship a **Torpedo** that extends
`BulletWeapon` and fires a projectile which re-asserts its own velocity each tick instead
of inheriting vanilla's water decay — full range submerged, slower and with a bubble wake,
ammo some cheap craftable. Our vehicle JSONs list `weapon` slots either way, so IA's and
MoMP's weapons still mount; the torpedo is simply the one that works at 100 blocks down.
Budget ~100 lines plus a small model, and keep the torpedo item in our own namespace so
nothing in the pack changes.

---

## 6. Vehicle classes and physics

Hierarchy: `Entity` -> `VehicleEntity` -> `DyeableVehicleEntity` -> `InventoryVehicleEntity`
-> `EngineVehicle` -> `AircraftEntity` -> (`AirplaneEntity`, `AirshipEntity`, `Rotorcraft`).

**Extend `EngineVehicle`, not `AircraftEntity`.** `EngineVehicle` gives fuel, boiler slots,
engine power and spin-up, the warning system, the engine sound and the gauge;
`AircraftEntity` adds the lift/glide/wind flight model and steam trails, which a submarine
does not want. Render with `InventoryVehicleRenderer` (the `AircraftEntityRenderer` MoMP
uses sits above it and is aircraft-specific).

`EngineVehicle` has exactly the hook we need:

```java
@Override public boolean worksUnderWater() { return true; }   // base returns false
```

`EngineVehicle.tick()` tests `isInWater() && !worksUnderWater()` before running the engine,
so without this override the sub is dead in the water, literally.

Then the two abstract methods from `VehicleEntity`:

- `updateController()` — read the input flags. IA already binds Up/Down
  (`key.immersive_aircraft.multi_control_up` / `_down`), forward/back, left/right, boost and
  "use weapon/mount", so **ballast needs no new keybinds**: Up/Down drive `verticalSpeed`,
  forward/back `engineSpeed`, left/right `yawSpeed`, and pitch comes from the mouse through
  `pitchSpeed`.
- `updateVelocity()` — the submarine model. Sketch:
  - neutral buoyancy while submerged and powered: cancel gravity (`getDefaultGravity()` is
    overridable) so input times `verticalSpeed` is the only vertical term;
  - slight positive buoyancy with the engine off, so a dead sub surfaces instead of sinking
    forever (a sub sunk with no fuel must stay recoverable);
  - drag from `friction` while submerged and `waterFriction` at the surface, with
    `horizontalDecay` / `verticalDecay` as the per-tick decays;
  - `stabilizer` levels pitch and roll toward neutral — this is what makes the gyroscope
    upgrades meaningful;
  - no `wind`, no `lift`, no `glideFactor`; small `rollFactor` so it banks into turns;
  - out of water: fall, no thrust, heavy `groundFriction` — a beached sub should be
    shoveable but useless.

Worth reusing: `VehicleEntity.tick()` already emits bubble particles for 200 ticks while
`isUnderWater()`, and **nothing in IA ejects or drowns a rider** — its `drowning` field is
only a particle counter. Crew air is therefore plain vanilla: riders drown in a submerged
open cockpit. Decide (section 12) whether the cabin grants air (`setAirSupply` on each
passenger per tick, conduit-style) — for an *exploratory* sub it almost certainly should,
and that is about ten lines.

### Proposed base stats

Starting numbers, to be tuned in-game. The reference points are IA's biplane
(`engineSpeed 0.035`, `mass 2.0`) and warship (`engineSpeed 0.035`, `mass 10.0`,
`horizontalDecay 0.97`, `verticalDecay 0.925`).

```json
// explorer_sub — slow, tough, heavy
"properties": {
  "engineSpeed": 0.022, "verticalSpeed": 0.020, "yawSpeed": 1.8, "pitchSpeed": 1.5,
  "acceleration": 0.5, "durability": 4.0, "fuel": 1.2, "friction": 0.020,
  "stabilizer": 0.15, "rollFactor": 4.0, "mass": 9.0,
  "wind": 0.0, "lift": 0.0, "glideFactor": 0.0,
  "waterFriction": 0.92, "groundFriction": 0.80,
  "horizontalDecay": 0.960, "verticalDecay": 0.940, "lightRange": 24
}

// scout_sub — fast, nimble, fragile
"properties": {
  "engineSpeed": 0.045, "verticalSpeed": 0.030, "yawSpeed": 3.5, "pitchSpeed": 3.0,
  "acceleration": 1.0, "durability": 1.5, "fuel": 0.8, "friction": 0.012,
  "stabilizer": 0.05, "rollFactor": 8.0, "mass": 2.5,
  "wind": 0.0, "lift": 0.0, "glideFactor": 0.0,
  "waterFriction": 0.95, "groundFriction": 0.85,
  "horizontalDecay": 0.975, "verticalDecay": 0.950, "lightRange": 16
}
```

### Seats, boxes, slots

- `passengerPositions` is a **list indexed by passenger count** — entry *n* holds the
  positions used when *n*+1 riders are aboard. So the Explorer needs four entries (the
  1-, 2-, 3- and 4-rider arrangements) and the Scout one. Positions are model units,
  `+z` forward.
- `boundingBoxes` tiles the hull with small boxes (`width`, `height`, `x`, `y`, `z`); IA
  uses 14 for the biplane. A long hull wants several, or players and squid swim through it.
- Slot layout (GUI pixels; crib the arrangement from `warship.json`):

```json
// explorer_sub
"inventorySlots": [
  {"type": "boiler",  "x": 40,  "y": 40}, {"type": "boiler",  "x": 80,  "y": 40},
  {"type": "upgrade", "x": 32,  "y": 70}, {"type": "upgrade", "x": 54,  "y": 70},
  {"type": "upgrade", "x": 76,  "y": 70}, {"type": "upgrade", "x": 98,  "y": 70},
  {"type": "weapon",  "x": 124, "y": 70}, {"type": "weapon",  "x": 124, "y": 92},
  {"type": "dye",     "x": 150, "y": 70}, {"type": "booster", "x": 150, "y": 92},
  {"type": "inventory", "x": 8, "y": 118, "cols": 9, "rows": 3}
]

// scout_sub
"inventorySlots": [
  {"type": "boiler",  "x": 80,  "y": 40},
  {"type": "upgrade", "x": 54,  "y": 70}, {"type": "upgrade", "x": 76, "y": 70},
  {"type": "weapon",  "x": 102, "y": 70}, {"type": "dye",     "x": 128, "y": 70},
  {"type": "inventory", "x": 8, "y": 100, "cols": 9, "rows": 1}
]
```

Available slot types, from `VehicleInventoryDescription`: `inventory`, `boiler`, `weapon`,
`upgrade`, `booster`, `banner`, `dye`, `ingredient`. `registerSlotType(...)` is public if we
ever want a sub-specific one (a `ballast` slot, say) — but prefer the stock types, since
those are what MoMP-style addons and any future sibling mod speak.

---

## 7. Storage

Covered by the `inventory` slots above: 27 for the Explorer, 9 for the Scout, saved into
the vehicle item when it is picked up (IA's `dropInventory` config flips that to dropping
on break). Nothing to write.

---

## 8. Headlights ("bright head lights for deep water exploration")

IA has no light system; VW's headlights are Luminance, and **Luminance 1.1.0 is already
loose in the pack**, so this is additive and cheap. Two levels:

1. **Data only** — ship `assets/submersibles/luminance/entities.json`:
   ```json
   { "submersibles:explorer_sub": { "luminance": 14, "underwater": true } }
   ```
   `LightData` reads `luminance/entities.json` from every namespace. This glows the whole
   hull. `underwater` must be `true` or the light is suppressed in water (that flag exists
   precisely because `minecraft:blaze` sets it `false`). Five minutes' work — but it is a
   lantern, not a headlight, and it cannot be switched off.
2. **Directional beams (what we want)** — client-side, in `SubmersiblesClient`:
   ```java
   Luminance.forEntityInterpolated(EXPLORER_SUB.get(), (sub, partial) ->
       sub.headlightsOn() ? List.of(new Line(x0, y0, z0, x1, y1, z1, 15)) : List.of());
   ```
   `Luminance.forEntity` / `forEntityInterpolated` / `forItem` are the whole public API
   (`com.chunkworks.luminance.api.Luminance`); the sources are `Point`, `Line` and `Field`.
   VW's own `client/Headlamps` does exactly this: a `Line` per lamp while powered, and a
   `Point` of `clamp(2 * range, 1, 15)` for unpowered marker lamps. Luminance is monochrome
   block light, falloff one level per block, `MAX_SOURCES 64`.
   - Beam length should read `lightRange` through `getProperties().get(LIGHT_RANGE)` so an
     upgrade can extend it.
   - Add a toggle, or don't: IA has no lights keybind (`key.vanillawheels.lights` is VW's),
     so either add our own keybind plus a synced `EntityDataAccessor<Boolean>`, or tie the
     lamps to engine power and skip the state entirely. Engine-powered is simpler and reads
     well — the lights come up with the motor.
   - **Luminance is a soft dependency.** Guard every reference behind
     `ModList.get().isLoaded("luminance")` and keep the calls in their own class so the mod
     still loads without it.

---

## 9. Crafting and repair, Vanilla-Wheels-style

What VW actually does, from `vanillawheels-1.12.0`'s lang and items:

- Part items: **Chassis** (per-vehicle — "%s Chassis" — it is what selects the vehicle),
  **Wheel**, **Engine**, Gas Can.
- A **Mechanic Lift** block with a Build / Paint / Repair panel and tooltipped slots:
  "Chassis — chooses the vehicle to build", "Wheels — one for each wheel on the vehicle",
  "Engine — needed only by powered vehicles", "Dye — repaints the vehicle on the deck". It
  wants "a clear, level five by six with a block of headroom".
- Repair: right-click the vehicle ("Broken — right-click it to repair it, or use a Mechanic
  Lift"), costing hunger ("Too hungry to work on it"), reported as "%s%% repaired"; or at
  the lift against materials ("Add materials", with a cost readout).
- Condition is a percentage on the item ("Condition: %s%%").

The IA-side plan that lands the same feel:

**Crafting.** IA's own parts are `immersive_aircraft:hull`, `engine`, `propeller`, `sail`
and `boiler`, and MoMP's recipes build a plane from an existing plane plus parts. We mirror
that with one sub-specific part, so the subs feel built rather than bought:

```
Pressure Hull  (submersibles:pressure_hull)  = IA hull + 4 iron blocks + 2 glass + prismarine
Explorer Sub   = 2 Pressure Hull, 1 engine, 2 propeller, 1 boiler, 2 iron blocks   (shaped)
Scout Sub      = 1 Pressure Hull, 1 engine, 1 propeller, 1 copper block            (shaped)
```

All plain `minecraft:crafting_shaped` in `data/submersibles/recipe/` — IA's and MoMP's own
vehicle recipes are nothing fancier. Add `data/submersibles/advancements/` entries the way
both mods do, so the recipes unlock on the first part crafted.

**Repair.** IA gives us the gesture already: damaged vehicle plus right-click (sneak, if
`requireShiftForRepair`) calls `repair(repairSpeed)` and
`causeFoodExhaustion(repairExhaustion)`, prints "%s%% repaired!", plays the clank and
throws particles. `hull_reinforcement` raises the ceiling. Nothing to write for parity with
VW's hand repair.

**No workshop block.** Decided 2026-10-07: workbench recipes only, no Mechanic-Lift
equivalent, no station to build or repair at. That drops a block, a block entity, a menu, a
screen and a model from the scope, and nothing else in the design depended on them. The
three things the lift would have done are covered without it:

- *Build* — the shaped recipes above, at any crafting table.
- *Paint* — the `dye` slot already in each sub's inventory screen (stock IA behaviour).
- *Repair* — IA's right-click repair, costing hunger, with `hull_reinforcement` raising
  the ceiling.

---

## 10. Models

Two new `.bbmodel`s in `assets/submersibles/objects/`. The conventions from the existing
vehicle work (`Dev mods\trailblazer`, `farmpickup`, `trailer`, and the bbmodel generator
notes) carry over for scale, naming and the pure-Python generate-then-preview loop.
Differences to respect for IA:

- `+z` is forward (same as VW after its 180-degree fix) — but verify on the first render,
  since IA reads the bbmodel with its own loader.
- Name the dyeable bones `dyed_body` / `dyed_body_highlights` to get MoMP's dye treatment;
  the renderer registers per-bone handlers by name.
- Model around the data: the seat positions, weapon mounts, bounding boxes and headlight
  line endpoints in the JSON are all read off the mesh, and that derivation is the fiddly
  part (it was for every VW vehicle too).
- Propeller and rudder bones get animated from `setAnimationVariables(float)` on the
  entity; `EngineVehicle` already exposes `engineRotation` and `enginePower` to drive them.

Shapes that fit the brief: Explorer = a fat three-window research sub, twin floods on a
chin pod, a visible hold hatch, two stern props. Scout = a one-man wet-bike sub, bubble
canopy, single prop, one spotlight.

---

## 11. Build and validation

1. Scaffold from `cavestalker`; add IA (and Luminance) as compile-only file dependencies.
2. Entity, item and renderer registration copied from MoMP's shape
   (`Registration.register(...)`, `DyeableAircraftItem` with an `AircraftConstructor`).
3. Vehicle JSONs first, with placeholder cube models — the slots, upgrades and GUI can all
   be proven before the real meshes exist.
4. `./gradlew build`, then `./gradlew runServer` with `run/eula.txt` as the headless pass
   (it catches bad recipes, advancements and JSON). Note that the aircraft and upgrade
   files are *data*: a malformed one logs `Parsing error on aircraft {}` and leaves the
   vehicle with an empty definition rather than crashing. **Grep the server log for that
   string — it is the quiet failure mode of this whole design.**
5. In-game checklist, in order: upgrade items accept into the slots -> `hull_reinforcement`
   visibly raises durability -> `nether_engine` visibly raises speed -> weapons fire at
   depth -> headlights light -> storage survives pickup -> crew do not drown -> repair
   click works.
6. **Do not install the jar into The Boys, or any instance.** Finishing means a built jar in
   `build/libs/`, a headless pass, and this document updated. nfx installs.

---

## 12. Open questions for nfx

1. **Names.** `submersibles` / Explorer / Scout are placeholders — and should this read as
   a sibling to MoMP ("Man of Many Subs") or stand on its own?
2. **Crew air.** Should the cabin keep riders breathing? (Recommend yes for the 4-seater;
   maybe a short reserve for the Scout.)
3. **Depth.** Any crush depth or pressure damage, or is deep water simply dark and safe?
   (Recommend: none in v1 — it adds a stat, a damage type and a HUD warning.)
4. ~~Dry Dock block~~ — **settled: no workshop block at all, workbench recipes only.**
5. **Lights:** always-on with the engine, or a toggle keybind?
6. ~~Weapons underwater~~ — **settled: guns must work at depth.** IA's cannon fires
   underwater but only ~19 blocks before it despawns, and the crossbow and bomb bay are
   effectively dead down there, so v1 ships our own Torpedo weapon with no water decay
   (section 5). Remaining sub-question: how many torpedo mounts each sub gets, and whether
   the Explorer's are fixed-forward or `ROTATING`.

---

## 13. Traps collected while reading the jars

- `worksUnderWater()` defaults to **false** on `EngineVehicle`; without the override the
  engine never runs in water.
- Misspelled stat keys in a vehicle JSON fail silently (upstream ships two of them).
- Luminance suppresses a light underwater unless the entry sets `"underwater": true`.
- `passengerPositions` is indexed by passenger count, not by seat.
- A vehicle's data file is found by its **entity type id**, so the file name must match the
  registered path exactly.
- Luminance and MoMP are both soft: load-guard anything that touches them.
- `getProperties().get(stat)` multiplies upgrades in; `getAdditive(stat)` adds them. Using
  the wrong one makes upgrades either do nothing or do far too much.
