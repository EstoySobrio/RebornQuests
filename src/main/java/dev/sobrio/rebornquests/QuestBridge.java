package dev.sobrio.rebornquests;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.api.IContainer;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.constants.QuestType;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.entity.data.INPCRole;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.api.handler.data.IQuestCategory;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.wrapper.NPCWrapper;

/**
 * Reads the Custom NPCs quest book. Panels are Custom NPCs quest categories, so nothing has to be
 * registered twice: the board is a view over what the pack already defines.
 */
public final class QuestBridge {
    /** Reward lines are read-only decoration, so the list is cut well before it can fill a screen. */
    private static final int MAX_REWARDS = 8;

    private static Map<Integer, String> typeNames;
    private static boolean cnpcMissing;

    private QuestBridge() {}

    public static boolean available() {
        if (cnpcMissing) return false;
        try {
            return NpcAPI.IsAvailable() && NpcAPI.Instance() != null;
        } catch (Throwable t) {
            cnpcMissing = true;
            return false;
        }
    }

    public static String error() {
        return cnpcMissing ? "Custom NPCs no esta cargado" : "";
    }

    /** The quests of one panel, in the order the panel lists them. Missing quests are skipped. */
    public static List<QuestRow> quests(List<Integer> ids) {
        List<QuestRow> out = new ArrayList<QuestRow>();
        if (!available()) return out;
        for (Integer id : ids) {
            if (id == null) continue;
            QuestRow row = quest(id.intValue());
            if (row != null) out.add(row);
        }
        return out;
    }

    /** One quest by id, or null when Custom NPCs no longer knows it. */
    public static QuestRow quest(int questId) {
        if (!available()) return null;
        try {
            IQuest quest = NpcAPI.Instance().getQuests().get(questId);
            if (quest == null) return null;
            QuestRow out = new QuestRow(quest.getId(), safe(quest.getName()), safe(quest.getLogText()),
                    quest.getType(), quest.getIsRepeatable());
            out.completeText = safe(quest.getCompleteText());
            out.completer = safe(quest.getNpcName());
            out.rewards = rewards(quest);
            return out;
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo leer la mision {}.", questId, t);
            return null;
        }
    }

