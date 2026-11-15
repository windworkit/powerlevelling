# Cline task list — Power Levelling

Work for Cline (in VS Code) while Claude is out of usage. Each task is small, self-contained, and stays away from what Claude is building next.

## Ground rules for Cline (read first)

1. **Log every file you touch** in `CLINE_CHANGES.md` (project root): the date, the task number, and the full path of each created or modified file. Claude reads this log to pull your work in before it changes anything. This is the most important rule.
2. **Text encoding:** save every file as **UTF-8 without BOM**. `src/main/resources/assets/powerlevelling/lang/en_us.json` has special characters (· — →). Never re-save it in another encoding, or they turn into junk like `Â·`. Add new keys only; don't reformat the file. **After editing ANY .json file, validate it** (`python -m json.tool <file>` must succeed). The build does NOT check lang files: a stray character breaks every translation in-game even though the build passes (this happened once with `";,`).
3. **Don't touch these.** Claude owns them right now:
   - `character/` (all of it)
   - `client/screen/CharacterScreen.java`
   - `item/`, `registry/ModItems.java`, `data/powerlevelling/recipe/`
   - `client/render/` (hair, tail, eye and orb rendering)
   - `network/` payload codecs
   - **Coming next for Claude (also off-limits):** `combat/`, `entity/`, `client/anim/`, `mixin/`, `client/input/`, `ki/KiSkills.java` (animations + melee combat engine)
4. **Build after each task:** `tools\build.bat`, then check `tools\logs\build.log` for `BUILD EXIT CODE: 0`. If it fails, fix it or undo it before moving on.
5. Keep changes small. One task at a time. Don't refactor code that isn't part of the task.
6. Design reference: Claude's design doc is in the claude.ai Project (`design/update1_design_doc.md`). The JSON schema for ki attacks is in `ki/KiAttackParts.java` (look at `ki_blast.json` for an example).

---

## Tasks (do in order; each one is optional)

**Done:** T1–T6 (checked by Claude on 11/14). Next up: T7.

### T1 — Real sounds for the ModSounds events (easy)
- Create `src/main/resources/assets/powerlevelling/sounds.json` for the 5 events in `registry/ModSounds.java` (`ki_charge`, `ki_blast_fire`, `ki_impact`, `stance_enter`, `stance_exit`).
- Point each one at a **vanilla** sound so no audio files are needed, e.g. `{"sounds":[{"name":"minecraft:entity.blaze.shoot","type":"event"}]}`. Pick sensible ones (stance enter: armor equip; exit: something softer).
- Add subtitle lang keys `subtitles.powerlevelling.<name>`.
- Play `STANCE_ENTER` / `STANCE_EXIT` when stance toggles on the server (find the stance toggle in `combat/CombatInputHandler.java`). Play at the player's position with `SoundSource.PLAYERS`, volume about 0.6.
- Test: toggle stance with R and hear it.

### T2 — Three more ki attacks (data only, easy)
Add JSON files next to `ki_blast.json` in `src/main/resources/data/powerlevelling/powerlevelling/ki_attack/`, using only the fields that file uses, plus a lang name `attack.powerlevelling.<id>` for each:
- `heavy_blast`: slow (speed 1.2), big (radius 0.6), hits hard (damage 12), costs 60 ki, windup 10, cooldown 30.
- `piercing_shot`: thin (radius 0.12), very fast (speed 3.5), damage 5, cost 25, windup 6, cooldown 12, long range 64.
- `scatter_charge`: a `powerlevelling:charged_blast` (copy `charged_blast.json`), but cheaper and smaller.
- Test with `/pl loadout set 3 powerlevelling:<id>`.
- Balance target: none of them should be strictly better than `ki_blast` at its job.

### T3 — Clearer commands (medium)
The user finds `/pl` commands confusing. In `command/ModCommands.java` only:
- Make `/pl help` short. Show the 6–8 most useful commands first ("Getting started"), with "admin/debug" commands under a separate `/pl help admin`.
- Rename nothing. Add friendlier aliases only:
  - `/pl stats` = open/print stats info
  - `/pl me` = `character info`
  - `/pl give exp <n>` = `addexp`
- Make every error message say what to type instead.
- Test: every old command still works exactly as before.

### T4 — Tooltips and item polish (easy)
- Make sure every item has a 2-line tooltip like the Rebirth Orb (gold line, gray line).
- Add lore-style flavor text for the Rebirth Orb.
- Stay out of `item/` for any items other than `RebirthOrbItem.java`. Claude is adding 3 new items.

