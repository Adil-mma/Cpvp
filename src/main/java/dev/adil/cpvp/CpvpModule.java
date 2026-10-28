package dev.adil.cpvp;

public enum CpvpModule {
    FAST_CRYSTAL("Fast Crystal", Group.CRYSTAL),
    AUTO_CRYSTAL("Auto Crystal", Group.CRYSTAL),
    HIT_CRYSTAL("Hit Crystal", Group.CRYSTAL),
    AUTO_TOTEM("Auto Totem", Group.CRYSTAL),
    ANCHOR_MACRO("Anchor Macro", Group.ANCHOR),
    SAFE_ANCHOR("Safe Anchor", Group.ANCHOR),
    DOUBLE_ANCHOR("Double Anchor", Group.ANCHOR),
    TOTEM_SWAP("Totem Swap", Group.ANCHOR);

    public enum Group { CRYSTAL, ANCHOR, UTILITY }

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
            case TOTEM_SWAP -> s.totemSwapEnabled;
        };
    }

    public void setEnabled(CpvpConfig.Settings s, boolean enabled) {
        // The three anchor macros conflict with one another, but Totem Swap is
        // independent and must not disable an anchor macro when toggled on.
        if (enabled && (this == ANCHOR_MACRO || this == SAFE_ANCHOR || this == DOUBLE_ANCHOR)) {
            if (this != ANCHOR_MACRO) s.anchorMacroEnabled = false;
            if (this != SAFE_ANCHOR) s.safeAnchorEnabled = false;
            if (this != DOUBLE_ANCHOR) s.doubleAnchorEnabled = false;
        }
        if (enabled && this == AUTO_CRYSTAL) s.hitCrystalEnabled = false;
        if (enabled && this == HIT_CRYSTAL) s.autoCrystalEnabled = false;
        switch (this) {
            case FAST_CRYSTAL -> s.fastCrystalEnabled = enabled;
            case AUTO_CRYSTAL -> s.autoCrystalEnabled = enabled;
            case HIT_CRYSTAL -> s.hitCrystalEnabled = enabled;
            case AUTO_TOTEM -> s.autoTotemEnabled = enabled;
            case ANCHOR_MACRO -> s.anchorMacroEnabled = enabled;
            case SAFE_ANCHOR -> s.safeAnchorEnabled = enabled;
            case DOUBLE_ANCHOR -> s.doubleAnchorEnabled = enabled;
            case TOTEM_SWAP -> s.totemSwapEnabled = enabled;
        }
    }

    public int bind(CpvpConfig.Settings s) {
        return switch (this) {
            case FAST_CRYSTAL -> s.fastCrystalBind;
            case AUTO_CRYSTAL -> s.autoCrystalBind;
            case HIT_CRYSTAL -> s.hitCrystalBind;
            case AUTO_TOTEM -> s.autoTotemBind;
            case ANCHOR_MACRO -> s.anchorMacroBind;
            case SAFE_ANCHOR -> s.safeAnchorBind;
            case DOUBLE_ANCHOR -> s.doubleAnchorBind;
            case TOTEM_SWAP -> s.totemSwapBind;
        };
    }

    public void setBind(CpvpConfig.Settings s, int key) {
        switch (this) {
            case FAST_CRYSTAL -> s.fastCrystalBind = key;
            case AUTO_CRYSTAL -> s.autoCrystalBind = key;
            case HIT_CRYSTAL -> s.hitCrystalBind = key;
            case AUTO_TOTEM -> s.autoTotemBind = key;
            case ANCHOR_MACRO -> s.anchorMacroBind = key;
            case SAFE_ANCHOR -> s.safeAnchorBind = key;
            case DOUBLE_ANCHOR -> s.doubleAnchorBind = key;
            case TOTEM_SWAP -> s.totemSwapBind = key;
        }
    }
}
