package dev.adil.cpvp;

public enum CpvpModule {
    FAST_CRYSTAL("Fast Crystal", Group.CRYSTAL),
    AUTO_CRYSTAL("Auto Crystal", Group.CRYSTAL),
    HIT_CRYSTAL("Hit Crystal", Group.CRYSTAL),
    AUTO_TOTEM("Auto Totem", Group.CRYSTAL),
    ANCHOR_MACRO("Anchor Macro", Group.ANCHOR),
    SAFE_ANCHOR("Safe Anchor", Group.ANCHOR),
    DOUBLE_ANCHOR("Double Anchor", Group.ANCHOR);

    public enum Group { CRYSTAL, ANCHOR }

    public final String title;
    public final Group group;

    CpvpModule(String title, Group group) {
        this.title = title;
        this.group = group;
    }

    public boolean enabled(CpvpConfig.Settings s) {
        return switch (this) {
            case FAST_CRYSTAL -> s.fastCrystalEnabled;
            case AUTO_CRYSTAL -> s.autoCrystalEnabled;
            case HIT_CRYSTAL -> s.hitCrystalEnabled;
            case AUTO_TOTEM -> s.autoTotemEnabled;
            case ANCHOR_MACRO -> s.anchorMacroEnabled;
            case SAFE_ANCHOR -> s.safeAnchorEnabled;
            case DOUBLE_ANCHOR -> s.doubleAnchorEnabled;
        };
    }

    public void setEnabled(CpvpConfig.Settings s, boolean enabled) {
        if (enabled && group == Group.ANCHOR) {
            if (this != ANCHOR_MACRO) s.anchorMacroEnabled = false;
            if (this != SAFE_ANCHOR) s.safeAnchorEnabled = false;
            if (this != DOUBLE_ANCHOR) s.doubleAnchorEnabled = false;
        }
        switch (this) {
            case FAST_CRYSTAL -> s.fastCrystalEnabled = enabled;
            case AUTO_CRYSTAL -> s.autoCrystalEnabled = enabled;
            case HIT_CRYSTAL -> s.hitCrystalEnabled = enabled;
            case AUTO_TOTEM -> s.autoTotemEnabled = enabled;
            case ANCHOR_MACRO -> s.anchorMacroEnabled = enabled;
            case SAFE_ANCHOR -> s.safeAnchorEnabled = enabled;
            case DOUBLE_ANCHOR -> s.doubleAnchorEnabled = enabled;
        }
    }
}
