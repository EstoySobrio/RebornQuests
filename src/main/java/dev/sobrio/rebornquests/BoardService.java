package dev.sobrio.rebornquests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

/** Turns the Custom NPCs quest book plus the LuckPerms rank into the board a player sees. */
public final class BoardService {
    private BoardService() {}

    /**
     * Staff is a LuckPerms permission, never an op flag: op alone must not open the admin tools while the
     * plugin is answering. The two cases where there is nothing to ask are the staff leaving the node empty
     * on purpose and a server with no permission plugin at all — and there the op list decides, at the level
     * the staff set, plus the owner of a local world so that he can still test his pack alone.
     */
    public static boolean isAdmin(EntityPlayerMP player) {
        if (player == null) return false;
        String node = adminNode();
        if (node.isEmpty()) return RankService.isOp(player) || RankService.isLocalOwner(player);
        return RankService.has(player, node);
    }

    /** What the mod answers to the question "who is allowed to move panels here". */
    public static String adminRule() {
        String node = adminNode();
        return node.isEmpty() ? "el op " + RebornQuests.config.opWithoutBridge
                + ", porque el nodo de administracion esta vacio"
                : "el nodo " + node + " de LuckPerms";
    }

    private static String adminNode() {
        return RebornQuests.config.adminNode.trim();
    }

    /** Optional global gate; empty boardNode means any ranked player can open the board. */
    public static boolean allowedToOpen(EntityPlayerMP player) {
        String node = RebornQuests.config.boardNode.trim();
        if (node.isEmpty()) return true;
        return isAdmin(player) || RankService.has(player, node);
    }

    public static BoardData build(EntityPlayerMP player) {
        RQConfig config = RebornQuests.config;
        BoardData data = new BoardData();
        data.title = config.title;
        RankService.Rank rank = RankService.rank(player);
        data.rankIndex = rank.index;
        data.rankLabel = rank.label();
        data.rankGroup = rank.group;
        data.bridge = RankService.bridge();
        data.admin = isAdmin(player);
        data.mode = player.server.isDedicatedServer() ? "dedicado" : "mundo local";
        data.cnpc = QuestBridge.available() ? "cargado" : "ausente";
        data.error = RankService.lastError();
        data.adminNode = config.adminNode;
        data.levels = String.join(",", RQConfig.DIFFICULTIES);
        data.villages = String.join(",", config.categories());
        data.ladder = ladder(config);
        if (data.admin) {
            data.settings.addAll(config.settings());
            data.rankLists.addAll(config.panelRankMissions);
            audit(data);
        }

        if (!QuestBridge.available()) {
            data.notice = "Custom NPCs no esta cargado: el tablon no puede leer misiones.";
            return data;
        }
        if (data.bridge.equals("ninguno")) {
            String where = player.server.isDedicatedServer() ? "server sin plugins" : "mundo local";
            data.notice = config.lockWithoutBridge
                    ? "No hay plugin de permisos (" + where + "): todos los paneles quedan cerrados."
                    : "Sin plugin de permisos (" + where + "): se usa el rango " + config.rankLabel(config.rankIndex(config.noBridgeRank))
                            + (data.admin ? " y tu llevas el mando." : ".");
        }

        QuestBridge.Stats stats = new QuestBridge.Stats();
        data.village = village(player, config);
        data.rankDepth = config.rankDepth;
        // What the rank card has to say when the staff asked for a node on top of the rank, so the reason he
        // cannot accept is told to him instead of leaving the board looking empty.
        data.boardNode = allowedToOpen(player) ? "" : config.boardNode.trim();
        // The staff access lists read every online player once, not once per panel and per node.
        List<Roster> roster = data.admin ? roster(player.server) : Collections.<Roster>emptyList();
        for (String name : config.panels()) {
            // Staff see the whole panel; a player only sees the lists their rank can reach, which is the
            // rank's own list plus the ranks below it down to the configured depth.
            List<Integer> ids = data.admin ? config.missionsAll(name)
                    : config.missionsFor(name, rank.index, data.village);
            List<QuestBridge.QuestRow> rows = QuestBridge.quests(ids);
            if (rows.size() > config.maxQuestRows) rows = rows.subList(0, config.maxQuestRows);
            QuestBridge.apply(player, rows, stats);

            BoardData.Panel panel = new BoardData.Panel();
            panel.name = name;
            panel.category = config.categoryOf(name);
            RQConfig.Requirement requirement = config.requirement(name);
            panel.requiredRank = requirement.rankIndex;
            panel.minFinished = requirement.minFinished;
            panel.nodes.addAll(config.nodes(name));
            if (data.admin) {
                panel.grants.addAll(config.grants(name));
                panel.npcs.addAll(config.npcs(name));
                panel.byRank.addAll(byRank(requirement, panel.nodes, roster));
            }

            String blocked = gate(player, name, requirement, rank, stats.finished, data.admin, panel.nodes);
            panel.locked = blocked != null;
            panel.reason = blocked == null ? "" : blocked;
            for (QuestBridge.QuestRow row : rows) {
                BoardData.Row out = new BoardData.Row();
                out.id = row.id;
                out.name = row.name;
                out.text = row.text;
                out.type = row.type;
                out.state = row.state;
                out.done = row.done;
                out.total = row.total;
                out.repeatable = row.repeatable;
                out.typeLabel = QuestBridge.typeLabel(row.type);
                out.completer = row.completer;
                out.completeText = row.completeText;
                out.rewards = row.rewards;
                timers(player, config, out, row.state);
                levelGate(config, stats, out, data.admin);
                out.acceptable = !panel.locked && row.acceptable && out.cooldownLeft <= 0 && out.gateOpen();
                panel.rows.add(out);
            }
            data.panels.add(panel);
        }
        data.finished = stats.finished;
        data.active = stats.active;
        if (data.admin) catalog(config, data);
        return data;
    }

