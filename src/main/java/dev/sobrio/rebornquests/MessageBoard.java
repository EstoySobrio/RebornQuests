package dev.sobrio.rebornquests;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server to client: the board the player just asked for. */
public class MessageBoard implements IMessage {
    public BoardData data = new BoardData();

    public MessageBoard() {}

    public MessageBoard(BoardData data) {
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = BoardData.read(new net.minecraft.network.PacketBuffer(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        data.write(new net.minecraft.network.PacketBuffer(buf));
    }

    public static class Handler implements IMessageHandler<MessageBoard, IMessage> {
        @Override
        public IMessage onMessage(MessageBoard message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) RebornQuests.proxy.showBoard(message.data);
            return null;
        }
    }
}
