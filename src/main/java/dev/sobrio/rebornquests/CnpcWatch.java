package dev.sobrio.rebornquests;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.server.MinecraftServer;

/**
 * Watches the data Custom NPCs writes for itself and puts every change in the audit record: quests,
 * dialogs and scripts, which are the three things a staff edit can break, plus the small binary files of
 * the pack.
 *
 * <p>What is watched is decided by path, on purpose. Custom NPCs keeps each quest in
 * {@code customnpcs/quests/<categoria>/<id>.json}, each dialog in {@code customnpcs/dialogs/...} and its
 * global scripts under {@code customnpcs/scripts/}; those are text, so the record can hold the exact lines
 * that came in and went out. Everything else is hashed only. Player progress ({@code playerdata}) is never
 * watched: it changes with every hand-in and would bury the real edits.
 *
 * <p>An NPC is not in any of those folders, because it is an entity: its own script, its dialog and its
 * role are saved inside the chunks. {@link NpcAudit} covers that half on the same clock, with the same
 * session of the editor and the rule that a set of NPCs moving at once is a file operation and not an edit.
 *
 * <p>Nothing here opens a Custom NPCs file for writing, and nothing hooks a script or a CNPC class: it is
 * a reader. The one exception is the category work of the editor menu, which lives in its own class and is
 * the only place the mod ever writes to those files.
 */
public final class CnpcWatch {
    /** Reading a file this big as text is pointless; it is hashed instead. */
    private static final int MAX_TEXT = 256 * 1024;
    /** One change never fills the record, however long the file is. */
    private static final int MAX_DIFF_LINES = 60;
    /** The only folder of Custom NPCs data this watch ever opens. */
    private static final String FOLDER = "customnpcs";
    /** Text files, which are the ones whose changed lines can be shown. */
    private static final List<String> TEXTUAL = Arrays.asList("json", "nss", "zs", "txt", "cfg");
    /** Folders that change on their own, so they are not staff edits. */
    private static final List<String> SKIP = Arrays.asList("playerdata");

    /** Each file as it looked the last time it was seen: its hash and, for text, its content. */
    private final Map<String, Seen> seen = new HashMap<String, Seen>();
    /** The player last seen inside an editor screen of Custom NPCs, which is who a change is blamed on. */
    private String lastEditor = "";
    private long lastEditorAt;
    /** Past this, a change is no longer credited to the last editor: the files moved on their own. */
    private static final long SESSION_MILLIS = 10L * 60L * 1000L;
    private boolean ready;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** One file as it was when this watch last looked at it. */
    private static final class Seen {
        final String hash;
        final String text;

        Seen(String hash, String text) {
            this.hash = hash;
            this.text = text;
        }
    }

    /** Walks the Custom NPCs folders of the running server and records what changed since last time. */
    public void scan(MinecraftServer server) {
        for (File root : folders(server)) {
            List<File> files = new ArrayList<File>();
            collect(new File(root, FOLDER), files, 0);
            for (File file : files) look(file, name(root, file));
        }
        if (ready) return;
        ready = true;
        AuditStore.record("el mod", "primera pasada", "customnpcs",
                seen.size() + " archivos de Custom NPCs leidos; a partir de ahora se anota cada cambio.");
    }

