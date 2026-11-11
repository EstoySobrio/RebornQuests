package dev.sobrio.rebornquests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Owns every tunable of the mod. Rank names, panel gates and the board texts all live here. */
public class RQConfig {
    public static final String GENERAL = "general";
    public static final String RANKS = "rango";
    public static final String PANELS = "paneles";
    public static final String BRIDGE = "luckperms";

    /** Tabs of the admin screen, in the order of its strip. */
    public static final int TAB_PANELS = 0;
    public static final int TAB_MISSIONS = 1;
    public static final int TAB_RANKS = 2;
    public static final int TAB_PERMS = 3;
    public static final int TAB_BOARD = 4;
    public static final int TAB_STATE = 5;
    /** What each mission difficulty asks for before its missions can be accepted. */
    public static final int TAB_LEVELS = 6;
    /** The record of changes, which this screen only ever reads. */
    public static final int TAB_AUDIT = 7;

    private final Configuration cfg;

    public String title = "Misiones";
    public String commandName = "rebornmisiones";
    public String commandAliases = "rbmisiones,rbm";

    public List<String> rankGroups = new ArrayList<String>();
    public List<String> rankLabels = new ArrayList<String>();
    public String noBridgeRank = "estudiante_academia";

    public String adminNode = "staff";
    public String boardNode = "";

    /** The panel list, in the order the board shows it. */
    public final List<PanelDef> panels = new ArrayList<PanelDef>();
    /** Categories panels are filed under, in the order the board lists them. */
    public List<String> panelCategories = new ArrayList<String>();
    /** LuckPerms nodes a panel demands, any number of them: "<nombre>;<nodo1,nodo2>". */
    public List<String> panelNodes = new ArrayList<String>();
    /** The Custom NPCs quests inside one panel: "<nombre>;<id,id,...>". */
    public List<String> panelMissions = new ArrayList<String>();
    /**
     * The missions one rank is offered inside one panel, which is what makes the same village offer a
     * different job list to a genin and to an ANBU: "<nombre>;<grupo>;<aldea>;<id,id,...>". A rank with
     * no line of its own falls back to the plain list of the panel.
     */
    public List<String> panelRankMissions = new ArrayList<String>();
    /**
     * Players the staff let into one panel by name, with no LuckPerms permission of any kind behind them:
     * "<nombre>;<jugador,jugador>". It is the way to test a panel with one account, or to keep a favour
     * without inventing a group for it.
     */
    public List<String> panelGrants = new ArrayList<String>();
    /**
     * The NPCs that carry the mod's own role on one panel: "<nombre>;<npc,npc>". Each name is the one Custom
     * NPCs shows over the NPC, or "#<uuid>" to pin one single NPC of that same name. Any NPC of the pack can
     * be listed, whatever role it has in its own editor, and one NPC listed on several panels opens them all.
     */
    public List<String> panelNpcs = new ArrayList<String>();
    /** Difficulty, cooldown and time limit of one quest: "<id>;<nivel>;<cooldown>;<limite>", minutes. */
    public List<String> missionData = new ArrayList<String>();
    /**
     * How far up the ladder one player has got: "<nivel>;<contado>;<minimas>" means that accepting a
     * mission of {@code nivel} asks for {@code minimas} finished missions of {@code contado} or harder.
     */
    public List<String> missionGates = new ArrayList<String>();

    /**
     * The tiers a mission can carry, from the easiest to the hardest, shown in game as "Mision D" a
     * "Mision S": D is the lowest level and S the highest. They have nothing to do with the LuckPerms
     * ladder of the players: this is how hard one mission is.
     */
    public static final String[] DIFFICULTIES = {"D", "C", "B", "A", "S"};
    /** Label of the group for a panel that files itself in no category. */
    public static final String NO_CATEGORY = "Sin categoria";

    /**
     * The name of the mod's own board role. It is not one of the roles Custom NPCs lists in its editor: an
     * NPC gets it by being written on {@link #panelNpcs}, which is why any NPC of the pack can carry a tablón.
     */
    public static final String BOARD_ROLE = "Misiones";
    /**
     * How far the config file of this build is migrated. A file written by an older build is brought up to
     * here the first time it is read, so updating the mod is what updates the config.
     */
    public static final int CONFIG_REVISION = 2;
    /** The revision the file was left on; 0 means it came from a build that never stamped one. */
    public int configRevision;
    /** Which build last touched the file, as the Estado tab shows it. */
    public String configStamp = "";

    public int refreshSeconds = 20;
    public boolean announceOnLogin = true;
    public boolean requireRankNode = false;
    /**
     * The op level a player needs when the server has no permission plugin to ask. Permissions are a
     * LuckPerms matter, so without one the only thing left that says "this is mine" is the op flag, and that
     * is what this asks for. It is never read while LuckPerms answers for the node.
     */
    public int opWithoutBridge = 4;
    public boolean lockWithoutBridge = false;
    public int maxQuestRows = 40;
    /** Where the on-screen mission clock starts; the first option switches it off. */
    public String hudMode = HUD_MODES[5];
    /** The clock of Custom NPCs' own quest book is replaced by the mod's board. */
    public boolean replaceQuestLog = true;
    /**
     * The Custom NPCs role that marks an NPC as a board, when the staff wants one. An NPC whose role is not
     * this one is never touched: his click, his dialogue, his scripts and his shop stay exactly as Custom
     * NPCs made them. 'cualquiera' leaves the mark where it has always been, which is the NPC being listed
     * on a panel, and that is the default so no NPC of a pack changes behaviour on its own.
     */
    public String boardRole = "cualquiera";
    /** Whether the time limit of a mission keeps running while its player is offline. */
    public boolean limitRunsOffline = false;
    /** The record of changes: on by default, because its whole point is holding staff accountable. */
    public boolean audit = true;
    /** Seconds between two walks of the Custom NPCs files looking for changes. */
    public int auditSeconds = 20;
    /** LuckPerms nodes a player's village is read from: the first category with this node on it wins. */
    public String villageNode = "aldea.";
    /** Village used when the player carries no village node at all. "-" means none. */
    public String villageDefault = "-";
    /**
     * How many ranks below its own a rank may reach for missions: 0 leaves only the lists of its own rank,
     * and {@link #rankDepthCap()} opens the whole ladder downward. It never walks up, so a genin never gets
     * the missions of a chunnin.
     */
    public int rankDepth = RANK_DEPTH_MAX;
    /** The biggest depth the file may carry at all; {@link #rankDepthCap()} trims it to the real ladder. */
    public static final int RANK_DEPTH_MAX = 60;
    public static final String[] HUD_MODES = {"apagado", "arriba a la izquierda", "arriba a la derecha",
        "abajo a la izquierda", "abajo a la derecha", "centro a la izquierda", "centro a la derecha"};
    /**
     * The roles Custom NPCs already has, in the order the staff picks them, with 'cualquiera' in front.
     * Nothing is added to Custom NPCs: the mod only reads the role its editor already puts on an NPC.
     */
    public static final String[] BOARD_ROLES = {"cualquiera", "ninguno", "comerciante", "seguidor", "banco",
        "transporte", "cartero", "companero", "dialogo"};
    /** How {@link #boardRole} is written when the role of the NPC is not looked at at all. */
    public static final String ANY_BOARD_ROLE = "cualquiera";

    public RQConfig(File file) {
        cfg = new Configuration(file);
    }

