package dev.sobrio.rebornquests.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.input.Mouse;

import dev.sobrio.rebornquests.AdminAction;
import dev.sobrio.rebornquests.BoardData;
import dev.sobrio.rebornquests.RQConfig;
import dev.sobrio.rebornquests.RebornQuests;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.SoundEvents;

/**
 * Staff screen, laid out like the Custom NPCs role screen it replaces: a tab strip, a table whose rows
 * carry a code, a description and an ACTUAL marker, and the action buttons at the bottom.
 *
 * <p>Nothing is written from here. Every click sends one key with its new value; the server checks the
 * staff node again, validates the value and answers with a refreshed screen, so no change needs a
 * restart.
 */
public class GuiAdminPanel extends GuiScreen {
    /** The screens that are not one step of the ladder; the ranks live inside Permisos y rangos. */
    private static final int[] FIXED_TABS = {RQConfig.TAB_PANELS, RQConfig.TAB_MISSIONS, RQConfig.TAB_LEVELS,
            RQConfig.TAB_PERMS, RQConfig.TAB_BOARD, RQConfig.TAB_AUDIT, RQConfig.TAB_STATE};
    private static final String[] FIXED_NAMES = {"Paneles", "Misiones", "Niveles", "Permisos y rangos", "Ajustes",
            "Auditoría", "Estado"};
    /** The three lists the access column of the Paneles form can hold. */
    private static final int ACCESS_HAND = 0;
    private static final int ACCESS_RANK = 1;
    private static final int ACCESS_NPCS = 2;
    /** The access column walks over in one order, so the heading word always says what comes next. */
    private static final int ACCESS_LAST = ACCESS_NPCS;
    /** How many of the NPC lists the Estado screen names one by one before it only counts them. */
    private static final int ROLE_ECHO = 4;
    private static final String LOOKUP = "@jugador";
    private static final String NEW_PANEL = "@nuevopanel";
    private static final int MARGIN = 12;
    private static final int ROW = 18;
    private static final int SETTING_ROW = 42;
    private static final int BTN = 18;
    private static final int LINE = 11;
    private static final int NODE_ROW = 14;
    private static final int[] DELTAS = {-5, -1, 1, 5};
    private static final String[] MARKS = {"-5", "-1", "+1", "+5"};
    private static final int[] TIME_DELTAS = {-60, -5, 5, 60};
    private static final String[] TIME_MARKS = {"-1h", "-5", "+5", "+1h"};
    private static final int STEP_W = 26;
    /** The preview button beside Recargar config. */
    private static final int PREVIEW_W = 84;
    /** Fields are created this wide so their text scroll never lands past the first characters. */
    private static final int FIELD_W = 220;

