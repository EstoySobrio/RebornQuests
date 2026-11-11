package dev.sobrio.rebornquests;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Read-only bridge to the permission plugin. LuckPerms is the target, but the ladder is also read
 * through plain Bukkit permissions so any plugin works, which is what a hybrid plugin+mod server exposes to mods.
 *
 * <p>The rank ladder lives in LuckPerms itself: a player's rank is the highest configured group they
 * hold. Nothing here ever writes a permission; promotion is an admin action outside this mod.</p>
 */
public final class RankService {
    private static final Map<UUID, Entry> CACHE = new HashMap<UUID, Entry>();

    private static Object lpUserManager;
    private static Method lpGetUser;
    private static Method lpLoadUser;
    private static Method lpPrimaryGroup;
    private static Method lpCachedData;
    private static Method lpPermissionData;
    private static Method lpCheck;
    private static Method lpAsBoolean;

    private static Class<?> bukkitClass;
    private static Object pluginManager;
    private static Method bukkitGetPlayer;
    private static Method bukkitGetOffline;

    private static long nextProbe;
    private static String bridgeName = "ninguno";
    private static String lastError = "";

    private static final class Entry {
        final int rankIndex;
        final String group;
        final long expires;

        Entry(int rankIndex, String group, long expires) {
            this.rankIndex = rankIndex;
            this.group = group;
            this.expires = expires;
        }
    }

    private RankService() {}

    /** Called from the server thread on every query; probes at most once every five seconds. */
    public static synchronized void probe() {
        if (lpUserManager != null || pluginManager != null) return;
        long now = System.currentTimeMillis();
        if (now < nextProbe) return;
        nextProbe = now + 5000L;
        try {
            bukkitClass = Class.forName("org.bukkit.Bukkit");
            pluginManager = invoke(null, method(bukkitClass, "getPluginManager"));
            Object luckPerms = invoke(pluginManager, method(pluginManager.getClass(), "getPlugin", String.class), "LuckPerms");
            if (luckPerms != null) wireLuckPerms(luckPerms);
            bukkitGetPlayer = optional(bukkitClass, "getPlayer", UUID.class);
            bukkitGetOffline = optional(bukkitClass, "getOfflinePlayer", UUID.class);
            if (bridgeName.equals("ninguno") && (bukkitGetPlayer != null || bukkitGetOffline != null)) bridgeName = "Bukkit";
            if (!bridgeName.equals("ninguno")) {
                RebornQuests.LOGGER.info("RebornQuests: rango resuelto por {}.", bridgeName);
            }
        } catch (Throwable t) {
            lastError = describe(t);
            pluginManager = null;
            lpUserManager = null;
        }
    }

    /**
     * LuckPerms 5.x hands out its api through LuckPermsProvider, and the Bukkit plugin that a hybrid
     * server sees is only a jar-in-jar loader, so both the accessor and the api class have to come from the
     * plugin's own classloader.
     */
    private static void wireLuckPerms(Object luckPerms) {
        Object api = null;
        Method getApi = optional(luckPerms.getClass(), "getApi");
        if (getApi != null) api = invoke(luckPerms, getApi);
        if (api == null) {
            try {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider", true, luckPerms.getClass().getClassLoader());
                api = invoke(null, provider.getMethod("get"));
            } catch (Throwable t) {
                lastError = describe(t);
            }
        }
        if (api == null) return;
        lpUserManager = invoke(api, optional(api.getClass(), "getUserManager"));
        if (lpUserManager == null) return;
        lpGetUser = optional(lpUserManager.getClass(), "getUser", UUID.class);
        lpLoadUser = optional(lpUserManager.getClass(), "loadUser", UUID.class);
        bridgeName = "LuckPerms";
    }

    public static synchronized void warmUp() {
        probe();
        RebornQuests.LOGGER.info("RebornQuests: puente de permisos = {}.", bridgeName);
        if (!live()) {
            RebornQuests.LOGGER.warn("RebornQuests: LuckPerms no respondio por su API (puente={}, error={}). "
                    + "El rango se lee entonces con los nodos group.<grupo> de Bukkit.", bridgeName, lastError);
        }
    }

