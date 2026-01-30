package io.asinphi.npcpuppetmaster.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RateLimiter {
    private final Cache<UUID, Deque<Long>> usageHistory;
    private final int maxUses;
    private final long windowMillis;

    public RateLimiter(int maxUses, long windowTime, TimeUnit timeUnit) {
        this.maxUses = maxUses;
        this.windowMillis = timeUnit.toMillis(windowTime);

        // Automatically removes entries that haven't been accessed for a while
        this.usageHistory = CacheBuilder.newBuilder()
                .expireAfterAccess(windowTime * 2, timeUnit)
                .build();
    }

    public long tryUse(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();

        Deque<Long> history = usageHistory.getIfPresent(uuid);
        if (history == null) {
            history = new ArrayDeque<>();
            usageHistory.put(uuid, history);
        }

        while (!history.isEmpty() && (now - history.peekFirst() > windowMillis)) {
            history.pollFirst();
        }

        if (history.size() >= maxUses) {
            // Calculate time until the oldest use expires
            long oldestUse = history.peekFirst();
            long unblockTime = oldestUse + windowMillis;
            return Math.max(0, unblockTime - now);
        }

        history.addLast(now);
        return 0; // Success; 0 remaining wait time
    }
}