    /**
     * The difficulty gate of one mission: how many missions of another level, or harder, have to be
     * finished before this one can be taken. Staff is never held back by it, so a test accept and the
     * preview of a panel keep working while the rules are being written.
     */
    private static void levelGate(RQConfig config, QuestBridge.Stats stats, BoardData.Row out, boolean admin) {
        if (admin) return;
        RQConfig.Gate gate = config.gate(out.difficulty);
        if (gate == null) return;
        out.gateLevel = gate.counted;
        out.gateMin = gate.min;
        out.gateHave = finishedAs(config, stats, gate.counted);
    }

    /**
     * Missions Custom NPCs holds as finished at that level or harder. The ids come from the quest book and
     * the level from this mod's own config: nothing of Custom NPCs is touched to work it out.
     */
    private static int finishedAs(RQConfig config, QuestBridge.Stats stats, String level) {
        int out = 0;
        for (Integer id : stats.finishedIds) {
            if (RQConfig.levelCovers(config.mission(id.intValue()).difficulty, level)) out++;
        }
        return out;
    }

    /**
     * Fills difficulty, cooldown and time limit for one mission and, on the way, seeds the two clocks the
     * board runs on: when a running mission was taken and when a finished one was handed in. Custom NPCs
     * remembers neither, so a board view is the only moment they can be recorded.
     */
    private static void timers(EntityPlayerMP player, RQConfig config, BoardData.Row out, int state) {
        RQConfig.Mission mission = config.mission(out.id);
        out.difficulty = mission.difficulty;
        out.cooldownMin = mission.cooldownMin;
        out.limitMin = mission.limitMin;
        long taken = MissionStore.acceptedAt(player.getUniqueID(), out.id);
        if (state == BoardData.ACTIVE) {
            if (taken <= 0L && mission.limitMin > 0) {
                MissionStore.stamp(player.getUniqueID(), out.id);
                taken = MissionStore.acceptedAt(player.getUniqueID(), out.id);
            }
            out.limitLeft = MissionStore.left(taken, mission.limitMin);
            out.limitSec = MissionStore.leftSeconds(taken, mission.limitMin);
            return;
        }
        if (state != BoardData.DONE || mission.cooldownMin <= 0) return;
        long done = MissionStore.finishedAt(player.getUniqueID(), out.id);
        if (done <= taken) {
            MissionStore.stampFinish(player.getUniqueID(), out.id);
            done = MissionStore.finishedAt(player.getUniqueID(), out.id);
        }
        out.cooldownLeft = MissionStore.left(done, mission.cooldownMin);
    }

