package dev.sobrio.rebornquests;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client to server: the admin screen creating a panel, filing it under a village, editing its gate,
 *  its permissions or the rules of one mission, or changing one config key. The staff node is checked again here. */
public class AdminAction implements IMessage {
    public static final int RULE = 0;
    public static final int NAME = 1;
    public static final int RELOAD = 2;
    public static final int CONFIG = 3;
    public static final int LOOKUP = 4;
    public static final int PANEL_NEW = 5;
    public static final int PANEL_DEL = 6;
    /** Sets the village a panel belongs to. */
    public static final int CATEGORY = 7;
    public static final int NODE_ADD = 8;
    public static final int NODE_DEL = 9;
    public static final int MISSION_ADD = 10;
    public static final int MISSION_DEL = 11;
    /** Difficulty, cooldown and time limit of one mission: {@link #rankIndex} is the quest id. */
    public static final int MISSION_DIFF = 12;
    public static final int MISSION_COOLDOWN = 13;
    public static final int MISSION_LIMIT = 14;
    public static final int PANEL_UP = 15;
    public static final int PANEL_DOWN = 16;
    /** Reorders the whole list so every village is together. */
    public static final int PANEL_GROUP = 17;
    /** One ladder step, edited from its own tab: {@link #rankIndex} is the position in the ladder. */
    public static final int RANK_GROUP = 18;
    public static final int RANK_LABEL = 19;
    public static final int RANK_UP = 20;
    public static final int RANK_DOWN = 21;
    /** A new ladder step: {@link #rankIndex} is the step it goes after and {@link #name} its group. */
    public static final int RANK_ADD = 22;
    /** Takes one ladder step out; refused while a panel still asks for it. */
    public static final int RANK_DEL = 23;
    /** Puts one rank's list of a panel back onto the panel's own list. */
    public static final int MISSION_LIST = 24;
    /** Lets one named player open a panel without rank or permissions: {@link #name} is the player. */
    public static final int GRANT_ADD = 25;
    public static final int GRANT_DEL = 26;
    /** Puts one NPC onto the panel, or takes it off: {@link #name} is its Custom NPCs name or {@code #uuid}. */
    public static final int NPC_ADD = 27;
    public static final int NPC_DEL = 28;
    /** Asks for the panel list without opening the staff screen: the NPC editor's own box sends this. */
    public static final int NPC_PANELS = 29;
    /** One panel per rank of every village, leaving alone the panels the staff already made. */
    public static final int PANEL_SEED = 30;

    public int kind;
    public String panel = "";
    public String name = "";
    public int rankIndex = -1;
    public int minFinished;
    /** The village a mission list is written for; empty means the panel's own list. */
    public String village = "";
    /** Which ladder step a mission action points at: -1 is the plain list of the panel. */
    public int listRank = -1;
    /**
     * True when the answer is meant for a screen that is not the staff panel, so the server sends it without
     * pulling the staff out of the window he is standing in. The Custom NPCs NPC editor uses it.
     */
    public boolean silent;

    public AdminAction() {}

    public AdminAction(int kind, String panel, String name, int rankIndex, int minFinished) {
        this.kind = kind;
        this.panel = panel == null ? "" : panel;
        this.name = name == null ? "" : name;
        this.rankIndex = rankIndex;
        this.minFinished = minFinished;
    }

    /** The same action pointed at one rank's list of one village. */
    public AdminAction toList(int listRank, String village) {
        this.listRank = listRank;
        this.village = village == null ? "" : village;
        return this;
    }

    /** The same action, answered without opening the staff screen. */
    public AdminAction silently() {
        this.silent = true;
        return this;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        net.minecraft.network.PacketBuffer in = new net.minecraft.network.PacketBuffer(buf);
        kind = in.readInt();
        panel = in.readString(80);
        name = in.readString(MAX_VALUE);
        rankIndex = in.readInt();
        minFinished = in.readInt();
        village = in.readString(60);
        listRank = in.readInt();
        silent = in.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        net.minecraft.network.PacketBuffer out = new net.minecraft.network.PacketBuffer(buf);
        out.writeInt(kind);
        out.writeString(panel);
        out.writeString(name);
        out.writeInt(rankIndex);
        out.writeInt(minFinished);
        out.writeString(village);
        out.writeInt(listRank);
        out.writeBoolean(silent);
    }

    /** A ladder or an alias list is long, so the value field carries up to this many characters. */
    public static final int MAX_VALUE = 400;

