package dev.sobrio.rebornquests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/** The whole board as it travels to the client. Built on the server, only read on the client. */
public class BoardData {
    public static final int OPEN = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;

    /** Control the admin screen draws for each editable key. */
    public static final int TEXT = 0;
    public static final int BOOL = 1;
    public static final int INT = 2;
    public static final int CHOICE = 3;

    /** Which of the two screens the board packet should open. */
    public static final int SCREEN_BOARD = 0;
    public static final int SCREEN_ADMIN = 1;

    private static final int MAX_TEXT = 6000;

    public int screen = SCREEN_BOARD;
    /**
     * True when the packet only fills the client's copy of the config and must not open a screen. The NPC
     * editor of Custom NPCs asks for this: the box that lists the panels one NPC opens lives inside the
     * editor's own tab, so the answer has to arrive without pulling the staff out of it.
     */
    public boolean silent;
    public String title = "Misiones";
    public String rankLabel = "sin rango";
    public String rankGroup = "";
    public String bridge = "ninguno";
    public String notice = "";
    public int rankIndex = -1;
    public int finished;
    public int active;
    public boolean admin;
    /** Difficulty levels a mission can carry, lowest first. The admin screen cycles over this list. */
    public String levels = "D,C,B,A,S";
    /** Villages panels are filed under, in the order the admin screen cycles them. */
    public String villages = "";
    /** The village LuckPerms puts this player in, which is what picks the per-rank lists he is shown. */
    public String village = "";
    /** How far down the ladder this player's rank reaches for missions, as the config says right now. */
    public int rankDepth;
    /**
     * The node the staff asks for on top of a rank, when the server was started with one: what the rank card
     * says when he can see the board but is not really allowed at it. Empty means nothing extra is asked.
     */
    public String boardNode = "";

    /** The config keys the staff may edit, sent only when he really holds the node. */
    public final List<Setting> settings = new ArrayList<Setting>();
    /** Read-only diagnostics for the Estado tab. */
    public String mode = "";
    public String cnpc = "";
    public String error = "";
    public String ladder = "";
    public String adminNode = "staff";

    public final List<Panel> panels = new ArrayList<Panel>();
    /** Panel the board should open on, decided by the NPC whose role was clicked. */
    public String focus = "";
    /**
     * The panels the clicked NPC carries: more than one when the staff put it on more than one list. The
     * board then shows only these panels and offers their missions to accept together. Empty means the whole
     * board, which is how the quest-book key and the command open it.
     */
    public final List<String> group = new ArrayList<String>();
    /** Every Custom NPCs quest, so the admin screen can offer them to a panel. Sent to staff only. */
    public final List<Quest> catalog = new ArrayList<Quest>();
    /**
     * The per-rank mission lists exactly as the config holds them, "<panel>;<grupo>;<aldea>;<ids>". Staff
     * only: the Misiones tab edits one of these lines at a time, so it has to see them all.
     */
    public final List<String> rankLists = new ArrayList<String>();
    /**
     * The newest lines of the audit record, one per entry: when, who, what kind of change, which file or
     * panel, and the lines that moved. Staff only, and only ever read — nothing in the mod can write a
     * change into these lines or take one out.
     */
    public final List<String> audit = new ArrayList<String>();

    public BoardData copy() {
        BoardData out = new BoardData();
        out.screen = screen;
        out.silent = silent;
        out.title = title;
        out.rankLabel = rankLabel;
        out.rankGroup = rankGroup;
        out.bridge = bridge;
        out.notice = notice;
        out.rankIndex = rankIndex;
        out.finished = finished;
        out.active = active;
        out.admin = admin;
        out.mode = mode;
        out.cnpc = cnpc;
        out.error = error;
        out.ladder = ladder;
        out.adminNode = adminNode;
        out.levels = levels;
        out.villages = villages;
        out.village = village;
        out.rankDepth = rankDepth;
        out.boardNode = boardNode;
        out.focus = focus;
        for (String name : group) out.group.add(name);
        for (Panel panel : panels) out.panels.add(panel.copy());
        for (Setting setting : settings) out.settings.add(setting.copy());
        for (Quest quest : catalog) out.catalog.add(quest.copy());
        for (String list : rankLists) out.rankLists.add(list);
        for (String entry : audit) out.audit.add(entry);
        return out;
    }

    public Panel panel(String name) {
        for (Panel panel : panels) {
            if (panel.name.equalsIgnoreCase(name)) return panel;
        }
        return null;
    }

