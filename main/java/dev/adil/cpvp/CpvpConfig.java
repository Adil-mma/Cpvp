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
        public boolean autoTotemEnabled;
        public int totemBackupSlot = 8;
        void sanitize() { totemBackupSlot = Math.max(0, Math.min(8, totemBackupSlot)); }
    }
}
