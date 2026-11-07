# GUI and Auto Totem

The Cpvp menu opens with the configurable **Open Cpvp menu** keybind. Its default is Insert; change it under **Options → Controls → Key Binds → Cpvp**. Left-click a module row to toggle it and right-click to open its settings.

Auto Totem can be enabled from the GUI or with its configured module key. Settings are stored in config/cpvp.json:

- **AUTO:** while no screen/container is open, replenishes the offhand and configured backup hotbar slot with vanilla inventory swap operations.
- **MANUAL:** performs no background swaps. While the player's own inventory is open, hovering a totem swaps it into the offhand. If the offhand already contains a totem, the hovered spare moves to the configured backup hotbar slot; an occupied destination is exchanged back into the source slot.
- Both modes require the player's own inventory menu. Cpvp does not move items through another container.

Sword Swap is independent from Anchor Macro. It selects the best available hotbar sword on the attack-button press when the crosshair is on another player, then restores the original slot after the configured delay. If the player manually changes the selected slot during the swap, Cpvp cancels the pending restore rather than overriding the manual choice.

Crystal and Anchor modules use the normal client game-mode interaction methods. Auto Crystal and anchor targeting may rotate the local player camera gradually; this is ordinary client yaw/pitch state, not a custom server-rotation packet. Double Anchor's second placement uses a synthetic interaction hit for its air-place mechanic, which servers may reject.
