package io.asinphi.npcpuppetmaster.util;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.PriorityQueue;
import java.util.Queue;

public class TaskScheduler {

    private record ScheduledTask(long targetTick, Runnable task) implements Comparable<ScheduledTask> {
        @Override
        public int compareTo(ScheduledTask other) {
            return Long.compare(this.targetTick, other.targetTick);
        }
    }

    private static final Queue<ScheduledTask> TASK_QUEUE = new PriorityQueue<>();

    /**
     * Initialize the tick listener. Call this once in the main mod initializer.
     */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TaskScheduler::onTick);
    }

    /**
     * Schedules a task to run after a specific number of ticks.
     *
     * @param server The MinecraftServer instance (needed to get current tick).
     * @param delayTicks The number of ticks to wait.
     * @param task The logic to execute.
     */
    public static void schedule(MinecraftServer server, int delayTicks, Runnable task) {
        if (server == null) return;
        long targetTick = server.getTickCount() + delayTicks;
        TASK_QUEUE.add(new ScheduledTask(targetTick, task));
    }

    private static void onTick(MinecraftServer server) {
        if (TASK_QUEUE.isEmpty()) return;

        int currentTick = server.getTickCount();

        // Process all tasks that are due or overdue
        while (!TASK_QUEUE.isEmpty() && TASK_QUEUE.peek().targetTick() <= currentTick) {
            ScheduledTask task = TASK_QUEUE.poll();
            if (task != null) {
                try {
                    task.task().run();
                } catch (Exception e) {
                    // Prevent one failing task from crashing the server tick loop
                    NPCPuppetmaster.LOGGER.error("Error executing task", e);
                }
            }
        }
    }
}