    /**
     * What the quest hands out, as one line. Custom NPCs gives the rewards when the quest is handed in at
     * its NPC, so the board only reads them: the mod never has to hand anything out itself.
     */
    private static String rewards(IQuest quest) {
        try {
            IContainer container = quest.getRewards();
            if (container == null) return "";
            StringBuilder out = new StringBuilder();
            int shown = 0;
            for (IItemStack stack : container.getItems()) {
                if (stack == null || stack.isEmpty()) continue;
                String name = safe(stack.getDisplayName());
                if (name.isEmpty()) continue;
                if (out.length() > 0) out.append(" · ");
                out.append(name);
                if (stack.getStackSize() > 1) out.append('x').append(stack.getStackSize());
                if (++shown == MAX_REWARDS) break;
            }
            return out.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Every quest of the pack with its category, which is what the admin mission picker lists. */
    public static List<QuestRef> allQuests() {
        List<QuestRef> out = new ArrayList<QuestRef>();
        if (!available()) return out;
        try {
            for (IQuestCategory holder : NpcAPI.Instance().getQuests().categories()) {
                if (holder == null) continue;
                String category = safe(holder.getName());
                for (IQuest quest : holder.quests()) {
                    if (quest == null) continue;
                    out.add(new QuestRef(quest.getId(), safe(quest.getName()), category));
                }
            }
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo listar todas las misiones.", t);
        }
        return out;
    }

    /**
     * A handful of quests already carrying the player's progress. The on-screen clock uses it because it
     * runs with no board open, so nobody has asked for the full board that would otherwise fill it in.
     */
    public static List<QuestRow> rows(EntityPlayerMP player, List<Integer> ids) {
        List<QuestRow> out = quests(ids);
        apply(player, out, new Stats());
        return out;
    }

    /** Fills progress and state for one player, reusing the stats box across every panel. */
    public static void apply(EntityPlayerMP player, List<QuestRow> rows, Stats stats) {
        if (!available() || rows.isEmpty()) return;
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(player);
            if (!(wrapped instanceof IPlayer)) return;
            IPlayer questPlayer = (IPlayer) wrapped;
            stats.finished = questPlayer.getFinishedQuests().length;
            stats.active = questPlayer.getActiveQuests().length;
            if (!stats.read) {
                // Custom NPCs is the only owner of what has been finished; the board just counts it by level.
                stats.read = true;
                for (IQuest quest : questPlayer.getFinishedQuests()) {
                    if (quest != null) stats.finishedIds.add(Integer.valueOf(quest.getId()));
                }
            }
            for (QuestRow row : rows) {
                if (questPlayer.hasFinishedQuest(row.id)) {
                    row.state = BoardData.DONE;
                } else if (questPlayer.hasActiveQuest(row.id)) {
                    row.state = BoardData.ACTIVE;
                } else {
                    row.state = BoardData.OPEN;
                }
                if (row.state != BoardData.DONE) {
                    row.acceptable = row.state == BoardData.OPEN && questPlayer.canQuestBeAccepted(row.id);
                }
                progress(questPlayer, row);
            }
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo leer el progreso del jugador {}.", player.getName(), t);
        }
    }

    private static void progress(IPlayer questPlayer, QuestRow row) {
        try {
            IQuest quest = NpcAPI.Instance().getQuests().get(row.id);
            if (quest == null) return;
            IQuestObjective[] objectives = quest.getObjectives(questPlayer);
            if (objectives == null) return;
            int done = 0;
            int total = 0;
            StringBuilder text = new StringBuilder();
            for (IQuestObjective objective : objectives) {
                if (objective == null) continue;
                total++;
                if (objective.isCompleted()) done++;
                if (text.length() < 220) {
                    if (text.length() > 0) text.append(" · ");
                    text.append(safe(objective.getText()));
                    if (objective.getMaxProgress() > 1) {
                        text.append(" (").append(Math.min(objective.getProgress(), objective.getMaxProgress()))
                            .append('/').append(objective.getMaxProgress()).append(')');
                    }
                }
            }
            row.done = done;
            row.total = total;
            if (row.text.isEmpty() && text.length() > 0) row.text = text.toString();
        } catch (Throwable t) {
            // Objectives are optional: a manual quest simply shows its log text.
        }
    }

    /** Starts a Custom NPCs quest for the player; Custom NPCs keeps the reward rules. */
    public static String start(EntityPlayerMP player, int questId, boolean panelUnlocked) {
        if (!available()) return "Custom NPCs no esta disponible en este servidor.";
        if (!panelUnlocked) return "Este panel todavia no esta disponible para tu rango.";
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(player);
            if (!(wrapped instanceof IPlayer)) return "No se pudo leer tu progreso de misiones.";
            IPlayer questPlayer = (IPlayer) wrapped;
            if (questPlayer.hasFinishedQuest(questId)) return "Esa mision ya esta completada.";
            if (questPlayer.hasActiveQuest(questId)) return "Esa mision ya esta activa.";
            if (!questPlayer.canQuestBeAccepted(questId)) return "Esa mision no se puede aceptar ahora mismo.";
            questPlayer.startQuest(questId);
            return null;
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo aceptar la mision {}.", questId, t);
            return "No se pudo aceptar la mision. Revisa latest.log.";
        }
    }

