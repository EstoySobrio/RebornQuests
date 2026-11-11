package dev.sobrio.rebornquests.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.sobrio.rebornquests.AdminAction;
import dev.sobrio.rebornquests.BoardData;
import dev.sobrio.rebornquests.RebornQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The editor of Custom NPCs drawn again, which is what was asked for instead of a redesign of their screens:
 * their panel keeps its size, their controls keep their places, their ids and their behaviour are untouched,
 * and everything the mod lays on top of them is its own — the surface behind, the frame, the strip that says
 * who is being edited and what he is, the re-painted controls, the gold edge around their fields and the block
 * that says which mission boards this NPC opens.
 *
 * <p>Nothing of Custom NPCs is written into. Their values are read, the answer of the mod comes back on the
 * channel that brings no screen with it, and the only thing that changes is this mod's own list of NPCs per
 * panel — so a dialogue, a script or a trade of the NPC stays exactly as it was made.
 *
 * <p>The controls of the block are laid into their list again every frame. Their editor rebuilds that list
 * whenever a role or a job is switched, and it rebuilds it without asking Forge, which is how a control hung
 * on it once stopped answering as soon as the staff touched the role. The role, the job and the name are read
 * from the entity every frame as well, so what the strip says moves with what the editor is holding.
 */
public class NpcEditorUi {
    private static final String CNPC = "noppes.npcs.client.gui.";
    private static final String OURS = "dev.sobrio.rebornquests.";

    /** Our own ids, above anything their editor hands to its controls, so their handlers pass over them. */
    private static final int TAB = 900;
    private static final int FIRST_ROW = 901;
    private static final int LAST_ROW = FIRST_ROW + 8;
    private static final int PAGE_BACK = 931;
    private static final int PAGE_NEXT = 932;
    private static final int CLOSE = 933;

    private static final int TOGGLE_W = 132;
    private static final int BAR_H = 16;
    private static final int SHEET_W = 208;
    private static final int HEAD_H = 24;
    private static final int ROW_H = 12;
    private static final int FOOT_H = 15;
    private static final int MAX_ROWS = 9;
    private static final int SIDE = 12;

    /** The frames this class waits before asking again for an answer that never came. */
    private static final int RETRY = 60;

    /** The roles and the jobs of Custom NPCs, in the order their own editor steps through them. */
    private static final String[] ROLES = {"ninguno", "comerciante", "seguidor", "banco", "transporte",
            "cartero", "companero", "dialogo"};
    private static final String[] JOBS = {"ninguno", "bardo", "sanador", "guardian", "dador de objetos",
            "seguidor", "generador", "conversacion", "cargachunks", "titere", "constructor", "granjero"};

    /** The value of a field this version does not have, which is the one case the strip admits it cannot read. */
    private static final int UNREAD = -2;

    private GuiScreen screen;
    private List<GuiButton> widgets;
    private Control tab;
    /** Their panel is read once per editor, and their map of fields with it, instead of once per frame. */
    private boolean placed;
    private Field fieldMap;

    /** The NPC being edited and the two numbers of his own data that say what he is. */
    private Entity target;
    private Object advanced;
    private Field roleField;
    private Field jobField;

    private String npc = "";
    private String uuid = "";
    private int role = UNREAD;
    private int job = UNREAD;
    private boolean seen;