    public static class Handler implements IMessageHandler<AdminAction, IMessage> {
        @Override
        public IMessage onMessage(final AdminAction message, MessageContext ctx) {
            if (ctx.side != Side.SERVER) return null;
            final EntityPlayerMP player = ctx.getServerHandler().player;
            if (player == null || player.server == null) return null;
            if (!RateGate.staff(player)) return null;
            // Config writes, LuckPerms reads, the record and every reply all belong to the server thread,
            // and a packet is answered somewhere else, so the whole of it is handed over.
            player.server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    handle(player, message);
                }
            });
            return null;
        }

        private static void handle(EntityPlayerMP player, AdminAction message) {
            if (!BoardService.isAdmin(player)) {
                // The box inside the editor of Custom NPCs asks read-only questions and has no screen of its
                // own to be sent to, so a player without the node is answered on the same channel instead of
                // being written at: the block says what is missing and nothing is asked a second time.
                if (message.silent) {
                    BoardService.sendDenied(player, "Te falta " + BoardService.adminRule() + " para mover paneles.");
                    return;
                }
                player.sendMessage(new TextComponentString("§cTe falta " + BoardService.adminRule()
                        + " para mover paneles."));
                return;
            }
            // A change of the mod's own rules is only ever asked for by the staff screen: the answer of a
            // silent ask is a panel list, so anything else coming on that channel is a hand-made packet.
            if (message.silent && message.kind != NPC_PANELS) {
                BoardService.sendDenied(player, "Por esa via solo se leen los paneles.");
                return;
            }
            // Every reply carries the admin screen, so the staff never gets bounced to the board. The box
            // inside the Custom NPCs editor asks for the same answer without a screen: its changes have to
            // land where the staff is standing, which is what {@link AdminAction#silent} says.
            if (message.silent) {
                BoardService.sendRoles(player, apply(message, player));
                return;
            }
            BoardService.openAdmin(player, apply(message, player));
        }

        private static String apply(AdminAction message, EntityPlayerMP sender) {
            String result = run(message, sender);
            // A query is not a change, so it never lands in the record; everything else does.
            if (message.kind != LOOKUP && message.kind != NPC_PANELS) {
                AuditStore.record(sender.getName(), label(message), subject(message),
                        result == null ? "sin cambio" : result);
            }
            return result;
        }

        /** What the record calls this kind of change, in the language the staff screen uses. */
        private static String label(AdminAction message) {
            switch (message.kind) {
                case RELOAD: return "recargar config";
                case CONFIG: return "ajuste";
                case PANEL_NEW: return "panel nuevo";
                case PANEL_DEL: return "panel borrado";
                case PANEL_GROUP: return "orden por aldea";
                case PANEL_SEED: return "paneles por rango";
                case PANEL_UP: case PANEL_DOWN: return "orden de paneles";
                case NAME: return "nombre de panel";
                case CATEGORY: return "aldea de panel";
                case RULE: return "regla de panel";
                case NODE_ADD: case NODE_DEL: return "permiso de panel";
                case GRANT_ADD: case GRANT_DEL: return "acceso de panel";
                case NPC_ADD: case NPC_DEL: return "npc del panel";
                case MISSION_ADD: case MISSION_DEL: case MISSION_LIST: return "lista de misiones";
                case MISSION_DIFF: return "dificultad de mision";
                case MISSION_COOLDOWN: return "enfriamiento de mision";
                case MISSION_LIMIT: return "tiempo limite de mision";
                case RANK_GROUP: case RANK_LABEL: return "rango";
                case RANK_UP: case RANK_DOWN: return "orden de rangos";
                case RANK_ADD: return "rango nuevo";
                case RANK_DEL: return "rango borrado";
                default: return "orden de panel";
            }
        }

        /** The thing that changed, as far as the action names it. */
        private static String subject(AdminAction message) {
            if (!message.panel.trim().isEmpty()) return message.panel;
            if (!message.name.trim().isEmpty()) return message.name;
            return message.rankIndex > 0 ? "mision " + message.rankIndex : "sin objetivo";
        }

        private static String run(AdminAction message, EntityPlayerMP sender) {
            RQConfig config = RebornQuests.config;
            if (message.kind == RELOAD) {
                config.load();
                RankService.clear();
                RebornQuests.refreshCommand();
                return "Configuracion recargada y rangos limpiados.";
            }
            if (message.kind == CONFIG) return config.apply(message.panel, message.name);
            if (message.kind == LOOKUP) return lookup(sender, message.panel);
            if (message.kind == NPC_PANELS) return "Paneles leidos para el editor de NPCs.";
            if (message.kind == PANEL_NEW) return config.createPanel(message.name);
            if (message.kind == PANEL_DEL) return config.deletePanel(message.panel);
            if (message.kind == PANEL_GROUP) return config.groupByCategory();
            if (message.kind == PANEL_SEED) return config.seedRankPanels();
            if (message.kind == RANK_GROUP) return config.setRankGroup(message.rankIndex, message.name);
            if (message.kind == RANK_LABEL) return config.setRankLabel(message.rankIndex, message.name);
            if (message.kind == RANK_UP) return config.moveRank(message.rankIndex, -1);
            if (message.kind == RANK_DOWN) return config.moveRank(message.rankIndex, 1);
            if (message.kind == RANK_ADD) return config.addRank(message.name, message.panel, message.rankIndex);
            if (message.kind == RANK_DEL) return config.removeRank(message.rankIndex);
            if (message.kind == PANEL_UP) return config.movePanel(message.panel, -1);
            if (message.kind == PANEL_DOWN) return config.movePanel(message.panel, 1);
            if (message.kind == CATEGORY) return config.setCategory(message.panel, message.name);
            if (message.kind == NODE_ADD) return config.addNode(message.panel, message.name);
            if (message.kind == NODE_DEL) return config.removeNode(message.panel, message.name);
            if (message.kind == GRANT_ADD) return config.grant(message.panel, message.name);
            if (message.kind == GRANT_DEL) return config.ungrant(message.panel, message.name);
            if (message.kind == NPC_ADD) return config.addNpc(message.panel, message.name);
            if (message.kind == NPC_DEL) return config.removeNpc(message.panel, message.name);
            if (message.kind == MISSION_ADD) {
                return config.addMission(message.panel, message.rankIndex, message.listRank, message.village);
            }
            if (message.kind == MISSION_DEL) {
                return config.removeMission(message.panel, message.rankIndex, message.listRank, message.village);
            }
            if (message.kind == MISSION_LIST) return config.clearRankList(message.panel, message.listRank, message.village);
            if (message.kind == MISSION_DIFF) return config.setMissionDifficulty(message.rankIndex, message.name);
            if (message.kind == MISSION_COOLDOWN) return config.setMissionCooldown(message.rankIndex, message.name);
            if (message.kind == MISSION_LIMIT) return config.setMissionLimit(message.rankIndex, message.name);
            if (!config.has(message.panel)) return "Ese panel ya no existe.";
            if (message.kind == NAME) return config.renamePanel(message.panel, message.name);
            if (message.kind == RULE) {
                if (message.rankIndex >= config.rankGroups.size()) return "Ese rango no esta en la ladder.";
                String group = message.rankIndex < 0 ? "-" : config.rankGroups.get(message.rankIndex);
                return config.setRequirement(message.panel, group, Math.max(0, message.minFinished));
            }
            return "Orden de panel desconocida.";
        }

        /** What the Estado tab asks: what LuckPerms and Custom NPCs say about one online player. */
        private static String lookup(EntityPlayerMP sender, String name) {
            String wanted = name == null ? "" : name.trim();
            if (wanted.isEmpty()) return "Escribe un jugador en la casilla de Consulta.";
            EntityPlayerMP target = sender.server.getPlayerList().getPlayerByUsername(wanted);
            if (target == null) return wanted + " no está conectado ahora mismo.";
            RankService.Rank rank = RankService.rank(target);
            BoardData data = BoardService.build(target);
            return wanted + ": rango " + rank.label() + " (" + (rank.group.isEmpty() ? "sin grupo" : rank.group)
                    + ") · " + data.finished + " completadas · " + data.active + " activas · "
                    + (BoardService.isAdmin(target) ? "mando (" + BoardService.adminRule() + ")"
                            : "sin mando (" + BoardService.adminRule() + ")")
                    + " · paneles abiertos: " + unlocked(data)
                    + " · accesos sueltos: " + RebornQuests.config.grantedPanels(target.getName());
        }

        private static String unlocked(BoardData data) {
            int out = 0;
            for (BoardData.Panel panel : data.panels) {
                if (!panel.locked) out++;
            }
            return String.valueOf(out) + " de " + data.panels.size();
        }
    }
}
