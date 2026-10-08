# Submersibles

A submarine protocol for NeoForge 1.21.1, layered on
[Vanilla Wheels](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels). A submarine
is a Vanilla Wheels vehicle (its look, seats, chests, fuel, paint, headlights, keys, condition,
repair, and the Mechanic Lift) that dives. This mod owns:
- the diving;
- the crew's air;
- the fit-out that takes Immersive Aircraft's upgrades;
- the Torpedo Tube.

Submarine mods ship data and assets only.

**1.0.0** is released (pack 1.78.0). It needs Vanilla Wheels 1.13.0 (its D-0031: the hull, the
crash judging and the up/down/get-out keys a body that moves in three dimensions shares).

It ships two things of its own: the **Torpedo Tube** and the **Torpedo**.

## Diving

| Key (default) | Does |
|---|---|
| W / S | ahead / astern; let go and the water slows it |
| A / D | the rudder: turn (slowly in place) |
| Space | climb; at the surface it floats, Space cannot lift it out |
| Left Shift | dive (never gets you out) |
| R | get out, at the hatch |
| Right mouse (Use) | fire a torpedo, while a Torpedo Tube is fitted |
| H | headlights: off, on, auto (Vanilla Wheels') |

Space, Left Shift and R are Vanilla Wheels' *Up*, *Down* and *Get out* (rebound under Vanilla
Wheels in Controls).

- **Depth.** Under water and powered, let go of Space and Shift and it holds its depth. It is
  trimmed to neutral: holding depth burns no fuel, and nor does sitting parked.
- **Fuel** burns only while you drive the propeller, the rudder or the planes; Vanilla Wheels'
  gas can fills it.
- **Dead in the water.** With no fuel, or worn to nothing, it is buoyant and rises to float at the
  surface.
- **The surface.** It floats with its `draft` of its height under water and runs along the top.
  Shift takes it down.
