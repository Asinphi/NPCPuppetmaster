package io.asinphi.npcpuppetmaster.attachment;

import com.mojang.serialization.Codec;
import io.asinphi.npcpuppetmaster.NPCPuppetmaster;
import io.asinphi.npcpuppetmaster.familiar.FamiliarData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class ModAttachments {

    public static final AttachmentType<Boolean> IS_TRANSIENT = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "is_transient"),
            Codec.BOOL
    );

    public static final AttachmentType<Boolean> IS_PUPPET = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "is_puppet"),
            Codec.BOOL
    );

    public static final AttachmentType<Boolean> IS_OWNER_ONLY = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "is_owner_only"),
            Codec.BOOL
    );

    // Default: Empty (No special permission required)
    public static final AttachmentType<String> REQUIRES_PERMISSION = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "requires_permission"),
            Codec.STRING
    );

    public static final AttachmentType<Map<String, FamiliarData>> FAMILIARS = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "familiars"),
            builder -> builder
                    .initializer(HashMap::new)
                    .persistent(Codec.unboundedMap(Codec.STRING, FamiliarData.CODEC)
                            .xmap(HashMap::new, map -> map))
                    .copyOnDeath()
    );

    public static final AttachmentType<List<String>> FAMILIAR_AUTOSPAWN = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(NPCPuppetmaster.MOD_ID, "familiar_autospawn"),
            builder -> builder
                    .initializer(ArrayList::new)
                    .persistent(Codec.list(Codec.STRING)
                            .xmap(ArrayList::new, ArrayList::new))
                    .copyOnDeath()
    );

    public static void init() {
        // Class loading triggers static initializers above
    }
}
