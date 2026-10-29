# Cpvp

Minecraft 26.2 client-side Fabric mod.

## Controls and settings

- **Open Cpvp menu** is a normal Minecraft keybind under **Options → Controls → Key Binds → Cpvp**. It defaults to `Insert`, but can be changed there, so it does not need to use Right Shift or the inventory/hotbar keys.
- Left-click a module in the main GUI to toggle it. Right-click a module to open its settings.
- Each module's **Toggle key** is configured inside that module's settings. Click the row, then press a keyboard key or mouse button. Use the reset arrow to clear it.
- Every setting has its own reset arrow. Delay intervals have independent Min and Max controls, each from 0 to 5 ticks. All delay intervals default to **1–2 ticks**.
- Delays are counted in client ticks (20 per second) and applied after each performed action: `0` acts every tick (the fastest a client can act), `1` every 2nd tick, and so on. With Min != Max a value in the range is rolled per action.
- A one-tick interval is approximately 50 ms. If Min is raised above Max, Max follows Min; if Max is lowered below Min, Min follows Max.

## Modules

- **Fast Crystal:** configurable delay interval and holding-use option.
- **Auto Crystal:** hold use on obsidian/bedrock: places a crystal and breaks it. "Stop above target" pauses it while your feet are above the crystal/base, so a stray click from above cannot kill you.
- **Hit Crystal:** hold use (vanilla places the crystals); it only breaks crystals in range, with the same above-target stop.
- **Anchor Macro:** hold use: place anchor, charge once with glowstone, switch to the detonator slot and detonate; repeats while held.
- **Safe Anchor:** looks at an anchor; if you are within "cover within distance" and no solid block is between you and it, a cover block is placed first. It never detonates unless cover is verified.
- **Double Anchor:** like Anchor Macro but runs the configured number of place/charge/detonate passes per held click, then waits for the use button to be released.
- **Auto Totem:** Auto and Manual modes, backup hotbar slot.
- **Totem Swap:** when attack is pressed with a totem selected in the main hand, temporarily selects a sword and returns to the original totem slot after the configured interval.

## Notes

- Minecraft and the server still determine whether a click/interaction succeeds; reach, inventory state, and server rules apply.
- Anchor automation should be tested on a local 26.2 world before relying on it on a server.