    public static String bridge() {
        probe();
        return bridgeName;
    }

    public static String lastError() {
        return lastError;
    }

    /** LuckPerms answered at least once, so groups and nodes come straight from its cache. */
    public static boolean live() {
        probe();
        return lpUserManager != null;
    }

    /** A hybrid plugin+mod server (or any Bukkit server) is up, even if LuckPerms itself is missing. */
    public static boolean hasBukkit() {
        probe();
        return pluginManager != null;
    }

    public static void invalidate(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static Rank rank(EntityPlayerMP player) {
        probe();
        UUID uuid = player.getUniqueID();
        long now = System.currentTimeMillis();
        Entry cached = CACHE.get(uuid);
        if (cached != null && cached.expires > now) return new Rank(cached.rankIndex, cached.group);

        RQConfig config = RebornQuests.config;
        int index = -1;
        String group = "";
        boolean loaded = true;
        if (live()) {
            Object user = resolveUser(uuid);
            loaded = user != null;
            if (user != null) {
                String primary = primaryGroup(user);
                index = config.rankIndex(primary);
                // The primary group is only his rank when the extra node says so too; requireRankNode is a
                // gate on the rank, not on the way the group was found, and before this the primary group
                // walked straight past it.
                if (index >= 0 && config.requireRankNode && !has(player, rankNode(primary))) index = -1;
                if (index < 0) index = highestGroup(player, uuid, config);
                if (index >= 0) group = config.rankGroups.get(index);
            }
        } else if (hasBukkit()) {
            // Another permission plugin: the same group.<x> nodes are what it answers with.
            index = highestGroup(player, uuid, config);
            if (index >= 0) group = config.rankGroups.get(index);
        } else if (!config.lockWithoutBridge) {
            // No Bukkit at all (singleplayer, or a plain Forge test server): one preset rank.
            int fallback = config.rankIndex(config.noBridgeRank);
            if (fallback >= 0) {
                index = fallback;
                group = config.noBridgeRank;
            }
        }
        // LuckPerms has not finished loading this user yet: retry soon instead of caching "no rank".
        CACHE.put(uuid, new Entry(index, group, System.currentTimeMillis() + (loaded ? config.refreshSeconds * 1000L : 1000L)));
        return new Rank(index, group);
    }

    private static int highestGroup(EntityPlayerMP player, UUID uuid, RQConfig config) {
        Object user = live() ? resolveUser(uuid) : null;
        for (int i = config.rankGroups.size() - 1; i >= 0; i--) {
            String candidate = config.rankGroups.get(i);
            String node = "group." + candidate.toLowerCase(Locale.ROOT);
            boolean member = user != null ? allowed(user, node) : bukkitHas(player, node);
            if (!member) continue;
            if (config.requireRankNode && !has(player, rankNode(candidate))) continue;
            return i;
        }
        return -1;
    }

    /** The extra node one group of the ladder needs when the staff asked for a node per rank. */
    private static String rankNode(String group) {
        return "rebornmisiones.rango." + (group == null ? "" : group.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Permission check used by the command gates and by the per-panel node option. With no plugin to ask,
     * {@link #localAccess} answers for it: the op list of the server, or the owner of a local world.
     */
    public static boolean has(EntityPlayerMP player, String node) {
        if (node == null || node.trim().isEmpty()) return true;
        probe();
        if (!hasBukkit()) return localAccess(player);
        if (live()) {
            Object user = resolveUser(player.getUniqueID());
            if (user != null && allowed(user, node)) return true;
        }
        return bukkitHas(player, node);
    }

    /** Singleplayer and plain-Forge worlds have no plugin to ask, so the server's own op flag answers. */
    private static boolean localAccess(EntityPlayerMP player) {
        return isOp(player) || isLocalOwner(player);
    }

    /**
     * The op level the server itself wrote for this player, which is the ops list and nothing else: a LAN
     * world opened with cheats does not put a guest in it, and that is exactly who must not get in.
     */
    public static int opLevel(EntityPlayerMP player) {
        try {
            return player.server.getPlayerList().getOppedPlayers().getPermissionLevel(player.getGameProfile());
        } catch (Throwable t) {
            lastError = describe(t);
            return 0;
        }
    }

    /** Whether the server has this player op'd at least as high as the mod asks for without a plugin. */
    public static boolean isOp(EntityPlayerMP player) {
        return opLevel(player) >= RebornQuests.config.opWithoutBridge;
    }

    /**
     * The person the local world belongs to. A singleplayer save with cheats off is not on the ops list, and
     * locking its own owner out of his pack would make the mod unusable to test; a player who joined over
     * LAN carries another name and is not him.
     */
    public static boolean isLocalOwner(EntityPlayerMP player) {
        try {
            return player.server.isSinglePlayer()
                    && player.getName().equalsIgnoreCase(player.server.getServerOwner());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean bukkitHas(EntityPlayerMP player, String node) {
        if (bukkitGetPlayer == null && bukkitGetOffline == null) return false;
        try {
            Object sender = bukkitGetPlayer != null ? invoke(null, bukkitGetPlayer, player.getUniqueID()) : null;
            if (sender == null && bukkitGetOffline != null) sender = invoke(null, bukkitGetOffline, player.getUniqueID());
            if (sender == null) return false;
            Method has = optional(sender.getClass(), "hasPermission", String.class);
            return has != null && Boolean.TRUE.equals(invoke(sender, has, node));
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    private static Object resolveUser(UUID uuid) {
        try {
            if (lpGetUser == null) return null;
            Object user = invoke(lpUserManager, lpGetUser, uuid);
            if (user == null && lpLoadUser != null) invoke(lpUserManager, lpLoadUser, uuid);
            return user;
        } catch (Throwable t) {
            lastError = describe(t);
            return null;
        }
    }

    private static String primaryGroup(Object user) {
        try {
            if (lpPrimaryGroup == null) lpPrimaryGroup = optional(user.getClass(), "getPrimaryGroup");
            if (lpPrimaryGroup == null) return "";
            Object value = invoke(user, lpPrimaryGroup);
            return value == null ? "" : value.toString();
        } catch (Throwable t) {
            lastError = describe(t);
            return "";
        }
    }

    private static boolean allowed(Object user, String node) {
        try {
            if (lpCachedData == null) lpCachedData = optional(user.getClass(), "getCachedData");
            if (lpCachedData == null) return false;
            Object cache = invoke(user, lpCachedData);
            if (cache == null) return false;
            if (lpPermissionData == null) lpPermissionData = optional(cache.getClass(), "getPermissionData");
            if (lpPermissionData == null) return false;
            Object data = invoke(cache, lpPermissionData);
            if (data == null) return false;
            if (lpCheck == null) lpCheck = optional(data.getClass(), "checkPermission", String.class);
            if (lpCheck == null) return false;
            Object result = invoke(data, lpCheck, node);
            if (result == null) return false;
            if (lpAsBoolean == null) lpAsBoolean = optional(result.getClass(), "asBoolean");
            if (lpAsBoolean == null) return false;
            return Boolean.TRUE.equals(invoke(result, lpAsBoolean));
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    /** The api classes live in LuckPerms' own loader, so every handle needs its access check off. */
    private static Method optional(Class<?> owner, String name, Class<?>... params) {
        try {
            Method found = owner.getMethod(name, params);
            found.setAccessible(true);
            return found;
        } catch (NoSuchMethodException e) {
            for (Method candidate : owner.getMethods()) {
                if (!candidate.getName().equals(name)) continue;
                if (candidate.getParameterTypes().length != params.length) continue;
                candidate.setAccessible(true);
                return candidate;
            }
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        Method found = optional(owner, name, params);
        if (found == null) throw new NoSuchMethodException(name + " on " + owner.getName());
        return found;
    }

    private static Object invoke(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            lastError = describe(e.getCause() == null ? e : e.getCause());
            return null;
        } catch (Throwable t) {
            lastError = describe(t);
            return null;
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** A resolved ladder position; index -1 means the player holds no rank group. */
    public static final class Rank {
        public final int index;
        public final String group;

        Rank(int index, String group) {
            this.index = index;
            this.group = group == null ? "" : group;
        }

        public boolean known() {
            return index >= 0;
        }

        public String label() {
            return RebornQuests.config.rankLabel(index);
        }
    }
}
