# Dynamic Keycards

Keycards and player-bound card readers for redstone access control.
NeoForge · Minecraft 1.21.1

## Features

- **Four card reader variants** — Insert, Touch, Swipe, and Advanced — sharing the
  same behavior with distinct looks and state lights (idle / accepted / denied /
  register, with blinking register LEDs).
- **Player binding**: a reader binds to whoever places it. The owner sneak-clicks
  to enter register mode, then sneak-clicks with a keycard to register it.
- **16 colored keycards**: blank until registered; the first registration stamps a
  unique key onto the card. Registered cards trigger a 3-second redstone pulse.
  Recolor cards with any dye (shapeless).
- **Golden Keycard**: master card that operates any reader and can toggle register
  mode on readers you don't own (creative only for now).
- Compatible with sneak-click pickup mods (e.g. Carry On) — readers can't be
  picked up by mistake.

## Build

```
gradle build        # jar lands in build/libs/
gradle runServer    # headless dedicated-server smoke test
gradle runClient
```

Requires JDK 21. Built with ModDevGradle against NeoForge 21.1.233.

## License

MIT
