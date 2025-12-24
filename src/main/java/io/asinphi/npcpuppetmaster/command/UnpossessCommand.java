package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import io.asinphi.npcpuppetmaster.PuppetManager;
import io.asinphi.npcpuppetmaster.config.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class UnpossessCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("unpossess")
                .requires(CommandSourceStack::isPlayer) // Only players can possess
                .executes(context -> unpossess(context.getSource()))
        );
    }

    private static int unpossess(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();

            if (PuppetManager.isPossessing(player)) {
                // Unforced possession end
                PuppetManager.cancelPossession(player, false);
                return 1;
            } else {
                Lang.send(player, "possess.not_possessing");
                return 0;
            }
        } catch (Exception e) {
            // Should not happen due to .requires(isPlayer), but safe to catch
            return 0;
        }
    }
}