    public void load() {
        cfg.load();
        migrate();
        boolean firstRun = !cfg.hasKey(PANELS, "rules");
        title = cfg.get(GENERAL, "title", "Misiones",
                "Titulo del tablon. Es lo que leen todos los jugadores al abrirlo.").getString();
        commandName = cfg.get(GENERAL, "commandName", "rebornmisiones",
                "Comando principal. Sus subcomandos se listan con /rebornmisiones ayuda.").getString();
        commandAliases = cfg.get(GENERAL, "commandAliases", "rbmisiones,rbm",
                "Alias separados por coma. Vacio para no tener ninguno.").getString();
        maxQuestRows = cfg.get(GENERAL, "maxQuestRows", 40, "Filas de misiones por panel antes de cortar la lista.", 1, 120).getInt();
        limitRunsOffline = cfg.get(GENERAL, "limitRunsOffline", false,
                "Si el tiempo limite de una mision sigue corriendo mientras su jugador esta desconectado. "
                        + "Desactivado, el reloj se para al salir y se reanuda al volver a entrar.").getBoolean();
        audit = cfg.get(GENERAL, "audit", true,
                "Registro de cambios: anota quien toca cada mision, dialogo, script de Custom NPCs o ajuste del "
                        + "panel, con las lineas exactas que entraron y salieron. Es solo de anadir: dentro del "
                        + "mod no existe ninguna forma de borrarlo, ni boton ni subcomando.").getBoolean();
        auditSeconds = cfg.get(GENERAL, "auditSeconds", 20,
                "Cada cuantos segundos se miran los archivos de Custom NPCs en busca de cambios. Con 5 se anota "
                        + "al momento; con 300 apenas cuesta. Solo importa cuando audit esta activado.",
                5, 600).getInt();
        hudMode = cfg.get(GENERAL, "hudMode", HUD_MODES[5],
                "Posicion por defecto del reloj de las misiones en curso. Cada jugador puede moverla o apagarla "
                        + "desde el propio libro de misiones; ahi se guarda su propia eleccion. 'apagado' no dibuja nada.").getString();
        replaceQuestLog = cfg.get(GENERAL, "replaceQuestLog", true,
                "La ventana de misiones de Custom NPCs se sustituye por el tablón del mod, con los tiempos de "
                        + "cada mision y su colocacion de HUD. Desactivado se abre la ventana original de CNPC.").getBoolean();
        boardRole = cfg.get(GENERAL, "boardRole", ANY_BOARD_ROLE,
                "Rol de Custom NPCs que marca al NPC de un tablón: ninguno, comerciante, seguidor, banco, "
                        + "transporte, cartero, companero o dialogo, y 'cualquiera' para no mirar el rol. Es un "
                        + "rol de los que ya trae Custom NPCs: el mod no anade ninguno, no toca su editor y no "
                        + "le quita el suyo a nadie. Con 'cualquiera' basta con que el NPC este escrito en un "
                        + "panel; con un rol puesto, el NPC necesita ademas llevar ese rol en su pestaña "
                        + "Advanced, y uno que no lo lleva se queda con su dialogo, sus scripts y su tienda "
                        + "exactamente igual que antes. Que panel abre cada NPC se apunta siempre en paneles.npcs: "
                        + "un NPC con el rol pero sin escribir en ningun panel no abre nada.").getString();
        configRevision = cfg.get(GENERAL, "configRevision", CONFIG_REVISION,
                "Que tan preparada esta la config para esta version del mod. La escribe el mod solo.").getInt();
        configStamp = cfg.get(GENERAL, "configStamp", "",
                "Version del mod que escribio la config por ultima vez. La escribe el mod solo.").getString();

        rankGroups = readList(cfg.get(RANKS, "groups", GROUPS_DEFAULT,
                "Grupos de LuckPerms del rango mas bajo al mas alto. El rango de un jugador es el grupo mas alto que tenga."));
        rankLabels = readList(cfg.get(RANKS, "labels", LABELS_DEFAULT,
                "Nombre mostrable de cada grupo, en el mismo orden que groups."));
        noBridgeRank = cfg.get(RANKS, "noBridgeRank", "estudiante_academia",
                "Grupo que se asume cuando no hay LuckPerms/Bukkit (singleplayer o prueba local).").getString();
        rankDepth = cfg.get(RANKS, "rankDepth", rankDepthCap(),
                "Cuantos rangos por debajo del tuyo puedes usar para misiones. 0 = solo las de tu rango. "
                        + "Con la ladder de 11, el Comandante Fujin (puesto 11) y 3 baja hasta ANBU (puesto 8). "
                        + "Un numero igual o mayor que la cantidad de rangos deja todos los inferiores. "
                        + "Nunca hacia arriba: un genin no acepta las misiones de un chunnin.").getInt();
        rankDepth = Math.max(0, Math.min(rankDepthCap(), rankDepth));

        adminNode = cfg.get(BRIDGE, "adminNode", "staff",
                "Permiso de LuckPerms que manda en el mod. Mientras tenga valor no se mira si el jugador es op. "
                        + "Vacio = manda solo el op 4, para cuando no tienes plugin de permisos.").getString();
        opWithoutBridge = cfg.get(BRIDGE, "opWithoutBridge", 4,
                "Nivel de op que hace falta cuando el server no tiene plugin de permisos (singleplayer, LAN o "
                        + "Forge puro). Los permisos son cosa de LuckPerms: sin plugin no hay nodo que comprobar, "
                        + "y entonces solo manda el op. Con 4 nadie entra sin estar op al maximo; con 0 entra "
                        + "cualquiera, que es lo que NO quieres en un LAN.", 0, 4).getInt();
        boardNode = cfg.get(BRIDGE, "boardNode", "",
                "Nodo de LuckPerms necesario para abrir el tablon. Vacio = basta con tener rango.").getString();
        refreshSeconds = cfg.get(BRIDGE, "refreshSeconds", 20, "Segundos que se guarda el rango leido de LuckPerms.", 1, 3600).getInt();
        announceOnLogin = cfg.get(BRIDGE, "announceOnLogin", true, "Avisa del rango detectado al entrar.").getBoolean();
        requireRankNode = cfg.get(BRIDGE, "requireRankNode", false,
                "Pide tambien el nodo rebornmisiones.rango.<grupo> ademas de la pertenencia al grupo.").getBoolean();
        villageNode = cfg.get(BRIDGE, "villageNode", "aldea.",
                "Prefijo del nodo de LuckPerms que dice de que aldea es un jugador: aldea.kono, aldea.suna... "
                        + "La aldea decide que lista de misiones le enseña cada panel para su rango.").getString();
        villageDefault = cfg.get(BRIDGE, "villageDefault", "-",
                "Aldea que se usa cuando el jugador no lleva ningun nodo de aldea. '-' = ninguna.").getString();
        lockWithoutBridge = cfg.get(BRIDGE, "lockWithoutBridge", false,
                "Sin LuckPerms/Bukkit, cierra todos los paneles en vez de usar noBridgeRank.").getBoolean();

        readPanels(cfg.get(PANELS, "rules", new String[0],
                "Paneles del mod, en el orden en que se muestran: <nombre>;<grupo>;<minimas>;<categoria>. "
                        + "Ejemplo: Misiones de Suna;chunnin;25;Suna. El grupo - no exige rango."));
        panelCategories = readList(cfg.get(PANELS, "categories", CATEGORIES_DEFAULT,
                "Categorias que agrupan los paneles en el tablón. Se pueden anadir desde el panel de administracion."));
        panelNodes = readList(cfg.get(PANELS, "nodes", new String[0],
                "Permisos de LuckPerms que exige un panel, todos a la vez: <nombre>;<nodo1,nodo2>. "
                        + "Un permiso que sea un grupo de la ladder se lee como group.<grupo>."));
        panelMissions = readList(cfg.get(PANELS, "missions", new String[0],
                "Misiones de Custom NPCs dentro de un panel: <nombre>;<id,id,...>. Los ids son los de la agenda de misiones."));
        panelRankMissions = readList(cfg.get(PANELS, "rankMissions", new String[0],
                "Misiones que un panel le ensena a un rango concreto de una aldea concreta: "
                        + "<nombre>;<grupo>;<aldea>;<id,id,...>. Un rango sin linea propia se queda con la "
                        + "lista normal del panel."));
        panelGrants = readList(cfg.get(PANELS, "grants", new String[0],
                "Jugadores que pueden abrir un panel sin tener su rango ni sus permisos, por nombre: "
                        + "<nombre>;<jugador,jugador>. Sirve para probar un panel con una cuenta suelta. "
                        + "Se da y se quita con el subcomando acceso."));
        panelNpcs = readList(cfg.get(PANELS, "npcs", new String[0],
                "Rol propio del mod: que NPCs abren cada panel, con el nombre que Custom NPCs le pone "
                        + "encima del NPC: <nombre>;<npc,npc>. Se edita sin tocar este archivo con «/"
                        + "rebornmisiones panel npc» desde el juego, o desde el propio tablón; aqui solo se "
                        + "lee lo que se puso alli. Vale tambien '#<uuid>' para atar el panel a un solo NPC "
                        + "aunque otro lleve el mismo nombre. Un NPC listado en varios paneles los abre todos, "
                        + "y el tablón entonces muestra lo que puedes aceptar de ellos juntos. No hace falta "
                        + "ningun rol de Custom NPCs: cualquier NPC del pack puede llevarlo, salvo que "
                        + "general.boardRole pida uno."));
        missionGates = readList(cfg.get(PANELS, "gates", new String[0],
                "Cuantas misiones hay que haber terminado para poder aceptar un nivel: <nivel>;<contado>;<minimas>. "
                        + "'contado' vale tambien como 'de ese nivel o mas duro'. Ejemplo: C;D;5 abre Mision C "
                        + "despues de 5 misiones D o mas duras. Nivel y contado van de D (bajo) a S (alto)."));
        missionData = readList(cfg.get(PANELS, "data", new String[0],
                "Nivel de mision (Mision D a Mision S), enfriamiento y tiempo limite, en minutos: "
                        + "<id>;<D|C|B|A|S>;<cooldown>;<limite>. Ejemplo: 12;B;60;30. "
                        + "El nivel de una mision no tiene nada que ver con el rango del jugador. "
                        + "Cooldown y limite: 0, no, false, off, desactivado o deshabilitado = sin efecto."));
        if (firstRun && panels.isEmpty()) seedPanels();
        cfg.save();
    }

    /**
     * Brings a file written by an older build up to the layout this build reads, so updating the mod is what
     * updates the config: keys the build retired come out, new ones are seeded from what the old ones said,
     * and every move is written in the record of changes. It runs before anything else is read, so the reads
     * below only ever see a file that is already up to date.
     */
    private void migrate() {
        int stored = cfg.hasKey(GENERAL, "configRevision")
                ? cfg.get(GENERAL, "configRevision", CONFIG_REVISION).getInt() : 0;
        if (stored >= CONFIG_REVISION) return;
        List<String> moves = new ArrayList<String>();
        if (stored < 1) {
            moves.add(retireBoardRole());
            moves.addAll(fixGateLevels());
        }
        if (stored < 2) {
            moves.add(dropLocalAdmin());
        }
        cfg.get(GENERAL, "configRevision", CONFIG_REVISION).set(CONFIG_REVISION);
        cfg.get(GENERAL, "configStamp", "").set(stamp());
        cfg.save();
        String detail = "de la revision " + stored + " a la " + CONFIG_REVISION;
        for (String move : moves) {
            if (move == null || move.trim().isEmpty()) continue;
            detail = detail + "\n" + move;
        }
        AuditStore.record("el mod", "config actualizada", "rebornquests.cfg", detail);
        RebornQuests.LOGGER.info("RebornQuests: config llevada de la revision {} a la {}. {}",
                Integer.valueOf(stored), Integer.valueOf(CONFIG_REVISION), detail.replace('\n', ' '));
    }

    /** How the file signs the build that last wrote it. */
    private static String stamp() {
        String version = RebornQuests.buildVersion();
        return version.isEmpty() ? "revision " + CONFIG_REVISION : version + " (revision " + CONFIG_REVISION + ")";
    }

    /**
     * The board stopped asking for one of Custom NPCs' own roles: it has a role of its own now, which is the
     * list of NPCs written on {@code paneles.npcs}. A pack that named its board NPC keeps that NPC, on the
     * first panel of the board, so nobody has to retype anything after the update.
     */
    private String retireBoardRole() {
        String named = cfg.hasKey(GENERAL, "roleName") ? cfg.get(GENERAL, "roleName", "").getString().trim() : "";
        boolean listed = cfg.hasKey(PANELS, "npcs") && cfg.get(PANELS, "npcs", new String[0]).getStringList().length > 0;
        remove(GENERAL, "roleName");
        remove(GENERAL, "requireDialogRole");
        if (listed) return "paneles.npcs ya tenia NPCs: los roles viejos de Custom NPCs fuera, sin tocar la lista.";
        String first = firstPanelName();
        if (first == null || named.isEmpty()) {
            return "Rol de Custom NPCs para el tablón fuera: ahora se elige NPC por NPC en paneles.npcs.";
        }
        cfg.get(PANELS, "npcs", new String[0]).set(new String[] { first + ";" + named });
        return "El NPC '" + named + "' pasa al rol propio del mod, en el panel " + first + ".";
    }

    /**
     * The two keys that answered "who is this without a permission plugin" are out, because between them
     * they let any player of a LAN world through: permissions are a LuckPerms matter, and where there is no
     * plugin to ask there is nothing left but the op flag of the server, which is what
     * {@code bridge.opWithoutBridge} now asks for. Nothing moves over on purpose — a file that had them on
     * said "everybody", and that is the one thing this is not going to keep.
     */
    private String dropLocalAdmin() {
        boolean had = cfg.hasKey(BRIDGE, "adminWithoutBridge") || cfg.hasKey(BRIDGE, "localAdmin");
        remove(BRIDGE, "adminWithoutBridge");
        remove(BRIDGE, "localAdmin");
        if (!had) return "Sin plugin de permisos manda el op (" + opWithoutBridgeText() + ").";
        return "Los dos 'sin plugin también vale' fuera: sin plugin de permisos manda el op ("
                + opWithoutBridgeText() + "), y el dueño de un mundo suyo sigue usando el suyo.";
    }

    private String opWithoutBridgeText() {
        int level = cfg.hasKey(BRIDGE, "opWithoutBridge")
                ? cfg.get(BRIDGE, "opWithoutBridge", 4).getInt() : 4;
        return level == 0 ? "cualquiera" : "nivel " + level;
    }

