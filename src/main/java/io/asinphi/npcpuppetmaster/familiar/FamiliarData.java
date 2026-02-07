package io.asinphi.npcpuppetmaster.familiar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record FamiliarData(UUID uuid, CompoundTag presetData) {
    public static final Codec<FamiliarData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(FamiliarData::uuid),
            CompoundTag.CODEC.fieldOf("preset_data").forGetter(FamiliarData::presetData)
    ).apply(instance, FamiliarData::new));
}
