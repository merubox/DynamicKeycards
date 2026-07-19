# Dynamic Keycards

Keycards and player-bound card readers for redstone access control.
NeoForge · Minecraft 1.21.1

See [MANUAL.md](MANUAL.md) for the full user manual ([Korean](MANUAL_KO.md)).

## Features

- **Four card reader variants** — Insert, Touch, Swipe, and Advanced — same behavior,
  distinct looks and status lights (idle / accepted / denied / blinking register mode).
- **Player binding**: a reader binds to its placer. The owner sneak-clicks to enter
  register mode, then sneak-clicks with a keycard to register it — or with an
  already-registered card to unregister it.
- **16 colored keycards**: blank until registered; the first registration stamps a
  unique key. Registered cards trigger a 3-second redstone pulse. Recolor with dye.
- **Card Duplicator**: copies a card's key onto blank keycards — sneak-click with the
  source card (green blink), then with a blank card.
- **Golden Keycard**: master card that operates any reader, toggles register mode on
  any reader, and can wipe a reader's registrations (creative only for now).
- Plays nice with sneak-click pickup mods (e.g. Carry On).

## Build

```
gradle build        # jar lands in build/libs/
gradle runServer    # headless dedicated-server smoke test
gradle runClient
```

Requires JDK 21. Built with ModDevGradle against NeoForge 21.1.233.

## License

MIT