    /** Every quest of the pack, tagged with the panel that already holds it. Staff screens only. */
    private static void catalog(RQConfig config, BoardData data) {
        for (QuestBridge.QuestRef quest : QuestBridge.allQuests()) {
            BoardData.Quest out = new BoardData.Quest();
            out.id = quest.id;
            out.name = quest.name;
            out.category = quest.category;
            out.panel = config.panelOfQuest(quest.id);
            data.catalog.add(out);
        }
    }

    /** How many lines of the record the staff screen is handed. */
    private static final int AUDIT_LINES = 25;

    /**
     * The tail of the audit record for the Auditoría tab, newest last. Only the head of each detail
     * travels: the screen is a summary of who touched what, and the console command is the one that prints
     * an entry whole. The list is read-only on both sides — the record itself can only ever grow.
     */
    private static void audit(BoardData data) {
        for (String entry : AuditStore.tail(AUDIT_LINES)) {
            data.audit.add(AuditStore.when(entry) + "\t" + AuditStore.who(entry) + "\t"
                    + AuditStore.kind(entry) + "\t" + AuditStore.subject(entry) + "\t"
                    + oneLine(AuditStore.detail(entry)));
        }
    }

    /** A diff is many lines; the screen shows its first few on one. */
    private static String oneLine(String detail) {
        String flat = detail == null ? "" : detail.replace("\r", "").replace("\n", "  |  ");
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + " ...";
    }

    /**
     * The gate of one panel: the players the staff let in by name, the ladder step, how far down that step
     * is allowed to reach, the completed-mission count and every permission node the staff listed. Nothing
     * here can raise a rank, it only reads what LuckPerms says — and a name on the access list opens the
     * panel without any permission at all, which is what the staff uses to test a panel with one account.
     */
    private static String gate(EntityPlayerMP player, String panel, RQConfig.Requirement requirement,
                               RankService.Rank rank, int finished, boolean admin, List<String> nodes) {
        RQConfig config = RebornQuests.config;
        if (admin) return null;
        if (config.isGranted(panel, player.getName())) return null;
        return rankGate(player, requirement, rank, finished, config, nodes);
    }

    /**
     * The part of the gate a rank carries on its own, with neither the staff bypass nor a name on the access
     * list helping. The staff screen runs it for every player online to list who really opens a panel.
     */
    private static String rankGate(EntityPlayerMP player, RQConfig.Requirement requirement,
                                   RankService.Rank rank, int finished, RQConfig config, List<String> nodes) {
        if (requirement.needsRank()) {
            if (!rank.known()) return "Necesitas el rango " + config.rankLabel(requirement.rankIndex) + " y aun no tienes ninguno.";
            if (rank.index < requirement.rankIndex) {
                return "Requiere el rango " + config.rankLabel(requirement.rankIndex)
                        + " (" + requirement.group + "). El tuyo es " + rank.label() + ".";
            }
            int floor = config.rankFloor(rank.index);
            if (requirement.rankIndex < floor) {
                return "Ese panel queda demasiado abajo: con " + rank.label()
                        + " solo llegas hasta " + config.rankLabel(floor) + ".";
            }
        }
        if (requirement.minFinished > 0 && finished < requirement.minFinished) {
            return "Necesitas " + requirement.minFinished + " misiones completadas. Llevas " + finished + ".";
        }
        for (String node : nodes) {
            if (RankService.has(player, nodeOf(node))) continue;
            return "Te falta el permiso " + node + ".";
        }
        return null;
    }

