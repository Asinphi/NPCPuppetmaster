package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.asinphi.npcavatar.avatar.EntityAvatarManager;
import io.asinphi.npcpuppetmaster.config.Config;
import io.asinphi.npcpuppetmaster.config.Lang;
import io.asinphi.npcpuppetmaster.familiar.FamiliarData;
import io.asinphi.npcpuppetmaster.familiar.FamiliarManager;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FamiliarCommand {
    private static final String PERMISSION = "npcp.familiar.use";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("familiar")
                .requires(src -> src.isPlayer() && Permissions.check(src, PERMISSION, 2))
                .executes(ctx -> sendHelp(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> sendHelp(ctx.getSource()))
                )
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> handleCreate(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        null))
                                .then(Commands.argument("displayName", StringArgumentType.string())
                                        .executes(ctx -> handleCreate(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "displayName")))
                                )
                        )
                )
                .then(Commands.literal("list")
                        .executes(ctx -> handleList(ctx.getSource()))
                )
                .then(Commands.literal("erase")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (String id : FamiliarManager.getFamiliars(ctx.getSource().getPlayerOrException()).keySet()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> handleErase(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        false))
                                .then(Commands.literal("permanently")
                                        .executes(ctx -> handleErase(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                true))
                                )
                        )
                )
                .then(Commands.literal("spawn")
                        .executes(ctx -> handleSpawn(ctx.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (String id : FamiliarManager.getFamiliars(ctx.getSource().getPlayerOrException()).keySet()) {
                                        builder.suggest(id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> handleSpawn(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))
                        )
                )
                .then(Commands.literal("edit")
                        .then(Commands.literal("name")
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> handleEditName(
                                                ctx,
                                                StringArgumentType.getString(ctx, "value"),
                                                null))
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    for (String id : FamiliarManager.getFamiliars(ctx.getSource().getPlayerOrException()).keySet()) {
                                                        builder.suggest(id);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> handleEditName(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "value"),
                                                        StringArgumentType.getString(ctx, "id")))
                                        )
                                )
                        )
                        .then(Commands.literal("autospawn")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> handleEditAutospawn(
                                                ctx,
                                                BoolArgumentType.getBool(ctx, "enabled"),
                                                null))
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    for (String id : FamiliarManager.getFamiliars(ctx.getSource().getPlayerOrException()).keySet()) {
                                                        builder.suggest(id);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> handleEditAutospawn(
                                                        ctx,
                                                        BoolArgumentType.getBool(ctx, "enabled"),
                                                        StringArgumentType.getString(ctx, "id")))
                                        )
                                )
                        )
                        .then(Commands.literal("avatar")
                                .executes(ctx -> handleEditAvatar(ctx, null))
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            for (String id : FamiliarManager.getFamiliars(ctx.getSource().getPlayerOrException()).keySet()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> handleEditAvatar(
                                                ctx,
                                                StringArgumentType.getString(ctx, "id")))
                                )
                        )
                )
        );
    }

    private static int sendHelp(CommandSourceStack source) {
        Lang.send(source, "commands.familiar.help");
        return 1;
    }

    private static int handleCreate(CommandSourceStack source, String id, String displayName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        FamiliarManager.CreateOutcome outcome = FamiliarManager.createFamiliar(player, id, displayName);
        return switch (outcome.result()) {
            case SUCCESS -> {
                FamiliarData data = outcome.data();
                Lang.send(source, "commands.familiar.create_success",
                        Placeholder.unparsed("id", id),
                        Placeholder.unparsed("name", FamiliarManager.getDisplayName(data.presetData(), id))
                );
                yield 1;
            }
            case EXISTS -> {
                Lang.send(source, "commands.familiar.create_exists", Placeholder.unparsed("id", id));
                yield 0;
            }
            case MAX_TOTAL -> {
                Lang.send(source, "commands.familiar.create_max_total",
                        Placeholder.unparsed("max", String.valueOf(Config.getMaxFamiliarTotal())));
                yield 0;
            }
            case FAILED -> {
                Lang.send(source, "commands.familiar.create_failed", Placeholder.unparsed("id", id));
                yield 0;
            }
        };
    }

    private static int handleList(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Map<String, FamiliarData> familiars = FamiliarManager.getFamiliars(player);
        if (familiars.isEmpty()) {
            Lang.send(source, "commands.familiar.list_empty");
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, FamiliarData> entry : familiars.entrySet()) {
            String id = entry.getKey();
            String name = FamiliarManager.getDisplayName(entry.getValue().presetData(), id);
            if (!sb.isEmpty()) sb.append("<newline>");
            sb.append("<yellow>").append(id).append("</yellow>")
                    .append(" <gray>—</gray> <white>").append(escapeMiniMessage(name)).append("</white>");
        }

        Lang.send(source, "commands.familiar.list",
                Placeholder.parsed("entries", sb.toString())
        );
        return 1;
    }

    private static int handleErase(CommandSourceStack source, String id, boolean permanent) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        if (!permanent) {
            if (!FamiliarManager.getFamiliars(player).containsKey(id)) {
                Lang.send(source, "commands.familiar.missing", Placeholder.unparsed("id", id));
                return 0;
            }
            Lang.send(source, "commands.familiar.erase_confirm",
                    Placeholder.unparsed("id", id),
                    Placeholder.parsed("command", getSuggestCommand("/familiar erase " + id + " permanently", "permanently delete"))
            );
            return 0;
        }

        FamiliarManager.EraseResult result = FamiliarManager.eraseFamiliar(player, id);
        if (result == FamiliarManager.EraseResult.NOT_FOUND) {
            Lang.send(source, "commands.familiar.missing", Placeholder.unparsed("id", id));
            return 0;
        }

        Lang.send(source, "commands.familiar.erase_success", Placeholder.unparsed("id", id));
        return 1;
    }

    private static int handleSpawn(CommandSourceStack source, String id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Map<String, FamiliarData> familiars = FamiliarManager.getFamiliars(player);
        if (familiars.isEmpty()) {
            Lang.send(source, "commands.familiar.none_saved");
            return 0;
        }

        String targetId = id;
        if (targetId == null || targetId.isBlank()) {
            String auto = FamiliarManager.findNextAutospawn(player, familiars);
            if (auto != null) {
                targetId = auto;
            } else if (familiars.size() == 1) {
                targetId = familiars.keySet().iterator().next();
            } else {
                Lang.send(source, "commands.familiar.spawn_choose",
                        Placeholder.parsed("options", getIdSuggestions("spawn", familiars.keySet()))
                );
                return 0;
            }
        }

        FamiliarManager.SpawnResult result = FamiliarManager.spawnFamiliar(player, targetId);
        return switch (result) {
            case SUCCESS -> {
                FamiliarData data = familiars.get(targetId);
                Lang.send(source, "commands.familiar.spawn_success",
                        Placeholder.unparsed("id", targetId),
                        Placeholder.unparsed("name", FamiliarManager.getDisplayName(data.presetData(), targetId))
                );
                yield 1;
            }
            case ALREADY_SPAWNED -> {
                Lang.send(source, "commands.familiar.spawn_already",
                        Placeholder.unparsed("id", targetId));
                yield 1;
            }
            case NOT_FOUND -> {
                Lang.send(source, "commands.familiar.missing", Placeholder.unparsed("id", targetId));
                yield 0;
            }
            case FAILED -> {
                Lang.send(source, "commands.familiar.spawn_failed",
                        Placeholder.unparsed("id", targetId));
                yield 0;
            }
        };
    }

    private static int handleEditName(@UnknownNullability CommandContext<CommandSourceStack> ctx, String value,
                                      String id) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String targetId = resolveSpawnedIdOrPrompt(source, player, id, ctx.getInput());
        if (targetId == null) return 0;

        var entity = FamiliarManager.getSpawnedEntity(player, targetId);
        if (entity == null) {
            Lang.send(source, "commands.familiar.not_spawned",
                    Placeholder.unparsed("id", targetId));
            return 0;
        }

        entity.setCustomName(Component.literal(value));
        FamiliarManager.updateStoredPreset(player, targetId, entity);

        Lang.send(source, "commands.familiar.edit_name_success",
                Placeholder.unparsed("id", targetId),
                Placeholder.unparsed("name", value)
        );
        return 1;
    }

    private static int handleEditAvatar(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String targetId = resolveSpawnedIdOrPrompt(source, player, id, ctx.getInput());
        if (targetId == null) return 0;

        var entity = FamiliarManager.getSpawnedEntity(player, targetId);
        if (entity == null) {
            Lang.send(source, "commands.familiar.not_spawned",
                    Placeholder.unparsed("id", targetId));
            return 0;
        }

        EntityAvatarManager.fetchFromPlayer(player, List.of(entity));

        Lang.send(source, "commands.familiar.edit_avatar_success",
                Placeholder.unparsed("id", targetId)
        );
        return 1;
    }

    private static int handleEditAutospawn(CommandContext<CommandSourceStack> ctx, boolean enabled, String id) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String targetId = resolveSpawnedIdOrPrompt(source, player, id, ctx.getInput());
        if (targetId == null) return 0;

        if (!FamiliarManager.setAutospawn(player, targetId, enabled)) {
            Lang.send(source, "commands.familiar.missing", Placeholder.unparsed("id", targetId));
            return 0;
        }

        if (enabled) {
            Lang.send(source, "commands.familiar.autospawn_enabled", Placeholder.unparsed("id", targetId));
        } else {
            Lang.send(source, "commands.familiar.autospawn_disabled", Placeholder.unparsed("id", targetId));
        }
        return 1;
    }

    private static String resolveSpawnedIdOrPrompt(CommandSourceStack source, ServerPlayer player, String id,
                                                   String command) {
        List<String> spawned = FamiliarManager.getSpawnedIds(player);
        if (spawned.isEmpty()) {
            Lang.send(source, "commands.familiar.none_spawned");
            return null;
        }

        if (id != null && !id.isBlank()) {
            return id;
        }

        if (spawned.size() == 1) {
            return spawned.get(0);
        }

        Lang.send(source, "commands.familiar.edit_choose",
                Placeholder.parsed("options", getIdSuggestions(command, spawned))
        );
        return null;
    }

    private static String getIdSuggestions(String command, Collection<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (!sb.isEmpty()) sb.append("</yellow>, <yellow>");
            sb.append("<click:run_command:'/")
                    .append(command.stripTrailing())
                    .append(" ")
                    .append(id)
                    .append("'>")
                    .append(id)
                    .append("</click>");
        }
        return sb.toString();
    }

    private static String getSuggestCommand(String command, String label) {
        return "<click:suggest_command:'" + command + "'><yellow>" + label + "</yellow></click>";
    }

    private static String escapeMiniMessage(String input) {
        if (input == null) return "";
        return input.replace("<", "&lt;").replace(">", "&gt;");
    }

    private FamiliarCommand() {}
}
