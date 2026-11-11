package dev.sobrio.rebornquests.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Where the mission clock sits and which missions it shows, kept on this machine. The server only says
 * which place a player who has never touched the clock starts with; moving it, switching it off and
 * hiding a mission are choices of his own, so they are stored in the client's config folder and never
 * travel anywhere.
 */
public final class HudSettings {
    public static final int OFF = 0;
    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;
    public static final int BOTTOM_RIGHT = 4;
    public static final int MIDDLE_LEFT = 5;
    public static final int MIDDLE_RIGHT = 6;
    /** Set as soon as the plate has been dragged: the saved pixels win over any preset. */
    public static final int FREE = 7;

    /** The plate never grows past this many missions, however many are running. */
    public static final int MAX_SHOWN = 3;

    private static final String FOLLOW_SERVER = "-1";

    private static Properties values = new Properties();
    private static File file;
    private static boolean loaded;

    private HudSettings() {}

    /** Called once from the client proxy, so the file lands next to the mod's other client data. */
    public static void init(File config) {
        File parent = config == null ? null : config.getParentFile();
        file = parent == null ? null : new File(parent, "rebornquests-hud.properties");
        read();
    }

    /** The preset in use: the player's own choice when he has made one, the server's default otherwise. */
    public static int mode(int serverDefault) {
        String own = get("mode", FOLLOW_SERVER);
        if (own.equals(FOLLOW_SERVER)) return serverDefault;
        int parsed = parseInt(own, serverDefault);
        return parsed < 0 || parsed > FREE ? serverDefault : parsed;
    }

    /** True when the plate has been placed by hand, which is when the stored pixels are used. */
    public static boolean placed() {
        return mode(ClientState.hudMode) == FREE;
    }

    /** The saved top-left of the plate, in scaled screen pixels. */
    public static int x() {
        return parseInt(get("x", "4"), 4);
    }

    public static int y() {
        return parseInt(get("y", "4"), 4);
    }

    /** Drops the local choice, so the clock follows the pack default again. */
    public static void followServer() {
        set("mode", FOLLOW_SERVER);
        write();
    }

    /** A preset from the placement screen: the plate snaps to that place and stops being hand-placed. */
    public static void setPreset(int mode) {
        set("mode", String.valueOf(mode < OFF || mode > MIDDLE_RIGHT ? MIDDLE_LEFT : mode));
        write();
    }

    /** The plate was dragged: remember the preset it left and the pixels it landed on. */
    public static void setPlacement(int mode, int x, int y) {
        set("mode", String.valueOf(mode));
        set("x", String.valueOf(Math.max(0, x)));
        set("y", String.valueOf(Math.max(0, y)));
        write();
    }

    /** Whether that mission is allowed on the plate; missions start out visible. */
    public static boolean shown(int questId) {
        return !hidden().contains(Integer.valueOf(questId));
    }

    public static void toggle(int questId) {
        List<String> out = new ArrayList<String>();
        for (Integer id : hidden()) {
            if (id.intValue() != questId) out.add(String.valueOf(id.intValue()));
        }
        if (out.size() == hidden().size()) out.add(String.valueOf(questId));
        set("hidden", join(out));
        write();
    }

    /** Quest ids the player took off the plate. */
    public static List<Integer> hidden() {
        List<Integer> out = new ArrayList<Integer>();
        for (String part : get("hidden", "").split(",")) {
            int id = parseInt(part.trim(), -1);
            if (id > 0) out.add(Integer.valueOf(id));
        }
        return out;
    }

    /**
     * The top-left of the plate for one preset, with the pixels of a hand placement clamped back inside
     * the screen. Every place is measured from the edge it belongs to, so a change of GUI scale keeps the
     * clock where the player put it instead of pushing it off screen.
     */
    public static int[] place(int mode, int width, int height, int screenW, int screenH) {
        if (mode == FREE) {
            int left = Math.min(x(), Math.max(0, screenW - width));
            int top = Math.min(y(), Math.max(0, screenH - height));
            return new int[] {left, top};
        }
        int margin = 4;
        int hotbar = 41;
        int left = mode == TOP_RIGHT || mode == BOTTOM_RIGHT || mode == MIDDLE_RIGHT
                ? screenW - width - margin : margin;
        int top;
        if (mode == TOP_LEFT || mode == TOP_RIGHT) top = margin;
        else if (mode == MIDDLE_LEFT || mode == MIDDLE_RIGHT) top = (screenH - height) / 2;
        else top = screenH - height - hotbar;
        return new int[] {left, top};
    }

    private static String get(String key, String fallback) {
        load();
        String value = values.getProperty(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static void set(String key, String value) {
        load();
        values.setProperty(key, value);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void load() {
        if (!loaded) read();
    }

    private static synchronized void read() {
        loaded = true;
        values = new Properties();
        if (file == null || !file.isFile()) return;
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            values.load(in);
        } catch (Throwable t) {
            // A broken client file only means the clock goes back to the pack default.
        } finally {
            close(in);
        }
    }

    private static synchronized void write() {
        if (file == null) return;
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            values.store(out, "RebornQuests: colocacion del reloj de misiones y misiones ocultas, solo de este cliente.");
        } catch (Throwable t) {
            // Nothing is lost if the client cannot remember where the clock was put.
        } finally {
            close(out);
        }
    }

    private static void close(java.io.Closeable value) {
        if (value == null) return;
        try {
            value.close();
        } catch (Throwable ignored) {
            // A closed handle is not worth a crash.
        }
    }
}