    private int top = 54;
    private int bottom = 190;
    private int panelX = MARGIN;
    private int panelW = 160;
    private int formX = MARGIN;
    private int formW = 240;
    private int lastX;
    private int lastY;
    private final Map<String, GuiTextField> fields = new HashMap<String, GuiTextField>();

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        ClientState.pendingRank = ClientState.NOT_STAGED;
        ClientState.pendingMin = ClientState.NOT_STAGED;
        layout();
        clampScrolls();
    }

    /** Everything is measured from the window, so a change of GUI scale just re-measures. */
    private void layout() {
        panelW = clamp(inner() * 38 / 100, 112, 250);
        panelX = MARGIN;
        formX = panelX + panelW + 6;
        formW = Math.max(150, this.width - MARGIN - formX);
        top = 54;
        bottom = this.height - 46;
        for (GuiTextField field : fields.values()) field.setVisible(false);
    }

    private int inner() {
        return this.width - 2 * MARGIN;
    }

    private int tabW() {
        return Math.max(42, inner() / FIXED_TABS.length);
    }

    private int contentTop() {
        return top + 22;
    }

    private int rows() {
        return Math.max(1, (bottom - contentTop() - 2) / ROW);
    }

    private int settingRows() {
        return Math.max(1, (bottom - contentTop() - 2) / SETTING_ROW);
    }

    private int maxPanelScroll() {
        return Math.max(0, ClientState.data.panels.size() - rows());
    }

    /** The ladder list of the Permisos y rangos tab, which has rows of its own. */
    private int maxRankScroll() {
        return Math.max(0, ladderGroups().size() - rows());
    }

    private int maxSettingScroll() {
        return Math.max(0, settingsList().size() - settingRows());
    }

    private int maxMissionScroll() {
        return Math.max(0, missionList().size() - rows());
    }

    private int maxNodeScroll() {
        BoardData.Panel selected = panel();
        return Math.max(0, (selected == null ? 0 : selected.nodes.size()) - listRows());
    }

    private int maxGrantScroll() {
        BoardData.Panel selected = panel();
        return Math.max(0, (selected == null ? 0 : accessList(selected).size()) - listRows());
    }

    /**
     * Which of the three lists the right column of the Paneles form holds: the names the staff added by
     * hand, the players who open the panel on their own, or the NPCs that carry its role.
     */
    private List<String> accessList(BoardData.Panel selected) {
        if (ClientState.accessView == ACCESS_RANK) return selected.byRank;
        if (ClientState.accessView == ACCESS_NPCS) return selected.npcs;
        return selected.grants;
    }

    /** How many rows both bottom lists get: everything between their heading and their fields. */
    private int listRows() {
        return Math.max(1, (addRowY() - 4 - nodesTop()) / NODE_ROW);
    }

    private void clampScrolls() {
        if (ClientState.rankView >= ladderGroups().size()) ClientState.rankView = -1;
        ClientState.adminScroll = clamp(ClientState.adminScroll, 0, maxPanelScroll());
        ClientState.rankScroll = clamp(ClientState.rankScroll, 0, maxRankScroll());
        ClientState.settingsScroll = clamp(ClientState.settingsScroll, 0, maxSettingScroll());
        ClientState.missionScroll = clamp(ClientState.missionScroll, 0, maxMissionScroll());
        ClientState.nodeScroll = clamp(ClientState.nodeScroll, 0, maxNodeScroll());
        ClientState.grantScroll = clamp(ClientState.grantScroll, 0, maxGrantScroll());
    }

    private int buttonY() {
        return this.height - 34;
    }

    /** The answer from the server reads as a status line, so it costs the form no rows. */
    private int noticeY() {
        return buttonY() - 10;
    }

    // ---------------------------------------------------------------------------------------------
    // rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        this.lastX = mouseX;
        this.lastY = mouseY;
        layout();
        clampScrolls();
        Dynasty.panel(MARGIN - 4, 8, this.width - 2 * (MARGIN - 4), this.height - 16);

        BoardData data = ClientState.data;
        centered("§lADMINISTRADOR DE MISIONES", 14, Dynasty.TITLE);
        centered("puente: " + data.bridge + "  ·  tu rango: " + data.rankLabel
                        + "  ·  modo: " + data.mode, 25, Dynasty.ACCENT);
        renderTabs();
        if (!data.notice.isEmpty()) centered(data.notice, noticeY(), Dynasty.GOLD);

        if (ClientState.adminTab == RQConfig.TAB_PANELS) {
            renderPanels();
            renderForm();
        } else if (ClientState.adminTab == RQConfig.TAB_MISSIONS) {
            renderPanelList();
            renderMissions();
        } else if (ClientState.adminTab == RQConfig.TAB_PERMS) {
            renderPerms();
        } else if (ClientState.adminTab == RQConfig.TAB_AUDIT) {
            renderAudit();
        } else if (ClientState.adminTab == RQConfig.TAB_STATE) {
            renderState();
        } else {
            renderSettings(heading(ClientState.adminTab), MARGIN, inner());
        }
        renderButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderTabs() {
        for (int i = 0; i < FIXED_TABS.length; i++) {
            drawTab(tabRect(i), FIXED_NAMES[i], FIXED_TABS[i] == ClientState.adminTab);
        }
        Dynasty.rule(MARGIN - 2, top - 4, inner() + 4);
    }

    private void drawTab(Rect rect, String text, boolean active) {
        Dynasty.box(rect.x, rect.y, rect.w, rect.h, active ? Dynasty.ROW_SELECTED : Dynasty.BUTTON);
        String label = Dynasty.fit(this.fontRenderer, text, rect.w - 6);
        draw(label, rect.x + (rect.w - this.fontRenderer.getStringWidth(label)) / 2F,
                rect.y + (rect.h - 8) / 2, active ? Dynasty.TITLE : Dynasty.HINT);
    }

    private Rect tabRect(int index) {
        int x = MARGIN + index * tabW();
        int w = index == FIXED_TABS.length - 1 ? this.width - MARGIN - x : tabW() - 2;
        return new Rect(x, 33, w, 16);
    }

    // ---------------------------------------------------------------------------------------------
    // Paneles
    // ---------------------------------------------------------------------------------------------

    private void renderPanels() {
        BoardData data = ClientState.data;
        Dynasty.box(panelX, top, panelW, bottom - top, Dynasty.LIST);
        section("PANELES Y ALDEAS", panelX + 8, top + 7, panelW - 16, String.valueOf(data.panels.size()));
        for (int i = 0; i < rows(); i++) {
            int index = ClientState.adminScroll + i;
            if (index >= data.panels.size()) break;
            BoardData.Panel item = data.panels.get(index);
            int y = contentTop() + i * ROW;
            boolean selected = index == ClientState.selectedAdmin;
            Dynasty.row(panelX + 5, y, panelW - 10, ROW - 2, selected,
                    Dynasty.hit(panelX + 5, y, panelW - 10, ROW - 2, lastX, lastY));
            draw("§l" + code(item.requiredRank), panelX + 11, y + 5, selected ? Dynasty.TITLE : Dynasty.ACCENT);
            String marker = selected ? "§lACTUAL" : item.rows.size() + " m.";
            int markerW = this.fontRenderer.getStringWidth(marker) + 14;
            draw(Dynasty.fit(this.fontRenderer, village(item) + "  ·  " + item.name, panelW - 42 - markerW),
                    panelX + 30, y + 5, selected ? Dynasty.BODY : Dynasty.MUTED);
            draw(marker, panelX + panelW - 10 - this.fontRenderer.getStringWidth(marker), y + 5,
                    selected ? Dynasty.GOLD : Dynasty.HINT);
        }
        if (data.panels.isEmpty()) {
            hint("Todavia no hay paneles. Escribe un nombre abajo y pulsa Crear panel.",
                    panelX + 10, contentTop() + 4, panelW - 20);
        }
        scrollbar(panelX + panelW - 4, data.panels.size(), ClientState.adminScroll, rows());
    }

    private void renderForm() {
        BoardData.Panel selected = panel();
        Dynasty.box(formX, top, formW, bottom - top, Dynasty.LIST);
        section("EL PANEL", formX + 8, top + 7, formW - 16, null);
        if (selected == null) {
            hint("Elige un panel de la tabla o crea uno nuevo abajo. Su nombre, su aldea, el rango que "
                    + "exige, sus permisos y su orden se aplican con el boton de abajo.",
                    formX + 10, contentTop() + 4, formW - 20);
            renderNewPanel();
            return;
        }
        draw(Dynasty.fit(this.fontRenderer, selected.rows.size() + " misiones  ·  "
                        + (selected.locked ? "tu no lo tienes: " + selected.reason : "abierto para ti"),
                formW - 20), formX + 10, contentTop() + 1,
                selected.locked ? Dynasty.BAD : Dynasty.HINT);

        int half = (formW - 26) / 2;
        int names = nameRowY();
        draw("Nombre", formX + 10, names - 9, Dynasty.ACCENT);
        draw("Aldea (a que aldea pertenecen sus misiones)", formX + 16 + half, names - 9, Dynasty.ACCENT);
        Rect namePlate = new Rect(formX + 10, names, half, BTN);
        Rect villagePlate = new Rect(formX + 16 + half, names, half, BTN);
        GuiTextField nameField = field(nameKey(selected), selected.name, 40);
        GuiTextField villageField = field(villageKey(selected), selected.category, 24);
        place(nameField, namePlate);
        place(villageField, villagePlate);
        Dynasty.fieldPlate(namePlate.x, namePlate.y, namePlate.w, namePlate.h, nameField.isFocused());
        Dynasty.fieldPlate(villagePlate.x, villagePlate.y, villagePlate.w, villagePlate.h, villageField.isFocused());
        nameField.drawTextBox();
        villageField.drawTextBox();

        int rankY = rankRowY();
        draw("Rango del jugador exigido", formX + 10, rankY - 9, Dynasty.ACCENT);
        Dynasty.button(this.fontRenderer, formX + 10, rankY, formW - 20, BTN,
                rankButton(stagedRank(selected)), lastX, lastY, ladderGroups().size() > 0);

        int stepY = stepRowY();
        draw("Misiones completadas minimas", formX + 10, stepY - 9, Dynasty.ACCENT);
        for (int i = 0; i < DELTAS.length; i++) {
            Dynasty.button(this.fontRenderer, stepRect(formX + 10, stepY, i).x, stepY, STEP_W, BTN,
                    MARKS[i], lastX, lastY, true);
        }
        draw("§l" + stagedMin(selected), formX + 10 + 4 * (STEP_W + 2) + 8F, stepY + 5, Dynasty.TITLE);

        int orderY = orderRowY();
        draw("Orden en el tablón", formX + 10, orderY - 9, Dynasty.ACCENT);
        int orderW = Math.max(46, (formW - 34) / 3);
        Dynasty.button(this.fontRenderer, formX + 10, orderY, orderW, BTN, "Subir", lastX, lastY,
                ClientState.selectedAdmin > 0);
        Dynasty.button(this.fontRenderer, formX + 16 + orderW, orderY, orderW, BTN, "Bajar", lastX, lastY,
                ClientState.selectedAdmin < ClientState.data.panels.size() - 1);
        Dynasty.button(this.fontRenderer, formX + 22 + 2 * orderW, orderY, orderW, BTN, "Por aldea",
                lastX, lastY, ClientState.data.panels.size() > 1);

        renderNodes(selected);
        renderGrants(selected);
        renderNewPanel();
    }

    /** The permissions the panel demands on top of the rank: a click on a row takes it away. */
    private void renderNodes(BoardData.Panel selected) {
        draw(Dynasty.fit(this.fontRenderer, "Permisos exigidos (todos) · clic", columnW()),
                nodesColX(), nodesTop() - 9, Dynasty.ACCENT);
        Dynasty.rule(nodesColX(), nodesTop() - 2, columnW());
        renderList(selected.nodes, ClientState.nodeScroll, nodesColX(), "sin permisos extra", true);
        renderAdd(nodesColX(), field(nodeKey(selected), "", 60));
    }

    /**
     * Who or what opens this panel, in the same column as the permissions: the names the staff added by
     * hand, the players online whose own rank, missions and nodes open it without help, or the NPCs that
     * carry its role. The last word of the heading moves to the next one, because one column holds the
     * three. A row of a list you are allowed to write takes that entry out.
     */
    private void renderGrants(BoardData.Panel selected) {
        int view = ClientState.accessView;
        draw(Dynasty.fit(this.fontRenderer, accessHeading(view), columnW() - accessToggleWidth() - 4),
                grantsColX(), nodesTop() - 9, Dynasty.ACCENT);
        String toggle = accessToggle(view);
        int toggleX = grantsColX() + columnW() - this.fontRenderer.getStringWidth(toggle);
        draw(toggle, toggleX, nodesTop() - 9,
                Dynasty.hit(toggleX, nodesTop() - 10, this.fontRenderer.getStringWidth(toggle), 10,
                        lastX, lastY) ? Dynasty.GOLD : Dynasty.HINT);
        Dynasty.rule(grantsColX(), nodesTop() - 2, columnW());
        renderList(accessList(selected), ClientState.grantScroll, grantsColX(), accessEmpty(view),
                view != ACCESS_RANK);
        if (view == ACCESS_RANK) {
            draw(Dynasty.fit(this.fontRenderer, accessDefinition(selected), columnW() - 10),
                    grantsColX() + 5, addRowY() + 6, Dynasty.HINT);
            return;
        }
        renderAdd(grantsColX(), view == ACCESS_NPCS
                ? field(npcKey(selected), "", 24) : field(grantKey(selected), "", 16));
    }

    /** What the access column is holding right now. */
    private static String accessHeading(int view) {
        if (view == ACCESS_RANK) return "Lo abren por su rango";
        if (view == ACCESS_NPCS) return "Los NPCs que lo abren · clic";
        return "Con acceso suelto · clic";
    }

    /** What the same column says when the list it holds is empty. */
    private static String accessEmpty(int view) {
        if (view == ACCESS_RANK) return "nadie conectado lo abre por su rango";
        if (view == ACCESS_NPCS) return "ningun NPC lo abre: el tablón lo da el comando";
        return "sin accesos sueltos";
    }

    /**
     * What the panel was defined as asking for, short enough to sit under its access column: the ladder step,
     * the minimum of completed missions and how many permission nodes are listed. The names above it are that
     * same definition applied to whoever is connected.
     */
    private String accessDefinition(BoardData.Panel selected) {
        return "piden " + code(selected.requiredRank) + " · " + selected.minFinished + " minimas · "
                + selected.nodes.size() + " permisos";
    }

    /** The word that swaps the access column; it is the heading's right end, so it never grows the row. */
    private String accessToggle(int view) {
        if (view == ACCESS_RANK) return "los NPCs";
        if (view == ACCESS_NPCS) return "a mano";
        return "por rango";
    }

    private int accessToggleWidth() {
        return this.fontRenderer.getStringWidth(accessToggle(ClientState.accessView)) + 4;
    }

    /** One clickable list column; hovering is the only decoration a row ever gets. */
    private void renderList(List<String> items, int scroll, int colX, String emptyText, boolean removable) {
        int listTop = nodesTop() + 2;
        for (int i = 0; i < listRows(); i++) {
            int index = scroll + i;
            if (index >= items.size()) break;
            int rowY = listTop + i * NODE_ROW;
            String value = items.get(index);
            boolean hovered = Dynasty.hit(colX, rowY, columnW(), NODE_ROW - 2, lastX, lastY);
            Dynasty.row(colX, rowY, columnW(), NODE_ROW - 2, false, hovered);
            draw(Dynasty.fit(this.fontRenderer, value, columnW() - 10), colX + 5, rowY + 4,
                    hovered ? (removable ? Dynasty.BAD : Dynasty.INFO) : Dynasty.BODY);
        }
        if (items.isEmpty()) {
            draw(Dynasty.fit(this.fontRenderer, emptyText, columnW() - 10), colX + 5, listTop + 4, Dynasty.HINT);
        }
    }

    /** The typed value and its Añadir button, both inside one column. */
    private void renderAdd(int colX, GuiTextField field) {
        Rect plate = addPlate(colX);
        Rect button = addButtonRect(colX);
        place(field, plate);
        Dynasty.fieldPlate(plate.x, plate.y, plate.w, plate.h, field.isFocused());
        field.drawTextBox();
        Dynasty.button(this.fontRenderer, button.x, button.y, button.w, BTN, "Añadir", lastX, lastY, true);
    }

    /** The create/delete row, always at the bottom of the Paneles form. */
    private void renderNewPanel() {
        Rect plate = newPanelPlate();
        Rect create = createRect();
        Rect delete = deleteRect();
        Rect seed = seedRect();
        GuiTextField field = field(NEW_PANEL, "", 40);
        place(field, plate);
        Dynasty.fieldPlate(plate.x, plate.y, plate.w, plate.h, field.isFocused());
        field.drawTextBox();
        Dynasty.button(this.fontRenderer, create.x, create.y, create.w, BTN, "Crear panel", lastX, lastY, true);
        Dynasty.button(this.fontRenderer, delete.x, delete.y, delete.w, BTN, "Borrar", lastX, lastY, panel() != null);
        Dynasty.button(this.fontRenderer, seed.x, seed.y, seed.w, BTN, "Por rango", lastX, lastY, true);
    }

    // ---------------------------------------------------------------------------------------------
    // Permisos y rangos: la ladder en la izquierda, lo elegido en la derecha
    // ---------------------------------------------------------------------------------------------

    /**
     * The one screen that holds both the ladder and the nodes. A rank is a row of a list here, not a tab of
     * its own: the strip above only ever carries the seven screens of the mod.
     */
    private void renderPerms() {
        renderLadder();
        if (ClientState.rankView >= 0) {
            renderRank(ClientState.rankView);
            return;
        }
        renderSettings("PERMISOS Y RANGOS", formX, formW);
    }

    /** Every step of the ladder, from the lowest to the highest, with the group of LuckPerms behind it. */
    private void renderLadder() {
        List<String> groups = ladderGroups();
        Dynasty.box(panelX, top, panelW, bottom - top, Dynasty.LIST);
        section("RANGOS", panelX + 8, top + 7, panelW - 16, String.valueOf(groups.size()));
        for (int i = 0; i < ladderRows(); i++) {
            int at = ClientState.rankScroll + i;
            if (at >= groups.size()) break;
            String group = groupAt(groups, at).trim();
            int y = contentTop() + i * ROW;
            boolean selected = at == ClientState.rankView;
            Dynasty.row(panelX + 5, y, panelW - 10, ROW - 2, selected,
                    Dynasty.hit(panelX + 5, y, panelW - 10, ROW - 2, lastX, lastY));
            draw("§l" + code(at), panelX + 11, y + 5, selected ? Dynasty.GOLD : Dynasty.ACCENT);
            draw(Dynasty.fit(this.fontRenderer, group.isEmpty() ? "sin grupo" : group, panelW - 54),
                    panelX + 42, y + 5, selected ? Dynasty.TITLE : Dynasty.MUTED);
        }
        if (groups.isEmpty()) {
            hint("La ladder esta vacia. Pulsa + rango y escribe el primer grupo de LuckPerms.",
                    panelX + 10, contentTop() + 4, panelW - 20);
        }
        scrollbar(panelX + panelW - 4, contentTop(), ladderToolY() - 4, groups.size(),
                ClientState.rankScroll, ladderRows());
        int y = ladderToolY();
        int half = (panelW - 16) / 2;
        Dynasty.button(this.fontRenderer, panelX + 6, y, half - 2, BTN, "+ rango", lastX, lastY, true);
        Dynasty.button(this.fontRenderer, panelX + 8 + half, y, panelW - 14 - half, BTN, "- rango",
                lastX, lastY, ClientState.rankView >= 0);
    }

    /** Rows of the ladder list: the bottom strip of the column belongs to + rango and - rango. */
    private int ladderRows() {
        return Math.max(1, (ladderToolY() - 4 - contentTop()) / ROW);
    }

    private int ladderToolY() {
        return bottom - 24;
    }

    /** The step itself: its LuckPerms group, the name it shows, its place in the ladder and its panels. */
    private void renderRank(int index) {
        List<String> groups = ladderGroups();
        if (index >= groups.size()) return;
        Dynasty.box(formX, top, formW, bottom - top, Dynasty.LIST);
        section("RANGO " + code(index), formX + 8, top + 7, formW - 16,
                "paso " + (index + 1) + " de " + groups.size());

        int half = (formW - 26) / 2;
        int rowY = contentTop() + 20;
        draw("Grupo de LuckPerms", formX + 10, rowY - 9, Dynasty.ACCENT);
        draw("Nombre que se muestra", formX + 16 + half, rowY - 9, Dynasty.ACCENT);
        Rect groupPlate = new Rect(formX + 10, rowY, half - 30, BTN);
        Rect labelPlate = new Rect(formX + 16 + half, rowY, half - 30, BTN);
        text(field(rankGroupKey(index), groupAt(groups, index), 40), groupPlate);
        text(field(rankLabelKey(index), groupAt(rankLabelList(), index), 40), labelPlate);
        Dynasty.button(this.fontRenderer, groupPlate.x + groupPlate.w + 4, rowY, 26, BTN, "OK", lastX, lastY, true);
        Dynasty.button(this.fontRenderer, labelPlate.x + labelPlate.w + 4, rowY, 26, BTN, "OK", lastX, lastY, true);

        int orderY = rowY + BTN + 16;
        draw("Orden en la ladder", formX + 10, orderY - 9, Dynasty.ACCENT);
        int orderW = Math.max(52, (formW - 34) / 3);
        Dynasty.button(this.fontRenderer, formX + 10, orderY, orderW, BTN, "Subir", lastX, lastY, index > 0);
        Dynasty.button(this.fontRenderer, formX + 16 + orderW, orderY, orderW, BTN, "Bajar", lastX, lastY,
                index < groups.size() - 1);
        Dynasty.button(this.fontRenderer, formX + 22 + 2 * orderW, orderY, orderW, BTN, "Sin LuckPerms",
                lastX, lastY, !groupAt(groups, index).equals(value("rango.noBridgeRank")));

        int open = 0;
        for (BoardData.Panel item : ClientState.data.panels) {
            if (item.requiredRank == index && !item.locked) open++;
        }
        draw(rankPanels(index).size() + " paneles exigen este rango  ·  " + open
                + " los tienes abiertos", formX + 10, orderY + BTN + 10, Dynasty.HINT);
        hint("Cuantas misiones hay que haber terminado para que un panel te abra este rango se pone en "
                + "la pestaña Paneles, junto al rango que exige.", formX + 10, orderY + BTN + 22, formW - 20, 2);
        draw("Pulsa otra vez el rango de la izquierda para volver a los permisos.",
                formX + 10, bottom - 16, Dynasty.MUTED);
    }

    private void text(GuiTextField field, Rect plate) {
        place(field, plate);
        Dynasty.fieldPlate(plate.x, plate.y, plate.w, plate.h, field.isFocused());
        field.drawTextBox();
    }

    private List<BoardData.Panel> rankPanels(int index) {
        List<BoardData.Panel> out = new ArrayList<BoardData.Panel>();
        for (BoardData.Panel item : ClientState.data.panels) {
            if (item.requiredRank == index) out.add(item);
        }
        return out;
    }

    private void clickRank(int index, int mouseX, int mouseY) {
        List<String> groups = ladderGroups();
        if (index >= groups.size()) return;
        int rowY = contentTop() + 20;
        int half = (formW - 26) / 2;
        Rect groupPlate = new Rect(formX + 10, rowY, half - 30, BTN);
        Rect labelPlate = new Rect(formX + 16 + half, rowY, half - 30, BTN);
        if (Dynasty.hit(groupPlate.x + groupPlate.w + 4, rowY, 26, BTN, mouseX, mouseY)) {
            click();
            sendRank(AdminAction.RANK_GROUP, index, textOf(rankGroupKey(index)));
            return;
        }
        if (Dynasty.hit(labelPlate.x + labelPlate.w + 4, rowY, 26, BTN, mouseX, mouseY)) {
            click();
            sendRank(AdminAction.RANK_LABEL, index, textOf(rankLabelKey(index)));
            return;
        }
        int orderY = rowY + BTN + 16;
        int orderW = Math.max(52, (formW - 34) / 3);
        if (Dynasty.hit(formX + 10, orderY, orderW, BTN, mouseX, mouseY) && index > 0) {
            click();
            sendRank(AdminAction.RANK_UP, index, "");
            return;
        }
        if (Dynasty.hit(formX + 16 + orderW, orderY, orderW, BTN, mouseX, mouseY) && index < groups.size() - 1) {
            click();
            sendRank(AdminAction.RANK_DOWN, index, "");
            return;
        }
        if (Dynasty.hit(formX + 22 + 2 * orderW, orderY, orderW, BTN, mouseX, mouseY)) {
            click();
            send(AdminAction.CONFIG, "rango.noBridgeRank", groupAt(groups, index));
        }
    }

    /**
     * The Permisos y rangos screen: the ladder column first, because its rows decide which of the two
     * right-hand lists is on screen.
     */
    private void clickPerms(int mouseX, int mouseY) {
        if (clickLadder(mouseX, mouseY)) return;
        if (ClientState.rankView >= 0) {
            clickRank(ClientState.rankView, mouseX, mouseY);
            return;
        }
        clickSettings(formX, formW, mouseX, mouseY);
    }

    /** A row opens that step of the ladder; pressing the one already open goes back to the permissions. */
    private boolean clickLadder(int mouseX, int mouseY) {
        int y = ladderToolY();
        int half = (panelW - 16) / 2;
        if (Dynasty.hit(panelX + 6, y, half - 2, BTN, mouseX, mouseY)) return addRank(ladderGroups().size() - 1);
        if (Dynasty.hit(panelX + 8 + half, y, panelW - 14 - half, BTN, mouseX, mouseY)) return dropRank();
        if (!Dynasty.hit(panelX + 5, contentTop(), panelW - 10, ladderRows() * ROW, mouseX, mouseY)) return false;
        int offset = mouseY - contentTop();
        if (offset % ROW >= ROW - 2) return false;
        int at = ClientState.rankScroll + offset / ROW;
        if (at < 0 || at >= ladderGroups().size()) return false;
        click();
        ClientState.rankView = ClientState.rankView == at ? -1 : at;
        return true;
    }

    private void sendRank(int kind, int index, String value) {
        RebornQuests.NET.sendToServer(new AdminAction(kind, "", cut(value, 110), index, 0));
    }

    private String textOf(String key) {
        GuiTextField field = fields.get(key);
        return field == null ? "" : field.getText().trim();
    }

    private static String groupAt(List<String> values, int index) {
        return index < values.size() ? values.get(index) : "";
    }

    private List<String> rankLabelList() {
        return split(value("rango.labels"), ",");
    }

    // ---------------------------------------------------------------------------------------------
    // Misiones
    // ---------------------------------------------------------------------------------------------

    /**
     * One list for both jobs: first the missions the list being edited already holds, then every other
     * mission of the pack. The first group is edited, the second one is added with a single click.
     */
    private List<BoardData.Quest> missionList() {
        BoardData data = ClientState.data;
        List<BoardData.Quest> out = new ArrayList<BoardData.Quest>();
        BoardData.Panel selected = panel();
        if (selected != null) {
            for (Integer id : targetIds()) {
                BoardData.Quest quest = questOf(selected, data, id.intValue());
                if (quest != null) out.add(quest);
            }
        }
        for (BoardData.Quest quest : data.catalog) {
            if (inTarget(quest.id)) continue;
            out.add(quest);
        }
        return out;
    }

    /** The row of the list: the panel's own row when it has one, and the catalog entry otherwise. */
    private static BoardData.Quest questOf(BoardData.Panel panel, BoardData data, int questId) {
        for (BoardData.Row row : panel.rows) {
            if (row.id != questId) continue;
            BoardData.Quest quest = new BoardData.Quest();
            quest.id = row.id;
            quest.name = row.name;
            quest.category = row.typeLabel;
            quest.panel = panel.name;
            return quest;
        }
        for (BoardData.Quest quest : data.catalog) {
            if (quest.id == questId) return quest;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Que lista esta editando el tab Misiones: la del panel, o la de un rango de una aldea
    // ---------------------------------------------------------------------------------------------

    /** The rank whose list is being edited, or -1 for the panel's own list. */
    private int editingRank() {
        List<String> groups = ladderGroups();
        if (groups.isEmpty() || ClientState.listRank < 0) return -1;
        return Math.min(ClientState.listRank, groups.size() - 1);
    }

    /** The ids the list being edited holds; a rank with no line of its own stands on the panel list. */
    private List<Integer> targetIds() {
        List<Integer> out = new ArrayList<Integer>();
        BoardData.Panel selected = panel();
        if (selected == null) return out;
        int rank = editingRank();
        String line = rank < 0 ? null : rankLine(selected.name, rank, ClientState.listVillage);
        if (line == null) {
            for (BoardData.Row row : selected.rows) out.add(Integer.valueOf(row.id));
            return out;
        }
        return lineIds(line);
    }

    /** True when the mission is inside the list being edited, which is what decides add or edit. */
    private boolean inTarget(int questId) {
        for (Integer id : targetIds()) {
            if (id.intValue() == questId) return true;
        }
        return false;
    }

    /** The stored line one rank of one village reads on this panel, or null when the panel list stands. */
    private String rankLine(String panelName, int rank, String village) {
        List<String> groups = ladderGroups();
        if (rank < 0 || rank >= groups.size()) return null;
        String group = groups.get(rank).trim();
        String wanted = village == null ? "" : village.trim();
        String any = null;
        for (String raw : ClientState.data.rankLists) {
            String[] parts = raw.split(";");
            if (parts.length < 4) continue;
            if (!parts[0].trim().equalsIgnoreCase(panelName)) continue;
            if (!parts[1].trim().equalsIgnoreCase(group)) continue;
            String stored = parts[2].trim();
            if (!wanted.isEmpty() && stored.equalsIgnoreCase(wanted)) return raw;
            if (stored.equals("*") && any == null) any = raw;
        }
        return wanted.isEmpty() ? any : null;
    }

    /** The quest ids one stored list line carries. */
    private static List<Integer> lineIds(String raw) {
        List<Integer> out = new ArrayList<Integer>();
        String[] parts = raw.split(";");
        if (parts.length < 4) return out;
        for (String id : parts[3].split(",")) {
            try {
                int questId = Integer.parseInt(id.trim());
                if (questId > 0) out.add(Integer.valueOf(questId));
            } catch (NumberFormatException ignored) {
                // A half-typed id is not worth a crash on the screen.
            }
        }
        return out;
    }

    /**
     * Where the two list selectors of the Misiones tab sit. They are under the line that names the panel, so
     * they cannot start at {@link #contentTop()}: that is where the panel line itself is written.
     */
    private int listControlTop() {
        return top + 29;
    }

    /** Where the mission rows of the Misiones tab start, under the two list selectors. */
    private int missionListTop() {
        return listControlTop() + 31;
    }

    /** Rows that fit under the selectors, which is what both the draw and the click measure. */
    private int missionRows() {
        return Math.max(1, (bottom - missionListTop() - 2) / ROW);
    }

    private String listLabel(int rank) {
        return rank < 0 ? "Lista: la del panel" : "Lista: " + code(rank);
    }

    private String villageLabel() {
        return ClientState.listVillage.isEmpty() ? "Aldea: cualquiera" : "Aldea: " + ClientState.listVillage;
    }

    private void renderPanelList() {
        BoardData.Panel selected = panel();
        List<BoardData.Quest> list = missionList();
        Dynasty.box(panelX, top, panelW, bottom - top, Dynasty.LIST);
        section("MISIONES", panelX + 8, top + 7, panelW - 16, String.valueOf(list.size()));
        draw(Dynasty.fit(this.fontRenderer, "Panel: " + (selected == null ? "-" : selected.name), panelW - 16),
                panelX + 8, top + 19, Dynasty.BODY);
        int rank = editingRank();
        int half = (panelW - 16) / 2;
        Dynasty.button(this.fontRenderer, panelX + 6, listControlTop(), panelW - 12, 13,
                listLabel(rank), lastX, lastY, true);
        Dynasty.button(this.fontRenderer, panelX + 6, listControlTop() + 15, half - 2, 13,
                villageLabel(), lastX, lastY, rank >= 0);
        Dynasty.button(this.fontRenderer, panelX + 8 + half, listControlTop() + 15, panelW - 14 - half, 13,
                "Quitar lista", lastX, lastY,
                selected != null && rank >= 0 && rankLine(selected.name, rank, ClientState.listVillage) != null);
        for (int i = 0; i < missionRows(); i++) {
            int index = ClientState.missionScroll + i;
            if (index >= list.size()) break;
            BoardData.Quest quest = list.get(index);
            int y = missionListTop() + i * ROW;
            boolean inside = inTarget(quest.id);
            boolean picked = quest.id == ClientState.selectedMission;
            Dynasty.row(panelX + 5, y, panelW - 10, ROW - 2, picked,
                    Dynasty.hit(panelX + 5, y, panelW - 10, ROW - 2, lastX, lastY));
            String mark = inside ? "§l[" + level(quest.id) + "]" : "+";
            draw(mark, panelX + 11, y + 5, inside ? Dynasty.difficultyColor(level(quest.id)) : Dynasty.ACCENT);
            draw(Dynasty.fit(this.fontRenderer, quest.name, panelW - 46), panelX + 27, y + 5,
                    picked ? Dynasty.TITLE : inside ? Dynasty.BODY : Dynasty.MUTED);
        }
        if (list.isEmpty()) {
            hint("Custom NPCs no tiene misiones todavia. Crea una en el editor de NPCs y vuelve aqui.",
                    panelX + 10, missionListTop() + 4, panelW - 20);
        }
        scrollbar(panelX + panelW - 4, missionListTop(), list.size(), ClientState.missionScroll, missionRows());
    }

    private void renderMissions() {
        BoardData.Panel selected = panel();
        Dynasty.box(formX, top, formW, bottom - top, Dynasty.LIST);
        section("LA MISION", formX + 8, top + 7, formW - 16, selected == null ? "sin panel" : selected.name);

        BoardData.Row row = mission();
        if (selected == null) {
            hint("Elige un panel en la pestaña Paneles. Despues, un clic en el + de una mision de la "
                    + "izquierda la pone en ese panel al momento.", formX + 10, contentTop() + 4, formW - 20);
            return;
        }
        if (row == null) {
            hint("Un clic en el + de una mision la añade al panel " + selected.name
                    + " sin reiniciar nada; vuelve a hacer clic sobre ella para poner su dificultad, su "
                    + "enfriamiento y su tiempo limite.", formX + 10, contentTop() + 4, formW - 20);
            return;
        }

        draw(Dynasty.fit(this.fontRenderer, "#" + row.id + "  " + row.name + "  ·  " + row.typeLabel, formW - 20),
                formX + 10, infoY(), Dynasty.BODY);
        if (!selected.locked && !row.acceptable && row.state == BoardData.OPEN) {
            draw("Custom NPCs no deja aceptarla ahora mismo.", formX + 10, infoY() + 11, Dynasty.HINT);
        }

        int levelY = levelRowY();
        draw("Dificultad de la mision (D a S; no es tu rango)", formX + 10, levelY - 9, Dynasty.ACCENT);
        Dynasty.button(this.fontRenderer, formX + 10, levelY, formW - 20, BTN,
                "Mision " + row.difficulty + "  \u25b8", lastX, lastY, levels().size() > 0);

        int cdY = cooldownRowY();
        draw("Enfriamiento al reaceptar", formX + 10, cdY - 9, Dynasty.ACCENT);
        timerRow(formX + 10, cdY, row.cooldownMin);

        int limitY = limitRowY();
        draw("Tiempo limite para terminarla", formX + 10, limitY - 9, Dynasty.ACCENT);
        timerRow(formX + 10, limitY, row.limitMin);
        draw("Si se acaba el tiempo sin terminarla, el enfriamiento empieza a contar.",
                formX + 10, limitY + BTN + 3, Dynasty.HINT);

        int valueY = valueRowY();
        Rect plate = new Rect(formX + 10, valueY, Math.max(50, formW - 170), BTN);
        GuiTextField field = field(valueKey(row.id), "", 12);
        place(field, plate);
        Dynasty.fieldPlate(plate.x, plate.y, plate.w, plate.h, field.isFocused());
        field.drawTextBox();
        Dynasty.button(this.fontRenderer, plate.x + plate.w + 4, valueY, 76, BTN, "Poner enfri.", lastX, lastY, true);
        Dynasty.button(this.fontRenderer, plate.x + plate.w + 84, valueY, 80, BTN, "Poner limite", lastX, lastY, true);
        draw("minutos, o no / desactivado / deshabilitado · Enter pone enfriamiento",
                formX + 10, valueY + BTN + 3, Dynasty.HINT);

        Dynasty.button(this.fontRenderer, formX + 10, bottom - 22, Math.max(80, formW - 20), BTN,
                editMode() ? "Quitar de la lista" : "Quitar del panel", lastX, lastY, true);
    }

    /** True when the Misiones tab is pointing at one rank's list instead of the panel's own. */
    private boolean editMode() {
        return editingRank() >= 0;
    }

    /** The five rows of the mission form; render and click measure them from the same place. */
    private int infoY() {
        return contentTop() + 4;
    }

    private int levelRowY() {
        return contentTop() + 32;
    }

    private int cooldownRowY() {
        return levelRowY() + BTN + 20;
    }

    private int limitRowY() {
        return cooldownRowY() + BTN + 20;
    }

    private int valueRowY() {
        return limitRowY() + BTN + 26;
    }

    private void timerRow(int x, int y, int minutes) {
        for (int i = 0; i < TIME_DELTAS.length; i++) {
            Dynasty.button(this.fontRenderer, stepRect(x, y, i).x, y, STEP_W, BTN, TIME_MARKS[i], lastX, lastY, true);
        }
        draw("§l" + time(minutes), x + 4 * (STEP_W + 2) + 8F, y + 5,
                minutes <= 0 ? Dynasty.HINT : Dynasty.TITLE);
    }

    // ---------------------------------------------------------------------------------------------
    // Ajustes / Rangos / Permisos
    // ---------------------------------------------------------------------------------------------

    /**
     * The list of editable settings, in the column it was given: full width on its own tab, the right column
     * of Permisos y rangos when no rank is open.
     */
    private void renderSettings(String heading, int x, int width) {
        List<BoardData.Setting> list = settingsList();
        Dynasty.box(x, top, width, bottom - top, Dynasty.LIST);
        section(heading, x + 8, top + 7, width - 16, list.size() + " ajustes");
        for (int i = 0; i < settingRows(); i++) {
            int index = ClientState.settingsScroll + i;
            if (index >= list.size()) break;
            BoardData.Setting setting = list.get(index);
            int y = contentTop() + i * SETTING_ROW;
            Dynasty.row(x + 5, y, width - 10, SETTING_ROW - 4, false,
                    Dynasty.hit(x + 5, y, width - 10, SETTING_ROW - 4, lastX, lastY));
            draw(Dynasty.fit(this.fontRenderer, setting.label, labelW(width) - 12), x + 11, y + 4, Dynasty.BODY);
            hint(setting.help, x + 11, y + 16, labelW(width) - 12, 2);
            renderControl(setting, control(x, y, width, setting));
        }
        if (list.isEmpty()) {
            hint("La version del server no manda ajustes de esta pestaña.", x + 12, contentTop() + 4, width - 24);
        }
        scrollbar(x + width - 4, list.size(), ClientState.settingsScroll, settingRows());
    }

    /**
     * The rows of the tab on screen. Permisos y rangos owns the rank rows too: the ladder is edited there,
     * so its three config lines have to be reachable from the same place.
     */
    private List<BoardData.Setting> settingsList() {
        List<BoardData.Setting> out = new ArrayList<BoardData.Setting>(ClientState.data.tab(ClientState.adminTab));
        if (ClientState.adminTab == RQConfig.TAB_PERMS) {
            out.addAll(ClientState.data.tab(RQConfig.TAB_RANKS));
        }
        return out;
    }

    /** The control picked by the setting type; its rectangle is shared with the click handler. */
    private void renderControl(BoardData.Setting setting, Rect rect) {
        if (setting.type == BoardData.BOOL) {
            Dynasty.button(this.fontRenderer, rect.x, rect.y, Math.min(76, rect.w), BTN,
                    setting.display() + " \u25b8", lastX, lastY, true);
            return;
        }
        if (setting.type == BoardData.CHOICE) {
            Dynasty.button(this.fontRenderer, rect.x, rect.y, rect.w, BTN,
                    setting.display() + " \u25b8", lastX, lastY, setting.optionList().size() > 0);
            return;
        }
        if (setting.type == BoardData.INT) {
            for (int i = 0; i < DELTAS.length; i++) {
                Dynasty.button(this.fontRenderer, stepRect(rect.x, rect.y, i).x, rect.y, STEP_W, BTN,
                        MARKS[i], lastX, lastY, true);
            }
            draw("§l" + setting.value, rect.x + 4 * (STEP_W + 2) + 8F, rect.y + 5, Dynasty.TITLE);
            return;
        }
        Rect box = textRect(rect);
        GuiTextField field = field(setting.key, setting.value, 100);
        place(field, box);
        Dynasty.fieldPlate(box.x, box.y, box.w, box.h, field.isFocused());
        field.drawTextBox();
        Dynasty.button(this.fontRenderer, rect.x + rect.w - 56, rect.y, 56, BTN, "Guardar", lastX, lastY, true);
    }

    private void renderState() {
        BoardData data = ClientState.data;
        Dynasty.box(MARGIN, top, inner(), bottom - top, Dynasty.LIST);
        section("ESTADO DEL SERVIDOR", MARGIN + 8, top + 7, inner() - 16, null);
        int y = contentTop() + 2;
        y = line(y, "Servidor: " + data.mode + "  ·  puente de permisos: " + data.bridge, Dynasty.BODY);
        y = line(y, "Custom NPCs: " + data.cnpc + "  ·  paneles leidos: " + data.panels.size()
                + "  ·  misiones del pack: " + data.catalog.size(), Dynasty.BODY);
        y = line(y, "Aldeas: " + (data.villages.isEmpty() ? "sin aldeas" : data.villages)
                + "  ·  niveles: " + data.levels, Dynasty.ACCENT);
        y = line(y, "Que pide cada nivel: " + levelGates(), Dynasty.BODY);
        y = line(y, data.adminNode.trim().isEmpty()
                ? "Mando: el op 4, porque el nodo de administracion esta vacio."
                : "Mando: el nodo " + data.adminNode + " de LuckPerms. El op no abre nada aqui.", Dynasty.ACCENT);
        y = line(y, "Tu rango: " + data.rankLabel + "  ·  grupo: "
                + (data.rankGroup.isEmpty() ? "sin grupo" : data.rankGroup), Dynasty.BODY);
        y = line(y, "Tu aldea: " + (data.village.isEmpty() ? "sin nodo de aldea" : data.village)
                + "  ·  listas del rango: bajas hasta " + listDepth(data), Dynasty.BODY);
        y = line(y, "Misiones: " + data.finished + " completadas  ·  " + data.active + " activas", Dynasty.BODY);
        y = line(y, "Ladder: " + (data.ladder.isEmpty() ? "sin grupos" : data.ladder), Dynasty.HINT);
        y = line(y, "Registro de cambios: " + ("true".equalsIgnoreCase(value("general.audit"))
                ? "activado, cada " + value("general.auditSeconds") + " s" : "parado")
                + "  ·  " + data.audit.size() + " entradas a la vista  ·  dentro del mod no hay forma de borrarlo",
                Dynasty.GOOD);
        y = line(y, "Rol del mod: " + boardNpcs(), Dynasty.HINT);
        y = line(y, "Comando: /" + value("general.commandName") + " sin argumentos abre esto  ·  alias: "
                + value("general.commandAliases"), Dynasty.HINT);
        y = line(y, "Nada de lo editable aqui necesita reiniciar el server.", Dynasty.GOOD);
        y = line(y, "Ajustes editables que manda el server: " + data.settings.size(), Dynasty.HINT);
        if (!data.error.isEmpty()) line(y, "Aviso: " + data.error, Dynasty.BAD);

        draw("Consulta de un jugador", MARGIN + 11, bottom - 40, Dynasty.ACCENT);
        Rect plate = lookupField();
        GuiTextField field = field(LOOKUP, ClientState.lookupName, 24);
        place(field, plate);
        Dynasty.fieldPlate(plate.x, plate.y, plate.w, plate.h, field.isFocused());
        field.drawTextBox();
        Dynasty.button(this.fontRenderer, plate.x + plate.w + 4, plate.y, 72, BTN, "Consultar", lastX, lastY, true);
    }

    /**
     * The record, read and never touched. The newest entries travel with the rest of the board, and what
     * does not fit on the screen is a page of the console listing away. There is no button here that
     * clears, hides or deletes an entry, and that is the whole point: the record exists to hold the person
     * being audited accountable, so nobody inside the game can make it shorter.
     */
    private void renderAudit() {
        BoardData data = ClientState.data;
        Dynasty.box(MARGIN, top, inner(), bottom - top, Dynasty.LIST);
        section("REGISTRO DE CAMBIOS  ·  SOLO LECTURA", MARGIN + 8, top + 7, inner() - 16,
                "el mas reciente arriba");
        int y = contentTop() + 2;
        if (data.audit.isEmpty()) {
            y = line(y, "Nada anotado todavia. El registro empieza a escribirse con el primer cambio de un "
                    + "panel, una mision, un dialogo o un script.", Dynasty.GOOD);
            line(y, "Registro " + ("true".equalsIgnoreCase(value("general.audit")) ? "activado" : "parado")
                    + "  ·  los archivos de Custom NPCs se revisan cada " + value("general.auditSeconds") + " s.",
                    Dynasty.HINT);
            return;
        }
        y = line(y, "Ajustes que llegan al registro: " + value("general.audit") + "  ·  cada "
                + value("general.auditSeconds") + " s  ·  lo que no cabe aqui se lista desde la consola con «log».",
                Dynasty.HINT);
        for (int i = data.audit.size() - 1; i >= 0; i--) {
            String[] parts = data.audit.get(i).split("\t", 5);
            String when = parts.length > 0 ? parts[0] : "";
            String who = parts.length > 1 ? parts[1] : "";
            String kind = parts.length > 2 ? parts[2] : "";
            String subject = parts.length > 3 ? parts[3] : "";
            String detail = parts.length > 4 ? parts[4] : "";
            y = line(y, "§e" + when + " §7· " + who + " · " + kind, Dynasty.ACCENT);
            y = line(y, "§f" + subject, Dynasty.BODY);
            if (!detail.isEmpty()) y = line(y, "§8" + detail, Dynasty.HINT);
            if (y > bottom - 52) {
                line(y, "... " + (i + 1) + " entradas mas antiguas, en la consola con «log».", Dynasty.HINT);
                return;
            }
        }
    }

    private void renderButtons() {
        int y = buttonY();
        Dynasty.button(this.fontRenderer, MARGIN, y, 104, BTN, "Recargar config", lastX, lastY, true);
        Dynasty.button(this.fontRenderer, previewX(), y, PREVIEW_W, BTN, "Vista previa", lastX, lastY, panel() != null);
        if (ClientState.adminTab == RQConfig.TAB_PANELS) {
            Dynasty.button(this.fontRenderer, this.width - MARGIN - 196, y, 104, BTN,
                    "Aplicar panel", lastX, lastY, panel() != null);
        }
        Dynasty.button(this.fontRenderer, this.width - MARGIN - 88, y, 88, BTN, "Salir", lastX, lastY, true);
    }

    /** The preview button sits next to Recargar, so it never crowds the right-hand pair. */
    private int previewX() {
        return MARGIN + 110;
    }

    /**
     * The board as a player sees it, drawn from the data the admin screen already holds. It is a look,
     * not a second editor: nothing is sent while it is open, and Volver brings this screen back.
     */
    private void openPreview() {
        if (panel() == null) return;
        click();
        ClientState.selectedPanel = ClientState.selectedAdmin;
        ClientState.selectedQuest = -1;
        ClientState.questScroll = 0;
        ClientState.detailScroll = 0;
        this.mc.displayGuiScreen(new GuiMisiones(true));
    }

    // ---------------------------------------------------------------------------------------------
    // input
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (GuiTextField field : fields.values()) {
            if (field.getVisible()) field.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton != 0) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (clickTab(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        int y = buttonY();
        if (Dynasty.hit(MARGIN, y, 104, BTN, mouseX, mouseY)) {
            click();
            send(AdminAction.RELOAD, "", "");
            return;
        }
        if (Dynasty.hit(previewX(), y, PREVIEW_W, BTN, mouseX, mouseY)) {
            openPreview();
            return;
        }
        if (Dynasty.hit(this.width - MARGIN - 88, y, 88, BTN, mouseX, mouseY)) {
            click();
            this.mc.displayGuiScreen(null);
            return;
        }
        if (ClientState.adminTab == RQConfig.TAB_PANELS) {
            if (clickPanelList(mouseX, mouseY) || clickForm(mouseX, mouseY)) {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
            if (Dynasty.hit(this.width - MARGIN - 196, y, 104, BTN, mouseX, mouseY)) applyPanel();
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (ClientState.adminTab == RQConfig.TAB_MISSIONS) {
            clickMissions(mouseX, mouseY);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (ClientState.adminTab == RQConfig.TAB_PERMS) {
            clickPerms(mouseX, mouseY);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (ClientState.adminTab == RQConfig.TAB_STATE) {
            clickState(mouseX, mouseY);
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        clickSettings(MARGIN, inner(), mouseX, mouseY);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean clickTab(int mouseX, int mouseY) {
        for (int i = 0; i < FIXED_TABS.length; i++) {
            if (tabRect(i).hit(mouseX, mouseY)) return selectTab(FIXED_TABS[i]);
        }
        return false;
    }

    /**
     * Alta de un rango: el nuevo paso entra debajo del ultimo, con un grupo de LuckPerms escrito por el
     * mod, y se abre en el acto para cambiarle el grupo y el nombre que se muestra.
     */
    private boolean addRank(int after) {
        click();
        ClientState.rankView = after + 1;
        ClientState.rankScroll = Math.max(0, ClientState.rankView - ladderRows() + 1);
        sendRank(AdminAction.RANK_ADD, after, "");
        return true;
    }

    /** Baja del rango abierto en la lista; el server la niega si un panel todavia lo exige. */
    private boolean dropRank() {
        int index = ClientState.rankView;
        if (index < 0) return true;
        click();
        ClientState.rankView = index - 1;
        sendRank(AdminAction.RANK_DEL, index, "");
        return true;
    }

    private boolean selectTab(int tab) {
        if (tab == ClientState.adminTab) return true;
        click();
        ClientState.adminTab = tab;
        ClientState.settingsScroll = 0;
        ClientState.missionScroll = 0;
        ClientState.pendingRank = ClientState.NOT_STAGED;
        ClientState.pendingMin = ClientState.NOT_STAGED;
        return true;
    }

    private boolean clickPanelList(int mouseX, int mouseY) {
        if (!Dynasty.hit(panelX + 5, contentTop(), panelW - 10, rows() * ROW, mouseX, mouseY)) return false;
        int offset = mouseY - contentTop();
        if (offset % ROW >= ROW - 2) return false;
        int index = ClientState.adminScroll + offset / ROW;
        if (index < 0 || index >= ClientState.data.panels.size()) return false;
        click();
        if (ClientState.selectedAdmin != index) {
            ClientState.selectedAdmin = index;
            ClientState.selectedMission = 0;
            ClientState.pendingRank = ClientState.NOT_STAGED;
            ClientState.pendingMin = ClientState.NOT_STAGED;
        }
        return true;
    }

    /** True when the click landed on a Paneles control, so the settings code never sees it. */
    private boolean clickForm(int mouseX, int mouseY) {
        int newPanelY = bottom - 22;
        if (panel() == null) {
            if (Dynasty.hit(createRect().x, newPanelY, createRect().w, BTN, mouseX, mouseY)) createPanel();
            if (Dynasty.hit(seedRect().x, newPanelY, seedRect().w, BTN, mouseX, mouseY)) {
                click();
                RebornQuests.NET.sendToServer(new AdminAction(AdminAction.PANEL_SEED, "", "", -1, 0));
            }
            return Dynasty.hit(formX, top, formW, bottom - top, mouseX, mouseY);
        }
        BoardData.Panel selected = panel();
        if (clickNodes(selected, mouseX, mouseY)) return true;
        if (clickGrants(selected, mouseX, mouseY)) return true;
        if (clickAdd(addButtonRect(nodesColX()), nodeKey(selected), AdminAction.NODE_ADD, mouseX, mouseY)) return true;
        if (ClientState.accessView != ACCESS_RANK && clickAdd(addButtonRect(grantsColX()),
                accessKey(selected), ClientState.accessView == ACCESS_NPCS
                        ? AdminAction.NPC_ADD : AdminAction.GRANT_ADD, mouseX, mouseY)) return true;
        int rankY = rankRowY();
        if (Dynasty.hit(formX + 10, rankY, formW - 20, BTN, mouseX, mouseY)) {
            click();
            int size = ladderGroups().size();
            int next = stagedRank(selected) + 1;
            ClientState.pendingRank = size == 0 || next >= size ? -1 : next;
            return true;
        }
        int stepY = stepRowY();
        for (int i = 0; i < DELTAS.length; i++) {
            if (stepRect(formX + 10, stepY, i).hit(mouseX, mouseY)) {
                click();
                ClientState.pendingMin = Math.max(0, stagedMin(selected) + DELTAS[i]);
                return true;
            }
        }
        int orderY = orderRowY();
        int orderW = Math.max(46, (formW - 34) / 3);
        if (Dynasty.hit(formX + 10, orderY, orderW, BTN, mouseX, mouseY)) {
            click();
            sendPanel(AdminAction.PANEL_UP, "", -1, 0);
            return true;
        }
        if (Dynasty.hit(formX + 16 + orderW, orderY, orderW, BTN, mouseX, mouseY)) {
            click();
            sendPanel(AdminAction.PANEL_DOWN, "", -1, 0);
            return true;
        }
        if (Dynasty.hit(formX + 22 + 2 * orderW, orderY, orderW, BTN, mouseX, mouseY)) {
            click();
            RebornQuests.NET.sendToServer(new AdminAction(AdminAction.PANEL_GROUP, "", "", -1, 0));
            return true;
        }
        if (Dynasty.hit(createRect().x, newPanelY, createRect().w, BTN, mouseX, mouseY)) {
            createPanel();
            return true;
        }
        if (Dynasty.hit(deleteRect().x, newPanelY, deleteRect().w, BTN, mouseX, mouseY)) {
            click();
            sendPanel(AdminAction.PANEL_DEL, "", -1, 0);
            ClientState.selectedMission = 0;
            return true;
        }
        if (Dynasty.hit(seedRect().x, newPanelY, seedRect().w, BTN, mouseX, mouseY)) {
            click();
            RebornQuests.NET.sendToServer(new AdminAction(AdminAction.PANEL_SEED, "", "", -1, 0));
            return true;
        }
        return Dynasty.hit(formX, top, formW, bottom - top, mouseX, mouseY);
    }

    /** A row of the permission list removes that permission. */
    private boolean clickNodes(BoardData.Panel selected, int mouseX, int mouseY) {
        int index = clickList(selected.nodes, ClientState.nodeScroll, nodesColX(), mouseX, mouseY);
        if (index < 0) return false;
        if (index >= selected.nodes.size()) return true;
        click();
        sendPanel(AdminAction.NODE_DEL, selected.nodes.get(index), -1, 0);
        return true;
    }

    /**
     * The access column of the Paneles form: its heading word moves to the next list, and a row of one the
     * staff writes takes that player or NPC out of the panel again. The rank's own list is only read.
     */
    private boolean clickGrants(BoardData.Panel selected, int mouseX, int mouseY) {
        if (clickAccessToggle(mouseX, mouseY)) return true;
        if (ClientState.accessView == ACCESS_RANK) {
            return clickList(selected.byRank, ClientState.grantScroll, grantsColX(), mouseX, mouseY) >= 0;
        }
        boolean npcs = ClientState.accessView == ACCESS_NPCS;
        List<String> items = npcs ? selected.npcs : selected.grants;
        int index = clickList(items, ClientState.grantScroll, grantsColX(), mouseX, mouseY);
        if (index < 0) return false;
        if (index >= items.size()) return true;
        click();
        sendPanel(npcs ? AdminAction.NPC_DEL : AdminAction.GRANT_DEL, items.get(index), -1, 0);
        return true;
    }

    /** The last word of the access heading, which is the only control that changes which list is shown. */
    private boolean clickAccessToggle(int mouseX, int mouseY) {
        String toggle = accessToggle(ClientState.accessView);
        int x = grantsColX() + columnW() - this.fontRenderer.getStringWidth(toggle);
        if (!Dynasty.hit(x - 3, nodesTop() - 10, this.fontRenderer.getStringWidth(toggle) + 3, 10,
                mouseX, mouseY)) return false;
        click();
        ClientState.accessView = ClientState.accessView >= ACCESS_LAST ? ACCESS_HAND : ClientState.accessView + 1;
        ClientState.grantScroll = 0;
        return true;
    }

    /** The row a click landed on in one list column, or -1 when the click was somewhere else. */
    private int clickList(List<String> items, int scroll, int colX, int mouseX, int mouseY) {
        int listTop = nodesTop() + 2;
        if (!Dynasty.hit(colX, listTop, columnW(), listRows() * NODE_ROW, mouseX, mouseY)) return -1;
        return scroll + (mouseY - listTop) / NODE_ROW;
    }

    /** The Añadir button of one column, which is the same thing Enter on its field does. */
    private boolean clickAdd(Rect button, String key, int kind, int mouseX, int mouseY) {
        GuiTextField typed = fields.get(key);
        if (typed == null || !button.hit(mouseX, mouseY)) return false;
        String value = typed.getText().trim();
        if (value.isEmpty()) return true;
        click();
        sendPanel(kind, value, -1, 0);
        typed.setText("");
        return true;
    }

    private void clickMissions(int mouseX, int mouseY) {
        BoardData.Panel selected = panel();
        if (clickListSelectors(mouseX, mouseY)) return;
        int listTop = missionListTop();
        if (Dynasty.hit(panelX + 5, listTop, panelW - 10, missionRows() * ROW, mouseX, mouseY)) {
            int offset = mouseY - listTop;
            if (offset % ROW >= ROW - 2) return;
            int index = ClientState.missionScroll + offset / ROW;
            List<BoardData.Quest> list = missionList();
            if (index < 0 || index >= list.size()) return;
            BoardData.Quest quest = list.get(index);
            click();
            if (inTarget(quest.id)) {
                ClientState.selectedMission = ClientState.selectedMission == quest.id ? 0 : quest.id;
                return;
            }
            if (selected == null) return;
            ClientState.selectedMission = quest.id;
            sendPanelList(AdminAction.MISSION_ADD, quest.id);
            return;
        }
        BoardData.Row row = mission();
        if (row == null) return;
        if (Dynasty.hit(formX + 10, levelRowY(), formW - 20, BTN, mouseX, mouseY)) {
            click();
            sendMission(AdminAction.MISSION_DIFF, row.id, nextLevel(row.difficulty));
            return;
        }
        for (int i = 0; i < TIME_DELTAS.length; i++) {
            if (!stepRect(formX + 10, cooldownRowY(), i).hit(mouseX, mouseY)) continue;
            click();
            sendMission(AdminAction.MISSION_COOLDOWN, row.id, valueOf(row.cooldownMin + TIME_DELTAS[i]));
            return;
        }
        for (int i = 0; i < TIME_DELTAS.length; i++) {
            if (!stepRect(formX + 10, limitRowY(), i).hit(mouseX, mouseY)) continue;
            click();
            sendMission(AdminAction.MISSION_LIMIT, row.id, valueOf(row.limitMin + TIME_DELTAS[i]));
            return;
        }
        int valueY = valueRowY();
        Rect plate = new Rect(formX + 10, valueY, Math.max(50, formW - 170), BTN);
        GuiTextField typed = fields.get(valueKey(row.id));
        String raw = typed == null ? "" : typed.getText().trim();
        if (Dynasty.hit(plate.x + plate.w + 4, valueY, 76, BTN, mouseX, mouseY)) {
            click();
            sendMission(AdminAction.MISSION_COOLDOWN, row.id, raw);
            return;
        }
        if (Dynasty.hit(plate.x + plate.w + 84, valueY, 80, BTN, mouseX, mouseY)) {
            click();
            sendMission(AdminAction.MISSION_LIMIT, row.id, raw);
            return;
        }
        if (Dynasty.hit(formX + 10, bottom - 22, Math.max(80, formW - 20), BTN, mouseX, mouseY)) {
            click();
            sendPanelList(AdminAction.MISSION_DEL, row.id);
            ClientState.selectedMission = 0;
        }
    }

    /**
     * The two selectors of the Misiones list: which rank's list is being edited, and for which village.
     * They only move what the screen points at, so a cycle costs no round trip until a mission is added
     * or taken out.
     */
    private boolean clickListSelectors(int mouseX, int mouseY) {
        List<String> groups = ladderGroups();
        int selectors = listControlTop();
        if (Dynasty.hit(panelX + 6, selectors, panelW - 12, 13, mouseX, mouseY)) {
            click();
            ClientState.listRank = ClientState.listRank >= groups.size() - 1 ? -1 : ClientState.listRank + 1;
            return true;
        }
        if (Dynasty.hit(panelX + 6, selectors + 15, (panelW - 16) / 2 - 2, 13, mouseX, mouseY)) {
            if (editingRank() < 0) return true;
            click();
            ClientState.listVillage = nextVillage(ClientState.listVillage);
            return true;
        }
        if (Dynasty.hit(panelX + 8 + (panelW - 16) / 2, selectors + 15, panelW - 14 - (panelW - 16) / 2, 13,
                mouseX, mouseY)) {
            BoardData.Panel selected = panel();
            if (selected == null || editingRank() < 0) return true;
            if (rankLine(selected.name, editingRank(), ClientState.listVillage) == null) return true;
            click();
            sendPanelList(AdminAction.MISSION_LIST, 0);
            return true;
        }
        return false;
    }

    /** Steps over "cualquiera" and every aldea the staff declared. */
    private String nextVillage(String current) {
        List<String> villages = split(ClientState.data.villages, ",");
        if (villages.isEmpty()) return "";
        if (current == null || current.isEmpty()) return villages.get(0);
        for (int i = 0; i < villages.size(); i++) {
            if (!villages.get(i).equalsIgnoreCase(current)) continue;
            return i + 1 < villages.size() ? villages.get(i + 1) : "";
        }
        return villages.get(0);
    }

    private void clickSettings(int x, int width, int mouseX, int mouseY) {
        List<BoardData.Setting> list = settingsList();
        for (int i = 0; i < settingRows(); i++) {
            int index = ClientState.settingsScroll + i;
            if (index >= list.size()) break;
            BoardData.Setting setting = list.get(index);
            int y = contentTop() + i * SETTING_ROW;
            if (!Dynasty.hit(x + 5, y, width - 10, SETTING_ROW - 4, mouseX, mouseY)) continue;
            act(setting, control(x, y, width, setting), mouseX, mouseY);
            return;
        }
    }

    /** One row's control: text saves, booleans and choices cycle, numbers step. */
    private void act(BoardData.Setting setting, Rect rect, int mouseX, int mouseY) {
        if (setting.type == BoardData.BOOL) {
            if (!Dynasty.hit(rect.x, rect.y, Math.min(76, rect.w), BTN, mouseX, mouseY)) return;
            click();
            send(AdminAction.CONFIG, setting.key, Boolean.toString(!Boolean.parseBoolean(setting.value)));
            return;
        }
        if (setting.type == BoardData.CHOICE) {
            List<String> options = setting.optionList();
            if (!Dynasty.hit(rect.x, rect.y, rect.w, BTN, mouseX, mouseY) || options.isEmpty()) return;
            click();
            send(AdminAction.CONFIG, setting.key, nextOption(setting, options));
            return;
        }
        if (setting.type == BoardData.INT) {
            for (int i = 0; i < DELTAS.length; i++) {
                if (!stepRect(rect.x, rect.y, i).hit(mouseX, mouseY)) continue;
                click();
                int next = intValue(setting.value, setting.min) + DELTAS[i];
                send(AdminAction.CONFIG, setting.key, String.valueOf(clamp(next, setting.min, setting.max)));
                return;
            }
            return;
        }
        if (Dynasty.hit(rect.x + rect.w - 56, rect.y, 56, BTN, mouseX, mouseY)) {
            click();
            GuiTextField field = fields.get(setting.key);
            saveText(setting, field == null ? setting.value : field.getText());
        }
    }

    private void clickState(int mouseX, int mouseY) {
        Rect plate = lookupField();
        if (Dynasty.hit(plate.x + plate.w + 4, plate.y, 72, BTN, mouseX, mouseY)) {
            click();
            GuiTextField field = fields.get(LOOKUP);
            lookup(field == null ? ClientState.lookupName : field.getText());
        }
    }

    /** Nombre, aldea, rango y minimas: everything the Paneles form staged goes in one click. */
    private void applyPanel() {
        BoardData.Panel selected = panel();
        if (selected == null) return;
        click();
        RebornQuests.NET.sendToServer(new AdminAction(AdminAction.RULE, cut(selected.name, 70), "",
                stagedRank(selected), stagedMin(selected)));
        GuiTextField nameField = fields.get(nameKey(selected));
        String name = nameField == null ? "" : nameField.getText().trim();
        if (!name.isEmpty() && !name.equals(selected.name)) {
            sendPanel(AdminAction.NAME, name, -1, 0);
        }
        GuiTextField villageField = fields.get(villageKey(selected));
        String village = villageField == null ? selected.category : villageField.getText().trim();
        if (!village.equals(selected.category)) {
            sendPanel(AdminAction.CATEGORY, village, -1, 0);
        }
        ClientState.pendingRank = ClientState.NOT_STAGED;
        ClientState.pendingMin = ClientState.NOT_STAGED;
    }

    private void createPanel() {
        GuiTextField field = fields.get(NEW_PANEL);
        String name = field == null ? "" : field.getText().trim();
        if (name.isEmpty()) {
            ClientState.data.notice = "Escribe primero el nombre del panel nuevo.";
            return;
        }
        click();
        RebornQuests.NET.sendToServer(new AdminAction(AdminAction.PANEL_NEW, "", cut(name, 40), -1, 0));
    }

    private void saveText(BoardData.Setting setting, String value) {
        send(AdminAction.CONFIG, setting.key, value);
    }

    private void lookup(String name) {
        ClientState.lookupName = name == null ? "" : name.trim();
        send(AdminAction.LOOKUP, ClientState.lookupName, "");
    }

    private void send(int kind, String key, String value) {
        RebornQuests.NET.sendToServer(new AdminAction(kind, cut(key, 70), cut(value, 110), -1, 0));
    }

    /** An action about one panel: the server answers with the refreshed screen. */
    private void sendPanel(int kind, String value, int questId, int minFinished) {
        BoardData.Panel selected = panel();
        String name = selected == null ? "" : cut(selected.name, 70);
        RebornQuests.NET.sendToServer(new AdminAction(kind, name, cut(value, 110), questId, minFinished));
    }

    /** The same, aimed at the list the Misiones tab is pointing at: the panel's, or one rank's. */
    private void sendPanelList(int kind, int questId) {
        BoardData.Panel selected = panel();
        String name = selected == null ? "" : cut(selected.name, 70);
        RebornQuests.NET.sendToServer(new AdminAction(kind, name, "", questId, 0)
                .toList(editingRank(), ClientState.listVillage));
    }

    /** A mission carries its own id in {@link AdminAction#rankIndex}; the tier and the times are global. */
    private void sendMission(int kind, int questId, String value) {
        RebornQuests.NET.sendToServer(new AdminAction(kind, "", cut(value, 110), questId, 0));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (Map.Entry<String, GuiTextField> entry : fields.entrySet()) {
            GuiTextField field = entry.getValue();
            if (!field.getVisible() || !field.isFocused()) continue;
            if (keyCode == 28 || keyCode == 156) {
                commit(entry.getKey(), field);
                return;
            }
            if (field.textboxKeyTyped(typedChar, keyCode)) return;
            break;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** Enter means the same thing as the button next to the field. */
    private void commit(String key, GuiTextField field) {
        if (key.equals(LOOKUP)) {
            click();
            lookup(field.getText());
            return;
        }
        if (key.equals(NEW_PANEL)) {
            createPanel();
            return;
        }
        if (key.startsWith("panel:") || key.startsWith("village:")) {
            click();
            applyPanel();
            return;
        }
        if (key.startsWith("rango:g") || key.startsWith("rango:l")) {
            click();
            sendRank(key.charAt(6) == 'g' ? AdminAction.RANK_GROUP : AdminAction.RANK_LABEL,
                    intValue(key.substring(7), ClientState.rankView), field.getText().trim());
            return;
        }
        if (key.startsWith("node:")) {
            click();
            sendPanel(AdminAction.NODE_ADD, field.getText().trim(), -1, 0);
            field.setText("");
            return;
        }
        if (key.startsWith("grant:")) {
            click();
            sendPanel(AdminAction.GRANT_ADD, field.getText().trim(), -1, 0);
            field.setText("");
            return;
        }
        if (key.startsWith("npc:")) {
            click();
            sendPanel(AdminAction.NPC_ADD, field.getText().trim(), -1, 0);
            field.setText("");
            return;
        }
        if (key.startsWith("mvalue:")) {
            click();
            BoardData.Row row = mission();
            if (row != null) sendMission(AdminAction.MISSION_COOLDOWN, row.id, field.getText().trim());
            return;
        }
        BoardData.Setting setting = setting(key);
        if (setting != null && setting.type == BoardData.TEXT) {
            click();
            saveText(setting, field.getText());
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields.values()) {
            if (field.getVisible()) field.updateCursorCounter();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getDWheel();
        if (delta == 0) return;
        int step = delta > 0 ? -1 : 1;
        if (ClientState.adminTab == RQConfig.TAB_PANELS) {
            if (Dynasty.hit(formX, top, formW, bottom - top, lastX, lastY)) {
                if (lastX >= grantsColX()) {
                    ClientState.grantScroll = clamp(ClientState.grantScroll + step, 0, maxGrantScroll());
                } else {
                    ClientState.nodeScroll = clamp(ClientState.nodeScroll + step, 0, maxNodeScroll());
                }
                return;
            }
            ClientState.adminScroll = clamp(ClientState.adminScroll + step, 0, maxPanelScroll());
            return;
        }
        if (ClientState.adminTab == RQConfig.TAB_MISSIONS) {
            if (Dynasty.hit(panelX, top, panelW, bottom - top, lastX, lastY)) {
                ClientState.missionScroll = clamp(ClientState.missionScroll + step, 0, maxMissionScroll());
                return;
            }
            BoardData.Panel selected = panel();
            if (selected != null) {
                ClientState.selectedAdmin = clamp(ClientState.selectedAdmin + step, 0, data().panels.size() - 1);
            }
            return;
        }
        if (ClientState.adminTab == RQConfig.TAB_PERMS) {
            if (Dynasty.hit(panelX, top, panelW, bottom - top, lastX, lastY)) {
                ClientState.rankScroll = clamp(ClientState.rankScroll + step, 0, maxRankScroll());
                return;
            }
            if (Dynasty.hit(formX, top, formW, bottom - top, lastX, lastY)) {
                ClientState.settingsScroll = clamp(ClientState.settingsScroll + step, 0, maxSettingScroll());
            }
            return;
        }
        if (Dynasty.hit(MARGIN, top, inner(), bottom - top, lastX, lastY)) {
            ClientState.settingsScroll = clamp(ClientState.settingsScroll + step, 0, maxSettingScroll());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // geometry, values
    // ---------------------------------------------------------------------------------------------

    /** Where the name row, the rank row, the stepper row and the order row sit; render and click share these. */
    private int nameRowY() {
        return contentTop() + 20;
    }

    private int rankRowY() {
        return nameRowY() + BTN + rowGap();
    }

    private int stepRowY() {
        return rankRowY() + BTN + rowGap();
    }

    private int orderRowY() {
        return stepRowY() + BTN + rowGap();
    }

    /** The space between two rows of the panel form: a short window squeezes it so nothing is cut off. */
    private int rowGap() {
        return bottom - contentTop() >= 240 ? 12 : 5;
    }

    /**
     * Where the two lists start. Bounded from the bottom so a short window still shows them: the
     * permission and access rows are what the staff edits most, so they keep their space first.
     */
    private int nodesTop() {
        return Math.min(orderRowY() + BTN + rowGap() + 8, addRowY() - 2 * NODE_ROW - 6);
    }

    /** The row of fields that add a permission or an access, one clear above the create/delete row. */
    private int addRowY() {
        return bottom - 40;
    }

    /** The two lists share the form as two columns: permissions left, named access right. */
    private int columnW() {
        return (formW - 26) / 2;
    }

    private int nodesColX() {
        return formX + 10;
    }

    private int grantsColX() {
        return formX + 16 + columnW();
    }

    /** The field of one column; the button beside it is {@link #addButtonRect}. */
    private Rect addPlate(int colX) {
        return new Rect(colX, addRowY(), Math.max(40, columnW() - 64), BTN);
    }

    private Rect addButtonRect(int colX) {
        Rect plate = addPlate(colX);
        return new Rect(plate.x + plate.w + 4, addRowY(), 60, BTN);
    }

    /** The name field of the create/delete row; the two buttons and the drawing all start from here. */
    private Rect newPanelPlate() {
        return new Rect(formX + 10, bottom - 22, Math.max(50, formW - 276), BTN);
    }

    private Rect createRect() {
        Rect plate = newPanelPlate();
        return new Rect(plate.x + plate.w + 4, bottom - 22, 76, BTN);
    }

    private Rect deleteRect() {
        Rect create = createRect();
        return new Rect(create.x + create.w + 4, bottom - 22, 62, BTN);
    }

    /** Fills in the panel every rank of every village is missing, which is how the pack ships. */
    private Rect seedRect() {
        Rect delete = deleteRect();
        return new Rect(delete.x + delete.w + 4, bottom - 22, 96, BTN);
    }

    private static Rect stepRect(int x, int y, int index) {
        return new Rect(x + index * (STEP_W + 2), y, STEP_W, BTN);
    }

    /** The label column of a settings row: the name and its help, and the control takes what is left. */
    private static int labelW(int width) {
        return clamp(width * 36 / 100, 92, 210);
    }

    private Rect control(int x, int y, int width, BoardData.Setting setting) {
        int left = x + labelW(width) + 6;
        return new Rect(left, y + 5, Math.max(80, x + width - left - 14), BTN);
    }

    private static Rect textRect(Rect rect) {
        return new Rect(rect.x, rect.y, Math.max(40, rect.w - 60), rect.h);
    }

    private Rect lookupField() {
        int w = Math.max(80, inner() - 100 - 24);
        return new Rect(MARGIN + 11, bottom - 26, w, BTN);
    }

    private static final class Rect {
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean hit(int mouseX, int mouseY) {
            return Dynasty.hit(x, y, w, h, mouseX, mouseY);
        }
    }

    /** Staged edits: what the panel says until Aplicar panel commits the change. */
    private int stagedRank(BoardData.Panel panel) {
        return ClientState.pendingRank == ClientState.NOT_STAGED ? panel.requiredRank : ClientState.pendingRank;
    }

    private int stagedMin(BoardData.Panel panel) {
        return ClientState.pendingMin == ClientState.NOT_STAGED ? panel.minFinished : ClientState.pendingMin;
    }

    /** The code column: the name of the required ladder step, or a dash when the panel asks nothing. */
    private String code(int index) {
        if (index < 0) return "-";
        List<String> labels = split(value("rango.labels"), ",");
        if (index < labels.size() && !labels.get(index).trim().isEmpty()) return labels.get(index).trim();
        List<String> groups = split(value("rango.groups"), ",");
        return index < groups.size() ? groups.get(index).toUpperCase(Locale.ROOT) : "-";
    }

    private BoardData.Panel panel() {
        BoardData data = ClientState.data;
        if (ClientState.selectedAdmin < 0 || ClientState.selectedAdmin >= data.panels.size()) return null;
        return data.panels.get(ClientState.selectedAdmin);
    }

    /** The mission the form edits: the row of the selected panel that carries the picked quest id. */
    private BoardData.Row mission() {
        BoardData.Panel panel = panel();
        if (panel == null || ClientState.selectedMission <= 0) return null;
        for (BoardData.Row row : panel.rows) {
            if (row.id == ClientState.selectedMission) return row;
        }
        return null;
    }

    private BoardData data() {
        return ClientState.data;
    }

    /** The level of one mission in the list, read from the row that carries it. */
    private String level(int questId) {
        BoardData.Panel panel = panel();
        if (panel == null) return "D";
        for (BoardData.Row row : panel.rows) {
            if (row.id == questId) return row.difficulty;
        }
        return "D";
    }

    private List<String> levels() {
        return split(ClientState.data.levels, ",");
    }

    /** The next level in the list, wrapping around, which is what the cycle button shows. */
    private String nextLevel(String current) {
        List<String> levels = levels();
        if (levels.isEmpty()) return current;
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).equalsIgnoreCase(current)) return levels.get((i + 1) % levels.size());
        }
        return levels.get(0);
    }

    /** Minutes as the screen writes them, and "no" once they are gone, which the server reads as off. */
    private static String valueOf(int minutes) {
        return minutes <= 0 ? "no" : String.valueOf(minutes);
    }

    private static String time(int minutes) {
        if (minutes <= 0) return "sin efecto";
        if (minutes < 60) return minutes + " min";
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    /** Village of one panel, or the plain label when the staff left it unfiled. */
    private static String village(BoardData.Panel panel) {
        return panel.category.isEmpty() ? "-" : panel.category;
    }

    private String rankName(int index) {
        List<String> groups = ladderGroups();
        if (index < 0 || index >= groups.size()) return code(index);
        return code(index) + " (" + groups.get(index) + ")";
    }

    private String rankButton(int index) {
        return index < 0 ? "sin exigir  \u25b8" : "rango " + rankName(index) + "  \u25b8";
    }

    private List<String> ladderGroups() {
        return split(value("rango.groups"), ",");
    }

    private static List<String> split(String value, String separator) {
        List<String> out = new ArrayList<String>();
        for (String part : value.split(separator)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private String value(String key) {
        BoardData.Setting setting = setting(key);
        return setting == null ? "" : setting.value;
    }

    private boolean truth(String key) {
        return Boolean.parseBoolean(value(key));
    }

    /**
     * The mod's own role as the Estado screen reads it: which NPCs open which panel. Custom NPCs has no slot
     * for a role invented by another mod, so the list lives here and any NPC of the pack can be given it.
     */
    private String boardNpcs() {
        List<String> listed = new ArrayList<String>();
        int npcs = 0;
        int panels = 0;
        for (BoardData.Panel panel : data().panels) {
            if (panel.npcs.isEmpty()) continue;
            panels++;
            npcs += panel.npcs.size();
            if (listed.size() < ROLE_ECHO) {
                listed.add("«" + panel.name + "» " + String.join(" ", panel.npcs));
            }
        }
        if (listed.isEmpty()) {
            return "ningun NPC lo lleva: el tablón se abre con el comando o con la tecla del libro";
        }
        String out = npcs + (npcs == 1 ? " NPC" : " NPCs") + " en " + panels
                + (panels == 1 ? " panel" : " paneles");
        return panels == listed.size() ? out + "  ·  " + String.join("  ·  ", listed) : out;
    }

    private BoardData.Setting setting(String key) {
        for (BoardData.Setting setting : ClientState.data.settings) {
            if (setting.key.equals(key)) return setting;
        }
        return null;
    }

    private static String nextOption(BoardData.Setting setting, List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(setting.value.trim())) {
                return options.get((i + 1) % options.size());
            }
        }
        return options.get(0);
    }

    private static String heading(int tab) {
        if (tab == RQConfig.TAB_PERMS) return "PERMISOS Y RANGOS";
        if (tab == RQConfig.TAB_LEVELS) return "NIVELES DE MISION";
        return "AJUSTES DEL TABLON";
    }

    private static int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String nameKey(BoardData.Panel panel) {
        return "panel:" + panel.name;
    }

    private static String villageKey(BoardData.Panel panel) {
        return "village:" + panel.name;
    }

    private static String nodeKey(BoardData.Panel panel) {
        return "node:" + panel.name;
    }

    private static String grantKey(BoardData.Panel panel) {
        return "grant:" + panel.name;
    }

    /** The field of the NPC list: what Custom NPCs calls the NPC, or {@code #<uuid>} to pin one of them. */
    private static String npcKey(BoardData.Panel panel) {
        return "npc:" + panel.name;
    }

    /** The field the access column is holding: the NPCs need their own, the rank list needs none. */
    private static String accessKey(BoardData.Panel panel) {
        return ClientState.accessView == ACCESS_NPCS ? npcKey(panel) : grantKey(panel);
    }

    private static String valueKey(int questId) {
        return "mvalue:" + questId;
    }

    /** The two fields of a rank tab, keyed by ladder position so a move keeps what was typed. */
    private static String rankGroupKey(int index) {
        return "rango:g" + index;
    }

    private static String rankLabelKey(int index) {
        return "rango:l" + index;
    }

    /**
     * Fields are kept per key for the whole session and 1.12.2 lets the frame move them, so typed text
     * survives a resize. Text a field has not been touched with is re-read from the server instead.
     *
     * <p>A field is born as wide as it will ever be drawn and its scroll is parked at the start: 1.12.2
     * derives the horizontal offset from the cursor position, so a narrow field that is widened later
     * would keep the offset of the last character and show only the tail of the text.
     */
    private GuiTextField field(String key, String serverValue, int maxLength) {
        GuiTextField field = fields.get(key);
        if (field == null) {
            field = new GuiTextField(fields.size() + 1, this.fontRenderer, 0, 0, FIELD_W, 12);
            field.setMaxStringLength(maxLength);
            field.setCanLoseFocus(true);
            field.setEnableBackgroundDrawing(false);
            field.setTextColor(Dynasty.BODY);
            field.setText(serverValue);
            field.setCursorPositionZero();
            fields.put(key, field);
        } else if (!field.isFocused() && !field.getText().equals(serverValue)) {
            field.setText(serverValue);
            field.setCursorPositionZero();
        }
        return field;
    }

    private static void place(GuiTextField field, Rect rect) {
        field.x = rect.x + 4;
        field.y = rect.y + 5;
        field.width = rect.w - 8;
        field.height = 8;
        field.setVisible(true);
        if (!field.isFocused()) field.setCursorPositionZero();
    }

    private void click() {
        if (this.mc.player != null) {
            this.mc.player.playSound(SoundEvents.UI_BUTTON_CLICK, 0.25F, 1.0F);
        }
    }

    /** What each mission difficulty asks for, in one line, read from the settings the server sent. */
    private String levelGates() {
        StringBuilder out = new StringBuilder();
        for (String level : split(ClientState.data.levels, ",")) {
            if (out.length() > 0) out.append("  ·  ");
            int min = intValue(value("gate." + level + ".min"), 0);
            out.append("Mision ").append(level).append(min <= 0 ? ": libre"
                    : ": " + min + " de Mision " + value("gate." + level + ".counted") + "+");
        }
        return out.length() == 0 ? "sin niveles" : out.toString();
    }

    /** How far down the ladder the player's rank reaches for missions, named by the rank it stops at. */
    private String listDepth(BoardData data) {
        if (data.rankIndex < 0) return "la lista normal del panel";
        if (data.rankDepth <= 0) return "solo tu rango";
        int floor = Math.max(0, data.rankIndex - data.rankDepth);
        if (floor == 0) return "todos los rangos inferiores";
        return data.rankDepth + (data.rankDepth == 1 ? " rango inferior" : " rangos inferiores")
                + ", hasta " + code(floor);
    }

    private int line(int y, String value, int color) {
        List<String> wrapped = this.fontRenderer.listFormattedStringToWidth(value, Math.max(60, inner() - 24));
        for (String part : wrapped) {
            if (y > bottom - 52) break;
            draw(part, MARGIN + 12, y, color);
            y += LINE;
        }
        return y + 2;
    }

    private void centered(String value, int y, int color) {
        String cut = Dynasty.fit(this.fontRenderer, value, inner());
        draw(cut, (this.width - this.fontRenderer.getStringWidth(cut)) / 2F, y, color);
    }

    private void section(String value, int x, int y, int width, String right) {
        draw("§l" + value, x, y, Dynasty.TITLE);
        Dynasty.rule(x, y + 10, width);
        if (right != null) {
            draw(right, x + width - this.fontRenderer.getStringWidth(right), y, Dynasty.HINT);
        }
    }

    private void hint(String value, int x, int y, int width) {
        hint(value, x, y, width, 5);
    }

    /** Wrapped helper text, cut at {@code maxLines} so a long help string never walks into the next row. */
    private void hint(String value, int x, int y, int width, int maxLines) {
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(value, Math.max(40, width));
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            draw(lines.get(i), x, y + i * LINE, Dynasty.HINT);
        }
    }

    private void draw(String value, float x, int y, int color) {
        this.fontRenderer.drawStringWithShadow(value, x, y, color);
    }

    private void scrollbar(int x, int size, int scroll, int visible) {
        scrollbar(x, contentTop(), bottom, size, scroll, visible);
    }

    /** The same, for a list that starts under the selectors drawn above it. */
    private void scrollbar(int x, int trackTop, int size, int scroll, int visible) {
        scrollbar(x, trackTop, bottom, size, scroll, visible);
    }

    /** The track of one list: it runs between its first row and the last pixel that is still its own. */
    private void scrollbar(int x, int trackTop, int trackBottom, int size, int scroll, int visible) {
        int max = Math.max(0, size - visible);
        int track = trackBottom - trackTop;
        if (max <= 0 || track <= 6) return;
        int thumb = Math.max(10, track * visible / Math.max(1, size));
        int y = trackTop + (track - thumb) * clamp(scroll, 0, max) / max;
        Dynasty.scrollbar(x, trackTop, track, y, thumb);
    }

    private static String cut(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** The server answers every edit with a refreshed screen, so nothing here needs refetching. */
    public void reload() {
        layout();
        clampScrolls();
    }
}
