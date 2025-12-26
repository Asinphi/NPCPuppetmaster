package io.asinphi.npcpuppetmaster.entity;

import io.asinphi.npcpuppetmaster.PuppetManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public class PlayerDummyEntity extends PathfinderMob {

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(PlayerDummyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public PlayerDummyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.lookControl = new DisabledLookControl(this);
        this.setPersistenceRequired();

        // Anti-Dupe: Ensure equipment never drops by default
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    public void setOwner(ServerPlayer player) {
        this.entityData.set(OWNER_UUID, Optional.of(player.getUUID()));
        this.setCustomName(player.getName());
        this.setCustomNameVisible(true);

        // Copy Equipment (Visual only)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setItemSlot(slot, player.getItemBySlot(slot).copy());
            // Re-enforce strict 0% drop chance immediately after setting item
            this.setDropChance(slot, 0.0F);
        }
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(OWNER_UUID);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);

        if (!this.level().isClientSide && damaged) {
            Optional<UUID> ownerId = getOwnerUUID();
            if (ownerId.isPresent() && this.getServer() != null) {
                ServerPlayer player = this.getServer().getPlayerList().getPlayer(ownerId.get());
                if (player != null) {
                    PuppetManager.cancelPossession(player, true);
                }
            }
        }
        return damaged;
    }

    @Override
    public @NotNull InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Only handle on server, Main Hand only
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            Optional<UUID> ownerId = getOwnerUUID();

            // Check if the clicker is the owner (who is currently possessing someone else)
            if (ownerId.isPresent() && ownerId.get().equals(player.getUUID())) {
                if (player instanceof ServerPlayer serverPlayer) {
                    // Trigger un-possession (false = not forced by damage)
                    PuppetManager.cancelPossession(serverPlayer, false);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void lerpHeadRotationStep(int lerpHeadSteps, double yHeadRot) {
        // No head turning
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void dropFromLootTable(DamageSource damageSource, boolean hitByPlayer) {
        // Do nothing (No loot table drops)
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (getOwnerUUID().isPresent()) {
            tag.putUUID("OwnerUUID", getOwnerUUID().get());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("OwnerUUID")));
        }
        // Re-enforce drop chances on load, just in case
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
    }

    // Prevent the LookControl from resetting xRot on tick
    private static class DisabledLookControl extends LookControl {
        public DisabledLookControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {}

        @Override
        protected boolean resetXRotOnTick() {
            return false;
        }
    }
}
