# GUI and Auto Totem

The main Cpvp screen opens with `9`. Left-click a module row to toggle its saved state; right-click to open that module's settings panel. The two main panels are Crystal and Anchor.

Auto Totem can be toggled with `L` or from the GUI. Its settings are stored in `config/cpvp.json`:
- `AUTO`: automatically moves a totem into the offhand when needed.
- `MANUAL`: while the player inventory is open and the offhand has no totem, hovering the cursor over a totem swaps it into the offhand (the same action as vanilla's "swap to offhand" key).
- Both modes keep the configured backup hotbar slot stocked with a spare totem whenever no screen is open, so Auto always has one to refill from and Manual always has one ready to hover.

The Crystal and Anchor entries expose separate configuration screens, but automated combat actions for those entries are not implemented in this branch; their status is shown as configuration-only rather than active.