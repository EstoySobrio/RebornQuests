package dev.sobrio.rebornquests;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client to server: the only few things the board can ask for. Every check runs again here. */
public class MessageAction implements IMessage {
    public static final int OPEN = 0;
    public static final int ACCEPT = 1;
    public static final int CLOSE = 2;
    /** The client saw a screen of the Custom NPCs editor open: whoever it was owns the next file change. */
    public static final int EDITOR = 3;

    public int kind;
    public int questId;

    public MessageAction() {}

    public MessageAction(int kind, int questId) {
        this.kind = kind;
        this.questId = questId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readInt();
        questId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(kind);
        buf.writeInt(questId);
    }

    public static class Handler implements IMessageHandler<MessageAction, IMessage> {
        @Override
        public IMessage onMessage(final MessageAction message, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null || player.server == null) return null;
            final int kind = message.kind;
            final int questId = message.questId;
            if (!RateGate.allow(player, "tablon" + kind, every(kind))) return null;
            // A packet is answered by a network thread, and everything this one leads to belongs to the
            // server: the quest book of Custom NPCs, the config file, the chat and the other players.
            player.server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    handle(player, kind, questId);
                }
            });
            return null;
        }

        /** How far apart two asks of the same kind have to be, in millis. */
        private static long every(int kind) {
            if (kind == ACCEPT) return 300L;
            if (kind == OPEN) return 600L;
            if (kind == EDITOR) return 200L;
            return 400L;
        }

        private static void handle(EntityPlayerMP player, int kind, int questId) {
            if (kind == OPEN) {
                if (!BoardService.allowedToOpen(player)) {
                    player.sendMessage(new TextComponentString("§cNo tienes acceso al tablón de misiones."));
                    return;
                }
                BoardService.open(player);
                return;
            }
            if (kind == ACCEPT) {
                // The node of the tablón is asked again inside accept: the one answered here is the screen,
                // and a hand-made packet never had a screen in front of it.
                String error = BoardService.accept(player, questId);
                if (error != null) {
                    player.sendMessage(new TextComponentString("§c" + error));
                    return;
                }
                BoardService.pushTimed(player);
                return;
            }
            if (kind == EDITOR) {
                // Only creative can open the editor of Custom NPCs, so the check is what makes the name
                // worth anything: a survival client reporting an editor session is simply ignored.
                if (player.capabilities.isCreativeMode) RebornQuests.WATCH.editing(player.getName());
                return;
            }
            RankService.invalidate(player.getUniqueID());
        }
    }
}