### T5 — README for players (easy)
Rewrite `README.md` (project root) for players and server owners:
- what the mod is
- controls: R stance, C charge, 1–4 skills, K stats/character, F guard (coming), Left Alt dash (coming)
- key commands
- config file locations: `run/config/powerlevelling-client.toml`, and the server config
- how to add custom ki attacks, races and hairstyles via datapacks and resource packs (point to the JSON examples)

Keep it accurate to what exists now. Mark unfinished things as "coming soon".

### T6 — Config comments (easy)
- In `config/ServerConfig.java` and `config/ClientConfig.java`, make every `.comment(...)` a clear one-line explanation with its default and range.
- No behavior changes.

### T7 — Power Levelling creative tab (easy)
- Add a dedicated creative tab "Power Levelling" (icon: the Rebirth Orb) in a new file `registry/ModCreativeTabs.java`, registered via `ModRegistries` (`DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID)`).
- Put all 4 items in it. Keep them in Tools & Utilities too.
- Lang key `itemGroup.powerlevelling`.

### T8 — In-game config screen (easy)
- Register NeoForge's built-in config screen so the Mods menu has a working **Config** button.
- In the client-only setup, use `modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)`. Put it in a client-only class, or guard it with `FMLEnvironment.dist`.
- Test: Mods → Power Levelling → Config opens and edits the client config.

### T9 — Advancements (data only, easy)
Add advancements under `src/main/resources/data/powerlevelling/advancement/` (1.21.1 folder name is singular). Use only vanilla triggers:
- a root advancement (icon: Rebirth Orb, background: any vanilla texture)
- "Fresh Start": obtain a Rebirth Orb (`minecraft:inventory_changed`)
- "New Look": obtain a Style Capsule
- "Attuned": obtain an Attunement Crystal
- "Reborn": obtain a Reincarnation Orb (frame: challenge)

Add lang keys for every title and description.

### T10 — Race icons (art, easy–medium)
- Each race JSON (`data/powerlevelling/powerlevelling/race/*.json`) has an `icon` path like `powerlevelling:textures/gui/race/saiyan.png`. Create 24×24 pixel-art icons at those paths for all 7 races.
- Make them simple, original symbols, not character portraits or logos:
  - Saiyan: a tail curl
  - Half-Saiyan: half tail, half flame
  - Human: a fist
  - Namekian: an antenna
  - Frost Demon: a horn
  - Majin: a tendril
  - Android: a gear
- Transparent background, 2–3 colors each. The creation screen already shows them if the file exists.

### T11 — Dungeon loot (data only, medium)
Add rare chances to find a Style Capsule or Attunement Crystal in chests:
1. Create a loot table `data/powerlevelling/loot_table/chests/rare_capsules.json` (folder `loot_table` is singular in 1.21.1): 1 roll, 85% empty, 10% Style Capsule, 5% Attunement Crystal.
2. Inject it with a NeoForge global loot modifier of type `neoforge:add_table`:
   - `data/powerlevelling/loot_modifiers/add_capsules.json`
   - registered in `data/neoforge/loot_modifiers/global_loot_modifiers.json` with `"replace": false`
   - conditions limit it to these chests: `minecraft:chests/simple_dungeon`, `minecraft:chests/abandoned_mineshaft`, `minecraft:chests/stronghold_corridor`
3. Never add the Reincarnation Orb to loot.
4. Test with `/loot give @s loot powerlevelling:chests/rare_capsules` a few times.

### T12 — Spanish translation (easy, mechanical)
- Create `src/main/resources/assets/powerlevelling/lang/es_es.json` with every key from `en_us.json` translated.
- Keep `%s` placeholders exactly, keep the key names, UTF-8 without BOM, valid JSON.
- Don't translate ids like `powerlevelling:saiyan`.

### T13 — Antenna & horn placeholder art for locked races (medium, art)
- `cosmetic_part/antenna_short.json`, `antenna_long.json` and `horn_small.json` point at geo and texture files that don't exist yet.
- Make them by following the conventions in `tools/gen_hair_art.py`: Bedrock-style `.geo.json` under `assets/powerlevelling/powerlevelling/geo/...`, plus grayscale PNG textures.
- Check them with `python3 tools/preview_cosmetics.py --validate`. It must report no warnings for these three.
- Keep the shapes simple and original.

### T14 — Wire the custom sound events into ki attacks (easy; do after T1)
- The ki attack JSONs reference vanilla sounds (`fire_sound`, `impact_sound`). Switch them to the mod's events, `powerlevelling:ki_blast_fire` and `powerlevelling:ki_impact`, so server owners can remap them in `sounds.json`.
- JSON edits only; don't change Java.
- Check every attack still makes sound in game.

---

When you're back on Claude, say "Cline did tasks X, Y". Claude will read `CLINE_CHANGES.md`, pull those files in, and check them.
