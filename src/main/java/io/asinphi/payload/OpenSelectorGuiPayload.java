package io.asinphi.payload;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public record OpenSelectorGuiPayload(List<Candidate> candidates, int possessedEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenSelectorGuiPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    NPCPuppetmaster.MOD_ID, "selector_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSelectorGuiPayload> CODEC =
            StreamCodec.composite(
                    Candidate.CODEC.apply(ByteBufCodecs.list()),
                    OpenSelectorGuiPayload::candidates,
                    ByteBufCodecs.VAR_INT,
                    OpenSelectorGuiPayload::possessedEntityId,
                    OpenSelectorGuiPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public record Candidate(int entityId, UUID uuid, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Candidate> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Candidate::entityId,
                UUIDUtil.STREAM_CODEC, Candidate::uuid,
                ByteBufCodecs.STRING_UTF8, Candidate::name,
                Candidate::new
        );
    }
}
