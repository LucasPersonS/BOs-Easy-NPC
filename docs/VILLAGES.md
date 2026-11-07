# Paravexia villages (Easy NPC fork)

Server-side village runtime in `core/Common/src/main/java/de/markusbordihn/easynpc/paravexia/village`.
A village is one JSON spec (format_version 1) plus the entities spawned for it. The runtime keeps
villages cheap: NPCs sleep when no player is near, residents only walk when the village scheduler
lets them, and a budget guard degrades villages when Easy NPC tick time goes over the budget.

## Village JSON (format_version 1)

```json
{
  "format_version": 1,
  "id": "aldeia_norte",
  "name": "Aldeia do Norte",
  "dimension": "minecraft:overworld",
  "center": {"x": 100, "y": 64, "z": -200},
  "radius": 40,
  "wake_radius": 72,
  "lore": "Optional village lore, stored only.",
  "pois": [
    {"id": "casa_ana", "type": "home", "pos": {"x": 110, "y": 64, "z": -190}},
    {"id": "forja", "type": "work", "pos": {"x": 92, "y": 65, "z": -210}},
    {"id": "praca", "type": "square", "pos": {"x": 100, "y": 64, "z": -200}}
  ],
  "npcs": [
    {
      "key": "ana",
      "entity_type": "easy_npc:humanoid",
      "name": "Ana, a ferreira",
      "profile": "resident",
      "home": "casa_ana",
      "schedule": [
        {"start": 1000, "poi": "forja"},
        {"start": 9000, "poi": "praca"},
        {"start": 13000, "poi": "casa_ana"}
      ],
      "dialogue_library": "paravexia:aldeia_norte/ana",
      "lore": "Optional NPC lore, stored only."
    },
    {"key": "joao", "entity_type": "easy_npc:humanoid", "name": "João", "profile": "functional", "home": "praca"}
  ]
}
```

| Field | Rules |
|---|---|
| `format_version` | must be `1` |
| `id`, POI `id`, NPC `key` | `[a-z0-9_-]{1,48}`; NPC keys are stable: they map to the spawned entity, so re-publishing never duplicates NPCs |
| `dimension` | optional, default `minecraft:overworld`; must exist on the server |
| `center`, `radius` | radius 4 – `maxVillageRadius` (48). POIs must be inside the radius and within ±32 blocks of `center.y` (path finding range of the move-home goal is 48) |
| `wake_radius` | optional, default `radius + 32`; between radius and `maxWakeRadius` (160) |
| `pois[].type` | `home`, `work`, `square`, `bench`, `custom` (display only, default `custom`) |
| `npcs[].entity_type` | an `easy_npc:*` entity type |
| `npcs[].profile` | `extra`, `resident`, `functional`, `guard` |
| `npcs[].home` | POI id; the NPC spawns there and returns there without schedule |
| `npcs[].schedule` | residents only; ≤ 12 entries, `start` = day time 0–23999, unique. The latest entry that started applies; before the first one the last entry of the previous day applies |
| `dialogue_library` | optional Hades dialogue library id (`config/easy_npc/dialogues`), linked like `/easy_npc dialog library apply`. A library that is not loaded yet only gives a warning; NPCs link to it on chunk load, when the village wakes and right after `/easy_npc dialog library reload` |
| `skin` | optional. `namespace:textures/....png` = texture from a jar/resource pack (e.g. `easy_npc:textures/entity/paravexia/ancoradouro/odete.png`, rendered for `humanoid` and `humanoid_slim`); a Minecraft player name = that player's skin (resolved off the server thread). Anything else is ignored with a warning. Applied on spawn, on chunk load and when a re-published spec changes it; a missing `skin` leaves the NPC's current skin alone |
| `name`, `lore` | name ≤ 64 chars, lore ≤ 4000 chars, lore stored only |
| any `hash` field | ignored on input; the server computes SHA-1 of the canonical JSON |

Caps (server-side, `village.cfg`): `maxNpcsPerVillage` 40, `maxVillageNpcs` 600 (all villages).

Applying the same spec again is a no-op (same hash), apart from re-queueing NPCs that were never spawned.
NPC keys removed from a spec delete their spawned NPCs (`EasyNPCEntityHandler.delete`); NPCs adopted with
`/easy_npc village npc add` (key = their UUID) are only released.

## Profiles

| Profile | Objectives | Attributes |
|---|---|---|
| `extra` | only look reset + look at player | immovable, not pushable, does not push |
| `resident` | look reset + look at player + move back to home (driven by the scheduler) | not pushable, does not push |
| `functional` | look reset + look at player (dialogue/trades kept) | immovable, not pushable, does not push |
| `guard` | untouched | untouched |

For managed profiles the runtime removes `LOOK_AT_MOB`, `LOOK_AT_ANIMAL`, `LOOK_RANDOM_AROUND`, all random
stroll objectives and the village wander objectives. Changes are only made when a value differs, so
re-applying a profile neither rebuilds goals nor marks NPC data as changed. `residentIdleStroll=true`
adds `RANDOM_STROLL_AROUND_HOME` to residents (costs movement packets).

## Runtime

- **Standing spots:** NPCs never stand on the raw POI block. Every NPC gets a slot at its POI
  (immovable profiles first, then residents, in spec order; slot 0 is the POI, the next ones ring
  around it 2 blocks apart) and each slot is resolved to the nearest block where the NPC fits:
  feet and head free of colliding blocks and liquids, solid block below, searched up to 6 blocks
  sideways and up/down (vertical moves count double, so a POI inside a wall resolves to the floor
  next to it, a POI under the floor or in the air to the same column). Only loaded chunks are read.
  Spots handed out are reserved, so NPCs are never placed inside each other. Spawns, resident
  schedule targets and immovable NPCs (placed once per session, also fixing NPCs from older
  versions) all use these spots; they are cached until the spec or a POI changes.
