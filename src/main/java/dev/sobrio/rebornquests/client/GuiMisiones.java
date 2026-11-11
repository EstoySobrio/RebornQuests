package dev.sobrio.rebornquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Mouse;

import dev.sobrio.rebornquests.BoardData;
import dev.sobrio.rebornquests.MessageAction;
import dev.sobrio.rebornquests.RebornQuests;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;

/**
 * The board, in the Dynasty style of the 1.20.1 guild screens: one gold-framed surface with three
 * panes — where the missions come from, the missions themselves and the detail of the one picked. Rows and
 * buttons are painted rather than vanilla widgets, but the server still re-validates every accept.
 *
 * <p>The same surface answers two different questions. Clicked at an NPC it is the counter of that NPC: its
 * panels on the left and their missions to accept, all of them together when the NPC carries several.
 * Opened with the quest-book key it is a book: the two categories Custom NPCs itself uses, ACTIVAS and
 * COMPLETADAS, and only what the player already holds.
 */
public class GuiMisiones extends GuiScreen {
    private static final int MARGIN = 12;
    private static final int ROW = 24;
    private static final int LINE = 11;
    /** The two small buttons under the detail pane and the gap between all three. */
    private static final int CLOCK_W = 92;
    private static final int EXIT_W = 70;
    private static final int GAP = 6;
    /** Height of the small tabs that sit between the title and the three columns. */
    private static final int TAB_H = 14;
    /** Width of the top-right tab, which is the same control the board has always had. */
    private static final int REFRESH_W = 84;
    private static final int CLOSE_W = 64;

    private int top = 44;
    private int bottom = 160;
    private int panelX = MARGIN;
    private int panelW = 120;
    private int questX = MARGIN;
    private int questW = 160;
    private int detailX = MARGIN;
    private int detailW = 200;
    private int buttonRowY = 200;
    private int acceptX = MARGIN;
    private int acceptW = 120;
    private int clockX = MARGIN;
    private int exitX = MARGIN;
    /** The rank button, which takes the whole lower left side, and the two tabs above the columns. */
    private int infoX = MARGIN;
    private int infoW = 200;
    private int refreshX = MARGIN;
    private int tabY = 26;
    /** The rank card, measured with the board so the click and the drawing always agree. */
    private int cardX = MARGIN;
    private int cardY = 60;
    private int cardW = 220;
    private int cardH = 120;
    /** True while the rank card covers the board. */
    private boolean showInfo;
    private int lastX;
    private int lastY;
    /** The board opened from the admin screen to look at one panel: read-only, and Volver goes back there. */
    private final boolean preview;
    private final List<Line> detail = new ArrayList<Line>();
    /** The flat middle list, rebuilt when the board, the category or the joint view change. */
    private List<Entry> flatCache;
    private long flatAt = -1L;
    private int flatKey = -1;

    public GuiMisiones() {
        this(false);
    }

    public GuiMisiones(boolean preview) {
        this.preview = preview;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        layout();
        clampScrolls();
        rebuildDetail();
    }

    /** Every width comes from the window, so a change of GUI scale just re-measures. */
    private void layout() {
        int inner = this.width - 2 * MARGIN;
        panelW = clamp(inner / 4, 84, 150);
        questW = clamp((inner - 4 * MARGIN) / 2, 110, 230);
        panelX = MARGIN;
        questX = panelX + panelW + 6;
        detailX = questX + questW + 6;
        detailW = Math.max(120, this.width - MARGIN - detailX);
        top = 44;
        bottom = this.height - 44;
        tabY = top - 18;
        refreshX = this.width - MARGIN - REFRESH_W;
        measureButtons();
        measureCard();
    }

    private int contentTop() {
        return top + 24;
    }

    /** The three buttons under the detail pane, measured once so the click and the draw agree. */
    private void measureButtons() {
        buttonRowY = this.height - 36;
        acceptX = detailX;
        acceptW = Math.max(64, detailW - CLOCK_W - EXIT_W - 2 * GAP);
        clockX = acceptX + acceptW + GAP;
        exitX = clockX + CLOCK_W + GAP;
        infoX = panelX;
        infoW = questX + questW - panelX;
    }

    /**
     * The rank card is measured with the board because it has to answer a click the very frame it opens:
     * four lines about the player and, under them, whatever the server still has to tell him.
     */
    private void measureCard() {
        cardW = clamp((this.width - 2 * MARGIN) / 2, 210, 340);
        cardX = (this.width - cardW) / 2;
        cardH = 34 + 4 * LINE + 8 + infoNotes().size() * 10 + 24;
        cardY = Math.max(tabY + TAB_H + 4, (this.height - cardH) / 2);
    }

