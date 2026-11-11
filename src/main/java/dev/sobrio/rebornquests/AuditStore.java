package dev.sobrio.rebornquests;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The audit record of the pack: every change the panel makes and every change Custom NPCs takes in its
 * own files, with the author and the exact lines that went in and out.
 *
 * <p>Three rules hold it together. It is <b>append-only</b>: every write opens the file at its end, so
 * nothing in the mod can shorten it, and there is no clear, delete, rotate or purge path anywhere in the
 * code. It is <b>not readable as a file</b>: each entry is deflated and length-prefixed, so the file is
 * binary for anybody browsing the server, and its name starts with a dot with the hidden attribute set on
 * top. And it is <b>in two places</b>: a mirror is written beside it, so deleting one copy does not
 * delete the history; the copy that survives keeps answering.
 *
 * <p>Only the console lists the entries in chat, and the staff screen reads the newest ones. The path is
 * never printed, on purpose.
 */
public final class AuditStore {
    /** Dot first, so the file is hidden on every system before the Windows attribute is even tried. */
    private static final String NAME = ".rebornquests-cambios";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /** One entry is capped: a huge script edit still records its head, never fills the disk. */
    private static final int MAX_DETAIL = 4000;

    private static File primary;
    private static File mirror;
    private static final List<String> cache = new ArrayList<String>();
    private static boolean read;

    private AuditStore() {}

    /** Called once in preInit with the config file, so the record lives in the config folder. */
    public static void init(File configFile) {
        File parent = configFile == null ? null : configFile.getParentFile();
        if (parent == null) return;
        primary = new File(parent, NAME);
        mirror = new File(savesDirectory(), NAME);
        if (sameFile(primary, mirror)) mirror = null;
    }

    /** The world or saves folder, which is the second place the record is kept. */
    private static File savesDirectory() {
        try {
            File dir = new File(net.minecraftforge.fml.common.FMLCommonHandler.instance().getSavesDirectory(),
                    "rebornquests");
            if (!dir.isDirectory() && !dir.mkdirs()) return dir;
            return dir;
        } catch (Throwable t) {
            return new File(".");
        }
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (Throwable t) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    /**
     * Appends one entry: who did it, what kind of change it is and the exact lines that changed. The
     * author of a Custom NPCs change is the last player seen with an editor screen open, or
     * {@code archivo} when nobody was.
     */
    public static void record(String author, String kind, String subject, String detail) {
        if (primary == null) return;
        String line = STAMP.format(new Date()) + "\t" + clean(author) + "\t" + clean(kind)
                + "\t" + clean(subject) + "\t" + cut(detail);
        write(line);
    }

    /** The same entry in both places; a failure in the mirror never loses the record. */
    private static void write(String entry) {
        append(primary, entry);
        if (mirror != null) append(mirror, entry);
        cache.add(entry);
    }

    private static void append(File file, String entry) {
        try {
            OutputStream raw = new FileOutputStream(file, true);
            try {
                byte[] packed = deflate(entry.getBytes(UTF8));
                DataOutputStream out = new DataOutputStream(raw);
                out.writeInt(packed.length);
                out.write(packed);
                out.flush();
            } finally {
                raw.close();
            }
            hide(file);
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: el registro de cambios no pudo escribirse en {}.",
                    file.getName(), t);
        }
    }

    /** Hidden on Windows; the leading dot already does it everywhere else. */
    private static void hide(File file) {
        try {
            java.nio.file.Files.setAttribute(file.toPath(), "dos:hidden", Boolean.TRUE,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (Throwable ignored) {
            // Nothing to do on a system without that attribute.
        }
    }

    /** Every entry, oldest first, taken from whichever copy still exists. */
    public static List<String> entries() {
        if (!read) {
            cache.clear();
            List<String> fromPrimary = load(primary);
            List<String> fromMirror = load(mirror);
            cache.addAll(fromPrimary.size() >= fromMirror.size() ? fromPrimary : fromMirror);
            read = true;
        }
        return cache;
    }

    /** One entry per line, in the order they were written; a broken tail is dropped, not fatal. */
    private static List<String> load(File file) {
        List<String> out = new ArrayList<String>();
        if (file == null || !file.isFile()) return out;
        try {
            DataInputStream in = new DataInputStream(new FileInputStream(file));
            try {
                while (true) {
                    int length;
                    try {
                        length = in.readInt();
                    } catch (java.io.EOFException done) {
                        break;
                    }
                    if (length <= 0 || length > 8 * 1024 * 1024) break;
                    byte[] packed = new byte[length];
                    in.readFully(packed);
                    String entry = inflate(packed);
                    if (entry != null) out.add(entry);
                }
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: el registro de cambios quedó ilegible en {}.",
                    file.getName(), t);
        }
        return out;
    }

    /** The newest {@code count} entries, oldest first, for the staff screen. */
    public static List<String> tail(int count) {
        List<String> all = entries();
        int from = Math.max(0, all.size() - Math.max(0, count));
        return new ArrayList<String>(all.subList(from, all.size()));
    }

    /**
     * One page of the record for the console, newest first, so the last change is always on page one.
     * Returns the entries of that page and leaves the count of pages to {@link #pages(int)}.
     */
    public static List<String> page(int number, int perPage) {
        List<String> all = entries();
        List<String> out = new ArrayList<String>();
        int size = Math.max(1, perPage);
        int last = all.size();
        int start = last - (number + 1) * size;
        int end = Math.min(last, start + size);
        start = Math.max(0, end - size);
        for (int i = start; i < end; i++) out.add(all.get(i));
        return out;
    }

    /** How many pages the record fills at this page size, never below one. */
    public static int pages(int perPage) {
        int size = Math.max(1, perPage);
        return Math.max(1, (entries().size() + size - 1) / size);
    }

    /** The date and time of one entry, as it was written. */
    public static String when(String entry) {
        return field(entry, 0);
    }

    public static String who(String entry) {
        return field(entry, 1);
    }

    public static String kind(String entry) {
        return field(entry, 2);
    }

    public static String subject(String entry) {
        return field(entry, 3);
    }

    /** The changed lines of one entry, which is the only part that runs over more than one line. */
    public static String detail(String entry) {
        String[] parts = entry.split("\t", 5);
        return parts.length < 5 ? "" : parts[4];
    }

    private static String field(String entry, int index) {
        String[] parts = entry.split("\t", 5);
        return index < parts.length ? parts[index] : "";
    }

    private static String cut(String value) {
        String clean = value == null ? "" : value.replace("\r", "");
        if (clean.length() <= MAX_DETAIL) return clean;
        return clean.substring(0, MAX_DETAIL) + "\n... (" + (clean.length() - MAX_DETAIL) + " mas)";
    }

    /** Tabs and newlines inside a field would break the record, so they never get in. */
    private static String clean(String value) {
        return value == null ? "" : value.replace("\t", " ").replace("\r", "").replace("\n", " ").trim();
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, raw.length / 4));
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static String inflate(byte[] packed) {
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, packed.length * 3));
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                out.write(buffer, 0, count);
            }
        } catch (Throwable t) {
            inflater.end();
            return null;
        }
        inflater.end();
        return new String(out.toByteArray(), UTF8);
    }
}