- **Stuck recovery:** with each awake village check (40 ticks), an NPC inside a block is moved to
  the nearest free spot, and a walking resident whose navigation stopped without progress for 3
  checks is moved to its target.

- **Wake/sleep:** every village is checked at most every `checkIntervalTicks` (40), staggered, at most
  `villagesCheckedPerTick` (4) per tick, using `level.players()` distance only. Awake when a non-spectator
  player is within `wake_radius`; asleep after nobody was within `wake_radius + sleepHysteresisBlocks` (16)
  for `sleepDelayTicks` (200).
- **Sleep without saves:** sleeping sets vanilla NoAI plus the entity tag `pvx_village_sleep`; waking only
  clears NoAI when the tag is present and the NPC is not paused by Easy NPC (`PauseManager`). No Easy NPC
  data is touched, so sleep/wake never schedules NPC file saves; a manual or Easy NPC pause is never cleared.
  NPCs loaded later (chunk load) get their profile and sleep state on the next ticks (`joinUpdatesPerTick` 16).
- **Scheduler:** every `schedulerIntervalTicks` (100) per awake village. A resident whose schedule spot changed
  gets a walker slot (limit `min(maxWalkersPerVillage 4, ceil(residents × maxWalkingPercent 25 %))`, 1 at budget
  level ≥ 1) and its home position is set to the spot, which starts Easy NPC's move-back-to-home goal. The slot
  is freed on arrival (`arrivalDistance` 3, horizontal) or after `walkerTimeoutTicks` (1200).
- **Gradual spawn:** at most `spawnPerTick` (2) NPCs per tick; spawns wait (deferred) while the home chunk is
  not entity-ticking and are retried when the village wakes; no chunk is loaded for a spawn.
- **Budget guard (NeoForge):** `EntityTickEvent` Pre/Post time of Easy NPC entities, averaged over
  `budgetWindowTicks` (100). Above `npcBudgetMicros` (4000) the level rises by one per window:
  L1 walkers → 1, L2 villages stay awake only with a player within `radius + budgetNearPlayerBlocks` (24),
  L3 extras of awake villages sleep too. Below `budgetRecoverPercent` (70 %) for `budgetRecoverTicks` (600)
  one level is recovered. Level changes are logged once.

## Commands (`/easy_npc village …`, permission level 2)

`create <id> <radius>` (center = your position) · `remove <village> [delete_npcs]` ·
`poi add <village> <poi> <type>` (your position) · `npc add <village> <npc> <profile> <homePoi>` ·
`npc remove <village> <npc>` · `schedule set <village> <npc> <dayTimeStart> <poi>` ·
`wake|sleep|auto <village>` (force for tests, `auto` follows players again) · `status [village]` ·
`import <file> [here]` (reads `config/easy_npc/villages/<file>.json`, Tab lists the files; `here` moves
the whole preset so its center is your position, in your dimension) · `poi set <village> <poi>` (moves a
POI to your position and places its NPCs again) · `poi list <village>` (every POI with coordinates and
where NPCs actually stand: green ok, red no free spot within 6 blocks, gray not loaded) ·
`respawn <village> <npc|all>` (moves loaded NPCs back to their spots for the current time).

## API for the bridge (`VillageApi`, JDK types only, runs on the server thread)

- `String apply(String json)` → `{"ok", "messages", "queuedSpawns", "updatedNpcs", "removedNpcs", "hash"}`
- `Map<String,Object> applyResult(String json)` / `validate(String json)` (no changes) / `remove(String id, boolean deleteNpcs)`
- `String export(String id)` → spec JSON or `""`
- `String status()` → budget level, Easy NPC µs per tick, queued spawns and per village state, counts by profile, walkers

## Cost (benchmark, 2026-11-07)

Headless NeoForge 21.1.265 server, 2 CPUs, 3 GB heap, 4 mock players inside the village, one village of
200 humanoid NPCs (60 extra, 100 resident with 3-slot schedules, 30 functional, 10 guard), `maxNpcsPerVillage=200`
for the test.

| Scenario | MSPT mean / p95 / p99 / max | Easy NPC per entity tick | Easy NPC per server tick | Packets/s per player |
|---|---|---|---|---|
| empty server (reference) | 1.7 / 2.5 / 4.1 / 7.8 | – | – | 2 |
| awake, residents walking to work | 3.55 / 6.54 / 11.9 / 34.0 | 8.4 µs | 1.68 ms | 57 |
| awake, schedule change (time 6000) | 3.13 / 4.96 / 7.9 / 34.0 | 7.6 µs | 1.51 ms | 38 |
| asleep (players 300 blocks away) | 2.99 / 4.64 / 7.3 / 56.7 (GC) | 4.8 µs | 0.96 ms | 2 |
| awake again | 3.46 / 5.38 / 8.2 / 79.3 (autosave tick) | 8.6 µs | 1.72 ms | 58 |
| same 200 NPCs without village profiles (earlier run): default / stroll | 4.27 / 5.14 mean | 12.0 / 16.8 µs | 2.39 / 3.35 ms | 66 / 522 |

The only ticks above 50 ms were a GC pause (56.7 ms, gc.log) and the world autosave tick (79.3 ms at uptime
≈ 320 s). The budget guard went to level 1 once while the 200 NPCs were being spawned and JIT-compiled, and
recovered by itself 36 s later.
