package dev.sobrio.rebornquests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.UUID;

/**
 * When each player accepted each mission. Custom NPCs knows the state of a quest but never when it was
 * taken, so the cooldown and the time limit of a mission need their own clock, kept in one small file
 * next to the config so it survives restarts on both the singleplayer world and a hybrid plugin+mod server.
 *
 * <p>One file per world, because the config folder is shared: without this the mission left running in one
 * save of the pack was still cooling off in the next, and a test world was never a clean test again. Before
 * a server is up there is no world to name, and the plain file is used, which is the one an older build
 * already filled — that file is what a world without a file of its own starts from.
 */
public final class MissionStore {
    /** Entries older than this are forgotten, which keeps the file short on a long-running server. */
    private static final long KEEP_MILLIS = 60L * 60L * 24L * 45L * 1000L;
    private static final String NAME = "rebornquests-tiempos";
    /** What every file here says about itself, and what the one of this world is called. */
    private static final String HEADER =
            "RebornQuests: momento de aceptacion y de entrega de cada mision, en millis.";

    private static Properties stamps = new Properties();
    /** The config folder, which is where the clocks of every world of this install live. */
    private static File dir;
    /** The file the stamps below were read from, so a new world is noticed and its file taken. */
    private static File file;
    private static boolean loaded;

    private MissionStore() {}

    /** Called once in preInit with the config file, so the clock lives in the same folder. */
    public static void init(File config) {
        dir = config == null ? null : config.getParentFile();
        load();
    }

    /** The clocks of the world this server runs, or the plain file while there is no server up. */
    private static File target() {
        if (dir == null) return null;
        String world = safeName(worldName());
        return new File(dir, world.isEmpty() ? NAME + ".properties" : NAME + "-" + world + ".properties");
    }

