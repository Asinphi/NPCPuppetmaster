package io.asinphi.npcpuppetmaster.networking;

import io.asinphi.npcpuppetmaster.PuppetManager;
import io.asinphi.payload.EntityActionPayload;
import io.asinphi.payload.OpenGuiPayload;
import io.asinphi.payload.OpenSelectorGuiPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ServerNetworking {

    public static void init() {
        // Register the Payload types
        PayloadTypeRegistry.playC2S().register(EntityActionPayload.ID, EntityActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenGuiPayload.ID, OpenGuiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSelectorGuiPayload.ID, OpenSelectorGuiPayload.CODEC);

        // Register the Receiver for the Action Packet
        ServerPlayNetworking.registerGlobalReceiver(EntityActionPayload.ID, (payload, context) -> {
            context.player().server.execute(() -> {
                PuppetManager.handleAction(context.player(), payload.entityId(), payload.action());
            });
        });
    }
}