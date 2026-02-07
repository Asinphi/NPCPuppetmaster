package io.asinphi.npcpuppetmaster.familiar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.PresetDataCapable;
import de.markusbordihn.easynpc.handler.PresetHandler;
import io.asinphi.npcavatar.avatar.AvatarBlobAppliedCallback;
import io.asinphi.npcavatar.avatar.EntityAvatarManager;
import io.asinphi.npcpuppetmaster.PuppetManager;
import io.asinphi.npcpuppetmaster.action.Action;
import io.asinphi.npcpuppetmaster.attachment.ModAttachments;
import io.asinphi.npcpuppetmaster.config.Config;
import io.asinphi.npcpuppetmaster.util.TaskScheduler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FamiliarManager {

    private static final String CUSTOM_NAME_KEY = "CustomName";

    private static final Map<UUID, LinkedHashMap<String, UUID>> spawnedFamiliars = new ConcurrentHashMap<>();

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            // Delay so chunk has time to start ticking, so the spawned entity can be found.
            TaskScheduler.schedule(server, 20, () -> {
                syncSpawnedFromWorld(player);
                spawnAutospawnFamiliars(player);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            spawnedFamiliars.remove(handler.player.getUUID());
        });

        AvatarBlobAppliedCallback.EVENT.register((target, source, blob) -> {
            if (spawnedFamiliars.containsKey(source.getUUID())) {
                for (Map.Entry<String, UUID> entry : spawnedFamiliars.get(source.getUUID()).entrySet()) {
                    if (entry.getValue().equals(target.getUUID())) {
                        updateStoredPreset(source, entry.getKey(), (LivingEntity) target);
                        break;
                    }
                }
            }
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    public static Map<String, FamiliarData> getFamiliars(ServerPlayer player) {
        return player.getAttachedOrCreate(ModAttachments.FAMILIARS);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static List<String> getAutospawnList(ServerPlayer player) {
        return player.getAttachedOrCreate(ModAttachments.FAMILIAR_AUTOSPAWN);
    }

    public enum CreateResult {
        SUCCESS,
        EXISTS,
        MAX_TOTAL,
        FAILED
    }

    public record CreateOutcome(CreateResult result, FamiliarData data) {
        public static CreateOutcome success(FamiliarData data) {
            return new CreateOutcome(CreateResult.SUCCESS, data);
        }

        public static CreateOutcome failed(CreateResult result) {
            return new CreateOutcome(result, null);
        }
    }

    public enum SpawnResult {
        SUCCESS,
        ALREADY_SPAWNED,
        NOT_FOUND,
        FAILED
    }

    public enum EraseResult {
        SUCCESS,
        NOT_FOUND
    }

    public static SpawnResult spawnFamiliar(ServerPlayer player, String id) {
        Map<String, FamiliarData> familiars = getFamiliars(player);
        FamiliarData data = familiars.get(id);
        if (data == null) {
            return SpawnResult.NOT_FOUND;
        }

        LivingEntity existing = findEntity(player.server, data.uuid());
        if (existing != null) {
            trackSpawned(player, id, data.uuid());
            return SpawnResult.ALREADY_SPAWNED;
        }

        enforceSpawnLimit(player, Config.getMaxFamiliarSpawned());
        LivingEntity entity = spawnFromPreset(player, data);
        if (entity == null) {
            return SpawnResult.FAILED;
        }

        trackSpawned(player, id, entity.getUUID());
        PuppetManager.forceHandleAction(player, entity.getId(), Action.FOLLOW);
        return SpawnResult.SUCCESS;
    }

    public static EraseResult eraseFamiliar(ServerPlayer player, String id) {
        Map<String, FamiliarData> familiars = getFamiliars(player);
        if (!familiars.containsKey(id)) {
            return EraseResult.NOT_FOUND;
        }

        familiars.remove(id);
        removeFromAutospawn(player, id);
        despawnIfSpawned(player, id);
        return EraseResult.SUCCESS;
    }

    public static boolean setAutospawn(ServerPlayer player, String id, boolean enabled) {
        if (!getFamiliars(player).containsKey(id)) {
            return false;
        }
        List<String> list = getAutospawnList(player);
        if (enabled) {
            list.remove(id);
            list.addFirst(id);
        } else {
            list.remove(id);
        }
        return true;
    }

    public static List<String> getSpawnedIds(ServerPlayer player) {
        pruneMissing(player);
        return new java.util.ArrayList<>(getSpawnedMap(player).keySet());
    }

    public static CreateOutcome createFamiliar(ServerPlayer player, String id, String displayName) {
        Map<String, FamiliarData> familiars = getFamiliars(player);
        if (familiars.containsKey(id)) {
            return CreateOutcome.failed(CreateResult.EXISTS);
        }

        int maxTotal = Config.getMaxFamiliarTotal();
        if (maxTotal >= 0 && familiars.size() >= maxTotal) {
            return CreateOutcome.failed(CreateResult.MAX_TOTAL);
        }

        ServerLevel level = player.serverLevel();
        Vec3 spawnPos = player.position();
        UUID uuid = UUID.randomUUID();

        Config.PresetOptions preset = Config.getFamiliarTemplatePreset();
        boolean success = PresetHandler.importCustomPreset(
                level,
                ResourceLocation.fromNamespaceAndPath("easy_npc", preset.getPath()),
                spawnPos,
                uuid,
                player
        );
        if (!success) {
            return CreateOutcome.failed(CreateResult.FAILED);
        }

        LivingEntity entity = (LivingEntity) LivingEntityManager.getEasyNPCEntityByUUID(uuid, level);
        if (entity == null) {
            return CreateOutcome.failed(CreateResult.FAILED);
        }

        if (entity instanceof PathfinderMob mob)
            moveMobNearby(mob);

        if (displayName != null && !displayName.isBlank()) {
            entity.setCustomName(Component.literal(displayName));
        } else {
            entity.setCustomName(Component.literal(id));
        }
        EntityAvatarManager.fetchFromPlayer(player, List.of(entity));
        applyPresetDefaults(entity, preset, player);

        FamiliarData data = toFamiliarData(entity, uuid);
        familiars.put(id, data);

        trackSpawned(player, id, uuid);
        enforceSpawnLimit(player, Config.getMaxFamiliarSpawned());
        PuppetManager.forceHandleAction(player, entity.getId(), Action.FOLLOW);

        return CreateOutcome.success(data);
    }

    private static void spawnAutospawnFamiliars(ServerPlayer player) {
        Map<String, FamiliarData> familiars = getFamiliars(player);
        if (familiars.isEmpty()) return;

        List<String> autospawn = getAutospawnList(player);
        if (autospawn.isEmpty()) return;

        int maxSpawned = Config.getMaxFamiliarSpawned();
        if (maxSpawned == 0) return;

        pruneMissing(player);
        LinkedHashMap<String, UUID> spawned = getSpawnedMap(player);
        int slots = maxSpawned < 0 ? Integer.MAX_VALUE : Math.max(0, maxSpawned - spawned.size());
        if (slots <= 0) return;

        for (String id : autospawn) {
            if (slots <= 0) break;
            FamiliarData data = familiars.get(id);
            if (data == null) continue;

            LivingEntity existing = findEntity(player.server, data.uuid());
            if (existing != null) {
                trackSpawned(player, id, data.uuid());
                continue;
            }

            LivingEntity entity = spawnFromPreset(player, data);
            trackSpawned(player, id, data.uuid());
            if (entity != null) {
                PuppetManager.forceHandleAction(player, entity.getId(), Action.FOLLOW);
                slots--;
            }
        }
    }

    public static LivingEntity spawnFromPreset(ServerPlayer player, FamiliarData data) {
        ServerLevel level = player.serverLevel();
        Vec3 spawnPos = player.position();

        boolean success = PresetHandler.importPreset(
                level,
                data.presetData().copy(),
                spawnPos,
                data.uuid(),
                player
        );
        if (!success) return null;

        LivingEntity entity = (LivingEntity) LivingEntityManager.getEasyNPCEntityByUUID(data.uuid(), level);
        if (entity == null) return null;
        if (entity instanceof PathfinderMob mob)
            moveMobNearby(mob);

        Config.PresetOptions preset = Config.getFamiliarTemplatePreset();
        applyPresetDefaults(entity, preset, player);
        return entity;
    }

    private static FamiliarData toFamiliarData(LivingEntity entity, UUID uuid) {
        if (!(entity instanceof EasyNPC<?> easyNpc)) {
            return new FamiliarData(uuid, entity.saveWithoutId(new CompoundTag()));
        }
        PresetDataCapable<?> presetData = easyNpc.getEasyNPCPresetData();
        if (presetData == null) {
            throw new RuntimeException("Failed to retrieve preset data from entity " + entity.getDisplayName()
                    .getString());
        }
        CompoundTag presetTag = presetData.serializePresetData();
        return new FamiliarData(uuid, presetTag);
    }

    public static void updateStoredPreset(ServerPlayer player, String id, LivingEntity entity) {
        Map<String, FamiliarData> familiars = getFamiliars(player);
        FamiliarData data = familiars.get(id);
        if (data == null) return;
        if (entity instanceof EasyNPC<?> easyNpc) {
            CompoundTag tag = easyNpc.getEasyNPCPresetData().serializePresetData();
            familiars.put(id, new FamiliarData(data.uuid(), tag));
        }
    }

    public static LivingEntity getSpawnedEntity(ServerPlayer player, String id) {
        FamiliarData data = getFamiliars(player).get(id);
        if (data == null) return null;
        return findEntity(player.server, data.uuid());
    }

    public static void enforceSpawnLimit(ServerPlayer player, int maxSpawned) {
        if (maxSpawned < 0) return;
        pruneMissing(player);
        LinkedHashMap<String, UUID> spawned = getSpawnedMap(player);
        while (spawned.size() > maxSpawned && maxSpawned > 0) {
            Map.Entry<String, UUID> oldest = spawned.entrySet().iterator().next();
            despawnByEntry(player, oldest.getValue());
            spawned.remove(oldest.getKey());
        }
    }

    public static void despawnIfSpawned(ServerPlayer player, String id) {
        LinkedHashMap<String, UUID> spawned = getSpawnedMap(player);
        UUID uuid = spawned.remove(id);
        if (uuid == null) return;
        despawnByEntry(player, uuid);
    }

    private static void despawnByEntry(ServerPlayer player, UUID uuid) {
        LivingEntity entity = findEntity(player.server, uuid);
        if (entity != null) {
            entity.discard();
        }
    }

    public static void trackSpawned(ServerPlayer player, String id, UUID uuid) {
        LinkedHashMap<String, UUID> spawned = getSpawnedMap(player);
        spawned.remove(id);
        spawned.put(id, uuid);
    }

    public static LinkedHashMap<String, UUID> getSpawnedMap(ServerPlayer player) {
        return spawnedFamiliars.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
    }

    public static void pruneMissing(ServerPlayer player) {
        LinkedHashMap<String, UUID> spawned = getSpawnedMap(player);
        if (spawned.isEmpty()) return;
        spawned.entrySet().removeIf(entry -> findEntity(player.server, entry.getValue()) == null);
    }

    private static void syncSpawnedFromWorld(ServerPlayer player) {
        Map<String, FamiliarData> familiars = getFamiliars(player);
        if (familiars.isEmpty()) return;
        LinkedHashMap<String, UUID> spawned = getSpawnedMap(player);
        for (Map.Entry<String, FamiliarData> entry : familiars.entrySet()) {
            String id = entry.getKey();
            if (spawned.containsKey(id)) continue;
            FamiliarData data = entry.getValue();
            if (findEntity(player.server, data.uuid()) != null) {
                trackSpawned(player, id, data.uuid());
            }
        }
    }

    public static LivingEntity findEntity(MinecraftServer server, UUID uuid) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    public static String findNextAutospawn(ServerPlayer player, Map<String, FamiliarData> familiars) {
        List<String> list = getAutospawnList(player);
        for (String id : list) {
            FamiliarData data = familiars.get(id);
            if (data == null) continue;
            if (findEntity(player.server, data.uuid()) == null) {
                return id;
            }
        }
        return null;
    }

    public static void removeFromAutospawn(ServerPlayer player, String id) {
        List<String> list = getAutospawnList(player);
        list.remove(id);
    }

    public static String getDisplayName(CompoundTag tag, String fallback) {
        if (tag.contains(CUSTOM_NAME_KEY)) {
            String raw = tag.getString(CUSTOM_NAME_KEY);
            if (!raw.isBlank()) {
                try {
                    JsonElement json = JsonParser.parseString(raw);
                    return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json)
                            .result()
                            .map(Component::getString)
                            .orElse(raw);
                } catch (Exception ignored) {
                    return raw;
                }
            }
        }
        return fallback;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void applyPresetDefaults(LivingEntity entity, Config.PresetOptions preset, ServerPlayer owner) {
        entity.setAttached(ModAttachments.IS_PUPPET, preset.isPuppet());
        entity.setAttached(ModAttachments.IS_OWNER_ONLY, preset.isOwnerOnly());
        entity.setAttached(ModAttachments.IS_TRANSIENT, preset.isTransient());
        if (entity instanceof EasyNPC<?> easyNpc && owner != null) {
            easyNpc.getEasyNPCOwnerData().setNPCOwner(owner);
        }
    }

    private static void moveMobNearby(PathfinderMob mob) {
        Vec3 safeSpot = LandRandomPos.getPosAway(mob, 3, 4, mob.getPosition(0));
        if (safeSpot != null)
            mob.setPos(safeSpot);
    }

    private FamiliarManager() {}
}
