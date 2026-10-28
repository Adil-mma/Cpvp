package dev.adil.cpvp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CpvpClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cpvp", "main"));
    private KeyMapping menu;
    private final boolean[] wasDown = new boolean[CpvpModule.values().length];

    @Override
    public void onInitializeClient() {
        CpvpConfig.load();
        // The GUI opener is a normal Minecraft Controls-menu binding.
        menu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cpvp.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_9, CATEGORY));

        ClientTickEvents.START_CLIENT_TICK.register(TotemSwapEngine::tick);

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (menu.consumeClick()) {
                if (c.player != null) c.gui.setScreen(new CpvpScreen());
            }

            CpvpModule[] modules = CpvpModule.values();
            boolean editingSettings = c.gui.screen() instanceof ModuleSettingsScreen;
            for (int i = 0; i < modules.length; i++) {
                int bind = modules[i].bind(CpvpConfig.settings());
                boolean down = !editingSettings && isBindDown(c, bind);
                if (down && !wasDown[i]) {
                    var settings = CpvpConfig.settings();
                    modules[i].setEnabled(settings, !modules[i].enabled(settings));
                    CpvpConfig.save();
                    if (c.player != null) {
                        c.player.sendSystemMessage(Component.literal(
                                "[Cpvp] " + modules[i].title + ": "
                                        + (modules[i].enabled(settings) ? "ON" : "OFF")));
                    }
                }
                wasDown[i] = down;
            }

            if (c.player != null && c.level != null && c.gameMode != null) {
                AutoTotemEngine.tick(c);
                CombatModuleEngine.tick(c);
            }
        });
    }

    private static boolean isBindDown(net.minecraft.client.Minecraft c, int bind) {
        if (bind == -1 || c.getWindow() == null) return false;
        long handle = c.getWindow().getWindow();
        if (bind >= 0) {
            return GLFW.glfwGetKey(handle, bind) == GLFW.GLFW_PRESS;
        }
        int mouseButton = -bind - 2;
        return mouseButton >= 0 && mouseButton <= 7
                && GLFW.glfwGetMouseButton(handle, mouseButton) == GLFW.GLFW_PRESS;
    }
}