    /** The world folder of the running server, which is where the Custom NPCs data lives. */
    private static List<File> folders(MinecraftServer server) {
        List<File> out = new ArrayList<File>();
        if (server == null) return out;
        try {
            File dir = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
            if (dir != null && dir.isDirectory() && new File(dir, FOLDER).isDirectory()) out.add(dir);
        } catch (Throwable t) {
            // A server that cannot say where its world is simply has nothing to watch.
        }
        // Sorted so two servers with the same data write the same order.
        out.sort(new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getPath().compareTo(b.getPath()); }
        });
        return out;
    }

    /** Every watched file under one folder, three levels deep, in a stable order. */
    private void collect(File dir, List<File> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 3) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        for (File child : children) {
            if (child.isDirectory()) {
                if (SKIP.contains(child.getName().toLowerCase(Locale.ROOT))) continue;
                collect(child, out, depth + 1);
                continue;
            }
            if (!child.isFile() || child.length() <= 0L) continue;
            if (child.getName().toLowerCase(Locale.ROOT).endsWith("_old")) continue;
            out.add(child);
        }
    }

    /** One file: its text is compared with the text it had the last time it was seen. */
    private void look(File file, String label) {
        String hash = hash(file);
        if (hash.isEmpty()) return;
        Seen old = seen.get(label);
        if (old != null && old.hash.equals(hash)) return;
        boolean textual = isTextual(file);
        String text = textual && file.length() <= MAX_TEXT ? plain(file) : null;
        if (old == null) {
            seen.put(label, new Seen(hash, text));
            if (ready) {
                AuditStore.record(editor(), kindOf(label), label,
                        "aparece un archivo nuevo (" + file.length() + " bytes, hash " + shortHash(hash) + ").");
            }
            return;
        }
        String diff = text == null || old.text == null ? "" : diff(old.text, text);
        seen.put(label, new Seen(hash, text));
        AuditStore.record(editor(), kindOf(label), label, diff.isEmpty()
                ? "cambia el archivo (" + file.length() + " bytes, hash " + shortHash(hash)
                        + " en vez de " + shortHash(old.hash) + ")."
                : diff);
    }

    private static boolean isTextual(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXTUAL.contains(name.substring(dot + 1));
    }

    /**
     * Called by the client when a screen of the Custom NPCs editor opens, which is the only way an edit of
     * its files can happen. Nothing is cancelled or changed here: a name and a time are written down, that
     * is all, and the next walk of the files decides whether anything actually moved.
     */
    public void editing(String player) {
        if (player == null || player.isEmpty()) return;
        lastEditor = player;
        lastEditorAt = System.currentTimeMillis();
    }

    /** Who is held responsible: the last player seen inside an editor screen of Custom NPCs. */
    String editor() {
        if (lastEditor == null || lastEditor.isEmpty()) return "sin sesion de editor";
        if (System.currentTimeMillis() - lastEditorAt > SESSION_MILLIS) {
            return lastEditor + " (su ultima sesion de editor hace mas de " + SESSION_MILLIS / 60000L + " min)";
        }
        return lastEditor;
    }

    /**
     * The same name, but only while the session is still inside its window: an NPC saves its own animation
     * and physics every few seconds, and that must not end up signed by somebody who closed the editor long
     * ago. Empty means nobody is editing right now.
     */
    String liveEditor() {
        if (lastEditor == null || lastEditor.isEmpty()) return "";
        return System.currentTimeMillis() - lastEditorAt > SESSION_MILLIS ? "" : lastEditor.trim();
    }

    /** Which kind of change the record shows, from the name of the file it comes from. */
    private static String kindOf(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        // Everything under scripts/ is a script of the world, a player or a forge event: none of them
        // belongs to one NPC, and those are audited where the NPCs are kept.
        if (lower.contains("script")) return lower.contains("npc")
                ? "script del NPC" : "script global de Custom NPCs";
        if (lower.contains("dialog")) return "dialogo de Custom NPCs";
        if (lower.contains("quest")) return "mision de Custom NPCs";
        if (lower.contains("recipe")) return "formula de Custom NPCs";
        if (lower.contains("linked")) return "npc vinculado";
        if (lower.contains("clone")) return "clon de Custom NPCs";
        return "archivo de Custom NPCs";
    }

    private static String name(File root, File file) {
        try {
            return file.getCanonicalPath().substring(root.getCanonicalPath().length() + 1)
                    .replace('\\', '/');
        } catch (Throwable t) {
            return file.getName();
        }
    }

    private static String plain(File file) {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), UTF8);
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) > 0 && out.length() < MAX_TEXT) {
                out.append(buffer, 0, count);
            }
            return out.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            close(reader);
        }
    }

    /**
     * The lines that went out and the lines that came in. Both ends that already matched are cut off, so
     * one edited script shows the edited part and not the whole file.
     */
    static String diff(String before, String after) {
        String[] oldLines = (before == null ? "" : before).split("\n", -1);
        String[] newLines = (after == null ? "" : after).split("\n", -1);
        int head = 0;
        while (head < oldLines.length && head < newLines.length
                && oldLines[head].equals(newLines[head])) head++;
        int oldEnd = oldLines.length;
        int newEnd = newLines.length;
        while (oldEnd > head && newEnd > head && oldLines[oldEnd - 1].equals(newLines[newEnd - 1])) {
            oldEnd--;
            newEnd--;
        }
        if (oldEnd == head && newEnd == head) return "";
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = head; i < oldEnd && shown < MAX_DIFF_LINES; i++, shown++) {
            out.append("- ").append(crop(oldLines[i])).append('\n');
        }
        for (int i = head; i < newEnd && shown < MAX_DIFF_LINES; i++, shown++) {
            out.append("+ ").append(crop(newLines[i])).append('\n');
        }
        int left = (oldEnd - head) + (newEnd - head) - shown;
        if (left > 0) out.append("... ").append(left).append(" lineas mas.");
        return out.toString().trim();
    }

    private static String crop(String line) {
        String one = line.length() <= 240 ? line : line.substring(0, 240) + " ...";
        return one.replace("\t", "  ");
    }

    private static String hash(File file) {
        InputStream in = null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) out.append(String.format("%02x", Byte.valueOf(b)));
            return out.toString();
        } catch (Throwable t) {
            return "";
        } finally {
            close(in);
        }
    }

    private static String shortHash(String hash) {
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }

    private static void close(java.io.Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (Throwable ignored) {
            // Nothing left to release.
        }
    }
}