- **Beached** (no water under it), it makes no way. Punch it six times to pack it (Vanilla
  Wheels'), and set it down in water.
- **Setting down:** right-click open water within reach with a packed submarine and it floats
  there. Aimed at a block, it is set on that block's face; one set on the seabed rises.
- **The crew breathe aboard**, however deep. Nobody aboard is hurt by diving.
- **A wreck** under water is not destroyed: it rises, crew aboard, and becomes a packed wreck at
  the surface. Set down again broken, it floats where it is put until it is repaired (D-0004).
- **Crashes wear it:** hitting rock or the seabed faster than a slow bump, at the profile's
  `crash` speeds (by default 0.15 blocks a tick is safe, 0.8 a wreck), divided by its durability.
  It slides along the seabed or under an overhang rather than sticking (its hull is met axis by
  axis).
- **Currents** push it, unless a gyroscope is fitted.
- No crush depth. Lamps light the dark: Vanilla Wheels' headlamps, through Luminance, under water
  too.

## The fit-out

Crouch and right-click the hull, anywhere a chest or a door does not claim, to open it:

- **Upgrade slots** (the profile's `upgrades`) take Immersive Aircraft's upgrades, and any item a
  data pack adds to `#submersibles:upgrades`. They change it by that mod's own rule and stat names
  (D-0002):

  | Upgrade | In a submarine |
  |---|---|
  | Hull Reinforcement | half the wear |
  | Nether Engine | +40% thrust, 30% more fuel |
  | Steel Boiler | +25% thrust, 50% more fuel |
  | Sturdy Pipes | +10% thrust |
  | Eco Engine | −20% thrust, a quarter of the fuel |
  | Industrial Gears | 20% less fuel |
  | Enhanced Propeller | about a third faster |
  | Improved Landing Gear | the propeller spools up faster |
  | Gyroscope | currents no longer push it; it levels faster |
  | Gyroscope with Dials / HUD | the same, and depth, heading and speed at the top left |

- **Weapon slots**, one per muzzle the profile names, take the Torpedo Tube.
- **The paint slot** takes a dye, which paints the hull.
- **The readout** under the rows: top speed, fuel burnt and wear taken, in percent of the bare
  hull.

The fit-out goes into the packed item with everything else.

## Torpedoes

The Torpedo Tube is the weapon (Immersive Aircraft's cannot mount on a Vanilla Wheels vehicle).
- **Crafting:**
  - a tube from three iron, a dispenser, redstone and a piston, three iron;
  - two torpedoes from TNT, an iron ingot, a copper ingot and gunpowder.
- **Loading:** put torpedoes in the hold.
- **Firing:** the tubes fire in turn on Use, each reloading for two seconds. A creative pilot needs
  no torpedo.
- **The run:** 1.2 blocks a tick under water for 96 blocks. Out of the water it falls.
- **On a hit:** it explodes with a creeper's power and breaks no block. It never hits its own
  submarine.

## For submarine mods: the contract

A submarine is two files of the same id, beside its mesh, texture and lang:

- `data/<ns>/vanillawheels/vehicle/<name>.json`: Vanilla Wheels' profile. Seats (the driver
  first), `engine`, `fuel`, `storage` (the hold), `headlights`, `paint`, `glass`, `camera`.
  `wheels` with `"drawn": false` are where the keel rests when beached.
- `data/<ns>/submersibles/submarine/<name>.json`, how it dives:

```jsonc
{
  "properties": {                  // Immersive Aircraft's names, so its upgrades apply by name
    "engineSpeed": 0.018,          // thrust a tick at full power, blocks a tick (required)
    "friction": 0.02,              // the propeller's slip: a share of the speed lost a tick (required)
    "verticalSpeed": 0.12,         // climb and dive, blocks a tick
    "yawSpeed": 2.5,               // degrees a tick
    "acceleration": 1.0,           // the spool's speed
    "fuel": 1.0,                   // ticks of fuel a working tick burns
    "durability": 1.0,             // wear is divided by it
    "stabilizer": 0.1,             // the share of the way its tilt eases a tick
    "wind": 1.0                    // the share of a current that pushes it
  },
  "hull_drag": 0.04,               // the hull's own drag: top speed = engineSpeed * (1 - d) / d, d = friction + hull_drag
  "reverse": 0.333, "spool_ticks": 40, "rise": 0.06, "draft": 0.7, "tilt": 12,
  "hull": [{"from": [-12, 0, -36], "to": [12, 24, 36]}],    // mesh units: what meets the world
  "propellers": [{"part": {"group": "propeller"}, "pivot": [0, 12, -38], "axis": [0, 0, 1], "speed": 1.0}],
  "upgrades": 4,                   // upgrade slots, 0..9
  "weapons": [[-6, 6, 38], [6, 6, 38]],   // a muzzle per weapon slot, mesh units
  "hatch": [0, 36, 2],             // where riders get out
  "crash": {"safe": 0.15, "touchdown_safe": 0.15, "wreck": 0.8},
  "air": true                      // the crew breathe aboard
}
```

Every bound is checked at load, and a bad file is refused naming its field.

## Layout

- `src/domain` (the JDK and Vanilla Wheels' pure layer, plain JUnit): `Dive` (the step,
  `struck`, `submersion`), `Hullform`, `DiveInput`, `Fitting` (Immersive Aircraft's rule),
  `Upgrades` (its files), `TorpedoRun`.
- `src/main`:
  - `api` (`Submersibles`, `SubmarineProfile`);
  - `Submarine` (the entity, extending Vanilla Wheels' `Vehicle`), `Torpedo`, `UpgradeTable`
    (the reload listener), `FitOutMenu`, `Launching` (setting down on water),
    `SubmersiblesContent`;
  - `net/Payloads` (controls, fire);
  - `client` (`DiveControls`, `SubmarineRenderer`, `TorpedoRenderer`, `FitOutScreen`,
    `Instruments`).
- `src/gametest`: the box submarine, stand-in upgrades on vanilla items (in Immersive Aircraft's
  format), 21 gametests, and the booth. A mod of its own, never shipped.

## Building and testing

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test               # the pure layer
./gradlew check              # plus the gametests and the booth (needs a display; -PskipBooth)
```

Vanilla Wheels 1.13.0 comes from Maven Local (`./gradlew publishToMavenLocal` in its repo first).
Art and test assets: `uv run --no-project python devtools/art/build.py`.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
