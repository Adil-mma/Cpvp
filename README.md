# Cpvp

Minecraft 26.2 client-side Fabric mod.

## Controls and settings

- **Open Cpvp menu** is a normal Minecraft keybind under **Options → Controls → Key Binds → Cpvp**. It defaults to `Insert`, but can be changed there.
- Left-click a module in the main GUI to toggle it. Right-click a module to open its settings.
- Each module's **Toggle key** is configured inside that module's settings. Click the row, then press a keyboard key or mouse button.
- Each setting has its own reset arrow. Delay rows use the dual-handle 0–5 tick slider shown in the updated settings screen.
- Delay values are actual client ticks, not milliseconds. At 20 TPS, 1 tick is approximately 50 ms; 0 is the fastest one-action-per-client-tick setting. The min/max value is rolled once per action.

## Modules

- **Fast Crystal:** places crystals on an aimed obsidian/bedrock base. It does not take right-click away from golden apples, enchanted golden apples, ender pearls, or shields.
- **Auto Crystal:** hold use on obsidian/bedrock to place a crystal and break it. The above-target check is based on the crystal/base top surface; it stops when the player's feet are clearly higher than that level.
- **Hit Crystal:** hold use to break crystals in range. Its default action delay is 0–1 ticks.
- **Anchor Macro:** the player places the respawn anchor manually. While use is held and the player is looking at that anchor, Cpvp charges it once with glowstone and detonates it with the configured hotbar slot. It does not place or switch to an anchor.
- **Safe Anchor:** looks only at a manually placed anchor. If the player is close and there is no cover, it tries to place a glowstone block between the player and anchor. It then charges the anchor once if needed and detonates it using the configured totem slot (default hotbar slot 9). It will not detonate when the requested cover cannot be placed or verified.
- **Double Anchor:** you manually place only the first anchor. While use is held and you keep aiming at that position, it charges with glowstone, switches back to the configured Respawn Anchor hotbar slot, clicks the charged anchor to detonate it, then uses the same anchor stack to place the next anchor at the same support face. It repeats until the configured pass count is reached. Default: 2 passes.
- **Auto Totem:** Auto mode replenishes offhand and backup hotbar automatically. Manual mode does nothing in the background: when the inventory is open, hover a totem to swap it into offhand; if offhand already has a totem, the hovered spare moves to the configured backup hotbar slot, swapping any item already in that destination back to the source slot.
- **Sword Swap:** when attack is pressed while the crosshair is on another player, temporarily switches to the best hotbar sword for eligible held items and returns to the original slot. End crystals, axes and pickaxes are excluded.

## Notes

- Minecraft and the server still determine whether an interaction succeeds; range, inventory state, and server rules apply.
- Anchor automation should be tested on a local 26.2 world before relying on it on a server.
