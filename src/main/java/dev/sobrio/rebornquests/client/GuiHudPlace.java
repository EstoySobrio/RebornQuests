package dev.sobrio.rebornquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import dev.sobrio.rebornquests.BoardData;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;

/**
 * Where the mission clock goes, chosen by the player himself inside the quest book. The plate is dragged
 * with the mouse, the six preset places are one click away, and every mission running with a time limit can
 * be kept on it or off it — the plate never grows past three lines, so the three that expire first are what
 * it shows.
 *
 * <p>Nothing here travels to the server: the choices are written to this client's own file the moment they
 * are made, which is why no permission is asked for and nothing needs a reload.
 */
public class GuiHudPlace extends GuiScreen {
    private static final int MARGIN = 12;
    private static final int ROW = 16;
    private static final int BUTTON = 16;
    /** The arrows nudge one pixel, or eight with Shift held. */
    private static final int KEY_UP = 200;
    private static final int KEY_DOWN = 208;
    private static final int KEY_LEFT = 203;
    private static final int KEY_RIGHT = 205;
    private static final int KEY_TAB = 15;
    private static final int KEY_ESCAPE = 1;

    private final List<int[]> presets = new ArrayList<int[]>();
    private final List<int[]> rows = new ArrayList<int[]>();
    private int[] offButton = new int[0];
    private int[] resetButton = new int[0];

    private int lastX;
    private int lastY;
    private boolean dragging;
    private int dragX;
    private int dragY;
    private int grabX;
    private int grabY;

    private int panelX;
    private int panelW = 210;
    private int listX;
    private int listW = 200;

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        int inner = this.width - 2 * MARGIN;
        panelW = Math.min(230, Math.max(150, inner / 3));
        panelX = MARGIN;
        listX = panelX + panelW + 8;
        listW = Math.max(140, this.width - MARGIN - listX);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.lastX = mouseX;
        this.lastY = mouseY;
        // The world stays visible on purpose: a clock has to be placed against the picture it will sit on.
        drawRect(0, 0, this.width, this.height, 0x60000000);
        if (this.dragging) {
            this.dragX = mouseX - this.grabX;
            this.dragY = mouseY - this.grabY;
        }
        ScaledResolution res = new ScaledResolution(this.mc);
        List<ClientState.Timed> shown = preview();
        int[] place = place(shown.size(), res);

        this.presets.clear();
        this.rows.clear();
        renderPresets();
        renderMissionList();
        renderPlate(res, shown, place);
        String back = "Esc para volver al tablon (se guarda solo)";
        draw(back, (this.width - this.fontRenderer.getStringWidth(back)) / 2F, this.height - 14, Dynasty.HINT);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The left plate: the six preset places, the switch and the way back to the pack default. */
    private void renderPresets() {
        Dynasty.panel(panelX - 4, 10, panelW + 8, this.height - 20);
        heading("COLOCAR EL RELOJ", panelX + 6, 18, panelW - 12);
        int y = 36;
        y = preset(y, HudSettings.TOP_LEFT, "Arriba izquierda");
        y = preset(y, HudSettings.TOP_RIGHT, "Arriba derecha");
        y = preset(y, HudSettings.MIDDLE_LEFT, "Centro izquierda");
        y = preset(y, HudSettings.MIDDLE_RIGHT, "Centro derecha");
        y = preset(y, HudSettings.BOTTOM_LEFT, "Abajo izquierda");
        y = preset(y, HudSettings.BOTTOM_RIGHT, "Abajo derecha");
        int mode = HudSettings.mode(ClientState.hudMode);
        y += 2;
        this.offButton = new int[] {panelX + 6, y, panelW - 12, BUTTON};
        Dynasty.button(this.fontRenderer, this.offButton[0], y, panelW - 12, BUTTON,
                mode == HudSettings.OFF ? "Reloj: apagado" : "Reloj: encendido", lastX, lastY, true);
        y += BUTTON + 4;
        this.resetButton = new int[] {panelX + 6, y, panelW - 12, BUTTON};
        Dynasty.button(this.fontRenderer, this.resetButton[0], y, panelW - 12, BUTTON,
                "Volver al defecto del server", lastX, lastY, true);
        y += BUTTON + 6;
        note("Arrastra la caja con el ratón. Las flechas mueven un pixel, con Mayus ocho y Tabulador salta "
                + "al siguiente sitio fijo.", panelX + 6, y, panelW - 12);
        if (mode == HudSettings.FREE) {
            draw("Colocacion propia: x " + dragOrSaved(0) + "  y " + dragOrSaved(1),
                    panelX + 6, this.height - 34, Dynasty.ACCENT);
        } else if (mode != HudSettings.OFF) {
            draw("Sitio fijo: se adapta al tamano de pantalla.", panelX + 6, this.height - 34, Dynasty.HINT);
        }
    }

