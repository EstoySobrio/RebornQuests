package dev.sobrio.rebornquests.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * The Dynasty visual language, ported to the 1.12.2 draw calls: a near-black surface, a gold frame
 * with L-shaped corner brackets and the same frame one size down on every button and field.
 *
 * <p>It extends Gui only to reach the shared rectangle helper; nothing here is a screen. The 1.20.1
 * original also swaps in two TTF faces, which 1.12.2 has no pipeline for, so bold gold caps carry the
 * display role instead.
 */
public final class Dynasty extends Gui {
    public static final int PANEL = 0xF018171D;
    public static final int GOLD = 0xFFE8C67A;
    public static final int GOLD_DIM = 0xFF8A6A22;
    public static final int TITLE = 0xFFE8C67A;
    public static final int ACCENT = 0xFFD9B86A;
    public static final int BODY = 0xFFE4D9BE;
    public static final int HINT = 0xFF9F978A;
    public static final int MUTED = 0xFF9E9E9E;
    public static final int GOOD = 0xFF7BD88F;
    public static final int WARN = 0xFFFFAA77;
    public static final int BAD = 0xFFFF7777;
    public static final int INFO = 0xFF74D8D8;
    public static final int BUTTON = 0xFF211F28;
    public static final int BUTTON_HOVER = 0xFF2C2937;
    public static final int BUTTON_DISABLED = 0xFF1A1920;
    public static final int FIELD = 0xFF121119;
    public static final int LIST = 0xE0101014;
    public static final int ROW = 0x55202024;
    public static final int ROW_SELECTED = 0xFF3B3425;
    public static final int TRACK = 0xFF37343A;

    /** Mission states, on the same colors the 1.20.1 board uses for the same meanings. */
    public static final int STATE_AVAILABLE = 0xFF63E06B;
    public static final int STATE_ACTIVE = 0xFF66D9E8;
    public static final int STATE_DONE = 0xFFA7A7A7;
    public static final int STATE_LOCKED = 0xFFFF6666;

    /**
     * The difficulty ramp. It stays inside the gold-and-parchment palette: sage for the lowest level and
     * crimson for the highest, with no saturated primaries, because a neon tag looks out of place on this
     * screen even though the same green reads fine as a state dot.
     */
    public static final int DIFF_D = 0xFFA9BEA0;
    public static final int DIFF_C = 0xFF8FB6C4;
    public static final int DIFF_B = 0xFFD9B86A;
    public static final int DIFF_A = 0xFFE09A5C;
    public static final int DIFF_S = 0xFFD96A6A;

    private Dynasty() {}

    /** Screen surface: fill, double gold frame and corner brackets. */
    public static void panel(int x, int y, int width, int height) {
        drawRect(x, y, x + width, y + height, PANEL);
        edge(x, y, width, height, 0, GOLD_DIM);
        edge(x, y, width, height, 1, GOLD);
        brackets(x, y, width, height, 6, 2);
    }

    /**
     * The frame of a panel with nothing inside it, for going around art that somebody else already drew:
     * a filled plate there would bury the controls it is meant to sit beside.
     */
    public static void frame(int x, int y, int width, int height) {
        edge(x, y, width, height, 0, GOLD_DIM);
        edge(x, y, width, height, 1, GOLD);
        brackets(x, y, width, height, 6, 2);
    }

    /** Button, field and list surface: the same frame, one pixel thick and with short brackets. */
    public static void box(int x, int y, int width, int height, int fill) {
        drawRect(x, y, x + width, y + height, fill);
        edge(x, y, width, height, 0, GOLD_DIM);
        brackets(x, y, width, height, 3, 1);
    }

    /** One row of a list; the gold bar marks the row that is selected. */
    public static void row(int x, int y, int width, int height, boolean selected, boolean hovered) {
        drawRect(x, y, x + width, y + height, selected ? ROW_SELECTED : hovered ? 0x772A2A31 : ROW);
        if (selected) drawRect(x, y, x + 2, y + height, GOLD);
    }

    /** Dim track with a gold thumb; thumbY and thumbHeight are already clamped by the caller. */
    public static void scrollbar(int x, int top, int track, int thumbY, int thumbHeight) {
        drawRect(x, top, x + 2, top + track, TRACK);
        drawRect(x, thumbY, x + 2, thumbY + Math.min(thumbHeight, track), GOLD);
    }

