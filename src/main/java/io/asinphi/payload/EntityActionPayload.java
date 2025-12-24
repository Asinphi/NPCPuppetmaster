package io.asinphi.payload;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import io.asinphi.npcpuppetmaster.action.Action;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EntityActionPayload(int entityId, Action action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityActionPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    NPCPuppetmaster.MOD_ID, "entity_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityActionPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EntityActionPayload::entityId,
                    Action.CODEC,
                    EntityActionPayload::action,
                    EntityActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
