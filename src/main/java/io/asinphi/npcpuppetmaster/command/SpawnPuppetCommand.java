package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.asinphi.npcpuppetmaster.PuppetManager;
import io.asinphi.npcpuppetmaster.config.Config;
import io.asinphi.npcpuppetmaster.config.Lang;
import io.asinphi.npcpuppetmaster.util.RateLimiter;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.TimeUnit;

public class SpawnPuppetCommand {

    private static RateLimiter rateLimiter;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (Config.getCreatePuppetMaxCount() >= 0 && Config.getCreatePuppetWindowMillis() > 0) {
            rateLimiter = new RateLimiter(
                    Config.getCreatePuppetMaxCount(),
                    Config.getCreatePuppetWindowMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS
            );
        }

        dispatcher.register(Commands.literal("spawnpuppet")
                .requires(src -> src.getPlayer() != null && Permissions.check(
                        src.getPlayer(), "npcp.spawnpuppet", 2))
                .executes(ctx -> {
                    Lang.send(ctx.getSource(), "commands.spawnpuppet.presets_list",
                            Placeholder.parsed("presets", getPresetsMiniMessage())
                    );
                    return 0;
                })
                .then(Commands.argument("preset", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (String presetName : Config.getPuppetPresets().keySet()) {
                                builder.suggest(presetName);
                            }
                            return builder.buildFuture();
                        })
                        .executes(SpawnPuppetCommand::spawnPuppet)
                        .then(Commands.argument("location", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("here");
                                    builder.suggest("there");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String location = StringArgumentType.getString(ctx, "location");
                                    return spawnPuppet(ctx, location);
                                })
                        )
                )
        );
    }

    private static int spawnPuppet(CommandContext<CommandSourceStack> ctx) {
        return spawnPuppet(ctx, "here");
    }

    private static int spawnPuppet(CommandContext<CommandSourceStack> ctx, String location) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        String presetName = StringArgumentType.getString(ctx, "preset");

        Config.PresetOptions presetOptions = Config.getPuppetPresets().get(presetName);
        if (presetOptions == null) {
            Lang.send(source, "commands.spawnpuppet.invalid_preset",
                    Placeholder.unparsed("preset", presetName),
                    Placeholder.parsed("valid_presets", getPresetsMiniMessage())
            );
            return 0;
        }

        assert player != null;
        if (rateLimiter != null && !Permissions.check(player,
                "npcp.spawnpuppet.bypass_rate_limit", 2)) {
            long waitTime = rateLimiter.tryUse(player);
            if (waitTime > 0) {
                Lang.send(source, "commands.spawnpuppet.rate_limited",
                        Placeholder.unparsed("preset", presetName),
                        Placeholder.unparsed("wait_time", formatDuration(waitTime)),
                        Placeholder.unparsed("max_count", String.valueOf(Config.getCreatePuppetMaxCount())),
                        Placeholder.unparsed("window", formatDuration(Config.getCreatePuppetWindowMillis()))
                );
                return 0;
            }
        }

        Vec3 spawnPos = resolveSpawnPosition(player, location);
        LivingEntity entity = PuppetManager.spawnNpc(presetOptions, player.serverLevel(), spawnPos, player);

        if (entity != null) {
            Lang.send(source, "commands.spawnpuppet.success",
                    Placeholder.unparsed("preset", presetName)
            );
            return 1;
        } else {
            Lang.send(source, "commands.spawnpuppet.failure",
                    Placeholder.unparsed("preset", presetName)
            );
            return 0;
        }
    }

    private static Vec3 resolveSpawnPosition(ServerPlayer player, String location) {
        if ("there".equalsIgnoreCase(location)) {
            Vec3 start = player.getEyePosition(1.0F);
            Vec3 look = player.getViewVector(1.0F);
            Vec3 end = start.add(look.scale(10.0D));
            HitResult hit = player.level().clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation();
            }
            return end;
        }
        return new Vec3(player.getX(), player.getY(), player.getZ());
    }

    private static String getPresetsMiniMessage() {
        StringBuilder sb = new StringBuilder();
        for (String presetName : Config.getPuppetPresets().keySet()) {
            if (!sb.isEmpty()) {
                sb.append("</yellow>, <yellow>");
            }
            sb.append("<click:suggest_command:'/spawnpuppet ")
              .append(presetName)
              .append("'>")
              .append(presetName)
              .append("</click>");
        }
        return sb.toString();
    }

    private static String formatDuration(long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        // If less than 60 seconds, just show seconds (e.g., "45s")
        if (seconds < 60) {
            return Math.max(1, seconds) + "s";
        }

        // Otherwise, show minutes and remaining seconds (e.g., "3m 12s")
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long remainingSeconds = seconds % 60;

        return minutes + "m" + (remainingSeconds > 0 ? " " + remainingSeconds + "s" : "");
    }
}
