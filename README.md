# Cpvp

Minecraft 26.2 client-side Fabric mod.

## Controls and settings

- **Open Cpvp menu** is a normal Minecraft keybind under **Options → Controls → Key Binds → Cpvp**. It defaults to Insert, but can be changed there.
- Left-click a module in the main GUI to toggle it. Right-click a module to open its settings.
- Each module's **Toggle key** is configured inside that module's settings. Click the row, then press a keyboard key or mouse button.
- Each setting has its own reset arrow. Delay rows use the dual-handle 0–5 tick slider.
- Delay values are client ticks, not milliseconds. At 20 TPS, 1 tick is approximately 50 ms. The min/max value is rolled once per performed action.

## Modules

- **Fast Crystal:** places crystals on the obsidian/bedrock block under the crosshair. It preserves right-click for golden apples, enchanted golden apples, ender pearls, and shields in either hand.
- **Auto Crystal:** while use is held, places a crystal on the obsidian/bedrock base under the crosshair and attacks a crystal the crosshair is on. The camera only turns (gradually, client-side) when the crosshair is on obsidian/bedrock that already carries a visible crystal, so the view is never moved anywhere else. It respects range and the configured above-target check.
- **Hit Crystal:** while use is held, attacks an End Crystal directly under the crosshair and within the configured range.
- **Anchor Macro:** the player places the respawn anchor manually. While use is held and the crosshair is on that anchor, Cpvp charges it with glowstone and detonates it with the configured hotbar slot, whatever that slot holds (a Respawn Anchor works; only glowstone cannot). It preserves priority-item use.
- **Safe Anchor:** after detecting the manually placed anchor, Cpvp charges it with glowstone. If self-protection is needed, it finds a replaceable cover cell with a solid support face, gradually turns the client camera toward that face, and places one Glowstone block. It then gradually restores the view direction saved when the anchor sequence began, before aiming back at the anchor and detonating with the hotbar slot chosen in its settings (default slot 9, normally a Totem; any item that can detonate an anchor works). It never substitutes obsidian or another cover block.
  While a Safe Anchor sequence runs, Cpvp owns the use button so a held right-click cannot add extra charges or anchors; press right-click again for the next anchor. The cover glowstone is sent once the camera is within about 3 degrees of the verified support face, so it also works while running.
- **Double Anchor:** manually place the first anchor. Cpvp charges it, detonates the first pass, then attempts to place the next anchor in the same cell using the module's air-place interaction hit. It charges the replacement and uses a Totem for the later detonation. The sequence continues after right-click is released. Air-place is not ordinary vanilla block placement; the server may reject it. Default: 2 passes.
- **Auto Totem:** Auto mode replenishes the offhand and configured backup hotbar slot through vanilla inventory swap operations. Manual mode only acts while the player's inventory is open and the cursor is over a totem; it swaps the hovered totem into the offhand, or into the configured backup slot if the offhand already contains a totem.
- **Sword Swap:** on the attack-button press while the crosshair is on another player, temporarily selects the best hotbar sword for eligible held items, then restores the previous slot. Any held item swaps (an End Crystal included) except axes and pickaxes. It never swaps for other entities or for air.

## Interaction model

- Combat actions use Minecraft's client game-mode interaction methods. Camera rotation is performed through the local player yaw/pitch; no custom server-rotation packet spoofing is used.
- The Double Anchor air-place attempt uses a constructed interaction hit for its specific mechanic. This is not a vanilla placement guarantee and can be rejected by a server or anti-cheat.
- Normal reach, visibility, inventory state, server validation, and game rules still apply. No anti-cheat bypass or ban-safety guarantee is claimed.
- Test all sequences in a local 26.2 world before relying on them on a server.
