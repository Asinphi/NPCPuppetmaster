package io.asinphi.npcpuppetmaster;

import io.asinphi.npcpuppetmaster.attachment.ModAttachments;
import io.asinphi.npcpuppetmaster.command.EditPuppetCommand;
import io.asinphi.npcpuppetmaster.command.PuppeteerCommand;
import io.asinphi.npcpuppetmaster.command.UnpossessCommand;
import io.asinphi.npcpuppetmaster.config.Config;
import io.asinphi.npcpuppetmaster.config.Lang;
import io.asinphi.npcpuppetmaster.entity.ModEntities;
import io.asinphi.npcpuppetmaster.networking.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NPCPuppetmaster implements ModInitializer {
	public static final String MOD_ID = "npc-puppetmaster";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
        ModEntities.init();
        ModAttachments.init();
        ServerNetworking.init();
        Lang.init();
        Config.load();
        PuppetManager.init();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher,
                 registryAccess, environment) -> {
                    PuppeteerCommand.register(dispatcher);
                    EditPuppetCommand.register(dispatcher);
                    UnpossessCommand.register(dispatcher);
                }
        );
	}

    public static void reload() {
        Lang.reload();
        Config.reload();
    }

    public static void debug(String msg, Object... args) {
        if (!Config.isDebugEnabled()) {
            return;
        }
        LOGGER.info("[DEBUG] " + msg, args);
    }
}