    /** Horizontal gold rule used under a section heading. */
    public static void rule(int x, int y, int width) {
        drawRect(x, y, x + width, y + 1, GOLD_DIM);
        drawRect(x + 1, y + 1, x + width - 1, y + 2, GOLD);
    }

    /** Dim track with a gold fill, used for mission progress. */
    public static void bar(int x, int y, int width, int height, float ratio, int fill) {
        drawRect(x, y, x + width, y + height, 0x80000000);
        edge(x, y, width, height, 0, TRACK);
        int inner = Math.round(Math.max(0F, Math.min(1F, ratio)) * (width - 2));
        if (inner > 0) drawRect(x + 1, y + 1, x + 1 + inner, y + height - 1, fill);
    }

    /** Vanilla's own button sprite reads like a settings menu, so every control is drawn here. */
    public static boolean button(FontRenderer fr, int x, int y, int width, int height, String label,
                                 int mx, int my, boolean enabled) {
        boolean hovered = enabled && hit(x, y, width, height, mx, my);
        box(x, y, width, height, enabled ? (hovered ? BUTTON_HOVER : BUTTON) : BUTTON_DISABLED);
        int color = !enabled ? 0xFF6E675C : hovered ? GOLD : BODY;
        String cut = fit(fr, label, width - 10);
        fr.drawStringWithShadow(cut, x + (width - fr.getStringWidth(cut)) / 2F, y + (height - 8) / 2F, color);
        return hovered;
    }

    public static boolean hit(int x, int y, int width, int height, int mx, int my) {
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    /** Plenty of time reads calm, the last minute reads red — the board and the clock agree on this. */
    public static int clockColor(int seconds) {
        if (seconds > 300) return INFO;
        if (seconds > 60) return WARN;
        return BAD;
    }

    /** D reads calm and S reads dangerous, following the order the staff set in the config. */
    public static int difficultyColor(String level) {
        if (level == null) return DIFF_D;
        if (level.equals("S")) return DIFF_S;
        if (level.equals("A")) return DIFF_A;
        if (level.equals("B")) return DIFF_B;
        if (level.equals("C")) return DIFF_C;
        return DIFF_D;
    }

    /** Gold-framed plate behind a vanilla text field, which insists on drawing its own box. */
    public static void fieldPlate(int x, int y, int width, int height, boolean focused) {
        box(x, y, width, height, FIELD);
        if (focused) edge(x + 2, y + 2, width - 4, height - 4, 0, GOLD);
    }

    /** Text cut to a pixel width, so a label never runs outside the frame it lives in. */
    public static String fit(FontRenderer fr, String value, int width) {
        if (value == null) return "";
        if (fr.getStringWidth(value) <= width) return value;
        String out = value;
        while (!out.isEmpty() && fr.getStringWidth(out + "…") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private static void edge(int x, int y, int width, int height, int inset, int color) {
        int left = x + inset, top = y + inset, right = x + width - inset, bottom = y + height - inset;
        drawRect(left, top, right, top + 1, color);
        drawRect(left, bottom - 1, right, bottom, color);
        drawRect(left, top, left + 1, bottom, color);
        drawRect(right - 1, top, right, bottom, color);
    }

    /** L-shaped marks in the four corners; they are what makes a Dynasty box recognisable. */
    private static void brackets(int x, int y, int width, int height, int arm, int inset) {
        int left = x + inset, top = y + inset, right = x + width - inset, bottom = y + height - inset;
        corner(left, top, 1, 1, arm);
        corner(right, top, -1, 1, arm);
        corner(left, bottom, 1, -1, arm);
        corner(right, bottom, -1, -1, arm);
    }

    /** One bracket: two 2px arms from the corner point, both growing toward the interior. */
    private static void corner(int x, int y, int dx, int dy, int arm) {
        drawRect(Math.min(x, x + arm * dx), Math.min(y, y + 2 * dy),
                Math.max(x, x + arm * dx), Math.max(y, y + 2 * dy), GOLD);
        drawRect(Math.min(x, x + 2 * dx), Math.min(y, y + arm * dy),
                Math.max(x, x + 2 * dx), Math.max(y, y + arm * dy), GOLD);
    }
}
