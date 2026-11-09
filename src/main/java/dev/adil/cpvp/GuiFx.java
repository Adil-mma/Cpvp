package dev.adil.cpvp;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Small shared GUI helpers: pop-in animation, hover-delay tooltips and the description texts. */
final class GuiFx {
    private static final long POP_NANOS = 150_000_000L;
    private static final long TOOLTIP_DELAY_NANOS = 1_000_000_000L;

    private final long openedAt = System.nanoTime();
    private int hoverKey = Integer.MIN_VALUE;
    private long hoverSince;

    /** 0..1 with ease-out, reaching 1 after about 150 ms. */
    float progress() {
        float t = Math.min(1f, (System.nanoTime() - openedAt) / (float) POP_NANOS);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Scales everything drawn until {@link #end} from 92% to 100% around the given centre. */
    void begin(GuiGraphicsExtractor g, float cx, float cy) {
        float p = progress();
        float s = 0.92f + 0.08f * p;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s, s);
        g.pose().translate(-cx, -cy);
    }

    void end(GuiGraphicsExtractor g) { g.pose().popMatrix(); }

    /** Fades a colour's alpha in with the pop animation. */
    int fade(int argb) {
        int a = Math.round(((argb >>> 24) & 0xFF) * progress());
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Tracks what is hovered; true once the same thing has been hovered for over one second. */
    boolean hoverReady(int key) {
        long now = System.nanoTime();
        if (key != hoverKey) {
            hoverKey = key;
            hoverSince = now;
        }
        return key != -1 && now - hoverSince >= TOOLTIP_DELAY_NANOS;
    }

    static void tooltip(GuiGraphicsExtractor g, Font font, String text, int mx, int my, int sw, int sh) {
        int maxW = 190;
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(next) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());

        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() * (font.lineHeight + 1) + 8;
        int bw = w + 10;
        int bx = Math.max(2, Math.min(mx + 10, sw - bw - 2));
        int by = Math.max(2, Math.min(my + 14, sh - h - 2));
        g.fill(bx, by, bx + bw, by + h, 0xF2101218);
        g.outline(bx, by, bw, h, 0xFF4D5668);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 2, 0xFF55B9E6);
        int ty = by + 5;
        for (String l : lines) {
            g.text(font, Component.literal(l), bx + 5, ty, 0xFFD6DCE8, false);
            ty += font.lineHeight + 1;
        }
    }

    static String module(CpvpModule m) {
        return switch (m) {
            case FAST_CRYSTAL -> "Places crystals on the obsidian or bedrock under your crosshair with a short delay. Right-click still works for golden apples, pearls and shields.";
            case AUTO_CRYSTAL -> "While right-click is held, places a crystal on the base under the crosshair and hits crystals. The camera only turns toward a crystal already standing on a base.";
            case HIT_CRYSTAL -> "While right-click is held, hits the End Crystal under your crosshair when it is in range.";
            case AUTO_TOTEM -> "Keeps a totem in your offhand and in a backup slot. AUTO restocks by itself, MANUAL swaps the totem you hover in your inventory.";
            case ANCHOR_MACRO -> "You place the anchor. Hold right-click on it to charge it with glowstone and detonate it from your chosen slot.";
            case SAFE_ANCHOR -> "You place the anchor. It charges it, blocks the front with a glowstone, switches to your detonation slot and explodes it.";
            case DOUBLE_ANCHOR -> "Runs the anchor sequence twice in a row: charge, detonate, place the next anchor, charge and detonate again.";
            case TOTEM_SWAP -> "When you hit another player it swaps to your best sword and back. Ignores axes, pickaxes, air and mobs.";
        };
    }

    static String setting(String label) {
        return switch (label) {
            case "Toggle key" -> "Key or mouse button that switches this module on and off in-game. Click, then press the key.";
            case "Delay", "Action delay" -> "Pause between actions in ticks (1 tick is about 50 ms). A random value between the two handles is picked each time. 0 or 1 means the next tick.";
            case "Stage delay" -> "Pause between the stages of one sequence, in ticks. A random value between the two handles is picked each time.";
            case "Swap delay" -> "How many ticks the sword stays selected before your previous slot is restored.";
            case "Hold use only" -> "Only acts while you hold right-click.";
            case "Target range", "Hit range" -> "Maximum distance in blocks to the crystal.";
            case "Stop above" -> "Do nothing when your eyes are above the target block.";
            case "Mode" -> "AUTO restocks automatically. MANUAL only acts on the totem you hover while your inventory is open.";
            case "Backup slot" -> "Hotbar slot (1-9) that is kept stocked with a spare totem.";
            case "Auto-charge" -> "Charge the anchor with glowstone before detonating it.";
            case "Detonator slot" -> "Hotbar slot (1-9) that is selected to detonate the charged anchor.";
            case "Detonation slot" -> "Hotbar slot (1-9) that is selected to detonate the charged anchor, normally your totem.";
            case "Cover distance" -> "Only block the front with glowstone when you are closer than this many blocks to the anchor.";
            case "Place cover" -> "Place one glowstone between you and the anchor before it explodes.";
            case "Crouch cover" -> "Crouch, place the cover glowstone on the anchor face you are looking at without moving the camera, then stand up.";
            case "Anchor slot" -> "Hotbar slot (1-9) holding the respawn anchors for the next pass.";
            default -> "";
        };
    }
}