    public int indexOfPanel(String name) {
        for (int i = 0; i < panels.size(); i++) {
            if (panels.get(i).name.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public Panel panelOfQuest(int questId) {
        for (Panel panel : panels) {
            for (Row row : panel.rows) {
                if (row.id == questId) return panel;
            }
        }
        return null;
    }

    public void write(PacketBuffer buf) {
        buf.writeString(cut(title));
        buf.writeString(cut(rankLabel));
        buf.writeString(cut(rankGroup));
        buf.writeString(cut(bridge));
        buf.writeString(cut(notice));
        buf.writeInt(rankIndex);
        buf.writeInt(finished);
        buf.writeInt(active);
        buf.writeBoolean(admin);
        buf.writeInt(panels.size());
        for (Panel panel : panels) panel.write(buf);
        buf.writeInt(screen);
        buf.writeString(cut(mode));
        buf.writeString(cut(cnpc));
        buf.writeString(cut(error));
        buf.writeString(cut(ladder));
        buf.writeString(cut(adminNode));
        buf.writeInt(settings.size());
        for (Setting setting : settings) setting.write(buf);
        buf.writeString(cut(levels));
        buf.writeString(cut(villages));
        buf.writeString(cut(village));
        buf.writeInt(rankDepth);
        buf.writeString(cut(focus));
        buf.writeInt(group.size());
        for (String name : group) buf.writeString(cut(name));
        buf.writeInt(catalog.size());
        for (Quest quest : catalog) quest.write(buf);
        buf.writeInt(rankLists.size());
        for (String list : rankLists) buf.writeString(cut(list));
        buf.writeInt(audit.size());
        for (String entry : audit) buf.writeString(cut(entry));
        buf.writeBoolean(silent);
        buf.writeString(cut(boardNode));
    }

    public static BoardData read(PacketBuffer buf) {
        BoardData data = new BoardData();
        data.title = buf.readString(MAX_TEXT);
        data.rankLabel = buf.readString(MAX_TEXT);
        data.rankGroup = buf.readString(MAX_TEXT);
        data.bridge = buf.readString(MAX_TEXT);
        data.notice = buf.readString(MAX_TEXT);
        data.rankIndex = buf.readInt();
        data.finished = buf.readInt();
        data.active = buf.readInt();
        data.admin = buf.readBoolean();
        int panels = buf.readInt();
        for (int i = 0; i < panels; i++) data.panels.add(Panel.read(buf));
        data.screen = buf.readInt();
        data.mode = buf.readString(MAX_TEXT);
        data.cnpc = buf.readString(MAX_TEXT);
        data.error = buf.readString(MAX_TEXT);
        data.ladder = buf.readString(MAX_TEXT);
        data.adminNode = buf.readString(MAX_TEXT);
        int settings = buf.readInt();
        for (int i = 0; i < settings; i++) data.settings.add(Setting.read(buf));
        data.levels = buf.readString(MAX_TEXT);
        data.villages = buf.readString(MAX_TEXT);
        data.village = buf.readString(MAX_TEXT);
        data.rankDepth = buf.readInt();
        data.focus = buf.readString(MAX_TEXT);
        int group = buf.readInt();
        for (int i = 0; i < group; i++) data.group.add(buf.readString(MAX_TEXT));
        int catalog = buf.readInt();
        for (int i = 0; i < catalog; i++) data.catalog.add(Quest.read(buf));
        int lists = buf.readInt();
        for (int i = 0; i < lists; i++) data.rankLists.add(buf.readString(MAX_TEXT));
        int entries = buf.readInt();
        for (int i = 0; i < entries; i++) data.audit.add(buf.readString(MAX_TEXT));
        data.silent = buf.readBoolean();
        data.boardNode = buf.readString(MAX_TEXT);
        return data;
    }

    static String cut(String value) {
        if (value == null) return "";
        return value.length() <= MAX_TEXT - 8 ? value : value.substring(0, MAX_TEXT - 8);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    /** The other half of {@link #join}: a comma list back into trimmed parts, empties dropped. */
    private static List<String> split(String value) {
        List<String> out = new ArrayList<String>();
        for (String part : value.split(",")) {
            if (!part.trim().isEmpty()) out.add(part.trim());
        }
        return out;
    }

    /** One panel of the mod: its own name, its village, its gate and the Custom NPCs quests it holds. */
    public static class Panel {
        public String name = "";
        /** The village the missions of this panel belong to; empty means unfiled. */
        public String category = "";
        public String reason = "";
        public int requiredRank = -1;
        public int minFinished;
        public boolean locked;
        /** LuckPerms nodes the panel demands on top of the rank. All of them have to be held. */
        public final List<String> nodes = new ArrayList<String>();
        /** Players the staff let into this panel by name, with no permission behind them. Staff only. */
        public final List<String> grants = new ArrayList<String>();
        /**
         * The NPCs the mod's own role is given to for this panel, as Custom NPCs names them or as
         * {@code #<uuid>}. Staff only: it is what decides which panel an NPC opens.
         */
        public final List<String> npcs = new ArrayList<String>();
        /**
         * The online players whose own rank, mission count and permission nodes open this panel, each one
         * followed by the reason. Staff only, and never editable: it is what LuckPerms says right now.
         */
        public final List<String> byRank = new ArrayList<String>();
        public final List<Row> rows = new ArrayList<Row>();

        Panel copy() {
            Panel out = new Panel();
            out.name = name;
            out.category = category;
            out.reason = reason;
            out.requiredRank = requiredRank;
            out.minFinished = minFinished;
            out.locked = locked;
            out.nodes.addAll(nodes);
            out.grants.addAll(grants);
            out.npcs.addAll(npcs);
            out.byRank.addAll(byRank);
            for (Row row : rows) out.rows.add(row.copy());
            return out;
        }

        void write(PacketBuffer buf) {
            buf.writeString(cut(name));
            buf.writeString(cut(category));
            buf.writeString(cut(join(nodes)));
            buf.writeString(cut(reason));
            buf.writeInt(requiredRank);
            buf.writeInt(minFinished);
            buf.writeBoolean(locked);
            buf.writeString(cut(join(grants)));
            buf.writeString(cut(join(byRank)));
            buf.writeString(cut(join(npcs)));
            buf.writeInt(rows.size());
            for (Row row : rows) row.write(buf);
        }

        static Panel read(PacketBuffer buf) {
            Panel panel = new Panel();
            panel.name = buf.readString(MAX_TEXT);
            panel.category = buf.readString(MAX_TEXT);
            for (String node : split(buf.readString(MAX_TEXT))) panel.nodes.add(node);
            panel.reason = buf.readString(MAX_TEXT);
            panel.requiredRank = buf.readInt();
            panel.minFinished = buf.readInt();
            panel.locked = buf.readBoolean();
            for (String name : split(buf.readString(MAX_TEXT))) panel.grants.add(name);
            for (String name : split(buf.readString(MAX_TEXT))) panel.byRank.add(name);
            for (String name : split(buf.readString(MAX_TEXT))) panel.npcs.add(name);
            int rows = buf.readInt();
            for (int i = 0; i < rows; i++) panel.rows.add(Row.read(buf));
            return panel;
        }

        /** "permiso" reads right for one node and "permisos" for the rest. */
        public String nodeCount() {
            int size = nodes.size();
            if (size == 0) return "sin permisos extra";
            return size + (size == 1 ? " permiso" : " permisos");
        }
    }

    /** One Custom NPCs quest as the admin mission picker sees it. */
    public static class Quest {
        public int id;
        public String name = "";
        public String category = "";
        public String panel = "";

        Quest copy() {
            Quest out = new Quest();
            out.id = id;
            out.name = name;
            out.category = category;
            out.panel = panel;
            return out;
        }

        void write(PacketBuffer buf) {
            buf.writeInt(id);
            buf.writeString(cut(name));
            buf.writeString(cut(category));
            buf.writeString(cut(panel));
        }

        static Quest read(PacketBuffer buf) {
            Quest out = new Quest();
            out.id = buf.readInt();
            out.name = buf.readString(MAX_TEXT);
            out.category = buf.readString(MAX_TEXT);
            out.panel = buf.readString(MAX_TEXT);
            return out;
        }
    }

    /** One quest line with the progress of the player who asked for the board. */
    public static class Row {
        public int id;
        public int type;
        public int state;
        public int done;
        public int total;
        public boolean acceptable;
        public boolean repeatable;
        public String name = "";
        public String text = "";
        public String typeLabel = "Otro";
        /** Mission tier the staff set, shown as "Mision D" a "Mision S". Not the rank of the player. */
        public String difficulty = "D";
        /** Minutes the player has to wait before accepting it again, and what is left for him. */
        public int cooldownMin;
        public int cooldownLeft;
        /** Minutes to finish it once accepted, and what is left for him; 0 means no time limit. */
        public int limitMin;
        public int limitLeft;
        /** The same clock in seconds, so the screen can count it down between two server updates. */
        public int limitSec;
        /** The NPC Custom NPCs names for handing the quest in; empty when the quest has none. */
        public String completer = "";
        /** The text Custom NPCs shows when the quest is handed in. */
        public String completeText = "";
        /** What the quest pays out, as one line read from Custom NPCs. */
        public String rewards = "";
        /** Gate of the mission's difficulty: {@code gateMin} finished of {@code gateLevel} or harder. */
        public String gateLevel = "";
        public int gateMin;
        /** How many the player already has, so both screens can say what is still missing. */
        public int gateHave;

        Row copy() {
            Row out = new Row();
            out.id = id;
            out.type = type;
            out.state = state;
            out.done = done;
            out.total = total;
            out.acceptable = acceptable;
            out.repeatable = repeatable;
            out.name = name;
            out.text = text;
            out.typeLabel = typeLabel;
            out.difficulty = difficulty;
            out.cooldownMin = cooldownMin;
            out.cooldownLeft = cooldownLeft;
            out.limitMin = limitMin;
            out.limitLeft = limitLeft;
            out.limitSec = limitSec;
            out.completer = completer;
            out.completeText = completeText;
            out.rewards = rewards;
            out.gateLevel = gateLevel;
            out.gateMin = gateMin;
            out.gateHave = gateHave;
            return out;
        }

        void write(PacketBuffer buf) {
            buf.writeInt(id);
            buf.writeInt(type);
            buf.writeInt(state);
            buf.writeInt(done);
            buf.writeInt(total);
            buf.writeBoolean(acceptable);
            buf.writeBoolean(repeatable);
            buf.writeString(cut(name));
            buf.writeString(cut(text));
            buf.writeString(cut(typeLabel));
            buf.writeString(cut(difficulty));
            buf.writeInt(cooldownMin);
            buf.writeInt(cooldownLeft);
            buf.writeInt(limitMin);
            buf.writeInt(limitLeft);
            buf.writeInt(limitSec);
            buf.writeString(cut(completer));
            buf.writeString(cut(completeText));
            buf.writeString(cut(rewards));
            buf.writeString(cut(gateLevel));
            buf.writeInt(gateMin);
            buf.writeInt(gateHave);
        }

        static Row read(PacketBuffer buf) {
            Row row = new Row();
            row.id = buf.readInt();
            row.type = buf.readInt();
            row.state = buf.readInt();
            row.done = buf.readInt();
            row.total = buf.readInt();
            row.acceptable = buf.readBoolean();
            row.repeatable = buf.readBoolean();
            row.name = buf.readString(MAX_TEXT);
            row.text = buf.readString(MAX_TEXT);
            row.typeLabel = buf.readString(MAX_TEXT);
            row.difficulty = buf.readString(MAX_TEXT);
            row.cooldownMin = buf.readInt();
            row.cooldownLeft = buf.readInt();
            row.limitMin = buf.readInt();
            row.limitLeft = buf.readInt();
            row.limitSec = buf.readInt();
            row.completer = buf.readString(MAX_TEXT);
            row.completeText = buf.readString(MAX_TEXT);
            row.rewards = buf.readString(MAX_TEXT);
            row.gateLevel = buf.readString(MAX_TEXT);
            row.gateMin = buf.readInt();
            row.gateHave = buf.readInt();
            return row;
        }

        public String status() {
            if (state == DONE) {
                if (cooldownLeft > 0) return "Repetible en " + minutes(cooldownLeft);
                return repeatable ? "Completada (repetible)" : "Completada";
            }
            if (state == ACTIVE) {
                String head = total > 1 ? "En curso " + done + "/" + total : "En curso";
                return limitLeft > 0 ? head + " · quedan " + minutes(limitLeft) : head;
            }
            if (!gateOpen()) return "Faltan " + (gateMin - gateHave) + " de Mision " + gateLevel;
            return acceptable ? "Disponible" : "No disponible";
        }

        /** True when the difficulty gate of this mission lets the player take it. */
        public boolean gateOpen() {
            return gateMin <= 0 || gateHave >= gateMin;
        }

        /** What the gate still asks for, in one line; empty when the mission is open. */
        public String gateMissing() {
            if (gateOpen()) return "";
            int left = gateMin - gateHave;
            return "Le faltan " + left + (left == 1 ? " mision" : " misiones") + " terminadas de Mision "
                    + gateLevel + " o mas duras. Llevas " + gateHave + " de " + gateMin + ".";
        }

        /** Conditions of this mission as the staff set them, in one line for either screen. */
        public String rules() {
            StringBuilder out = new StringBuilder("Mision ").append(difficulty);
            out.append(cooldownMin > 0 ? "  ·  enfriamiento " + minutes(cooldownMin) : "  ·  sin enfriamiento");
            out.append(limitMin > 0 ? "  ·  limite " + minutes(limitMin) : "  ·  sin limite de tiempo");
            if (gateMin > 0) {
                out.append("  ·  pide ").append(gateMin).append(" de Mision ").append(gateLevel)
                        .append(" (llevas ").append(gateHave).append(')');
            }
            return out.toString();
        }

        private static String minutes(int value) {
            if (value < 60) return value + " min";
            int hours = value / 60;
            int rest = value % 60;
            return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
        }
    }

    /** Seconds as a clock the player can read while it runs down: 1:04:30, 4:07 or 38 s. */
    public static String clock(int seconds) {
        if (seconds <= 0) return "agotado";
        if (seconds < 60) return seconds + " s";
        int rest = seconds % 60;
        if (seconds < 3600) return (seconds / 60) + ":" + (rest < 10 ? "0" : "") + rest;
        int left = seconds % 3600;
        return (seconds / 3600) + ":" + pad(left / 60) + ":" + pad(left % 60);
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    /**
     * One editable config key as the admin screen sees it. The type picks the control: a text field, a
     * si/no button, a stepper or a cycle button over the option list.
     */
    public static class Setting {
        public int tab;
        public int type;
        public String key = "";
        public String label = "";
        public String value = "";
        public String help = "";
        public String options = "";
        public int min;
        public int max;

        public static Setting text(int tab, String key, String label, String value, String help) {
            return make(tab, TEXT, key, label, value, help, "", 0, 0);
        }

        public static Setting bool(int tab, String key, String label, boolean value, String help) {
            return make(tab, BOOL, key, label, Boolean.toString(value), help, "", 0, 0);
        }

        public static Setting number(int tab, String key, String label, int value, int min, int max, String help) {
            return make(tab, INT, key, label, Integer.toString(value), help, "", min, max);
        }

        public static Setting choice(int tab, String key, String label, String value,
                                     List<String> options, String help) {
            StringBuilder joined = new StringBuilder();
            for (String option : options) {
                if (joined.length() > 0) joined.append('|');
                joined.append(option);
            }
            return make(tab, CHOICE, key, label, value, help, joined.toString(), 0, 0);
        }

        private static Setting make(int tab, int type, String key, String label, String value,
                                    String help, String options, int min, int max) {
            Setting out = new Setting();
            out.tab = tab;
            out.type = type;
            out.key = key;
            out.label = label;
            out.value = value == null ? "" : value;
            out.help = help == null ? "" : help;
            out.options = options;
            out.min = min;
            out.max = max;
            return out;
        }

        public List<String> optionList() {
            List<String> out = new ArrayList<String>();
            for (String option : options.split("\\|")) {
                if (!option.trim().isEmpty()) out.add(option.trim());
            }
            return out;
        }

        /** What the control shows; the option list already comes from the server, so no local lookup. */
        public String display() {
            if (type == BOOL) return Boolean.parseBoolean(value) ? "sí" : "no";
            if (value.trim().isEmpty()) return type == CHOICE ? "sin elegir" : "(vacío)";
            if (key.equals("rango.noBridgeRank") && value.trim().equals("-")) return "sin rango";
            return value;
        }

        Setting copy() {
            Setting out = new Setting();
            out.tab = tab;
            out.type = type;
            out.key = key;
            out.label = label;
            out.value = value;
            out.help = help;
            out.options = options;
            out.min = min;
            out.max = max;
            return out;
        }

        void write(PacketBuffer buf) {
            buf.writeInt(tab);
            buf.writeInt(type);
            buf.writeInt(min);
            buf.writeInt(max);
            buf.writeString(cut(key));
            buf.writeString(cut(label));
            buf.writeString(cut(value));
            buf.writeString(cut(help));
            buf.writeString(cut(options));
        }

        static Setting read(PacketBuffer buf) {
            Setting out = new Setting();
            out.tab = buf.readInt();
            out.type = buf.readInt();
            out.min = buf.readInt();
            out.max = buf.readInt();
            out.key = buf.readString(MAX_TEXT);
            out.label = buf.readString(MAX_TEXT);
            out.value = buf.readString(MAX_TEXT);
            out.help = buf.readString(MAX_TEXT);
            out.options = buf.readString(MAX_TEXT);
            return out;
        }
    }

    /** The settings of one tab, in the order the screen lists them. */
    public List<Setting> tab(int index) {
        List<Setting> out = new ArrayList<Setting>();
        for (Setting setting : settings) {
            if (setting.tab == index) out.add(setting);
        }
        return out;
    }
}
