package dev.sobrio.rebornquests.client;

import java.util.List;

import dev.sobrio.rebornquests.BoardData;
import dev.sobrio.rebornquests.CommonProxy;
import dev.sobrio.rebornquests.MessageTime.Entry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

/** Hands the board coming from the server to the screen, always on the render thread. */
public class ClientProxy extends CommonProxy {
    @Override
    public void showBoard(final BoardData data) {
        // A silent answer belongs to the box inside the Custom NPCs editor: it changes nothing on screen,
        // it only tells that box what the panels say now.
        if (data.silent) {
            ClientState.roleData = data;
            return;
        }
        ClientState.data = data;
        ClientState.boardTakenAt = System.currentTimeMillis();
        focus(data);
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (data.screen == BoardData.SCREEN_ADMIN) {
                    if (mc.currentScreen instanceof GuiAdminPanel) ((GuiAdminPanel) mc.currentScreen).reload();
                    else mc.displayGuiScreen(new GuiAdminPanel());
                    return;
                }
                if (mc.currentScreen instanceof GuiMisiones) ((GuiMisiones) mc.currentScreen).reload();
                else mc.displayGuiScreen(new GuiMisiones());
            }
        });
    }

    /**
     * The clocks arrive on their own, because the on-screen countdown has to work with no screen open.
     * The deadline is turned into this machine's clock, so the count runs between two updates. The mode is
     * only the place to start with: a player who has moved the clock keeps his own spot.
     */
    @Override
    public void showTimed(final int mode, final boolean replaceLog, final List<Entry> entries) {
        final long now = System.currentTimeMillis();
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientState.hudMode = mode;
                ClientState.replaceQuestLog = replaceLog;
                ClientState.timed.clear();
                for (Entry entry : entries) {
                    ClientState.timed.add(new ClientState.Timed(entry.id, entry.name, entry.difficulty,
                            entry.done, entry.total, now + entry.seconds * 1000L));
                }
            }
        });
    }

    @Override
    public void registerHud() {
        HudSettings.init(dev.sobrio.rebornquests.RebornQuests.configFile);
        MinecraftForge.EVENT_BUS.register(new HudTimeLeft());
        MinecraftForge.EVENT_BUS.register(new QuestLogHook());
        // Nothing of the Custom NPCs editor is drawn over: their screens stay exactly as they ship. The panel
        // an NPC opens is decided by its role, and the lists are edited from the staff screen and the
        // command. NpcEditorUi is here but not registered until its replacement is written.
    }

    /**
     * The NPC that was clicked already decided which panel is interesting, so the board opens there
     * instead of at the top of the list. The staff screen keeps the panel it had selected.
     */
    private static void focus(BoardData data) {
        if (data.screen != BoardData.SCREEN_ADMIN) {
            // An NPC of several panels is one counter, so its board opens with everything it offers together.
            ClientState.groupAll = data.group.size() > 1;
        }
        if (data.focus == null || data.focus.trim().isEmpty()) return;
        int index = data.indexOfPanel(data.focus);
        if (index < 0) return;
        if (data.screen == BoardData.SCREEN_ADMIN) {
            ClientState.selectedAdmin = index;
            ClientState.selectedMission = 0;
            ClientState.missionScroll = 0;
            return;
        }
        ClientState.selectedPanel = index;
        ClientState.selectedQuest = -1;
        ClientState.questScroll = 0;
        ClientState.detailScroll = 0;
    }
}
