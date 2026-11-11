package dev.sobrio.rebornquests;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server to client: the missions the player has running, with their progress and the seconds left of the
 * time limit, plus where on the screen they are drawn. The client counts the seconds down itself, so this
 * only travels when a clock changes — every twenty seconds, on login and right after an accept.
 */
public class MessageTime implements IMessage {
    /** The most the packet ever carries, which is more than a player can run at once. */
    private static final int MAX_ENTRIES = 16;

    /** One line of the on-screen clock. */
    public static final class Entry {
        public final int id;
        public final String name;
        public final String difficulty;
        public final int done;
        public final int total;
        public final int seconds;

        public Entry(int id, String name, String difficulty, int done, int total, int seconds) {
            this.id = id;
            this.name = name;
            this.difficulty = difficulty;
            this.done = done;
            this.total = total;
            this.seconds = seconds;
        }
    }

    private int mode = 5;
    /** Whether the client should answer the quest-book key with the mod's board instead of CNPC's window. */
    private boolean replaceLog = true;
    private final List<Entry> entries = new ArrayList<Entry>();

    public MessageTime() {}

    public MessageTime(int mode, boolean replaceLog, List<Entry> entries) {
        this.mode = mode;
        this.replaceLog = replaceLog;
        this.entries.addAll(entries);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer in = new PacketBuffer(buf);
        mode = in.readInt();
        replaceLog = in.readBoolean();
        entries.clear();
        int count = Math.min(in.readInt(), MAX_ENTRIES);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(in.readInt(), in.readString(60), in.readString(4), in.readInt(),
                    in.readInt(), in.readInt()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer out = new PacketBuffer(buf);
        out.writeInt(mode);
        out.writeBoolean(replaceLog);
        out.writeInt(Math.min(entries.size(), MAX_ENTRIES));
        for (int i = 0; i < entries.size() && i < MAX_ENTRIES; i++) {
            Entry entry = entries.get(i);
            out.writeInt(entry.id);
            out.writeString(entry.name);
            out.writeString(entry.difficulty);
            out.writeInt(entry.done);
            out.writeInt(entry.total);
            out.writeInt(entry.seconds);
        }
    }

    public static class Handler implements IMessageHandler<MessageTime, IMessage> {
        @Override
        public IMessage onMessage(MessageTime message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) return null;
            RebornQuests.proxy.showTimed(message.mode, message.replaceLog, message.entries);
            return null;
        }
    }
}
