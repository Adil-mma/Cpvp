# GUI and Auto Totem

The main Cpvp screen opens with `9`. Left-click a module row to toggle its saved state; right-click to open that module's settings panel. The two main panels are Crystal and Anchor.

Auto Totem can be toggled with `L` or from the GUI. Its settings are stored in `config/cpvp.json`:
- `AUTO`: automatically moves a totem into the offhand when needed and maintains the configured backup hotbar slot.
- `MANUAL`: while the player inventory is open, hovering the cursor over a totem picks it up to the cursor.

The Crystal and Anchor entries expose separate configuration screens, but automated combat actions for those entries are not implemented in this branch; their status is shown as configuration-only rather than active.