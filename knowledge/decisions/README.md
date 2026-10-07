---
title: Submersibles — decisions log
type: index
layer: store
tags: [index]
---

# Submersibles — decisions log

**Append-only.** Never edit an entry's rationale; supersede via a new entry with
`supersedes: D-NNNN`. Every entry has a status: `Active` / `Superseded` / `Rejected`.

- [D-0001](D-0001.md): Diving. A submarine is a Vanilla Wheels vehicle (its D-0030, D-0031) whose id is also in `submersibles:submarine`. It is trimmed to hold its depth (no fuel to hold it), buoyant when dead, floats at its draft, and is beached out of water. Its crew breathe aboard; a wreck at depth comes up before it is packed. A packed one aimed at water floats there. No crush depth. The brief's Immersive Aircraft addon was turned down on Rusty's call.

- [D-0002](D-0002.md): The fit-out. Upgrade, weapon and paint slots, opened with a crouching click. Upgrades are read from Immersive Aircraft's own `aircraft_upgrades` files with no link to it, applied by its own stacking rule (penalties multiply, then bonuses add; the stabilizer added), by its stat names; the drag is split so the Enhanced Propeller gives a third more, not four times.

- [D-0003](D-0003.md): Torpedoes. The Torpedo Tube is the weapon (Immersive Aircraft's cannot mount here). A torpedo holds 1.2 b/t in water for 96 blocks, explodes with a creeper's power and breaks no block, and never strikes its own submarine. Tubes fire in turn on Use and reload in 2 s; a creative pilot needs no torpedo.

- [D-0004](D-0004.md): A broken submarine set down from its item floats where it is put, to be repaired; only a wreck made under water is packed when it comes up (Rotorcraft's D-0004, the same fix).