    /**
     * The quests one player holds, running or handed in, whatever panel of the mod they sit on or do not
     * sit on. Custom NPCs is the only owner of that list; the board reads it to show what he has taken away
     * from a dialogue the staff never put on a board.
     */
    public static List<Integer> heldIds(EntityPlayerMP player) {
        List<Integer> out = new ArrayList<Integer>();
        if (!available()) return out;
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(player);
            if (!(wrapped instanceof IPlayer)) return out;
            IPlayer questPlayer = (IPlayer) wrapped;
            for (IQuest quest : questPlayer.getActiveQuests()) {
                if (quest != null) out.add(Integer.valueOf(quest.getId()));
            }
            for (IQuest quest : questPlayer.getFinishedQuests()) {
                if (quest != null) out.add(Integer.valueOf(quest.getId()));
            }
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudieron listar las misiones de {}.", player.getName(), t);
        }
        return out;
    }

    /**
     * How many quests Custom NPCs holds as finished for one player, which is all the panel gate of another
     * player needs. Negative when the quest book cannot be read, so a broken read never opens a panel.
     */
    public static int finishedCount(EntityPlayerMP player) {        if (!available()) return -1;
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(player);
            return wrapped instanceof IPlayer ? ((IPlayer) wrapped).getFinishedQuests().length : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Whether Custom NPCs still holds that quest as running for the player. */
    public static boolean isActive(EntityPlayerMP player, int questId) {
        if (!available()) return false;
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(player);
            return wrapped instanceof IPlayer && ((IPlayer) wrapped).hasActiveQuest(questId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Gives a mission back so it can be accepted again; null when it worked. */
    public static String stop(EntityPlayerMP player, int questId) {
        if (!available()) return "Custom NPCs no esta disponible en este servidor.";
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(player);
            if (!(wrapped instanceof IPlayer)) return "No se pudo leer tu progreso de misiones.";
            ((IPlayer) wrapped).stopQuest(questId);
            return null;
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo parar la mision {}.", questId, t);
            return "No se pudo retirar la mision. Revisa latest.log.";
        }
    }

    /** -1 when the entity is not a Custom NPCs NPC; otherwise its role id, see RoleType. */
    public static int roleType(Entity entity) {
        NPCWrapper npc = npc(entity);
        if (npc == null) return -1;
        try {
            INPCRole role = npc.getRole();
            return role == null ? -1 : role.getType();
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * The mission picked in the NPC's role configuration. Custom NPCs stores it as a plain public
     * field on the role object, and the API interface does not expose it, so it is read by reflection.
     * Returns -1 when the NPC has no role mission at all.
     */
    public static int roleQuest(Entity entity) {
        NPCWrapper npc = npc(entity);
        if (npc == null) return -1;
        try {
            INPCRole role = npc.getRole();
            if (role == null) return -1;
            Object value = role.getClass().getField("questId").get(role);
            int id = value instanceof Integer ? ((Integer) value).intValue() : -1;
            return id > 0 ? id : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean isNpc(Entity entity) {
        return npc(entity) != null;
    }

    /** The name typed in the Custom NPCs editor, which is how the mod's own board role finds an NPC. */
    public static String npcName(Entity entity) {
        NPCWrapper npc = npc(entity);
        if (npc == null) return "";
        try {
            String value = npc.getName();
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * The id Custom NPCs gives one NPC of its own, for the board role that has to tell two NPCs of the same
     * name apart. Empty when the NPC cannot be read.
     */
    public static String npcUuid(Entity entity) {
        NPCWrapper npc = npc(entity);
        if (npc == null) return "";
        try {
            String value = npc.getUUID();
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    private static NPCWrapper npc(Entity entity) {
        if (entity == null || !available()) return null;
        try {
            Object wrapped = NpcAPI.Instance().getIEntity(entity);
            return wrapped instanceof NPCWrapper ? (NPCWrapper) wrapped : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String typeLabel(int type) {
        if (typeNames == null) typeNames = loadTypes();
        String name = typeNames.get(type);
        if (name == null) return "Otro";
        if (name.equals("MANUAL")) return "Manual";
        if (name.equals("ITEM")) return "Objetos";
        if (name.equals("KILL")) return "Derrotar";
        if (name.equals("AREA_KILL")) return "Derrotar zona";
        if (name.equals("LOCATION")) return "Lugar";
        if (name.equals("DIALOG")) return "Dialogo";
        return name;
    }

    private static Map<Integer, String> loadTypes() {
        Map<Integer, String> out = new HashMap<Integer, String>();
        try {
            for (Field field : QuestType.class.getFields()) {
                if (field.getType() != int.class) continue;
                field.setAccessible(true);
                out.put(field.getInt(null), field.getName());
            }
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudieron leer los tipos de mision.", t);
        }
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").replace("\r", "").trim();
    }

    /** Per-player counters taken from Custom NPCs. */
    public static final class Stats {
        public int finished;
        public int active;
        /**
         * The ids Custom NPCs holds as finished, read once per board so the difficulty gates can count
         * them. Nothing here is ever written back: the quest book stays exactly where it is.
         */
        public final List<Integer> finishedIds = new ArrayList<Integer>();
        private boolean read;
    }

    /** One quest line before the player's progress is known. */
    public static final class QuestRow {
        public final int id;
        public final String name;
        public String text;
        public final int type;
        public final boolean repeatable;
        /** The NPC Custom NPCs asks for when the quest is done, straight from its own book. */
        public String completer = "";
        /** What Custom NPCs shows once the quest has been handed in. */
        public String completeText = "";
        /** The reward list of the quest, one line, read from its reward container. */
        public String rewards = "";
        public int state = BoardData.OPEN;
        public int done;
        public int total;
        public boolean acceptable;

        QuestRow(int id, String name, String text, int type, boolean repeatable) {
            this.id = id;
            this.name = name;
            this.text = text;
            this.type = type;
            this.repeatable = repeatable;
        }
    }

    /** A quest as the mission picker lists it: its id, its name and the Custom NPCs category it lives in. */
    public static final class QuestRef {
        public final int id;
        public final String name;
        public final String category;

        QuestRef(int id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }
    }
}
