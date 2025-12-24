package io.asinphi.npcpuppetmaster.command;

import com.mojang.brigadier.CommandDispatcher;
import de.markusbordihn.easynpc.commands.arguments.EasyNPCArgument;
import io.asinphi.npcpuppetmaster.PuppetManager;
import io.asinphi.npcpuppetmaster.action.Action;
import io.asinphi.npcpuppetmaster.util.PermissionUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class PuppeteerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("puppeteer")
                .requires(source ->
                        PermissionUtil.check(source, "npcp.puppeteer.use", 2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PuppetManager.openSelectorGui(player);
                    return 1;
                })
                .then(Commands.argument("npc", EasyNPCArgument.npc())
                        .suggests(PuppetManager.getSuggestions())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                            PuppetManager.openGui(player, target);
                            return 1;
                        })
                        .then(Commands.literal("possess")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                                    PuppetManager.handleAction(player, target.getId(), Action.POSSESS);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("follow")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                                    PuppetManager.handleAction(player, target.getId(), Action.FOLLOW);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("unfollow")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                                    PuppetManager.handleAction(player, target.getId(), Action.UNFOLLOW);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("come")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                                    PuppetManager.handleAction(player, target.getId(), Action.MOVE_HERE);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("go")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                                    PuppetManager.handleAction(player, target.getId(), Action.GO_AWAY);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("delete")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    LivingEntity target = (LivingEntity) EasyNPCArgument.getEntity(context, "npc");
                                    PuppetManager.handleAction(player, target.getId(), Action.DELETE);
                                    return 1;
                                })
                        )
                )
        );
    }
}