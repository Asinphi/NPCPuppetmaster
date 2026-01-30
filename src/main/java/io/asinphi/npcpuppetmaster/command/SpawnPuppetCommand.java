package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.markusbordihn.easynpc.handler.PresetHandler;
import io.asinphi.npcpuppetmaster.config.Config;
import io.asinphi.npcpuppetmaster.config.Lang;
import io.asinphi.npcpuppetmaster.util.RateLimiter;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
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
                )
        );
    }

    private static int spawnPuppet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        String presetName = StringArgumentType.getString(ctx, "preset");

        if (!Config.getPuppetPresets().containsKey(presetName)) {
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

        // TODO Bump EasyNPC and use its newer importPreset method
        boolean success = PresetHandler.importCustomPreset(player.serverLevel(),
                ResourceLocation.fromNamespaceAndPath("easy_npc", Config.getPuppetPresets().get(presetName)),
                new Vec3(player.getX(), player.getY(), player.getZ()),
                UUID.randomUUID(),
                player);

        if (success) {
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

        return String.format("%dm %ds", minutes, remainingSeconds);
    }
}
