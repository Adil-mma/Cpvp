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
    private KeyMapping menu, totem;
    private KeyMapping[] moduleKeys;

    @Override
    public void onInitializeClient() {
        CpvpConfig.load();
        menu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cpvp.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_9, CATEGORY));
        totem = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cpvp.auto_totem", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));
        moduleKeys = new KeyMapping[] {
                key("key.cpvp.fast_crystal", GLFW.GLFW_KEY_F6),
                key("key.cpvp.auto_crystal", GLFW.GLFW_KEY_F7),
                key("key.cpvp.hit_crystal", GLFW.GLFW_KEY_F8),
                key("key.cpvp.anchor_macro", GLFW.GLFW_KEY_F9),
                key("key.cpvp.safe_anchor", GLFW.GLFW_KEY_F10),
                key("key.cpvp.double_anchor", GLFW.GLFW_KEY_F11)
        };

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
            CpvpModule[] modules = {
                    CpvpModule.FAST_CRYSTAL, CpvpModule.AUTO_CRYSTAL, CpvpModule.HIT_CRYSTAL,
                    CpvpModule.ANCHOR_MACRO, CpvpModule.SAFE_ANCHOR, CpvpModule.DOUBLE_ANCHOR
            };
            for (int i = 0; i < moduleKeys.length; i++) {
                while (moduleKeys[i].consumeClick()) {
                    var settings = CpvpConfig.settings();
                    modules[i].setEnabled(settings, !modules[i].enabled(settings));
                    CpvpConfig.save();
                    if (c.player != null) c.player.sendSystemMessage(Component.literal(
                            "[Cpvp] " + modules[i].title + ": "
                                    + (modules[i].enabled(settings) ? "ON" : "OFF")));
                }
            }
            if (c.player != null && c.level != null && c.gameMode != null) {
                AutoTotemEngine.tick(c);
                CombatModuleEngine.tick(c);
            }
        });
    }

    private static KeyMapping key(String id, int keyCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                id, InputConstants.Type.KEYSYM, keyCode, CATEGORY));
    }
}
