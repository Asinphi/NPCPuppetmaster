package io.asinphi.payload;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record OpenGuiPayload(int entityId, boolean isFollowing, boolean isPossessing)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenGuiPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    NPCPuppetmaster.MOD_ID, "open_gui"));

    // Defines how to encode/decode this packet automatically
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuiPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenGuiPayload::entityId,
                    ByteBufCodecs.BOOL,
                    OpenGuiPayload::isFollowing,
                    ByteBufCodecs.BOOL,
                    OpenGuiPayload::isPossessing,
                    OpenGuiPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
