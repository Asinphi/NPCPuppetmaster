package io.asinphi.npcpuppetmaster;

import io.asinphi.npcpuppetmaster.entity.ModEntities;
import io.asinphi.npcpuppetmaster.keybinds.ModKeyBindings;
import io.asinphi.npcpuppetmaster.networking.ClientNetworking;
import io.asinphi.npcpuppetmaster.renderer.PlayerDummyRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class NPCPuppetmasterClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ModKeyBindings.init();
        ClientNetworking.init();
        EntityRendererRegistry.register(ModEntities.PLAYER_DUMMY, PlayerDummyRenderer::new);
    }
}