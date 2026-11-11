package dev.sobrio.rebornquests.client;

import dev.sobrio.rebornquests.MessageAction;
import dev.sobrio.rebornquests.RebornQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The quest book of Custom NPCs is answered with the mod's own board. Nothing of Custom NPCs is touched:
 * its window is cancelled as it opens and the same key, the same side tab and the same NPC button then show
 * the Dynasty screen, which reads the very same quests from the server. The switch is the pack's, so a
 * server that wants the original window back can hand it over without restarting anything.
 *
 * <p>The same hook is what tells the server that the editor of Custom NPCs is in somebody's hands, which is
 * the only thing the audit has to say who changed a script. It says so when a screen of that editor opens
 * and again while it stays open, because the record must never blame a download on a name whose session
 * ended a long edit ago.
 */
public class QuestLogHook {
    private static final String QUEST_LOG = "noppes.npcs.client.gui.player.GuiQuestLog";
    /** Everything Custom NPCs draws that is not one of the screens a player uses to talk to an NPC. */
    private static final String EDITOR = "noppes.npcs.client.gui.";
    private static final String PLAYER_SCREENS = "noppes.npcs.client.gui.player.";
    /** Ten seconds of one open editor screen are still one session: the mod keeps saying so. */
    private static final int HEARTBEAT_TICKS = 200;

    private int beat;

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui == null) return;
        String name = gui.getClass().getName();
        // The editor of Custom NPCs is the only thing that can change its files, so the mod says so the
        // moment it opens: the record then knows whose hands the next changed line came from.
        if (isEditor(name)) {
            beat = 0;
            RebornQuests.NET.sendToServer(new MessageAction(MessageAction.EDITOR, 0));
        }
        if (!ClientState.replaceQuestLog || !name.equals(QUEST_LOG)) return;
        event.setCanceled(true);
        final Minecraft mc = Minecraft.getMinecraft();
        RebornQuests.NET.sendToServer(new MessageAction(MessageAction.OPEN, 0));
        // The screen is opened after this one has been cancelled, so the two never fight over the slot.
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                mc.displayGuiScreen(new GuiMisiones());
            }
        });
    }

    /**
     * One editor screen can sit open for half an hour while a script is written, so the session is repeated
     * on its own clock. Without it the walk of the NPCs would stop knowing who is editing and would have to
     * sign every change of that NPC as if nobody had touched it.
     */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (gui == null || !isEditor(gui.getClass().getName())) return;
        if (++beat < HEARTBEAT_TICKS) return;
        beat = 0;
        RebornQuests.NET.sendToServer(new MessageAction(MessageAction.EDITOR, 0));
    }

    /** Which of the screens Custom NPCs draws belong to its editor and which to the player's own book. */
    private static boolean isEditor(String name) {
        return name.startsWith(EDITOR) && !name.startsWith(PLAYER_SCREENS);
    }
}