    /** The panel the board list starts with, straight from the file because the lists are not read yet. */
    private String firstPanelName() {

        for (String raw : cfg.get(PANELS, "rules", new String[0]).getStringList()) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            int cut = trimmed.indexOf(';');
            String name = (cut < 0 ? trimmed : trimmed.substring(0, cut)).trim();
            if (!name.isEmpty()) return name;
        }
        return null;
    }

    /**
     * A difficulty can only ever ask for work of an <i>easier</i> level, so a gate line naming its own level
     * or a harder one never meant anything. Those lines are moved one step down the ramp, and the bottom of
     * the ramp, which had nothing below it, loses the gate instead.
     */
    private List<String> fixGateLevels() {
        List<String> out = new ArrayList<String>();
        List<String> kept = new ArrayList<String>();
        for (String raw : cfg.get(PANELS, "gates", new String[0]).getStringList()) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split(";");
            int level = parts.length > 0 ? levelIndexOf(parts[0]) : -1;
            int counted = parts.length > 1 ? levelIndexOf(parts[1]) : -1;
            int min = parts.length > 2 ? parseInt(parts[2], 0) : 0;
            if (level < 0 || counted < 0 || min <= 0 || counted < level) {
                kept.add(trimmed);
                continue;
            }
            if (level == 0) {
                out.add("Mision D: se quita un requisito que se pedia a si mismo (" + min + " de "
                        + DIFFICULTIES[counted] + ").");
                continue;
            }
            String below = levelAt(level - 1);
            kept.add(DIFFICULTIES[level] + ";" + below + ";" + min);
            out.add("Mision " + DIFFICULTIES[level] + ": cuenta " + DIFFICULTIES[counted] + " -> " + below + ".");
        }
        cfg.get(PANELS, "gates", new String[0]).set(kept.toArray(new String[0]));
        return out;
    }

    /** Takes one retired key out of the file; a section that is not there has nothing to retire. */
    private void remove(String section, String key) {
        if (!cfg.hasKey(section, key)) return;
        cfg.getCategory(section).remove(key);
    }

    /**
     * The NPCs written on one panel, in the order the staff added them. Each one is the name Custom NPCs
     * shows over the NPC, or {@code #<uuid>} for one single NPC of that name.
     */
    public List<String> npcs(String panel) {
        List<String> out = new ArrayList<String>();
        String[] parts = entry(panelNpcs, panel);
        if (parts == null || parts.length < 2) return out;
        for (String npc : parts[1].split(",")) {
            String trimmed = npc.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /**
     * Every panel an NPC carries the role of: the panels that list its name, or list its uuid. The board
     * opens on the first of them and, when there is more than one, offers what can be accepted across all.
     */
    public List<String> panelsOfNpc(String name, String uuid) {
        String wanted = name == null ? "" : name.trim();
        List<String> out = new ArrayList<String>();
        for (PanelDef def : panels) {
            for (String entry : npcs(def.name)) {
                if (sameNpc(entry, wanted, uuid)) {
                    out.add(def.name);
                    break;
                }
            }
        }
        return out;
    }

    /** A uuid entry pins one NPC of its own; a name entry matches any NPC the editor calls by that name. */
    private static boolean sameNpc(String entry, String name, String uuid) {
        String wanted = entry.trim();
        if (wanted.isEmpty()) return false;
        if (wanted.startsWith("#")) {
            return uuid != null && !uuid.isEmpty() && wanted.substring(1).equalsIgnoreCase(uuid.trim());
        }
        return wanted.equalsIgnoreCase(name);
    }

    /**
     * The id Custom NPCs gives the role that marks a board, which is what {@code QuestBridge.roleType} answers
     * with for an NPC. {@code -1} for {@link #ANY_BOARD_ROLE}: then the role is never looked at and every NPC
     * written on a panel opens it.
     */
    public int boardRoleId() {
        return boardRoleId(boardRole);
    }

    /** The same id for any of the names of {@link #BOARD_ROLES}; -1 when nothing has to be checked. */
    public static int boardRoleId(String role) {
        String wanted = fold(role);
        if (wanted.isEmpty() || ANY_BOARD_ROLE.equals(wanted)) return -1;
        for (int i = 0; i < ROLE_IDS.length; i++) {
            if (BOARD_ROLES[i + 1].equals(wanted)) return ROLE_IDS[i];
        }
        return -1;
    }

    /** The role names of {@link #BOARD_ROLES} minus 'cualquiera', next to the id Custom NPCs gives each one. */
    private static final int[] ROLE_IDS = {0, 1, 2, 3, 4, 5, 6, 7};

    /** The way one typed role is stored, or null when it is not one of {@link #BOARD_ROLES}. */
    public static String boardRoleChoice(String value) {
        String wanted = fold(value);
        for (String option : BOARD_ROLES) {
            if (option.equals(wanted)) return option;
        }
        return null;
    }

    /**
     * Whether one clicked NPC is the kind of NPC the board lives on. An NPC of another role is left alone
     * completely: no click of his is cancelled, so his dialogue, his scripts and his shop work as Custom
     * NPCs made them.
     */
    public boolean isBoardRole(int roleType) {
        int wanted = boardRoleId();
        return wanted < 0 || wanted == roleType;
    }

    /** Puts the mod's role on an NPC of one panel. Nothing of Custom NPCs is written to make it work. */
    public String addNpc(String panel, String npc) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        String wanted = cleanNpc(npc);
        if (wanted == null) return "Escribe el nombre del NPC tal y como lo pone Custom NPCs, sin ; ni coma.";
        List<String> names = npcs(def.name);
        if (containsName(names, wanted)) return wanted + " ya abría " + def.name + ".";
        names.add(wanted);
        writeList(panelNpcs, def.name, join(names, ","));
        save();
        return wanted + " abre " + def.name + " (" + names.size() + (names.size() == 1 ? " NPC)." : " NPCs).");
    }

    public String removeNpc(String panel, String npc) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        String wanted = npc == null ? "" : npc.trim();
        List<String> names = npcs(def.name);
        if (!containsName(names, wanted)) return wanted + " no abría " + def.name + ".";
        for (int i = names.size() - 1; i >= 0; i--) {
            if (names.get(i).equalsIgnoreCase(wanted)) names.remove(i);
        }
        writeList(panelNpcs, def.name, join(names, ","));
        save();
        return names.isEmpty() ? def.name + " no lo abre ningun NPC por ahora."
                : def.name + " se queda con " + names.size() + (names.size() == 1 ? " NPC." : " NPCs.");
    }

    /** Names are what the editor shows, so they keep their spaces; a uuid entry is a '#' and the uuid. */
    private static String cleanNpc(String npc) {
        String wanted = npc == null ? "" : npc.trim();
        if (wanted.startsWith("#")) {
            String uuid = wanted.substring(1).trim().replace(" ", "");
            if (uuid.isEmpty() || uuid.length() > 40) return null;
            return "#" + uuid;
        }
        if (wanted.isEmpty() || wanted.contains(";") || wanted.contains(",") || wanted.length() > 40) return null;
        return wanted;
    }

    /**
     * A brand new pack gets one panel per rank of every village, which is the way the board is meant to be
     * used: each panel already asks for the rank it belongs to, so a player only ever sees the ones he can
     * really accept from and the staff only has to put missions inside the one he is editing.
     */
    private void seedPanels() {
        seedRankPanels();
    }

    /**
     * The panels of the ladder, one per rank and village. Names that already exist are left alone, so this
     * can be run again on a pack that was started by hand without duplicating anything.
     */
    public String seedRankPanels() {
        int before = panels.size();
        for (String village : panelCategories) {
            String town = cleanCategory(village);
            if (town.isEmpty()) continue;
            for (int i = 0; i < rankGroups.size(); i++) {
                String name = rankPanelName(town, rankLabel(i));
                if (name.isEmpty() || has(name)) continue;
                PanelDef panel = new PanelDef();
                panel.name = name;
                panel.group = rankGroups.get(i);
                panel.category = town;
                panels.add(panel);
            }
        }
        int added = panels.size() - before;
        save();
        return added == 0
                ? "Todos los paneles de rango ya estaban: " + panels.size() + " paneles."
                : added + " paneles creados, uno por rango de cada aldea (" + panels.size() + " en total).";
    }

    /** The name of the panel one rank of one village owns: the village first, then the rank as it reads. */
    private static String rankPanelName(String village, String label) {
        String name = (village + " " + label).trim().replace(";", "").replace(",", " ");
        return name.length() > 40 ? name.substring(0, 40).trim() : name;
    }

    public void save() {
        cfg.get(PANELS, "rules", new String[0]).set(writePanels());
        cfg.get(PANELS, "categories", new String[0]).set(panelCategories.toArray(new String[0]));
        cfg.get(PANELS, "nodes", new String[0]).set(panelNodes.toArray(new String[0]));
        cfg.get(PANELS, "missions", new String[0]).set(panelMissions.toArray(new String[0]));
        cfg.get(PANELS, "rankMissions", new String[0]).set(panelRankMissions.toArray(new String[0]));
        cfg.get(PANELS, "grants", new String[0]).set(panelGrants.toArray(new String[0]));
        cfg.get(PANELS, "npcs", new String[0]).set(panelNpcs.toArray(new String[0]));
        cfg.get(PANELS, "gates", new String[0]).set(missionGates.toArray(new String[0]));
        cfg.get(PANELS, "data", new String[0]).set(missionData.toArray(new String[0]));
        cfg.save();
    }

    /** "<nombre>;<grupo>;<minimas>;<categoria>" per panel, which is how the file stores them. */
    private String[] writePanels() {
        List<String> out = new ArrayList<String>();
        for (PanelDef panel : panels) {
            out.add(panel.name + ";" + panel.group + ";" + panel.minFinished + ";" + panel.category);
        }
        return out.toArray(new String[0]);
    }

    private void readPanels(Property rules) {
        panels.clear();
        for (String raw : rules.getStringList()) {
            String[] parts = raw.split(";");
            if (parts.length == 0 || parts[0].trim().isEmpty()) continue;
            if (has(parts[0])) continue;
            PanelDef panel = new PanelDef();
            panel.name = parts[0].trim();
            panel.group = parts.length > 1 ? parts[1].trim() : "-";
            panel.minFinished = parts.length > 2 ? Math.max(0, parseInt(parts[2], 0)) : 0;
            panel.category = parts.length > 3 ? cleanCategory(parts[3]) : "";
            panels.add(panel);
        }
    }

    /**
     * Every key the admin screen may edit, already filled with its current value. The screen draws a
     * control per entry from its type, so a new tunable only needs one line here.
     */
    public List<BoardData.Setting> settings() {
        List<BoardData.Setting> out = new ArrayList<BoardData.Setting>();
        out.add(BoardData.Setting.text(TAB_BOARD, "general.title", "Titulo del tablón", title,
                "Lo que se lee arriba del tablón."));
        out.add(BoardData.Setting.number(TAB_BOARD, "general.maxQuestRows", "Filas por panel", maxQuestRows, 1, 120,
                "Se corta la lista de misiones mas alla de este numero."));
        out.add(BoardData.Setting.bool(TAB_BOARD, "general.limitRunsOffline",
                "El tiempo límite sigue corriendo al desconectar", limitRunsOffline,
                "Apagado, el reloj de una misión se para mientras su jugador está fuera y se reanuda al "
                        + "volver, así que nadie pierde una misión por cerrar el juego."));
        out.add(BoardData.Setting.choice(TAB_BOARD, "general.hudMode", "Posición por defecto del reloj", hudMode,
                Arrays.asList(HUD_MODES),
                "Caja con las misiones en curso, su progreso y lo que queda de su tiempo. Es el sitio con el que "
                        + "empieza todo el mundo: cada jugador la mueve, la coloca o la apaga desde el propio "
                        + "libro de misiones y ahí se le guarda su elección."));
        out.add(BoardData.Setting.bool(TAB_BOARD, "general.replaceQuestLog", "Sustituir la ventana de misiones de Custom NPCs",
                replaceQuestLog, "Abre el tablón del mod en lugar de la ventana original de Custom NPCs, con los "
                        + "tiempos de cada misión y la colocación del reloj."));
        out.add(BoardData.Setting.choice(TAB_BOARD, "general.boardRole", "Rol de Custom NPCs del NPC de tablón",
                boardRole, java.util.Arrays.asList(BOARD_ROLES),
                "Con 'cualquiera' basta con que el NPC esté escrito en un panel de paneles.npcs, que es como "
                        + "siempre ha sido. Si pones un rol, el NPC necesita llevar ese rol en su pestaña "
                        + "Advanced de Custom NPCs además de estar escrito en el panel, y el que no lo lleva no "
                        + "hace nada al pulsarle: su diálogo, sus guiones y su tienda siguen siendo los de "
                        + "Custom NPCs. El mod no añade roles ni toca el editor de NPCs."));
        out.add(BoardData.Setting.bool(TAB_BOARD, "general.audit", "Registrar los cambios", audit,
                "Guarda quién cambió cada misión, diálogo, script de Custom NPCs o ajuste del panel, con las "
                        + "líneas exactas. El registro es solo de añadir: ni esta pantalla ni el comando pueden "
                        + "borrarlo. Se lee en la pestaña Auditoría y desde la consola con «log»."));
        out.add(BoardData.Setting.number(TAB_BOARD, "general.auditSeconds", "Segundos entre dos revisiones",
                auditSeconds, 5, 600,
                "Cada cuánto se miran los archivos de Custom NPCs buscando cambios. Con 5 se anota al momento; "
                        + "con 300 apenas cuesta."));
        out.add(BoardData.Setting.text(TAB_BOARD, "general.commandName", "Comando", commandName,
                "Se registra de nuevo al aplicar: no hace falta reiniciar el server."));
        out.add(BoardData.Setting.text(TAB_BOARD, "general.commandAliases", "Alias (separados por coma)", commandAliases,
                "Tambien se aplican sin reiniciar. Sin alias: /rebornmisiones sin argumentos abre el panel admin."));
        out.add(BoardData.Setting.text(TAB_BOARD, "paneles.categories", "Aldeas (separadas por coma)",
                join(panelCategories, ", "),
                "Categorías del tablón: cada panel pertenece a una aldea. Una nueva se añade sola si la escribes."));

        out.add(BoardData.Setting.text(TAB_RANKS, "rango.groups", "Ladder de grupos (menor a mayor)",
                join(rankGroups, ", "), "Tienen que existir como grupos de LuckPerms."));
        out.add(BoardData.Setting.text(TAB_RANKS, "rango.labels", "Etiquetas (mismo orden)",
                join(rankLabels, ", "), "Vacío para mostrar el nombre del grupo en mayúsculas."));
        out.add(BoardData.Setting.number(TAB_RANKS, "rango.rankDepth", "Rangos inferiores que puedes usar",
                rankDepth, 0, rankDepthCap(),
                "Un rango acepta las listas de su propio rango y las de los que estén por debajo hasta esta "
                        + "cantidad. 0 deja solo las suyas; con 3, el Comandante Fūjin baja hasta ANBU. Con "
                        + rankDepthCap() + " o más, que es la cantidad de rangos de la ladder, llega a todos "
                        + "los inferiores. Nunca hacia arriba: un genin no hace misiones de chunnin ni superiores."));

        out.add(BoardData.Setting.choice(TAB_PERMS, "rango.noBridgeRank", "Rango sin LuckPerms",
                noBridgeRank, rankGroups, "Se usa en singleplayer o en un Forge puro sin plugins."));
        out.add(BoardData.Setting.bool(TAB_PERMS, "luckperms.requireRankNode", "Exigir nodo de rango además del grupo",
                requireRankNode, "Pide rebornmisiones.rango.<grupo> en LuckPerms."));

        out.add(BoardData.Setting.text(TAB_PERMS, "luckperms.adminNode", "Nodo de administración", adminNode,
                "Vacío: manda el op (el nivel de abajo). Con valor: solo ese nodo de LuckPerms, y el op no abre nada."));
        out.add(BoardData.Setting.text(TAB_PERMS, "luckperms.boardNode", "Nodo para abrir el tablón", boardNode,
                "Vacío: basta con tener algún rango. Con valor se comprueba también al aceptar una misión, "
                        + "no solo al abrir el tablón."));
        out.add(BoardData.Setting.number(TAB_PERMS, "luckperms.opWithoutBridge",
                "Op necesario cuando no hay LuckPerms", opWithoutBridge, 0, 4,
                "Los permisos se deciden en LuckPerms. Si el server no lleva plugin de permisos no hay nodo "
                        + "que comprobar, y entonces solo manda el op: hace falta este nivel de op para abrir "
                        + "el panel de administración, el tablón y cualquier panel que pida un nodo. Vale para "
                        + "el server dedicado y para los que entran por LAN; el dueño de un mundo suyo sigue "
                        + "pudiendo usar su propio pack sin estar op. Con 0 cualquiera pasa, y eso es un "
                        + "agujero: dejalo en 4 salvo que sepas lo que haces."));
        List<String> villages = new ArrayList<String>();
        villages.add("-");
        villages.addAll(categories());
        out.add(BoardData.Setting.choice(TAB_PERMS, "luckperms.villageDefault", "Aldea por defecto",
                villageChoice(), villages,
                "La aldea de un jugador se lee del nodo " + villageNode + "<aldea> de LuckPerms. Sin ese nodo, "
                        + "se usa esta. La aldea decide qué lista de misiones le enseña cada panel a su rango."));
        out.add(BoardData.Setting.text(TAB_PERMS, "luckperms.villageNode", "Prefijo del nodo de aldea",
                villageNode, "Con 'aldea.' el nodo de un jugador de Kono es aldea.kono."));
        out.add(BoardData.Setting.bool(TAB_PERMS, "luckperms.lockWithoutBridge", "Cerrar todo sin plugin", lockWithoutBridge,
                "Sin plugin de permisos, ningún panel se abre en vez de usar el rango de abajo."));
        out.add(BoardData.Setting.number(TAB_PERMS, "luckperms.refreshSeconds", "Cache del rango (s)", refreshSeconds,
                1, 3600, "Cada cuántos segundos se vuelve a leer el rango en LuckPerms."));
        out.add(BoardData.Setting.bool(TAB_PERMS, "luckperms.announceOnLogin", "Avisar del rango al entrar",
                announceOnLogin, "Mensaje con el rango y las misiones completadas al conectarse."));
        out.addAll(levelSettings());
        return out;
    }

    /**
     * The two knobs of every mission difficulty: which difficulty gets counted and how many of those a
     * player must already have finished before a mission of this level opens. Only levels <b>easier</b> than
     * the one being opened can be counted, so the ramp reads D, C, B, A under the levels C, B, A and S, and
     * the bottom level has nothing below it to count. A count of 0 leaves the level open to anybody who can
     * see it, which is how a pack that never touched them behaves.
     */
    private List<BoardData.Setting> levelSettings() {
        List<BoardData.Setting> out = new ArrayList<BoardData.Setting>();
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            String level = DIFFICULTIES[i];
            Gate gate = gate(level);
            int where = gateCounted(i, gate);
            String counted = where < 0 ? NO_GATE : levelAt(where);
            int min = gate == null ? 0 : gate.min;
            out.add(BoardData.Setting.choice(TAB_LEVELS, "gate." + level + ".counted",
                    "Una Mision " + level + " se abre contando", counted, countedOptions(i),
                    "Cuenta las terminadas de ese nivel y de todos los que haya entre él y Mision " + level
                            + ". Un nivel no puede pedirse misiones de su propio nivel ni de uno mas duro, por"
                            + " eso Mision " + level + " solo elige entre " + countedChoices(i) + "."));
            out.add(BoardData.Setting.number(TAB_LEVELS, "gate." + level + ".min",
                    "Cuantas hacen falta para una Mision " + level, min, 0, 999,
                    "0 deja las misiones Mision " + level + " abiertas para quien las vea. Con un numero, el "
                            + "tablon avisa de lo que falta y no deja aceptarlas hasta tenerlo."));
        }
        return out;
    }

    /** The one option every level has besides its easier levels, which is the gate switched off. */
    public static final String NO_GATE = "sin requisito";

    /** What the screen offers a level: 'sin requisito' first, then every level easier than it. */
    private static List<String> countedOptions(int levelIndex) {
        List<String> out = new ArrayList<String>();
        out.add(NO_GATE);
        for (int i = 0; i < levelIndex; i++) out.add(DIFFICULTIES[i]);
        return out;
    }

    /** The same list as one line, so the help text of a level names what it can actually pick. */
    private static String countedChoices(int levelIndex) {
        List<String> out = new ArrayList<String>();
        for (String option : countedOptions(levelIndex)) {
            out.add(option.equals(NO_GATE) ? "dejarla abierta" : "Mision " + option);
        }
        return out.size() == 1 ? out.get(0)
                : String.join(", ", out.subList(0, out.size() - 1)) + " o " + out.get(out.size() - 1);
    }

    /**
     * Which level one gate counts, as its position in the ramp. -1 means the level asks nothing at all,
     * which is what a level with no gate line shows on the screen.
     */
    private static int gateCounted(int levelIndex, Gate gate) {
        if (gate != null) {
            int counted = levelIndexOf(gate.counted);
            if (counted >= 0 && counted < levelIndex) return counted;
        }
        return levelIndex <= 0 ? -1 : levelIndex - 1;
    }

    /** Writes one key of {@link #settings()} after validating it, and returns the line for the screen. */
    public String apply(String key, String raw) {
        String value = raw == null ? "" : raw.trim();
        try {
            if (key.equals("general.title")) {
                if (value.isEmpty()) return "El título no puede quedar vacío.";
                put(GENERAL, "title", value);
                return "Título: " + value + ".";
            }
            if (key.equals("general.maxQuestRows")) {
                int rows = toInt(value, maxQuestRows);
                if (rows < 1 || rows > 120) return "Las filas por panel van de 1 a 120.";
                put(GENERAL, "maxQuestRows", rows);
                return "Cada panel muestra hasta " + rows + " misiones.";
            }
            if (key.equals("general.limitRunsOffline")) {
                boolean flag = toBool(value);
                put(GENERAL, "limitRunsOffline", flag);
                return flag ? "El tiempo limite no se para aunque el jugador cierre el juego."
                        : "El tiempo limite se para mientras el jugador este desconectado.";
            }
            if (key.equals("luckperms.villageNode")) {
                if (value.isEmpty() || value.contains(";") || value.contains(",")) {
                    return "El prefijo no puede quedar vacio ni llevar ; o coma.";
                }
                put(BRIDGE, "villageNode", value);
                return "La aldea se lee del nodo " + villageNode + "<aldea>.";
            }
            if (key.equals("luckperms.villageDefault")) {
                String village = value.equals("-") ? "" : cleanCategory(value);
                if (!village.isEmpty() && !categories().contains(village)) return "Esa aldea no existe: " + join(categories(), ", ");
                put(BRIDGE, "villageDefault", village.isEmpty() ? "-" : village);
                return village.isEmpty() ? "Sin nodo de aldea, ninguna." : "Sin nodo de aldea, " + village + ".";
            }
            if (key.equals("general.hudMode")) {
                String mode = hudChoice(value);
                if (mode == null) return "Esa posicion de pantalla no esta en la lista.";
                put(GENERAL, "hudMode", mode);
                return "apagado".equals(mode) ? "El reloj de misiones arranca apagado para todo el mundo."
                        : "El reloj arranca " + mode + " para quien no lo haya movido.";
            }
            if (key.equals("general.replaceQuestLog")) {
                boolean flag = toBool(value);
                put(GENERAL, "replaceQuestLog", flag);
                return flag ? "La tecla de misiones abre el tablón del mod."
                        : "La tecla de misiones vuelve a abrir la ventana original de Custom NPCs.";
            }
            if (key.equals("general.audit")) {
                boolean flag = toBool(value);
                put(GENERAL, "audit", flag);
                return flag ? "Los cambios vuelven a registrarse."
                        : "El registro se para. Lo que ya esta escrito no se borra: sigue leyendose igual.";
            }
            if (key.equals("general.auditSeconds")) {
                int seconds = toInt(value, auditSeconds);
                if (seconds < 5 || seconds > 600) return "La revision va de 5 a 600 segundos.";
                put(GENERAL, "auditSeconds", seconds);
                return "Los archivos de Custom NPCs se revisan cada " + seconds + " s.";
            }
            if (key.equals("general.commandName")) {
                if (value.isEmpty() || value.contains(" ")) return "El comando no puede llevar espacios.";
                put(GENERAL, "commandName", value.toLowerCase(Locale.ROOT));
                RebornQuests.refreshCommand();
                return "Comando " + commandName + " — ya vale sin reiniciar.";
            }
            if (key.equals("general.commandAliases")) {
                put(GENERAL, "commandAliases", value.replace(" ", ""));
                RebornQuests.refreshCommand();
                return commandAliases.isEmpty() ? "Sin alias, solo vale /" + commandName + "."
                        : "Alias: " + commandAliases + " — ya valen sin reiniciar.";
            }
            if (key.equals("paneles.categories")) {
                List<String> villages = split(value);
                cfg.get(PANELS, "categories", CATEGORIES_DEFAULT,
                        "Categorias que agrupan los paneles en el tablón.").set(villages.toArray(new String[0]));
                flush();
                return "Aldeas: " + (panelCategories.isEmpty() ? "ninguna" : join(panelCategories, ", ")) + ".";
            }
            if (key.equals("rango.groups")) {
                List<String> groups = split(value);
                if (groups.isEmpty()) return "La ladder necesita al menos un grupo.";
                cfg.get(RANKS, "groups", GROUPS_DEFAULT, GROUPS_COMMENT)
                        .set(groups.toArray(new String[0]));
                flush();
                return "Ladder: " + join(rankGroups, " < ") + ".";
            }
            if (key.equals("rango.labels")) {
                cfg.get(RANKS, "labels", LABELS_DEFAULT, LABELS_COMMENT)
                        .set(split(value).toArray(new String[0]));
                flush();
                return "Etiquetas: " + (rankLabels.isEmpty() ? "las mayúsculas del grupo" : join(rankLabels, ", ")) + ".";
            }
            if (key.equals("rango.rankDepth")) {
                int depth = toInt(value, rankDepth);
                if (depth < 0) return "La profundidad no puede ser negativa.";
                if (depth > rankDepthCap()) depth = rankDepthCap();
                put(RANKS, "rankDepth", depth);
                if (depth == 0) return "Cada rango solo usa sus propias listas de misiones.";
                if (depth >= rankGroups.size()) {
                    return "Con " + depth + " cualquier rango llega a todos los inferiores.";
                }
                return "Un rango baja hasta " + depth + (depth == 1 ? " rango inferior." : " rangos inferiores.");
            }
            if (key.equals("rango.noBridgeRank")) {
                if (!value.equals("-") && rankIndex(value) < 0) return "Ese grupo no está en la ladder: " + join(rankGroups, ", ");
                put(RANKS, "noBridgeRank", value);
                return "Sin plugin de permisos se asume " + (value.equals("-") ? "sin rango" : rankLabel(rankIndex(value))) + ".";
            }
            if (key.equals("luckperms.adminNode")) {
                String node = value.trim().equals("-") ? "" : value.trim();
                put(BRIDGE, "adminNode", node);
                return node.isEmpty() ? "Mando sobre el mod: solo el op 4, sin nodo de LuckPerms."
                        : "Mando sobre el mod: el nodo " + node + " (nunca el op).";
            }
            if (key.equals("luckperms.boardNode")) {
                put(BRIDGE, "boardNode", value);
                return value.isEmpty() ? "Cualquiera con rango abre el tablón." : "El tablón exige el nodo " + value + ".";
            }
            if (key.equals("luckperms.requireRankNode")) {
                boolean flag = toBool(value);
                put(BRIDGE, "requireRankNode", flag);
                return flag ? "Además del grupo se exige rebornmisiones.rango.<grupo>." : "Basta con pertenecer al grupo.";
            }
            if (key.equals("luckperms.opWithoutBridge")) {
                int level = toInt(value, opWithoutBridge);
                if (level < 0 || level > 4) return "El nivel de op va de 0 a 4.";
                put(BRIDGE, "opWithoutBridge", level);
                return level == 0 ? "Sin plugin de permisos entra cualquiera, también un invitado de LAN."
                        : "Sin plugin de permisos manda el op " + level + ".";
            }
            if (key.equals("general.boardRole")) {
                String role = boardRoleChoice(value);
                if (role == null) return "Ese rol de Custom NPCs no está en la lista.";
                put(GENERAL, "boardRole", role);
                return ANY_BOARD_ROLE.equals(role)
                        ? "Abre el tablón todo NPC escrito en un panel, sin mirar su rol."
                        : "Solo un NPC con el rol " + role + " de Custom NPCs abre el panel donde esté escrito.";
            }
            if (key.equals("luckperms.lockWithoutBridge")) {
                boolean flag = toBool(value);
                put(BRIDGE, "lockWithoutBridge", flag);
                return flag ? "Sin plugin de permisos quedan cerrados todos los paneles." : "Sin plugin se usa el rango de respaldo.";
            }
            if (key.equals("luckperms.refreshSeconds")) {
                int seconds = toInt(value, refreshSeconds);
                if (seconds < 1 || seconds > 3600) return "La cache va de 1 a 3600 segundos.";
                put(BRIDGE, "refreshSeconds", seconds);
                return "El rango se releerá cada " + seconds + " s.";
            }
            if (key.equals("luckperms.announceOnLogin")) {
                boolean flag = toBool(value);
                put(BRIDGE, "announceOnLogin", flag);
                return flag ? "Se avisa del rango al entrar." : "Sin aviso al entrar.";
            }
            if (key.startsWith("gate.")) return applyGate(key, value);
        } catch (Throwable t) {
            RebornQuests.LOGGER.warn("RebornQuests: no se pudo guardar {}.", key, t);
            return "Fallo al guardar " + key + ": " + t.getClass().getSimpleName();
        }
        return "Ese ajuste no se puede cambiar desde el panel.";
    }

    /** The villages the pack starts with; every panel is filed under one of them. */
    private static final String[] CATEGORIES_DEFAULT = {"Kono", "Kiri", "Suna", "Iwa", "Kumo", "Uzu"};

    private static final String GROUPS_COMMENT =            "Grupos de LuckPerms del rango mas bajo al mas alto. El rango de un jugador es el grupo mas alto que tenga.";
    private static final String LABELS_COMMENT =
            "Nombre mostrable de cada grupo, en el mismo orden que groups.";

    /**
     * The ladder the pack ships with: eleven LuckPerms groups from academy student to Fūjin commander.
     * The group names are what LuckPerms has to hold; both lists are editable from the Rangos tab.
     */
    private static final String[] GROUPS_DEFAULT = {
        "estudiante_academia", "genin", "chunnin", "lider_chunnin", "jonin", "tokubetsu_jonin",
        "tokubetsu_jonin_elite", "anbu", "comandante_anbu", "fuerza_especial_fujin", "comandante_fujin"
    };
    private static final String[] LABELS_DEFAULT = {
        "Estudiante de academia", "Genin", "Chunnin", "Líder chunnin", "Jonin", "Tokubetsu Jōnin",
        "Tokubetsu Jōnin de Élite", "ANBU", "Comandante ANBU", "Fuerza Especial Fūjin", "Comandante Fūjin"
    };

    private void put(String section, String key, String value) {
        cfg.get(section, key, value).set(value);
        flush();
    }

    private void put(String section, String key, boolean value) {
        cfg.get(section, key, value).set(value);
        flush();
    }

    private void put(String section, String key, int value) {
        cfg.get(section, key, value).set(value);
        flush();
    }

    /** The file is the source of truth: after any write the in-memory copy is read back. */
    private void flush() {
        cfg.save();
        load();
    }

    private static boolean toBool(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("si") || v.equals("yes") || v.equals("on");
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<String> split(String value) {
        List<String> out = new ArrayList<String>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static List<String> readList(Property prop) {
        List<String> out = new ArrayList<String>();
        for (String value : prop.getStringList()) {
            if (value != null && !value.trim().isEmpty()) out.add(value.trim());
        }
        return out;
    }

    /** The HUD position as it is stored, or null when the staff typed something else. */
    private static String hudChoice(String value) {
        String wanted = fold(value);
        for (String option : HUD_MODES) {
            if (fold(option).equals(wanted)) return option;
        }
        return null;
    }

    /** Which place the client draws the clock in; 0 means the drawing is switched off. */
    public int hudModeId() {
        for (int i = 0; i < HUD_MODES.length; i++) {
            if (HUD_MODES[i].equalsIgnoreCase(hudMode)) return i;
        }
        return 5;
    }

    /** How the screen shows the current default village, where "-" is the stored "none". */
    private String villageChoice() {
        return villageDefault == null || villageDefault.trim().isEmpty() ? "-" : villageDefault.trim();
    }

    /**
     * Position of one difficulty in the ladder: D is 0, the easiest, and S is the last and the hardest.
     * Unknown levels read as the bottom so a typo can never unlock the top of the ladder.
     */
    public static int levelIndex(String value) {
        String wanted = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            if (DIFFICULTIES[i].equals(wanted)) return i;
        }
        return 0;
    }

    public static String levelAt(int index) {
        return DIFFICULTIES[Math.max(0, Math.min(DIFFICULTIES.length - 1, index))];
    }

    /** Position of one difficulty in the ladder, or -1 when it is not one of them at all. */
    public static int levelIndexOf(String value) {
        String wanted = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            if (DIFFICULTIES[i].equals(wanted)) return i;
        }
        return -1;
    }

    /** One step harder than {@code value}, wrapping back to D after S: what the cycle button shows. */
    public static String nextLevel(String value) {
        return levelAt((levelIndex(value) + 1) % DIFFICULTIES.length);
    }

    /** True when {@code have} is as hard as {@code want} or harder, which is how a gate counts. */
    public static boolean levelCovers(String have, String want) {
        return levelIndex(have) >= levelIndex(want);
    }

    /** Lowercase, without accents or spaces, so what the staff typed still matches the list. */
    private static String fold(String value) {
        String out = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return out.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o")
                .replace("ú", "u").replace("ñ", "n").replace(" ", "");
    }

    private static String cut(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    /** Index in rankGroups, or -1 when the group is not part of the ladder. */
    public int rankIndex(String group) {
        if (group == null) return -1;
        String needle = group.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < rankGroups.size(); i++) {
            if (rankGroups.get(i).toLowerCase(Locale.ROOT).equals(needle)) return i;
        }
        return -1;
    }

    /** Writes the LuckPerms group of one ladder step, which is what its own tab edits. */
    public String setRankGroup(int index, String value) {
        if (index < 0 || index >= rankGroups.size()) return "Ese rango ya no esta en la ladder.";
        String group = cut(value, 40).toLowerCase(Locale.ROOT);
        if (group.isEmpty() || group.contains(" ")) return "El grupo no puede quedar vacío ni llevar espacios.";
        int other = rankIndex(group);
        if (other >= 0 && other != index) return group + " ya es el rango " + (other + 1) + " de la ladder.";
        rankGroups.set(index, group);
        writeRanks();
        return "Rango " + (index + 1) + ": grupo " + group + ".";
    }

    /** Writes the name one ladder step shows. Empty means the group name in capitals. */
    public String setRankLabel(int index, String value) {
        if (index < 0 || index >= rankGroups.size()) return "Ese rango ya no esta en la ladder.";
        String label = cut(value, 40);
        while (rankLabels.size() < rankGroups.size()) rankLabels.add("");
        rankLabels.set(index, label);
        writeRanks();
        return label.isEmpty() ? "Rango " + (index + 1) + ": se muestra el nombre del grupo."
                : "Rango " + (index + 1) + ": " + label + ".";
    }

    /** Swaps a ladder step with its neighbour; its label travels with it. */
    public String moveRank(int index, int delta) {
        if (index < 0 || index >= rankGroups.size()) return "Ese rango ya no esta en la ladder.";
        int to = index + delta;
        if (to < 0 || to >= rankGroups.size()) return "Ese rango ya esta en el borde de la ladder.";
        String group = rankGroups.remove(index);
        rankGroups.add(to, group);
        String label = index < rankLabels.size() ? rankLabels.remove(index) : "";
        while (rankLabels.size() < to) rankLabels.add("");
        rankLabels.add(to, label);
        writeRanks();
        return group + " pasa al puesto " + (to + 1) + " de " + rankGroups.size() + ".";
    }

    private void writeRanks() {
        cfg.get(RANKS, "groups", GROUPS_DEFAULT, GROUPS_COMMENT)
                .set(rankGroups.toArray(new String[0]));
        cfg.get(RANKS, "labels", LABELS_DEFAULT, LABELS_COMMENT)
                .set(rankLabels.toArray(new String[0]));
        flush();
    }

    /**
     * Adds a ladder step after {@code after}, or on top of everything when {@code after} is past the end.
     * The group is what LuckPerms has to hold; the name it shows is optional.
     */
    public String addRank(String group, String label, int after) {
        if (rankGroups.size() >= 60) return "La ladder ya tiene 60 rangos.";
        String wanted = cut(group, 40).toLowerCase(Locale.ROOT).replace(' ', '_');
        if (wanted.isEmpty()) wanted = "rango_" + (rankGroups.size() + 1);
        if (wanted.contains(";") || wanted.contains(",")) {
            return "El rango nuevo necesita un grupo de LuckPerms sin ; ni coma.";
        }
        if (rankIndex(wanted) >= 0) return wanted + " ya esta en la ladder.";
        int at = after < 0 || after >= rankGroups.size() ? rankGroups.size() : after + 1;
        rankGroups.add(at, wanted);
        while (rankLabels.size() < rankGroups.size()) rankLabels.add("");
        String name = cut(label, 40);
        rankLabels.add(at, name.isEmpty() ? "Rango " + (at + 1) : name);
        writeRanks();
        return "Anadido " + rankLabel(at) + " (grupo " + wanted + ") en el puesto " + (at + 1) + ".";
    }

    /**
     * Takes one ladder step out. A panel that still asks for it is refused by name, so a delete can never
     * leave a board nobody can open; its per-rank mission lists go with it.
     */
    public String removeRank(int index) {
        if (index < 0 || index >= rankGroups.size()) return "Ese rango ya no esta en la ladder.";
        if (rankGroups.size() <= 1) return "La ladder necesita al menos un rango.";
        String group = rankGroups.get(index);
        List<String> users = new ArrayList<String>();
        for (PanelDef def : panels) {
            if (rankIndex(def.group) == index) users.add(def.name);
        }
        if (!users.isEmpty()) {
            return group + " todavia lo exigen " + String.join(", ", users) + ". Cambiales el rango antes.";
        }
        rankGroups.remove(index);
        if (index < rankLabels.size()) rankLabels.remove(index);
        dropRankMissions(group);
        writeRanks();
        String extra = rankIndex(noBridgeRank) < 0 ? " y " + rankGroups.get(0) + " pasa a ser el rango sin LuckPerms." : "";
        if (rankIndex(noBridgeRank) < 0) put(RANKS, "noBridgeRank", rankGroups.get(0));
        return "Rango " + group + " fuera de la ladder." + extra;
    }

    /** Every panel that still names one ladder group, used by the refuse message of {@link #removeRank}. */
    private void dropRankMissions(String group) {
        for (int i = panelRankMissions.size() - 1; i >= 0; i--) {
            String[] parts = panelRankMissions.get(i).split(";");
            if (parts.length > 1 && normalize(parts[1]).equals(normalize(group))) panelRankMissions.remove(i);
        }
    }

    public String rankLabel(int index) {
        if (index < 0 || index >= rankGroups.size()) return "sin rango";
        if (index < rankLabels.size() && !rankLabels.get(index).trim().isEmpty()) return rankLabels.get(index).trim();
        return rankGroups.get(index).toUpperCase(Locale.ROOT);
    }

    /** Highest configured ladder step, used when a rule names an unknown group. */
    public int fallbackRank() {
        return Math.max(0, rankGroups.size() - 1);
    }

    public Requirement requirement(String panel) {
        PanelDef def = panel(panel);
        if (def == null) return new Requirement(-1, 0, "");
        int index = rankIndex(def.group);
        if (index < 0 && !def.group.isEmpty() && !def.group.equals("-")) index = rankIndex(noBridgeRank);
        return new Requirement(index, def.minFinished, def.group);
    }

    /**
     * Difficulty, cooldown and time limit of one Custom NPCs quest. Reading never writes: a quest with
     * no line yet simply reports the defaults (level D, no cooldown, no limit).
     */
    public Mission mission(int questId) {
        String[] parts = entry(missionData, questId);
        Mission out = new Mission(questId);
        if (parts == null || parts.length < 2) return out;
        out.difficulty = difficulty(parts[1]);
        out.cooldownMin = parts.length > 2 ? clampMinutes(parseInt(parts[2], 0)) : 0;
        out.limitMin = parts.length > 3 ? clampMinutes(parseInt(parts[3], 0)) : 0;
        return out;
    }

    /** Every mission carrying a cooldown or a time limit, which is all the clock has to walk. */
    public List<Mission> timedMissions() {
        List<Mission> out = new ArrayList<Mission>();
        for (String raw : missionData) {
            String[] parts = raw.split(";");
            int id = parts.length > 0 ? parseInt(parts[0].trim(), -1) : -1;
            if (id <= 0) continue;
            Mission mission = mission(id);
            if (mission.cooldownMin > 0 || mission.limitMin > 0) out.add(mission);
        }
        return out;
    }

    public String setMissionDifficulty(int questId, String value) {
        Mission mission = mission(questId);
        mission.difficulty = difficulty(value);
        writeMission(mission);
        return "Mision " + questId + " pasa a Mision " + mission.difficulty + ".";
    }

    /**
     * Cooldown and limit accept a number of minutes or a word meaning "off": no, false, off, -, nunca,
     * disable(d), desactivar/desactivado, deshabilitar/deshabilitado. That is how the admin screen's
     * "sin limite" button clears one.
     */
    public String setMissionCooldown(int questId, String value) {
        Mission mission = mission(questId);
        mission.cooldownMin = minutes(value, mission.cooldownMin);
        writeMission(mission);
        return "Mision " + questId + " -> enfriamiento " + minutesText(mission.cooldownMin) + ".";
    }

    public String setMissionLimit(int questId, String value) {
        Mission mission = mission(questId);
        mission.limitMin = minutes(value, mission.limitMin);
        writeMission(mission);
        return "Mision " + questId + " -> tiempo limite " + minutesText(mission.limitMin) + ".";
    }

    /** Minutes from what the staff typed; a word meaning off is a 0, garbage keeps the old value. */
    private static int minutes(String value, int fallback) {
        String wanted = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) return fallback;
        if (wanted.equals("no") || wanted.equals("false") || wanted.equals("off") || wanted.equals("-")
                || wanted.equals("nunca") || wanted.equals("desactivar") || wanted.startsWith("disable")
                || wanted.startsWith("desactiv") || wanted.startsWith("deshabilit")) {
            return 0;
        }
        return clampMinutes(toInt(wanted, fallback));
    }

    private static String minutesText(int minutes) {
        if (minutes <= 0) return "sin limite";
        if (minutes < 60) return minutes + " min";
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    /** Cooldowns and limits are minutes, capped at a month so a typo cannot lock a mission forever. */
    private static int clampMinutes(int value) {
        return Math.max(0, Math.min(value, 43200));
    }

    /** Replaces (or appends) the line of one quest and writes the file. */
    private void writeMission(Mission mission) {
        for (int i = 0; i < missionData.size(); i++) {
            String[] parts = missionData.get(i).split(";");
            if (parts.length == 0 || parseInt(parts[0].trim(), -1) != mission.id) continue;
            missionData.set(i, mission.write());
            save();
            return;
        }
        missionData.add(mission.write());
        save();
    }

    /** The whole line of one quest, which is how the file stores it. */
    private String[] entry(List<String> list, int questId) {
        for (String raw : list) {
            String[] parts = raw.split(";");
            if (parts.length > 0 && parseInt(parts[0].trim(), -1) == questId) return parts;
        }
        return null;
    }

    /** Cuts a free-form level to the configured list, case and spaces aside; D when it matches nothing. */
    public static String difficulty(String value) {
        String wanted = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (String level : DIFFICULTIES) {
            if (level.equals(wanted)) return level;
        }
        return DIFFICULTIES[0];
    }

    // ---------------------------------------------------------------------------------------------
    // Gates of difficulty: how much of the lower levels a player needs to reach the next one
    // ---------------------------------------------------------------------------------------------

    /**
     * What one mission level asks for before it opens: {@code min} missions finished at {@code counted}
     * or harder. D is the bottom of the ladder and S the top, so a C gate normally counts D work.
     */
    public static final class Gate {
        public final String level;
        public final String counted;
        public final int min;

        Gate(String level, String counted, int min) {
            this.level = level;
            this.counted = counted;
            this.min = min;
        }
    }

    /** The gate of one level, or null when that level asks nothing. */
    public Gate gate(String level) {
        String wanted = difficulty(level);
        int at = levelIndexOf(wanted);
        for (String raw : missionGates) {
            String[] parts = raw.split(";");
            if (parts.length < 3 || !difficulty(parts[0]).equals(wanted)) continue;
            int min = Math.max(0, parseInt(parts[2], 0));
            if (min <= 0) continue;
            int counted = levelIndexOf(parts[1]);
            // A line counting its own level or a harder one is not a gate: the ramp only ever goes down.
            if (counted < 0 || counted >= at) continue;
            return new Gate(wanted, difficulty(parts[1]), min);
        }
        return null;
    }

    /** The gate of one level by its position in {@link #DIFFICULTIES}. */
    public Gate gate(int levelIndex) {
        return gate(levelAt(levelIndex));
    }

    /**
     * Writes (or clears, with a count of zero) the gate of one level. A level can never be gated by
     * itself: the top of the ladder would then need its own work first.
     */
    public String setGate(int levelIndex, int countedIndex, int min) {
        String level = levelAt(levelIndex);
        int want = Math.max(0, Math.min(min, 9999));
        // Checked before anything is taken out: a refusal must leave the gate that was already there.
        if (want > 0 && countedIndex >= levelIndex) {
            return "Mision " + level + " solo puede contar niveles mas faciles que ella. "
                    + "Cambia primero que se cuenta.";
        }
        for (int i = missionGates.size() - 1; i >= 0; i--) {
            String[] parts = missionGates.get(i).split(";");
            if (parts.length > 0 && difficulty(parts[0]).equals(level)) missionGates.remove(i);
        }
        if (want <= 0 || countedIndex < 0) {
            save();
            return "Mision " + level + " vuelve a estar abierta para cualquiera que la vea.";
        }
        String counted = levelAt(countedIndex);
        missionGates.add(level + ";" + counted + ";" + want);
        save();
        return "Mision " + level + " pide " + want + " terminadas de Mision " + counted + " o mas duras.";
    }

    /**
     * One knob of the Niveles tab. The other keeps whatever the level already asks, so the two controls
     * edit one gate without the staff having to set both at the same time.
     */
    private String applyGate(String key, String value) {
        String[] parts = key.split("\\.");
        if (parts.length != 3) return "Ese ajuste de nivel no existe.";
        int level = levelIndexOf(parts[1]);
        if (level < 0) return "Esa dificultad de mision no existe: " + parts[1];
        Gate current = gate(DIFFICULTIES[level]);
        int counted = gateCounted(level, current);
        int min = current == null ? 0 : current.min;
        if (parts[2].equals("counted")) {
            if (NO_GATE.equalsIgnoreCase(value.trim())) return setGate(level, -1, min);
            int wanted = levelIndexOf(value);
            if (wanted < 0) return "Esa dificultad no esta en la lista: " + value;
            if (wanted >= level) {
                return "Mision " + DIFFICULTIES[level] + " no puede contar Mision " + DIFFICULTIES[wanted]
                        + ": solo cuenta las mas faciles que ella.";
            }
            return setGate(level, wanted, min);
        }
        if (parts[2].equals("min")) {
            int want = toInt(value, min);
            if (want < 0 || want > 999) return "La cantidad va de 0 a 999.";
            if (want > 0 && counted < 0) {
                return level <= 0 ? "Mision D es la mas facil de la rampa: no hay nada mas duro que contar."
                        : "Mision " + DIFFICULTIES[level] + " esta sin requisito: elige primero que se cuenta.";
            }
            return setGate(level, counted, want);
        }
        return "Ese ajuste de nivel no existe.";
    }

    /** Every gate as configured, in ladder order, which is how the screen lists them. */
    public List<Gate> gates() {
        List<Gate> out = new ArrayList<Gate>();
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            Gate gate = gate(DIFFICULTIES[i]);
            if (gate != null) out.add(gate);
        }
        return out;
    }

    /** One line per level with its gate, in ladder order, for the state screen and the chat. */
    public String gatesText() {
        StringBuilder out = new StringBuilder();
        for (String level : DIFFICULTIES) {
            Gate gate = gate(level);
            if (out.length() > 0) out.append("  ·  ");
            out.append("Mision ").append(level).append(gate == null ? ": libre"
                    : ": " + gate.min + " de " + gate.counted + "+");
        }
        return out.toString();
    }

    /** Every LuckPerms node the panel demands, in the order they were added. All of them are required. */
    public List<String> nodes(String panel) {
        List<String> out = new ArrayList<String>();
        String[] parts = entry(panelNodes, panel);
        if (parts == null || parts.length < 2) return out;
        for (String node : parts[1].split(",")) {
            String trimmed = node.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** The Custom NPCs quest ids inside one panel. */
    public List<Integer> missions(String panel) {
        String[] parts = entry(panelMissions, panel);
        return ids(parts == null || parts.length < 2 ? null : parts[1]);
    }

    /** Written so a rank with no line of its own keeps showing the plain list of the panel. */
    public static final String ANY_VILLAGE = "*";

    /**
     * The missions one panel offers to one rank of one village: the rank's own list when it has one,
     * counting a village-wide line before a "any village" one, and the panel list otherwise.
     */
    public List<Integer> missions(String panel, int rank, String village) {
        int at = rankRow(panel, rank, village);
        return at < 0 ? missions(panel) : ids(field(at, 3));
    }

    /** True when that rank has its own list on this panel, which is the mode the screen shows. */
    public boolean hasRankList(String panel, int rank, String village) {
        return rankRow(panel, rank, village) >= 0;
    }

    /**
     * Everything one rank of one village can actually take on this panel: its own list and the lists of
     * the ranks below it, down as far as {@link #rankDepth} allows. A rank with no list of its own stands
     * on the plain panel list, so a pack that never touched the per-rank rows behaves as before. The walk
     * only goes down, so a genin never gets the missions of a chunnin.
     */
    public List<Integer> missionsFor(String panel, int rank, String village) {
        List<Integer> out = new ArrayList<Integer>();
        if (rank < 0 || rank >= rankGroups.size()) return unique(out, missions(panel));
        for (int at = rank; at >= rankFloor(rank); at--) {
            int row = rankRow(panel, at, village);
            unique(out, row < 0 ? missions(panel) : ids(field(row, 3)));
        }
        return out;
    }

    /** The rank the walk stops at, which is what the screen names in its own hint. */
    public int rankFloor(int rank) {
        return Math.max(0, rank - Math.min(Math.max(0, rankDepth), rankDepthCap()));
    }

    /**
     * The depth the ladder can actually ask for: at this number every rank of the pack is already below
     * the highest one, so a bigger value would mean the same thing. Typing more than this in the config
     * simply puts the whole ladder within reach of the ranks on top of it.
     */
    public int rankDepthCap() {
        return Math.max(1, Math.min(RANK_DEPTH_MAX, rankGroups.size()));
    }

    /** Appends the ids that are not in the list yet, in the order they were read. */
    private static List<Integer> unique(List<Integer> out, List<Integer> more) {
        for (Integer id : more) {
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    /** The row of {@link #panelRankMissions} one rank of one panel reads, or -1 when the panel list stands. */
    private int rankRow(String panel, int rank, String village) {
        if (rank < 0 || rank >= rankGroups.size()) return -1;
        String group = normalize(rankGroups.get(rank));
        String wanted = normalize(village);
        int any = -1;
        for (int i = 0; i < panelRankMissions.size(); i++) {
            String[] parts = panelRankMissions.get(i).split(";");
            if (parts.length < 4) continue;
            if (!normalize(parts[0]).equals(normalize(panel))) continue;
            if (!normalize(parts[1]).equals(group)) continue;
            if (normalize(parts[2]).equals(wanted)) return i;
            if (any < 0 && normalize(parts[2]).equals(ANY_VILLAGE)) any = i;
        }
        return any;
    }

    /** One field of one stored rank row, or null when the row is shorter than that. */
    private String field(int row, int index) {
        if (row < 0 || row >= panelRankMissions.size()) return null;
        String[] parts = panelRankMissions.get(row).split(";");
        return parts.length > index ? parts[index] : null;
    }

    /** Quest ids out of one "id,id,..." field; unknown ids are dropped. */
    private static List<Integer> ids(String field) {
        List<Integer> out = new ArrayList<Integer>();
        if (field == null) return out;
        for (String id : field.split(",")) {
            int questId = parseInt(id.trim(), -1);
            if (questId > 0 && !out.contains(Integer.valueOf(questId))) out.add(Integer.valueOf(questId));
        }
        return out;
    }

    /** The panel holding that quest, or null when no panel lists it. */
    public String panelOfQuest(int questId) {
        for (PanelDef def : panels) {
            if (missionsAll(def.name).contains(Integer.valueOf(questId))) return def.name;
        }
        return null;
    }

    /**
     * Every quest a panel holds for anybody: its own list plus all the per-rank rows written for it. The
     * staff screen works on this, so a mission a lower rank owns is never shown as if it were missing.
     */
    public List<Integer> missionsAll(String panel) {
        List<Integer> out = new ArrayList<Integer>(missions(panel));
        for (int i = 0; i < panelRankMissions.size(); i++) {
            String[] parts = panelRankMissions.get(i).split(";");
            if (parts.length < 4) continue;
            if (!normalize(parts[0]).equals(normalize(panel))) continue;
            unique(out, ids(parts[3]));
        }
        return out;
    }

    /** Adds a mission to the plain list of the panel. */
    public String addMission(String panel, int questId) {
        return addMission(panel, questId, -1, "");
    }

    public String removeMission(String panel, int questId) {
        return removeMission(panel, questId, -1, "");
    }

    /**
     * Adds a mission where the screen is pointed: with a rank of -1 the panel list itself, and with a rank
     * the list that rank sees. A rank with no list of its own starts from the panel list, so adding the
     * first mission to it does not leave the rest behind.
     */
    public String addMission(String panel, int questId, int rank, String village) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        if (questId <= 0) return "El id de misión tiene que ser un número mayor que 0.";
        if (rank >= rankGroups.size()) return "Ese rango no está en la ladder.";
        List<Integer> ids = rank < 0 ? missions(def.name) : missions(def.name, rank, village);
        if (ids.contains(Integer.valueOf(questId))) {
            return "Esa misión ya está en " + def.name + where(rank, village) + ".";
        }
        ids.add(Integer.valueOf(questId));
        writeMissions(def.name, rank, village, ids);
        save();
        return def.name + where(rank, village) + " ahora tiene " + ids.size() + " misiones.";
    }

    public String removeMission(String panel, int questId, int rank, String village) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        if (rank >= rankGroups.size()) return "Ese rango no está en la ladder.";
        List<Integer> ids = rank < 0 ? missions(def.name) : missions(def.name, rank, village);
        if (!ids.remove(Integer.valueOf(questId))) {
            return "Esa misión no está en " + def.name + where(rank, village) + ".";
        }
        writeMissions(def.name, rank, village, ids);
        save();
        return "Misión " + questId + " quitada de " + def.name + where(rank, village) + ".";
    }

    /**
     * Puts one rank's list back onto the panel's own list, so a village stops carrying a special set for
     * that rank without anybody having to edit the file.
     */
    public String clearRankList(String panel, int rank, String village) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        int at = rankRow(def.name, rank, village);
        if (at < 0) return def.name + " ya usa su lista normal para ese rango.";
        panelRankMissions.remove(at);
        save();
        return def.name + " vuelve a su lista normal" + where(rank, village) + ".";
    }

    /** Writes the ids where they belong: the panel list, or one rank's own line. */
    private void writeMissions(String panel, int rank, String village, List<Integer> ids) {
        if (rank < 0 || rank >= rankGroups.size()) {
            writeList(panelMissions, panel, joinIds(ids));
            return;
        }
        int at = rankRow(panel, rank, village);
        String group = rankGroups.get(rank);
        String stored = at < 0 ? cleanVillage(village) : cut(field(at, 2), 40);
        String line = panel + ";" + group + ";" + stored + ";" + joinIds(ids);
        if (at < 0) panelRankMissions.add(line);
        else panelRankMissions.set(at, line);
    }

    /** The village a row is stored under, or the "any village" mark when the screen named none. */
    private static String cleanVillage(String village) {
        return village == null || village.trim().isEmpty() ? ANY_VILLAGE : cleanCategory(village);
    }

    /** " (rango ANBU de Kono)", which is how the screen's answers say where a change landed. */
    private String where(int rank, String village) {
        if (rank < 0) return "";
        String out = " para " + rankLabel(rank);
        if (village != null && !village.trim().isEmpty()) out += " de " + village.trim();
        return out;
    }

    /** Adds one required permission; a ladder group is accepted and read as its group.<grupo> node. */
    public String addNode(String panel, String node) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        String wanted = node == null ? "" : node.trim();
        if (wanted.isEmpty() || wanted.contains(";") || wanted.contains(",")) {
            return "El permiso no puede quedar vacío ni llevar ; o coma.";
        }
        List<String> nodes = nodes(def.name);
        if (nodes.contains(wanted)) return def.name + " ya exigía " + wanted + ".";
        nodes.add(wanted);
        writeList(panelNodes, def.name, join(nodes, ","));
        save();
        return def.name + " exige " + nodes.size() + (nodes.size() == 1 ? " permiso extra." : " permisos extra.");
    }

    public String removeNode(String panel, String node) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        List<String> nodes = nodes(def.name);
        if (!nodes.remove(node == null ? "" : node.trim())) return "Ese permiso no estaba en " + def.name + ".";
        writeList(panelNodes, def.name, join(nodes, ","));
        save();
        return nodes.isEmpty() ? def.name + " vuelve a no exigir permisos extra."
                : def.name + " se queda con " + nodes.size() + " permisos.";
    }

    /** The players this panel lets in by name, in the order the staff wrote them. */
    public List<String> grants(String panel) {
        List<String> out = new ArrayList<String>();
        String[] parts = entry(panelGrants, panel);
        if (parts == null || parts.length < 2) return out;
        for (String name : parts[1].split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** True when this exact player was let into this exact panel, whatever his rank or nodes say. */
    public boolean isGranted(String panel, String player) {
        if (player == null || player.isEmpty()) return false;
        for (String name : grants(panel)) {
            if (name.equalsIgnoreCase(player)) return true;
        }
        return false;
    }

    /** The panels one player was let into by name, which is what «acceso listar» answers with. */
    public List<String> grantedPanels(String player) {
        List<String> out = new ArrayList<String>();
        for (PanelDef def : panels) {
            if (isGranted(def.name, player)) out.add(def.name);
        }
        return out;
    }

    /**
     * Opens one panel for one named player without touching LuckPerms: no group, no node, nothing that
     * could be read as a promotion. The rank ladder and the permissions of the panel stay exactly as they
     * are for everybody else.
     */
    public String grant(String panel, String player) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        String wanted = cleanGrant(player);
        if (wanted == null) return "Escribe un nombre de jugador sin ; ni coma.";
        List<String> names = grants(def.name);
        if (containsName(names, wanted)) return wanted + " ya podía abrir " + def.name + ".";
        names.add(wanted);
        writeList(panelGrants, def.name, join(names, ","));
        save();
        return wanted + " abre " + def.name + " sin necesitar rango ni permisos (" + names.size() + " con acceso).";
    }

    public String ungrant(String panel, String player) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        String wanted = player == null ? "" : player.trim();
        List<String> names = grants(def.name);
        if (!containsName(names, wanted)) return wanted + " no tenía acceso a " + def.name + ".";
        for (int i = names.size() - 1; i >= 0; i--) {
            if (names.get(i).equalsIgnoreCase(wanted)) names.remove(i);
        }
        writeList(panelGrants, def.name, join(names, ","));
        save();
        return names.isEmpty() ? def.name + " vuelve a no tener accesos sueltos."
                : def.name + " se queda con " + names.size() + " accesos sueltos.";
    }

    /** A name is what Minecraft calls it: no separators, not empty, not longer than an account. */
    private static String cleanGrant(String player) {
        String wanted = player == null ? "" : player.trim();
        if (wanted.isEmpty() || wanted.contains(";") || wanted.contains(",") || wanted.length() > 16) return null;
        return wanted;
    }

    private static boolean containsName(List<String> names, String wanted) {
        for (String name : names) {
            if (name.equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    public String createPanel(String name) {
        String wanted = cleanName(name);
        if (wanted == null) return EMPTY_NAME;
        if (has(wanted)) return "Ya existe un panel llamado " + wanted + ".";
        PanelDef panel = new PanelDef();
        panel.name = wanted;
        panel.group = "-";
        panels.add(panel);
        save();
        return "Panel " + wanted + " creado. Ponle aldea en Paneles y misiones en la pestaña Misiones.";
    }

    public String deletePanel(String name) {
        String wanted = cleanName(name);
        if (wanted == null) return EMPTY_NAME;
        if (!has(wanted)) return "No hay ningún panel llamado " + wanted + ".";
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).name.equalsIgnoreCase(wanted)) panels.remove(i);
        }
        drop(panelNodes, wanted);
        drop(panelMissions, wanted);
        drop(panelGrants, wanted);
        drop(panelNpcs, wanted);
        save();
        return "Panel " + wanted + " borrado. Sus misiones siguen en Custom NPCs.";
    }

    /** A panel is its own name, so renaming moves the permissions and the missions that mention it. */
    public String renamePanel(String from, String to) {
        String oldName = cleanName(from);
        if (oldName == null) return EMPTY_NAME;
        String newName = cleanName(to);
        if (newName == null) return EMPTY_NAME;
        PanelDef def = panel(oldName);
        if (def == null) return "No hay ningún panel llamado " + oldName + ".";
        if (has(newName)) return "Ya existe un panel llamado " + newName + ".";
        def.name = newName;
        move(panelNodes, oldName, newName);
        move(panelMissions, oldName, newName);
        move(panelGrants, oldName, newName);
        move(panelNpcs, oldName, newName);
        save();
        return oldName + " pasa a llamarse " + newName + ".";
    }

    public String setRequirement(String panel, String group, int minDone) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        boolean noRank = group == null || group.trim().isEmpty() || group.trim().equals("-");
        def.group = noRank ? "-" : group.trim();
        def.minFinished = Math.max(0, minDone);
        save();
        return def.name + " -> rango " + (noRank ? "sin exigir" : rankLabel(rankIndex(def.group)))
                + ", minimas " + def.minFinished + ".";
    }

    /**
     * Files a panel under a village. A village nobody has used yet is added to the list, so the admin
     * screen can type a new one without editing the config by hand.
     */
    public String setCategory(String panel, String value) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        String village = cleanCategory(value);
        def.category = village;
        if (!village.isEmpty() && !panelCategories.contains(village)) {
            panelCategories.add(village);
        }
        save();
        return def.name + (village.isEmpty() ? " ya no pertenece a ninguna aldea." : " es de " + village + ".");
    }

    /** The village of one panel, or "" when it belongs to none. */
    public String categoryOf(String panel) {
        PanelDef def = panel(panel);
        return def == null ? "" : def.category;
    }

    /** Every village the board knows: the configured list plus whatever a panel still uses. */
    public List<String> categories() {
        List<String> out = new ArrayList<String>(panelCategories);
        for (PanelDef def : panels) {
            if (!def.category.isEmpty() && !out.contains(def.category)) out.add(def.category);
        }
        return out;
    }

    /** The panels of one village, in board order. Empty or "-" means the unfiled group. */
    public List<String> panelsIn(String category) {
        String wanted = cleanCategory(category);
        List<String> out = new ArrayList<String>();
        for (PanelDef def : panels) {
            if (def.category.equals(wanted)) out.add(def.name);
        }
        return out;
    }

    /** Reorders the list so each village is together, keeping the order inside every village. */
    public String groupByCategory() {
        List<String> known = categories();
        known.add("");
        List<PanelDef> ordered = new ArrayList<PanelDef>();
        for (String village : known) {
            for (PanelDef def : panels) {
                if (def.category.equals(village)) ordered.add(def);
            }
        }
        panels.clear();
        panels.addAll(ordered);
        save();
        return "Paneles agrupados por aldea.";
    }

    /** Moves a panel one slot up (-1) or down (+1) in the board order. */
    public String movePanel(String panel, int delta) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        int from = panels.indexOf(def);
        int to = Math.max(0, Math.min(panels.size() - 1, from + delta));
        if (to == from) return def.name + " ya esta en el borde de la lista.";
        panels.remove(from);
        panels.add(to, def);
        save();
        return def.name + " pasa al puesto " + (to + 1) + " de " + panels.size() + ".";
    }

    /** Moves a panel to an absolute slot, which is what the command uses. */
    public String movePanelTo(String panel, int index) {
        PanelDef def = panel(panel);
        if (def == null) return "Ese panel no existe.";
        int from = panels.indexOf(def);
        int to = Math.max(0, Math.min(panels.size() - 1, index));
        if (to == from) return def.name + " ya esta en el puesto " + (to + 1) + ".";
        panels.remove(from);
        panels.add(to, def);
        save();
        return def.name + " pasa al puesto " + (to + 1) + " de " + panels.size() + ".";
    }

    /** Panel names in config order; the board and the admin table both follow it. */
    public List<String> panels() {
        List<String> out = new ArrayList<String>();
        for (PanelDef panel : panels) out.add(panel.name);
        return out;
    }

    /** The definition of one panel, matched without case or surrounding spaces. */
    public PanelDef panel(String name) {
        String wanted = name == null ? "" : name.trim();
        for (PanelDef panel : panels) {
            if (panel.name.equalsIgnoreCase(wanted)) return panel;
        }
        return null;
    }

    public boolean has(String name) {
        return panel(name) != null;
    }

    /** Trimmed name, or null when it is empty and therefore unusable as a panel name. */
    private static String cleanName(String value) {
        String wanted = value == null ? "" : value.trim().replace(";", "");
        if (wanted.isEmpty()) return null;
        return wanted.length() > 40 ? wanted.substring(0, 40) : wanted;
    }

    private static final String EMPTY_NAME = "El panel necesita un nombre (hasta 40 caracteres).";

    /** Village name trimmed; "-" and empty both mean "not filed under any village". */
    private static String cleanCategory(String value) {
        String wanted = value == null ? "" : value.trim().replace(";", "").replace(",", "");
        if (wanted.isEmpty() || wanted.equals("-")) return "";
        return wanted.length() > 24 ? wanted.substring(0, 24) : wanted;
    }

    /** The whole entry of one panel in a per-panel string list, or null when it is not listed. */
    private static String[] entry(List<String> list, String panel) {
        String wanted = normalize(panel);
        for (String raw : list) {
            String[] parts = raw.split(";");
            if (parts.length > 0 && normalize(parts[0]).equals(wanted)) return parts;
        }
        return null;
    }

    /** Replaces (or appends) the second field of one panel's entry. */
    private static void writeList(List<String> list, String panel, String value) {
        for (int i = 0; i < list.size(); i++) {
            String[] parts = list.get(i).split(";");
            if (parts.length == 0 || !normalize(parts[0]).equals(normalize(panel))) continue;
            list.set(i, parts[0].trim() + ";" + value);
            return;
        }
        list.add(panel.trim() + ";" + value);
    }

    private static void move(List<String> list, String from, String to) {
        for (int i = 0; i < list.size(); i++) {
            String[] parts = list.get(i).split(";");
            if (parts.length == 0 || !normalize(parts[0]).equals(normalize(from))) continue;
            StringBuilder rebuilt = new StringBuilder(to);
            for (int p = 1; p < parts.length; p++) rebuilt.append(';').append(parts[p]);
            list.set(i, rebuilt.toString());
        }
    }

    private static String joinIds(List<Integer> ids) {
        StringBuilder out = new StringBuilder();
        for (Integer id : ids) {
            if (out.length() > 0) out.append(',');
            out.append(id.intValue());
        }
        return out.toString();
    }

    private static void drop(List<String> list, String panel) {
        String wanted = normalize(panel);
        for (int i = list.size() - 1; i >= 0; i--) {
            String[] parts = list.get(i).split(";");
            if (parts.length > 0 && normalize(parts[0]).equals(wanted)) list.remove(i);
        }
    }

    /** One panel of the mod: its own name, its gate and the category it is filed under. */
    public static final class PanelDef {
        public String name = "";
        public String group = "-";
        public int minFinished;
        /** Free-text folder the board groups panels under. Empty means {@link #NO_CATEGORY}. */
        public String category = "";
    }

    /** Difficulty, cooldown and time limit of one Custom NPCs quest, in minutes. */
    public static final class Mission {
        public final int id;
        public String difficulty = DIFFICULTIES[0];
        public int cooldownMin;
        public int limitMin;

        Mission(int id) {
            this.id = id;
        }

        /** "<id>;<nivel>;<cooldown>;<limite>", which is how {@code paneles.data} stores it. */
        String write() {
            return id + ";" + difficulty + ";" + cooldownMin + ";" + limitMin;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Required ladder step (-1 = any rank or none) plus the mission count gate. */
    public static final class Requirement {
        public final int rankIndex;
        public final int minFinished;
        public final String group;

        Requirement(int rankIndex, int minFinished, String group) {
            this.rankIndex = rankIndex;
            this.minFinished = minFinished;
            this.group = group;
        }

        public boolean needsRank() {
            return rankIndex >= 0;
        }
    }
}
