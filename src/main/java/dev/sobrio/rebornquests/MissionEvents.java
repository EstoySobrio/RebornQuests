package dev.sobrio.rebornquests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Join/leave housekeeping, the delayed rank greeting and the click on a board NPC. */
public class MissionEvents {
    /** Twenty seconds of server ticks between two runs of the time-limit walk. */
    private static final int EXPIRE_TICKS = 400;
    /** The longest break the offline clock takes out, which is how long the stamp file keeps anything. */
    private static final long MAX_PAUSE = 60L * 60L * 24L * 45L * 1000L;

    private int tick;
    /** The record of changes keeps its own clock, so its interval does not move the mission walk. */
    private int auditTick;
    /** LuckPerms loads a user shortly after the join event, so the greeting waits a couple of seconds. */
    private final Map<String, Integer> pending = new HashMap<String, Integer>();
    private final List<String> expired = new ArrayList<String>();

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // The gate of Custom NPCs' own routes is put on its bus the first time somebody can be asked for
        // a quest, in case the mod of the quests was slower to answer than this one at startup.
        QuestGuards.register();
        RankService.invalidate(player.getUniqueID());
        RankService.probe();
        resumeClock(player);
        // A limit that ran out during the absence is closed the moment he comes back: until the next walk of
        // the expired missions, which runs every twenty seconds, his clock would have read 0:00 on a mission
        // that was already over and his finished count would be one short.
        if (QuestBridge.available() && BoardService.expire(player) > 0) RankService.invalidate(player.getUniqueID());
        BoardService.pushTimed(player);
        if (RebornQuests.config.announceOnLogin) pending.put(player.getName(), 60);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pending.remove(event.player.getName());
        RankService.invalidate(event.player.getUniqueID());
        // The pause is only stamped when the staff wants clocks to stop offline; a server that keeps them
        // running never writes a break for the next login to take out.
        if (!RebornQuests.config.limitRunsOffline) MissionStore.stampLogout(event.player.getUniqueID());
    }

    /**
     * What the player came back to: every clock that was still running when he left is moved forward by the
     * time he spent away, so a mission with nine minutes left still has nine. A limit or a cooldown that
     * would have run out in the night is left alone, because it really did finish while nobody was there —
     * and the walk of the expired missions closes it on the spot.
     */
    private static void resumeClock(EntityPlayerMP player) {
        RQConfig config = RebornQuests.config;
        long out = MissionStore.loggedOutAt(player.getUniqueID());
        long away = out <= 0L ? 0L : System.currentTimeMillis() - out;
        MissionStore.forgetLogout(player.getUniqueID());
        // A break only counts when it is a real one: no stamp at all, a clock that went backwards or an
        // absence longer than the horizon the file keeps entries for is left as it is.
        if (config.limitRunsOffline || away <= 0L || away > MAX_PAUSE) return;
        for (RQConfig.Mission mission : config.timedMissions()) {
            if (mission.limitMin > 0) {
                long taken = MissionStore.acceptedAt(player.getUniqueID(), mission.id);
                if (MissionStore.left(taken, mission.limitMin) > 0) {
                    MissionStore.pause(player.getUniqueID(), mission.id, false, away);
                }
            }
            if (mission.cooldownMin > 0) {
                long done = MissionStore.finishedAt(player.getUniqueID(), mission.id);
                if (MissionStore.left(done, mission.cooldownMin) > 0) {
                    MissionStore.pause(player.getUniqueID(), mission.id, true, away);
                }
            }
        }
        MissionStore.save();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        expireTimedOut();
        auditWalk();
        if (pending.isEmpty()) return;
        expired.clear();
        for (Map.Entry<String, Integer> entry : pending.entrySet()) {
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }
            expired.add(entry.getKey());
            EntityPlayerMP player = serverPlayer(entry.getKey());
            if (player != null) BoardService.announce(player);
        }
        for (String name : expired) pending.remove(name);
    }

    /**
     * The time limit of a mission is walked every twenty seconds. Nobody needs a finer clock, and the
     * walk only touches the missions the staff actually gave a limit to. The same walk refills the
     * on-screen countdown, whose seconds the client then ticks on its own.
     */
    private void expireTimedOut() {
        if (++tick < EXPIRE_TICKS) return;
        tick = 0;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || !QuestBridge.available()) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            BoardService.expire(player);
            BoardService.pushTimed(player);
        }
    }

    /**
     * The files Custom NPCs writes for itself are walked on their own clock, which the staff sets between
     * five seconds and ten minutes, and with them the NPCs it keeps in the chunks. Nothing of Custom NPCs
     * is asked for: the files are read from disk, an NPC is written to a tag the way the game writes it
     * when saving, and the only thing that happens with either is a line in the record. A server that does
     * not want the watch can switch it off and keep every single feature of the pack.
     */
    private void auditWalk() {
        RQConfig config = RebornQuests.config;
        if (!config.audit) return;
        if (++auditTick < Math.max(5, config.auditSeconds) * 20) return;
        auditTick = 0;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        RebornQuests.WATCH.scan(server);
        // The NPCs are signed with the editor session only while it is really open: their own saved data
        // moves on its own, and a download would otherwise be blamed on the last person who opened the editor.
        RebornQuests.NPCS.scan(server, RebornQuests.WATCH.liveEditor());
    }

    private static EntityPlayerMP serverPlayer(String name) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUsername(name);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isRemote || event.getTarget() == null) return;
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        List<String> panels = boardPanels(event.getTarget());
        if (panels.isEmpty()) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        // The staff opens the editor of Custom NPCs on an NPC by hitting him with one of its tools, and the
        // NPC that carries the board is exactly the one he has to be able to edit. Nobody else's click moves.
        if (BoardService.isAdmin(player) && holdsNpcTool(player, event.getHand())) return;
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (!BoardService.allowedToOpen(player) && !BoardService.isAdmin(player)) {
            player.sendMessage(new TextComponentString("§cAun no tienes rango para abrir el tablón."));
            return;
        }
        BoardService.open(player, "", panels.get(0), panels);
    }

    /** True when the hand that clicked carries a tool of Custom NPCs, editor, cloner, wand or scripter. */
    private static boolean holdsNpcTool(EntityPlayerMP player, net.minecraft.util.EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held == null || held.getItem() == null) return false;
        return held.getItem().getClass().getName().startsWith("noppes.npcs.items.");
    }

    /**
     * Which panels the clicked NPC carries. The mod's own role is the list {@code paneles.npcs}, so any NPC of
     * the pack can be the board and the role Custom NPCs has on it is never looked at — unless the staff asked
     * for one in {@code general.boardRole}, which then has to be the role the NPC really carries. An NPC that
     * is not on that list, or that does not carry the role asked for, is left alone: no click of his is
     * cancelled, and its dialogues, scripts and trades keep working exactly as Custom NPCs made them.
     */
    private static List<String> boardPanels(Entity entity) {
        if (!QuestBridge.isNpc(entity)) return new ArrayList<String>();
        if (!RebornQuests.config.isBoardRole(QuestBridge.roleType(entity))) return new ArrayList<String>();
        return RebornQuests.config.panelsOfNpc(QuestBridge.npcName(entity), QuestBridge.npcUuid(entity));
    }
}
