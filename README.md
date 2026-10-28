# Cpvp

Minecraft 26.2 client-side Fabric mod.

## Controls and settings

- **Open Cpvp menu** is a normal Minecraft keybind under **Options → Controls → Key Binds → Cpvp**. It defaults to `9`, but can be changed there, so it does not need to use Right Shift.
- Left-click a module in the main GUI to toggle it. Right-click a module to open its settings.
- Each module's **Toggle key** is configured inside that module's settings. Click the row, then press a keyboard key or mouse button. Use the reset arrow to clear it.
- Every setting has its own reset arrow. Delay intervals have independent Min and Max controls, each from 0 to 5 ticks. All delay intervals default to **1–2 ticks**.
- A one-tick interval is approximately 50 ms. If Min is raised above Max, Max follows Min; if Max is lowered below Min, Min follows Max.

## Modules

- **Fast Crystal:** configurable delay interval and holding-use option.
- **Auto Crystal:** configurable range and delay interval; handles a crystal entity under the crosshair as well as crystals already spawned nearby.
- **Hit Crystal:** configurable range, delay interval, and above-target stop.
- **Anchor Macro:** delay and stage-delay intervals, auto-charge option, and detonator slot.
- **Safe Anchor:** action-delay interval, self-distance, and cover protection option.
- **Double Anchor:** repeat and stage-delay intervals, pass count, and detonator slot.
- **Auto Totem:** Auto and Manual modes, backup hotbar slot.
- **Totem Swap:** when attack is pressed with a totem selected in the main hand, temporarily selects a sword and returns to the original totem slot after the configured interval.

## Notes

- Minecraft and the server still determine whether a click/interaction succeeds; reach, inventory state, and server rules apply.
- Anchor automation should be tested on a local 26.2 world before relying on it on a server.
