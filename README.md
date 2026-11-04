# Cpvp

Minecraft 26.2 client-side Fabric mod.

## Controls and settings

- **Open Cpvp menu** is a normal Minecraft keybind under **Options → Controls → Key Binds → Cpvp**. It defaults to Insert, but can be changed there.
- Left-click a module in the main GUI to toggle it. Right-click a module to open its settings.
- Each module's **Toggle key** is configured inside that module's settings. Click the row, then press a keyboard key or mouse button.
- Each setting has its own reset arrow. Delay rows use the dual-handle 0–5 tick slider.
- Delay values are client ticks, not milliseconds. At 20 TPS, 1 tick is approximately 50 ms. The min/max value is rolled once per performed action.

## Modules

- **Fast Crystal:** places crystals on the obsidian/bedrock block currently under the real crosshair. It does not take right-click away from golden apples, enchanted golden apples, ender pearls, or shields.
- **Auto Crystal:** while use is held, places a crystal on the aimed obsidian/bedrock base. It breaks a crystal only when the real crosshair is on that crystal; it never turns the camera to acquire a target. A visible crystal in range prevents placement from using a stale block hit.
- **Hit Crystal:** while use is held, attacks an End Crystal currently under the crosshair and within the configured range.
- **Anchor Macro:** the player places the respawn anchor manually. While use is held and the real crosshair is on that anchor, Cpvp charges it with glowstone and detonates it with the configured hotbar slot. It does not place or switch to an anchor.
- **Safe Anchor:** after the player manually places and aims at an anchor, Cpvp charges it with glowstone. When self-protection is needed, it selects a replaceable cover cell that has a solid support block. The player must aim at the real support face to place the glowstone, then aim back at the anchor to detonate it with the configured Totem slot (default hotbar slot 9). The camera is never moved by the module.
- **Double Anchor:** manually place the first anchor and aim at it. Cpvp charges and detonates it, then waits for a normal crosshair hit on a solid support face that places the next anchor into the original cell. The player must aim at the anchor again for later charge/detonation stages. It continues after right-click is released and stops with a chat reason when the server does not confirm a stage. It does not synthesize air-place hits; an unsupported cell cannot be force-filled. Default: 2 passes.
- **Auto Totem:** Auto mode replenishes the offhand and configured backup hotbar slot using vanilla inventory swap operations. Manual mode only acts while the player's inventory is open and the cursor is over a totem; it swaps the hovered totem into the offhand, or into the configured backup slot if the offhand already contains a totem.
- **Sword Swap:** on the attack-button press while the crosshair is on another player, temporarily selects the best hotbar sword for eligible held items, then restores the prior slot. End crystals, axes, and pickaxes are excluded.

## Interaction model

- Combat interactions use the normal client game-mode interaction methods and actual crosshair hit results. The module does not spoof server rotation or manufacture an air-cell block hit.
- Minecraft and the server still decide whether an interaction succeeds. Normal reach, visibility, inventory state, block support, and server rules apply.
- Anchor sequences should be tested in a local 26.2 world before relying on them on a server.
