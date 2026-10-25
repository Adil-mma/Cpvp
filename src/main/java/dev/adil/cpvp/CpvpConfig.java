package dev.adil.cpvp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CpvpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cpvp.json");
    private static Settings settings = new Settings();
    private CpvpConfig() {}
    public static Settings settings() { return settings; }
    public static void load() {
        if (!Files.exists(FILE)) { save(); return; }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            settings = loaded == null ? new Settings() : loaded;
            settings.sanitize();
        } catch (IOException | RuntimeException e) { settings = new Settings(); save(); }
    }
    public static void save() {
        settings.sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) { GSON.toJson(settings, writer); }
        } catch (IOException e) { System.err.println("[Cpvp] Could not save config: " + e.getMessage()); }
    }
    public static final class Settings {
        public boolean fastCrystalEnabled, autoCrystalEnabled, autoAnchorEnabled, autoTotemEnabled, macroEnabled;
        public boolean safeAnchorEnabled = true;
        public int fastCrystalDelayTicks = 0, autoCrystalDelayTicks = 2, autoAnchorDelayTicks = 3;
        public int totemBackupSlot = 8, macroMode = 0;
        public double targetRange = 5.0, safeAnchorDistance = 4.5;
        void sanitize() {
            fastCrystalDelayTicks=clamp(fastCrystalDelayTicks,0,10);
            autoCrystalDelayTicks=clamp(autoCrystalDelayTicks,0,10);
            autoAnchorDelayTicks=clamp(autoAnchorDelayTicks,0,10);
            totemBackupSlot=clamp(totemBackupSlot,0,8); macroMode=clamp(macroMode,0,1);
            targetRange=Math.max(3,Math.min(8,targetRange));
            safeAnchorDistance=Math.max(2,Math.min(8,safeAnchorDistance));
        }
        private static int clamp(int n,int a,int b) { return Math.max(a,Math.min(b,n)); }
    }
}