    private int left, top, wide, high, screenW, screenH;
    private int barX, barY, popX, popY;
    private int rows = MAX_ROWS;
    private int page;
    private int stamp = -1;
    private int wait;
    private boolean open;
    private int mx = -1;
    private int my = -1;

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        forget();
    }

    /** The void behind their editor takes the surface colour of every other screen of the pack. */
    @SubscribeEvent
    public void onBackground(GuiScreenEvent.BackgroundDrawnEvent event) {
        GuiScreen gui = event.getGui();
        if (!isCnpc(gui)) return;
        gui.drawRect(0, 0, gui.width, gui.height, Dynasty.PANEL);
    }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        List<GuiButton> list = event.getButtonList();
        forget();
        if (!isCnpc(gui) || list == null) return;
        screen = gui;
        widgets = list;
        target = edited(gui);
        if (target == null) return;
        npc = trim(target.getName());
        uuid = String.valueOf(target.getUniqueID());
        bind(target);
        tab = new Control(TAB, 0, 0, TOGGLE_W, BAR_H, "");
        ask();
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (gui == null || !isCnpc(gui) || widgets == null || gui != screen) return;
        // Their menus of NPCs have no panel to sit inside; their controls are painted either way.
        boolean known = placed || place(gui);
        placed = known;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        mx = event.getMouseX();
        my = event.getMouseY();
        BoardData data = ClientState.roleData;
        follow();
        if (known) layout(data);
        inject(data);
        for (GuiButton widget : widgets) paint(widget, fr);
        if (!known) return;
        frame(gui);
        head(fr);
        bar(fr, data);
        if (open) sheet(fr, data);
    }

    /**
     * Their editor routes its own clicks, so the presses of our ids arrive here already sorted out. A press
     * of one of theirs only asks that ours be laid down again, because their handler may rebuild its list
     * behind our back — a role, a job, a page of dialogue.
     */
    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Post event) {
        GuiButton pressed = event.getButton();
        if (pressed == null || event.getGui() != screen) return;
        // Their own list, taken again from the event: an editor that rebuilt it has just handed us the new one.
        List<GuiButton> live = event.getButtonList();
        if (live != null) widgets = live;
        if (pressed.id < TAB || tab == null || widgets == null) {
            stamp = -1;
            // A press of one of theirs is read again here instead of on the next frame: a role or a job has
            // just moved and the question about it goes out now, not after the screen has been drawn.
            follow();
            return;
        }
        BoardData data = ClientState.roleData;
        int id = pressed.id;
        if (id == TAB) {
            open = !open;
            if (open) {
                page = 0;
                ask();
            }
            stamp = -1;
            return;
        }
        if (!open) return;
        if (id == CLOSE) {
            open = false;
            stamp = -1;
            return;
        }
        if (id == PAGE_BACK || id == PAGE_NEXT) {
            page = clamp(page + (id == PAGE_NEXT ? 1 : -1), 0, lastPage(data));
            stamp = -1;
            return;
        }
        if (id >= FIRST_ROW && id <= LAST_ROW) {
            int at = page * rows + (id - FIRST_ROW);
            if (data != null && data.panels != null && at < data.panels.size()) toggle(data.panels.get(at));
        }
    }

    // --------------------------------------------------------------------------------------------- the bands

    /** The strip above their panel: who is being edited, and what the editor is holding him as right now. */
    private void head(FontRenderer fr) {
        if (target == null || top < 14) return;
        String title = "EDITOR DE NPC";
        String tail = "Rol: " + label(ROLES, role) + "   Trabajo: " + label(JOBS, job);
        int room = wide - fr.getStringWidth(title) - fr.getStringWidth(tail) - 26;
        if (room < 30) return;
        int y = top - 11;
        fr.drawStringWithShadow(title, left, y, Dynasty.TITLE);
        fr.drawStringWithShadow(Dynasty.fit(fr, npc, room), left + fr.getStringWidth(title) + 6, y + 1, Dynasty.BODY);
        fr.drawStringWithShadow(tail, left + wide - fr.getStringWidth(tail), y + 1, Dynasty.INFO);
    }

    /** Our toolbar under their panel, which is the only place the block of the mod starts from. */
    private void bar(FontRenderer fr, BoardData data) {
        if (tab == null || wide < TOGGLE_W + 90) return;
        Dynasty.button(fr, barX, barY, TOGGLE_W, BAR_H, toggle(data), mx, my, true);
        String note = note(data);
        int color = data == null || data.admin ? Dynasty.HINT : Dynasty.BAD;
        fr.drawStringWithShadow(Dynasty.fit(fr, note, wide - TOGGLE_W - SIDE - 14),
                barX + TOGGLE_W + 8, barY + (BAR_H - 8) / 2, color);
    }

    private String toggle(BoardData data) {
        if (data == null) return "Paneles: esperando";
        if (open) return "Cerrar paneles";
        return "Paneles: " + held(data) + " de " + total(data);
    }

    /** What the toolbar answers with: the notice of the server while it lasts, then the plain count. */
    private String note(BoardData data) {
        if (data == null) return "sin respuesta del servidor";
        if (!data.notice.trim().isEmpty()) return data.notice;
        if (!data.admin) return "te falta el nodo de staff";
        return "abre " + held(data) + " de " + total(data) + " paneles";
    }

    /** The whole list, in the same palette as the boards: two lines of who, then one line per panel. */
    private void sheet(FontRenderer fr, BoardData data) {
        Dynasty.panel(popX, popY, SHEET_W, HEAD_H + rows * ROW_H + FOOT_H);
        fr.drawStringWithShadow(Dynasty.fit(fr, first(data), SHEET_W - 10), popX + 5, popY + 3, Dynasty.TITLE);
        String what = "Rol: " + label(ROLES, role) + "   Trabajo: " + label(JOBS, job);
        fr.drawStringWithShadow(Dynasty.fit(fr, what, SHEET_W - 10), popX + 5, popY + 13, Dynasty.INFO);
        Dynasty.rule(popX + 4, popY + HEAD_H - 3, SHEET_W - 8);
        int first = page * rows;
        for (int i = 0; i < rows; i++) {
            if (data == null || data.panels == null || first + i >= data.panels.size()) break;
            line(fr, data.panels.get(first + i), popX + 4, popY + HEAD_H + i * ROW_H, SHEET_W - 8);
        }
        footer(fr, data, popY + HEAD_H + rows * ROW_H);
    }

    private String first(BoardData data) {
        if (data == null) return "sin respuesta del servidor";
        if (!data.admin) return "te falta el nodo de staff";
        return "Paneles que abre " + npc;
    }

    private void line(FontRenderer fr, BoardData.Panel panel, int x, int y, int width) {
        boolean holds = holds(panel);
        Dynasty.row(x, y, width, ROW_H - 1, holds, Dynasty.hit(x, y, width, ROW_H - 1, mx, my));
        fr.drawStringWithShadow(holds ? "abre" : "no", x + 4, y + 2, holds ? Dynasty.GOLD : Dynasty.MUTED);
        String name = panel.category.trim().isEmpty() ? panel.name : panel.category + " - " + panel.name;
        fr.drawStringWithShadow(Dynasty.fit(fr, name, width - 48), x + 30, y + 2,
                holds ? Dynasty.BODY : Dynasty.HINT);
        int others = others(panel, holds);
        if (others <= 0) return;
        String more = "+" + others;
        fr.drawStringWithShadow(more, x + width - 6 - fr.getStringWidth(more), y + 2, Dynasty.MUTED);
    }

    private void footer(FontRenderer fr, BoardData data, int y) {
        Dynasty.button(fr, popX + 4, y + 1, 14, 12, "<", mx, my, page > 0);
        Dynasty.button(fr, popX + SHEET_W - 40, y + 1, 14, 12, ">", mx, my, page < lastPage(data));
        Dynasty.button(fr, popX + SHEET_W - 22, y + 1, 18, 12, "x", mx, my, true);
        String out = total(data) == 0 ? "sin paneles" : (page + 1) + "/" + (lastPage(data) + 1);
        fr.drawStringWithShadow(out, popX + 24, y + 4, Dynasty.HINT);
        fr.drawStringWithShadow("+N = otros NPCs", popX + 62, y + 4, Dynasty.MUTED);
    }

    // --------------------------------------------------------------------------------------- their controls

    /**
     * One control of theirs, in the palette the rest of the pack uses. The label is theirs, read from the
     * button every frame, so a value they change — a role, a job, a yes, a page — shows here without this
     * class knowing anything about what it means.
     */
    private void paint(GuiButton button, FontRenderer fr) {
        if (button == null || !button.visible || button.width <= 0 || button.height <= 0 || fr == null) return;
        String type = button.getClass().getName();
        if (!type.startsWith(CNPC) || type.startsWith(OURS)) return;
        // Sliders, scroll lists and the tab art carry their own meaning in their own drawing.
        if (type.contains("Slider") || type.contains("Scroll") || type.contains("Menu")) return;

        boolean hovered = button.enabled && Dynasty.hit(button.x, button.y, button.width, button.height, mx, my);
        Dynasty.box(button.x, button.y, button.width, button.height,
                !button.enabled ? Dynasty.BUTTON_DISABLED : hovered ? Dynasty.BUTTON_HOVER : Dynasty.BUTTON);

        boolean turns = type.contains("BiDirectional");
        String label = button.displayString == null ? "" : button.displayString;
        String cut = Dynasty.fit(fr, label, button.width - (turns ? 26 : 10));
        int color = !button.enabled ? 0xFF6E675C : hovered ? Dynasty.GOLD : Dynasty.BODY;
        fr.drawStringWithShadow(cut, button.x + (button.width - fr.getStringWidth(cut)) / 2F,
                button.y + (button.height - 8) / 2F, color);
        if (!turns) return;
        // Their two-way buttons are stepped with arrows, and the arrows are the only hint that they are.
        fr.drawStringWithShadow("<", button.x + 5, button.y + (button.height - 8) / 2F, Dynasty.ACCENT);
        fr.drawStringWithShadow(">", button.x + button.width - 5 - fr.getStringWidth(">"),
                button.y + (button.height - 8) / 2F, Dynasty.ACCENT);
    }

    /**
     * Their panel keeps its own art, and their text fields keep their own caret and selection, so neither is
     * redrawn: the gold frame is laid around the panel and each field is edged just outside its rectangle,
     * which is all of the Dynasty they were missing without burying what they carry.
     */
    private void frame(GuiScreen gui) {
        Dynasty.frame(left - 1, top - 1, wide + 2, high + 2);
        if (target == null) return;
        for (Object value : fields(gui)) {
            if (!(value instanceof GuiTextField)) continue;
            GuiTextField field = (GuiTextField) value;
            if (!field.getVisible()) continue;
            Dynasty.frame(field.x - 3, field.y - 3, field.width + 6, field.height + 6);
        }
    }

    /** The fields of their screen, read out of their own map so nothing of theirs has to be answered for. */
    private List<Object> fields(GuiScreen gui) {
        List<Object> out = new ArrayList<Object>();
        try {
            if (fieldMap == null) fieldMap = find(gui.getClass(), "textfields");
            if (fieldMap == null) return out;
            fieldMap.setAccessible(true);
            Object value = fieldMap.get(gui);
            if (!(value instanceof Map)) return out;
            out.addAll(((Map<?, ?>) value).values());
        } catch (Throwable t) {
            // An editor of another version may not keep them in a map; the gold edge is only a garnish.
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ the click list

    /**
     * The click areas of the block, in front of their own controls so the block is answered first, and laid
     * down again every frame that anything about them moved. Their controls are never moved, never replaced
     * and never taken out of their list.
     */
    private void inject(BoardData data) {
        if (widgets == null || tab == null) return;
        int now = (open ? 1 : 0) + page * 31 + rows * 131 + popX + popY * 977 + total(data) * 1000003;
        boolean present = false;
        for (GuiButton widget : widgets) {
            if (widget == tab) {
                present = true;
                break;
            }
        }
        if (present && now == stamp) return;
        stamp = now;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).id >= TAB) widgets.remove(i);
        }
        widgets.add(0, tab);
        if (!open) return;
        int first = page * rows;
        for (int i = 0; i < rows; i++) {
            if (data == null || data.panels == null || first + i >= data.panels.size()) break;
            widgets.add(new Control(FIRST_ROW + i, popX + 4, popY + HEAD_H + i * ROW_H, SHEET_W - 8, ROW_H - 1, ""));
        }
        int y = popY + HEAD_H + rows * ROW_H + 1;
        widgets.add(press(PAGE_BACK, popX + 4, y, 14, page > 0));
        widgets.add(press(PAGE_NEXT, popX + SHEET_W - 40, y, 14, page < lastPage(data)));
        widgets.add(press(CLOSE, popX + SHEET_W - 22, y, 18, true));
    }

    private Control press(int id, int x, int y, int width, boolean enabled) {
        Control out = new Control(id, x, y, width, 12, "");
        out.enabled = enabled;
        return out;
    }

    /**
     * Where the block goes and how many lines it has room for. It is worked out every frame, because the
     * answer of the server can bring more or fewer panels than the frame before, and a list that kept the
     * height of the previous answer is the one thing a staff would read as a broken screen.
     */
    private void layout(BoardData data) {
        int below = screenH - barY - BAR_H - 2;
        int above = barY - 2;
        int room = Math.max(HEAD_H + FOOT_H + ROW_H, Math.max(below, above));
        rows = Math.max(1, Math.min(MAX_ROWS, (room - HEAD_H - FOOT_H) / ROW_H));
        if (total(data) > 0) rows = Math.min(rows, total(data));
        page = clamp(page, 0, lastPage(data));
        popX = clamp(barX, 2, Math.max(2, screenW - SHEET_W - 2));
        popY = below >= above ? barY + BAR_H + 2 : barY - 2 - (HEAD_H + rows * ROW_H + FOOT_H);
        popY = Math.max(2, popY);
    }

    // ------------------------------------------------------------------------------------------------ state

    /** Their panel, in the pixels this screen has room for, and the two bands that come out of it. */
    private boolean place(GuiScreen gui) {
        int xSize = num(gui, "xSize");
        int ySize = num(gui, "ySize");
        if (xSize <= 0 || ySize <= 0) return false;
        left = num(gui, "guiLeft");
        top = num(gui, "guiTop");
        wide = xSize;
        high = ySize;
        screenW = gui.width;
        screenH = gui.height;
        barX = clamp(left + SIDE, 2, screenW - TOGGLE_W - 2);
        barY = clamp(top + high + 3, 2, screenH - BAR_H - 2);
        if (tab != null) {
            tab.x = barX;
            tab.y = barY;
            tab.width = TOGGLE_W;
            tab.height = BAR_H;
        }
        return true;
    }

    /**
     * The role, the job and the name as the editor holds them, taken from the entity every frame: their own
     * buttons write straight into it, so a switch is here before the NPC is saved. When any of the three
     * moves, the block asks the server again, which is what a staff who has just changed the role of an NPC
     * expects to see without having to close and open the editor.
     */
    private void follow() {
        if (target == null) return;
        int nowRole = read(roleField);
        int nowJob = read(jobField);
        String nowName = trim(target.getName());
        boolean moved = seen && (nowRole != role || nowJob != job || !nowName.equals(npc));
        role = nowRole;
        job = nowJob;
        npc = nowName;
        seen = true;
        if (moved) {
            stamp = -1;
            ask();
            return;
        }
        if (ClientState.roleData != null || ++wait < RETRY) return;
        ask();
    }

    /** The question the block lives on, on the channel that never brings a screen with it. */
    private void ask() {
        wait = 0;
        if (target == null) return;
        RebornQuests.NET.sendToServer(new AdminAction(AdminAction.NPC_PANELS, "", "", -1, 0).silently());
    }

    private void forget() {
        screen = null;
        widgets = null;
        tab = null;
        placed = false;
        fieldMap = null;
        target = null;
        advanced = null;
        roleField = null;
        jobField = null;
        npc = "";
        uuid = "";
        role = UNREAD;
        job = UNREAD;
        seen = false;
        open = false;
        page = 0;
        rows = MAX_ROWS;
        stamp = -1;
        wait = 0;
        mx = -1;
        my = -1;
    }

    /** Give this NPC the panel, or take out whichever of its entries is holding him. */
    private void toggle(BoardData.Panel panel) {
        for (String entry : panel.npcs) {
            if (!holds(entry)) continue;
            send(AdminAction.NPC_DEL, panel.name, entry);
            return;
        }
        send(AdminAction.NPC_ADD, panel.name, npc);
    }

    /**
     * The change, and the answer that comes back with it. The server replies on the silent channel, so the
     * check of the line flips without a screen and the staff stays inside the editor he was already using.
     */
    private void send(int kind, String panel, String value) {
        RebornQuests.NET.sendToServer(new AdminAction(kind, panel, value, -1, 0).silently());
        stamp = -1;
    }

    private boolean holds(BoardData.Panel panel) {
        if (panel == null || panel.npcs == null) return false;
        for (String entry : panel.npcs) {
            if (holds(entry)) return true;
        }
        return false;
    }

    /** True when one entry of a panel is this NPC: by the name the editor shows, or pinned by his id. */
    private boolean holds(String entry) {
        String wanted = trim(entry);
        if (wanted.isEmpty()) return false;
        return wanted.equalsIgnoreCase(npc) || wanted.equalsIgnoreCase("#" + uuid);
    }

    /** How many more NPCs share the panel, which is what the number at the end of a line counts. */
    private static int others(BoardData.Panel panel, boolean holds) {
        if (panel == null || panel.npcs == null) return 0;
        return Math.max(0, panel.npcs.size() - (holds ? 1 : 0));
    }

    private int total(BoardData data) {
        return data == null || data.panels == null ? 0 : data.panels.size();
    }

    private int held(BoardData data) {
        if (data == null || data.panels == null) return 0;
        int out = 0;
        for (BoardData.Panel panel : data.panels) {
            if (holds(panel)) out++;
        }
        return out;
    }

    private int lastPage(BoardData data) {
        return Math.max(0, (total(data) + rows - 1) / rows - 1);
    }

    // ---------------------------------------------------------------------------------------------- reading

    /** The two fields of their own data class, looked up once per editor rather than once per frame. */
    private void bind(Entity npc) {
        Object data = dataOf(npc);
        if (data == null) return;
        advanced = data;
        roleField = find(data.getClass(), "role");
        jobField = find(data.getClass(), "job");
    }

    private static Object dataOf(Entity npc) {
        try {
            Field field = find(npc.getClass(), "advanced");
            if (field == null) return null;
            field.setAccessible(true);
            return field.get(npc);
        } catch (Throwable t) {
            return null;
        }
    }

    private int read(Field field) {
        if (field == null || advanced == null) return UNREAD;
        try {
            return field.getInt(advanced);
        } catch (Throwable t) {
            return UNREAD;
        }
    }

    /** The NPC the editor is standing in front of; Custom NPCs keeps him on a public field of its own screen. */
    private static Entity edited(GuiScreen gui) {
        try {
            Field field = find(gui.getClass(), "npc");
            if (field == null) return null;
            field.setAccessible(true);
            Object value = field.get(gui);
            return value instanceof Entity ? (Entity) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** One of the four numbers that say where their panel sits on the screen. */
    private static int num(GuiScreen gui, String name) {
        try {
            Field field = find(gui.getClass(), name);
            if (field == null) return 0;
            field.setAccessible(true);
            return field.getInt(gui);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Field find(Class<?> type, String name) {
        for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
            try {
                return at.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // The field belongs to one of their base classes; the walk keeps looking.
            }
        }
        return null;
    }

    private static String label(String[] table, int at) {
        if (at == UNREAD) return "sin lectura";
        if (at < 0) return "ninguno";
        return at < table.length ? table[at] : "otro (" + at + ")";
    }

    private static boolean isCnpc(GuiScreen gui) {
        return gui != null && gui.getClass().getName().startsWith(CNPC);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }

    /**
     * Their own button class, because their editor casts every control it is handed before it looks at what
     * it is: a plain vanilla one pressed here would throw inside their code. The ids of this class stay above
     * nine hundred, where none of theirs ever reaches, so their handlers read a number they pass over.
     */
    public static class Control extends noppes.npcs.client.gui.util.GuiNpcButton {
        public Control(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }
    }
}
