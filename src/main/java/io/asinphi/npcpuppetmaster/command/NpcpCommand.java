package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import io.asinphi.npcpuppetmaster.config.Lang;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class NpcpCommand {
    private static final String PERMISSION_HELP = "npcp.use";
    private static final String PERMISSION_RELOAD = "npcp.reload";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npcp")
                .requires(Permissions.require(PERMISSION_HELP, 2))
                .executes(ctx -> sendHelp(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> sendHelp(ctx.getSource()))
                )
                .then(Commands.literal("reload")
                        .requires(Permissions.require(PERMISSION_RELOAD, 2))
                        .executes(ctx -> reload(ctx.getSource()))
                )
        );
    }

    private static int sendHelp(CommandSourceStack source) {
        Lang.send(source, "commands.help");
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        NPCPuppetmaster.reload();
        Lang.send(source, "commands.reload.completed");
        return 1;
    }

    private NpcpCommand() {}
}
