package dev.sobrio.rebornquests.client;

import java.util.ArrayList;
import java.util.List;

import dev.sobrio.rebornquests.BoardData;

/** The last board the server sent, plus where each list was left, so a refresh keeps your place. */
public final class ClientState {
    /** {@link #pendingRank} value meaning "nothing staged yet". */
    public static final int NOT_STAGED = -2;

    public static BoardData data = new BoardData();
    public static int selectedPanel = -1;
    public static int selectedQuest = -1;
    public static int selectedAdmin = -1;
    /** The quest id the Misiones form edits; 0 means nothing is picked. An id, not a slot, because a
     * mission moves to the top of the list the moment it is added to the panel. */
    public static int selectedMission = 0;
    public static int adminTab;
    public static int pendingRank = NOT_STAGED;
    public static int pendingMin = NOT_STAGED;
    public static String lookupName = "";
    /** Which list the Misiones tab is editing: -1 is the panel's own list, a number is one rank's. */
    public static int listRank = -1;
    /**
     * The staff data the box inside the Custom NPCs editor reads: the panels and the NPCs each of them
     * lists. It is a copy of its own, because the board the player was looking at must not change when one
     * of those answers arrives.
     */
    public static BoardData roleData;
    /** The village that rank list belongs to; empty means every village of the rank. */
    public static String listVillage = "";
    public static int panelScroll;
    public static int questScroll;
    public static int detailScroll;
    public static int adminScroll;
    public static int missionScroll;
    public static int nodeScroll;
    /** The list of names with access scrolls on its own, beside the permissions list. */
    public static int grantScroll;
    /** Which access list the Paneles form shows: 0 the names added by hand, 1 the rank's own. */
    public static int accessView;
    public static int settingsScroll;
    /**
     * The ladder step open in the Permisos y rangos tab, or -1 for the permissions themselves. It is a step
     * of the ladder, not a difficulty: the letters D..S always mean how hard a mission is.
     */
    public static int rankView = -1;
    /** Where the ladder list was left; it scrolls on its own, separate from the panel list. */
    public static int rankScroll;
    /** Which of the two lists of the book is open: 0 the missions in progress, 1 the finished ones. */
    public static int bookCat;
    /**
     * True when an NPC that carries more than one panel is looked at as a whole: the middle list then holds
     * every mission he could accept at that NPC, no matter which of its panels it sits on.
     */
    public static boolean groupAll;

    /** When the current board arrived, so its countdowns tick on their own until the next one comes. */
    public static long boardTakenAt;

    /** The timed missions the on-screen clock lists, pushed by the server whether a screen is open or not. */
    public static final List<Timed> timed = new ArrayList<Timed>();

    /** The clock position the server says to start with; {@link HudSettings} wins once the player moves it. */
    public static int hudMode = 5;

    /** Whether the server wants the quest-book key to open this board instead of Custom NPCs' window. */
    public static boolean replaceQuestLog = true;

    private ClientState() {}

    public static void forget() {
        selectedPanel = -1;
        selectedQuest = -1;
        selectedAdmin = -1;
        selectedMission = 0;
        adminTab = 0;
        pendingRank = NOT_STAGED;
        pendingMin = NOT_STAGED;
        listRank = -1;
        listVillage = "";
        panelScroll = 0;
        questScroll = 0;
        detailScroll = 0;
        adminScroll = 0;
        missionScroll = 0;
        nodeScroll = 0;
        grantScroll = 0;
        settingsScroll = 0;
        rankView = -1;
        rankScroll = 0;
        bookCat = 0;
        groupAll = false;
        boardTakenAt = 0L;
        timed.clear();
    }

    /** One running mission with the moment its time limit runs out, measured on this machine. */
    public static final class Timed {
        /** The Custom NPCs quest id, which is what the choice of what to show is filed under. */
        public final int id;
        public final String name;
        public final String difficulty;
        public final int done;
        public final int total;
        public final long endsAt;

        public Timed(int id, String name, String difficulty, int done, int total, long endsAt) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.done = done;
            this.total = total;
            this.endsAt = endsAt;
        }

        /** Seconds left now, never negative. */
        public int left() {
            return (int) Math.max(0L, (endsAt - System.currentTimeMillis()) / 1000L);
        }
    }
}
