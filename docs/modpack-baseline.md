# ExtractCraft Modpack Baseline

Minecraft: 1.21.1
NeoForge: 21.1.230
Branch: feature/modpack-baseline

## Purpose

This document tracks the local client-side modpack baseline for ExtractCraft development.

The goal is to build a stable, high-FPS, shader-capable Minecraft client for testing ExtractCraft maps and gameplay without committing third-party mod `.jar` files into the repo.

External mod `.jar` files are installed locally in:

```text
C:\Users\chase\ExtractCraft\run\mods
```

Shader packs are installed locally in:

```text
C:\Users\chase\ExtractCraft\run\shaderpacks
```

The `run/` folder is intentionally ignored by Git.

---

## Current client performance / visuals stack

### Sodium

* Loader/platform: NeoForge
* Minecraft version: 1.21.1
* Installed locally in: `run/mods`
* Purpose: FPS/performance optimization
* Status: launches successfully

Notes:

* Sodium is the first performance baseline mod.
* `/startraid rocket_platform` still works with Sodium installed.

---

### Iris

* Loader/platform: NeoForge
* Minecraft version: 1.21.1
* Installed locally in: `run/mods`
* File used: `iris-neoforge-1.8.12+mc1.21.1.jar`
* Purpose: shader support
* Status: launches successfully with Sodium

Notes:

* In the dev client/mod list, Iris may display as:

  * `1.8.12-snapshot+mc1.21.1-local (development environment)`
* This is acceptable as long as the actual jar is the NeoForge 1.21.1 Iris build and shader options appear in-game.

---

### Complementary Reimagined

* Shader pack: Complementary Reimagined
* Version: r5.8.1
* Shader loader: Iris
* Game version support: 1.21.x
* Installed locally in: `run/shaderpacks`
* Purpose: shader pack / visual polish
* Status: added for testing

Installation:

* Downloaded through the manual download option.
* Do not use the Complementary installer for this dev setup.
* Do not unzip the shaderpack.
* Place the downloaded `.zip` directly into:

```text
C:\Users\chase\ExtractCraft\run\shaderpacks
```

In-game:

* Go to `Options → Video Settings → Shader Packs`
* Select Complementary Reimagined.
* Start with Medium or High preset before trying Ultra.

Recommended initial shader settings:

* Preset: Medium or High
* Render Distance: 12–16
* Simulation Distance: 6–8
* Entity Distance: 75% or lower
* Clouds: Off
* Particles: Decreased
* VSync: Off unless tearing is noticeable
* Shadow Resolution: 1024 or 2048
* Shadow Distance: conservative/lower first
* Volumetrics: low/off if FPS dips

---

## Attempted but removed / deferred

### Continuity

* Purpose: connected textures
* Status: removed/deferred for now

Reason:

* The installed Continuity jar required:

  * `fabric-api`
  * `connector`
* On NeoForge, this means adding a Fabric compatibility layer such as Sinytra Connector and Forgified Fabric API.
* Decision: keep the baseline lean for now with Sodium + Iris + Complementary Reimagined.
* Connected textures can be revisited later if needed.

Potential future options:

* Add Sinytra Connector + Forgified Fabric API + Continuity.
* Or try a native NeoForge continuity-style fork if compatible with the final modpack.

---

## Current ExtractCraft gameplay baseline

The following ExtractCraft systems should continue working with the visual/performance stack installed.

### Raid maps

Available commands:

```text
/startraid
/startraid test
/startraid compact
/startraid city_block
/startraid rocket_platform
/testraidextract
```

Map source types:

* `GENERATED_PLATFORM`

  * `test`
  * `compact`
* `STRUCTURE_TEMPLATE`

  * `city_block`
* `EXISTING_WORLD_AREA`

  * `rocket_platform`

---

### Rocket Platform marker-authored raid loop

The `rocket_platform` map is an existing-world-area map. It should be tested by opening the rocket platform world directly in the dev client, then running:

```text
/startraid rocket_platform
```

Marker authoring commands:

```text
/raidmarkers scan rocket_platform
/raidmarkers save rocket_platform
/raidmarkers render rocket_platform
/raidmarkers clear rocket_platform
```

Current marker runtime behavior:

* `player_spawn_marker`

  * Controls rocket platform raid spawn.
* `extraction_marker`

  * Creates 3x3x3 extraction zones.
* `mob_spawn_marker`

  * Spawns helmeted tracked zombies.
  * Zombies spawn one block above marker blocks.
  * Zombies wear chainmail helmets and do not visually burn in daylight.
  * Helmet drop chance is set to 0.
* `loot_marker`

  * Becomes a vanilla barrel with common loot.
* `rare_loot_marker`

  * Becomes a vanilla barrel with rare loot.

Marker blocks:

* Are available in the ExtractCraft creative tab.
* Have no collision.
* Break instantly or near-instantly.
* Emit light level 12.
* Are currently solid-colored placeholder blocks.
* Transparency is deferred.

Saved marker JSON path:

```text
C:\Users\chase\ExtractCraft\run\extractcraft\raid_markers\rocket_platform_markers.json
```

---

## Testing checklist after adding client mods

After installing or updating a client mod, run:

```powershell
cd C:\Users\chase\ExtractCraft
.\gradlew.bat runClient
```

Then test:

```text
/startraid rocket_platform
```

Confirm:

* Game launches.
* Shaders/rendering work.
* Marker blocks render.
* Player spawns from saved marker.
* Extraction works from saved marker.
* Mob markers spawn helmeted zombies.
* Loot/rare loot markers become barrels.
* Extracting keeps loot.
* Death/timer failure restores starting inventory.
* `/testraidextract` cleans up mobs.
* `/raidmarkers scan/save/render/clear` still work.

Also sanity check:

```text
/startraid test
/startraid compact
/startraid city_block
```

---

## Git / repository policy

Do not commit local downloaded mod jars from:

```text
run/mods
```

Do not commit shaderpacks from:

```text
run/shaderpacks
```

Track the modpack setup through this document instead.

The `run/` folder should remain ignored by Git.

Commit updates to this documentation when:

* a new client mod is added,
* a mod is removed/deferred,
* exact versions change,
* shader setup changes,
* a compatibility decision is made.

---

## Current known good baseline

Known working local setup:

```text
Minecraft 1.21.1
NeoForge 21.1.230
Sodium NeoForge 1.21.1
Iris NeoForge 1.21.1
Complementary Reimagined r5.8.1
```

Continuity is currently deferred.
