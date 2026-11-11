package dev.sobrio.rebornquests;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.event.QuestEvent;

/**
 * Custom NPCs lets a quest be taken from its dialogue, from a script and from its own book, and none of
 * those routes ever passed the rules of the board: rank, difficulty gate and cooling-off only existed where
 * this mod asked for the quest itself. This is the place they all go through, because Custom NPCs questions
 * its own event bus before it writes a quest on a player and stops when the answer is cancelled.
 *
 * <p>Only a quest one of the mod's panels lists is policed. A quest the staff never put on a board is not
 * the mod's business, and nothing of Custom NPCs or of a script of the pack is changed for it: the mod reads
 * the answer and, when it has to be no, says why.
 */
public class QuestGuards {
    /** How long the word given to the board holds, which is one server action and never a whole session. */
    private static final long BOARD_MILLIS = 3000L;
    /** The asks this mod made itself, by player and mission, so the gate below lets exactly those through. */
    private static final Map<String, Long> FROM_BOARD = new HashMap<String, Long>();

    private static boolean registered;

    /**
     * Puts the gate on the bus of Custom NPCs, which is its own and not Forge's. Without the mod there is
     * nothing to put and nothing to police, so a pack without Custom NPCs keeps working as it always did.
     */
    public static void register() {
        if (registered) return;
        try {
            if (!NpcAPI.IsAvailable() || NpcAPI.Instance() == null) return;
            EventBus bus = NpcAPI.Instance().events();
            if (bus == null) return;
            bus.register(new QuestGuards());
            registered = true;
            RebornQuests.LOGGER.info("RebornQuests: las misiones de los paneles se controlan por cualquier via.");
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo colocar el control de misiones de Custom NPCs.", t);
        }
    }

    @SubscribeEvent
    public void onQuestStart(QuestEvent.QuestStartEvent event) {
        EntityPlayerMP player = player(event);
        if (player == null) return;
        int questId = event.quest == null ? -1 : event.quest.getId();
        if (questId <= 0) return;
        // The board checked its own ask a moment ago, and a second read of the same rules is not free.
        if (takeBoard(player, questId)) return;
        if (RebornQuests.config.panelOfQuest(questId) == null) return;
        String error = BoardService.checkAccept(player, questId, false);
        if (error == null) return;
        event.setCanceled(true);
        player.sendMessage(new TextComponentString("§c" + error));
        RebornQuests.LOGGER.warn("RebornQuests: {} no pudo aceptar la mision {} por otra via: {}.",
                player.getName(), Integer.valueOf(questId), error);
    }

    /** The vanilla player of a Custom NPCs event, or null when the event came from anywhere else. */
    private static EntityPlayerMP player(QuestEvent event) {
        if (event == null || !(event.player instanceof IPlayer)) return null;
        try {
            return ((IPlayer) event.player).getMCEntity();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Says that the start about to happen is the one this mod asked for, after checking it itself. */
    static void throughBoard(EntityPlayerMP player, int questId) {
        long now = System.currentTimeMillis();
        if (FROM_BOARD.size() > 64) {
            for (Map.Entry<String, Long> entry : new HashMap<String, Long>(FROM_BOARD).entrySet()) {
                if (entry.getValue().longValue() < now) FROM_BOARD.remove(entry.getKey());
            }
        }
        FROM_BOARD.put(key(player, questId), Long.valueOf(now + BOARD_MILLIS));
    }

    /** Whether this start is the board's own ask, which is then used up and cannot be spent twice. */
    private static boolean takeBoard(EntityPlayerMP player, int questId) {
        Long until = FROM_BOARD.remove(key(player, questId));
        return until != null && until.longValue() >= System.currentTimeMillis();
    }

    private static String key(EntityPlayerMP player, int questId) {
        return player.getName() + "|" + questId;
    }
}
