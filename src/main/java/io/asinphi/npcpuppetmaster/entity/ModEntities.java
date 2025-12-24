package io.asinphi.npcpuppetmaster.entity;

import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

    public static final EntityType<PlayerDummyEntity> PLAYER_DUMMY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "player_dummy"),
            // Use standard Vanilla Builder
            EntityType.Builder.of(PlayerDummyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f) // Sets the bounding box
                    .build(ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "player_dummy").toString())
    );

    public static void init() {
        //noinspection ConstantConditions
        FabricDefaultAttributeRegistry.register(PLAYER_DUMMY, PlayerDummyEntity.createAttributes());
    }
}