    private int preset(int y, int mode, String label) {
        boolean active = HudSettings.mode(ClientState.hudMode) == mode;
        Dynasty.button(this.fontRenderer, panelX + 6, y, panelW - 12, BUTTON,
                (active ? "> " : "") + label, lastX, lastY, true);
        this.presets.add(new int[] {panelX + 6, y, panelW - 12, BUTTON, mode});
        return y + BUTTON + 2;
    }

    /** The right plate: every mission with a time limit, ticked when it is allowed on the clock. */
    private void renderMissionList() {
        Dynasty.box(listX - 4, 10, listW + 8, this.height - 20, Dynasty.LIST);
        heading("MISIONES EN EL RELOJ", listX + 4, 18, listW - 8);
        draw("§lCabe " + HudSettings.MAX_SHOWN + " como maximo: las que antes caducan.",
                listX + 4, 30, Dynasty.HINT);
        int row = 46;
        if (ClientState.timed.isEmpty()) {
            note("Ahora mismo no tienes ninguna mision con tiempo limite. Coloca la caja y se vera cuando "
                    + "aceptes una.", listX + 4, row, listW - 8);
            return;
        }
        for (ClientState.Timed entry : ClientState.timed) {
            if (row > this.height - 40) break;
            boolean visible = HudSettings.shown(entry.id);
            int[] box = new int[] {listX, row, listW - 4, ROW - 2, entry.id};
            Dynasty.row(box[0], row, box[2], ROW - 2, false,
                    Dynasty.hit(box[0], row, box[2], ROW - 2, lastX, lastY));
            draw(visible ? "§l[x]" : "[ ]", listX + 4, row + 3, visible ? Dynasty.ACCENT : Dynasty.MUTED);
            draw(Dynasty.fit(this.fontRenderer, entry.name, listW - 78), listX + 24, row + 3,
                    visible ? Dynasty.BODY : Dynasty.MUTED);
            String clock = entry.left() <= 0 ? "agotado" : BoardData.clock(entry.left());
            draw(clock, listX + listW - 8 - this.fontRenderer.getStringWidth(clock), row + 3,
                    Dynasty.clockColor(entry.left()));
            this.rows.add(box);
            row += ROW;
        }
    }

    /** The plate itself, with a gold outline showing that it is the thing being moved. */
    private void renderPlate(ScaledResolution res, List<ClientState.Timed> shown, int[] place) {
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
        int width = HudTimeLeft.width();
        int height = HudTimeLeft.height(shown.size());
        drawRect(place[0] - 3, place[1] - 3, place[0] + width + 3, place[1] + height + 3, 0x33E8C67A);
        Dynasty.box(place[0] - 2, place[1] - 2, width + 4, height + 4, 0x00000000);
        HudTimeLeft.plate(this.mc, place[0], place[1], shown);
        GlStateManager.popMatrix();
    }

    private int dragOrSaved(int index) {
        if (this.dragging) return index == 0 ? this.dragX : this.dragY;
        return index == 0 ? HudSettings.x() : HudSettings.y();
    }

    /** The plate preview: the missions that would show, or one made-up line when nothing is running. */
    private List<ClientState.Timed> preview() {
        List<ClientState.Timed> shown = HudTimeLeft.shown();
        if (!shown.isEmpty()) return shown;
        List<ClientState.Timed> out = new ArrayList<ClientState.Timed>();
        out.add(new ClientState.Timed(0, "Mision de ejemplo", "D", 0, 1,
                System.currentTimeMillis() + 1800L * 1000L));
        return out;
    }

