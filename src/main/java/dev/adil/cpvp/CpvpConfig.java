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
        } catch (IOException | RuntimeException e) {
            settings = new Settings();
            save();
        }
    }

    public static void save() {
        settings.sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) { GSON.toJson(settings, writer); }
        } catch (IOException e) {
            System.err.println("[Cpvp] Could not save config: " + e.getMessage());
        }
    }

    public static final class Settings {
        public boolean fastCrystalEnabled;
        public boolean autoCrystalEnabled;
        public boolean hitCrystalEnabled;
        public boolean autoTotemEnabled;
        public boolean anchorMacroEnabled;
        public boolean safeAnchorEnabled;
        public boolean doubleAnchorEnabled;

        public String autoTotemMode = "AUTO";
        public int totemBackupSlot = 8;

        public int fastCrystalDelayMs = 50;
        public boolean fastCrystalOnlyWhileHolding = true;

        public double autoCrystalRange = 4.0;
        public int autoCrystalDelayMs = 100;
        public boolean autoCrystalStopAboveTarget = true;

        public double hitCrystalRange = 4.0;
        public int hitCrystalDelayMs = 25;
        public boolean hitCrystalStopAboveTarget = true;

        public int anchorMacroDelayMs = 100;
        public int anchorStageDelayMs = 50;
        public int anchorDetonatorSlot = 0;
        public boolean anchorMacroAutoCharge = true;

        public int safeAnchorDelayMs = 100;
        public double safeAnchorSelfDistance = 5.0;
        public boolean safeAnchorProtectSelf = true;

        public int doubleAnchorDelayMs = 100;
        public int doubleAnchorPasses = 2;

        void sanitize() {
            if (autoTotemMode == null) autoTotemMode = "AUTO";
            autoTotemMode = autoTotemMode.equalsIgnoreCase("MANUAL") ? "MANUAL" : "AUTO";
            totemBackupSlot = clamp(totemBackupSlot, 0, 8);
            fastCrystalDelayMs = clamp(fastCrystalDelayMs, 0, 500);
            autoCrystalRange = clamp(autoCrystalRange, 1.0, 6.0);
            autoCrystalDelayMs = clamp(autoCrystalDelayMs, 0, 1000);
            hitCrystalRange = clamp(hitCrystalRange, 1.0, 8.0);
            hitCrystalDelayMs = clamp(hitCrystalDelayMs, 0, 500);
            anchorMacroDelayMs = clamp(anchorMacroDelayMs, 0, 1000);
            anchorStageDelayMs = clamp(anchorStageDelayMs, 0, 1000);
            anchorDetonatorSlot = clamp(anchorDetonatorSlot, 0, 8);
            safeAnchorDelayMs = clamp(safeAnchorDelayMs, 0, 1000);
            safeAnchorSelfDistance = clamp(safeAnchorSelfDistance, 2.0, 8.0);
            doubleAnchorDelayMs = clamp(doubleAnchorDelayMs, 0, 500);
            doubleAnchorPasses = clamp(doubleAnchorPasses, 2, 4);
        }

        private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
        private static double clamp(double v, double min, double max) {
            if (!Double.isFinite(v)) return min;
            return Math.max(min, Math.min(max, v));
        }
    }
}
