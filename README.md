# Power Levelling

A Dragon Ball-inspired combat and ki progression mod for Minecraft. **Blocky, polished, skill-based.**

Built for **NeoForge 21.1 (Minecraft 1.21.1)**.

---

## 🎮 What This Mod Adds

- **Character Creation** — Choose your race (Saiyan, Human, Namekian, Frost Demon, Majin, Android, Half-Saiyan), ki nature, and appearance.
- **Progression System** — Level up to the configurable cap (150 by default), earn stat points, and advance four mastery tracks (Martial, Ki Control, Toughness, Mobility).
- **Ki Combat** — Charge ki, fire blasts, charge and hold attacks, and assign skills to 4 loadout slots.
- **Melee Combat** — Light/heavy combos with startup, active and recovery frames, hitstun, knockback and buffered inputs.
- **Dash / Dodge** — A stamina-priced burst in your movement direction with brief invulnerability and a cooldown.
- **Transformations** — Saiyans and Half-Saiyans can hold their transform key in stance to go Super Saiyan (+damage, +speed, constant ki drain).
- **Zenkai Boosts** — Survive near-death and score a kill to trigger a power surge (bonus stats + EXP).
- **Stance Combat** — Enter combat stance (R) to enable melee, ki skills, charging and transformations.

---

## ⌨️ Controls (Defaults)

| Key | Action |
|-----|--------|
| **K** | Open Stats / Character screen |
| **R** | Toggle Combat Stance |
| **C** (hold) | Charge Ki |
| **1–4** | Skill Slots (only in stance; assign with `/pl loadout`) |
| **G** (hold) | Transform / power down (Saiyan races, in stance) |
| **V** | Loadout wheel (not implemented yet) |
| **F** | Guard (coming soon) |
| **Left Alt** | Dash / Dodge (movement direction, costs stamina) |

---

## 🔧 Key Commands

| Command | Description |
|---------|-------------|
| `/pl help` | Quick-start commands |
| `/pl help admin` | Admin/debug commands (op) |
| `/pl stats` | Show your level, stats, mastery, resources |
| `/pl info` | Alias for `/pl stats` |
| `/pl me` | Show your character info |
| `/pl character open` | Open character creation/editor |
| `/pl character tail` | Wrap/unwrap your tail |
| `/pl loadout set <1-4> <attack>` | Assign a ki attack to a slot |
| `/pl loadout clear <1-4>` | Clear a skill slot |
| `/pl give exp <amount>` | Give yourself battle EXP (op) |
| `/pl addexp <amount>` | Give EXP (op) |

---

## ⚙️ Configuration

| File | Location |
|------|----------|
| Client config | `run/config/powerlevelling-client.toml` |
| Server config | `run/config/powerlevelling-server.toml` (or `world/serverconfig/...` on dedicated servers) |

Key server settings: level cap, stat cap, EXP rate, Zenkai cooldown, ki charge rate, transformation damage/speed/ki-drain/hold time, dash stamina/impulse/i-frames/cooldown, size-affects-hitbox, etc.

---

## 📦 Custom Content via Data Packs & Resource Packs

Power Levelling is fully data-driven. Add your own content by creating a data pack (`data/<namespace>/powerlevelling/...`) or resource pack:

| Type | Folder | Example |
|------|--------|---------|
| **Races** | `data/<ns>/powerlevelling/race/` | `human.json`, `saiyan.json` |
| **Ki Natures** | `data/<ns>/powerlevelling/ki_nature/` | `balanced.json`, `ferocious.json` |
| **Cosmetic Parts (hair/tails/horns)** | `data/<ns>/powerlevelling/cosmetic_part/` | `spike_fringe.json`, `saiyan_tail.json` |
| **Ki Attacks** | `data/<ns>/powerlevelling/ki_attack/` | `ki_blast.json`, `charged_blast.json` |

See the mod's built-in JSON files under `src/main/resources/data/powerlevelling/powerlevelling/` for templates.

---

## 🚧 Coming Soon

- Guard system
- Lock-on and the loadout wheel
- Flight mechanics
- Controller support
- Barrier ki attack and the custom attack creator
- Mob scaling and per-target anti-farm EXP

---

## 🛠️ Development

- Requires **JDK 21**
- Build: `.\gradlew.bat build`
- Run client: `.\gradlew.bat runClient`
- Run server: `.\gradlew.bat runServer`

---

*Power Levelling — Train. Fight. Ascend.*
