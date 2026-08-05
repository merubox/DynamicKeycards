# Dynamic Keycards — User Manual

[한국어 버전 (Korean version)](MANUAL_KO.md)

For version 0.1.5. Every interaction is a **right-click**; "sneak" means holding Shift.

---

## 1. Items

All item types appear in the **Dynamic Keycards** creative tab. In survival, keycards and member cards are obtained by registering/duplicating.

### Blank Cards → Keycards (16 colors)
- A **Blank Card** is the craftable base card (one per vanilla dye color) and carries
  no key.
- **Registering** a blank card on a card reader turns it into a **Keycard** of the same
  color, stamping its **own key** (tooltip: `Key: xxxxxxxx`). A keycard may also carry
  **inherited keys** picked up through duplication — readers accept any of them, but
  registration always stamps only the own key.
- **Duplicating** a keycard onto a blank card also produces a keycard. **Duplication is
  a fork**: the copy starts with everything the source could open at that moment, and
  from then on the two are completely separate cards — registering or unregistering one
  never affects the other.
- Recolor any card by crafting it with **any dye** (shapeless, any placement).
  ⚠️ Dyeing wipes every key — the card comes out as a **blank card** of the dye color.

### Manager & Member Access Cards (16 colors each)
- The **Manager Access Card** is crafted from a blank card; its first registration mints the
  card's shared key. From then on **its registrations also apply to every member
  card it issued**:
  wherever the manager is registered, every issued member card passes too — and
  unregistering the manager shuts all of its member cards out of that reader.
- **Member Access Cards** are issued by duplicating a manager onto blank cards
  (same color is kept). They are pure pass tokens: they can't be registered,
  duplicated, or managed per reader — all control goes through the manager card.
- Duplicating a manager onto a **blank (unregistered) manager card** creates a **co-manager** —
  an exact clone with equal rights over the same member cards.
- There is **no owner binding**: possession is authority. Hand the manager over to
  transfer control; guard it (an ender chest works) to keep it. Dyeing a member card
  recycles it into a blank card; managers can't be dyed.

### Golden Keycard (skeleton key)
- **Operates every card reader**, regardless of registration or ownership.
- Can toggle **register mode** even on readers you don't own (see the reader table).
- Never carries a key, can't be registered or duplicated. Creative-only for now.

### Estate Keycard (owner-scoped master key)
- Crafted, then **activated by right-clicking**: the first click asks to confirm (white),
  a second click within ~5s binds it to you (green, "Bound to …") and writes your id onto
  the card.
- Once bound, it works exactly like a **golden keycard, but only on readers you own**
  (readers you placed) — standing use passes them, sneaking arms/toggles register mode.
  It does nothing on other players' readers.
- The binding lives on the card, so it **keeps working after you hand it to someone
  else**. It can't be registered or duplicated.

---

## 2. Card Readers (5 variants)

**Insert · Touch · Swipe · Advanced · Obsidian** — all five behave identically; only
the looks, status lights, recipe cost, and (for Obsidian) mining time differ.
Placeable on floors, walls, and ceilings.

### Basics
- A reader **binds to the player who places it** (= the owner).
- Once the owner **registers** a card, that card operates the reader.
- Using a registered card (standing) emits a **brief redstone signal** (strength 15,
  strongly powering the mounting face). The default length is 3 seconds, configurable
  via `defaultPulseLengthTicks` (see §8).
- One card can be registered on many readers, and one reader can hold many cards.
- Forked cards are managed **individually**: removing one card's registration only
  stops that card — related copies keep working.
- Each reader holds at most a configurable number of registrations
  (`maxRegistrationsPerReader` in `config/dynamickeycards-common.toml`, default 128);
  past the limit, register mode reports "Registration limit reached".
- Breaking a reader erases its binding and registrations.
- The **Obsidian** reader has the same hardness and blast resistance as a vanilla
  obsidian block, and needs a diamond (or better) pickaxe.

### Status lights
| Light | Meaning |
|---|---|
| Off | Idle |
| Solid green | Accepted (signal active) |
| Solid red | Denied (1s) |
| Blinking register display | Register mode armed (the Advanced type shows its blue screen with blinking LEDs) |

