package dev.adil.cpvp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CpvpClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cpvp", "main"));
    private KeyMapping menu, totem;

    @Override
    public void onInitializeClient() {
        CpvpConfig.load();
        menu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cpvp.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_9, CATEGORY));
        totem = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cpvp.auto_totem", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (menu.consumeClick()) {
                if (c.player != null) c.gui.setScreen(new CpvpScreen());
            }
            while (totem.consumeClick()) {
                var settings = CpvpConfig.settings();
                settings.autoTotemEnabled = !settings.autoTotemEnabled;
                CpvpConfig.save();
                if (c.player != null) c.player.sendSystemMessage(Component.literal(
                        "[Cpvp] Auto Totem: " + (settings.autoTotemEnabled ? "ON" : "OFF")
                                + " (" + settings.autoTotemMode + ")"));
            }
            if (c.player != null && c.level != null && c.gameMode != null) {
                AutoTotemEngine.tick(c);
            }
        });
    }
}
