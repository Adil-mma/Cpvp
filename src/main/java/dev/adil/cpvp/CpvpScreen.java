package dev.adil.cpvp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CpvpScreen extends Screen {
    public CpvpScreen(){super(Component.literal("Cpvp • PvP"));}
    @Override protected void init(){
        int w=330,h=260,left=(width-w)/2,top=(height-h)/2;var s=CpvpConfig.settings();
        addRenderableWidget(Button.builder(label("Fast Crystal",s.fastCrystalEnabled),b->{s.fastCrystalEnabled=!s.fastCrystalEnabled;CpvpConfig.save();b.setMessage(label("Fast Crystal",s.fastCrystalEnabled));}).bounds(left+12,top+36,148,22).build());
        addRenderableWidget(Button.builder(label("Auto Crystal",s.autoCrystalEnabled),b->{s.autoCrystalEnabled=!s.autoCrystalEnabled;CpvpConfig.save();b.setMessage(label("Auto Crystal",s.autoCrystalEnabled));}).bounds(left+170,top+36,148,22).build());
        addRenderableWidget(Button.builder(label("Auto Anchor",s.autoAnchorEnabled),b->{s.autoAnchorEnabled=!s.autoAnchorEnabled;CpvpConfig.save();b.setMessage(label("Auto Anchor",s.autoAnchorEnabled));}).bounds(left+12,top+64,148,22).build());
        addRenderableWidget(Button.builder(label("Safe Anchor",s.safeAnchorEnabled),b->{s.safeAnchorEnabled=!s.safeAnchorEnabled;CpvpConfig.save();b.setMessage(label("Safe Anchor",s.safeAnchorEnabled));}).bounds(left+170,top+64,148,22).build());
        addRenderableWidget(Button.builder(label("Auto Totem",s.autoTotemEnabled),b->{s.autoTotemEnabled=!s.autoTotemEnabled;CpvpConfig.save();b.setMessage(label("Auto Totem",s.autoTotemEnabled));}).bounds(left+12,top+92,148,22).build());
        addRenderableWidget(Button.builder(label("Macro enabled",s.macroEnabled),b->{s.macroEnabled=!s.macroEnabled;CpvpConfig.save();b.setMessage(label("Macro enabled",s.macroEnabled));}).bounds(left+170,top+92,148,22).build());
        addRenderableWidget(Button.builder(Component.literal("Fast Crystal delay: "+s.fastCrystalDelayTicks+"t"),b->{s.fastCrystalDelayTicks=(s.fastCrystalDelayTicks+1)%11;CpvpConfig.save();b.setMessage(Component.literal("Fast Crystal delay: "+s.fastCrystalDelayTicks+"t"));}).bounds(left+12,top+128,148,22).build());
        addRenderableWidget(Button.builder(Component.literal("Crystal delay: "+s.autoCrystalDelayTicks+"t"),b->{s.autoCrystalDelayTicks=(s.autoCrystalDelayTicks+1)%11;CpvpConfig.save();b.setMessage(Component.literal("Crystal delay: "+s.autoCrystalDelayTicks+"t"));}).bounds(left+170,top+128,148,22).build());
        addRenderableWidget(Button.builder(Component.literal("Anchor delay: "+s.autoAnchorDelayTicks+"t"),b->{s.autoAnchorDelayTicks=(s.autoAnchorDelayTicks+1)%11;CpvpConfig.save();b.setMessage(Component.literal("Anchor delay: "+s.autoAnchorDelayTicks+"t"));}).bounds(left+12,top+156,148,22).build());
        addRenderableWidget(Button.builder(Component.literal("Totem slot: "+(s.totemBackupSlot+1)),b->{s.totemBackupSlot=(s.totemBackupSlot+1)%9;CpvpConfig.save();b.setMessage(Component.literal("Totem slot: "+(s.totemBackupSlot+1));}).bounds(left+170,top+156,148,22).build());
        addRenderableWidget(Button.builder(Component.literal("Macro mode: "+(s.macroMode==0?"Crystal":"Anchor")),b->{s.macroMode=(s.macroMode+1)%2;CpvpConfig.save();b.setMessage(Component.literal("Macro mode: "+(s.macroMode==0?"Crystal":"Anchor")));}).bounds(left+12,top+184,148,22).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(left+170,top+184,148,22).build());
    }
    private static Component label(String n,boolean on){return Component.literal(n+": "+(on?"ON":"OFF"));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float pt){
        g.fill(0,0,width,height,0xB0000000);int w=330,h=260,l=(width-w)/2,t=(height-h)/2;
        g.fill(l,t,l+w,t+h,0xF0121720);g.outline(l,t,w,h,0xFF4AA3FF);
        g.text(font,Component.literal("CPVP • PvP modules"),l+12,t+12,0xFF75C7FF,true);
        super.extractRenderState(g,mx,my,pt);
    }
}
