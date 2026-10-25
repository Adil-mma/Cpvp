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
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cpvp","main"));
    private KeyMapping menu,fast,crystal,anchor,safe,totem,macro;
    @Override public void onInitializeClient(){
        CpvpConfig.load();
        menu=bind("key.cpvp.open_menu",GLFW.GLFW_KEY_9);
        fast=bind("key.cpvp.fast_crystal",GLFW.GLFW_KEY_G);
        crystal=bind("key.cpvp.auto_crystal",GLFW.GLFW_KEY_H);
        anchor=bind("key.cpvp.auto_anchor",GLFW.GLFW_KEY_J);
        safe=bind("key.cpvp.safe_anchor",GLFW.GLFW_KEY_K);
        totem=bind("key.cpvp.auto_totem",GLFW.GLFW_KEY_L);
        macro=bind("key.cpvp.macro",GLFW.GLFW_KEY_B);
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(menu.consumeClick()&&c.player!=null)c.gui.setScreen(new CpvpScreen());
            toggle(fast,c,"Fast Crystal",0);toggle(crystal,c,"Auto Crystal",1);
            toggle(anchor,c,"Auto Anchor",2);toggle(safe,c,"Safe Anchor",3);toggle(totem,c,"Auto Totem",4);
            while(macro.consumeClick()){
                var s=CpvpConfig.settings();s.macroEnabled=true;CpvpConfig.save();CombatEngine.runMacro(c);
                if(c.player!=null)c.player.sendSystemMessage(Component.literal("[Cpvp] Macro executed"));
            }
            if(c.player!=null&&c.level!=null&&c.gameMode!=null){AutoTotemEngine.tick(c);CombatEngine.tick(c);}
        });
    }
    private static KeyMapping bind(String name,int key){return KeyMappingHelper.registerKeyMapping(new KeyMapping(name,InputConstants.Type.KEYSYM,key,CATEGORY));}
    private static void toggle(KeyMapping key,Minecraft c,String name,int n){
        while(key.consumeClick()){
            var s=CpvpConfig.settings();
            switch(n){case 0->s.fastCrystalEnabled=!s.fastCrystalEnabled;case 1->s.autoCrystalEnabled=!s.autoCrystalEnabled;
                case 2->s.autoAnchorEnabled=!s.autoAnchorEnabled;case 3->s.safeAnchorEnabled=!s.safeAnchorEnabled;case 4->s.autoTotemEnabled=!s.autoTotemEnabled;}
            CpvpConfig.save();
            if(c.player!=null){boolean on=switch(n){case 0->s.fastCrystalEnabled;case 1->s.autoCrystalEnabled;case 2->s.autoAnchorEnabled;case 3->s.safeAnchorEnabled;default->s.autoTotemEnabled;};
                c.player.sendSystemMessage(Component.literal("[Cpvp] "+name+": "+(on?"ON":"OFF")));}
        }
    }
}
