# Dynamic Keycards — User Manual

[한국어 버전 (Korean version)](MANUAL_KO.md)

For version 0.0.2. Every interaction is a **right-click**; "sneak" means holding Shift.

---

## 1. Items

### Keycards (16 colors)
- One per vanilla dye color. A freshly crafted card is **blank** (carries no key).
- Registering it on a card reader, or copying onto it with a card duplicator, stamps a
  **unique key** onto the card (shown in the tooltip as `Key: xxxxxxxx`). Two cards of
  the same color with different keys are different cards.
- Recolor a card by crafting it with **any dye** (shapeless, any placement).
  ⚠️ Dyeing wipes the key — the card comes out **blank**.
- Registering a stack of keyed cards stamps the whole stack with the same key.

### Golden Keycard (master card)
- **Operates every card reader**, regardless of registration or ownership.
- Can toggle **register mode** even on readers you don't own (see the reader table).
- Never carries a key, can't be registered or duplicated. Creative-only for now.

---

## 2. Card Readers (4 variants)

**Insert · Touch · Swipe · Advanced** — all four behave identically; only the looks and
status lights differ. Placeable on floors, walls, and ceilings.

### Basics
- A reader **binds to the player who places it** (= the owner).
- Once the owner **registers** a card, that card operates the reader.
- Using a registered card (standing) emits a **3-second redstone pulse**
  (signal strength 15, strongly powering the mounting face).
- One card can be registered on many readers, and one reader can hold many cards.
- Breaking a reader erases its binding and registrations.

### Status lights
| Light | Meaning |
|---|---|
| Off | Idle |
| Solid green | Accepted (pulse running, 3s) |
| Solid red | Denied (1s) |
| Blinking register display | Register mode armed (the Advanced type shows its blue screen with blinking LEDs) |

### Interaction table

**Normal state (register mode off)**

| Action | Owner | Non-owner |
|---|---|---|
| Standing + bare hand | Nothing | Nothing |
| Sneaking + bare hand | **Arm register mode** ("Register your keycard") | "Reader not bound to you" (red) |
| Standing + registered card | **Pass** (pulse, no subtitle) | **Pass** |
| Standing + unregistered/blank card | "Unregistered keycard" (red) | Same |
| Sneaking + card | Nothing | Nothing |
| Standing + golden keycard | **Pass** | **Pass** |
| Sneaking + golden keycard | **Arm register mode** | **Arm register mode** |
| Any other item | Nothing | Nothing |

**Register mode armed** (same for owner and non-owner)

| Action | Result |
|---|---|
| Bare-hand click (standing or sneaking) | Cancel register mode ("Registration cancelled") |
| Sneaking + new card | **Register** — a blank card gets a fresh key ("Registration complete", green) |
| Sneaking + already-registered card | **Unregister** ("Registration removed") |
| Standing + card | Nothing |
| Standing + golden keycard | Cancel register mode |
| Sneaking + golden keycard | **Full reset** — wipes every registered card ("Card reader has been reset") |

---

## 3. Card Duplicator

Copies a keyed card's key onto blank keycards.

### Basics
- An 8×8 pad, placeable on floors, walls, and ceilings.
- **No ownership** — anyone can use it.
- **Emits no redstone.**
- Insert the source card, then a blank card: the blank card receives the same key as
  the source. Card colors are kept, so differently colored cards can share one key.
- Breaking the duplicator discards any pending source.

### Status lights
| Light | Meaning |
|---|---|
| Off | Idle |
| Blinking green | Copy pending (waiting for a blank card) |
| Solid green | Duplication complete (1.5s) |
| Solid red | Rejected (1s) |

### Interaction table

**Idle (no source inserted)** — same for everyone

| Action | Result |
|---|---|
| Standing + bare hand | Nothing |
| Sneaking + bare hand | Prompt ("Insert the card to duplicate") |
| Sneaking + keyed card | **Source inserted** — green light starts blinking ("Now insert a blank keycard") |
| Sneaking + blank card | Rejected ("A blank keycard has nothing to duplicate", red) |
| Sneaking + golden keycard | Rejected ("Golden keycards can't be duplicated", red) |
| Standing + card | Nothing |
| Any other item | Nothing |

**Copy pending (source inserted, green blinking)**

| Action | Result |
|---|---|
| Bare-hand click (standing or sneaking) | Cancel ("Duplication cancelled") |
| Sneaking + blank card | **Duplication complete** — solid green ("Duplication complete", green) |
| Sneaking + keyed card | Rejected ("That keycard isn't blank", red) — source kept |
| Sneaking + golden keycard | Rejected ("Golden keycards can't be duplicated", red) — source kept |
| Standing + card | Nothing |
| Any other item | Nothing |

A rejection keeps the pending source; after the red flash the light returns to
blinking green.

---

## 4. Recipes

### Card readers (shaped)
Middle and bottom rows are shared: **redstone – redstone lamp – redstone / iron ingot ×3**
(the Advanced reader uses gold ingots ×3 instead).

| Variant | Top row |
|---|---|
| Insert | (empty) hopper (empty) |
| Touch | black stained glass ×3 |
| Swipe | rail ×3 |
| Advanced | amethyst shard ×3 |

### Keycards
- **White Keycard**: redstone ×3 / (empty) paper (empty) / heavy weighted pressure plate ×3
- **Dyeing**: any keycard + any dye (shapeless) → that color's keycard (key is wiped)

### Card Duplicator
(empty) any keycard (empty) / gold ingot – obsidian – gold ingot / obsidian ×3

### Golden Keycard
No recipe (creative-only).

---

## 5. Compatibility notes
- Readers and duplicators can't be picked up by sneak-click carrying mods
  (e.g. Carry On).
- Metal ingredients use common tags (`c:ingots/iron`, etc.), so equivalent materials
  from other mods work in the recipes.
