package io.asinphi.npcpuppetmaster.networking;

import io.asinphi.npcpuppetmaster.gui.CloseableCottonScreen;
import io.asinphi.npcpuppetmaster.gui.NPCControlGui;
import io.asinphi.npcpuppetmaster.gui.SelectorGui;
import io.asinphi.payload.OpenGuiPayload;
import io.asinphi.payload.OpenSelectorGuiPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class ClientNetworking {

    // Call this in your ClientModInitializer (onInitializeClient)
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(OpenGuiPayload.ID, (payload, context) -> {
            int entityId = payload.entityId();

            context.client().execute(() -> {
                if (context.client().level != null) {
                    Entity target = context.client().level.getEntity(entityId);

                    if (target instanceof LivingEntity livingTarget) {
                        Minecraft.getInstance().setScreen(new CloseableCottonScreen(
                                new NPCControlGui(livingTarget, payload.isFollowing(), payload.isPossessing())));
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenSelectorGuiPayload.ID, (payload, context) -> {
            List<OpenSelectorGuiPayload.Candidate> candidates = payload.candidates();

            context.client().execute(() -> {
                // Open the Selector GUI
                Minecraft.getInstance().setScreen(new CloseableCottonScreen(new SelectorGui(candidates,
                        payload.possessedEntityId())));
            });
        });
    }
}