    /** The save this server was started from, as empty as it can be when nothing is running yet. */
    private static String worldName() {
        try {
            net.minecraft.server.MinecraftServer server =
                    net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return "";
            String folder = server.getFolderName();
            return folder == null || folder.trim().isEmpty() ? server.getWorldName() : folder;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Only what a file name can hold everywhere, so a world called "Mundo (test)" does not break the clock. */
    private static String safeName(String value) {
        StringBuilder out = new StringBuilder();
        String wanted = value == null ? "" : value.trim();
        for (int i = 0; i < wanted.length() && out.length() < 40; i++) {
            char c = wanted.charAt(i);
            boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
            if (letter) out.append(c);
            else if (c == '_' || c == '-' || c == '.' || Character.isWhitespace(c)) out.append('_');
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') out.setLength(out.length() - 1);
        return out.toString();
    }

    /** Accept time in millis, or 0 when the mission was never accepted through the board. */
    public static long acceptedAt(UUID player, int questId) {
        return at(key(player, questId));
    }

    /** Marks the mission as taken now, which starts its limit and, later, its cooldown. */
    public static void stamp(UUID player, int questId) {
        put(key(player, questId));
    }

    /** When the board first saw this mission finished, or 0 when it never did. */
    public static long finishedAt(UUID player, int questId) {
        return at(key(player, questId) + "|fin");
    }

    /**
     * Records the finish now. Custom NPCs keeps no completion date, so the cooldown of a repeatable
     * mission is measured from the first board view after it was done.
     */
    public static void stampFinish(UUID player, int questId) {
        put(key(player, questId) + "|fin");
    }

    /** When this player last closed the game, or 0 when the mod never saw him leave. */
    public static long loggedOutAt(UUID player) {
        return at(playerKey(player) + "|out");
    }

    /** The pause starts here: while he is away, the clocks of his missions do not move. */
    public static void stampLogout(UUID player) {
        put(playerKey(player) + "|out");
    }

    /** The length of the pause has been used, so it is taken out and cannot be counted twice. */
    public static void forgetLogout(UUID player) {
        load();
        if (stamps.remove(playerKey(player) + "|out") != null) write();
    }

    /**
     * Moves one clock of one player forward by the time he spent away, so what is left on screen is exactly
     * what was left when he closed the game. Only a clock that was still running gets moved — the caller
     * checks it, because a cooldown that finished during the night has to stay finished.
     */
    public static void pause(UUID player, int questId, boolean cooldown, long away) {
        if (away <= 0L) return;
        String name = key(player, questId) + (cooldown ? "|fin" : "");
        long at = at(name);
        if (at <= 0L) return;
        load();
        stamps.setProperty(name, String.valueOf(at + away));
    }

    /** One write for a whole login walk, instead of one per mission. */
    public static void save() {
        load();
        write();
    }

    /** Minutes left before one timer runs out, counting from {@code at}; never negative. */
    public static int left(long at, int minutes) {
        if (at <= 0L || minutes <= 0) return 0;
        long spent = (System.currentTimeMillis() - at) / 60000L;
        return (int) Math.max(0L, minutes - spent);
    }

    /**
     * The same clock in seconds, which is what the countdown on the screen is drawn from. Minutes are
     * enough to decide a rule, but a player watching 9:59 fall to 9:58 wants to see it move.
     */
    public static int leftSeconds(long at, int minutes) {
        if (at <= 0L || minutes <= 0) return 0;
        return (int) Math.max(0L, (at + minutes * 60000L - System.currentTimeMillis()) / 1000L);
    }

    /** Drops every entry that is too old to matter, then writes the file. */
    public static void purge() {
        load();
        long cutoff = System.currentTimeMillis() - KEEP_MILLIS;
        boolean changed = false;
        for (String name : stamps.stringPropertyNames()) {
            long at;
            try {
                at = Long.parseLong(stamps.getProperty(name).trim());
            } catch (NumberFormatException e) {
                at = 0L;
            }
            if (at >= cutoff) continue;
            stamps.remove(name);
            changed = true;
        }
        if (changed) write();
    }

    private static String key(UUID player, int questId) {
        return playerKey(player) + "|" + questId;
    }

    /** The player half of every key, which is also where his logout is kept. */
    private static String playerKey(UUID player) {
        return player == null ? "none" : player.toString();
    }

    private static long at(String key) {
        load();
        String value = stamps.getProperty(key);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static void put(String key) {
        load();
        stamps.setProperty(key, String.valueOf(System.currentTimeMillis()));
        write();
    }

    private static void load() {
        File wanted = target();
        if (loaded && same(file, wanted)) return;
        take(wanted);
    }

    /**
     * Moves the clocks over onto the file of one world. A world that has none of its own yet starts from the
     * shared file, which is the one an older build wrote, so an update neither hands a mission back nor
     * restarts a cooldown that was already running.
     */
    private static synchronized void take(File wanted) {
        loaded = true;
        file = wanted;
        stamps = new Properties();
        if (wanted == null) return;
        if (wanted.isFile()) {
            readFrom(wanted);
            return;
        }
        File shared = new File(dir, NAME + ".properties");
        if (same(shared, wanted) || !shared.isFile()) return;
        readFrom(shared);
        if (stamps.isEmpty()) return;
        write();
        RebornQuests.LOGGER.info("RebornQuests: los relojes de {} empiezan con los de {}.",
                wanted.getName(), shared.getName());
    }

    private static void readFrom(File from) {
        InputStream in = null;
        try {
            in = new FileInputStream(from);
            stamps.load(in);
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo leer el reloj de misiones.", t);
        } finally {
            close(in);
        }
    }

    private static boolean same(File one, File other) {
        if (one == null || other == null) return one == other;
        return one.getAbsolutePath().equals(other.getAbsolutePath());
    }

    /**
     * Written whole into a temporary file and then moved onto the real one, because a crash in the middle of
     * a plain write used to leave half a clock behind — and half a clock is a mission that never ends, or one
     * that can never be taken again.
     */
    private static synchronized void write() {
        if (file == null) return;
        File temp = new File(file.getAbsolutePath() + ".tmp");
        OutputStream out = null;
        try {
            out = new FileOutputStream(temp);
            stamps.store(out, HEADER);
            close(out);
            out = null;
            java.nio.file.Files.move(temp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo guardar el reloj de misiones.", t);
            if (temp.isFile() && !temp.delete()) {
                RebornQuests.LOGGER.warn("RebornQuests: se queda el archivo temporal {}.", temp.getName());
            }
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
