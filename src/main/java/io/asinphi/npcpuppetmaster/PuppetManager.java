package io.asinphi.npcpuppetmaster;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable;
import io.asinphi.npcavatar.avatar.EntityAvatarManager;
import io.asinphi.npcpuppetmaster.action.Action;
import io.asinphi.npcpuppetmaster.attachment.ModAttachments;
import io.asinphi.npcpuppetmaster.config.Config;
import io.asinphi.npcpuppetmaster.config.Lang;
import io.asinphi.npcpuppetmaster.entity.ModEntities;
import io.asinphi.npcpuppetmaster.entity.PlayerDummyEntity;
import io.asinphi.payload.OpenGuiPayload;
import io.asinphi.payload.OpenSelectorGuiPayload;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PuppetManager {

    public record PossessionSession(
            UUID dummyUuid,
            ResourceKey<Level> dummyDimension,
            UUID npcUuid,
            ResourceKey<Level> npcDimension,
            // Original State Preservation
            boolean wasInvisible,
            boolean wasInvulnerable,
            boolean wasNoAi,
            float originalHealth // of the player
    ) {}

    private static final int MAX_HISTORY = 5;
    private static final double REACH_DISTANCE = 10.0; // How far away to detect NPCs
    private static final float FOLLOW_STOP_DISTANCE = 2.7f; // Distance to stop when following
    public static final TicketType<UUID> POSSESSION_TICKET = TicketType
            .create("possession", Comparator.comparing(UUID::toString));
    private static final Map<Action, String> actionPermissions = Map.of(
            Action.DELETE, "delete",
            Action.MOVE_HERE, "move",
            Action.GO_AWAY, "move",
            Action.POSSESS, "possess",
            Action.FOLLOW, "follow",
            Action.UNFOLLOW, "follow"
    );
    private static final Map<UUID, PossessionSession> activePossessions = new ConcurrentHashMap<>();
    private static final Map<UUID, LinkedList<UUID>> recentNpcs = new ConcurrentHashMap<>();

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Cancel possession on logout
            if (isPossessing(handler.player)) {
                cancelPossession(handler.player, false);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Iterate over a copy of the keys to avoid ConcurrentModificationException
            Set<UUID> players = new HashSet<>(activePossessions.keySet());
            for (UUID playerId : players) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    cancelPossession(player, false);
                }
            }
            activePossessions.clear();
        });

        // Cancel death if possessing and cancel possession instead
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource,
                                                       damageAmount) -> {
            if (entity instanceof ServerPlayer player && isPossessing(player)) {
                cancelPossession(player, true); // true = forced
                return false;
            }
            return true; // Allow death for everyone else
        });
    }

    public static void openGui(ServerPlayer player, LivingEntity target) {
        // Check if target is a puppet
        Boolean isPuppetAttachment = target.getAttached(ModAttachments.IS_PUPPET);
        boolean isPuppet = Boolean.TRUE.equals(isPuppetAttachment) ||
                !Boolean.FALSE.equals(isPuppetAttachment) &&
                        Config.areNpcsPuppetsByDefault();
        if (!isPuppet) {
            Lang.send(player, "not_a_puppet");
            return;
        }

        // Check special permission attachment
        String specialNode = target.getAttached(ModAttachments.REQUIRES_PERMISSION);
        if (specialNode != null && !specialNode.isEmpty()) {
            String specialPermission = "npcp.puppeteer.special." + specialNode + ".use";
            if (!Permissions.check(player, specialPermission, 2)) {
                Lang.send(player, "require_permission", Placeholder.unparsed("permission", specialPermission));
                return;
            }
        }

        recordSelection(player, target);
        boolean isFollowing = false;
        if (target instanceof EasyNPC<?> npc) {
            ObjectiveDataCapable<?> data = npc.getEasyNPCObjectiveData();
            if (data != null && data.hasObjective(ObjectiveType.FOLLOW_ENTITY_BY_UUID)) {
                isFollowing = true;
            }
        }
        boolean isPossessing = activePossessions.containsKey(player.getUUID()) &&
                activePossessions.get(player.getUUID()).npcUuid() == target.getUUID();
        ServerPlayNetworking.send(player, new OpenGuiPayload(target.getId(), isFollowing, isPossessing));
    }

    public static void openSelectorGui(ServerPlayer player) {
        List<OpenSelectorGuiPayload.Candidate> candidates = new ArrayList<>();
        Set<UUID> addedUuids = new HashSet<>();
        int possessedId = -1;

        // Add currently possessed NPC (if exists)
        if (activePossessions.containsKey(player.getUUID())) {
            PossessionSession session = activePossessions.get(player.getUUID());
            ServerLevel npcLevel = player.getServer().getLevel(session.npcDimension());
            if (npcLevel != null) {
                Entity entity = npcLevel.getEntity(session.npcUuid());
                if (entity instanceof LivingEntity npc) {
                    candidates.add(new OpenSelectorGuiPayload.Candidate(
                            npc.getId(),
                            npc.getUUID(),
                            npc.getName().getString()
                    ));
                    addedUuids.add(npc.getUUID());
                    possessedId = npc.getId();
                }
            }
        }

        // Add NPC being looked at
        LivingEntity lookingAt = getEntityLookingAt(player);
        if (lookingAt instanceof EasyNPC<?>) {
            candidates.add(new OpenSelectorGuiPayload.Candidate(
                    lookingAt.getId(),
                    lookingAt.getUUID(),
                    lookingAt.getName().getString()
            ));
            addedUuids.add(lookingAt.getUUID());
        }

        // History: Recent NPCs
        if (recentNpcs.containsKey(player.getUUID())) {
            for (UUID uuid : recentNpcs.get(player.getUUID())) {
                if (addedUuids.contains(uuid)) continue;

                Entity entity = player.serverLevel().getEntity(uuid);

                if (entity instanceof LivingEntity living && living instanceof EasyNPC<?>) {
                    candidates.add(new OpenSelectorGuiPayload.Candidate(
                            living.getId(),
                            living.getUUID(),
                            living.getName().getString()
                    ));
                    addedUuids.add(uuid);
                }
            }
        }

        ServerPlayNetworking.send(player, new OpenSelectorGuiPayload(candidates, possessedId));
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void handleAction(ServerPlayer player, int entityId, Action action) {
        Entity entity = player.level().getEntity(entityId);
        Boolean isPuppetAttachment = entity.getAttached(ModAttachments.IS_PUPPET);
        boolean isPuppet = Boolean.TRUE.equals(isPuppetAttachment) ||
                !Boolean.FALSE.equals(isPuppetAttachment) &&
                        Config.areNpcsPuppetsByDefault();
        if (!isPuppet) {
            Lang.send(player, "not_a_puppet");
            return;
        }

        String actionPermission = actionPermissions.get(action);

        if (actionPermission != null) {
            String specialNode = entity.getAttached(ModAttachments.REQUIRES_PERMISSION);
            if (specialNode != null && !specialNode.isEmpty()) {
                String specialPermission = "npcp.puppeteer.special." + specialNode + "." + actionPermission;
                if (!Permissions.check(player, specialPermission, 2)) {
                    Lang.send(player, "require_permission", Placeholder.unparsed("permission", specialPermission));
                    return;
                }
            }

            String permission = "npcp.puppeteer.action." + actionPermission;
            if (!Permissions.check(player, permission, 2)) {
                Lang.send(player, "require_permission", Placeholder.unparsed("permission", permission));
                return;
            }
        }
        forceHandleAction(player, entityId, action);
    }

    /**
     * Force handle action without permission checks.
     */
    public static void forceHandleAction(ServerPlayer player, int entityId, Action action) {
        Entity entity = player.level().getEntity(entityId);

        if (!(entity instanceof Mob mob)) {
            // Most actions require a Mob (for AI/Navigation).
            // If it's just a generic LivingEntity (like a Player), we can't control it easily.
            return;
        }

        switch (action) {
            case DELETE -> {
                mob.discard(); // remove() was renamed to discard() in recent mappings
                player.sendSystemMessage(Component.literal("Entity removed."));
            }
            case MOVE_HERE -> {
                // Pathfind to the player's current position
                mob.getNavigation().moveTo(player, 1.0f);
            }
            case GO_AWAY -> {
                // Simple logic: Pick a random spot 10 blocks away in the opposite direction
                Vec3 direction = mob.position().subtract(player.position()).normalize();
                Vec3 target = mob.position().add(direction.scale(10.0));
                mob.getNavigation().moveTo(target.x, target.y, target.z, 1.2f);
            }
            case POSSESS -> {
                startPossession(player, mob);
            }
            case UNPOSSESS -> {
                cancelPossession(player, false);
            }
            case FOLLOW -> {
                if (!(mob instanceof EasyNPC<?> npc)) {
                    return; // FOLLOW action requires EasyNPC for objectives
                }
                ObjectiveDataCapable<?> objectiveData = npc.getEasyNPCObjectiveData();
                if (objectiveData == null) {
                    return;
                }
                ObjectiveDataEntry followObjective = new ObjectiveDataEntry(ObjectiveType.FOLLOW_ENTITY_BY_UUID);
                followObjective.setTargetEntityUUID(player.getUUID());
                // Use reflection to change the stop distance
                try {
                    Field distanceField = ObjectiveDataEntry.class.getDeclaredField("stopDistance");
                    distanceField.setAccessible(true);
                    distanceField.setFloat(followObjective, FOLLOW_STOP_DISTANCE);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // Log error if the field name changes in a future update
                    NPCPuppetmaster.LOGGER.error("Failed to set stop distance for FOLLOW objective", e);
                }

                objectiveData.addOrUpdateCustomObjective(followObjective);
            }
            case UNFOLLOW -> {
                if (!(mob instanceof EasyNPC<?> npc)) {
                    return; // FOLLOW action requires EasyNPC for objectives
                }
                ObjectiveDataCapable<?> objectiveData = npc.getEasyNPCObjectiveData();
                if (objectiveData == null) {
                    return;
                }
                ObjectiveDataEntry obj = objectiveData.getObjective(ObjectiveType.FOLLOW_ENTITY_BY_UUID);
                // Remove by objective data entry because removing by objective type fails
                objectiveData.removeCustomObjective(obj);
            }
        }
    }

    public static boolean isPossessing(ServerPlayer player) {
        return activePossessions.containsKey(player.getUUID());
    }

    public static void startPossession(ServerPlayer player, LivingEntity npc) {
        if (activePossessions.containsKey(player.getUUID())) {
            cancelPossession(player, false);
        }

        // NPC must have an avatar override
        if (!EntityAvatarManager.hasAvatarOverride(npc)) {
            Lang.send(player, "possess.no_avatar_override");
            return;
        }

        ServerLevel level = (ServerLevel) player.level();

        boolean originalInvisible = npc.isInvisible();
        boolean originalInvulnerable = npc.isInvulnerable();
        boolean originalNoAi = false;
        if (npc instanceof Mob mob)
            originalNoAi = mob.isNoAi();

        // Spawn Dummy at Player's Location
        PlayerDummyEntity dummy = ModEntities.PLAYER_DUMMY.create(level);
        if (dummy == null) return;

        dummy.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        dummy.setYHeadRot(player.getYHeadRot());
        dummy.setYBodyRot(player.yBodyRot);
        dummy.yHeadRotO = player.getYHeadRot(); // Prevents lerping from 0 on first frame
        dummy.setOwner(player);
        level.addFreshEntity(dummy);

        // Copy Player -> Dummy (so dummy looks like player)
        EntityAvatarManager.copyTo(player, List.of(dummy));

        // Copy NPC -> Player (so player looks like NPC)
        EntityAvatarManager.copyTo(npc, List.of(player));

        // Teleport Player to NPC
        player.teleportTo(level, npc.getX(), npc.getY(), npc.getZ(), npc.getYHeadRot(), npc.getXRot());

        // Hide/Store the NPC
        // We move it to the void and disable it, rather than deleting it, to preserve its internal state/UUID.
        npc.setInvisible(true);
        npc.setInvulnerable(true);
        npc.teleportTo(level, npc.getX(), level.getMaxBuildHeight() + 100, npc.getZ(), Set.of(), npc.getYRot(),
                npc.getXRot());
        if (npc instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(true);
        }

        setChunkLoaded(level, dummy.position(), player.getUUID(), true);
        setChunkLoaded(level, npc.position(), player.getUUID(), true);

        // Record Session
        activePossessions.put(player.getUUID(), new PossessionSession(
                dummy.getUUID(),
                level.dimension(), // Dummy Dim
                npc.getUUID(),
                level.dimension(), // NPC Dim (Initially same as player)
                originalInvisible,
                originalInvulnerable,
                originalNoAi,
                player.getHealth()
        ));

        player.setHealth(player.getMaxHealth());

        Lang.send(player, "possess.success", Placeholder.parsed("target", npc.getName().getString()));
    }

    public static void cancelPossession(ServerPlayer player, boolean wasForced) {
        PossessionSession session = activePossessions.remove(player.getUUID());
        if (session == null) return;

        ServerLevel dummyLevel = player.getServer().getLevel(session.dummyDimension());
        ServerLevel npcLevel = player.getServer().getLevel(session.npcDimension());

        // Restore NPC
        Entity entity = null;
        if (npcLevel != null) {
            entity = npcLevel.getEntity(session.npcUuid());
            if (entity instanceof LivingEntity npc) {
                npc.setYHeadRot(player.getYHeadRot());
                npc.setYBodyRot(player.yBodyRot);
                npc.yHeadRotO = player.getYHeadRot(); // Prevent interpolation glitch

                // Restore Flags
                npc.setInvisible(session.wasInvisible());
                npc.setInvulnerable(session.wasInvulnerable());
                if (npc instanceof Mob mob)
                    mob.setNoAi(session.wasNoAi());

                // Teleport NPC to Player's CURRENT location
                npc.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), Set.of(),
                        player.getYRot(), player.getXRot());
            }
        }

        // Remove Dummy
        if (dummyLevel != null) {
            Entity dummyEntity = dummyLevel.getEntity(session.dummyUuid());

            if (dummyEntity instanceof PlayerDummyEntity dummy) {
                player.teleportTo(dummyLevel, dummyEntity.getX(), dummyEntity.getY(), dummyEntity.getZ(),
                        dummyEntity.getYRot(), dummyEntity.getXRot());

                dummy.discard();
            }
        }

        // Reset player avatar by removing the avatar override
        EntityAvatarManager.clearAvatarFromEntity(player);

        player.setHealth(session.originalHealth());

        if (!wasForced) {
            Lang.send(player, "possess.end", Placeholder.parsed("target",
                    entity != null ? entity.getName().getString() : "the NPC"));
        } else {
            Lang.send(player, "possess.forced_end", Placeholder.parsed("target",
                    entity != null ? entity.getName().getString() : "the NPC"));
        }
    }

    /**
     * Combined Suggestion Provider:
     * 1. Checks if the player is looking directly at an NPC.
     * 2. Adds the recent history list.
     */
    public static SuggestionProvider<CommandSourceStack> getSuggestions() {
        return (context, builder) -> {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) return builder.buildFuture();

            // Use a Set to avoid duplicates (e.g., if looking at a recent NPC)
            // LinkedHashSet preserves insertion order (Looking At -> Recent)
            Set<String> suggestions = new LinkedHashSet<>();

            // 1. Raycast: Is the player looking at someone?
            LivingEntity lookingAt = getEntityLookingAt(player);
            if (lookingAt instanceof EasyNPC<?>) {
                // If you want to emphasize this, you can wrap it in quotes or color codes,
                // but for arguments, plain text is safest.
                suggestions.add(lookingAt.getStringUUID());
            }

            // 2. Add History
            if (recentNpcs.containsKey(player.getUUID())) {
                suggestions.addAll(recentNpcs.get(player.getUUID()).stream()
                        .map(UUID::toString)
                        .toList());
            }

            return SharedSuggestionProvider.suggest(suggestions, builder);
        };
    }

    private static void recordSelection(ServerPlayer player, LivingEntity npc) {
        UUID uuid = npc.getUUID();

        recentNpcs.compute(player.getUUID(), (k, history) -> {
            if (history == null) history = new LinkedList<>();
            history.remove(uuid);
            history.addFirst(uuid);
            if (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
            return history;
        });
    }

    /**
     * Helper to raycast from player eyes to find an entity.
     */
    private static LivingEntity getEntityLookingAt(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(REACH_DISTANCE));

        // Create a search box around the ray
        AABB searchBox = player.getBoundingBox()
                .expandTowards(lookVec.scale(REACH_DISTANCE))
                .inflate(1.0D);

        // Perform the raycast
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                endPos,
                searchBox,
                (entity) -> entity instanceof LivingEntity && !entity.isSpectator(), // Filter
                REACH_DISTANCE * REACH_DISTANCE // Squared distance
        );

        if (hitResult != null && hitResult.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static void setChunkLoaded(ServerLevel level, Vec3 pos, UUID lockId, boolean loaded) {
        if (level == null) return;
        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(pos));
        if (loaded) {
            level.getChunkSource().addRegionTicket(POSSESSION_TICKET, chunkPos, 2, lockId);
        } else {
            level.getChunkSource().removeRegionTicket(POSSESSION_TICKET, chunkPos, 2, lockId);
        }
    }
}