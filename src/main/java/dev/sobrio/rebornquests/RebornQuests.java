package dev.sobrio.rebornquests;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = RebornQuests.MODID, name = RebornQuests.NAME, version = "@VERSION@",
        acceptedMinecraftVersions = "[1.12.2]")
public class RebornQuests {
    public static final String MODID = "rebornquests";
    public static final String NAME = "RebornQuests";

    public static Logger LOGGER = LogManager.getLogger(NAME);
    public static SimpleNetworkWrapper NET;
    public static RQConfig config;
    /** The mod's config file, kept so the client can put its own clock placement next to it. */
    public static java.io.File configFile;
    /** Held so a rename from the admin screen can take it out of the command map and put it back. */
    public static CommandRebornMisiones command;
    private static String version;
    /** The reader of Custom NPCs' own files, which is what puts a script or quest edit in the record. */
    public static final CnpcWatch WATCH = new CnpcWatch();
    /** The same record for the data Custom NPCs keeps on the NPCs themselves, which is not in any file. */
    public static final NpcAudit NPCS = new NpcAudit();

    @SidedProxy(clientSide = "dev.sobrio.rebornquests.client.ClientProxy",
            serverSide = "dev.sobrio.rebornquests.CommonProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = event.getSuggestedConfigurationFile();
        // The clocks and the record are put in place before the config is read: a migration writes its line
        // into the record, and by then the record has to know which file it is.
        MissionStore.init(configFile);
        AuditStore.init(configFile);
        config = new RQConfig(configFile);
        config.load();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NET.registerMessage(MessageBoard.Handler.class, MessageBoard.class, 0, net.minecraftforge.fml.relauncher.Side.CLIENT);
        NET.registerMessage(MessageAction.Handler.class, MessageAction.class, 1, net.minecraftforge.fml.relauncher.Side.SERVER);
        NET.registerMessage(AdminAction.Handler.class, AdminAction.class, 2, net.minecraftforge.fml.relauncher.Side.SERVER);
        NET.registerMessage(MessageTime.Handler.class, MessageTime.class, 3, net.minecraftforge.fml.relauncher.Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(new MissionEvents());
        proxy.registerHud();
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        command = new CommandRebornMisiones();
        event.registerServerCommand(command);
        MissionStore.purge();
        RankService.warmUp();
        // By now every mod of the pack is initialised, which is when the API of Custom NPCs answers, and the
        // rules of the board have to be on the bus of that mod to hold for its own dialogues and scripts.
        QuestGuards.register();
    }

    @EventHandler
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        RankService.clear();
    }

    /**
     * The version this build was made with, taken from the mod annotation, which the build writes in. Empty
     * when the jar still carries the token instead of a number: the config then stamps its revision alone.
     */
    public static String buildVersion() {
        if (version != null) return version;
        Mod annotation = RebornQuests.class.getAnnotation(Mod.class);
        String value = annotation == null ? "" : annotation.version();
        version = value == null || value.indexOf('@') >= 0 ? "" : value.trim();
        return version;
    }

    /**
     * Renaming the command or its aliases from the admin screen must not need a restart. In 1.12.2 the
     * command map holds one entry per name and per alias, all pointing at the same instance, so the
     * instance is taken out by every key it answers to and registered again with its new names.
     */
    public static void refreshCommand() {
        if (command == null) return;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        net.minecraft.command.ICommandManager manager = server.getCommandManager();
        if (!(manager instanceof net.minecraft.command.CommandHandler)) return;
        net.minecraft.command.CommandHandler handler = (net.minecraft.command.CommandHandler) manager;
        java.util.Iterator<java.util.Map.Entry<String, net.minecraft.command.ICommand>> entries =
                handler.getCommands().entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue() == command) entries.remove();
        }
        handler.registerCommand(command);
    }
}