    /** Where the plate is drawn: under the cursor while it is dragged, otherwise as it was saved. */
    private int[] place(int lines, ScaledResolution res) {
        int width = HudTimeLeft.width();
        int height = HudTimeLeft.height(lines);
        if (this.dragging) {
            return new int[] {clamp(this.dragX, res.getScaledWidth() - width),
                    clamp(this.dragY, res.getScaledHeight() - height)};
        }
        int mode = HudSettings.mode(ClientState.hudMode);
        if (mode == HudSettings.OFF) mode = HudSettings.MIDDLE_LEFT;
        return HudSettings.place(mode, width, height, res.getScaledWidth(), res.getScaledHeight());
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        for (int[] preset : this.presets) {
            if (!Dynasty.hit(preset[0], preset[1], preset[2], preset[3], mouseX, mouseY)) continue;
            click();
            HudSettings.setPreset(preset[4]);
            return;
        }
        if (hit(this.offButton, mouseX, mouseY)) {
            click();
            HudSettings.setPreset(HudSettings.mode(ClientState.hudMode) == HudSettings.OFF
                    ? HudSettings.MIDDLE_LEFT : HudSettings.OFF);
            return;
        }
        if (hit(this.resetButton, mouseX, mouseY)) {
            click();
            HudSettings.followServer();
            return;
        }
        for (int[] row : this.rows) {
            if (!Dynasty.hit(row[0], row[1], row[2], row[3], mouseX, mouseY)) continue;
            click();
            HudSettings.toggle(row[4]);
            return;
        }
        ScaledResolution res = new ScaledResolution(this.mc);
        List<ClientState.Timed> shown = preview();
        int[] plate = place(shown.size(), res);
        if (Dynasty.hit(plate[0] - 2, plate[1] - 2, HudTimeLeft.width() + 4,
                HudTimeLeft.height(shown.size()) + 4, mouseX, mouseY)) {
            this.dragging = true;
            this.grabX = mouseX - plate[0];
            this.grabY = mouseY - plate[1];
            this.dragX = plate[0];
            this.dragY = plate[1];
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.dragging) {
            ScaledResolution res = new ScaledResolution(this.mc);
            int[] plate = place(preview().size(), res);
            HudSettings.setPlacement(HudSettings.FREE, plate[0], plate[1]);
            this.dragging = false;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiMisiones());
            return;
        }
        int mode = HudSettings.mode(ClientState.hudMode);
        if (keyCode == KEY_TAB) {
            click();
            HudSettings.setPreset(nextPreset(mode));
            return;
        }
        int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? 8 : 1;
        int x = mode == HudSettings.FREE ? HudSettings.x() : 4;
        int y = mode == HudSettings.FREE ? HudSettings.y() : 4;
        if (keyCode == KEY_LEFT) x -= step;
        else if (keyCode == KEY_RIGHT) x += step;
        else if (keyCode == KEY_UP) y -= step;
        else if (keyCode == KEY_DOWN) y += step;
        else {
            super.keyTyped(typedChar, keyCode);
            return;
        }
        HudSettings.setPlacement(HudSettings.FREE, Math.max(0, x), Math.max(0, y));
    }

    /** Tab walks the six fixed places instead of dropping the plate back to the top-left corner. */
    private static int nextPreset(int mode) {
        if (mode == HudSettings.TOP_RIGHT) return HudSettings.MIDDLE_LEFT;
        if (mode == HudSettings.MIDDLE_LEFT) return HudSettings.MIDDLE_RIGHT;
        if (mode == HudSettings.MIDDLE_RIGHT) return HudSettings.BOTTOM_LEFT;
        if (mode == HudSettings.BOTTOM_LEFT) return HudSettings.BOTTOM_RIGHT;
        if (mode == HudSettings.BOTTOM_RIGHT) return HudSettings.TOP_LEFT;
        return HudSettings.TOP_LEFT;
    }

    private static boolean hit(int[] box, int mouseX, int mouseY) {
        return box.length == 4 && Dynasty.hit(box[0], box[1], box[2], box[3], mouseX, mouseY);
    }

    private void heading(String value, int x, int y, int width) {
        draw("§l" + value, x, y, Dynasty.TITLE);
        Dynasty.rule(x, y + 10, width);
    }

    private void note(String value, int x, int y, int width) {
        for (String line : this.fontRenderer.listFormattedStringToWidth(value, Math.max(40, width))) {
            draw(line, x, y, Dynasty.HINT);
            y += 10;
        }
    }

    private void draw(String value, float x, int y, int color) {
        this.fontRenderer.drawStringWithShadow(value, x, y, color);
    }

    private void click() {
        if (this.mc.player != null) {
            this.mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.25F, 1.0F);
        }
    }
}
