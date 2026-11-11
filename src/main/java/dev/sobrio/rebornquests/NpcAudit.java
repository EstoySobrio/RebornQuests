package dev.sobrio.rebornquests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

/**
 * The half of the Custom NPCs audit that no folder watch can see: an NPC is an entity, so its script, the
 * dialog it carries and the role it was given live inside the saved chunks and in no file of their own.
 *
 * <p>Each pass writes every NPC to a tag with the very call the game makes when it saves the world, keeps
 * the lines a staff edit can change and compares them with the lines of the previous pass. Nothing is read
 * back into an entity and no Custom NPCs class is even named: an NPC is recognised by the package its own
 * class lives in, so a server without the mod has nothing to look at and says so once.
 *
 * <p>The keys that move on their own are left out of the comparison, because a script that walks an NPC
 * around would otherwise fill the record with its footsteps.
 */
public final class NpcAudit {
    /** Every entity Custom NPCs makes lives under this package. */
    private static final String NPC = "noppes.npcs.entity.";
    /** Runtime keys: the position, the health or the uptime of an NPC are not edits of the pack. */
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
            "Pos", "Motion", "Rotation", "Air", "Fire", "Health", "Age", "FallDistance", "HurtTime",
            "AttackTime", "HurtByTimestamp", "Invulnerable", "PersistenceRequired", "Leashed", "UUID",
            "UUIDMost", "UUIDLeast", "DimensionId", "PortalCooldown", "EntityId", "NPCID", "ModRev",
            "KilledTime", "TotalTicksAlive", "Registered", "Inventory", "Equipment", "Console",
            // What Custom NPCs writes for the way an NPC stands, walks and is being carried: a scripted NPC
            // moves those on its own every few seconds, and a record of them would be its footsteps.
            "MovingState", "StandingState", "Orientation", "inWall", "outOfWorld", "DeadBody",
            "MaxHealth", "Companion",
            // Custom NPCs keeps every script twice, as one text and as the same text split into lines.
            // Only the text is worth a record, because the lines would be reported twice.
            "ScriptList"));
    /** How many NPCs one pass is willing to write to a tag, so a huge map cannot stall the tick. */
    private static final int MAX_NPCS = 400;
    /** How deep the tag is walked, how many entries one NPC is allowed to produce and how long one of
     * them may be when it is a single line. A script is not cut: its lines are what the record shows. */
    private static final int MAX_DEPTH = 7;
    private static final int MAX_LINES = 3000;
    private static final int MAX_VALUE = 400;

    /** How many NPCs arriving or leaving in one pass is a file operation and not an edit of one of them. */
    private static final int BATCH = 4;
    /** How many names of such a set the record keeps. */
    private static final int BATCH_NAMES = 12;

    /** Each NPC as its snapshot looked the last time it was seen, keyed by the id the entity itself has. */
    private final Map<String, String> seen = new HashMap<String, String>();
    private boolean ready;

    /**
     * Records what changed in the NPCs of the running server since the last pass.
     *
     * @param editor the player whose Custom NPCs editor session is open right now, empty when nobody's is
     */
    public void scan(MinecraftServer server, String editor) {
        String who = signed(editor, server);
        List<Entity> npcs = collect(server);
        Set<String> alive = new HashSet<String>();
        List<String> fresh = new ArrayList<String>();
        for (Entity entity : npcs) {
            String key = identity(entity);
            String text = snapshot(entity);
            if (key.isEmpty() || text == null) continue;
            alive.add(key);
            String old = seen.get(key);
            seen.put(key, text);
            if (old == null) {
                if (ready) fresh.add(key);
                continue;
            }
            if (old.equals(text)) continue;
            String diff = CnpcWatch.diff(old, text);
            AuditStore.record(who, kind(diff), key, diff.isEmpty()
                    ? "cambia el NPC sin que se note en sus datos guardados." : diff);
        }
        batch(fresh, who, "aparece un NPC nuevo.", "Llegan");
        if (npcs.size() >= MAX_NPCS) return;
        List<String> gone = new ArrayList<String>();
        for (String key : new ArrayList<String>(seen.keySet())) {
            if (alive.contains(key)) continue;
            seen.remove(key);
            if (ready) gone.add(key);
        }
        batch(gone, who, "desaparece el NPC.", "Se van");
        if (ready) return;
        ready = true;
        AuditStore.record("el mod", "primera pasada", "npcs de Custom NPCs",
                seen.size() + " NPCs leidos; a partir de ahora se anota cada cambio de su script o de sus datos.");
    }

    /**
     * A set of NPCs that moves all at once is written as one entry and signed by nobody: a download, an
     * import or a chunk that unloads brings or takes dozens of them in a single pass, and one line each
     * blamed on whoever happened to have the editor open would fill the record with edits nobody made. A
     * handful of them is still the one NPC somebody was working on, so those keep their own entry.
     */
    private static void batch(List<String> keys, String who, String single, String head) {
        if (keys.isEmpty()) return;
        if (keys.size() < BATCH) {
            for (String key : keys) {
                AuditStore.record(who, "npc de Custom NPCs", key, single);
            }
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < keys.size() && i < BATCH_NAMES; i++) {
            if (names.length() > 0) names.append(", ");
            names.append(keys.get(i));
        }
        if (keys.size() > BATCH_NAMES) names.append(" y ").append(keys.size() - BATCH_NAMES).append(" mas");
        AuditStore.record("descarga o importacion", "lote de NPCs", keys.size() + " NPCs",
                head + " " + keys.size() + " NPCs en la misma pasada, lo que hace un fichero y no la"
                        + " edicion de uno: " + names + ".");
    }

    /**
     * Who an NPC change is signed with. The saved data of an NPC moves by itself, so only an editor session
     * that is open right now and held by a player who is still connected is worth a name; everything else
     * says nobody was editing instead of inventing somebody.
     */
    private static String signed(String editor, MinecraftServer server) {
        String who = editor == null ? "" : editor.trim();
        if (who.isEmpty() || server == null) return "sin sesion de editor";
        return server.getPlayerList().getPlayerByUsername(who) == null ? "sin sesion de editor" : who;
    }

    /** The NPCs loaded in every world of the server, capped so one pass costs a bounded amount of time. */
    private static List<Entity> collect(MinecraftServer server) {
        List<Entity> out = new ArrayList<Entity>();
        if (server == null || server.worlds == null) return out;
        for (WorldServer world : server.worlds) {
            if (world == null) continue;
            List<Entity> entities;
            try {
                entities = world.getLoadedEntityList();
            } catch (Throwable t) {
                continue;
            }
            for (Entity entity : entities) {
                if (entity == null || !entity.getClass().getName().startsWith(NPC)) continue;
                out.add(entity);
                if (out.size() >= MAX_NPCS) return out;
            }
        }
        out.sort(new Comparator<Entity>() {
            @Override
            public int compare(Entity a, Entity b) {
                return identity(a).compareTo(identity(b));
            }
        });
        return out;
    }

    /** The name the staff sees, with the id that never repeats, so a renamed NPC is still the same entry. */
    private static String identity(Entity entity) {
        try {
            return entity.getName() + " #" + entity.getPersistentID().toString().substring(0, 8);
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * The saved data of one NPC as sorted text: one line per value, with the path it sits at. Sorted, so
     * the same NPC written twice gives the same text and a change shows as a change and not as a shuffle.
     */
    private static String snapshot(Entity entity) {
        try {
            NBTTagCompound tag = new NBTTagCompound();
            entity.writeToNBT(tag);
            List<String> lines = new ArrayList<String>();
            walk(tag, "", lines, 0);
            if (lines.size() > MAX_LINES) return null;
            Collections.sort(lines);
            StringBuilder out = new StringBuilder();
            for (String line : lines) out.append(line).append('\n');
            return out.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void walk(NBTBase value, String path, List<String> lines, int depth) {
        if (value == null || depth > MAX_DEPTH || lines.size() > MAX_LINES) return;
        if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;
            for (int i = 0; i < list.tagCount(); i++) {
                walk(list.get(i), path + "/" + i, lines, depth + 1);
            }
            return;
        }
        if (value instanceof NBTTagCompound) {
            NBTTagCompound tag = (NBTTagCompound) value;
            for (String key : tag.getKeySet()) {
                if (SKIP.contains(key)) continue;
                walk(tag.getTag(key), path + "/" + key, lines, depth + 1);
            }
            return;
        }
        String text = value.toString();
        if (text.length() > MAX_VALUE && text.indexOf('\n') < 0) {
            text = text.substring(0, MAX_VALUE) + " ...";
        }
        lines.add(crop(path + " = " + text));
    }

    /** A script line and a role line mean two different things in the record, so they are named apart. */
    private static String kind(String diff) {
        return diff.contains("/Scripts") || diff.contains("ScriptLanguage") || diff.contains("ScriptEnabled")
                ? "script del NPC" : "datos del NPC";
    }

    /** One value per line, without the characters that would break the record. */
    private static String crop(String line) {
        return line.replace('\t', ' ').replace('\r', ' ');
    }
}
