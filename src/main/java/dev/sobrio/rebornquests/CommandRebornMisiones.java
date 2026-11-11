package dev.sobrio.rebornquests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/**
 * Single entry point for everything the mod does by command: /rebornmisiones <subcomando>. The admin
 * screen covers the same ground with buttons, so this is what the console and scripts use.
 */
public class CommandRebornMisiones extends CommandBase {
    private static final List<String> SUBS = Arrays.asList(
            "ayuda", "abrir", "rango", "estado", "recargar", "panel", "mision", "log", "acceso");

    /**
     * The two short names Vince asked for. They are always there, and the old "tablon" alias never is,
     * so the command surface does not depend on a config file that may still list the previous default.
     */
    private static final List<String> FIXED = Arrays.asList("rbmisiones", "rbm");
    private static final String RETIRED = "tablon";

    @Override
    public String getName() {
        return RebornQuests.config.commandName;
    }

    @Override
    public List<String> getAliases() {
        List<String> out = new ArrayList<String>();
        for (String alias : RebornQuests.config.commandAliases.split(",")) {
            String trimmed = alias.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(getName()) || trimmed.equalsIgnoreCase(RETIRED)) continue;
            out.add(trimmed);
        }
        for (String alias : FIXED) {
            if (!alias.equalsIgnoreCase(getName()) && !out.contains(alias)) out.add(alias);
        }
        return out;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + getName() + " <sin argumentos=panel admin|abrir|rango|estado|recargar|panel|mision|log|acceso>";
    }

    /**
     * Being op is not what opens any of this: a player needs the LuckPerms node the config calls the
     * administration node, which is the same rule the panel and the buttons follow. The console, RCON and
     * command blocks are not players, so they answer to the server itself.
     */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) return BoardService.isAdmin((EntityPlayerMP) sender);
        return true;
    }

    /**
     * Zero on purpose: the level would be checked against the op flag before this class gets to say who is
     * staff, and a member of the team without op would not even see the command.
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            adminPanel(sender);
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("abrir") || sub.equals("tablon")) {
            open(server, sender, args);
            return;
        }
        if (sub.equals("rango")) {
            EntityPlayerMP target = playerOf(server, sender, args, 1);
            RankService.Rank rank = RankService.rank(target);
            BoardData data = BoardService.build(target);
            tell(sender, "Rango: §e" + rank.label() + "§r (grupo " + (rank.group.isEmpty() ? "-" : rank.group)
                    + ") · misiones completadas: §e" + data.finished + "§r · activas: " + data.active
                    + " · paneles abiertos: " + unlocked(data) + " · puente: " + RankService.bridge());
            return;
        }
        if (sub.equals("estado")) {
            requireAdmin(sender);
            tell(sender, "Permisos: " + RankService.bridge() + " · Custom NPCs: " + QuestBridge.available());
            tell(sender, "Bukkit: " + RankService.hasBukkit() + " · LuckPerms: " + RankService.live()
                    + " · mundo local: " + !server.isDedicatedServer());
            tell(sender, "Ladder: " + String.join(" < ", RebornQuests.config.rankGroups));
            tell(sender, "Aldeas: " + RebornQuests.config.categories());
            tell(sender, "Comando: /" + getName() + " · alias: " + RebornQuests.config.commandAliases);
            tell(sender, "Puente activo: " + RankService.live() + " · error: " + RankService.lastError());
            return;
        }
        if (sub.equals("recargar")) {
            requireAdmin(sender);
            RebornQuests.config.load();
            RankService.clear();
            RebornQuests.refreshCommand();
            AuditStore.record(sender.getName(), "comando", "recargar", "configuracion recargada desde la consola");
            tell(sender, "Configuracion recargada, rangos limpiados y comando registrado de nuevo.");
            return;
        }
        if (sub.equals("panel")) {
            panels(sender, args);
            auditWrite(sender, args);
            return;
        }
        if (sub.equals("mision")) {
            missions(sender, args);
            auditWrite(sender, args);
            return;
        }
        if (sub.equals("log") || sub.equals("registro")) {
            log(sender, args);
            return;
        }
        if (sub.equals("acceso") || sub.equals("accesos")) {
            access(sender, args);
            auditWrite(sender, args);
            return;
        }
        help(sender);
    }

    private void open(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP target = playerOf(server, sender, args, 1);
        if (!BoardService.allowedToOpen(target) && !BoardService.isAdmin(target)) {
            tell(sender, "§c" + target.getName() + " no tiene acceso al tablón.");
            return;
        }
        String focus = args.length > 2 ? args[2] : "";
        if (!focus.isEmpty() && !RebornQuests.config.has(focus)) {
            tell(sender, "§cEse panel no existe. Lista con /" + getName() + " panel listar.");
            return;
        }
        BoardService.open(target, "", focus);
        tell(sender, focus.isEmpty() ? "Tablón abierto para " + target.getName() + "."
                : "Tablón abierto para " + target.getName() + " en el panel " + focus + ".");
    }

    // ---------------------------------------------------------------------------------------------
    // panels
    // ---------------------------------------------------------------------------------------------

    private void panels(ICommandSender sender, String[] args) throws CommandException {
        requireAdmin(sender);
        RQConfig config = RebornQuests.config;
        String action = args.length > 1 ? args[1].toLowerCase() : "listar";
        if (action.equals("listar")) {
            if (config.panels.isEmpty()) {
                tell(sender, "Todavia no hay paneles. Crea uno con /" + getName() + " panel nuevo <nombre>.");
                return;
            }
            int slot = 1;
            for (RQConfig.PanelDef def : config.panels) {
                RQConfig.Requirement req = config.requirement(def.name);
                tell(sender, "§e" + slot++ + ". " + def.name + "§r · aldea: "
                        + (def.category.isEmpty() ? "-" : def.category) + " · rango: "
                        + (req.rankIndex >= 0 ? config.rankLabel(req.rankIndex) : "sin exigir")
                        + " · minimas: " + req.minFinished
                        + " · misiones: " + config.missions(def.name).size()
                        + " · permisos: " + (config.nodes(def.name).isEmpty() ? "-" : String.join(",", config.nodes(def.name)))
                        + " · accesos sueltos: " + config.grants(def.name).size()
                        + " · npcs: " + config.npcs(def.name).size());
            }
            return;
        }
        if (action.equals("nuevo")) {
            need(args, 3, "panel nuevo <nombre>");
            tell(sender, config.createPanel(joinFrom(args, 2)));
            return;
        }
        if (action.equals("borrar")) {
            need(args, 3, "panel borrar <nombre>");
            tell(sender, config.deletePanel(args[2]));
            return;
        }
        if (action.equals("renombrar")) {
            need(args, 4, "panel renombrar <nombre> <nombre nuevo>");
            tell(sender, config.renamePanel(args[2], joinFrom(args, 3)));
            return;
        }
        if (action.equals("aldea")) {
            need(args, 4, "panel aldea <nombre> <aldea|->");
            tell(sender, config.setCategory(args[2], joinFrom(args, 3)));
            return;
        }
        if (action.equals("requisitos")) {
            need(args, 5, "panel requisitos <nombre> <grupo|-> <minimas>");
            String group = args[3];
            if (!group.equals("-") && config.rankIndex(group) < 0) {
                tell(sender, "§cEse grupo no esta en la ladder: " + config.rankGroups);
                return;
            }
            tell(sender, config.setRequirement(args[2], group, toInt(args[4], 0)));
            return;
        }
        if (action.equals("subir") || action.equals("bajar")) {
            need(args, 3, "panel " + action + " <nombre>");
            tell(sender, config.movePanel(args[2], action.equals("subir") ? -1 : 1));
            return;
        }
        if (action.equals("orden")) {
            need(args, 4, "panel orden <nombre> <puesto>");
            tell(sender, config.movePanelTo(args[2], toInt(args[3], 1) - 1));
            return;
        }
        if (action.equals("agrupar")) {
            tell(sender, config.groupByCategory());
            return;
        }
        if (action.equals("permiso")) {
            need(args, 5, "panel permiso <nombre> <add|del> <permiso>");
            tell(sender, args[3].equalsIgnoreCase("add") ? config.addNode(args[2], joinFrom(args, 4))
                    : config.removeNode(args[2], joinFrom(args, 4)));
            return;
        }
        if (action.equals("mision")) {
            need(args, 5, "panel mision <nombre> <add|del> <id de mision>");
            int questId = toInt(args[4], -1);
            if (questId <= 0) {
                tell(sender, "§cEl id de mision tiene que ser un numero mayor que 0.");
                return;
            }
            tell(sender, args[3].equalsIgnoreCase("add") ? config.addMission(args[2], questId)
                    : config.removeMission(args[2], questId));
            return;
        }
        // Which NPCs open this panel is the mod's own role, so the list lives here and in the editor's box.
        if (action.equals("npc")) {
            need(args, 5, "panel npc <nombre> <add|del> <nombre del NPC|#uuid>");
            tell(sender, args[3].equalsIgnoreCase("add") ? config.addNpc(args[2], joinFrom(args, 4))
                    : config.removeNpc(args[2], joinFrom(args, 4)));
            return;
        }
        if (action.equals("npcs")) {
            need(args, 3, "panel npcs <nombre>");
            List<String> npcs = config.npcs(args[2]);
            if (npcs.isEmpty()) {
                tell(sender, "El panel " + args[2] + " no lista ningun NPC. Da uno con /" + getName()
                        + " panel npc " + args[2] + " add <nombre>.");
                return;
            }
            tell(sender, "§e" + args[2] + "§r lo abren: " + String.join(", ", npcs));
            return;
        }
        // One panel per rank of every village, which is how the pack ships. Names that already exist are
        // kept, so running it on a hand-made board only fills in the ranks that were missing.
        if (action.equals("sembrar")) {
            tell(sender, config.seedRankPanels());
            return;
        }
        throw new WrongUsageException("/" + getName()
                + " panel <listar|nuevo|borrar|renombrar|aldea|requisitos|subir|bajar|orden|agrupar|sembrar"
                + "|permiso|mision|npc|npcs>");
    }

    // ---------------------------------------------------------------------------------------------
    // missions
    // ---------------------------------------------------------------------------------------------

    private void missions(ICommandSender sender, String[] args) throws CommandException {
        requireAdmin(sender);
        RQConfig config = RebornQuests.config;
        String action = args.length > 1 ? args[1].toLowerCase() : "listar";
        if (action.equals("listar")) {
            List<QuestBridge.QuestRef> all = QuestBridge.allQuests();
            if (all.isEmpty()) {
                tell(sender, "Custom NPCs no tiene misiones todavia.");
                return;
            }
            for (QuestBridge.QuestRef quest : all) {
                RQConfig.Mission mission = config.mission(quest.id);
                tell(sender, "§e#" + quest.id + " " + quest.name + "§r (" + quest.category + ") · aldea del panel: "
                        + orNone(config.panelOfQuest(quest.id)) + " · dificultad " + mission.difficulty
                        + " · enfriamiento " + mission.cooldownMin + " min · limite " + mission.limitMin + " min");
            }
            return;
        }
        need(args, 4, "mision <dificultad|cooldown|limite> <id> <valor>");
        int questId = toInt(args[2], -1);
        if (questId <= 0) {
            tell(sender, "§cEl id de mision tiene que ser un numero mayor que 0.");
            return;
        }
        String value = joinFrom(args, 3);
        if (levelAction(action)) {
            tell(sender, config.setMissionDifficulty(questId, value));
            return;
        }
        if (action.equals("cooldown") || action.equals("enfriamiento")) {
            tell(sender, config.setMissionCooldown(questId, value));
            return;
        }
        if (action.equals("limite") || action.equals("tiempo")) {
            tell(sender, config.setMissionLimit(questId, value));
            return;
        }
        throw new WrongUsageException("/" + getName() + " mision <listar|dificultad|cooldown|limite>");
    }

    /** How many changes one page of the record holds. */
    private static final int LOG_PAGE = 10;
    /** Long diffs are cut in the chat listing; the record itself keeps them whole. */
    private static final int LOG_DETAIL_LINES = 4;

    /**
     * The record, page by page, from the console and nowhere else. A player is turned away on purpose: the
     * person being audited must not be the one reading it from inside the game, and the same reason leaves
     * the file with no delete, clear or rotate path anywhere in the mod. This only ever reads.
     */
    private void log(ICommandSender sender, String[] args) throws CommandException {
        if (sender instanceof EntityPlayerMP) {
            throw new CommandException("El registro de cambios solo se lista desde la consola del server.");
        }
        int pages = AuditStore.pages(LOG_PAGE);
        int wanted = args.length > 1 ? toInt(args[1], 1) - 1 : 0;
        int number = Math.max(0, Math.min(pages - 1, wanted));
        List<String> entries = AuditStore.page(number, LOG_PAGE);
        tell(sender, "§eRegistro de cambios§r · pagina " + (number + 1) + " de " + pages + " · "
                + AuditStore.entries().size() + " entradas · de la mas nueva a la mas vieja");
        if (entries.isEmpty()) {
            tell(sender, "Todavia no hay nada anotado.");
            return;
        }
        for (String entry : entries) {
            tell(sender, "§e" + AuditStore.when(entry) + " §7· " + AuditStore.who(entry) + " · "
                    + AuditStore.kind(entry) + " · §f" + AuditStore.subject(entry));
            String[] lines = AuditStore.detail(entry).split("\n");
            for (int i = 0; i < lines.length && i < LOG_DETAIL_LINES; i++) {
                tell(sender, "§8  " + crop(lines[i]));
            }
            if (lines.length > LOG_DETAIL_LINES) {
                tell(sender, "§8  ... " + (lines.length - LOG_DETAIL_LINES) + " lineas mas en el registro.");
            }
        }
        int next = number + 2 > pages ? 1 : number + 2;
        tell(sender, "§7Otra pagina: §f" + getName() + " log " + next + "§7 de " + pages + ".");
    }

    private static String crop(String line) {
        String flat = line.replace("\r", "");
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + " ...";
    }

    /**
     * Which players open which panel without the rank or the permissions behind it. Nothing here writes to
     * LuckPerms: the names live in this mod's own file, so a grant is not a promotion and taking one away
     * is not a demotion. The player comes before the panel because panel names carry spaces.
     */
    private void access(ICommandSender sender, String[] args) throws CommandException {
        requireAdmin(sender);
        RQConfig config = RebornQuests.config;
        String action = args.length > 1 ? args[1].toLowerCase() : "listar";
        if (action.equals("panel") || action.equals("quienes")) {
            need(args, 3, "acceso panel <panel>");
            accessOfPanel(sender, config, joinFrom(args, 2));
            return;
        }
        if (action.equals("listar")) {
            if (args.length > 2) {
                String who = joinFrom(args, 2);
                List<String> open = config.grantedPanels(who);
                tell(sender, who + ": " + (open.isEmpty() ? "ningun acceso suelto" : String.join(", ", open)));
                return;
            }
            int with = 0;
            for (RQConfig.PanelDef def : config.panels) {
                List<String> names = config.grants(def.name);
                if (names.isEmpty()) continue;
                with++;
                tell(sender, "§e" + def.name + "§r · " + String.join(", ", names));
            }
            if (with == 0) {
                tell(sender, "Ningun panel tiene accesos sueltos. Da uno con /" + getName()
                        + " acceso dar <jugador> <panel>.");
            }
            tell(sender, "§7De un panel entero, con los que lo abren por su rango: §f" + getName()
                    + " acceso panel <panel>§7.");
            return;
        }
        need(args, 4, "acceso " + action + " <jugador> <panel>");
        String who = args[2];
        String panel = joinFrom(args, 3);
        if (action.equals("dar") || action.equals("add")) {
            tell(sender, config.grant(panel, who));
            return;
        }
        if (action.equals("quitar") || action.equals("del")) {
            tell(sender, config.ungrant(panel, who));
            return;
        }
        throw new WrongUsageException("/" + getName() + " acceso <listar|panel|dar|quitar> [jugador] [panel]");
    }

    /**
     * One panel and both ways into it: the players online whose own rank, missions and permission nodes open
     * it, and the names the staff added by hand. A hand-added name that also qualifies on its own is said
     * twice on purpose, because that is exactly the difference the staff is asking about.
     */
    private void accessOfPanel(ICommandSender sender, RQConfig config, String wanted) throws CommandException {
        RQConfig.PanelDef def = config.panel(wanted);
        if (def == null) {
            tell(sender, "Ese panel no existe. Escribe su nombre exacto o usa /" + getName() + " panel listar.");
            return;
        }
        List<String> byRank = BoardService.openers(def.name, sender.getServer());
        List<String> manual = config.grants(def.name);
        List<String> nodes = config.nodes(def.name);
        RQConfig.Requirement requirement = config.requirement(def.name);
        tell(sender, "§7Lo definido para §e" + def.name + "§7: §f"
                + (requirement.needsRank() ? "rango " + config.rankLabel(requirement.rankIndex)
                        + " (" + requirement.group + ")" : "cualquier rango")
                + " §7· misiones completadas: §f" + requirement.minFinished
                + " §7· permisos: §f" + (nodes.isEmpty() ? "ninguno" : String.join(", ", nodes)));
        tell(sender, "§e" + def.name + "§r · lo abren por su rango/permisos " + byRank.size()
                + " conectado(s) · añadido a mano " + manual.size() + " nombre(s)");
        if (byRank.isEmpty()) {
            tell(sender, "§7  Por rango o permisos no lo abre nadie conectado ahora mismo.");
        }
        for (String entry : byRank) {
            tell(sender, "§8  por rango §f" + entry);
        }
        if (manual.isEmpty()) {
            tell(sender, "§7  Nadie añadido a mano. Da uno con /" + getName() + " acceso dar <jugador> "
                    + def.name + ".");
        }
        for (String name : manual) {
            String offline = sender.getServer().getPlayerList().getPlayerByUsername(name) == null
                    ? " §7(desconectado)" : "";
            tell(sender, "§8  a mano §f" + name + offline
                    + (reaches(byRank, name) ? " §7(y tambien por su rango)" : ""));
        }
    }

    /** True when the by-rank lines already carry that name; those lines read "<name> · <reason>". */
    private static boolean reaches(List<String> byRank, String name) {
        for (String entry : byRank) {
            int space = entry.indexOf(' ');
            if (entry.substring(0, space < 0 ? entry.length() : space).equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void help(ICommandSender sender) {
        tell(sender, "§e/" + getName() + "§r - panel de administración. Lo abre " + BoardService.adminRule() + ".");
        tell(sender, "§e/" + getName() + " abrir [jugador] [panel]§r - abre el tablón, y en el panel que digas.");
        tell(sender, "§e/" + getName() + " rango [jugador]§r - rango leido de LuckPerms.");
        tell(sender, "§e/" + getName() + " panel listar§r - paneles, aldeas y requisitos.");
        tell(sender, "§e/" + getName() + " panel nuevo|borrar|renombrar <nombre> [nuevo]§r - alta y baja de paneles.");
        tell(sender, "§e/" + getName() + " panel aldea <nombre> <aldea|->§r - a qué aldea pertenece el panel.");
        tell(sender, "§e/" + getName() + " panel requisitos <nombre> <grupo|-> <minimas>§r - gate de un panel.");
        tell(sender, "§e/" + getName() + " panel subir|bajar|orden|agrupar§r - orden de los paneles en el tablón.");
        tell(sender, "§e/" + getName() + " panel sembrar§r - un panel por rango de cada aldea, sin tocar los que ya existen.");
        tell(sender, "§e/" + getName() + " panel permiso <nombre> <add|del> <permiso>§r - permisos de LuckPerms exigidos.");
        tell(sender, "§e/" + getName() + " panel mision <nombre> <add|del> <id>§r - misiones dentro del panel.");
        tell(sender, "§e/" + getName() + " panel npc <nombre> <add|del> <NPC|#uuid>§r - que ese NPC abra el panel.");
        tell(sender, "§e/" + getName() + " panel npcs <nombre>§r - los NPCs que el panel lista.");
        tell(sender, "§e/" + getName() + " mision listar§r - todas las misiones con su dificultad y sus tiempos.");
        tell(sender, "§e/" + getName() + " mision dificultad <id> <D|C|B|A|S>§r - el nivel: Mision D a Mision S.");
        tell(sender, "§e/" + getName() + " mision cooldown|limite <id> <minutos|no>§r - enfriamiento y tiempo limite.");
        tell(sender, "§e/" + getName() + " estado§r / recargar§r - puente y configuración.");
        tell(sender, "§e/" + getName() + " log [pagina]§r - el registro de cambios, en paginas y solo desde la consola.");
        tell(sender, "§e/" + getName() + " acceso listar [jugador]§r - quien abre un panel sin rango ni permisos.");
        tell(sender, "§e/" + getName() + " acceso panel <panel>§r - quien lo abre por su rango y quien esta anadido a mano.");
        tell(sender, "§e/" + getName() + " acceso dar|quitar <jugador> <panel>§r - acceso suelto, sin tocar LuckPerms.");
        tell(sender, "§7Nada de esto necesita reiniciar el server. Los jugadores abren el tablón con el NPC.");
    }

    /** The bare command is the only door to the staff screen, and it is a screen, so it needs a player. */
    private void adminPanel(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            tell(sender, "El panel de administración es una pantalla: ábralo desde el juego con /" + getName() + ".");
            return;
        }
        requireAdmin(sender);
        BoardService.openAdmin((EntityPlayerMP) sender, "");
    }

    private static String unlocked(BoardData data) {
        int out = 0;
        for (BoardData.Panel panel : data.panels) {
            if (!panel.locked) out++;
        }
        return out + " de " + data.panels.size();
    }

    private static String orNone(String value) {
        return value == null || value.trim().isEmpty() ? "ninguna" : value;
    }

    /**
     * The two names the staff can type for the tier of a mission. "rango" is deliberately not one of
     * them: a rank is a step of the ninja ladder, D to S is only how hard a mission is.
     */
    private static boolean levelAction(String value) {
        return value.equalsIgnoreCase("dificultad") || value.equalsIgnoreCase("nivel");
    }

    private static void need(String[] args, int length, String usage) throws WrongUsageException {
        if (args.length < length) throw new WrongUsageException(usage);
    }

    /** Names carry spaces, so everything from one argument on is one value. */
    private static String joinFrom(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static EntityPlayerMP playerOf(MinecraftServer server, ICommandSender sender, String[] args, int index)
            throws CommandException {
        if (args.length > index) return getPlayer(server, sender, args[index]);
        if (sender instanceof EntityPlayerMP) return (EntityPlayerMP) sender;
        throw new CommandException("Desde la consola hay que indicar el nombre del jugador.");
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void requireAdmin(ICommandSender sender) throws CommandException {
        if (sender instanceof EntityPlayerMP) {
            if (BoardService.isAdmin((EntityPlayerMP) sender)) return;
            throw new CommandException("Te falta " + BoardService.adminRule() + ".");
        }
        if (sender.canUseCommand(4, "")) return;
        throw new CommandException("Solo la consola o alguien con " + BoardService.adminRule() + " puede usar esto.");
    }

    private static void tell(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString("§7[RebornQuests] §r" + message));
    }

    /**
     * A command line that changes the config belongs in the record exactly like the same change made with a
     * button in the admin screen: same author, same hidden file. A listing writes nothing, so it is never an
     * entry, and the whole argument line is kept because that is the change itself.
     */
    private static void auditWrite(ICommandSender sender, String[] args) {
        if (!writes(args)) return;
        StringBuilder line = new StringBuilder();
        for (String part : args) {
            if (line.length() > 0) line.append(' ');
            line.append(part);
        }
        AuditStore.record(sender.getName(), "comando",
                args.length > 1 ? args[0] + " " + args[1] : args[0], line.toString());
    }

    /** True when the arguments of panel, mision or acceso ask for a change instead of a listing. */
    private static boolean writes(String[] args) {
        if (args.length < 2) return false;
        String action = args[1].toLowerCase();
        if (action.equals("listar") || action.equals("npcs")) return false;
        if (args[0].equalsIgnoreCase("acceso")
                && (action.equals("panel") || action.equals("quienes"))) return false;
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, SUBS);
        if (args.length == 2 && args[0].equalsIgnoreCase("panel")) {
            return getListOfStringsMatchingLastWord(args, "listar", "nuevo", "borrar", "renombrar", "aldea",
                    "requisitos", "subir", "bajar", "orden", "agrupar", "sembrar", "permiso", "mision", "npc", "npcs");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("panel") && args[1].equalsIgnoreCase("npc")) {
            return getListOfStringsMatchingLastWord(args, "add", "del");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mision")) {
            return getListOfStringsMatchingLastWord(args, "listar", "dificultad", "nivel", "cooldown",
                    "enfriamiento", "limite", "tiempo");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("panel")
                && !args[1].equalsIgnoreCase("listar") && !args[1].equalsIgnoreCase("nuevo")
                && !args[1].equalsIgnoreCase("agrupar") && !args[1].equalsIgnoreCase("sembrar")) {
            return getListOfStringsMatchingLastWord(args, RebornQuests.config.panels());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("panel") && args[1].equalsIgnoreCase("aldea")) {
            return getListOfStringsMatchingLastWord(args, RebornQuests.config.categories());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("panel") && args[1].equalsIgnoreCase("requisitos")) {
            return getListOfStringsMatchingLastWord(args, RebornQuests.config.rankGroups);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mision") && !args[1].equalsIgnoreCase("listar")) {
            return getListOfStringsMatchingLastWord(args, questIds());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("mision") && levelAction(args[1])) {
            return getListOfStringsMatchingLastWord(args, RQConfig.DIFFICULTIES);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("abrir") || args[0].equalsIgnoreCase("rango"))) {
            return getListOfStringsMatchingLastWord(args, server.getPlayerList().getOnlinePlayerNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("abrir")) {
            return getListOfStringsMatchingLastWord(args, RebornQuests.config.panels());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("acceso")) {
            return getListOfStringsMatchingLastWord(args, "listar", "panel", "dar", "quitar");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("acceso")) {
            if (args[1].equalsIgnoreCase("panel") || args[1].equalsIgnoreCase("quienes")) {
                return getListOfStringsMatchingLastWord(args, RebornQuests.config.panels());
            }
            return getListOfStringsMatchingLastWord(args, server.getPlayerList().getOnlinePlayerNames());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("acceso") && !args[1].equalsIgnoreCase("listar")
                && !args[1].equalsIgnoreCase("panel")) {
            return getListOfStringsMatchingLastWord(args, RebornQuests.config.panels());
        }
        return new ArrayList<String>();
    }

    private static String[] questIds() {
        List<QuestBridge.QuestRef> all = QuestBridge.allQuests();
        List<String> out = new ArrayList<String>();
        for (QuestBridge.QuestRef quest : all) out.add(String.valueOf(quest.id));
        return out.toArray(new String[0]);
    }
}
