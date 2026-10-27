# Cpvp

Minecraft 26.2 client-side Fabric mod.

## Controls

- `9`: open the two-panel Crystal / Anchor GUI.
- `L`: toggle Auto Totem.
- `F6`: toggle Fast Crystal.
- `F7`: toggle Auto Crystal.
- `F8`: toggle Hit Crystal.
- `F9`: toggle Anchor Macro.
- `F10`: toggle Safe Anchor.
- `F11`: toggle Double Anchor.

All keybindings can be changed in Minecraft's Controls menu. In the GUI, left-click a module to toggle it and right-click to edit its settings. Enabled modules show `ON`.

## Module behavior

- **Fast Crystal:** repeats crystal placement on a looked-at obsidian/bedrock block while use is held; delay is configurable.
- **Auto Crystal:** while use is held, attempts crystal placement and attacks nearby crystals at a configurable range. Its safety stop prevents actions when the player is above the target.
- **Hit Crystal:** while use is held, attacks a targeted or nearby crystal, with configurable range, delay, and above-target stop.
- **Anchor Macro:** while use is held, attempts to place an anchor on the looked-at face, charge it with glowstone, and use the configured hotbar slot to detonate it.
- **Safe Anchor:** checks for solid cover between the player and a targeted anchor. If cover is absent, it attempts to place a solid block before detonating; it does not detonate if cover cannot be verified.
- **Double Anchor:** automates anchor placement/charging/detonation with a configurable stage delay.
- **Auto Totem:** retains Auto and Manual modes.

## Notes

- Combat actions are client-side inputs and remain subject to Minecraft/server validation, interaction reach, inventory contents, and server tick rate.
- Delay values are milliseconds; a value of `0` still cannot make the server process actions faster than its own limits.
- Anchor automation modules are mutually exclusive to avoid conflicting placement/detonation sequences.
- The generated build should be tested in a local Minecraft 26.2 instance and on the intended server before relying on it in combat.