    /** What the card says besides the four lines, cut to three so it can never outgrow the screen. */
    private List<String> infoNotes() {
        List<String> out = new ArrayList<String>();
        BoardData data = ClientState.data;
        if (this.fontRenderer == null) return out;
        int width = Math.max(60, cardW - 24);
        if (!data.boardNode.trim().isEmpty()) {
            out.addAll(this.fontRenderer.listFormattedStringToWidth(
                    "El tablón también pide el permiso " + data.boardNode.trim() + ".", width));
        }
        if (!data.notice.trim().isEmpty()) {
            out.addAll(this.fontRenderer.listFormattedStringToWidth(data.notice.trim(), width));
        }
        while (out.size() > 3) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private int rows() {
        return Math.max(1, (bottom - contentTop() - 2) / ROW);
    }

    private int detailLines() {
        return Math.max(1, (bottom - contentTop() - 4) / LINE);
    }

    private int maxPanelScroll() {
        return Math.max(0, visible().size() - rows());
    }

    /**
     * The panels the left column is about. An NPC was clicked for one or more of its own panels, so only
     * those are listed; the quest-book key and the command carry none, and then his whole board is.
     */
    private static List<Integer> visible() {
        BoardData data = ClientState.data;
        List<Integer> out = new ArrayList<Integer>();
        if (!data.group.isEmpty()) {
            for (String name : data.group) {
                int at = data.indexOfPanel(name);
                if (at >= 0 && (data.admin || !data.panels.get(at).locked)) out.add(Integer.valueOf(at));
            }
            if (!out.isEmpty()) return out;
        }
        for (int i = 0; i < data.panels.size(); i++) {
            if (shown(data.panels.get(i))) out.add(Integer.valueOf(i));
        }
        return out;
    }

    private static boolean shown(BoardData.Panel panel) {
        return ClientState.data.admin || !panel.locked;
    }

    /**
     * The book, which is what the quest-book key opens when no NPC was clicked: what the player already
     * has, in the two categories Custom NPCs itself uses, and nothing to accept.
     */
    private boolean book() {
        return !preview && ClientState.data.group.isEmpty();
    }

    /** One NPC, several panels, and the staff's counter read as a whole instead of panel by panel. */
    private boolean joint() {
        return !preview && ClientState.data.group.size() > 1 && ClientState.groupAll;
    }

    /**
     * The flat list the middle column shows instead of one panel's rows: the two categories of the book, or
     * everything acceptable at a multi-panel NPC. Null means the middle column keeps showing the panel.
     * Kept between frames, because the screen measures with it every time it draws.
     */
    private List<Entry> showing() {
        int key = (ClientState.bookCat * 2 + (ClientState.groupAll ? 1 : 0)) * 8192
                + ClientState.data.panels.size();
        if (flatAt != ClientState.boardTakenAt || flatKey != key) {
            flatCache = buildFlat();
            flatAt = ClientState.boardTakenAt;
            flatKey = key;
        }
        return flatCache;
    }

    private List<Entry> buildFlat() {
        if (book()) return entries(ClientState.bookCat == 0 ? BoardData.ACTIVE : BoardData.DONE, false);
        if (joint()) return entries(BoardData.OPEN, true);
        return null;
    }

    /**
     * The missions of every panel listed, in panel order, as the flat rows the book and the joint view need.
     * With {@code onlyOpen} the list is the missions that can be taken right now, whatever state they carry.
     */
    private List<Entry> entries(int state, boolean onlyOpen) {
        List<Entry> out = new ArrayList<Entry>();
        Set<Integer> once = new HashSet<Integer>();
        BoardData data = ClientState.data;
        for (Integer boxed : visible()) {
            int at = boxed.intValue();
            BoardData.Panel panel = data.panels.get(at);
            if (panel.locked && !data.admin) continue;
            for (int i = 0; i < panel.rows.size(); i++) {
                BoardData.Row row = panel.rows.get(i);
                if (onlyOpen ? !row.acceptable : row.state != state) continue;
                // One quest can sit on several panels and is still one quest of his, so it is listed once.
                if (!once.add(Integer.valueOf(row.id))) continue;
                out.add(new Entry(panel, at, i, row));
            }
        }
        return out;
    }

    private int maxQuestScroll() {
        List<Entry> flat = showing();
        if (flat != null) return Math.max(0, flat.size() - rows());
        BoardData.Panel selected = panel();
        return Math.max(0, (selected == null ? 0 : selected.rows.size()) - rows());
    }

    private int maxDetailScroll() {
        return Math.max(0, detail.size() - detailLines());
    }

    private void clampScrolls() {
        ClientState.panelScroll = clamp(ClientState.panelScroll, 0, maxPanelScroll());
        ClientState.questScroll = clamp(ClientState.questScroll, 0, maxQuestScroll());
        ClientState.detailScroll = clamp(ClientState.detailScroll, 0, maxDetailScroll());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        this.lastX = mouseX;
        this.lastY = mouseY;
        layout();
        clampScrolls();
        Dynasty.panel(MARGIN - 4, 8, this.width - 2 * (MARGIN - 4), this.height - 16);

        BoardData data = ClientState.data;
        String title = "§l" + (preview ? "VISTA PREVIA  ·  " + data.title : data.title);
        draw(Dynasty.fit(this.fontRenderer, title, this.width - 2 * MARGIN),
                (this.width - this.fontRenderer.getStringWidth(title)) / 2F, 15, Dynasty.TITLE);
        // The board itself says nothing about who he is: that is what the rank button is for. The preview is
        // a read-only look at one panel, so it names the panel instead.
        if (preview) {
            String seen = Dynasty.fit(this.fontRenderer, previewIdentity(data), this.width - 2 * MARGIN);
            draw(seen, (this.width - this.fontRenderer.getStringWidth(seen)) / 2F, 27, Dynasty.ACCENT);
        } else {
            renderTabs();
        }

        renderPanels();
        renderQuests();
        renderDetail();
        renderButtons();
        if (showInfo) {
            renderInfoCard();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * The two tabs between the title and the columns: an NPC of several panels can be read as one list, and
     * the board can be asked again for what the server holds right now.
     */
    private void renderTabs() {
        if (ClientState.data.group.size() > 1) {
            Dynasty.button(this.fontRenderer, panelX, tabY, panelW, TAB_H,
                    joint() ? "Panel por panel" : "Todo junto", lastX, lastY, true);
        }
        Dynasty.button(this.fontRenderer, refreshX, tabY, REFRESH_W, TAB_H, "Actualizar", lastX, lastY, true);
    }

    /**
     * The rank card: the village and rank the server found for him and how far he has come, in the middle of
     * the board instead of printed above it. The two lines the server still wants to say, the permission the
     * tablón asks for besides his rank and the warning about Custom NPCs, live underneath the four values.
     */
    private void renderInfoCard() {
        BoardData data = ClientState.data;
        Dynasty.box(cardX, cardY, cardW, cardH, Dynasty.FIELD);
        Dynasty.frame(cardX - 2, cardY - 2, cardW + 4, cardH + 4);
        String heading = "§lINFORMACION DE RANGO";
        draw(heading, cardX + (cardW - this.fontRenderer.getStringWidth(heading)) / 2F, cardY + 9, Dynasty.TITLE);
        Dynasty.rule(cardX + 10, cardY + 22, cardW - 20);
        String village = data.village.trim().isEmpty() ? "sin aldea" : data.village.trim();
        String rank = data.rankLabel.trim().isEmpty() ? "sin rango" : data.rankLabel.trim();
        int y = cardY + 30;
        y = cardRow("Aldea", village, y);
        y = cardRow("Rango", rank, y);
        y = cardRow("Misiones completadas", String.valueOf(data.finished), y);
        y = cardRow("Misiones activas", String.valueOf(data.active), y);
        List<String> notes = infoNotes();
        if (!notes.isEmpty()) {
            Dynasty.rule(cardX + 10, y + 2, cardW - 20);
            y += 8;
            for (String note : notes) {
                draw(note, cardX + 12, y, Dynasty.HINT);
                y += 10;
            }
        }
        Dynasty.button(this.fontRenderer, cardX + (cardW - CLOSE_W) / 2, cardY + cardH - 22,
                CLOSE_W, 16, "Cerrar", lastX, lastY, true);
    }

    /** One label of the card with its value lined up on the right, in the parchment tones of the board. */
    private int cardRow(String label, String value, int y) {
        draw(label, cardX + 12, y + 2, Dynasty.HINT);
        draw("§l" + value, cardX + cardW - 12 - this.fontRenderer.getStringWidth(value), y + 2, Dynasty.BODY);
        return y + LINE;
    }

    /** What the preview says instead of the player line: the panel it looks at and its mission count. */
    private static String previewIdentity(BoardData data) {
        BoardData.Panel panel = panel();
        if (panel == null) return "Elige un panel a la izquierda  ·  solo lectura, nada se acepta";
        String village = panel.category.isEmpty() ? "" : "  ·  aldea " + panel.category;
        return panel.name + village + "  ·  " + panel.rows.size()
                + (panel.rows.size() == 1 ? " mision  ·  " : " misiones  ·  ")
                + "solo lectura, nada se acepta";
    }

    private void renderPanels() {
        if (book()) {
            renderCategories();
            return;
        }
        BoardData data = ClientState.data;
        List<Integer> shown = visible();
        Dynasty.box(panelX, top, panelW, bottom - top, Dynasty.LIST);
        section("PANELES", panelX + 8, top + 7, panelW - 16, shown.size() + "");
        for (int i = 0; i < rows(); i++) {
            int index = ClientState.panelScroll + i;
            if (index >= shown.size()) break;
            BoardData.Panel item = data.panels.get(shown.get(index).intValue());
            int y = contentTop() + i * ROW;
            boolean selected = shown.get(index).intValue() == ClientState.selectedPanel;
            Dynasty.row(panelX + 5, y, panelW - 10, ROW - 3, selected,
                    Dynasty.hit(panelX + 5, y, panelW - 10, ROW - 3, lastX, lastY));
            drawRect(panelX + panelW - 17, y + 7, panelX + panelW - 11, y + 13,
                    item.locked ? Dynasty.STATE_LOCKED : Dynasty.STATE_AVAILABLE);
            draw(Dynasty.fit(this.fontRenderer, item.name, panelW - 40), panelX + 12, y + 4,
                    selected ? Dynasty.TITLE : Dynasty.BODY);
            draw(Dynasty.fit(this.fontRenderer, village(item) + (item.locked ? "  ·  bloqueado"
                            : "  ·  " + item.rows.size() + " misiones"), panelW - 28),
                    panelX + 12, y + 14, item.locked ? Dynasty.BAD : Dynasty.HINT);
        }
        if (shown.isEmpty()) {
            hint("Ningun panel te deja aceptar misiones ahora.", panelX + 10, contentTop() + 6, panelW - 20);
            return;
        }
        scrollbar(panelX + panelW - 4, shown.size(), ClientState.panelScroll, rows());
    }

    /**
     * The book's left column: the two categories Custom NPCs itself uses, each with what it holds. Nothing
     * else is offered there, because a book is what the player already has, not what he could still take.
     */
    private void renderCategories() {
        Dynasty.box(panelX, top, panelW, bottom - top, Dynasty.LIST);
        section("TU LIBRO", panelX + 8, top + 7, panelW - 16, null);
        List<Entry> active = entries(BoardData.ACTIVE, false);
        List<Entry> finished = entries(BoardData.DONE, false);
        for (int i = 0; i < 2; i++) {
            int y = contentTop() + i * ROW;
            boolean picked = ClientState.bookCat == i;
            Dynasty.row(panelX + 5, y, panelW - 10, ROW - 3, picked,
                    Dynasty.hit(panelX + 5, y, panelW - 10, ROW - 3, lastX, lastY));
            drawRect(panelX + panelW - 17, y + 7, panelX + panelW - 11, y + 13,
                    i == 0 ? Dynasty.STATE_ACTIVE : Dynasty.STATE_DONE);
            draw("§l" + (i == 0 ? "ACTIVAS" : "COMPLETADAS"), panelX + 12, y + 4,
                    picked ? Dynasty.TITLE : Dynasty.BODY);
            int count = i == 0 ? active.size() : finished.size();
            draw(count + (count == 1 ? " mision" : " misiones"), panelX + 12, y + 14, Dynasty.HINT);
        }
        if (active.isEmpty() && finished.isEmpty()) {
            hint("No has aceptado ninguna mision.", panelX + 10, contentTop() + 2 * ROW + 6, panelW - 20);
        }
    }

    /** Village of one panel, or the plain label when the staff left it unfiled. */
    private static String village(BoardData.Panel panel) {
        return panel.category.isEmpty() ? "sin aldea" : panel.category;
    }

    private void renderQuests() {
        List<Entry> flat = showing();
        if (flat != null) {
            renderFlat(flat);
            return;
        }
        BoardData.Panel selected = panel();
        Dynasty.box(questX, top, questW, bottom - top, Dynasty.LIST);
        section("MISIONES", questX + 8, top + 7, questW - 16,
                selected == null ? "-" : selected.rows.size() + "");
        if (selected == null) {
            hint("Elige un panel de la izquierda.", questX + 10, contentTop() + 6, questW - 20);
            return;
        }
        for (int i = 0; i < rows(); i++) {
            int index = ClientState.questScroll + i;
            if (index >= selected.rows.size()) break;
            BoardData.Row row = selected.rows.get(index);
            int y = contentTop() + i * ROW;
            boolean picked = index == ClientState.selectedQuest;
            Dynasty.row(questX + 5, y, questW - 10, ROW - 3, picked,
                    Dynasty.hit(questX + 5, y, questW - 10, ROW - 3, lastX, lastY));
            drawRect(questX + questW - 17, y + 7, questX + questW - 11, y + 13, stateColor(row));
            draw("§l[" + row.difficulty + "]", questX + 12, y + 4, Dynasty.difficultyColor(row.difficulty));
            draw(Dynasty.fit(this.fontRenderer, row.name, questW - 104), questX + 30, y + 4,
                    picked ? Dynasty.TITLE : Dynasty.BODY);
            String limit = limitLabel(row);
            if (!limit.isEmpty()) {
                draw(limit, questX + questW - 22 - this.fontRenderer.getStringWidth(limit), y + 4,
                        limitColor(row));
            }
            draw(shortState(row, selected), questX + 12, y + 14, stateColor(row));
            if (row.total > 1) {
                String count = row.done + "/" + row.total;
                draw(count, questX + questW - 22 - this.fontRenderer.getStringWidth(count), y + 14, Dynasty.HINT);
            }
        }
        if (selected.locked) {
            hint("§c" + selected.reason, questX + 10, bottom - 22, questW - 20);
        }
        scrollbar(questX + questW - 4, selected.rows.size(), ClientState.questScroll, rows());
    }

    /**
     * The middle column read as one list instead of panel by panel: the category of the book that is open,
     * or everything the clicked NPC has to offer across all its panels. The panel a mission came from is
     * named on its second line, since a flat list would otherwise lose where it lives.
     */
    private void renderFlat(List<Entry> list) {
        Dynasty.box(questX, top, questW, bottom - top, Dynasty.LIST);
        section(flatHeading(), questX + 8, top + 7, questW - 16, list.size() + "");
        for (int i = 0; i < rows(); i++) {
            int index = ClientState.questScroll + i;
            if (index >= list.size()) break;
            Entry entry = list.get(index);
            BoardData.Row row = entry.row;
            int y = contentTop() + i * ROW;
            boolean picked = entry.at == ClientState.selectedPanel && entry.index == ClientState.selectedQuest;
            Dynasty.row(questX + 5, y, questW - 10, ROW - 3, picked,
                    Dynasty.hit(questX + 5, y, questW - 10, ROW - 3, lastX, lastY));
            drawRect(questX + questW - 17, y + 7, questX + questW - 11, y + 13, stateColor(row));
            draw("§l[" + row.difficulty + "]", questX + 12, y + 4, Dynasty.difficultyColor(row.difficulty));
            draw(Dynasty.fit(this.fontRenderer, row.name, questW - 104), questX + 30, y + 4,
                    picked ? Dynasty.TITLE : Dynasty.BODY);
            String limit = limitLabel(row);
            if (!limit.isEmpty()) {
                draw(limit, questX + questW - 22 - this.fontRenderer.getStringWidth(limit), y + 4,
                        limitColor(row));
            }
            draw(Dynasty.fit(this.fontRenderer, flatState(entry), questW - 24), questX + 12, y + 14,
                    stateColor(row));
        }
        if (list.isEmpty()) {
            hint(book() ? "No has aceptado ninguna mision."
                    : "Ninguna mision de este NPC puedes aceptar ahora.",
                    questX + 10, contentTop() + 6, questW - 20);
        }
        scrollbar(questX + questW - 4, list.size(), ClientState.questScroll, rows());
    }

    /** What the flat list is called, which is the one thing that tells the two uses of the column apart. */
    private String flatHeading() {
        if (book()) return ClientState.bookCat == 0 ? "TUS ACTIVAS" : "TUS COMPLETADAS";
        return "LO QUE PUEDES ACEPTAR";
    }

    /** Second line of a flat row: its own state, and the panel it came from so the list stays readable. */
    private static String flatState(Entry entry) {
        return shortState(entry.row, entry.panel) + "  ·  " + entry.panel.name;
    }

    private void renderDetail() {
        BoardData.Row row = quest();
        Dynasty.box(detailX, top, detailW, bottom - top, Dynasty.LIST);
        section("DETALLE DE LA MISION", detailX + 8, top + 7, detailW - 16, null);
        int first = contentTop();
        int left = remaining(row);
        if (row != null && row.limitMin > 0) {
            boolean running = row.state == BoardData.ACTIVE;
            draw("§l" + (running
                            ? (left > 0 ? "Tiempo restante: " + BoardData.clock(left) : "Tiempo agotado")
                            : "Tiempo limite: " + shortMinutes(row.limitMin)),
                    detailX + 10, first, running ? Dynasty.clockColor(left) : Dynasty.HINT);
            first += LINE;
        }
        if (row != null && row.total > 1) {
            Dynasty.bar(detailX + 10, first, detailW - 34, 4, (float) row.done / row.total, stateColor(row));
            first += 10;
        }
        int y = first - ClientState.detailScroll * LINE;
        for (Line line : detail) {
            if (y >= first - 2 && y <= bottom - 12) {
                draw(line.text, detailX + 10, y, line.color);
            }
            y += LINE;
        }
        BoardData.Panel selected = panel();
        if (selected != null && selected.locked && row != null) {
            draw("No puedes aceptarla todavia.", detailX + 10, bottom - 12, Dynasty.BAD);
        }
        scrollbar(detailX + detailW - 4, detail.size(), ClientState.detailScroll, detailLines());
    }

    private void renderButtons() {
        BoardData.Panel selected = panel();
        BoardData.Row row = quest();
        boolean open = selected != null && !selected.locked;
        boolean acceptable = row != null && row.acceptable && open;
        int y = buttonRowY;
        if (preview) {
            Dynasty.button(this.fontRenderer, questX, y, questW, 18, "Volver al panel", lastX, lastY, true);
            String seen = row == null ? "Nada seleccionado"
                    : acceptable ? "Si se pudiera aceptar" : shortState(row, selected).toUpperCase(Locale.ROOT);
            Dynasty.button(this.fontRenderer, acceptX, y, acceptW, 18, seen, lastX, lastY, false);
            Dynasty.button(this.fontRenderer, clockX, y, CLOCK_W, 18, "Volver", lastX, lastY, true);
            Dynasty.button(this.fontRenderer, exitX, y, EXIT_W, 18, "Cerrar", lastX, lastY, true);
            return;
        }
        Dynasty.button(this.fontRenderer, infoX, y, infoW, 18,
                showInfo ? "Ocultar informacion" : "Informacion de rango", lastX, lastY, true);
        if (book()) {
            // A book holds what he already took: accepting is done at the tablon of an NPC.
            Dynasty.button(this.fontRenderer, acceptX, y, acceptW, 18, "Aceptar en un NPC", lastX, lastY, false);
        } else {
            String label = row == null ? "Aceptar mision"
                    : acceptable ? "Aceptar mision" : open ? "No disponible" : "Panel bloqueado";
            Dynasty.button(this.fontRenderer, acceptX, y, acceptW, 18, label, lastX, lastY, acceptable);
        }
        Dynasty.button(this.fontRenderer, clockX, y, CLOCK_W, 18, "Colocar reloj", lastX, lastY, true);
        Dynasty.button(this.fontRenderer, exitX, y, EXIT_W, 18, "Salir", lastX, lastY, true);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        // While the card covers the board it swallows the click, so a row underneath never moves by accident.
        if (showInfo) {
            if (Dynasty.hit(cardX + (cardW - CLOSE_W) / 2, cardY + cardH - 22, CLOSE_W, 16, mouseX, mouseY)) {
                click();
            }
            showInfo = false;
            return;
        }
        int y = buttonRowY;
        if (preview) {
            if (Dynasty.hit(questX, y, questW, 18, mouseX, mouseY)
                    || Dynasty.hit(clockX, y, CLOCK_W, 18, mouseX, mouseY)) {
                backToAdmin();
                return;
            }
            if (Dynasty.hit(exitX, y, EXIT_W, 18, mouseX, mouseY)) {
                click();
                this.mc.displayGuiScreen(null);
                return;
            }
        } else if (Dynasty.hit(infoX, y, infoW, 18, mouseX, mouseY)) {
            click();
            showInfo = true;
            return;
        } else if (Dynasty.hit(acceptX, y, acceptW, 18, mouseX, mouseY)) {
            accept();
            return;
        } else if (Dynasty.hit(clockX, y, CLOCK_W, 18, mouseX, mouseY)) {
            click();
            this.mc.displayGuiScreen(new GuiHudPlace());
            return;
        } else if (Dynasty.hit(exitX, y, EXIT_W, 18, mouseX, mouseY)) {
            click();
            RebornQuests.NET.sendToServer(new MessageAction(MessageAction.CLOSE, 0));
            this.mc.displayGuiScreen(null);
            return;
        }
        if (!preview) {
            if (Dynasty.hit(refreshX, tabY, REFRESH_W, TAB_H, mouseX, mouseY)) {
                click();
                RebornQuests.NET.sendToServer(new MessageAction(MessageAction.OPEN, 0));
                return;
            }
            if (ClientState.data.group.size() > 1 && Dynasty.hit(panelX, tabY, panelW, TAB_H, mouseX, mouseY)) {
                click();
                ClientState.groupAll = !ClientState.groupAll;
                ClientState.questScroll = 0;
                ClientState.selectedQuest = -1;
                rebuildDetail();
                return;
            }
        }
        if (book()) {
            int category = rowAt(panelX + 5, panelW - 10, 2, 0);
            if (category >= 0) {
                click();
                ClientState.bookCat = category;
                ClientState.selectedQuest = -1;
                ClientState.questScroll = 0;
                ClientState.detailScroll = 0;
                rebuildDetail();
                return;
            }
        }
        List<Entry> flat = showing();
        if (flat != null) {
            int picked = rowAt(questX + 5, questW - 10, flat.size(), ClientState.questScroll);
            if (picked >= 0) {
                click();
                Entry entry = flat.get(picked);
                boolean same = entry.at == ClientState.selectedPanel && entry.index == ClientState.selectedQuest;
                ClientState.selectedPanel = entry.at;
                ClientState.selectedQuest = same ? -1 : entry.index;
                ClientState.detailScroll = 0;
                rebuildDetail();
                return;
            }
        }
        if (book()) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        BoardData.Panel selected = panel();
        List<Integer> shown = visible();
        int picked = rowAt(panelX + 5, panelW - 10, shown.size(), ClientState.panelScroll);
        if (picked >= 0) {
            click();
            // The rows are the ones on his board, but the selection is kept as the panel really is numbered.
            int at = shown.get(picked).intValue();
            ClientState.selectedPanel = ClientState.selectedPanel == at ? -1 : at;
            ClientState.selectedQuest = -1;
            ClientState.questScroll = 0;
            // Choosing one panel of an NPC of several is the way back to reading them one by one.
            ClientState.groupAll = false;
            rebuildDetail();
            return;
        }
        if (flat == null && selected != null) {
            picked = rowAt(questX + 5, questW - 10, selected.rows.size(), ClientState.questScroll);
            if (picked >= 0) {
                click();
                ClientState.selectedQuest = ClientState.selectedQuest == picked ? -1 : picked;
                ClientState.detailScroll = 0;
                rebuildDetail();
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /** Which row of a list the cursor sits on, or -1 when it is outside every row. */
    private int rowAt(int x, int width, int size, int scroll) {
        if (!Dynasty.hit(x, contentTop(), width, rows() * ROW - 3, lastX, lastY)) return -1;
        int offset = lastY - contentTop();
        int index = scroll + offset / ROW;
        if (offset % ROW >= ROW - 3) return -1;
        return index >= 0 && index < size ? index : -1;
    }

    private void accept() {
        BoardData.Panel selected = panel();
        BoardData.Row row = quest();
        if (selected == null || selected.locked || row == null || !row.acceptable) return;
        click();
        RebornQuests.NET.sendToServer(new MessageAction(MessageAction.ACCEPT, row.id));
    }

    private void click() {
        if (this.mc.player != null) {
            this.mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.25F, 1.0F);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getDWheel();
        if (delta == 0) return;
        int step = delta > 0 ? -1 : 1;
        boolean inPanels = Dynasty.hit(panelX, top, panelW, bottom - top, lastX, lastY);
        boolean inQuests = Dynasty.hit(questX, top, questW, bottom - top, lastX, lastY);
        if (inPanels) {
            ClientState.panelScroll = clamp(ClientState.panelScroll + step, 0, maxPanelScroll());
        } else if (inQuests) {
            ClientState.questScroll = clamp(ClientState.questScroll + step, 0, maxQuestScroll());
        } else {
            ClientState.detailScroll = clamp(ClientState.detailScroll + step * 2, 0, maxDetailScroll());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 && showInfo) {
            showInfo = false;
            return;
        }
        if (keyCode == 1 && preview) {
            backToAdmin();
            return;
        }
        if (keyCode == 1) RebornQuests.NET.sendToServer(new MessageAction(MessageAction.CLOSE, 0));
        super.keyTyped(typedChar, keyCode);
    }

    /** The preview is a look, so leaving it costs nothing and the admin screen comes straight back. */
    private void backToAdmin() {
        click();
        this.mc.displayGuiScreen(new GuiAdminPanel());
    }

    private void rebuildDetail() {
        detail.clear();
        BoardData.Panel selected = panel();
        BoardData.Row row = quest();
        int width = detailW - 24;
        if (row == null) {
            add("Selecciona una mision de la lista.", Dynasty.MUTED);
            add("", 0);
            add("Cada panel es un tablón propio del mod y pertenece a una aldea.", Dynasty.HINT);
            add("Sus misiones son las de Custom NPCs; el staff decide su dificultad y sus tiempos.", Dynasty.HINT);
            add("El estado y el progreso los decide el servidor.", Dynasty.HINT);
            if (selected != null && selected.locked) {
                add("", 0);
                wrap("§c" + selected.reason, Dynasty.BAD, width);
            }
        } else {
            wrap("§l" + row.name, Dynasty.TITLE, width);
            add("Mision #" + row.id + "  ·  tipo: " + row.typeLabel, Dynasty.HINT);
            if (selected != null) {
                add("Panel: " + selected.name + "  ·  aldea: " + village(selected), Dynasty.HINT);
            }
            add(row.rules(), Dynasty.difficultyColor(row.difficulty));
            if (!row.gateOpen()) wrap("§c" + row.gateMissing(), Dynasty.BAD, width);
            add("Estado: " + (selected != null && selected.locked ? "panel bloqueado" : stateLine(row)),
                    stateColor(row));
            if (row.total > 1) add("Progreso: " + row.done + " de " + row.total, Dynasty.INFO);
            if (!row.rewards.trim().isEmpty()) wrap("Recompensas: " + row.rewards, Dynasty.ACCENT, width);
            if (!row.completer.trim().isEmpty()) {
                add("Entregar en: " + row.completer, Dynasty.BODY);
            }
            if (row.state == BoardData.DONE && !row.completeText.trim().isEmpty()) {
                add("", 0);
                wrap(row.completeText, Dynasty.HINT, width);
            }
            if (row.limitMin > 0 && row.state == BoardData.ACTIVE) {
                add(row.cooldownMin > 0 ? "Si se acaba el tiempo entra en su enfriamiento de "
                                + row.cooldownMin + " min." : "Si se acaba el tiempo se quita y vuelve a estar disponible.",
                        Dynasty.HINT);
            }
            if (row.repeatable) add("Repetible.", Dynasty.HINT);
            if (selected != null && selected.locked) add("Panel bloqueado: " + selected.reason, Dynasty.BAD);
            if (!row.text.trim().isEmpty()) {
                add("", 0);
                add("§lDescripcion", Dynasty.ACCENT);
                wrap(row.text, Dynasty.BODY, width);
            }
        }
        ClientState.detailScroll = clamp(ClientState.detailScroll, 0, maxDetailScroll());
    }

    private void add(String value, int color) {
        detail.add(new Line(value, color));
    }

    private void wrap(String value, int color, int width) {
        for (String line : this.fontRenderer.listFormattedStringToWidth(value, Math.max(40, width))) {
            add(line, color);
        }
    }

    private void section(String value, int x, int y, int width, String right) {
        draw("§l" + value, x, y, Dynasty.TITLE);
        Dynasty.rule(x, y + 10, width);
        if (right != null) {
            draw(right, x + width - this.fontRenderer.getStringWidth(right), y, Dynasty.HINT);
        }
    }

    private void hint(String value, int x, int y, int width) {
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(value, Math.max(40, width));
        for (int i = 0; i < lines.size() && i < 3; i++) {
            draw(lines.get(i), x, y + i * 10, Dynasty.HINT);
        }
    }

    private void draw(String value, float x, int y, int color) {
        this.fontRenderer.drawStringWithShadow(value, x, y, color);
    }

    private void scrollbar(int x, int size, int scroll, int visible) {
        int max = Math.max(0, size - visible);
        int track = bottom - contentTop();
        if (max <= 0 || track <= 6) return;
        int thumb = Math.max(10, track * visible / Math.max(1, size));
        int y = contentTop() + (track - thumb) * clamp(scroll, 0, max) / max;
        Dynasty.scrollbar(x, contentTop(), track, y, thumb);
    }

    private static String shortState(BoardData.Row row, BoardData.Panel panel) {
        if (panel.locked) return "bloqueada";
        if (row.state == BoardData.DONE) {
            if (row.cooldownLeft > 0) return "repetible en " + row.cooldownLeft + " min";
            return row.repeatable ? "terminada (repetible)" : "terminada";
        }
        if (row.state == BoardData.ACTIVE) {
            String head = row.total > 1 ? "activa " + row.done + "/" + row.total : "activa";
            int left = remaining(row);
            return left > 0 ? head + " · quedan " + BoardData.clock(left) : head;
        }
        if (row.cooldownLeft > 0) return "en enfriamiento " + row.cooldownLeft + " min";
        return row.acceptable ? "disponible" : "no disponible";
    }

    /**
     * What the list shows on the right of a row: the countdown while the mission runs, and the limit it
     * will have when accepted. A mission with no limit keeps the whole row for its name.
     */
    private static String limitLabel(BoardData.Row row) {
        if (row.limitMin <= 0) return "";
        if (row.state != BoardData.ACTIVE) return shortMinutes(row.limitMin);
        int left = remaining(row);
        return left > 0 ? BoardData.clock(left) : "agotado";
    }

    private static int limitColor(BoardData.Row row) {
        return row.state == BoardData.ACTIVE ? Dynasty.clockColor(remaining(row)) : Dynasty.MUTED;
    }

    /** Minutes in the short form the list can afford: 25m, 1h, 2h30. */
    private static String shortMinutes(int minutes) {
        if (minutes < 60) return minutes + "m";
        int rest = minutes % 60;
        return rest == 0 ? minutes / 60 + "h" : minutes / 60 + "h" + rest;
    }

    /** Status without the stale minutes, because the live line at the top of the pane carries the clock. */
    private static String stateLine(BoardData.Row row) {
        if (row.state == BoardData.ACTIVE && remaining(row) > 0) {
            return row.total > 1 ? "En curso " + row.done + "/" + row.total : "En curso";
        }
        return row.status();
    }

    /** The seconds left of a running mission, counted here between two updates from the server. */
    private static int remaining(BoardData.Row row) {
        if (row == null || row.state != BoardData.ACTIVE || row.limitSec <= 0) return 0;
        long spent = (System.currentTimeMillis() - ClientState.boardTakenAt) / 1000L;
        return (int) Math.max(0L, row.limitSec - spent);
    }

    private static int stateColor(BoardData.Row row) {
        if (row.state == BoardData.DONE) return Dynasty.STATE_DONE;
        if (row.state == BoardData.ACTIVE) return Dynasty.STATE_ACTIVE;
        return row.acceptable ? Dynasty.STATE_AVAILABLE : Dynasty.STATE_LOCKED;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Called when the server pushes a refreshed board while this screen is already open. */
    public void reload() {
        layout();
        clampScrolls();
        rebuildDetail();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static BoardData.Panel panel() {
        BoardData data = ClientState.data;
        if (ClientState.selectedPanel < 0 || ClientState.selectedPanel >= data.panels.size()) return null;
        return data.panels.get(ClientState.selectedPanel);
    }

    private static BoardData.Row quest() {
        BoardData.Panel panel = panel();
        if (panel == null || ClientState.selectedQuest < 0 || ClientState.selectedQuest >= panel.rows.size()) return null;
        return panel.rows.get(ClientState.selectedQuest);
    }

    /**
     * One mission with the panel it belongs to and the slot it holds there, so a flat list can still select
     * it exactly the way the panel's own rows do and the detail pane needs no second copy of anything.
     */
    private static final class Entry {
        private final BoardData.Panel panel;
        private final int at;
        private final int index;
        private final BoardData.Row row;

        private Entry(BoardData.Panel panel, int at, int index, BoardData.Row row) {
            this.panel = panel;
            this.at = at;
            this.index = index;
            this.row = row;
        }
    }

    /** One wrapped detail line plus the color its kind of content gets. */
    private static final class Line {
        private final String text;
        private final int color;

        private Line(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}
