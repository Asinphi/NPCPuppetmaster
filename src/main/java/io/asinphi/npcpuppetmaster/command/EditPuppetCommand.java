package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.markusbordihn.easynpc.commands.arguments.EasyNPCArgument;
import io.asinphi.npcpuppetmaster.attachment.ModAttachments;
import io.asinphi.npcpuppetmaster.config.Lang;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;

public class EditPuppetCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("editpuppet")
                .requires(Permissions.require("npcp.edit", 2))
                .then(Commands.argument("target", EasyNPCArgument.npc())
                        .then(Commands.literal("transient")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(EditPuppetCommand::setIsTransient)
                                )
                        )
                        .then(Commands.literal("ispuppet")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(EditPuppetCommand::setIsPuppet)
                                )
                        )
                        .then(Commands.literal("owneronly")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(EditPuppetCommand::setIsOwnerOnly)
                                )
                        )
                        .then(Commands.literal("permission")
                                .then(Commands.literal("clear")
                                        .executes(EditPuppetCommand::clearPermission)
                                )
                                .then(Commands.literal("set")
                                    .then(Commands.argument("node", StringArgumentType.string())
                                            .executes(EditPuppetCommand::setPermission)
                                    )
                                )
                        )
                )
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int setIsTransient(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = (Entity) EasyNPCArgument.getEntity(context, "target");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        target.setAttached(ModAttachments.IS_TRANSIENT, enabled);

        Lang.send(context.getSource(), "commands.editpuppet.transient.success",
                Placeholder.parsed("target", target.getName().getString()),
                Placeholder.parsed("despawn", enabled ? "<yellow>despawn</yellow>" : "<green>never despawn</green>")
        );

        return 1;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int setIsPuppet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = (Entity) EasyNPCArgument.getEntity(context, "target");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        target.setAttached(ModAttachments.IS_PUPPET, enabled);

        Lang.send(context.getSource(), "commands.editpuppet.is_puppet.success",
                Placeholder.parsed("target", target.getName().getString()),
                Placeholder.parsed("enabled", enabled ? "<green>enabled</green>" : "<red>disabled</red>")
        );

        return 1;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int setIsOwnerOnly(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = (Entity) EasyNPCArgument.getEntity(context, "target");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        target.setAttached(ModAttachments.IS_OWNER_ONLY, enabled);

        Lang.send(context.getSource(), "commands.editpuppet.owneronly.success",
                Placeholder.parsed("target", target.getName().getString()),
                Placeholder.parsed("only_by_owner", enabled ? "<red>exclusively by its owner</red>" :
                        "<yellow>by anyone</yellow>")
        );

        return 1;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int clearPermission(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = (Entity) EasyNPCArgument.getEntity(context, "target");

        // Remove the attachment to reset it
        target.removeAttached(ModAttachments.REQUIRES_PERMISSION);
        Lang.send(context.getSource(), "commands.editpuppet.permission.cleared",
                Placeholder.parsed("target", target.getName().getString())
        );

        return 1;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int setPermission(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = (Entity) EasyNPCArgument.getEntity(context, "target");
        String node = StringArgumentType.getString(context, "node");
        // Set the required permission string
        target.setAttached(ModAttachments.REQUIRES_PERMISSION, node);
        Lang.send(context.getSource(), "commands.editpuppet.permission.success",
                Placeholder.parsed("target", target.getName().getString()),
                Placeholder.parsed("permission", "npcp.puppeteer.special." + node + ".*")
        );

        return 1;
    }
}
