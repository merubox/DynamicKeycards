# Changelog

[한국어 버전 (Korean version)](CHANGELOG_KO.md)

All notable changes to Dynamic Keycards are documented here.

## 0.1.0

### Changed
- **New Blank Card recipe**: dried kelp ×3 on top, gold nugget – redstone – paper in
  the middle, iron nugget ×3 on the bottom (replaces the heavy-pressure-plate recipe).
  Metal ingredients are tag-based (`c:dusts/redstone`, `c:nuggets/gold`,
  `c:nuggets/iron`), so equivalents from other mods work.

### Added
- **GitHub README** (English/Korean) with a feature overview, progress table, and a
  card showcase image.

## 0.0.9

### Added
- **Distinct feedback tones** on both machines (vanilla note-block sounds, no custom
  assets): high bell = pass / duplication complete, bright pling = registered, low
  pling = removed / cancelled / reset, low bass = denied. The result is audible
  without watching the status lights.
- **Jade (WAILA) support**: looking at a reader shows its owner (resolved server-side,
  so offline owners display too) and whether register mode is armed; looking at a
  duplicator shows whether a copy is pending. Optional dependency — loaded only when
  Jade is installed.

### Changed
- **Manager Access Card texture**: the lettering was replaced with a crown.
- **"Crew" terminology removed** from every tooltip, action-bar message, and the
  manuals, in all 7 languages. Relationships are described plainly instead: a manager
  card's registrations also apply to the member cards it issued.
- **Hold-Shift tooltip now matches Create's official translations** per language
  (Korean: "설명 보기", Japanese: "説明を表示…", etc.; English stays
  "Hold [Shift] for Summary").
- **EMI: the registering category's input now cycles blank card ↔ keycard**, since an
  already keyed keycard can be registered on more readers too.

## 0.0.8

### Changed
- **Corrected the 0.0.6 rename — only the crew cards use the "Access Card" name.**
  The keyed card is the **Keycard** again (`<color>_keycard`) and the golden card is
  the **Golden Keycard** (`golden_keycard`); their ids, models, textures, tags, and
  all 7 languages were reverted. The **Blank Card** and the crew **Manager / Member
  Access Cards** keep their 0.0.6 names. (Breaking for worlds that used 0.0.6/0.0.7
  keycards — re-obtain them.)

### Fixed
- **EMI: recipes now show for every color, not just white.** The duplicating category
  is generated per color (16 fork + 16 member + 16 co-manager entries instead of one
  white-only entry each), so looking up any colored keycard / member / manager card
  finds its recipe.

## 0.0.7

### Added
- **EMI recipe-viewer integration.** When [EMI](https://modrinth.com/mod/emi) is
  installed, the machine "recipes" now show up in its recipe browser as two
  categories:
  - **Card Registering** — each blank card becomes the same-color access card on a
    reader (16 entries).
  - **Card Duplicating** — fork (access + blank → access copy), issue member
    (manager + blank → member), and co-manager (manager + blank manager → manager).

  Each entry is laid out Create-style: the input card(s) on the left, the machine
  block shown as a hoverable catalyst in the middle (hover it for the machine's
  name), an arrow, and the produced card on the right. Any reader / the duplicator
  is registered as a workstation, so you can jump straight from the block to its
  recipes. EMI is an optional dependency — the plugin is only loaded when EMI is
  present.

## 0.0.6

### Changed
- **Renamed the card family.** The craftable base is now the **Blank Card** (name
  kept generic for future card types); the keyed result is the **Access Card**; the
  crew cards dropped "keycard" and became **Manager Access Card** / **Member Access
  Card**; the golden card is the **Golden Access Card**. Item ids, textures, recipes,
  tags, and text were renamed to match (breaking for existing worlds — re-obtain the
  cards).
- **All item types now appear in the creative tab** (blank / access / manager /
  member / golden, per color), not just the craftable ones.

## 0.0.5

### Changed
- **Split the plain card into a Blank Keycard and a Keycard.** Blank Keycards
  (16 colors) are now the craftable base and carry no key; registering a blank
  card on a reader turns it into a same-color **Keycard** with a fresh key, and
  duplicating a card onto a blank turns it into a keycard (fork copy) or a crew
  member. This makes "unkeyed vs keyed" visible at a glance instead of relying on
  the tooltip.
- Recipes moved onto the blank card: the shaped White recipe now yields a **White
  Blank Keycard**, the Crew Manager recipe surrounds a **blank card** with
  diamonds, and dyeing any card yields the target color's **blank card**.
- Keycards and crew members are no longer in the creative tab (they are gameplay
  results); the tab holds blank cards, crew managers, and the golden keycard.

## 0.0.4

### Added
- **Crew Manager Keycards** (16 colors): crafted blank (diamonds around a keycard);
  the first registration mints the crew's group key. Their registrations apply to the
  whole crew, and they are never re-keyed by the duplicator. No owner binding —
  possession is authority.
- **Crew Member Keycards** (16 colors): issued by duplicating a manager onto blank
  keycards (color kept). Pass-only — can't be registered or duplicated, but can be
  shut out per reader. Dyeing recycles them into blank keycards.
- **Co-managers**: duplicating a manager onto a blank manager keycard clones the
  group key with equal rights.
- **Registration cap per reader**: `maxRegistrationsPerReader` in
  `config/dynamickeycards-common.toml` (default 128).

## 0.0.3

### Changed
- **Duplication is now a true fork.** The copy inherits everything the source could
  open at the moment of copying, and both cards receive a fresh own key — from then on
  registering, unregistering, or blocking one card never affects the other.
  (Previously a copy shared the source's key forever.)
- Registration always stamps a card's **own key**; inherited keys open readers but are
  never written by registration.
- The Golden Keycard is now described as a **skeleton key** (the group-managing
  "Crew Manager" card planned for a later version takes over the master role).

### Added
- **Per-card registration toggling.** In register mode, one press always toggles the
  presented card: accepted cards get unregistered (only that card — related copies
  keep working), rejected cards get registered. Internally this uses a block list
  that always beats the allow list, but players only ever see register/remove.
- Keycard tooltips now show the number of inherited keys.
- This changelog.

## 0.0.2

### Added
- **Card Duplicator** (8×8 pad, floor/wall/ceiling): copies a card's key onto blank
  keycards. Green blink while waiting for the blank card, solid green on completion,
  red flash on rejected inputs. No redstone output, no ownership.
- **Unregister**: in register mode, presenting an already-registered card removes it.
- **Golden full reset**: with register mode armed, sneak-clicking with the Golden
  Keycard wipes every registered card.
- User manual (`MANUAL.md` English, `MANUAL_KO.md` Korean).

### Changed
- Cancelling a duplication works with a bare hand standing or sneaking, matching the
  card reader's register-mode convention.

## 0.0.1

Initial release.

- **Four card reader variants** — Insert, Touch, Swipe, Advanced — identical behavior,
  distinct looks; four visual states (idle / accepted / denied / blinking register).
- **Player binding**: readers bind to their placer; the owner registers keycards by
  sneak-clicking; registered cards trigger a 3-second redstone pulse.
- **16 colored keycards** with unique keys, dye recoloring, and shaped/shapeless
  recipes; **Golden Keycard** master card (creative-only).
- Create-style tooltips, action-bar feedback, 7-language localization.
- Compatibility with sneak-click pickup mods (e.g. Carry On).