    /** How many players the access lists are worked out for, and how many names one panel may carry. */
    private static final int MAX_ROSTER = 128;
    private static final int MAX_OPENERS = 80;

    /** One online player as the access lists see him: his rank, his missions and his staff node. */
    private static final class Roster {
        final EntityPlayerMP player;
        final RankService.Rank rank;
        final int finished;
        final boolean staff;

        Roster(EntityPlayerMP player, RankService.Rank rank, int finished, boolean staff) {
            this.player = player;
            this.rank = rank;
            this.finished = finished;
            this.staff = staff;
        }
    }

    /** The players online, each read once for the whole staff screen instead of once per panel. */
    private static List<Roster> roster(net.minecraft.server.MinecraftServer server) {
        List<Roster> out = new ArrayList<Roster>();
        for (EntityPlayerMP other : server.getPlayerList().getPlayers()) {
            if (other == null || out.size() >= MAX_ROSTER) continue;
            out.add(new Roster(other, RankService.rank(other), QuestBridge.finishedCount(other), isAdmin(other)));
        }
        return out;
    }

    /**
     * Who opens one panel on their own, as "<name> · <reason>" lines: the same read the staff screen lists,
     * so the command line and the panel never disagree. Only players online can be asked; a name that is
     * offline only ever shows up in the manual list.
     */
    public static List<String> openers(String panel, net.minecraft.server.MinecraftServer server) {
        RQConfig config = RebornQuests.config;
        return byRank(config.requirement(panel), config.nodes(panel), roster(server));
    }

    /**
     * Who opens a panel through his own rank: his ladder step, the missions he has handed in and the nodes
     * the panel demands, read exactly the way the gate reads them, with each name followed by the reason.
     * A staff member without a rank behind the panel is listed as staff, because he does open it. Offline
     * players are never listed: LuckPerms only answers for somebody this server still knows, and the manual
     * list is where a name that is offline lives.
     */
    private static List<String> byRank(RQConfig.Requirement requirement, List<String> nodes,
                                       List<Roster> roster) {
        List<String> out = new ArrayList<String>();
        RQConfig config = RebornQuests.config;
        for (Roster entry : roster) {
            if (out.size() >= MAX_OPENERS) break;
            if (rankGate(entry.player, requirement, entry.rank, entry.finished, config, nodes) == null) {
                out.add(entry.player.getName() + " · " + entry.rank.label());
            } else if (entry.staff) {
                out.add(entry.player.getName() + " · staff");
            }
        }
        return out;
    }

    /** A ladder group written as a permission means membership of that group. */
    private static String nodeOf(String node) {
        int index = RebornQuests.config.rankIndex(node);
        return index < 0 ? node : "group." + RebornQuests.config.rankGroups.get(index).toLowerCase(Locale.ROOT);
    }

    /**
     * The village this player belongs to, read from LuckPerms as {@code aldea.<aldea>} on one of the
     * configured villages. It is what decides which per-rank mission list a panel shows, so the staff can
     * put the same rank of two villages on different jobs.
     */
    private static String village(EntityPlayerMP player, RQConfig config) {
        String prefix = config.villageNode == null ? "" : config.villageNode.trim();
        if (!prefix.isEmpty()) {
            for (String village : config.categories()) {
                if (RankService.has(player, prefix + village.toLowerCase(Locale.ROOT))) return village;
            }
        }
        String fallback = config.villageDefault == null ? "" : config.villageDefault.trim();
        return fallback.isEmpty() || fallback.equals("-") ? "" : fallback;
    }

    public static void open(EntityPlayerMP player) {
        open(player, "", "");
    }

    /** The admin panel edits come back as a fresh board, so the staff sees the change land. */
    public static void open(EntityPlayerMP player, String notice) {
        open(player, notice, "");
    }

    /**
     * Opens the board for a player. {@code focus} is the panel the board starts on, which is how an NPC
     * whose role carries a mission of one panel jumps straight to it.
     */
    public static void open(EntityPlayerMP player, String notice, String focus) {
        open(player, notice, focus, new ArrayList<String>());
    }

