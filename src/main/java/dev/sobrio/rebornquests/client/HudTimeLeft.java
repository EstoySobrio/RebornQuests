package dev.sobrio.rebornquests.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.sobrio.rebornquests.BoardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The mission clock on a Dynasty plate: the missions in course, how many objectives each one has done and
 * what is left of its time limit. The server only names the seconds when a clock changes; this counts them
 * down itself, so the digits move between two updates.
 *
 * <p>It is drawn in two places. On the world, because a player who took a mission and walked away still
 * needs the clock, and on top of every Custom NPCs window, because that is where the missions live. Where
 * it sits, whether it shows at all and which of his missions it lists are the player's own choices, made
 * in the quest book and kept in his client file; three lines is all the plate ever grows to, and the three
 * most urgent ones are what it picks.
 */
public class HudTimeLeft {
    private static final int WIDTH = 152;
    private static final int HEADER = 14;
    private static final int ROW = 23;
    /** Every Custom NPCs window counts, the quest book the player opens and the editor staff use alike. */
    private static final String CNPC_PACKAGE = "noppes.npcs.";

    /** The world, with no window over it. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;
        draw(mc, event.getResolution());
    }

    /** Over a Custom NPCs screen, where the overlay event never reaches. */
    @SubscribeEvent
    public void onScreenDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!isCustomNpcs(event.getGui())) return;
        Minecraft mc = Minecraft.getMinecraft();
        draw(mc, new ScaledResolution(mc));
    }

    /** Leaving a world forgets the board and its clocks, so a new server never starts with stale lines. */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) ClientState.forget();
    }

    private static boolean isCustomNpcs(GuiScreen gui) {
        return gui != null && gui.getClass().getName().startsWith(CNPC_PACKAGE);
    }

    private static void draw(Minecraft mc, ScaledResolution res) {
        int mode = HudSettings.mode(ClientState.hudMode);
        if (mode == HudSettings.OFF) return;
        List<ClientState.Timed> shown = shown();
        if (shown.isEmpty()) return;
        int[] place = HudSettings.place(mode, WIDTH, height(shown.size()),
                res.getScaledWidth(), res.getScaledHeight());
        plate(mc, place[0], place[1], shown);
    }

    /** The plate itself, at a place the caller picked; the placement screen draws the very same box. */
    public static void plate(Minecraft mc, int x, int y, List<ClientState.Timed> shown) {
        FontRenderer fr = mc.fontRenderer;
        if (fr == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
        Dynasty.box(x, y, WIDTH, height(shown.size()), Dynasty.PANEL);
        fr.drawStringWithShadow("§lMISIONES EN CURSO", x + 5, y + 3, Dynasty.TITLE);
        Dynasty.rule(x + 4, y + HEADER - 3, WIDTH - 8);
        int row = y + HEADER;
        for (ClientState.Timed entry : shown) row = drawRow(fr, entry, x, row);
        GlStateManager.popMatrix();
    }

    /** How tall the plate is with that many lines, which is what the placement screen needs to clamp it. */
    public static int height(int lines) {
        return HEADER + Math.max(1, lines) * ROW + 2;
    }

    /** How wide the plate always is. */
    public static int width() {
        return WIDTH;
    }

    /** One mission: the difficulty it was set with, its name, the countdown and a bar of its objectives. */
    private static int drawRow(FontRenderer fr, ClientState.Timed entry, int x, int y) {
        String tag = "§l[" + entry.difficulty + "]";
        int left = entry.left();
        String clock = left <= 0 ? "agotado" : BoardData.clock(left);
        fr.drawStringWithShadow(tag, x + 5, y + 1, Dynasty.difficultyColor(entry.difficulty));
        int timeWidth = fr.getStringWidth(clock);
        fr.drawStringWithShadow(clock, x + WIDTH - 5 - timeWidth, y + 1, Dynasty.clockColor(left));
        int nameX = x + 8 + fr.getStringWidth(tag);
        int room = x + WIDTH - 8 - timeWidth - nameX;
        fr.drawStringWithShadow(Dynasty.fit(fr, entry.name, room), nameX, y + 1, Dynasty.BODY);
        if (entry.total > 0) {
            int barWidth = WIDTH - 42;
            Dynasty.bar(x + 5, y + 12, barWidth, 5, entry.done / (float) entry.total, Dynasty.ACCENT);
            fr.drawStringWithShadow(entry.done + "/" + entry.total, x + 10 + barWidth, y + 11, Dynasty.HINT);
        }
        return y + ROW;
    }

    /**
     * The lines the plate holds: the missions the player left visible, the soonest to expire first, and
     * never more than three. A clock that ran out stays until the server drops it on its next walk, so the
     * player sees the mission die instead of watching a line vanish for no reason.
     */
    public static List<ClientState.Timed> shown() {
        List<ClientState.Timed> out = new ArrayList<ClientState.Timed>();
        for (ClientState.Timed entry : ClientState.timed) {
            if (!HudSettings.shown(entry.id)) continue;
            out.add(entry);
        }
        Collections.sort(out, new Comparator<ClientState.Timed>() {
            @Override
            public int compare(ClientState.Timed a, ClientState.Timed b) {
                return Long.compare(a.endsAt, b.endsAt);
            }
        });
        while (out.size() > HudSettings.MAX_SHOWN) out.remove(out.size() - 1);
        return out;
    }
}
