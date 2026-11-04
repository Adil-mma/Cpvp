# GUI and Auto Totem

The Cpvp menu opens with the configurable **Open Cpvp menu** keybind. Its default is Insert; change it under **Options → Controls → Key Binds → Cpvp**. Left-click a module row to toggle it and right-click to open its settings.

Auto Totem can be enabled from the GUI or with its configured module key. Settings are stored in config/cpvp.json:

- **AUTO:** while no screen/container is open, replenishes the offhand and configured backup hotbar slot with vanilla inventory swap operations.
- **MANUAL:** performs no background swaps. While the player's own inventory is open, hovering a totem swaps it into the offhand. If the offhand already contains a totem, the hovered spare moves to the configured backup hotbar slot; an occupied destination is swapped back into the source slot.
- Both modes require the player's own inventory menu. Cpvp does not move items through another container.

The Crystal and Anchor modules are active in this branch. Their interactions use regular client game-mode methods and actual crosshair hit results. Automatic camera rotation and synthetic air-place hits are not used.