    /**
     * The same board, cut down to the panels one NPC carries. {@code group} is what the mod's own role says
     * that NPC opens, and the board then lists those panels and their missions together. Empty is the whole
     * board, which is what the quest-book key and the command open.
     */
    public static void open(EntityPlayerMP player, String notice, String focus, List<String> group) {
        BoardData data = build(player);
        data.focus = focus == null ? "" : focus;
        if (group != null) {
            for (String name : group) {
                if (name != null && !name.trim().isEmpty() && data.panel(name) != null) data.group.add(name);
            }
        }
        if (data.group.isEmpty() && !data.focus.isEmpty() && data.panel(data.focus) != null) {
            // A board asked for one named panel is a counter, not the book of what he already holds.
            data.group.add(data.focus);
        }
        // The book also carries the missions that no board of the mod holds; an NPC counter stays with its own panels.
        if (data.group.isEmpty()) {
            BoardData.Panel spare = outside(player, RebornQuests.config);
            if (spare != null) data.panels.add(spare);
        }
        if (notice != null && !notice.trim().isEmpty()) data.notice = notice;
        RebornQuests.NET.sendTo(new MessageBoard(data), player);
        pushTimed(player);
    }

    /** How the extra list of missions outside every panel is named on the board. */
    public static final String OUTSIDE_PANEL = "Sin panel";

    /**
     * The quests this player holds that no panel of the mod carries, as one extra list: Custom NPCs hands a
     * quest out from any dialogue, and a book that hid it would look broken even though the mod never
     * offered it. Nothing there can be accepted, because the mission is not this mod's to give. Null when
     * he holds no quest outside the boards.
     */
    private static BoardData.Panel outside(EntityPlayerMP player, RQConfig config) {
        if (!QuestBridge.available()) return null;
        List<Integer> ids = new ArrayList<Integer>();
        for (Integer held : QuestBridge.heldIds(player)) {
            if (held == null || config.panelOfQuest(held.intValue()) != null) continue;
            ids.add(held);
        }
        if (ids.isEmpty()) return null;
        List<QuestBridge.QuestRow> rows = QuestBridge.quests(ids);
        if (rows.size() > config.maxQuestRows) rows = rows.subList(0, config.maxQuestRows);
        BoardData.Panel panel = new BoardData.Panel();
        panel.name = OUTSIDE_PANEL;
        QuestBridge.apply(player, rows, new QuestBridge.Stats());
        for (QuestBridge.QuestRow row : rows) {
            BoardData.Row out = new BoardData.Row();
            out.id = row.id;
            out.name = row.name;
            out.text = row.text;
            out.type = row.type;
            out.state = row.state;
            out.done = row.done;
            out.total = row.total;
            out.repeatable = row.repeatable;
            out.typeLabel = QuestBridge.typeLabel(row.type);
            out.completer = row.completer;
            out.completeText = row.completeText;
            out.rewards = row.rewards;
            timers(player, config, out, row.state);
            out.acceptable = false;
            panel.rows.add(out);
        }
        return panel;
    }

    /** Same packet, but the client opens the staff screen: /rbmisiones pelado is the only way in. */
    public static void openAdmin(EntityPlayerMP player, String notice) {
        BoardData data = adminData(player, notice);
        if (data == null) return;
        RebornQuests.NET.sendTo(new MessageBoard(data), player);
        // A config change comes back through here, so the clock moves to its new corner on the spot.
        pushTimed(player);
    }

    /**
     * The panel lists with no screen attached. The box this mod adds to the editor of Custom NPCs reads them
     * from where it sits: the staff is inside another mod's window editing one NPC, and pulling him out of it
     * to answer one click would lose the NPC in his hands.
     */
    public static void sendRoles(EntityPlayerMP player, String notice) {
        BoardData data = rolesData(player);
        if (notice != null && !notice.trim().isEmpty()) data.notice = notice;
        RebornQuests.NET.sendTo(new MessageBoard(data), player);
    }