Results are audible too: **pass = high bell**, **registered = bright pling**,
**removed / cancelled / reset = low pling**, **denied = low bass**.

### Interaction table

**Normal state (register mode off)**

| Action | Owner | Non-owner |
|---|---|---|
| Standing + bare hand | Nothing | Nothing |
| Sneaking + bare hand | **Arm register mode** ("Register your keycard") | "Reader not bound to you" (red) |
| Standing + registered card | **Pass** (signal, no subtitle) | **Pass** |
| Standing + unregistered/blank card | "Unregistered keycard" (red) | Same |
| Sneaking + card | Nothing | Nothing |
| Standing + golden keycard | **Pass** | **Pass** |
| Sneaking + golden keycard | **Arm register mode** | **Arm register mode** |
| Any other item | Nothing | Nothing |

**Register mode armed** (same for owner and non-owner)

| Action | Result |
|---|---|
| Bare-hand click (standing or sneaking) | Cancel register mode ("Registration cancelled") |
| Sneaking + a card this reader accepts | **Unregister** ("Registration removed") — only this card; related copies keep working |
| Sneaking + a blank card | **Register** — the blank card becomes a same-color **keycard** with a fresh key ("Registration complete", green) |
| Sneaking + a keyed card this reader rejects | **Register** ("Registration complete", green) |
| Sneaking + a member card | "Member access cards can't be registered" (red) |
| Standing + card | Nothing |
| Standing + golden keycard | Cancel register mode |
| Sneaking + golden keycard (1st) | **Confirm reset** — "Sneak-click again to wipe every registered card" (red) |
| Sneaking + golden keycard (2nd) | **Full reset** — wipes every registered card ("Card reader has been reset") |

