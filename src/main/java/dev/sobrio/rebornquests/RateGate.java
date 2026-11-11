package dev.sobrio.rebornquests;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * How often one player may ask the same thing. Every answer to a packet reads the quest book of Custom NPCs,
 * and a client is free to send the same ask as fast as it likes, so the ask is what gets limited here and not
 * the player: the screen simply does not answer twice in a row, which is what a real click never does.
 */
final class RateGate {
    /** A map that only grows is its own problem, so old keys come out once it is big enough to matter. */
    private static final int SWEEP_AT = 512;
    private static final long SWEEP_AFTER = 60000L;

    private static final Map<String, Long> LAST = new HashMap<String, Long>();

    private RateGate() {}

    /** True when this ask is far enough from the last one of the same kind to be answered. */
    static boolean allow(EntityPlayerMP player, String action, long everyMillis) {
        String key = player.getName() + "|" + action;
        long now = System.currentTimeMillis();
        synchronized (LAST) {
            Long at = LAST.get(key);
            if (at != null && now - at.longValue() < everyMillis) return false;
            LAST.put(key, Long.valueOf(now));
            if (LAST.size() <= SWEEP_AT) return true;
            for (Map.Entry<String, Long> entry : new HashMap<String, Long>(LAST).entrySet()) {
                if (now - entry.getValue().longValue() > SWEEP_AFTER) LAST.remove(entry.getKey());
            }
            return true;
        }
    }

    /** Called from the staff panel, where one click moves one thing and the answer redraws the whole screen. */
    static boolean staff(EntityPlayerMP player) {
        return allow(player, "panel", 120L);
    }
}