    /**
     * The panels, their village and the NPCs written on each of them, and nothing else. No quest of Custom
     * NPCs is read here, no audit and no roster: the staff asked which boards an NPC opens, which is a
     * question this mod's own config answers. The full board answers it too, but it walks every mission of
     * the pack to do it, and that walk is what made the list of the editor arrive after the click.
     */
    public static BoardData rolesData(EntityPlayerMP player) {
        RQConfig config = RebornQuests.config;
        BoardData data = new BoardData();
        data.title = config.title;
        data.screen = BoardData.SCREEN_ADMIN;
        data.silent = true;
        data.admin = isAdmin(player);
        data.villages = String.join(",", config.categories());
        data.ladder = ladder(config);
        for (String name : config.panels()) {
            BoardData.Panel panel = new BoardData.Panel();
            panel.name = name;
            panel.category = config.categoryOf(name);
            panel.npcs.addAll(config.npcs(name));
            data.panels.add(panel);
        }
        return data;
    }

    /**
     * The answer to a silent question this player may not have: no panel of the pack is named in it, only the
     * reason. The editor box of a player without the node reads it and says the same thing the chat would.
     */
    public static void sendDenied(EntityPlayerMP player, String notice) {
        BoardData data = new BoardData();
        data.screen = BoardData.SCREEN_ADMIN;
        data.silent = true;
        data.admin = false;
        data.notice = notice;
        RebornQuests.NET.sendTo(new MessageBoard(data), player);
    }

    /** The staff board, or null when the player turned out not to hold the node after all. */
    private static BoardData adminData(EntityPlayerMP player, String notice) {
        BoardData data = build(player);
        data.screen = BoardData.SCREEN_ADMIN;
        if (!data.admin) {
            player.sendMessage(new TextComponentString("§cTe falta " + adminRule()
                    + " para abrir el panel de administración."));
            return null;
        }
        if (notice != null && !notice.trim().isEmpty()) data.notice = notice;
        return data;
    }