### Linking to another reader
- Hold an unplaced reader and right-click an existing one to set it to link — the
  item gets an enchant-glint shimmer and a white message confirms it ("Tuned to
  that device"). Right-click empty air to cancel before placing it.
- Place it: the connection completes ("Connected to the existing device", green),
  and a white outline highlights the connected reader for as long as you're
  holding a reader (or sensor) set to connect to it. The same gesture links an
  advanced sensor to a reader too — see §4.
- Once linked (it's mutual — both readers point at each other), each reader
  accepts a card the moment it's registered on **either** reader, and honors a
  card blocked on either too.
- Everything else about each reader stays independent: owner, register mode,
  signal mode/frequency, signal length. You still register or remove a card at
  one specific reader — linking only changes which cards each reader *accepts*,
  not where you manage them.
- There's currently no way to unlink two already-placed readers — only cancel
  before placing, by right-clicking empty air with the unplaced item.

---

## 3. Card Duplicator

Forks a keyed card onto blank keycards.

### Basics
- An 8×8 pad, placeable on floors, walls, and ceilings.
- **No ownership** — anyone can use it.
- **Emits no redstone.**
- Touch the source card, then a blank card. The copy opens **everything the source
  could open at that moment** (card colors don't matter), and from then on the two are
  **completely separate cards**: registering, unregistering, or blocking one never
  affects the other.
- Breaking the duplicator discards any pending source.

### Status lights
| Light | Meaning |
|---|---|
| Off | Idle |
| Blinking green | Copy pending (waiting for a blank card) |
| Solid green | Duplication complete (1.5s) |
| Solid red | Rejected (1s) |

The duplicator is audible too: **complete = high bell**, **source inserted = bright
pling**, **cancelled = low pling**, **rejected = low bass**.

### Interaction table

**Idle (no source inserted)** — same for everyone

| Action | Result |
|---|---|
| Standing + bare hand | Nothing |
| Sneaking + bare hand | Prompt ("Touch the card to duplicate") |
| Sneaking + keyed card | **Source touched** — green light starts blinking ("Now touch a blank card") |
| Sneaking + keyed manager card | **Source touched** — the manager is never re-keyed |
| Sneaking + blank card | Rejected ("A blank card has nothing to duplicate", red) |
| Sneaking + member card | Rejected ("Member access cards can't be duplicated", red) |
| Sneaking + golden keycard | Rejected ("Golden keycards can't be duplicated", red) |
| Standing + card | Nothing |
| Any other item | Nothing |

**Copy pending (source inserted, green blinking)**

| Action | Result |
|---|---|
| Bare-hand click (standing or sneaking) | Cancel ("Duplication cancelled") |
| Sneaking + blank card | **Duplication complete** — the blank card becomes a same-color **keycard** (fork copy), or a **member access card** when the source is a manager |
| Sneaking + blank (unregistered) manager card | **Co-manager** issued (manager source only; otherwise "Only a manager access card can be copied onto a blank manager card", red) |
| Sneaking + keyed card / member | Rejected ("That card isn't blank", red) — source kept |
| Sneaking + golden keycard | Rejected ("Golden keycards can't be duplicated", red) — source kept |
| Standing + card | Nothing |
| Any other item | Nothing |

A rejection keeps the pending source; after the red flash the light returns to
blinking green.

---

## 4. Sensors

**Wall Sensor · Ceiling Sensor** (plain tier) and **Advanced Wall Sensor · Advanced
Ceiling Sensor** (advanced tier). A wall sensor mounts on a vertical wall face and
opens outward in the direction it was placed; a ceiling sensor mounts flush on a
ceiling block and is fully symmetric (no facing).

### Basics
- Detects living entities (mobs, players — spectators don't count) in its own cell
  and the cell directly below.
- Outputs a **continuous** redstone signal (strength 15, strongly powering the
  mounting face) that tracks detection directly — it stays on for as long as
  something's there, rather than running for a fixed length once triggered like a
  reader's signal does.
- **Release delay**: configurable per sensor, how long the signal keeps going after
  the last detection (0 ticks cuts it the instant nothing's left). Same adjustment
  screen as a reader's signal length — see the Create notes in §6.
- **No ownership** — anyone can configure or pick one up.
- Breaking a sensor loses its configuration (and, for the advanced tier, its binding
  and color too).

### Advanced sensors: binding to a reader
- Hold an unplaced advanced sensor and right-click an existing card reader to bind
  them — same gesture, message flow, and highlight as linking two readers together
  (see §2).
- Once bound, the sensor's own signal mode and frequency slots are locked — the
  reader owns those now. Its own signal length (the release delay above) stays
  independently adjustable, since a proximity sensor and a tap reader are used
  differently enough that they shouldn't share one timing knob.
- While bound, whenever a nearby player in the sensor's detection zone carries a
  card the reader would accept — anywhere in their inventory, not just their hand —
  the sensor triggers the reader's own accept signal remotely: same sound, same light,
  same redstone, as if physically tapped. The sensor's own local signal fires too,
  on the same detection.
- Left unbound, an advanced sensor works exactly like the plain one in every way.

### Dyeing (advanced sensors only)
- Right-click a placed advanced sensor with any of the 16 vanilla dyes to recolor
  it (consumes one dye, unless you're in creative).
- Right-click it with a **gold nugget** to reset it back to its natural
  gold-accented look (consumes one nugget the same way; does nothing if it's
  already undyed).

### Recipes
- **Wall Sensor** (makes 2): iron ingot ×3 / redstone – observer – redstone / black
  stained glass ×3.
- **Advanced Wall Sensor** (makes 2): the same layout with gold ingots instead of
  iron.
- **Ceiling Sensor** / **Advanced Ceiling Sensor**: no recipe of their own — put a
  wall sensor (or advanced wall sensor) on a crafting table by itself to swap it for
  the ceiling version, and vice versa.

---

## 5. Recipes

### Card readers (shaped)
Middle and bottom rows are shared: **redstone – redstone lamp – redstone / iron ingot ×3**
(the Advanced reader uses gold ingots ×3 instead).

| Variant | Top row |
|---|---|
| Insert | (empty) hopper (empty) |
| Touch | black stained glass ×3 |
| Swipe | rail ×3 |
| Advanced | amethyst shard ×3 |

The **Obsidian** reader has its own recipe, independent of the shared pattern above:
gold ingot – hopper – gold ingot / redstone – redstone lamp – redstone / obsidian ×3.

### Keycards
- **White Blank Card**: dried kelp ×3 / gold nugget – redstone – paper / iron nugget ×3
- **Dyeing**: any card + any dye (shapeless) → that color's **blank card** (key is wiped)

### Card Duplicator
(empty) any keycard (empty) / gold ingot – obsidian – gold ingot / obsidian ×3

### Manager Access Card (per color)
paper – gold ingot – paper / gold ingot – that color's blank card – gold ingot / paper – gold ingot – paper

### Golden Keycard
No recipe (creative-only).

### Estate Keycard
obsidian – redstone – obsidian / diamond – any blank card – gold ingot / obsidian – redstone – obsidian

---

## 6. Compatibility notes
- Readers and duplicators can't be picked up by sneak-click carrying mods
  (e.g. Carry On).
- Metal ingredients use common tags (`c:ingots/iron`, etc.), so equivalent materials
  from other mods work in the recipes.
- With **Jade** installed, looking at a reader shows its owner and whether register
  mode is armed, and looking at a duplicator shows whether a copy is pending
  (optional — no effect when Jade is absent).
- With **Create** installed, a reader (or a sensor — see §4) responds to its
  wrench:
  - **Standing + wrench** opens the link screen — two ghost frequency slots (like a
    Redstone Link transmitter) that let the device's accept signal transmit
    wirelessly over a Redstone Link network. A reader's signal mode picks how: Normal
    (physical redstone only), Link (wireless only), or **Mixed** (both physical and
    wireless at once).
  - The number display in that same screen shows the device's current **signal
    length**. Hold right-click on it for a moment to open an adjustment scale —
    three rows (ticks / seconds / minutes) with milestone marks every 10 units.
    Move the mouse (or scroll) to pick a value, then release right-click to confirm it.
  - The trash-icon button clears the frequency slots *and* resets the signal length
    back to the default in one click.
  - **Sneak + wrench** asks for confirmation first (red warning) — a second sneak +
    wrench click then picks the device straight into your inventory instead of
    breaking it normally, reusing the block's own break sound and particles for the
    pickup. For a reader, this is owner-only; a non-owner gets the usual "not bound
    to you" response. Sensors have no owner, so anyone can pick one up.
  - A bound advanced sensor has its signal mode/frequency slots locked (the reader
    it's bound to owns those) — only its own signal length stays adjustable there.
  - Optional — a device behaves as it always has when Create isn't installed.

---

## 7. Recipe viewer (EMI)
If [EMI](https://modrinth.com/mod/emi) is installed, the card **machines** show up in
its recipe browser — because registering and duplicating happen through block
interaction, not a crafting grid, they get their own categories:

- **Card Registering** — registering a blank card or a keycard on a reader (16
  entries). The input slot cycles between the blank card and the keycard, since an
  already keyed keycard can be registered on more readers too.
- **Card Duplicating** — the three duplicator outcomes:
  - **Fork**: a keycard + a blank card → a new keycard copy.
  - **Issue member**: a manager access card + a blank card → a member access card.
  - **Co-manager**: a manager access card + a blank manager card → a second manager.

Each entry reads left-to-right as a process: the input card(s), then the
machine block (shown in the middle — **hover it to see which machine it is**), an
arrow, and the result on the right. Any reader and the duplicator are registered as
**workstations**, so you can look at the block in EMI and jump straight to its
recipes (and back, via recipe-tree lookups on the cards).

> EMI is optional — this only appears when EMI is installed.

---

## 8. Config & commands

**Config** (`config/dynamickeycards-common.toml`):
- `maxRegistrationsPerReader` (default 128) — per-reader registration cap.
- `defaultPulseLengthTicks` (default 60 = 3 seconds) — the accept-signal length used by
  a reader that hasn't had its own set. Per-reader signal lengths can be set directly
  from a reader's link screen (see §6's Create notes).

**Command:**
- `/dynamickeycards transfer <player>` — while looking at one of your readers, hands
  its ownership to another player. Registrations are kept; only the owner changes.
  Operators (permission level 2) can transfer any reader.
