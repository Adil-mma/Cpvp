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
        public boolean totemSwapEnabled;

        // Keyboard codes are GLFW key codes. Values <= -2 encode mouse buttons; -1 means unbound.
        public int fastCrystalBind = -1;
        public int autoCrystalBind = -1;
        public int hitCrystalBind = -1;
        public int autoTotemBind = -1;
        public int anchorMacroBind = -1;
        public int safeAnchorBind = -1;
        public int doubleAnchorBind = -1;
        public int totemSwapBind = -1;

        public String autoTotemMode = "AUTO";
        public int totemBackupSlot = 8;

        public int fastCrystalMinDelayTicks = 1;
        public int fastCrystalMaxDelayTicks = 2;
        public boolean fastCrystalOnlyWhileHolding = true;

        public double autoCrystalRange = 4.0;
        public int autoCrystalMinDelayTicks = 1;
        public int autoCrystalMaxDelayTicks = 2;
        public boolean autoCrystalStopAboveTarget = true;

        public double hitCrystalRange = 4.0;
        public int hitCrystalMinDelayTicks = 0;
        public int hitCrystalMaxDelayTicks = 1;
        public boolean hitCrystalStopAboveTarget = true;

        public int anchorMacroMinDelayTicks = 1;
        public int anchorMacroMaxDelayTicks = 2;
        public int anchorMacroMinStageDelayTicks = 1;
        public int anchorMacroMaxStageDelayTicks = 2;
        public int anchorDetonatorSlot = 0;
        public boolean anchorMacroAutoCharge = true;

        public int safeAnchorMinDelayTicks = 1;
        public int safeAnchorMaxDelayTicks = 2;
        public double safeAnchorSelfDistance = 5.0;
        public boolean safeAnchorProtectSelf = true;
        /** Hotbar index used to detonate a protected anchor with a totem (default slot 9). */
        public int safeAnchorTotemSlot = 8;

        public int doubleAnchorMinDelayTicks = 1;
        public int doubleAnchorMaxDelayTicks = 2;
        public int doubleAnchorMinStageDelayTicks = 1;
        public int doubleAnchorMaxStageDelayTicks = 2;
        public int doubleAnchorPasses = 2;

        public int totemSwapMinDelayTicks = 1;
        public int totemSwapMaxDelayTicks = 2;

        void sanitize() {
            if (autoTotemMode == null) autoTotemMode = "AUTO";
            autoTotemMode = autoTotemMode.equalsIgnoreCase("MANUAL") ? "MANUAL" : "AUTO";
            totemBackupSlot = clamp(totemBackupSlot, 0, 8);
            anchorDetonatorSlot = clamp(anchorDetonatorSlot, 0, 8);
            doubleAnchorPasses = clamp(doubleAnchorPasses, 2, 4);
            autoCrystalRange = clamp(autoCrystalRange, 1.0, 6.0);
            hitCrystalRange = clamp(hitCrystalRange, 1.0, 8.0);
            safeAnchorSelfDistance = clamp(safeAnchorSelfDistance, 2.0, 8.0);
            safeAnchorTotemSlot = clamp(safeAnchorTotemSlot, 0, 8);

            fastCrystalBind = clampBind(fastCrystalBind);
            autoCrystalBind = clampBind(autoCrystalBind);
            hitCrystalBind = clampBind(hitCrystalBind);
            autoTotemBind = clampBind(autoTotemBind);
            anchorMacroBind = clampBind(anchorMacroBind);
            safeAnchorBind = clampBind(safeAnchorBind);
            doubleAnchorBind = clampBind(doubleAnchorBind);
            totemSwapBind = clampBind(totemSwapBind);

            normalizeRange(fastCrystalMinDelayTicks, fastCrystalMaxDelayTicks, (a, b) -> {
                fastCrystalMinDelayTicks = a; fastCrystalMaxDelayTicks = b;
            });
            normalizeRange(autoCrystalMinDelayTicks, autoCrystalMaxDelayTicks, (a, b) -> {
                autoCrystalMinDelayTicks = a; autoCrystalMaxDelayTicks = b;
            });
            normalizeRange(hitCrystalMinDelayTicks, hitCrystalMaxDelayTicks, (a, b) -> {
                hitCrystalMinDelayTicks = a; hitCrystalMaxDelayTicks = b;
            });
            normalizeRange(anchorMacroMinDelayTicks, anchorMacroMaxDelayTicks, (a, b) -> {
                anchorMacroMinDelayTicks = a; anchorMacroMaxDelayTicks = b;
            });
            normalizeRange(anchorMacroMinStageDelayTicks, anchorMacroMaxStageDelayTicks, (a, b) -> {
                anchorMacroMinStageDelayTicks = a; anchorMacroMaxStageDelayTicks = b;
            });
            normalizeRange(safeAnchorMinDelayTicks, safeAnchorMaxDelayTicks, (a, b) -> {
                safeAnchorMinDelayTicks = a; safeAnchorMaxDelayTicks = b;
            });
            normalizeRange(doubleAnchorMinDelayTicks, doubleAnchorMaxDelayTicks, (a, b) -> {
                doubleAnchorMinDelayTicks = a; doubleAnchorMaxDelayTicks = b;
            });
            normalizeRange(doubleAnchorMinStageDelayTicks, doubleAnchorMaxStageDelayTicks, (a, b) -> {
                doubleAnchorMinStageDelayTicks = a; doubleAnchorMaxStageDelayTicks = b;
            });
            normalizeRange(totemSwapMinDelayTicks, totemSwapMaxDelayTicks, (a, b) -> {
                totemSwapMinDelayTicks = a; totemSwapMaxDelayTicks = b;
            });
        }

        private static int clampBind(int v) {
            if (v == -1 || (v <= -2 && v >= -9)) return v;
            return clamp(v, 0, 348);
        }

        private static void normalizeRange(int min, int max, RangeConsumer consumer) {
            int a = clamp(min, 0, 5), b = clamp(max, 0, 5);
            if (a > b) { int t = a; a = b; b = t; }
            consumer.accept(a, b);
        }

        @FunctionalInterface
        private interface RangeConsumer { void accept(int min, int max); }

        private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
        private static double clamp(double v, double min, double max) {
            if (!Double.isFinite(v)) return min;
            return Math.max(min, Math.min(max, v));
        }
    }
}