    /** The ladder as one line, built here so the client never guesses it from its own config. */
    private static String ladder(RQConfig config) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < config.rankGroups.size(); i++) {
            if (i > 0) out.append("  <  ");
            out.append(config.rankLabel(i)).append(" (").append(config.rankGroups.get(i)).append(')');
        }
        return out.toString();
    }

    /**
     * Takes one mission for a player from his board: the same rules the board showed him, read again against
     * a board built right now, and then the start of Custom NPCs.
     */
    public static String accept(EntityPlayerMP player, int questId) {
        String error = checkAccept(player, questId, true);
        if (error != null) return error;
        // Custom NPCs answers for every route a quest can be taken by, so the one this mod just checked has
        // to be recognised on its way back or it would be measured twice, and refused for the board node.
        QuestGuards.throughBoard(player, questId);
        error = QuestBridge.start(player, questId, true);
        if (error == null) MissionStore.stamp(player.getUniqueID(), questId);
        return error;
    }

    /**
     * Whether this player may take this mission right now: the panel it sits on, its rank, its difficulty
     * gate and its cooling-off, all of them read from a freshly built board so a hacked client cannot unlock
     * anything. {@code board} says whether the ask came through the tablón, which is the only route that
     * carries the global board permission. Null means nothing stops him.
     */
    public static String checkAccept(EntityPlayerMP player, int questId, boolean board) {
        if (board && !allowedToOpen(player)) return "No tienes acceso al tablón de misiones.";
        BoardData fresh = build(player);
        BoardData.Panel panel = fresh.panelOfQuest(questId);
        if (panel == null) return "Esa mision ya no esta en el tablón.";
        if (panel.locked) return panel.reason;
        for (BoardData.Row row : panel.rows) {
            if (row.id != questId) continue;
            if (row.cooldownLeft > 0) {
                return "Esa mision vuelve a estar disponible en " + row.cooldownLeft + " min.";
            }
            if (!row.gateOpen()) return row.gateMissing();
            if (!row.acceptable) return "Esa mision no se puede aceptar ahora mismo.";
            return null;
        }
        return "Esa mision ya no esta en el tablón.";
    }

    /**
     * Drops every mission of one player whose time limit ran out and returns how many went away. The
     * clock started when the mission was accepted, so a mission taken outside the board is measured from
     * the first time this walk sees it running.
     *
     * <p>Running out stamps the mission as finished on purpose: a mission whose time died goes into its
     * cooling-off like a handed-in one, instead of being re-accepted on the spot.
     */
    public static int expire(EntityPlayerMP player) {
        RQConfig config = RebornQuests.config;
        int expired = 0;
        for (RQConfig.Mission mission : config.timedMissions()) {
            if (mission.limitMin <= 0) continue;
            if (!QuestBridge.isActive(player, mission.id)) continue;
            long at = MissionStore.acceptedAt(player.getUniqueID(), mission.id);
            if (at == 0L) {
                MissionStore.stamp(player.getUniqueID(), mission.id);
                continue;
            }
            if (MissionStore.left(at, mission.limitMin) > 0) continue;
            if (QuestBridge.stop(player, mission.id) != null) continue;
            MissionStore.stampFinish(player.getUniqueID(), mission.id);
            expired++;
            player.sendMessage(new TextComponentString("§cSe acabó el tiempo de la mision "
                    + missionName(mission.id) + " (" + mission.limitMin + " min)." + back(mission)));
        }
        return expired;
    }

    /** The cooling-off an expired mission just started, worded for the chat line. */
    private static String back(RQConfig.Mission mission) {
        if (mission.cooldownMin <= 0) return " Vuelve a aceptarla cuando pueda.";
        return " Empieza su enfriamiento de " + mission.cooldownMin + " min.";
    }

    /**
     * Sends the on-screen clock: every mission the player has running with a time limit, with its progress
     * and the seconds left. With the setting on "apagado" the list goes empty, which is how the client
     * hides the box. The mode travels too, because it is only the position the player who has never moved
     * the clock himself starts with — his own placement, if he made one, is kept on his machine.
     */
    public static void pushTimed(EntityPlayerMP player) {
        RQConfig config = RebornQuests.config;
        int mode = config.hudModeId();
        List<MessageTime.Entry> out = new ArrayList<MessageTime.Entry>();
        if (mode > 0 && QuestBridge.available()) {
            List<Integer> ids = new ArrayList<Integer>();
            for (RQConfig.Mission mission : config.timedMissions()) {
                if (mission.limitMin > 0 && QuestBridge.isActive(player, mission.id)) ids.add(mission.id);
            }
            for (QuestBridge.QuestRow row : QuestBridge.rows(player, ids)) {
                RQConfig.Mission mission = config.mission(row.id);
                long at = MissionStore.acceptedAt(player.getUniqueID(), row.id);
                if (at <= 0L) {
                    // A mission taken from an NPC dialogue has no clock yet; this push is the first sight
                    // of it running, so the countdown starts here rather than twenty seconds later.
                    MissionStore.stamp(player.getUniqueID(), row.id);
                    at = MissionStore.acceptedAt(player.getUniqueID(), row.id);
                }
                int seconds = MissionStore.leftSeconds(at, mission.limitMin);
                if (seconds <= 0) continue;
                out.add(new MessageTime.Entry(row.id, row.name, mission.difficulty, row.done, row.total, seconds));
            }
        }
        RebornQuests.NET.sendTo(new MessageTime(mode, config.replaceQuestLog, out), player);
    }

    private static String missionName(int questId) {
        QuestBridge.QuestRow row = QuestBridge.quest(questId);
        return row == null ? "#" + questId : row.name;
    }

    public static void announce(EntityPlayerMP player) {
        RankService.Rank rank = RankService.rank(player);
        BoardData data = build(player);
        player.sendMessage(new TextComponentString("§e" + RebornQuests.config.title
                + ": rango " + rank.label() + ", " + data.finished + " misiones completadas."));
    }
}
